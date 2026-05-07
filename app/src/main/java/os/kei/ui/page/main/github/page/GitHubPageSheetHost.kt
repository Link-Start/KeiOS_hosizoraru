package os.kei.ui.page.main.github.page

import android.content.Context
import androidx.compose.runtime.Composable
import os.kei.ui.page.main.github.actions.GitHubActionsSheet
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.sheet.GitHubActionsArtifactDetailSheet
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheet
import os.kei.ui.page.main.github.sheet.GitHubCheckLogicSheet
import os.kei.ui.page.main.github.sheet.GitHubDecisionAssistDetailSheet
import os.kei.ui.page.main.github.sheet.GitHubDeleteTrackDialog
import os.kei.ui.page.main.github.sheet.GitHubStrategySheet
import os.kei.ui.page.main.github.sheet.GitHubTrackEditSheet
import os.kei.ui.page.main.github.sheet.GitHubTrackImportDialog
import os.kei.ui.page.main.host.pager.MainPageBackdropSet

@Composable
internal fun GitHubPageSheetHost(
    context: Context,
    backdrops: MainPageBackdropSet,
    state: GitHubPageState,
    actions: GitHubPageActions,
    contentDerivedState: GitHubPageContentDerivedState,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    checkLogicDownloaderOptions: List<DownloaderOption>,
    hasKeiOsSelfTrack: Boolean,
    tracksExporting: Boolean,
    tracksImporting: Boolean,
    onEnsureKeiOsSelfTrack: () -> Unit,
    onExportTrackedItems: () -> Unit,
    onImportTrackedItems: () -> Unit,
    onOpenStarImport: () -> Unit,
    onConfirmTrackImport: () -> Unit
) {
    GitHubStrategySheet(
        show = state.showStrategySheet,
        backdrop = backdrops.sheet,
        lookupConfig = state.lookupConfig,
        selectedStrategyInput = state.selectedStrategyInput,
        selectedActionsStrategyInput = state.selectedActionsStrategyInput,
        githubApiTokenInput = state.githubApiTokenInput,
        showApiTokenPlainText = state.showApiTokenPlainText,
        credentialCheckRunning = state.credentialCheckRunning,
        credentialCheckError = state.credentialCheckError,
        credentialCheckStatus = state.credentialCheckStatus,
        strategyBenchmarkRunning = state.strategyBenchmarkRunning,
        strategyBenchmarkError = state.strategyBenchmarkError,
        strategyBenchmarkReport = state.strategyBenchmarkReport,
        trackedCount = contentDerivedState.trackedUi.overviewMetrics.trackedCount,
        recommendedTokenGuideExpanded = state.recommendedTokenGuideExpanded,
        onDismissRequest = actions::closeStrategySheet,
        onApply = actions::applyLookupConfig,
        onSelectedStrategyChange = { state.selectedStrategyInput = it },
        onSelectedActionsStrategyChange = { state.selectedActionsStrategyInput = it },
        onTokenInputChange = {
            state.githubApiTokenInput = it
            state.credentialCheckError = null
            state.credentialCheckStatus = null
        },
        onToggleTokenVisibility = { state.showApiTokenPlainText = !state.showApiTokenPlainText },
        onRunCredentialCheck = actions::runCredentialCheck,
        onRunStrategyBenchmark = actions::runStrategyBenchmark,
        onRecommendedTokenGuideExpandedChange = { state.recommendedTokenGuideExpanded = it },
        onOpenExternalUrl = { url, failureMessage ->
            actions.openExternalUrl(url = url, failureMessage = failureMessage)
        }
    )

    GitHubCheckLogicSheet(
        show = state.showCheckLogicSheet,
        backdrop = backdrops.sheet,
        lookupConfig = state.lookupConfig,
        trackedCount = contentDerivedState.trackedUi.overviewMetrics.trackedCount,
        refreshIntervalHours = state.refreshIntervalHours,
        refreshIntervalHoursInput = state.refreshIntervalHoursInput,
        checkAllTrackedPreReleasesInput = state.checkAllTrackedPreReleasesInput,
        aggressiveApkFilteringInput = state.aggressiveApkFilteringInput,
        preciseApkVersionEnabledInput = state.preciseApkVersionEnabledInput,
        shareImportLinkageEnabledInput = state.shareImportLinkageEnabledInput,
        shareImportFlowModeInput = state.shareImportFlowModeInput,
        onlineShareTargetPackageInput = state.onlineShareTargetPackageInput,
        preferredDownloaderPackageInput = state.preferredDownloaderPackageInput,
        decisionAssistEnabledInput = state.decisionAssistEnabledInput,
        repositoryHealthCardEnabledInput = state.repositoryHealthCardEnabledInput,
        apkTrustCheckEnabledInput = state.apkTrustCheckEnabledInput,
        releaseNotesModeInput = state.releaseNotesModeInput,
        installedOnlineShareTargets = installedOnlineShareTargets,
        showCheckLogicIntervalPopup = state.showCheckLogicIntervalPopup,
        showDownloaderPopup = state.showDownloaderPopup,
        showOnlineShareTargetPopup = state.showOnlineShareTargetPopup,
        showShareImportFlowModePopup = state.showShareImportFlowModePopup,
        showReleaseNotesModePopup = state.showReleaseNotesModePopup,
        checkLogicIntervalPopupAnchorBounds = state.checkLogicIntervalPopupAnchorBounds,
        downloaderPopupAnchorBounds = state.downloaderPopupAnchorBounds,
        onlineShareTargetPopupAnchorBounds = state.onlineShareTargetPopupAnchorBounds,
        shareImportFlowModePopupAnchorBounds = state.shareImportFlowModePopupAnchorBounds,
        releaseNotesModePopupAnchorBounds = state.releaseNotesModePopupAnchorBounds,
        downloaderOptions = checkLogicDownloaderOptions,
        hasKeiOsSelfTrack = hasKeiOsSelfTrack,
        exportInProgress = tracksExporting,
        importInProgress = tracksImporting,
        onDismissRequest = actions::closeCheckLogicSheet,
        onApply = { actions.applyCheckLogicSheet(installedOnlineShareTargets) },
        onEnsureKeiOsSelfTrack = onEnsureKeiOsSelfTrack,
        onExportTrackedItems = onExportTrackedItems,
        onImportTrackedItems = onImportTrackedItems,
        onOpenStarImport = onOpenStarImport,
        onRefreshIntervalHoursInputChange = { state.refreshIntervalHoursInput = it },
        onCheckAllTrackedPreReleasesInputChange = { state.checkAllTrackedPreReleasesInput = it },
        onAggressiveApkFilteringInputChange = { state.aggressiveApkFilteringInput = it },
        onPreciseApkVersionEnabledInputChange = { state.preciseApkVersionEnabledInput = it },
        onShareImportLinkageEnabledInputChange = { state.shareImportLinkageEnabledInput = it },
        onShareImportFlowModeInputChange = { state.shareImportFlowModeInput = it },
        onPreferredDownloaderPackageInputChange = { state.preferredDownloaderPackageInput = it },
        onOnlineShareTargetPackageInputChange = { state.onlineShareTargetPackageInput = it },
        onDecisionAssistEnabledInputChange = { state.decisionAssistEnabledInput = it },
        onRepositoryHealthCardEnabledInputChange = { state.repositoryHealthCardEnabledInput = it },
        onApkTrustCheckEnabledInputChange = { state.apkTrustCheckEnabledInput = it },
        onReleaseNotesModeInputChange = { state.releaseNotesModeInput = it },
        onShowCheckLogicIntervalPopupChange = { state.showCheckLogicIntervalPopup = it },
        onShowDownloaderPopupChange = { state.showDownloaderPopup = it },
        onShowOnlineShareTargetPopupChange = { state.showOnlineShareTargetPopup = it },
        onShowShareImportFlowModePopupChange = { state.showShareImportFlowModePopup = it },
        onShowReleaseNotesModePopupChange = { state.showReleaseNotesModePopup = it },
        onCheckLogicIntervalPopupAnchorBoundsChange = {
            state.checkLogicIntervalPopupAnchorBounds = it
        },
        onDownloaderPopupAnchorBoundsChange = { state.downloaderPopupAnchorBounds = it },
        onOnlineShareTargetPopupAnchorBoundsChange = {
            state.onlineShareTargetPopupAnchorBounds = it
        },
        onShareImportFlowModePopupAnchorBoundsChange = {
            state.shareImportFlowModePopupAnchorBounds = it
        },
        onReleaseNotesModePopupAnchorBoundsChange = {
            state.releaseNotesModePopupAnchorBounds = it
        }
    )

    GitHubActionsSheet(
        show = state.showActionsSheet,
        backdrop = backdrops.sheet,
        state = state,
        onDismissRequest = actions::closeActionsSheet,
        onRefresh = actions::refreshActionsSheet,
        onSelectWorkflow = actions::selectActionsWorkflow,
        onSelectBranch = actions::selectActionsBranch,
        onSelectRun = actions::selectActionsRun,
        onLoadMoreRuns = actions::loadMoreActionsRuns,
        onBranchesExpandedChange = actions::setActionsBranchesExpanded,
        onWorkflowsExpandedChange = actions::setActionsWorkflowsExpanded,
        onRunsExpandedChange = actions::setActionsRunsExpanded,
        onArtifactsExpandedChange = { state.actionsArtifactsExpanded = it },
        onRefreshRun = actions::refreshActionsRunStatus,
        onDownloadArtifact = actions::downloadActionsArtifact,
        onShareArtifact = actions::shareActionsArtifact,
        onOpenRun = actions::openSelectedActionsRun,
        onOpenArtifactDetail = { runMatch, artifactMatch, recommended ->
            state.actionsArtifactDetailRequest = GitHubActionsArtifactDetailRequest(
                runMatch = runMatch,
                artifactMatch = artifactMatch,
                recommended = recommended
            )
        }
    )

    GitHubDecisionAssistDetailSheet(
        request = state.decisionAssistDetailRequest,
        backdrop = backdrops.sheet,
        versionState = state.decisionAssistDetailRequest?.item?.id
            ?.let { state.checkStates[it] }
            ?: os.kei.ui.page.main.github.VersionCheckUi(),
        assetBundle = state.decisionAssistDetailRequest?.item?.id
            ?.let { state.apkAssetBundles[it] },
        assetLoading = state.decisionAssistDetailRequest
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
            ?.item
            ?.id
            ?.let { state.releaseNotesLoading[it] == true } == true,
        assetError = state.decisionAssistDetailRequest
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
            ?.item
            ?.id
            ?.let { state.releaseNotesErrors[it] }
            .orEmpty(),
        onDismissRequest = { state.decisionAssistDetailRequest = null },
        onRefreshHealth = { item -> actions.refreshTrackedItem(item, showToastOnError = true) },
        onRefreshReleaseNotes = { item, itemState ->
            actions.loadReleaseNotes(
                item = item,
                itemState = itemState,
                clearCache = true
            )
        },
        onOpenExternalUrl = { url, failureMessage ->
            actions.openExternalUrl(url = url, failureMessage = failureMessage)
        }
    )

    GitHubActionsArtifactDetailSheet(
        request = state.actionsArtifactDetailRequest,
        backdrop = backdrops.sheet,
        hasToken = state.lookupConfig.actionsArtifactDownloadsAvailable,
        downloading = state.actionsArtifactDetailRequest?.artifactMatch?.artifact?.id
            ?.let { state.actionsArtifactDownloadLoadingId == it } == true,
        sharing = state.actionsArtifactDetailRequest?.artifactMatch?.artifact?.id
            ?.let { state.actionsArtifactShareLoadingId == it } == true,
        onDismissRequest = { state.actionsArtifactDetailRequest = null },
        onRefreshRun = actions::refreshActionsRunStatus,
        onDownload = actions::downloadActionsArtifact,
        onShare = actions::shareActionsArtifact
    )

    val apkInfoAsset = state.apkInfoDetailRequest
    val apkInfoKey = apkInfoAsset?.githubApkInfoKey().orEmpty()
    GitHubApkInfoSheet(
        asset = apkInfoAsset,
        info = state.apkInfoResults[apkInfoKey],
        installedInfo = state.apkInfoInstalledResults[apkInfoKey],
        loading = state.apkInfoLoading[apkInfoKey] == true,
        error = state.apkInfoErrors[apkInfoKey].orEmpty(),
        backdrop = backdrops.sheet,
        onRefresh = { apkInfoAsset?.let(actions::refreshApkInfo) },
        onDownload = { apkInfoAsset?.let(actions::openApkInDownloader) },
        onShare = { apkInfoAsset?.let(actions::shareApkLink) },
        onDismissRequest = { state.apkInfoDetailRequest = null }
    )

    GitHubTrackEditSheet(
        show = state.showAddSheet,
        backdrop = backdrops.sheet,
        editingTrackedItem = state.editingTrackedItem,
        repoUrlInput = state.repoUrlInput,
        appSearch = state.appSearch,
        packageNameInput = state.packageNameInput,
        repoUrlScanRunning = state.repoUrlScanRunning,
        packageNameScanRunning = state.packageNameScanRunning,
        pickerExpanded = state.pickerExpanded,
        selectedApp = state.selectedApp,
        appList = state.appList,
        preferPreReleaseInput = state.preferPreReleaseInput,
        alwaysShowLatestReleaseDownloadButtonInput = state.alwaysShowLatestReleaseDownloadButtonInput,
        onDismissRequest = actions::dismissTrackSheet,
        onApply = actions::applyTrackSheet,
        onRepoUrlInputChange = { state.repoUrlInput = it },
        onAppSearchChange = { state.appSearch = it },
        onPackageNameInputChange = { input ->
            state.packageNameInput = input
            val selected = state.selectedApp
            val normalizedInput = input.trim()
            if (selected != null) {
                if (normalizedInput.isBlank()) {
                    state.selectedApp = null
                } else if (!selected.packageName.equals(normalizedInput, ignoreCase = true)) {
                    state.selectedApp = null
                }
            }
        },
        onScanRepoUrl = actions::scanRepoUrlFromPackage,
        onScanPackageName = actions::scanPackageNameFromRepo,
        onPickerExpandedChange = { state.pickerExpanded = it },
        onSelectedAppChange = { app ->
            state.selectedApp = app
            if (app != null) {
                state.packageNameInput = app.packageName
            }
        },
        onPreferPreReleaseInputChange = { state.preferPreReleaseInput = it },
        onAlwaysShowLatestReleaseDownloadButtonInputChange = {
            state.alwaysShowLatestReleaseDownloadButtonInput = it
        }
    )

    GitHubDeleteTrackDialog(
        pendingDeleteItem = state.pendingDeleteItem,
        deleteInProgress = state.deleteInProgress,
        onDismissRequest = { state.pendingDeleteItem = null },
        onCancel = {
            if (!state.deleteInProgress) {
                state.pendingDeleteItem = null
            }
        },
        onConfirmDelete = actions::confirmDeletePendingItem
    )

    GitHubTrackImportDialog(
        preview = state.pendingTrackImportPreview,
        importInProgress = tracksImporting,
        onDismissRequest = {
            if (!tracksImporting) {
                state.dismissTrackImportPreview()
            }
        },
        onCancel = {
            if (!tracksImporting) {
                state.dismissTrackImportPreview()
            }
        },
        onConfirmImport = {
            val preview = state.pendingTrackImportPreview
            if (preview == null) return@GitHubTrackImportDialog
            if (!preview.canImport) {
                state.dismissTrackImportPreview()
                return@GitHubTrackImportDialog
            }
            if (tracksImporting) return@GitHubTrackImportDialog
            onConfirmTrackImport()
        }
    )

}
