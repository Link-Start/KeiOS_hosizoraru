package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsBranchOption
import os.kei.feature.github.model.GitHubActionsDownloadRecord
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubActionsRunTrackingPlan
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactSignal
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactsSnapshot
import os.kei.feature.github.model.GitHubActionsWorkflowMatch
import os.kei.feature.github.model.GitHubApiAuthMode
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubStrategyBenchmarkReport
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.feature.github.model.githubAssetSourceSignature
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.actions.GitHubActionsSectionExpansionState
import os.kei.ui.page.main.github.section.GitHubOverviewUiState
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseExpansionState
import os.kei.ui.page.main.github.share.GitHubPendingShareImportAttachCandidate
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.github.share.GitHubShareImportPreview
import os.kei.ui.page.main.github.share.GitHubShareImportResult
import os.kei.ui.page.main.widget.chrome.ScrollChromeVisibilityController

@Stable
internal class GitHubPageState(
    private val searchBarHideThresholdPx: Float,
    actionsSectionExpansionState: GitHubActionsSectionExpansionState = GitHubActionsSectionExpansionState(),
    overviewUiState: GitHubOverviewUiState = GitHubOverviewUiState(),
    trackedReleaseExpansionState: GitHubTrackedReleaseExpansionState = GitHubTrackedReleaseExpansionState()
) {
    var trackedSearch by mutableStateOf("")
    var trackedFilterMode by mutableStateOf(GitHubTrackedFilterMode.All)
    var repoUrlInput by mutableStateOf("")
    var packageNameInput by mutableStateOf("")
    var repoScanCandidates by mutableStateOf<List<GitHubPackageRepositoryScanCandidate>>(emptyList())
    var appSearch by mutableStateOf("")
    var addTrackAppPickerFirstVisibleItemIndex by mutableIntStateOf(0)
    var addTrackAppPickerFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    var pickerExpanded by mutableStateOf(false)
    var showAddSheet by mutableStateOf(false)
    var showStrategySheet by mutableStateOf(false)
    var showCheckLogicSheet by mutableStateOf(false)
    var showActionsSheet by mutableStateOf(false)
    var showOverviewEntrySheet by mutableStateOf(false)
    var showDownloaderPopup by mutableStateOf(false)
    var editingTrackedItem by mutableStateOf<GitHubTrackedApp?>(null)
    var actionsTargetItem by mutableStateOf<GitHubTrackedApp?>(null)
    var actionsLoading by mutableStateOf(false)
    var actionsRunsLoading by mutableStateOf(false)
    var actionsError by mutableStateOf<String?>(null)
    var actionsAuthMode by mutableStateOf<GitHubApiAuthMode?>(null)
    var actionsDefaultBranch by mutableStateOf("")
    var actionsSelectedBranch by mutableStateOf("")
    var actionsBranchManuallySelected by mutableStateOf(false)
    var actionsBranchOptions by mutableStateOf<List<GitHubActionsBranchOption>>(emptyList())
    var actionsRawWorkflows by mutableStateOf<List<GitHubActionsWorkflow>>(emptyList())
    var actionsWorkflowSignals by mutableStateOf<Map<Long, GitHubActionsWorkflowArtifactSignal>>(emptyMap())
    var actionsWorkflows by mutableStateOf<List<GitHubActionsWorkflowMatch>>(emptyList())
    var actionsSelectedWorkflowId by mutableStateOf<Long?>(null)
    var actionsWorkflowManuallySelected by mutableStateOf(false)
    var actionsSnapshot by mutableStateOf<GitHubActionsWorkflowArtifactsSnapshot?>(null)
    var actionsRuns by mutableStateOf<List<GitHubActionsRunMatch>>(emptyList())
    var actionsRunLimit by mutableStateOf(6)
    var actionsSelectedRunId by mutableStateOf<Long?>(null)
    var actionsBranchesExpanded by mutableStateOf(actionsSectionExpansionState.branchesExpanded)
    var actionsWorkflowsExpanded by mutableStateOf(actionsSectionExpansionState.workflowsExpanded)
    var actionsRunsExpanded by mutableStateOf(actionsSectionExpansionState.runsExpanded)
    var actionsArtifactsExpanded by mutableStateOf(true)
    var actionsArtifactFilter by mutableStateOf(GitHubActionsArtifactFilter.Recommended)
    var actionsDownloadHistory by mutableStateOf<List<GitHubActionsDownloadRecord>>(emptyList())
    var actionsRunTrackingPlans by mutableStateOf<Map<Long, GitHubActionsRunTrackingPlan>>(emptyMap())
    var actionsArtifactDownloadLoadingId by mutableStateOf<Long?>(null)
    var actionsArtifactShareLoadingId by mutableStateOf<Long?>(null)
    var actionsRunWatchJob by mutableStateOf<Job?>(null)
    var debugActionsUpdateNotificationLoading by mutableStateOf(false)
    var preferPreReleaseInput by mutableStateOf(false)
    var alwaysShowLatestReleaseDownloadButtonInput by mutableStateOf(false)
    var checkActionsUpdatesInput by mutableStateOf(false)
    var preciseApkVersionModeInput by mutableStateOf(GitHubTrackedPreciseApkVersionMode.FollowGlobal)
    var trackSourceModeInput by mutableStateOf(GitHubTrackedSourceMode.GitHubRepository)
    var repoUrlScanRunning by mutableStateOf(false)
    var packageNameScanRunning by mutableStateOf(false)
    var selectedApp by mutableStateOf<InstalledAppItem?>(null)
    var appList by mutableStateOf<List<InstalledAppItem>>(emptyList())
    var appListLoaded by mutableStateOf(false)
    var appListRefreshing by mutableStateOf(false)
    var hasAutoRequestedPermission by mutableStateOf(false)
    var hasInitialized by mutableStateOf(false)
    var hasActiveInitialized by mutableStateOf(false)
    var lastTrackStoreSignalVersion by mutableStateOf(0L)
    var showActionMenuPopup by mutableStateOf(false)
    var showOnlineShareTargetPopup by mutableStateOf(false)
    var downloaderPopupAnchorBounds by mutableStateOf<IntRect?>(null)
    var onlineShareTargetPopupAnchorBounds by mutableStateOf<IntRect?>(null)
    var shareImportFlowModePopupAnchorBounds by mutableStateOf<IntRect?>(null)
    var pendingTrackImportPreview by mutableStateOf<GitHubTrackImportPreview?>(null)
    var pendingShareImportPreview by mutableStateOf<GitHubShareImportPreview?>(null)
    var pendingShareImportTrack by mutableStateOf<GitHubPendingShareImportTrack?>(null)
    var pendingShareImportAttachCandidate by mutableStateOf<GitHubPendingShareImportAttachCandidate?>(null)
    var pendingShareImportResult by mutableStateOf<GitHubShareImportResult?>(null)
    var trackCardFocusRequest by mutableStateOf<GitHubTrackCardFocusRequest?>(null)
    private var nextTrackCardFocusRequestVersion by mutableIntStateOf(0)
    var decisionAssistDetailRequest by mutableStateOf<GitHubDecisionAssistDetailRequest?>(null)
    var actionsArtifactDetailRequest by mutableStateOf<GitHubActionsArtifactDetailRequest?>(null)
    var apkInfoDetailRequest by mutableStateOf<GitHubApkInfoDetailRequest?>(null)
    var managedInstallConfirmRequest by mutableStateOf<GitHubManagedInstallConfirmRequest?>(null)
    var shareImportResolving by mutableStateOf(false)
    var sortMode by mutableStateOf(GitHubSortMode.Update)
    var sortDirection by mutableStateOf(GitHubSortDirection.Forward)
    var overviewExpanded by mutableStateOf(overviewUiState.expanded)
    var overviewVisibleEntries by mutableStateOf(overviewUiState.visibleEntries)
    var pendingDeleteItem by mutableStateOf<GitHubTrackedApp?>(null)
    var overviewRefreshState by mutableStateOf(OverviewRefreshState.Idle)
    var lastRefreshMs by mutableStateOf(0L)
    var refreshIntervalHours by mutableStateOf(3)
    var refreshProgress by mutableStateOf(0f)
    var lookupConfig by mutableStateOf(GitHubLookupConfig())
    var selectedStrategyInput by mutableStateOf(lookupConfig.selectedStrategy)
    var selectedActionsStrategyInput by mutableStateOf(lookupConfig.actionsStrategy)
    var githubApiTokenInput by mutableStateOf("")
    var checkAllTrackedPreReleasesInput by mutableStateOf(false)
    var checkAllDirectApkPreReleasesInput by mutableStateOf(false)
    var aggressiveApkFilteringInput by mutableStateOf(false)
    var preciseApkVersionEnabledInput by mutableStateOf(false)
    var scanSystemAppsByDefaultInput by mutableStateOf(false)
    var profileDepthInput by mutableStateOf(GitHubProfileDepth.Basic)
    var shareImportFlowModeInput by mutableStateOf(lookupConfig.shareImportFlowMode)
    var appManagedShareInstallEnabledInput by mutableStateOf(false)
    var onlineShareTargetPackageInput by mutableStateOf("")
    var preferredDownloaderPackageInput by mutableStateOf("")
    var decisionAssistEnabledInput by mutableStateOf(false)
    var repositoryHealthCardEnabledInput by mutableStateOf(false)
    var apkTrustCheckEnabledInput by mutableStateOf(false)
    var showApiTokenPlainText by mutableStateOf(false)
    var showShareImportFlowModePopup by mutableStateOf(false)
    var strategyBenchmarkRunning by mutableStateOf(false)
    var strategyBenchmarkError by mutableStateOf<String?>(null)
    var strategyBenchmarkReport by mutableStateOf<GitHubStrategyBenchmarkReport?>(null)
    var credentialCheckRunning by mutableStateOf(false)
    var credentialCheckError by mutableStateOf<String?>(null)
    var credentialCheckStatus by mutableStateOf<GitHubApiCredentialStatus?>(null)
    var recommendedTokenGuideExpanded by mutableStateOf(false)
    var assetSourceSignature by mutableStateOf("")
    var refreshAllJob by mutableStateOf<Job?>(null)
    var deleteInProgress by mutableStateOf(false)
    var showSearchBar by mutableStateOf(true)
    private var pendingShowSearchBar: Boolean? = null
    private var canScrollBackward by mutableStateOf(false)
    private var canScrollForward by mutableStateOf(false)
    private val searchBarVisibilityController = ScrollChromeVisibilityController(searchBarHideThresholdPx)

    val trackedItems = mutableStateListOf<GitHubTrackedApp>()
    val checkStates = mutableStateMapOf<String, VersionCheckUi>()
    val apkAssetBundles = mutableStateMapOf<String, GitHubReleaseAssetBundle>()
    val apkAssetLoading = mutableStateMapOf<String, Boolean>()
    val apkAssetErrors = mutableStateMapOf<String, String>()
    val apkAssetExpanded = mutableStateMapOf<String, Boolean>()
    val apkAssetIncludeAll = mutableStateMapOf<String, Boolean>()
    val releaseNotesLoading = mutableStateMapOf<String, Boolean>()
    val releaseNotesErrors = mutableStateMapOf<String, String>()
    val releaseNotesTargets = mutableStateMapOf<String, List<GitHubReleaseNotesTarget>>()
    val releaseNotesSelectedTargets = mutableStateMapOf<String, GitHubReleaseNotesTarget>()
    val releaseNotesBundles = mutableStateMapOf<String, GitHubReleaseAssetBundle>()
    val releaseNotesApkVersions = mutableStateMapOf<String, GitHubRemoteApkVersionInfo>()
    val apkAssetBundleLoadedAtMs = mutableStateMapOf<String, Long>()
    val releaseNotesTargetsLoadedAtMs = mutableStateMapOf<String, Long>()
    val releaseNotesBundleLoadedAtMs = mutableStateMapOf<String, Long>()
    val apkInfoLoading = mutableStateMapOf<String, Boolean>()
    val apkInfoErrors = mutableStateMapOf<String, String>()
    val apkInfoResults = mutableStateMapOf<String, GitHubApkManifestInfo>()
    val apkInfoInstalledResults = mutableStateMapOf<String, GitHubInstalledPackageInfo?>()
    val managedInstallLoading = mutableStateMapOf<String, Boolean>()
    val itemRefreshLoading = mutableStateMapOf<String, Boolean>()
    val actionsStatusRefreshingRunIds = mutableStateMapOf<Long, Boolean>()
    val actionsRecommendedRunSnapshots =
        mutableStateMapOf<String, GitHubActionsRecommendedRunSnapshot>()
    val trackedCardExpanded = mutableStateMapOf<String, Boolean>()
    val trackedLocalVersionExpanded = mutableStateMapOf<String, Boolean>().apply {
        putAll(trackedReleaseExpansionState.localVersionExpanded)
    }
    val trackedStableVersionExpanded = mutableStateMapOf<String, Boolean>().apply {
        putAll(trackedReleaseExpansionState.stableVersionExpanded)
    }
    val trackedPreReleaseVersionExpanded = mutableStateMapOf<String, Boolean>().apply {
        putAll(trackedReleaseExpansionState.preReleaseVersionExpanded)
    }
    val trackedFirstInstallAtByPackage = mutableStateMapOf<String, Long>()
    val trackedAddedAtById = mutableStateMapOf<String, Long>()
    val trackedModifiedAtById = mutableStateMapOf<String, Long>()

    val addButtonScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            searchBarVisibilityController.updateWithinScrollBounds(
                deltaY = available.y,
                visible = pendingShowSearchBar ?: showSearchBar,
                canScrollBackward = canScrollBackward,
                canScrollForward = canScrollForward
            ) {
                pendingShowSearchBar = it
            }
            return Offset.Zero
        }
    }

    fun updateScrollBounds(
        canScrollBackward: Boolean,
        canScrollForward: Boolean
    ) {
        this.canScrollBackward = canScrollBackward
        this.canScrollForward = canScrollForward
    }

    fun settleScrollChromeVisibility() {
        pendingShowSearchBar?.let { target ->
            if (showSearchBar != target) {
                showSearchBar = target
            }
        }
        pendingShowSearchBar = null
        searchBarVisibilityController.reset()
    }

    fun requestTrackCardFocus(trackId: String) {
        val normalizedTrackId = trackId.trim()
        if (normalizedTrackId.isBlank()) return
        nextTrackCardFocusRequestVersion += 1
        trackCardFocusRequest = GitHubTrackCardFocusRequest(
            trackId = normalizedTrackId,
            version = nextTrackCardFocusRequestVersion
        )
    }

    fun consumeTrackCardFocus(request: GitHubTrackCardFocusRequest) {
        if (trackCardFocusRequest?.version == request.version) {
            trackCardFocusRequest = null
        }
    }

    fun activeStrategyId(): String = lookupConfig.selectedStrategy.storageId

    fun buildAssetSourceSignature(config: GitHubLookupConfig = lookupConfig): String {
        return config.githubAssetSourceSignature()
    }

    fun matchesAssetSourceSignature(
        bundle: GitHubReleaseAssetBundle,
        config: GitHubLookupConfig = lookupConfig
    ): Boolean {
        return bundle.sourceConfigSignature.isNotBlank() &&
            bundle.sourceConfigSignature == buildAssetSourceSignature(config)
    }

    fun clearAllAssetUiState() {
        apkAssetBundles.clear()
        apkAssetLoading.clear()
        apkAssetErrors.clear()
        apkAssetExpanded.clear()
        apkAssetIncludeAll.clear()
        releaseNotesLoading.clear()
        releaseNotesErrors.clear()
        releaseNotesTargets.clear()
        releaseNotesSelectedTargets.clear()
        releaseNotesBundles.clear()
        releaseNotesApkVersions.clear()
        apkAssetBundleLoadedAtMs.clear()
        releaseNotesTargetsLoadedAtMs.clear()
        releaseNotesBundleLoadedAtMs.clear()
        apkInfoLoading.clear()
        apkInfoErrors.clear()
        apkInfoResults.clear()
        apkInfoInstalledResults.clear()
    }

    fun clearAssetRuntimeState(itemId: String) {
        apkAssetExpanded.remove(itemId)
        apkAssetLoading.remove(itemId)
        apkAssetErrors.remove(itemId)
        apkAssetBundles.remove(itemId)
        releaseNotesLoading.remove(itemId)
        releaseNotesErrors.remove(itemId)
        releaseNotesTargets.remove(itemId)
        releaseNotesSelectedTargets.remove(itemId)
        releaseNotesBundles.remove(itemId)
        releaseNotesApkVersions.keys.removeAll { key -> key.startsWith("$itemId|") }
        apkAssetBundleLoadedAtMs.remove(itemId)
        releaseNotesTargetsLoadedAtMs.remove(itemId)
        releaseNotesBundleLoadedAtMs.remove(itemId)
    }

    fun clearAssetUiState(itemId: String) {
        clearAssetRuntimeState(itemId)
        apkAssetIncludeAll.remove(itemId)
    }

    fun retainTrackedUiState(validItemIds: Set<String>) {
        trackedCardExpanded.keys.retainAll(validItemIds)
        trackedLocalVersionExpanded.keys.retainAll(validItemIds)
        trackedStableVersionExpanded.keys.retainAll(validItemIds)
        trackedPreReleaseVersionExpanded.keys.retainAll(validItemIds)
        apkAssetExpanded.keys.retainAll(validItemIds)
        apkAssetIncludeAll.keys.retainAll(validItemIds)
        itemRefreshLoading.keys.retainAll(validItemIds)
        actionsRecommendedRunSnapshots.keys.retainAll(validItemIds)
        apkAssetLoading.keys.retainAll(validItemIds)
        apkAssetErrors.keys.retainAll(validItemIds)
        apkAssetBundles.keys.retainAll(validItemIds)
        releaseNotesLoading.keys.retainAll(validItemIds)
        releaseNotesErrors.keys.retainAll(validItemIds)
        releaseNotesTargets.keys.retainAll(validItemIds)
        releaseNotesSelectedTargets.keys.retainAll(validItemIds)
        releaseNotesBundles.keys.retainAll(validItemIds)
        apkAssetBundleLoadedAtMs.keys.retainAll(validItemIds)
        releaseNotesTargetsLoadedAtMs.keys.retainAll(validItemIds)
        releaseNotesBundleLoadedAtMs.keys.retainAll(validItemIds)
        releaseNotesApkVersions.keys.removeAll { key ->
            validItemIds.none { itemId -> key.startsWith("$itemId|") }
        }
    }

    fun recordTrackedFirstInstallAt(packageName: String, firstInstallAtMillis: Long) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) return
        if (firstInstallAtMillis <= 0L) return
        val current = trackedFirstInstallAtByPackage[normalizedPackageName]
        if (current == null || current <= 0L || firstInstallAtMillis < current) {
            trackedFirstInstallAtByPackage[normalizedPackageName] = firstInstallAtMillis
        }
    }

    fun retainTrackedFirstInstallAtByTrackedItems() {
        val trackedPackages = trackedItems
            .map { it.packageName.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        trackedFirstInstallAtByPackage.keys.retainAll(trackedPackages)
    }

    fun recordTrackedAddedAt(trackId: String, addedAtMillis: Long) {
        val normalizedTrackId = trackId.trim()
        if (normalizedTrackId.isBlank()) return
        if (addedAtMillis <= 0L) return
        val current = trackedAddedAtById[normalizedTrackId]
        if (current == null || current <= 0L || addedAtMillis < current) {
            trackedAddedAtById[normalizedTrackId] = addedAtMillis
        }
    }

    fun retainTrackedAddedAtByTrackedItems() {
        val trackedIds = trackedItems
            .map { it.id.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        trackedAddedAtById.keys.retainAll(trackedIds)
    }

    fun recordTrackedModifiedAt(trackId: String, modifiedAtMillis: Long) {
        val normalizedTrackId = trackId.trim()
        if (normalizedTrackId.isBlank()) return
        if (modifiedAtMillis <= 0L) return
        trackedModifiedAtById[normalizedTrackId] = modifiedAtMillis
    }

    fun retainTrackedModifiedAtByTrackedItems() {
        val trackedIds = trackedItems
            .map { it.id.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        trackedModifiedAtById.keys.retainAll(trackedIds)
    }

    fun dismissStrategySheet() {
        showStrategySheet = false
        showApiTokenPlainText = false
        credentialCheckRunning = false
        recommendedTokenGuideExpanded = false
    }

    fun dismissCheckLogicSheet() {
        showDownloaderPopup = false
        showOnlineShareTargetPopup = false
        showShareImportFlowModePopup = false
        pendingTrackImportPreview = null
        showCheckLogicSheet = false
    }

    fun resetActionsSheetState() {
        actionsRunWatchJob?.cancel()
        actionsRunWatchJob = null
        actionsLoading = false
        actionsRunsLoading = false
        actionsError = null
        actionsAuthMode = null
        actionsDefaultBranch = ""
        actionsSelectedBranch = ""
        actionsBranchManuallySelected = false
        actionsBranchOptions = emptyList()
        actionsRawWorkflows = emptyList()
        actionsWorkflowSignals = emptyMap()
        actionsWorkflows = emptyList()
        actionsSelectedWorkflowId = null
        actionsWorkflowManuallySelected = false
        actionsSnapshot = null
        actionsRuns = emptyList()
        actionsRunLimit = 6
        actionsSelectedRunId = null
        actionsArtifactsExpanded = true
        actionsArtifactFilter = GitHubActionsArtifactFilter.Recommended
        actionsDownloadHistory = emptyList()
        actionsRunTrackingPlans = emptyMap()
        actionsArtifactDownloadLoadingId = null
        actionsArtifactShareLoadingId = null
        actionsArtifactDetailRequest = null
        actionsStatusRefreshingRunIds.clear()
    }

    fun dismissActionsSheet() {
        showActionsSheet = false
        actionsTargetItem = null
        resetActionsSheetState()
    }

    fun dismissTrackImportPreview() {
        pendingTrackImportPreview = null
    }

    fun dismissShareImportPreview() {
        pendingShareImportPreview = null
    }

    fun resetTrackEditor() {
        editingTrackedItem = null
        repoUrlInput = ""
        packageNameInput = ""
        repoScanCandidates = emptyList()
        selectedApp = null
        appSearch = ""
        pickerExpanded = false
        preferPreReleaseInput = false
        alwaysShowLatestReleaseDownloadButtonInput = false
        checkActionsUpdatesInput = false
        preciseApkVersionModeInput = GitHubTrackedPreciseApkVersionMode.FollowGlobal
        trackSourceModeInput = GitHubTrackedSourceMode.GitHubRepository
        repoUrlScanRunning = false
        packageNameScanRunning = false
    }

    fun dismissTrackSheet() {
        showAddSheet = false
        resetTrackEditor()
    }
}

internal data class GitHubApkInfoDetailRequest(
    val item: GitHubTrackedApp,
    val asset: GitHubReleaseAssetFile
)

internal data class GitHubManagedInstallConfirmRequest(
    val item: GitHubTrackedApp,
    val asset: GitHubReleaseAssetFile
)

internal fun GitHubTrackedApp.githubManagedInstallKey(asset: GitHubReleaseAssetFile): String {
    return listOf(
        id,
        owner,
        repo,
        asset.name,
        asset.downloadUrl,
        asset.apiAssetUrl,
        asset.sizeBytes.toString(),
        asset.digest
    ).joinToString("|")
}

internal enum class GitHubDecisionAssistDetailType {
    RepositoryHealth,
    ReleaseNotes
}

internal enum class GitHubActionsArtifactFilter {
    Recommended,
    Alternatives,
    All
}

internal data class GitHubDecisionAssistDetailRequest(
    val type: GitHubDecisionAssistDetailType,
    val item: GitHubTrackedApp
)

internal data class GitHubActionsArtifactDetailRequest(
    val runMatch: GitHubActionsRunMatch,
    val artifactMatch: GitHubActionsArtifactMatch,
    val recommended: Boolean
)

internal fun GitHubReleaseAssetFile.githubApkInfoKey(): String {
    return listOf(name, downloadUrl, apiAssetUrl, sizeBytes.toString()).joinToString("|")
}

internal fun releaseNotesApkVersionKey(itemId: String, target: GitHubReleaseNotesTarget): String {
    return listOf(
        itemId.trim(),
        target.tagName.trim(),
        target.htmlUrl.trim()
    ).joinToString("|")
}

@Composable
internal fun rememberGitHubPageState(viewModel: GitHubPageViewModel): GitHubPageState {
    val density = LocalDensity.current
    val searchBarHideThresholdPx = remember(density) {
        with(density) { 28.dp.toPx() }
    }
    return remember(viewModel, searchBarHideThresholdPx) {
        viewModel.pageState(searchBarHideThresholdPx)
    }
}
