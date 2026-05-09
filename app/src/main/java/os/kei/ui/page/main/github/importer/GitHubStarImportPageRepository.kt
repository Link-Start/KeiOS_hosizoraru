package os.kei.ui.page.main.github.importer

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.feature.github.GitHubBoundedRunner
import os.kei.feature.github.data.local.GitHubStarImportApkVerificationCacheStore
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryService
import os.kei.feature.github.domain.GitHubRepositoryDiscoverySource
import os.kei.feature.github.domain.GitHubStarImportApkVerifier
import os.kei.feature.github.domain.GitHubStarImportApplier
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.StarImportApplyResult

internal data class StarImportLoadRequest(
    val source: StarImportUiSource,
    val usernameInput: String,
    val listUrlInput: String,
    val forcedStarListUrl: String? = null
)

internal data class StarImportProgress(
    val progress: Float,
    @param:StringRes val phaseRes: Int
)

internal sealed interface StarImportLoadResult {
    data class Lists(val items: List<GitHubStarListSummary>) : StarImportLoadResult
    data class Preview(val preview: GitHubStarredRepositoryImportPreview) : StarImportLoadResult
}

internal class GitHubStarImportPageRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val snapshotLoader: () -> GitHubTrackSnapshot = GitHubTrackStore::loadSnapshot,
    private val discoverySourceFactory: (String) -> GitHubRepositoryDiscoverySource = { apiToken ->
        GitHubRepositoryDiscoveryRepository(apiToken = apiToken)
    },
    private val apkVerifierFactory: () -> GitHubStarImportApkVerifier = {
        GitHubStarImportApkVerifier(
            source = GitHubApkPackageNameScanRepository(),
            cache = GitHubStarImportApkVerificationCacheStore
        )
    }
) {
    suspend fun loadPreview(
        request: StarImportLoadRequest,
        onProgress: suspend (StarImportProgress) -> Unit = {}
    ): StarImportLoadResult {
        return withContext(ioDispatcher) {
            onProgress(
                StarImportProgress(
                    progress = 0.08f,
                    phaseRes = R.string.github_star_import_loading_source
                )
            )
            val snapshot = snapshotLoader()
            val lookupConfig = snapshot.lookupConfig
            val targetSource = request.forcedStarListUrl
                ?.let { StarImportUiSource.ListUrl }
                ?: request.source
            val targetListUrl = request.forcedStarListUrl ?: request.listUrlInput.trim()
            val targetUsername = request.usernameInput.toGitHubUsernameInput()
            val source = discoverySourceFactory(lookupConfig.apiToken)

            if (
                targetSource == StarImportUiSource.ListUrl &&
                request.forcedStarListUrl == null &&
                targetListUrl.isGitHubStarsOverviewInput()
            ) {
                onProgress(
                    StarImportProgress(
                        progress = 0.34f,
                        phaseRes = R.string.github_star_import_loading_lists
                    )
                )
                val lists = source.fetchStarLists(targetListUrl).getOrThrow()
                if (lists.isNotEmpty()) {
                    return@withContext StarImportLoadResult.Lists(lists)
                }
            }

            val resolvedListUrl = if (
                targetSource == StarImportUiSource.ListUrl &&
                request.forcedStarListUrl == null &&
                targetListUrl.isGitHubStarsOverviewInput()
            ) {
                targetListUrl.toGitHubStarsRepositoryUrlInput()
            } else {
                targetListUrl
            }
            onProgress(
                StarImportProgress(
                    progress = 0.64f,
                    phaseRes = R.string.github_star_import_loading_repositories
                )
            )
            val importRequest = GitHubStarredRepositoryImportRequest(
                source = targetSource.toRequestSource(),
                username = targetUsername,
                starListUrl = resolvedListUrl,
                apiToken = lookupConfig.apiToken,
                limit = STAR_IMPORT_PREVIEW_LIMIT
            )
            val preview = GitHubRepositoryDiscoveryService(source)
                .previewStarredRepositoryImport(
                    request = importRequest,
                    existingItems = snapshot.items
                )
                .getOrThrow()
            onProgress(
                StarImportProgress(
                    progress = 0.88f,
                    phaseRes = R.string.github_star_import_loading_preview
                )
            )
            StarImportLoadResult.Preview(preview)
        }
    }

    suspend fun verifyApkAssets(
        targets: List<GitHubRepositoryImportCandidate>
    ): List<Pair<String, GitHubStarImportApkVerification>> {
        val uniqueTargets = targets
            .distinctBy { it.trackedApp.id }
            .take(MAX_APK_VERIFICATION_BATCH)
        if (uniqueTargets.isEmpty()) return emptyList()
        return withContext(ioDispatcher) {
            val snapshot = snapshotLoader()
            val verifier = apkVerifierFactory()
            GitHubBoundedRunner.mapOrdered(
                items = uniqueTargets,
                maxConcurrency = MAX_PARALLEL_APK_VERIFICATIONS,
                threadName = "github-star-import-verify"
            ) { candidate ->
                candidate.trackedApp.id to verifier.verify(
                    candidate = candidate,
                    lookupConfig = snapshot.lookupConfig,
                    refreshIntervalHours = snapshot.refreshIntervalHours
                )
            }
        }
    }

    suspend fun importCandidates(
        context: Context,
        candidates: List<GitHubRepositoryImportCandidate>,
        verificationStates: Map<String, StarImportApkVerificationUiState>
    ): StarImportApplyResult {
        val selectedForImport = applyVerifiedPackageNamesToStarImportCandidates(
            candidates = candidates,
            verificationStates = verificationStates
        )
        return withContext(ioDispatcher) {
            GitHubStarImportApplier.apply(
                context = context,
                candidates = selectedForImport
            )
        }
    }

    companion object {
        private const val STAR_IMPORT_PREVIEW_LIMIT = 1_000
        private const val MAX_APK_VERIFICATION_BATCH = 30
        private const val MAX_PARALLEL_APK_VERIFICATIONS = 4
    }
}
