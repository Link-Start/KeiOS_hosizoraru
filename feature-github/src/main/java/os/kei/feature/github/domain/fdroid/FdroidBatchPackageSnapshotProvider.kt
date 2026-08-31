package os.kei.feature.github.domain.fdroid

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidRepositoryIndexClient
import os.kei.feature.github.data.remote.fdroid.FdroidRepositorySnapshot
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.buildFdroidRepositoryTrackIdentity
import os.kei.feature.github.model.isFdroidRepositoryTrack
import java.util.Locale

private const val DEFAULT_FDROID_REPOSITORY_BATCH_SIZE = 4

fun interface FdroidRepositoryPackagesSnapshotProvider {
    suspend fun loadRepositoryPackagesSnapshot(
        repoUrl: String,
        packageNames: Set<String>,
        forceRefresh: Boolean
    ): Result<FdroidRepositorySnapshot>
}

class FdroidRepositoryIndexPackagesSnapshotProvider(
    private val client: FdroidRepositoryIndexClient = FdroidRepositoryIndexClient()
) : FdroidRepositoryPackagesSnapshotProvider {
    override suspend fun loadRepositoryPackagesSnapshot(
        repoUrl: String,
        packageNames: Set<String>,
        forceRefresh: Boolean
    ): Result<FdroidRepositorySnapshot> {
        return client.fetchIndexV2Packages(
            repoBaseUrl = repoUrl,
            packageNames = packageNames,
            forceRefresh = forceRefresh
        )
    }
}

class FdroidBatchPackageSnapshotProvider(
    trackedItems: List<GitHubTrackedApp>,
    private val packageProvider: FdroidPackageSnapshotProvider = FdroidPackageApiSnapshotProvider(),
    private val repositoryPackagesProvider: FdroidRepositoryPackagesSnapshotProvider =
        FdroidRepositoryIndexPackagesSnapshotProvider(),
    private val minRepositoryBatchSize: Int = DEFAULT_FDROID_REPOSITORY_BATCH_SIZE
) : FdroidPackageSnapshotProvider, FdroidPackageLookupSnapshotProvider {
    private val repositoryModePackagesByUrl: Map<String, Set<String>> =
        trackedItems
            .asSequence()
            .filter { item -> item.isFdroidRepositoryTrack() }
            .mapNotNull { item ->
                item.fdroidIdentityOrNull()?.let { identity ->
                    identity.normalizedRepoUrl to identity.packageName
                }
            }
            .groupBy(
                keySelector = { (repoUrl, _) -> repoUrl },
                valueTransform = { (_, packageName) -> packageName }
            )
            .mapValues { (_, packageNames) ->
                packageNames
                    .asSequence()
                    .map { name -> name.trim() }
                    .filter { name -> name.isNotBlank() }
                    .distinctBy { name -> name.lowercase(Locale.ROOT) }
                    .toSet()
            }
            .filterValues { packageNames -> packageNames.size >= minRepositoryBatchSize.coerceAtLeast(2) }

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
        return if (identity.normalizedRepoUrl in repositoryModePackagesByUrl) {
            loadLookupFromRepository(
                repoUrl = identity.normalizedRepoUrl,
                packageName = identity.packageName,
                forceRefresh = forceRefresh
            )
        } else {
            loadLookupFromApi(
                item = item,
                repoUrl = identity.normalizedRepoUrl,
                packageName = identity.packageName,
                forceRefresh = forceRefresh
            )
        }
    }

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
        val packageNames =
            repositoryModePackagesByUrl[repoUrl]
                ?.plus(packageName)
                ?: setOf(packageName)
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
                forceRefresh = forceRefresh
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
