@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import os.kei.feature.github.data.local.GitHubAppPickerPreferences
import os.kei.feature.github.model.FdroidRepositoryPresets
import os.kei.ui.page.main.github.actions.GitHubActionsSheet
import os.kei.ui.page.main.github.actions.GitHubActionsSheetUiState
import os.kei.ui.page.main.github.install.GitHubApkInfoSheetBinding
import os.kei.ui.page.main.github.install.GitHubManagedInstallConfirmSheetBinding
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerDerivedState
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerInput
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetInput
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheetUiState
import os.kei.ui.page.main.github.sheet.GitHubCheckLogicSheet
import os.kei.ui.page.main.github.sheet.GitHubDebugSheet
import os.kei.ui.page.main.github.sheet.GitHubDeleteTrackDialog
import os.kei.ui.page.main.github.sheet.GitHubDroidSourcesSheet
import os.kei.ui.page.main.github.sheet.GitHubFdroidDetailSheet
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetInput
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheetUiState
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
    onRequestAppIcons: (List<String>) -> Unit,
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
    val trackedPackageNames by
        remember(state) {
            derivedStateOf {
                state.trackedItems.map { item -> item.packageName }.toSet()
            }
        }
    val enabledFdroidCommonRepos =
        remember(state.lookupConfig.normalizedFdroidCommonRepoIds) {
            FdroidRepositoryPresets.commonSearchReposForIds(
                state.lookupConfig.normalizedFdroidCommonRepoIds
            )
        }

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
        foregroundManagedDownloadBoostEnabledInput =
            state.foregroundManagedDownloadBoostEnabledInput,
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
        onDismissRequest = actions::closeCheckLogicSheet,
        onApply = { actions.applyCheckLogicSheet(installedOnlineShareTargets) },
        onEnsureKeiOsSelfTrack = onEnsureKeiOsSelfTrack,
        onCheckAllTrackedPreReleasesInputChange = actions::setCheckAllTrackedPreReleasesInput,
        onCheckAllDirectApkPreReleasesInputChange = actions::setCheckAllDirectApkPreReleasesInput,
        onAggressiveApkFilteringInputChange = actions::setAggressiveApkFilteringInput,
        onPreciseApkVersionEnabledInputChange = actions::setPreciseApkVersionEnabledInput,
        onScanSystemAppsByDefaultInputChange = actions::setScanSystemAppsByDefaultInput,
        onProfileDepthInputChange = actions::setProfileDepthInput,
        onShareImportFlowModeInputChange = actions::setShareImportFlowModeInput,
        onAppManagedShareInstallEnabledInputChange = actions::setAppManagedShareInstallEnabledInput,
        onForegroundManagedDownloadBoostEnabledInputChange =
            actions::setForegroundManagedDownloadBoostEnabledInput,
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

    GitHubDroidSourcesSheet(
        show = state.showDroidSourcesSheet,
        backdrop = backdrops.sheet,
        lookupConfig = state.lookupConfig,
        selectedRepoIds = state.fdroidCommonRepoIdsInput,
        onDismissRequest = actions::closeDroidSourcesSheet,
        onApply = actions::applyDroidSourcesSheet,
        onRepoEnabledChange = actions::setFdroidCommonRepoEnabled,
    )

    GitHubDebugSheet(
        show = state.showDebugSheet,
        backdrop = backdrops.sheet,
        trackedCount = contentDerivedState.trackedUi.overviewMetrics.trackedCount,
        visibleIncrementalTargetCount =
            selectGitHubDebugVisibleRefreshTargets(
                contentDerivedState.trackedUi.sortedTracked,
            ).size,
        failedCount =
            state.trackedItems.count { item ->
                state.checkStates[item.id]?.failed == true
            },
        backgroundFullRefreshLoading = state.debugBackgroundFullRefreshLoading,
        backgroundDueRefreshLoading = state.debugBackgroundDueRefreshLoading,
        forceDueRefreshLoading = state.debugForceDueRefreshLoading,
        visibleIncrementalRefreshLoading = state.debugVisibleIncrementalRefreshLoading,
        actionsUpdateNotificationLoading = state.debugActionsUpdateNotificationLoading,
        onDismissRequest = actions::closeDebugSheet,
        onRunBackgroundFullRefresh = actions::runDebugBackgroundFullRefresh,
        onRunBackgroundDueRefresh = actions::runDebugBackgroundDueRefresh,
        onForceBackgroundDueRefresh = actions::forceDebugBackgroundDueRefresh,
        onRefreshVisibleIncremental = {
            actions.refreshDebugVisibleIncremental(contentDerivedState.trackedUi.sortedTracked)
        },
        onRefreshFailedIncremental = {
            actions.refreshFailedTrackedItems(showToast = true)
        },
        onSendActionsUpdateNotification = actions::sendDebugActionsUpdateNotification,
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
        controller = actions.apkInstallController,
        backdrop = backdrops.sheet,
        sheetState = apkInfoSheetState,
        onRequestSheetState = onRequestApkInfoSheetState,
        onSearchQueryChange = onApkInfoSearchQueryChange,
        onClearSheetState = onClearApkInfoSheetState,
        onDownload = actions::openApkInDownloader,
        onShare = actions::shareApkLink,
    )

    GitHubManagedInstallConfirmSheetBinding(
        controller = actions.apkInstallController,
        backdrop = backdrops.sheet,
        sheetState = managedInstallConfirmSheetState,
        onRequestSheetState = onRequestManagedInstallConfirmSheetState,
        onClearSheetState = onClearManagedInstallConfirmSheetState,
    )

    GitHubFdroidDetailSheet(
        request = state.fdroidDetailRequest,
        backdrop = backdrops.sheet,
        onDismissRequest = actions::dismissFdroidDetail,
        onRefresh = actions::refreshFdroidDetail,
        onOpenExternalUrl = { url ->
            actions.openExternalUrl(url = url)
        },
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
        externalBuildUntilReleaseInput = state.externalBuildUntilReleaseInput,
        updateIntervalModeInput = state.updateIntervalModeInput,
        actionsUpdateIntervalModeInput = state.actionsUpdateIntervalModeInput,
        preciseApkVersionModeInput = state.preciseApkVersionModeInput,
        ignoreModeInput = state.ignoreModeInput,
        ignoredStableReleaseKeyInput = state.ignoredStableReleaseKeyInput,
        ignoredPreReleaseKeyInput = state.ignoredPreReleaseKeyInput,
        fdroidVersionSelectionModeInput = state.fdroidVersionSelectionModeInput,
        fdroidVersionNameRegexInput = state.fdroidVersionNameRegexInput,
        fdroidApkNameRegexInput = state.fdroidApkNameRegexInput,
        fdroidTrustPolicyInput = state.fdroidTrustPolicyInput,
        fdroidAntiFeaturePolicyInput = state.fdroidAntiFeaturePolicyInput,
        fdroidRepoScopeIdInput = state.fdroidRepoScopeIdInput,
        fdroidAppSearchQueryInput = state.fdroidAppSearchQueryInput,
        fdroidAppSearchCandidates = state.fdroidAppSearchCandidates,
        fdroidAppSearchFailures = state.fdroidAppSearchFailures,
        fdroidAppSearchFailuresExpanded = state.fdroidAppSearchFailuresExpanded,
        fdroidAppSearchRepoReports = state.fdroidAppSearchRepoReports,
        fdroidSelectedCandidate = state.fdroidSelectedCandidate,
        fdroidAppSearchRunning = state.fdroidAppSearchRunning,
        enabledFdroidCommonRepos = enabledFdroidCommonRepos,
        sourceModeDropdownExpanded = state.sourceModeDropdownExpanded,
        sourceModeDropdownAnchorBounds = state.sourceModeDropdownAnchorBounds,
        updateIntervalDropdownExpanded = state.updateIntervalDropdownExpanded,
        updateIntervalDropdownAnchorBounds = state.updateIntervalDropdownAnchorBounds,
        actionsIntervalDropdownExpanded = state.actionsIntervalDropdownExpanded,
        actionsIntervalDropdownAnchorBounds = state.actionsIntervalDropdownAnchorBounds,
        preciseModeDropdownExpanded = state.preciseModeDropdownExpanded,
        preciseModeDropdownAnchorBounds = state.preciseModeDropdownAnchorBounds,
        ignoreModeDropdownExpanded = state.ignoreModeDropdownExpanded,
        ignoreModeDropdownAnchorBounds = state.ignoreModeDropdownAnchorBounds,
        fdroidVersionSelectionDropdownExpanded = state.fdroidVersionSelectionDropdownExpanded,
        fdroidVersionSelectionDropdownAnchorBounds =
            state.fdroidVersionSelectionDropdownAnchorBounds,
        fdroidTrustPolicyDropdownExpanded = state.fdroidTrustPolicyDropdownExpanded,
        fdroidTrustPolicyDropdownAnchorBounds = state.fdroidTrustPolicyDropdownAnchorBounds,
        fdroidAntiFeaturePolicyDropdownExpanded = state.fdroidAntiFeaturePolicyDropdownExpanded,
        fdroidAntiFeaturePolicyDropdownAnchorBounds =
            state.fdroidAntiFeaturePolicyDropdownAnchorBounds,
        fdroidRepoScopeDropdownExpanded = state.fdroidRepoScopeDropdownExpanded,
        fdroidRepoScopeDropdownAnchorBounds = state.fdroidRepoScopeDropdownAnchorBounds,
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
        onRequestAppIcons = onRequestAppIcons,
        onRequestAppPickerState = onRequestAppPickerState,
        onAppPickerPreferencesChange = onAppPickerPreferencesChange,
        onAddAppPickerScrollPositionChange = actions::setTrackAppPickerScrollPosition,
        onSelectedAppChange = actions::setTrackSelectedApp,
        onPreferPreReleaseInputChange = actions::setTrackPreferPreReleaseInput,
        onAlwaysShowLatestReleaseDownloadButtonInputChange =
            actions::setTrackAlwaysShowLatestReleaseDownloadButtonInput,
        onCheckActionsUpdatesInputChange = actions::setTrackCheckActionsUpdatesInput,
        onExternalBuildUntilReleaseInputChange = actions::setTrackExternalBuildUntilReleaseInput,
        onUpdateIntervalModeInputChange = actions::setTrackUpdateIntervalModeInput,
        onActionsUpdateIntervalModeInputChange = actions::setTrackActionsUpdateIntervalModeInput,
        onPreciseApkVersionModeInputChange = actions::setTrackPreciseApkVersionModeInput,
        onIgnoreModeInputChange = actions::setTrackIgnoreModeInput,
        onFdroidVersionSelectionModeInputChange =
            actions::setTrackFdroidVersionSelectionModeInput,
        onFdroidVersionNameRegexInputChange = actions::setTrackFdroidVersionNameRegexInput,
        onFdroidApkNameRegexInputChange = actions::setTrackFdroidApkNameRegexInput,
        onFdroidTrustPolicyInputChange = actions::setTrackFdroidTrustPolicyInput,
        onFdroidAntiFeaturePolicyInputChange = actions::setTrackFdroidAntiFeaturePolicyInput,
        onFdroidRepoScopeIdInputChange = actions::setTrackFdroidRepoScopeIdInput,
        onFdroidAppSearchQueryInputChange = actions::setTrackFdroidAppSearchQueryInput,
        onSearchFdroidAppsByName = actions::searchTrackFdroidAppsByName,
        onScanFdroidReposFromPackage = actions::scanTrackFdroidReposFromPackage,
        onRetryFdroidSearchFailures = actions::retryTrackFdroidSearchFailures,
        onFdroidSearchFailuresExpandedChange = actions::setTrackFdroidSearchFailuresExpanded,
        onFdroidAppSearchCandidateSelected = actions::selectTrackFdroidAppSearchCandidate,
        onSourceModeDropdownExpandedChange = actions::setTrackSourceModeDropdownExpanded,
        onSourceModeDropdownAnchorBoundsChange = actions::setTrackSourceModeDropdownAnchorBounds,
        onUpdateIntervalDropdownExpandedChange = actions::setTrackUpdateIntervalDropdownExpanded,
        onUpdateIntervalDropdownAnchorBoundsChange = actions::setTrackUpdateIntervalDropdownAnchorBounds,
        onActionsIntervalDropdownExpandedChange = actions::setTrackActionsIntervalDropdownExpanded,
        onActionsIntervalDropdownAnchorBoundsChange = actions::setTrackActionsIntervalDropdownAnchorBounds,
        onPreciseModeDropdownExpandedChange = actions::setTrackPreciseModeDropdownExpanded,
        onPreciseModeDropdownAnchorBoundsChange = actions::setTrackPreciseModeDropdownAnchorBounds,
        onIgnoreModeDropdownExpandedChange = actions::setTrackIgnoreModeDropdownExpanded,
        onIgnoreModeDropdownAnchorBoundsChange = actions::setTrackIgnoreModeDropdownAnchorBounds,
        onFdroidVersionSelectionDropdownExpandedChange =
            actions::setTrackFdroidVersionSelectionDropdownExpanded,
        onFdroidVersionSelectionDropdownAnchorBoundsChange =
            actions::setTrackFdroidVersionSelectionDropdownAnchorBounds,
        onFdroidTrustPolicyDropdownExpandedChange =
            actions::setTrackFdroidTrustPolicyDropdownExpanded,
        onFdroidTrustPolicyDropdownAnchorBoundsChange =
            actions::setTrackFdroidTrustPolicyDropdownAnchorBounds,
        onFdroidAntiFeaturePolicyDropdownExpandedChange =
            actions::setTrackFdroidAntiFeaturePolicyDropdownExpanded,
        onFdroidAntiFeaturePolicyDropdownAnchorBoundsChange =
            actions::setTrackFdroidAntiFeaturePolicyDropdownAnchorBounds,
        onFdroidRepoScopeDropdownExpandedChange =
            actions::setTrackFdroidRepoScopeDropdownExpanded,
        onFdroidRepoScopeDropdownAnchorBoundsChange =
            actions::setTrackFdroidRepoScopeDropdownAnchorBounds,
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
