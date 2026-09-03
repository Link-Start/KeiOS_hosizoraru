package os.kei.feature.github.domain.fdroid

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import os.kei.feature.github.data.remote.fdroid.FdroidPackagePageClient
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidRepositoryIndexClient
import os.kei.feature.github.data.remote.fdroid.FdroidRepositorySnapshot
import os.kei.feature.github.data.remote.fdroid.fdroidPackagePageUrl
import os.kei.feature.github.model.FdroidIndexFormat
import os.kei.feature.github.model.FdroidRepositoryPresets
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.buildFdroidRepositoryTrackIdentity
import os.kei.feature.github.model.isFdroidRepositoryTrack
import java.util.Locale

/**
 * The most a background refresh will download to read one repository's index.
 *
 * Takes a third-party repository whole -- IzzyOnDroid publishes ~14 MB -- and stops well short of
 * f-droid.org's ~58 MB, which has a package page to read instead. A repository that has neither an
 * affordable index nor a page falls back to the thin API, exactly as it did before.
 */
private const val DEFAULT_MAX_REFRESH_INDEX_BYTES: Long = 16L * 1024L * 1024L

/** The package page as a seam, so a refresh can be tested without reaching a repository's website. */
fun interface FdroidPackagePageSnapshotProvider {
    suspend fun loadPackagePageSnapshot(
        repoUrl: String,
        packageName: String
    ): Result<FdroidPackageSnapshot>
}

class FdroidPackagePageHttpProvider(
    private val client: FdroidPackagePageClient = FdroidPackagePageClient()
) : FdroidPackagePageSnapshotProvider {
    override suspend fun loadPackagePageSnapshot(
        repoUrl: String,
        packageName: String
    ): Result<FdroidPackageSnapshot> =
        client.fetchPackagePage(repoBaseUrl = repoUrl, packageName = packageName)
}

fun interface FdroidRepositoryPackagesSnapshotProvider {
    /**
     * @param maxIndexBytes the most this read may download before giving up, so a caller that cannot
     *   afford a repository's index spends that much and no more. See `FdroidRepositoryIndexClient`.
     *   Stated rather than defaulted: a `fun interface` cannot default its own abstract method, and
     *   defaulting it to unbounded elsewhere is how a caller silently pulls fifty-eight megabytes.
     */
    suspend fun loadRepositoryPackagesSnapshot(
        repoUrl: String,
        packageNames: Set<String>,
        forceRefresh: Boolean,
        maxIndexBytes: Long
    ): Result<FdroidRepositorySnapshot>
}

class FdroidRepositoryIndexPackagesSnapshotProvider(
    private val client: FdroidRepositoryIndexClient = FdroidRepositoryIndexClient()
) : FdroidRepositoryPackagesSnapshotProvider {
    override suspend fun loadRepositoryPackagesSnapshot(
        repoUrl: String,
        packageNames: Set<String>,
        forceRefresh: Boolean,
        maxIndexBytes: Long
    ): Result<FdroidRepositorySnapshot> {
        return client.fetchIndexV2Packages(
            repoBaseUrl = repoUrl,
            packageNames = packageNames,
            forceRefresh = forceRefresh,
            maxIndexBytes = maxIndexBytes
        )
    }
}

/**
 * How a refresh batch reads F-Droid, and what it is willing to spend to do it.
 *
 * The choice matters because these indexes are large: `index-v2.json` is ~14 MB on IzzyOnDroid and ~58 MB
 * on f-droid.org, and a refresh runs on a schedule rather than because someone asked. So the rule is that
 * a background check spends bytes only when it cannot do its job without them:
 *
 * - **The package page** where the repository has one this app reads. 37 KB, and it carries the size,
 *   date, ABIs and minSdk the thin API does not — which is what makes the cached sidecar worth reading.
 * - **The index** when a track in that repository has a trust policy that needs an APK hash or a signer.
 *   Those are the fields no cheaper source publishes, and without them the check does not merely look
 *   poorer, it fails. This is also what an unreachable page falls back to, under a byte budget.
 * - **The thin package API** otherwise.
 *
 * The trade this replaces: every repository with four or more tracked packages used to read the index
 * unconditionally, so tracking four f-droid.org apps meant pulling 58 MB every refresh cycle. Reading
 * four pages instead is about 150 KB. The cost is that a page carries no repository description, mirror
 * list or package count, so a detail sheet for such a track now shows the preset's name and the address
 * rather than the repository's own blurb — see [pageRepositorySnapshot].
 */
class FdroidBatchPackageSnapshotProvider(
    private val trackedItems: List<GitHubTrackedApp>,
    private val packageProvider: FdroidPackageSnapshotProvider = FdroidPackageApiSnapshotProvider(),
    private val repositoryPackagesProvider: FdroidRepositoryPackagesSnapshotProvider =
        FdroidRepositoryIndexPackagesSnapshotProvider(),
    private val pageProvider: FdroidPackagePageSnapshotProvider = FdroidPackagePageHttpProvider(),
    private val maxIndexBytes: Long = DEFAULT_MAX_REFRESH_INDEX_BYTES
) : FdroidPackageSnapshotProvider, FdroidPackageLookupSnapshotProvider {
    /**
     * Repositories where some tracked item's trust policy needs an APK hash or a signer.
     *
     * The only case worth an index in a background refresh: those fields exist nowhere cheaper, and
     * `FdroidReleaseCheckSource` fails the check outright without them.
     */
    private val fingerprintRequiredRepoUrls: Set<String> =
        trackedItems
            .asSequence()
            .filter { item -> item.isFdroidRepositoryTrack() }
            .filter { item ->
                item.fdroidConfig.trustPolicy == FdroidTrustPolicy.RequireApkHash ||
                    item.fdroidConfig.trustPolicy == FdroidTrustPolicy.RequireOfficialSignerIndex
            }
            .mapNotNull { item -> item.fdroidIdentityOrNull()?.normalizedRepoUrl }
            .toSet()

    private val mutex = Mutex()
    private val repositoryCache = mutableMapOf<String, Result<FdroidRepositorySnapshot>>()
    private val packageCache = mutableMapOf<String, Result<FdroidPackageSnapshot>>()
    private val repositoryInFlight = mutableMapOf<String, CompletableDeferred<Result<FdroidRepositorySnapshot>>>()
    private val packageInFlight = mutableMapOf<String, CompletableDeferred<Result<FdroidPackageSnapshot>>>()

    override suspend fun loadPackageSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean
    ): Result<FdroidPackageSnapshot> {
        return loadPackageLookupSnapshot(item, forceRefresh)
            .map { lookup -> lookup.packageSnapshot }
    }

    override suspend fun loadPackageLookupSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean
    ): Result<FdroidPackageLookupSnapshot> {
        return loadLookupSnapshot(item, forceRefresh)
    }

    private suspend fun loadLookupSnapshot(
        item: GitHubTrackedApp,
        forceRefresh: Boolean
    ): Result<FdroidPackageLookupSnapshot> {
        val identity = item.fdroidIdentityOrNull()
            ?: return Result.failure(IllegalArgumentException("invalid F-Droid repository URL or package"))
        val repoUrl = identity.normalizedRepoUrl
        // The index first only where a trust policy makes it necessary; otherwise it is megabytes spent
        // to make a cached detail sheet prettier, on a schedule nobody asked for.
        if (repoUrl in fingerprintRequiredRepoUrls) {
            val indexed = loadLookupFromRepository(repoUrl, identity.packageName, forceRefresh)
            if (indexed.isSuccess) return indexed
        }
        if (fdroidPackagePageUrl(repoUrl, identity.packageName) != null) {
            val page = loadLookupFromPage(repoUrl, identity.packageName)
            if (page.isSuccess) return page
        } else {
            // No page to read, so the index is the only source with files in it. Budgeted, so this is a
            // third-party repository's fourteen megabytes and never f-droid.org's fifty-eight.
            val indexed = loadLookupFromRepository(repoUrl, identity.packageName, forceRefresh)
            if (indexed.isSuccess) return indexed
        }
        return loadLookupFromApi(
            item = item,
            repoUrl = repoUrl,
            packageName = identity.packageName,
            forceRefresh = forceRefresh
        )
    }

    /**
     * The package page, plus the repository facts that survive without an index.
     *
     * A page states nothing about the repository itself, so the sidecar would otherwise lose the name it
     * shows. The identity and the preset list both know it, and neither needs a request.
     */
    private suspend fun loadLookupFromPage(
        repoUrl: String,
        packageName: String
    ): Result<FdroidPackageLookupSnapshot> =
        pageProvider
            .loadPackagePageSnapshot(repoUrl = repoUrl, packageName = packageName)
            .map { snapshot ->
                FdroidPackageLookupSnapshot(
                    packageSnapshot = snapshot,
                    repositorySnapshot = pageRepositorySnapshot(repoUrl)
                )
            }

    private fun pageRepositorySnapshot(repoUrl: String): FdroidRepositorySnapshot =
        FdroidRepositorySnapshot(
            repoUrl = repoUrl,
            format = FdroidIndexFormat.Unknown,
            repoName = FdroidRepositoryPresets.entries
                .firstOrNull { preset -> preset.repoUrl.trimEnd('/') == repoUrl.trimEnd('/') }
                ?.displayName
                .orEmpty(),
            repoDescription = "",
            timestampMillis = null,
            mirrors = emptyList(),
            packages = emptyMap()
        )

    private suspend fun loadLookupFromRepository(
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean
    ): Result<FdroidPackageLookupSnapshot> {
        return loadRepositoryPackages(
            repoUrl = repoUrl,
            packageName = packageName,
            forceRefresh = forceRefresh
        ).mapCatching { repository ->
            val packageSnapshot = repository.packageSnapshot(packageName)
                ?: error("F-Droid package $packageName was not found in $repoUrl")
            FdroidPackageLookupSnapshot(
                packageSnapshot = packageSnapshot,
                repositorySnapshot = repository
            )
        }
    }

    private suspend fun loadRepositoryPackages(
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean
    ): Result<FdroidRepositorySnapshot> {
        // The same set the version-history page asks for, so the two share one cache entry and one
        // download rather than pulling the same megabytes twice -- see fdroidTrackedPackagesIn.
        val packageNames = fdroidTrackedPackagesIn(repoUrl, trackedItems) + packageName
        val key =
            repoUrl.cacheKey() +
                "|" +
                packageNames
                    .map { name -> name.cacheKey() }
                    .sorted()
                    .joinToString(",")
        return loadRepository(
            cacheKey = key,
            repoUrl = repoUrl,
            forceRefresh = forceRefresh
        ) {
            repositoryPackagesProvider.loadRepositoryPackagesSnapshot(
                repoUrl = repoUrl,
                packageNames = packageNames,
                forceRefresh = forceRefresh,
                maxIndexBytes = maxIndexBytes
            )
        }
    }

    private suspend fun loadRepository(
        cacheKey: String,
        repoUrl: String,
        forceRefresh: Boolean,
        loader: suspend () -> Result<FdroidRepositorySnapshot>
    ): Result<FdroidRepositorySnapshot> {
        val inFlight = mutex.withLock {
            if (!forceRefresh) {
                repositoryCache[cacheKey]?.let { cached -> return cached }
            }
            repositoryInFlight[cacheKey]?.let { existing ->
                return@withLock InFlight(existing, owner = false)
            }
            val created = CompletableDeferred<Result<FdroidRepositorySnapshot>>()
            repositoryInFlight[cacheKey] = created
            InFlight(created, owner = true)
        }
        if (!inFlight.owner) {
            return inFlight.deferred.await()
        }
        val result =
            try {
                loader()
            } catch (error: CancellationException) {
                mutex.withLock { repositoryInFlight.remove(cacheKey) }
                inFlight.deferred.completeExceptionally(error)
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        mutex.withLock {
            repositoryCache[cacheKey] = result
            repositoryInFlight.remove(cacheKey)
        }
        inFlight.deferred.complete(result)
        return result
    }

    private suspend fun loadLookupFromApi(
        item: GitHubTrackedApp,
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean
    ): Result<FdroidPackageLookupSnapshot> {
        val apiResult = loadPackageFromApi(
            item = item,
            repoUrl = repoUrl,
            packageName = packageName,
            forceRefresh = forceRefresh
        )
        if (apiResult.isSuccess) {
            return apiResult.map { snapshot ->
                FdroidPackageLookupSnapshot(packageSnapshot = snapshot)
            }
        }
        val repositoryResult = loadLookupFromRepository(
            repoUrl = repoUrl,
            packageName = packageName,
            forceRefresh = forceRefresh
        )
        if (repositoryResult.isSuccess) return repositoryResult
        val apiError = apiResult.exceptionOrNull()
        val repositoryError = repositoryResult.exceptionOrNull()
        // Both halves failed, and only one of them can be the cause. The repository error stays the
        // cause because it is the later, more specific attempt -- but the API error is attached as a
        // suppressed exception rather than dropped, because it is the half that carries a *typed*
        // failure worth classifying. An oversized response throws
        // `BoundedContentTextReadTooLargeException` here, and folding it into the message alone left
        // `GitHubRefreshFailureClassifier` with nothing to find: every oversized F-Droid repository
        // reported "unknown failure" while the app's own response-size diagnostics stayed unreachable.
        return Result.failure(
            IllegalStateException(
                "F-Droid package API failed: ${apiError?.message ?: "unknown"}; " +
                    "repository index fallback failed: ${repositoryError?.message ?: "unknown"}",
                repositoryError ?: apiError
            ).apply {
                if (repositoryError != null && apiError != null) addSuppressed(apiError)
            }
        )
    }

    private suspend fun loadPackageFromApi(
        item: GitHubTrackedApp,
        repoUrl: String,
        packageName: String,
        forceRefresh: Boolean
    ): Result<FdroidPackageSnapshot> {
        val key = "$repoUrl|$packageName".cacheKey()
        val inFlight = mutex.withLock {
            if (!forceRefresh) {
                packageCache[key]?.let { cached -> return cached }
            }
            packageInFlight[key]?.let { existing ->
                return@withLock InFlight(existing, owner = false)
            }
            val created = CompletableDeferred<Result<FdroidPackageSnapshot>>()
            packageInFlight[key] = created
            InFlight(created, owner = true)
        }
        if (!inFlight.owner) {
            return inFlight.deferred.await()
        }
        val result =
            try {
                packageProvider.loadPackageSnapshot(item, forceRefresh)
            } catch (error: CancellationException) {
                mutex.withLock { packageInFlight.remove(key) }
                inFlight.deferred.completeExceptionally(error)
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        mutex.withLock {
            packageCache[key] = result
            packageInFlight.remove(key)
        }
        inFlight.deferred.complete(result)
        return result
    }

    private fun GitHubTrackedApp.fdroidIdentityOrNull() =
        buildFdroidRepositoryTrackIdentity(repoUrl, packageName)

    private fun String.cacheKey(): String = trim().lowercase(Locale.ROOT)

    private data class InFlight<T>(
        val deferred: CompletableDeferred<Result<T>>,
        val owner: Boolean
    )
}
