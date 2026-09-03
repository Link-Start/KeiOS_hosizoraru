package os.kei.feature.github.data.remote.fdroid

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.io.SharedHttpClient
import os.kei.core.io.boundedBy
import os.kei.core.io.executeCancellable
import os.kei.feature.github.data.local.fdroid.FdroidRepoCacheRecord
import os.kei.feature.github.data.local.fdroid.FdroidRepoCacheRequestKey
import os.kei.feature.github.data.local.fdroid.FdroidRepoIndexCacheStore
import os.kei.feature.github.data.local.fdroid.FdroidRepositoryIndexCacheStore
import os.kei.feature.github.data.local.fdroid.withFetchedAt
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val FDROID_INDEX_STREAM_BUFFER_SIZE = 64 * 1024
private val FDROID_INDEX_CACHE_MAX_AGE_MILLIS = 12.hours.inWholeMilliseconds

class FdroidRepositoryIndexClient(
    private val client: OkHttpClient = defaultClient,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val cacheStore: FdroidRepositoryIndexCacheStore = FdroidRepoIndexCacheStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun searchIndexV2(
        repoBaseUrl: String,
        query: String,
        packageName: String,
        limit: Int,
        forceRefresh: Boolean = false,
        maxAgeMillis: Long = FDROID_INDEX_CACHE_MAX_AGE_MILLIS
    ): Result<FdroidRepositorySnapshot> = withContext(ioDispatcher) {
        fdroidRepositoryIndexResult {
            val normalizedRepoUrl = repoBaseUrl.trim().trimEnd('/')
            require(normalizedRepoUrl.isNotBlank()) { "F-Droid repository URL is blank" }
            val cacheKey = FdroidRepoCacheRequestKey.search(
                repoUrl = normalizedRepoUrl,
                query = query,
                packageName = packageName,
                limit = limit
            )
            loadFromCacheOrNetwork(
                cacheKey = cacheKey,
                forceRefresh = forceRefresh,
                maxAgeMillis = maxAgeMillis,
                failurePrefix = "F-Droid index-v2 search failed"
            ) { reader ->
                FdroidIndexV2StreamParser
                    .searchIndex(
                        repoUrl = normalizedRepoUrl,
                        reader = reader,
                        query = query,
                        packageName = packageName,
                        limit = limit
                    )
                    .getOrThrow()
            }
        }
    }

    /**
     * @param maxIndexBytes aborts the read once the index has streamed this far, for a caller that wants
     *   the index only if it is cheap. f-droid.org publishes nearly sixty megabytes and a third-party
     *   repository a few, so "read it if it is small" is a budget rather than a per-host list — see
     *   `FdroidVersionHistoryProvider`. Unbounded by default, which is what the refresh path relies on.
     */
    suspend fun fetchIndexV2Packages(
        repoBaseUrl: String,
        packageNames: Set<String>,
        forceRefresh: Boolean = false,
        maxAgeMillis: Long = FDROID_INDEX_CACHE_MAX_AGE_MILLIS,
        maxIndexBytes: Long = Long.MAX_VALUE
    ): Result<FdroidRepositorySnapshot> = withContext(ioDispatcher) {
        fdroidRepositoryIndexResult {
            val normalizedRepoUrl = repoBaseUrl.trim().trimEnd('/')
            require(normalizedRepoUrl.isNotBlank()) { "F-Droid repository URL is blank" }
            require(packageNames.any { name -> name.trim().isNotBlank() }) {
                "F-Droid package names are blank"
            }
            val cacheKey = FdroidRepoCacheRequestKey.packages(
                repoUrl = normalizedRepoUrl,
                packageNames = packageNames
            )
            loadFromCacheOrNetwork(
                cacheKey = cacheKey,
                forceRefresh = forceRefresh,
                maxAgeMillis = maxAgeMillis,
                failurePrefix = "F-Droid index-v2 package fetch failed",
                maxIndexBytes = maxIndexBytes
            ) { reader ->
                FdroidIndexV2StreamParser
                    .loadPackages(
                        repoUrl = normalizedRepoUrl,
                        reader = reader,
                        packageNames = packageNames
                    )
                    .getOrThrow()
            }
        }
    }

    private suspend fun loadFromCacheOrNetwork(
        cacheKey: FdroidRepoCacheRequestKey,
        forceRefresh: Boolean,
        maxAgeMillis: Long,
        failurePrefix: String,
        maxIndexBytes: Long = Long.MAX_VALUE,
        parser: suspend (java.io.Reader) -> FdroidRepositorySnapshot
    ): FdroidRepositorySnapshot {
        val nowMillis = clock()
        val cached = cacheStore.load(cacheKey)
        if (!forceRefresh && cached?.isFresh(nowMillis, maxAgeMillis) == true) {
            return cached.snapshot
        }
        val request = requestBuilder(
            repoUrl = cacheKey.repoUrl,
            cached = cached
        ).build()
        client.executeCancellable(request).use { response ->
            if (response.code == HTTP_NOT_MODIFIED) {
                val reusable = cached ?: error("$failurePrefix (HTTP 304 without local cache)")
                val refreshed = reusable.withFetchedAt(nowMillis)
                cacheStore.save(cacheKey, refreshed)
                return refreshed.snapshot
            }
            check(response.isSuccessful) {
                "$failurePrefix (HTTP ${response.code})"
            }
            // The declared length short-circuits before a byte of body is read; the streaming bound is
            // the backstop for a server that declares none.
            val declaredLength = response.body.contentLength()
            check(declaredLength <= maxIndexBytes) {
                "$failurePrefix (index is $declaredLength bytes, budget $maxIndexBytes)"
            }
            val snapshot = response.body.charStream()
                .buffered(FDROID_INDEX_STREAM_BUFFER_SIZE)
                .boundedBy(maxIndexBytes)
                .use { reader -> parser(reader) }
            cacheStore.save(
                cacheKey,
                FdroidRepoCacheRecord(
                    repoUrl = cacheKey.repoUrl,
                    fetchedAtMillis = nowMillis,
                    etag = response.header("ETag").orEmpty(),
                    lastModified = response.header("Last-Modified").orEmpty(),
                    snapshot = snapshot
                )
            )
            return snapshot
        }
    }

    private fun requestBuilder(
        repoUrl: String,
        cached: FdroidRepoCacheRecord?
    ): Request.Builder {
        return Request.Builder()
            .url("$repoUrl/index-v2.json")
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,*/*")
            .apply {
                val etag = cached?.etag.orEmpty()
                val lastModified = cached?.lastModified.orEmpty()
                if (etag.isNotBlank()) header("If-None-Match", etag)
                if (lastModified.isNotBlank()) header("If-Modified-Since", lastModified)
            }
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val USER_AGENT = "KeiOS-App/1.0 (Android)"
        val defaultClient: OkHttpClient = SharedHttpClient.base.newBuilder()
            .connectTimeout(8.seconds)
            .readTimeout(30.seconds)
            .callTimeout(42.seconds)
            .build()
    }
}

private inline fun <T> fdroidRepositoryIndexResult(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
