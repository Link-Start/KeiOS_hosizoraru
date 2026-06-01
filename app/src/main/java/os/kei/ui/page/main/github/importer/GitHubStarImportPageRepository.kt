package os.kei.ui.page.main.github.importer

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.domain.GitHubRepositoryDiscoverySource
import os.kei.feature.github.domain.GitHubStarImportApkVerifier
import os.kei.feature.github.domain.GitHubStarImportService
import os.kei.feature.github.model.GitHubRepositoryImportCandidate
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarImportQuality
import os.kei.feature.github.model.GitHubStarListSummary
import os.kei.feature.github.model.GitHubStarredRepositoryImportPreview
import os.kei.feature.github.model.GitHubStarredRepositoryImportRequest
import os.kei.feature.github.model.StarImportApplyResult

@Immutable
internal data class StarImportLoadRequest(
    val source: StarImportUiSource,
    val usernameInput: String,
    val listUrlInput: String,
    val forcedStarListUrl: String? = null
)

@Immutable
internal data class StarImportProgress(
    val progress: Float,
    @param:StringRes val phaseRes: Int
)

internal sealed interface StarImportLoadResult {
    @Immutable
    data class Lists(val items: List<GitHubStarListSummary>) : StarImportLoadResult

    @Immutable
    data class Preview(val preview: GitHubStarredRepositoryImportPreview) : StarImportLoadResult
}

internal class GitHubStarImportPageRepository(
    private val starImportService: GitHubStarImportService = GitHubStarImportService(),
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
) {
    internal constructor(
        ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
        defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
        snapshotLoader: () -> GitHubTrackSnapshot,
        discoverySourceFactory: (String) -> GitHubRepositoryDiscoverySource =
            GitHubStarImportService::defaultDiscoverySource,
        apkVerifierFactory: () -> GitHubStarImportApkVerifier =
            GitHubStarImportService::defaultApkVerifier,
    ) : this(
        starImportService = GitHubStarImportService(
            ioDispatcher = ioDispatcher,
            snapshotLoader = snapshotLoader,
            discoverySourceFactory = discoverySourceFactory,
            apkVerifierFactory = apkVerifierFactory,
        ),
        defaultDispatcher = defaultDispatcher,
        ioDispatcher = ioDispatcher,
    )

    suspend fun loadInitialState(): GitHubStarImportUiState {
        return withContext(ioDispatcher) {
            val snapshot = starImportService.loadTrackSnapshot()
            val savedDraft = GitHubStarImportDraftStore.load()
            GitHubStarImportUiState(
                source = savedDraft.source,
                apiTokenAvailable = snapshot.lookupConfig.apiToken.isNotBlank(),
                usernameInput = savedDraft.usernameInput,
                listUrlInput = savedDraft.listUrlInput,
                filterInput = savedDraft.filterInput,
                viewFilter = savedDraft.viewFilter,
                qualityFilters = savedDraft.qualityFilters,
                conflictStrategy = savedDraft.conflictStrategy,
                selectedIds = savedDraft.selectedIds,
            )
        }
    }

    suspend fun loadApiTokenAvailable(): Boolean {
        return starImportService.loadApiTokenAvailable()
    }

    suspend fun saveDraft(draft: GitHubStarImportDraft) {
        withContext(ioDispatcher) {
            GitHubStarImportDraftStore.save(draft)
        }
    }

    suspend fun clearDraftSelection() {
        withContext(ioDispatcher) {
            GitHubStarImportDraftStore.clearSelection()
        }
    }

    suspend fun buildCandidateListUiState(
        candidates: List<GitHubRepositoryImportCandidate>,
        filterInput: String,
        viewFilter: StarImportViewFilter,
        qualityFilters: Set<GitHubStarImportQuality>,
        conflictStrategy: StarImportConflictStrategy,
        selectedIds: Set<String>,
        verificationStates: Map<String, StarImportApkVerificationUiState>,
    ): StarImportCandidateListUiState {
        return withContext(defaultDispatcher) {
            buildStarImportCandidateListUiState(
                candidates = candidates,
                filterInput = filterInput,
                viewFilter = viewFilter,
                qualityFilters = qualityFilters,
                conflictStrategy = conflictStrategy,
                selectedIds = selectedIds,
                verificationStates = verificationStates,
            )
        }
    }

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
            val snapshot = starImportService.loadTrackSnapshot()
            val lookupConfig = snapshot.lookupConfig
            val targetSource = request.forcedStarListUrl
                ?.let { StarImportUiSource.ListUrl }
                ?: request.source
            val targetListUrl = request.forcedStarListUrl ?: request.listUrlInput.trim()
            val targetUsername = request.usernameInput.toGitHubUsernameInput()

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
                val lists = starImportService.fetchStarLists(
                    listUrl = targetListUrl,
                    apiToken = lookupConfig.apiToken,
                )
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
            val preview = starImportService.previewStarredRepositoryImport(
                request = importRequest,
                existingItems = snapshot.items,
            )
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
            val snapshot = starImportService.loadTrackSnapshot()
            starImportService.verifyApkAssets(
                targets = uniqueTargets,
                lookupConfig = snapshot.lookupConfig,
                refreshIntervalHours = snapshot.refreshIntervalHours,
                maxConcurrency = MAX_PARALLEL_APK_VERIFICATIONS,
            )
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
            starImportService.importCandidates(
                candidates = selectedForImport,
                onRefreshNeeded = { AppBackgroundScheduler.scheduleGitHubRefresh(context) },
            )
        }
    }

    companion object {
        private const val STAR_IMPORT_PREVIEW_LIMIT = 1_000
        private const val MAX_APK_VERIFICATION_BATCH = 30
        private const val MAX_PARALLEL_APK_VERIFICATIONS = 4
    }
}
