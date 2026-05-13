package os.kei.ui.page.main.github.page

import android.content.Context
import androidx.compose.runtime.Composable
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.github.actions.GitHubActionsSheet
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.sheet.GitHubActionsArtifactDetailSheet
import os.kei.ui.page.main.github.sheet.GitHubApkInfoSheet
import os.kei.ui.page.main.github.sheet.GitHubCheckLogicSheet
import os.kei.ui.page.main.github.sheet.GitHubDecisionAssistDetailSheet
import os.kei.ui.page.main.github.sheet.GitHubDeleteTrackDialog
import os.kei.ui.page.main.github.sheet.GitHubManagedInstallConfirmSheet
import os.kei.ui.page.main.github.sheet.GitHubOverviewEntrySheet
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
    onConfirmTrackImport: () -> Unit
) {
    GitHubOverviewEntrySheet(
        show = state.showOverviewEntrySheet,
        backdrop = backdrops.sheet,
        visibleEntries = state.overviewVisibleEntries,
        onEntryVisibleChange = actions::setOverviewEntryVisible,
        onReset = actions::resetOverviewEntries,
        onDismissRequest = actions::closeOverviewEntrySheet
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
        checkAllTrackedPreReleasesInput = state.checkAllTrackedPreReleasesInput,
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
        onCheckAllTrackedPreReleasesInputChange = { state.checkAllTrackedPreReleasesInput = it },
        onAggressiveApkFilteringInputChange = { state.aggressiveApkFilteringInput = it },
        onPreciseApkVersionEnabledInputChange = { state.preciseApkVersionEnabledInput = it },
        onScanSystemAppsByDefaultInputChange = { state.scanSystemAppsByDefaultInput = it },
        onProfileDepthInputChange = { state.profileDepthInput = it },
        onShareImportFlowModeInputChange = { state.shareImportFlowModeInput = it },
        onAppManagedShareInstallEnabledInputChange = {
            state.appManagedShareInstallEnabledInput = it
        },
        onPreferredDownloaderPackageInputChange = { state.preferredDownloaderPackageInput = it },
        onOnlineShareTargetPackageInputChange = { state.onlineShareTargetPackageInput = it },
        onDecisionAssistEnabledInputChange = { state.decisionAssistEnabledInput = it },
        onRepositoryHealthCardEnabledInputChange = { state.repositoryHealthCardEnabledInput = it },
        onApkTrustCheckEnabledInputChange = { state.apkTrustCheckEnabledInput = it },
        onShowDownloaderPopupChange = { state.showDownloaderPopup = it },
        onShowOnlineShareTargetPopupChange = { state.showOnlineShareTargetPopup = it },
        onShowShareImportFlowModePopupChange = { state.showShareImportFlowModePopup = it },
        onDownloaderPopupAnchorBoundsChange = { state.downloaderPopupAnchorBounds = it },
        onOnlineShareTargetPopupAnchorBoundsChange = {
            state.onlineShareTargetPopupAnchorBounds = it
        },
        onShareImportFlowModePopupAnchorBoundsChange = {
            state.shareImportFlowModePopupAnchorBounds = it
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
        assetBundle = state.decisionAssistDetailRequest
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
            ?.item
            ?.id
            ?.let { state.releaseNotesBundles[it] },
        releaseNotesTargets = state.decisionAssistDetailRequest
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
            ?.item
            ?.id
            ?.let { state.releaseNotesTargets[it] }
            .orEmpty(),
        selectedReleaseNotesTarget = state.decisionAssistDetailRequest
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
            ?.item
            ?.id
            ?.let { state.releaseNotesSelectedTargets[it] },
        releaseNotesApkVersion = state.decisionAssistDetailRequest
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
            ?.item
            ?.let { item ->
                state.releaseNotesSelectedTargets[item.id]?.let { target ->
                    state.releaseNotesApkVersions[releaseNotesApkVersionKey(item.id, target)]
                }
            },
        preciseApkVersionEnabled = state.decisionAssistDetailRequest
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.ReleaseNotes }
            ?.item
            ?.let { state.lookupConfig.forTrackedItem(it).preciseApkVersionEnabled } == true,
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
        healthRefreshing = state.decisionAssistDetailRequest
            ?.takeIf { it.type == GitHubDecisionAssistDetailType.RepositoryHealth }
            ?.item
            ?.id
            ?.let { state.itemRefreshLoading[it] == true || state.checkStates[it]?.loading == true } == true,
        onDismissRequest = { state.decisionAssistDetailRequest = null },
        onRefreshHealth = { item ->
            actions.refreshTrackedItem(
                item = item,
                showToastOnError = true,
                profilePurposeOverride = GitHubRepositoryProfilePurpose.ManualDeepRefresh,
                forceRefresh = true
            )
        },
        onRefreshReleaseNotes = { item, itemState ->
            actions.loadReleaseNotes(
                item = item,
                itemState = itemState,
                clearCache = true
            )
        },
        onSelectReleaseNotesTarget = { item, target ->
            actions.selectReleaseNotesTarget(item, target)
        },
        onOpenExternalUrl = actions::openExternalUrl
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

    val apkInfoRequest = state.apkInfoDetailRequest
    val apkInfoAsset = apkInfoRequest?.asset
    val apkInfoKey = apkInfoAsset?.githubApkInfoKey().orEmpty()
    val apkInfoManagedInstallRunning = apkInfoRequest?.let { request ->
        state.managedInstallLoading[request.item.githubManagedInstallKey(request.asset)] == true
    } == true
    GitHubApkInfoSheet(
        asset = apkInfoAsset,
        info = state.apkInfoResults[apkInfoKey],
        installedInfo = state.apkInfoInstalledResults[apkInfoKey],
        loading = state.apkInfoLoading[apkInfoKey] == true,
        error = state.apkInfoErrors[apkInfoKey].orEmpty(),
        backdrop = backdrops.sheet,
        managedInstallEnabled = state.lookupConfig.appManagedShareInstallEnabled,
        managedInstallRunning = apkInfoManagedInstallRunning,
        onRefresh = {
            apkInfoRequest?.let { actions.refreshApkInfo(it.item, it.asset) }
        },
        onDownload = {
            apkInfoRequest?.let { actions.openApkInDownloader(it.item, it.asset) }
        },
        onShare = { apkInfoAsset?.let(actions::shareApkLink) },
        onDismissRequest = { state.apkInfoDetailRequest = null }
    )

    val managedInstallConfirmRequest = state.managedInstallConfirmRequest
    val managedInstallAsset = managedInstallConfirmRequest?.asset
    val managedInstallInfoKey = managedInstallAsset?.githubApkInfoKey().orEmpty()
    val managedInstallRunning = managedInstallConfirmRequest?.let { request ->
        state.managedInstallLoading[request.item.githubManagedInstallKey(request.asset)] == true
    } == true
    GitHubManagedInstallConfirmSheet(
        request = managedInstallConfirmRequest,
        info = state.apkInfoResults[managedInstallInfoKey],
        installedInfo = state.apkInfoInstalledResults[managedInstallInfoKey],
        loading = state.apkInfoLoading[managedInstallInfoKey] == true,
        error = state.apkInfoErrors[managedInstallInfoKey].orEmpty(),
        running = managedInstallRunning,
        backdrop = backdrops.sheet,
        onConfirm = actions::confirmManagedInstall,
        onDismissRequest = actions::dismissManagedInstallConfirm
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
        trackedPackageNames = state.trackedItems.map { it.packageName }.toSet(),
        appListRefreshing = state.appListRefreshing,
        addAppPickerRememberedFirstVisibleItemIndex = state.addTrackAppPickerFirstVisibleItemIndex,
        addAppPickerRememberedFirstVisibleItemScrollOffset =
            state.addTrackAppPickerFirstVisibleItemScrollOffset,
        sourceModeInput = state.trackSourceModeInput,
        preferPreReleaseInput = state.preferPreReleaseInput,
        alwaysShowLatestReleaseDownloadButtonInput = state.alwaysShowLatestReleaseDownloadButtonInput,
        checkActionsUpdatesInput = state.checkActionsUpdatesInput,
        preciseApkVersionModeInput = state.preciseApkVersionModeInput,
        globalPreciseApkVersionEnabled = state.lookupConfig.preciseApkVersionEnabled,
        onDismissRequest = actions::dismissTrackSheet,
        onApply = actions::applyTrackSheet,
        onRepoUrlInputChange = {
            state.repoUrlInput = it
            state.repoScanCandidates = emptyList()
        },
        onSourceModeInputChange = { mode ->
            state.trackSourceModeInput = mode
            state.repoScanCandidates = emptyList()
            if (mode == GitHubTrackedSourceMode.DirectApk) {
                state.alwaysShowLatestReleaseDownloadButtonInput = false
                state.checkActionsUpdatesInput = false
            }
        },
        onAppSearchChange = { state.appSearch = it },
        onPackageNameInputChange = { input ->
            state.packageNameInput = input
            state.repoScanCandidates = emptyList()
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
        onRepoScanCandidateSelected = actions::selectRepoScanCandidate,
        onPickerExpandedChange = actions::setTrackAppPickerExpanded,
        onRefreshAppList = actions::refreshTrackAppList,
        onAddAppPickerScrollPositionChange = { index, offset ->
            state.addTrackAppPickerFirstVisibleItemIndex = index
            state.addTrackAppPickerFirstVisibleItemScrollOffset = offset
        },
        onSelectedAppChange = { app ->
            state.selectedApp = app
            state.repoScanCandidates = emptyList()
            if (app != null) {
                state.packageNameInput = app.packageName
            }
        },
        onPreferPreReleaseInputChange = { state.preferPreReleaseInput = it },
        onAlwaysShowLatestReleaseDownloadButtonInputChange = {
            state.alwaysShowLatestReleaseDownloadButtonInput = it
        },
        onCheckActionsUpdatesInputChange = {
            state.checkActionsUpdatesInput = it
        },
        onPreciseApkVersionModeInputChange = {
            state.preciseApkVersionModeInput = it
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
