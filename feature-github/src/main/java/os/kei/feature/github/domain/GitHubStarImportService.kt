package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.local.GitHubStarImportApkVerificationCacheStore
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.StarImportApplyResult

class GitHubStarImportService(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val snapshotLoader: () -> GitHubTrackSnapshot = GitHubTrackStore::loadSnapshot,
    private val discoverySourceFactory: (String) -> GitHubRepositoryDiscoverySource = ::defaultDiscoverySource,
    private val apkVerifierFactory: () -> GitHubStarImportApkVerifier = ::defaultApkVerifier,
) {
    suspend fun loadTrackSnapshot(): GitHubTrackSnapshot =
        withContext(ioDispatcher) {
            snapshotLoader()
        }

    suspend fun loadApiTokenAvailable(): Boolean =
        withContext(ioDispatcher) {
            snapshotLoader().lookupConfig.apiToken.isNotBlank()
        }

    suspend fun fetchStarLists(
        listUrl: String,
        apiToken: String,
    ): List<GitHubStarListSummary> =
        withContext(ioDispatcher) {
            discoverySourceFactory(apiToken).fetchStarLists(listUrl).getOrThrow()
        }

    suspend fun previewStarredRepositoryImport(
        request: GitHubStarredRepositoryImportRequest,
        existingItems: List<GitHubTrackedApp>,
    ): GitHubStarredRepositoryImportPreview =
        GitHubRepositoryDiscoveryService(
            source = discoverySourceFactory(request.apiToken),
            ioDispatcher = ioDispatcher,
        ).previewStarredRepositoryImport(
            request = request,
            existingItems = existingItems,
        ).getOrThrow()

    suspend fun verifyApkAssets(
        targets: List<GitHubRepositoryImportCandidate>,
        lookupConfig: GitHubLookupConfig,
        refreshIntervalHours: Int,
        maxConcurrency: Int,
    ): List<Pair<String, GitHubStarImportApkVerification>> =
        withContext(ioDispatcher) {
            val verifier = apkVerifierFactory()
            GitHubExecution.mapOrderedBounded(
                items = targets,
                maxConcurrency = maxConcurrency,
            ) { candidate ->
                candidate.trackedApp.id to verifier.verify(
                    candidate = candidate,
                    lookupConfig = lookupConfig,
                    refreshIntervalHours = refreshIntervalHours,
                )
            }
        }

    fun importCandidates(
        candidates: List<GitHubRepositoryImportCandidate>,
        onRefreshNeeded: () -> Unit,
    ): StarImportApplyResult =
        GitHubStarImportApplier.apply(
            candidates = candidates,
            onRefreshNeeded = onRefreshNeeded,
        )

    companion object {
        fun defaultDiscoverySource(apiToken: String): GitHubRepositoryDiscoverySource =
            GitHubRepositoryDiscoveryRepository(apiToken = apiToken)

        fun defaultApkVerifier(): GitHubStarImportApkVerifier =
            GitHubStarImportApkVerifier(
                source = GitHubApkPackageNameScanRepository(),
                cache = GitHubStarImportApkVerificationCacheStore,
            )
    }
}
