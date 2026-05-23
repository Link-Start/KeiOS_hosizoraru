@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import os.kei.feature.github.data.local.GitHubAppPickerPreferences
import os.kei.ui.page.main.github.actions.GitHubActionsSheet
import os.kei.ui.page.main.github.actions.GitHubActionsSheetUiState
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerDerivedState
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerInput
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetInput
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetUiState
import os.kei.ui.page.main.github.sheet.GitHubCheckLogicSheet
import os.kei.ui.page.main.github.sheet.GitHubDeleteTrackDialog
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetInput
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetUiState
import os.kei.ui.page.main.github.sheet.GitHubOverviewEntrySheet
import os.kei.ui.page.main.github.sheet.GitHubReleaseNotesDetailInput
import os.kei.ui.page.main.github.sheet.GitHubReleaseNotesDetailUiState
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
    appPickerDerivedState: GitHubTrackAppPickerDerivedState,
    appPickerPreferences: GitHubAppPickerPreferences,
    apkInfoSheetState: GitHubApkInfoSheetUiState,
    actionsSheetState: GitHubActionsSheetUiState,
    releaseNotesDetailState: GitHubReleaseNotesDetailUiState,
    managedInstallConfirmSheetState: GitHubManagedInstallConfirmSheetUiState,
    hasKeiOsSelfTrack: Boolean,
    tracksExporting: Boolean,
    tracksImporting: Boolean,
    onEnsureKeiOsSelfTrack: () -> Unit,
    onRequestAppPickerState: (GitHubTrackAppPickerInput) -> Unit,
    onAppPickerPreferencesChange: (GitHubAppPickerPreferences) -> Unit,
    onRequestApkInfoSheetState: (GitHubApkInfoSheetInput) -> Unit,
    onApkInfoSearchQueryChange: (String) -> Unit,
    onClearApkInfoSheetState: () -> Unit,
    onRequestReleaseNotesDetailState: (GitHubReleaseNotesDetailInput) -> Unit,
    onClearReleaseNotesDetailState: () -> Unit,
    onRequestManagedInstallConfirmSheetState: (GitHubManagedInstallConfirmSheetInput) -> Unit,
    onClearManagedInstallConfirmSheetState: () -> Unit,
    onConfirmTrackImport: () -> Unit,
) {
    val trackedPackageNames =
        remember(state.trackedItems) {
            state.trackedItems.map { item -> item.packageName }.toSet()
        }

    GitHubOverviewEntrySheet(
        show = state.showOverviewEntrySheet,
        backdrop = backdrops.sheet,
        visibleEntries = state.overviewVisibleEntries,
        onEntryVisibleChange = actions::setOverviewEntryVisible,
        onReset = actions::resetOverviewEntries,
        onDismissRequest = actions::closeOverviewEntrySheet,
    )

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
        onSelectedStrategyChange = actions::setSelectedStrategyInput,
        onSelectedActionsStrategyChange = actions::setSelectedActionsStrategyInput,
        onTokenInputChange = actions::setApiTokenInput,
        onToggleTokenVisibility = actions::toggleApiTokenVisibility,
        onRunCredentialCheck = actions::runCredentialCheck,
        onRunStrategyBenchmark = actions::runStrategyBenchmark,
        onRecommendedTokenGuideExpandedChange = actions::setRecommendedTokenGuideExpanded,
        onOpenExternalUrl = { url, failureMessage ->
            actions.openExternalUrl(url = url, failureMessage = failureMessage)
        },
    )

    GitHubCheckLogicSheet(
        show = state.showCheckLogicSheet,
        backdrop = backdrops.sheet,
        lookupConfig = state.lookupConfig,
        trackedCount = contentDerivedState.trackedUi.overviewMetrics.trackedCount,
        refreshIntervalHours = state.refreshIntervalHours,
        checkAllTrackedPreReleasesInput = state.checkAllTrackedPreReleasesInput,
        checkAllDirectApkPreReleasesInput = state.checkAllDirectApkPreReleasesInput,
        aggressiveApkFilteringInput = state.aggressiveApkFilteringInput,
        preciseApkVersionEnabledInput = state.preciseApkVersionEnabledInput,
        scanSystemAppsByDefaultInput = state.scanSystemAppsByDefaultInput,
        profileDepthInput = state.profileDepthInput,
        shareImportFlowModeInput = state.shareImportFlowModeInput,
        appManagedShareInstallEnabledInput = state.appManagedShareInstallEnabledInput,
        onlineShareTargetPackageInput = state.onlineShareTargetPackageInput,
        preferredDownloaderPackageInput = state.preferredDownloaderPackageInput,
        decisionAssistEnabledInput = state.decisionAssistEnabledInput,
        repositoryHealthCardEnabledInput = state.repositoryHealthCardEnabledInput,
        apkTrustCheckEnabledInput = state.apkTrustCheckEnabledInput,
        installedOnlineShareTargets = installedOnlineShareTargets,
        showDownloaderPopup = state.showDownloaderPopup,
        showOnlineShareTargetPopup = state.showOnlineShareTargetPopup,
        showShareImportFlowModePopup = state.showShareImportFlowModePopup,
        downloaderPopupAnchorBounds = state.downloaderPopupAnchorBounds,
        onlineShareTargetPopupAnchorBounds = state.onlineShareTargetPopupAnchorBounds,
        shareImportFlowModePopupAnchorBounds = state.shareImportFlowModePopupAnchorBounds,
        downloaderOptions = checkLogicDownloaderOptions,
        hasKeiOsSelfTrack = hasKeiOsSelfTrack,
        exportInProgress = tracksExporting,
        importInProgress = tracksImporting,
        debugActionsUpdateNotificationLoading = state.debugActionsUpdateNotificationLoading,
        onDismissRequest = actions::closeCheckLogicSheet,
        onApply = { actions.applyCheckLogicSheet(installedOnlineShareTargets) },
        onEnsureKeiOsSelfTrack = onEnsureKeiOsSelfTrack,
        onSendDebugActionsUpdateNotification = actions::sendDebugActionsUpdateNotification,
        onCheckAllTrackedPreReleasesInputChange = actions::setCheckAllTrackedPreReleasesInput,
        onCheckAllDirectApkPreReleasesInputChange = actions::setCheckAllDirectApkPreReleasesInput,
        onAggressiveApkFilteringInputChange = actions::setAggressiveApkFilteringInput,
        onPreciseApkVersionEnabledInputChange = actions::setPreciseApkVersionEnabledInput,
        onScanSystemAppsByDefaultInputChange = actions::setScanSystemAppsByDefaultInput,
        onProfileDepthInputChange = actions::setProfileDepthInput,
        onShareImportFlowModeInputChange = actions::setShareImportFlowModeInput,
        onAppManagedShareInstallEnabledInputChange = actions::setAppManagedShareInstallEnabledInput,
        onPreferredDownloaderPackageInputChange = actions::setPreferredDownloaderPackageInput,
        onOnlineShareTargetPackageInputChange = actions::setOnlineShareTargetPackageInput,
        onDecisionAssistEnabledInputChange = actions::setDecisionAssistEnabledInput,
        onRepositoryHealthCardEnabledInputChange = actions::setRepositoryHealthCardEnabledInput,
        onApkTrustCheckEnabledInputChange = actions::setApkTrustCheckEnabledInput,
        onShowDownloaderPopupChange = actions::setShowDownloaderPopup,
        onShowOnlineShareTargetPopupChange = actions::setShowOnlineShareTargetPopup,
        onShowShareImportFlowModePopupChange = actions::setShowShareImportFlowModePopup,
        onDownloaderPopupAnchorBoundsChange = actions::setDownloaderPopupAnchorBounds,
        onOnlineShareTargetPopupAnchorBoundsChange = actions::setOnlineShareTargetPopupAnchorBounds,
        onShareImportFlowModePopupAnchorBoundsChange = actions::setShareImportFlowModePopupAnchorBounds,
    )

    GitHubActionsSheet(
        show = state.showActionsSheet,
        backdrop = backdrops.sheet,
        state = state,
        derivedState = actionsSheetState,
        onDismissRequest = actions::closeActionsSheet,
        onRefresh = actions::refreshActionsSheet,
        onSelectWorkflow = actions::selectActionsWorkflow,
        onSelectBranch = actions::selectActionsBranch,
        onSelectRun = actions::selectActionsRun,
        onLoadMoreRuns = actions::loadMoreActionsRuns,
        onBranchesExpandedChange = actions::setActionsBranchesExpanded,
        onWorkflowsExpandedChange = actions::setActionsWorkflowsExpanded,
        onRunsExpandedChange = actions::setActionsRunsExpanded,
        onArtifactsExpandedChange = actions::setActionsArtifactsExpanded,
        onArtifactFilterChange = actions::setActionsArtifactFilter,
        onRefreshRun = actions::refreshActionsRunStatus,
        onInstallArtifact = actions::installActionsArtifact,
        onDownloadArtifact = actions::downloadActionsArtifact,
        onShareArtifact = actions::shareActionsArtifact,
        onOpenRun = actions::openSelectedActionsRun,
        onOpenArtifactDetail = actions::openActionsArtifactDetail,
    )

    GitHubDecisionAssistSheetBinding(
        state = state,
        actions = actions,
        backdrop = backdrops.sheet,
        releaseNotesDetailState = releaseNotesDetailState,
        onRequestReleaseNotesDetailState = onRequestReleaseNotesDetailState,
        onClearReleaseNotesDetailState = onClearReleaseNotesDetailState,
    )

    GitHubActionsArtifactDetailSheetBinding(
        state = state,
        actions = actions,
        backdrop = backdrops.sheet,
    )

    GitHubApkInfoSheetBinding(
        state = state,
        actions = actions,
        backdrop = backdrops.sheet,
        sheetState = apkInfoSheetState,
        onRequestSheetState = onRequestApkInfoSheetState,
        onSearchQueryChange = onApkInfoSearchQueryChange,
        onClearSheetState = onClearApkInfoSheetState,
    )

    GitHubManagedInstallConfirmSheetBinding(
        state = state,
        actions = actions,
        backdrop = backdrops.sheet,
        sheetState = managedInstallConfirmSheetState,
        onRequestSheetState = onRequestManagedInstallConfirmSheetState,
        onClearSheetState = onClearManagedInstallConfirmSheetState,
    )

    GitHubTrackEditSheet(
        show = state.showAddSheet,
        backdrop = backdrops.sheet,
        editingTrackedItem = state.editingTrackedItem,
        repoUrlInput = state.repoUrlInput,
        repoScanCandidates = state.repoScanCandidates,
        appSearch = state.appSearch,
        packageNameInput = state.packageNameInput,
        repoUrlScanRunning = state.repoUrlScanRunning,
        packageNameScanRunning = state.packageNameScanRunning,
        pickerExpanded = state.pickerExpanded,
        selectedApp = state.selectedApp,
        appList = state.appList,
        appPickerDerivedState = appPickerDerivedState,
        appPickerPreferences = appPickerPreferences,
        trackedPackageNames = trackedPackageNames,
        appListRefreshing = state.appListRefreshing,
        addAppPickerRememberedFirstVisibleItemIndex = state.addTrackAppPickerFirstVisibleItemIndex,
        addAppPickerRememberedFirstVisibleItemScrollOffset =
            state.addTrackAppPickerFirstVisibleItemScrollOffset,
        sourceModeInput = state.trackSourceModeInput,
        preferPreReleaseInput = state.preferPreReleaseInput,
        alwaysShowLatestReleaseDownloadButtonInput = state.alwaysShowLatestReleaseDownloadButtonInput,
        checkActionsUpdatesInput = state.checkActionsUpdatesInput,
        updateIntervalModeInput = state.updateIntervalModeInput,
        actionsUpdateIntervalModeInput = state.actionsUpdateIntervalModeInput,
        preciseApkVersionModeInput = state.preciseApkVersionModeInput,
        sourceModeDropdownExpanded = state.sourceModeDropdownExpanded,
        sourceModeDropdownAnchorBounds = state.sourceModeDropdownAnchorBounds,
        updateIntervalDropdownExpanded = state.updateIntervalDropdownExpanded,
        updateIntervalDropdownAnchorBounds = state.updateIntervalDropdownAnchorBounds,
        actionsIntervalDropdownExpanded = state.actionsIntervalDropdownExpanded,
        actionsIntervalDropdownAnchorBounds = state.actionsIntervalDropdownAnchorBounds,
        preciseModeDropdownExpanded = state.preciseModeDropdownExpanded,
        preciseModeDropdownAnchorBounds = state.preciseModeDropdownAnchorBounds,
        globalRefreshIntervalHours = state.refreshIntervalHours,
        globalPreciseApkVersionEnabled = state.lookupConfig.preciseApkVersionEnabled,
        onDismissRequest = actions::dismissTrackSheet,
        onApply = actions::applyTrackSheet,
        onRepoUrlInputChange = actions::setTrackRepoUrlInput,
        onSourceModeInputChange = actions::setTrackSourceModeInput,
        onAppSearchChange = actions::setTrackAppSearch,
        onPackageNameInputChange = actions::setTrackPackageNameInput,
        onScanRepoUrl = actions::scanRepoUrlFromPackage,
        onScanPackageName = actions::scanPackageNameFromRepo,
        onRepoScanCandidateSelected = actions::selectRepoScanCandidate,
        onPickerExpandedChange = actions::setTrackAppPickerExpanded,
        onRefreshAppList = actions::refreshTrackAppList,
        onRequestAppPickerState = onRequestAppPickerState,
        onAppPickerPreferencesChange = onAppPickerPreferencesChange,
        onAddAppPickerScrollPositionChange = actions::setTrackAppPickerScrollPosition,
        onSelectedAppChange = actions::setTrackSelectedApp,
        onPreferPreReleaseInputChange = actions::setTrackPreferPreReleaseInput,
        onAlwaysShowLatestReleaseDownloadButtonInputChange =
            actions::setTrackAlwaysShowLatestReleaseDownloadButtonInput,
        onCheckActionsUpdatesInputChange = actions::setTrackCheckActionsUpdatesInput,
        onUpdateIntervalModeInputChange = actions::setTrackUpdateIntervalModeInput,
        onActionsUpdateIntervalModeInputChange = actions::setTrackActionsUpdateIntervalModeInput,
        onPreciseApkVersionModeInputChange = actions::setTrackPreciseApkVersionModeInput,
        onSourceModeDropdownExpandedChange = actions::setTrackSourceModeDropdownExpanded,
        onSourceModeDropdownAnchorBoundsChange = actions::setTrackSourceModeDropdownAnchorBounds,
        onUpdateIntervalDropdownExpandedChange = actions::setTrackUpdateIntervalDropdownExpanded,
        onUpdateIntervalDropdownAnchorBoundsChange = actions::setTrackUpdateIntervalDropdownAnchorBounds,
        onActionsIntervalDropdownExpandedChange = actions::setTrackActionsIntervalDropdownExpanded,
        onActionsIntervalDropdownAnchorBoundsChange = actions::setTrackActionsIntervalDropdownAnchorBounds,
        onPreciseModeDropdownExpandedChange = actions::setTrackPreciseModeDropdownExpanded,
        onPreciseModeDropdownAnchorBoundsChange = actions::setTrackPreciseModeDropdownAnchorBounds,
    )

    GitHubDeleteTrackDialog(
        pendingDeleteItem = state.pendingDeleteItem,
        deleteInProgress = state.deleteInProgress,
        onDismissRequest = actions::dismissPendingDeleteItem,
        onCancel = actions::dismissPendingDeleteItem,
        onConfirmDelete = actions::confirmDeletePendingItem,
    )

    GitHubTrackImportDialog(
        preview = state.pendingTrackImportPreview,
        importInProgress = tracksImporting,
        onDismissRequest = {
            if (!tracksImporting) {
                actions.dismissTrackImportPreview()
            }
        },
        onCancel = {
            if (!tracksImporting) {
                actions.dismissTrackImportPreview()
            }
        },
        onConfirmImport = {
            actions.confirmTrackImportPreview(
                importInProgress = tracksImporting,
                onConfirmTrackImport = onConfirmTrackImport,
            )
        },
    )
}
