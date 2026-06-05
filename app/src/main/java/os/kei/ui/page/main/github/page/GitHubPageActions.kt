package os.kei.ui.page.main.github.page

import android.content.Context
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.core.system.AppPackageChangedEvent
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.domain.GitHubRefreshRuntimeState
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubShareImportFlowMode
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.page.action.GitHubActionsActions
import os.kei.ui.page.main.github.page.action.GitHubAssetActions
import os.kei.ui.page.main.github.page.action.GitHubConfigActions
import os.kei.ui.page.main.github.page.action.GitHubPageActionEnvironment
import os.kei.ui.page.main.github.page.action.GitHubRefreshActions
import os.kei.ui.page.main.github.page.action.GitHubTrackActions
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.section.GitHubOverviewEntry

internal class GitHubPageActions(
    context: Context,
    scope: CoroutineScope,
    state: GitHubPageState,
    viewModel: GitHubPageViewModel,
    repository: GitHubPageRepository,
    systemDmOption: DownloaderOption,
    openLinkFailureMessage: String,
) {
    private val env =
        GitHubPageActionEnvironment(
            context = context,
            scope = scope,
            state = state,
            viewModel = viewModel,
            repository = repository,
            systemDmOption = systemDmOption,
            openLinkFailureMessage = openLinkFailureMessage,
        )
    private val assetActions = GitHubAssetActions(env)
    private val refreshActions = GitHubRefreshActions(env, assetActions)
    private val actionsActions = GitHubActionsActions(env, assetActions)
    private val configActions = GitHubConfigActions(env, refreshActions, assetActions)
    private val trackActions = GitHubTrackActions(env, refreshActions, assetActions)
    private val debugNotificationActions = GitHubDebugNotificationActionFacade(env)
    private val overviewActions = GitHubOverviewActionFacade(env)
    private val shareImportActions = GitHubShareImportActionFacade(env)
    private val packageChangedActions = GitHubPackageChangedActionFacade(env, refreshActions, assetActions)
    private val trackedRefreshActions = GitHubTrackedRefreshActionFacade(env, refreshActions, assetActions)

    fun openStrategySheet() = configActions.openStrategySheet()

    fun dispose() {
        assetActions.dispose()
    }

    fun closeStrategySheet() = configActions.closeStrategySheet()

    fun setSelectedStrategyInput(value: GitHubLookupStrategyOption) = configActions.setSelectedStrategyInput(value)

    fun setSelectedActionsStrategyInput(value: GitHubActionsLookupStrategyOption) =
        configActions.setSelectedActionsStrategyInput(value)

    fun setApiTokenInput(value: String) = configActions.setApiTokenInput(value)

    fun toggleApiTokenVisibility() = configActions.toggleApiTokenVisibility()

    fun setRecommendedTokenGuideExpanded(value: Boolean) = configActions.setRecommendedTokenGuideExpanded(value)

    fun openCheckLogicSheet() = configActions.openCheckLogicSheet()

    fun closeCheckLogicSheet() = configActions.closeCheckLogicSheet()

    fun setCheckAllTrackedPreReleasesInput(value: Boolean) = configActions.setCheckAllTrackedPreReleasesInput(value)

    fun setCheckAllDirectApkPreReleasesInput(value: Boolean) =
        configActions.setCheckAllDirectApkPreReleasesInput(value)

    fun setAggressiveApkFilteringInput(value: Boolean) = configActions.setAggressiveApkFilteringInput(value)

    fun setPreciseApkVersionEnabledInput(value: Boolean) = configActions.setPreciseApkVersionEnabledInput(value)

    fun setScanSystemAppsByDefaultInput(value: Boolean) = configActions.setScanSystemAppsByDefaultInput(value)

    fun setProfileDepthInput(value: GitHubProfileDepth) = configActions.setProfileDepthInput(value)

    fun setShareImportFlowModeInput(value: GitHubShareImportFlowMode) = configActions.setShareImportFlowModeInput(value)

    fun setAppManagedShareInstallEnabledInput(value: Boolean) = configActions.setAppManagedShareInstallEnabledInput(value)

    fun setPreferredDownloaderPackageInput(value: String) = configActions.setPreferredDownloaderPackageInput(value)

    fun setOnlineShareTargetPackageInput(value: String) = configActions.setOnlineShareTargetPackageInput(value)

    fun setDecisionAssistEnabledInput(value: Boolean) = configActions.setDecisionAssistEnabledInput(value)

    fun setRepositoryHealthCardEnabledInput(value: Boolean) = configActions.setRepositoryHealthCardEnabledInput(value)

    fun setApkTrustCheckEnabledInput(value: Boolean) = configActions.setApkTrustCheckEnabledInput(value)

    fun setShowDownloaderPopup(value: Boolean) = configActions.setShowDownloaderPopup(value)

    fun setShowOnlineShareTargetPopup(value: Boolean) = configActions.setShowOnlineShareTargetPopup(value)

    fun setShowShareImportFlowModePopup(value: Boolean) = configActions.setShowShareImportFlowModePopup(value)

    fun setDownloaderPopupAnchorBounds(value: IntRect?) = configActions.setDownloaderPopupAnchorBounds(value)

    fun setOnlineShareTargetPopupAnchorBounds(value: IntRect?) = configActions.setOnlineShareTargetPopupAnchorBounds(value)

    fun setShareImportFlowModePopupAnchorBounds(value: IntRect?) =
        configActions.setShareImportFlowModePopupAnchorBounds(value)

    fun sendDebugActionsUpdateNotification() = debugNotificationActions.sendActionsUpdateNotification()

    fun openActionsSheet(item: GitHubTrackedApp) = actionsActions.openActionsSheet(item)

    fun closeActionsSheet() = actionsActions.closeActionsSheet()

    fun refreshActionsSheet() = actionsActions.refreshActionsSheet()

    fun selectActionsWorkflow(workflowId: Long) = actionsActions.selectActionsWorkflow(workflowId)

    fun selectActionsBranch(branch: String) = actionsActions.selectActionsBranch(branch)

    fun selectActionsRun(runId: Long) = actionsActions.selectActionsRun(runId)

    fun loadMoreActionsRuns() = actionsActions.loadMoreActionsRuns()

    fun setActionsBranchesExpanded(value: Boolean) = actionsActions.setBranchesExpanded(value)

    fun setActionsWorkflowsExpanded(value: Boolean) = actionsActions.setWorkflowsExpanded(value)

    fun setActionsRunsExpanded(value: Boolean) = actionsActions.setRunsExpanded(value)

    fun setActionsArtifactsExpanded(value: Boolean) = actionsActions.setArtifactsExpanded(value)

    fun setActionsArtifactFilter(value: GitHubActionsArtifactFilter) = actionsActions.setArtifactFilter(value)

    fun setOverviewExpanded(value: Boolean) = overviewActions.setOverviewExpanded(value)

    fun setTrackedStableVersionExpanded(
        itemId: String,
        value: Boolean,
    ) = env.viewModel.setTrackedStableVersionExpanded(itemId, value)

    fun setTrackedLocalVersionExpanded(
        itemId: String,
        value: Boolean,
    ) = env.viewModel.setTrackedLocalVersionExpanded(itemId, value)

    fun setTrackedPreReleaseVersionExpanded(
        itemId: String,
        value: Boolean,
    ) = env.viewModel.setTrackedPreReleaseVersionExpanded(itemId, value)

    fun setTrackedCardExpanded(
        itemId: String,
        value: Boolean,
    ) = env.viewModel.setTrackedCardExpanded(itemId, value)

    fun setSortMode(value: GitHubSortMode) {
        env.state.sortMode = value
        GitHubPageUiStateStore.setSortMode(value)
    }

    fun setSortDirection(value: GitHubSortDirection) {
        env.state.sortDirection = value
        GitHubPageUiStateStore.setSortDirection(value)
    }

    fun setTrackedFilterMode(value: GitHubTrackedFilterMode) {
        env.state.trackedFilterMode = value
        GitHubPageUiStateStore.setTrackedFilterMode(value)
    }

    fun setTrackedSearch(value: String) {
        env.state.trackedSearch = value
    }

    fun setShowActionMenuPopup(value: Boolean) {
        env.state.showActionMenuPopup = value
    }

    fun setFailedFilterEnabled(enabled: Boolean) {
        setTrackedFilterMode(
            if (enabled) {
                GitHubTrackedFilterMode.FailedChecks
            } else {
                GitHubTrackedFilterMode.All
            },
        )
    }

    fun openOverviewEntrySheet() = overviewActions.openOverviewEntrySheet()

    fun closeOverviewEntrySheet() = overviewActions.closeOverviewEntrySheet()

    fun setOverviewEntryVisible(
        entry: GitHubOverviewEntry,
        visible: Boolean,
    ) = overviewActions.setOverviewEntryVisible(entry, visible)

    fun resetOverviewEntries() = overviewActions.resetOverviewEntries()

    fun refreshActionsRunStatus(runId: Long) = actionsActions.refreshActionsRunStatus(runId)

    fun installActionsArtifact(
        runId: Long,
        artifactId: Long,
    ) = actionsActions.installActionsArtifact(runId = runId, artifactId = artifactId)

    fun downloadActionsArtifact(
        runId: Long,
        artifactId: Long,
    ) = actionsActions.downloadActionsArtifact(runId = runId, artifactId = artifactId)

    fun shareActionsArtifact(
        runId: Long,
        artifactId: Long,
    ) = actionsActions.shareActionsArtifact(runId = runId, artifactId = artifactId)

    fun openSelectedActionsRun() = actionsActions.openSelectedActionsRun()

    fun openActionsArtifactDetail(
        runMatch: GitHubActionsRunMatch,
        artifactMatch: GitHubActionsArtifactMatch,
        recommended: Boolean,
    ) {
        env.state.actionsArtifactDetailRequest =
            GitHubActionsArtifactDetailRequest(
                runMatch = runMatch,
                artifactMatch = artifactMatch,
                recommended = recommended,
            )
    }

    fun dismissActionsArtifactDetail() {
        env.state.actionsArtifactDetailRequest = null
    }

    fun dismissApkInfoDetail() {
        env.state.apkInfoDetailRequest = null
    }

    suspend fun reloadApps(forceRefresh: Boolean = false) = refreshActions.reloadApps(forceRefresh = forceRefresh)

    suspend fun initializeWarmSnapshot() = refreshActions.initializeWarmSnapshot()

    suspend fun initializePageActiveWork() = refreshActions.initializePageActiveWork()

    suspend fun syncTrackSnapshotFromStore(forceRefreshApps: Boolean = true) = refreshActions.syncSnapshotFromStore(forceRefreshApps)

    fun applyRefreshRuntimeDisplay(runtime: GitHubRefreshRuntimeState) =
        refreshActions.applyRefreshRuntimeDisplay(runtime)

    fun handleTrackMutationRefresh(
        affectedTrackIds: Set<String>,
        removedTrackIds: Set<String>,
    ) {
        env.scope.launch {
            refreshActions.handleTrackMutationRefresh(
                affectedTrackIds = affectedTrackIds,
                removedTrackIds = removedTrackIds,
            )
        }
    }

    suspend fun syncActiveShareImportFlowFromStore() = shareImportActions.syncActiveFlowFromStore()

    suspend fun syncLocalAppStateOnPageActive() {
        refreshActions.syncLocalAppStateWithInstalledApps(forceRefreshApps = true)
    }

    fun refreshAllTracked(
        showToast: Boolean = true,
        forceRefresh: Boolean = true,
    ) = trackedRefreshActions.refreshAllTracked(
        showToast = showToast,
        forceRefresh = forceRefresh,
    )

    fun refreshVisibleTracked(
        items: List<GitHubTrackedApp>,
        showToast: Boolean = true,
        forceRefresh: Boolean = true,
    ) = trackedRefreshActions.refreshVisibleTracked(
        items = items,
        showToast = showToast,
        forceRefresh = forceRefresh,
    )

    fun openDecisionAssistDetail(
        type: GitHubDecisionAssistDetailType,
        item: GitHubTrackedApp,
    ) {
        val itemState = env.state.checkStates[item.id] ?: VersionCheckUi()
        env.state.decisionAssistDetailRequest =
            GitHubDecisionAssistDetailRequest(
                type = type,
                item = item,
            )
        when (type) {
            GitHubDecisionAssistDetailType.ReleaseNotes -> {
                assetActions.loadReleaseNotesTargets(
                    item = item,
                    itemState = itemState,
                    forceRefresh = false,
                )
            }

            GitHubDecisionAssistDetailType.RepositoryHealth -> {
                refreshRepositoryHealthDetailIfNeeded(item, itemState)
            }
        }
    }

    fun dismissDecisionAssistDetail() {
        env.state.decisionAssistDetailRequest = null
    }

    private fun refreshRepositoryHealthDetailIfNeeded(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
    ) {
        val refreshing =
            env.state.itemRefreshLoading[item.id] == true ||
                env.state.checkStates[item.id]?.loading == true
        if (!shouldAutoRefreshRepositoryHealthDetail(
                itemState = itemState,
                lookupConfig = env.state.lookupConfig.forTrackedItem(item),
                refreshing = refreshing,
            )
        ) {
            return
        }
        trackedRefreshActions.refreshTrackedItem(
            item = item,
            showToastOnError = false,
            profilePurposeOverride = GitHubRepositoryProfilePurpose.DetailFull,
            forceRefresh = false,
        )
    }

    fun refreshTrackedItem(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = true,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = true,
    ) = trackedRefreshActions.refreshTrackedItem(
        item = item,
        showToastOnError = showToastOnError,
        profilePurposeOverride = profilePurposeOverride,
        forceRefresh = forceRefresh,
    )

    fun refreshFailedTrackedItems(showToast: Boolean = true) = trackedRefreshActions.refreshFailedTrackedItems(showToast)

    fun runStrategyBenchmark() = configActions.runStrategyBenchmark()

    fun runCredentialCheck() = configActions.runCredentialCheck()

    fun handleInstalledOnlineShareTargetsChanged(installedOnlineShareTargets: List<OnlineShareTargetOption>) =
        configActions.handleInstalledOnlineShareTargetsChanged(installedOnlineShareTargets)

    fun currentTrackStoreSignalVersion(): Long = env.repository.currentTrackStoreSignalVersion()

    fun trackStoreSignalVersions() = env.repository.trackStoreSignalVersions()

    fun buildAppListPermissionIntent() = env.repository.buildAppListPermissionIntent(env.context)

    fun applyLookupConfig() = configActions.applyLookupConfig()

    fun applyCheckLogicSheet(installedOnlineShareTargets: List<OnlineShareTargetOption>) =
        configActions.applyCheckLogicSheet(installedOnlineShareTargets)

    fun selectRefreshIntervalHours(hours: Int) = configActions.selectRefreshIntervalHours(hours)

    suspend fun previewTrackedItemsImport(raw: String) = configActions.previewTrackedItemsImport(raw)

    suspend fun applyTrackedItemsImport(preview: GitHubTrackImportPreview) = configActions.applyTrackedItemsImport(preview)

    suspend fun importTrackedItemsJson(raw: String) = configActions.importTrackedItemsJson(raw)

    fun cancelPendingShareImportTrack(showToast: Boolean = true) = shareImportActions.cancelPendingTrack(showToast)

    fun openShareImportFlow() = shareImportActions.openFlow()

    fun cancelActiveShareImportFlow(showToast: Boolean = true) = shareImportActions.cancelActiveFlow(showToast)

    fun focusShareImportResult() = shareImportActions.focusResult()

    fun dismissShareImportResult(showToast: Boolean = false) = shareImportActions.dismissResult(showToast)

    fun trimExpiredPendingShareImportTrack(nowMillis: Long = env.clock.nowMs()) = shareImportActions.trimExpiredPendingTrack(nowMillis)

    fun openExternalUrl(
        url: String,
        failureMessage: String = env.openLinkFailureMessage,
    ) = assetActions.openExternalUrl(url = url, failureMessage = failureMessage)

    fun shareApkLink(asset: GitHubReleaseAssetFile) = assetActions.shareApkLink(asset)

    fun openApkInDownloader(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) = assetActions.openApkInDownloader(item, asset)

    fun installApkWithKeiOs(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) = assetActions.installApkWithKeiOs(item, asset)

    fun openApkInfo(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) = assetActions.openApkInfo(item, asset)

    fun confirmManagedInstall() = assetActions.confirmManagedInstall()

    fun dismissManagedInstallConfirm() = assetActions.dismissManagedInstallConfirm()

    fun refreshApkInfo(
        item: GitHubTrackedApp,
        asset: GitHubReleaseAssetFile,
    ) = assetActions.openApkInfo(item, asset, forceRefresh = true)

    fun clearApkAssetUiState(itemId: String) = assetActions.clearApkAssetUiState(itemId)

    fun clearApkAssetCache(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
    ) = assetActions.clearApkAssetCache(item, itemState)

    fun collapseApkAssetPanel(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
    ) = assetActions.clearApkAssetStateAndCache(item, itemState)

    fun collapseTrackedCard(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
    ) = assetActions.clearApkAssetStateAndCache(item, itemState)

    fun loadApkAssets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        toggleOnlyWhenCached: Boolean = true,
        includeAllAssets: Boolean = false,
        allowLatestReleaseFallback: Boolean = false,
    ) = assetActions.loadApkAssets(
        item = item,
        itemState = itemState,
        toggleOnlyWhenCached = toggleOnlyWhenCached,
        includeAllAssets = includeAllAssets,
        allowLatestReleaseFallback = allowLatestReleaseFallback,
    )

    fun loadReleaseNotes(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        clearCache: Boolean = false,
    ) = assetActions.loadReleaseNotes(
        item = item,
        itemState = itemState,
        clearCache = clearCache,
    )

    fun loadReleaseNotesTargets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        forceRefresh: Boolean = false,
    ) = assetActions.loadReleaseNotesTargets(
        item = item,
        itemState = itemState,
        forceRefresh = forceRefresh,
    )

    fun selectReleaseNotesTarget(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget,
    ) = assetActions.selectReleaseNotesTarget(
        item = item,
        target = target,
    )

    fun openTrackSheetForAdd() = trackActions.openTrackSheetForAdd()

    fun ensureKeiOsSelfTrack() = trackActions.ensureKeiOsSelfTrack()

    fun openTrackSheetForEdit(item: GitHubTrackedApp) = trackActions.openTrackSheetForEdit(item)

    fun dismissTrackSheet() = trackActions.dismissTrackSheet()

    fun setTrackAppPickerExpanded(value: Boolean) = trackActions.setTrackAppPickerExpanded(value)

    fun setTrackRepoUrlInput(value: String) = trackActions.setRepoUrlInput(value)

    fun setTrackSourceModeInput(value: GitHubTrackedSourceMode) = trackActions.setTrackSourceModeInput(value)

    fun setTrackAppSearch(value: String) = trackActions.setAppSearch(value)

    fun setTrackPackageNameInput(value: String) = trackActions.setPackageNameInput(value)

    fun setTrackAppPickerScrollPosition(
        index: Int,
        offset: Int,
    ) = trackActions.setAddAppPickerScrollPosition(index, offset)

    fun setTrackSelectedApp(value: InstalledAppItem?) = trackActions.setSelectedApp(value)

    fun setTrackPreferPreReleaseInput(value: Boolean) = trackActions.setPreferPreReleaseInput(value)

    fun setTrackAlwaysShowLatestReleaseDownloadButtonInput(value: Boolean) =
        trackActions.setAlwaysShowLatestReleaseDownloadButtonInput(value)

    fun setTrackCheckActionsUpdatesInput(value: Boolean) = trackActions.setCheckActionsUpdatesInput(value)

    fun setTrackUpdateIntervalModeInput(value: GitHubTrackedUpdateIntervalMode) =
        trackActions.setUpdateIntervalModeInput(value)

    fun setTrackActionsUpdateIntervalModeInput(value: GitHubTrackedActionsUpdateIntervalMode) =
        trackActions.setActionsUpdateIntervalModeInput(value)

    fun setTrackPreciseApkVersionModeInput(value: GitHubTrackedPreciseApkVersionMode) =
        trackActions.setPreciseApkVersionModeInput(value)

    fun setTrackIgnoreModeInput(value: GitHubTrackedIgnoreMode) =
        trackActions.setIgnoreModeInput(value)

    fun setTrackSourceModeDropdownExpanded(value: Boolean) = trackActions.setSourceModeDropdownExpanded(value)

    fun setTrackSourceModeDropdownAnchorBounds(value: IntRect?) = trackActions.setSourceModeDropdownAnchorBounds(value)

    fun setTrackUpdateIntervalDropdownExpanded(value: Boolean) = trackActions.setUpdateIntervalDropdownExpanded(value)

    fun setTrackUpdateIntervalDropdownAnchorBounds(value: IntRect?) =
        trackActions.setUpdateIntervalDropdownAnchorBounds(value)

    fun setTrackActionsIntervalDropdownExpanded(value: Boolean) = trackActions.setActionsIntervalDropdownExpanded(value)

    fun setTrackActionsIntervalDropdownAnchorBounds(value: IntRect?) =
        trackActions.setActionsIntervalDropdownAnchorBounds(value)

    fun setTrackPreciseModeDropdownExpanded(value: Boolean) = trackActions.setPreciseModeDropdownExpanded(value)

    fun setTrackPreciseModeDropdownAnchorBounds(value: IntRect?) = trackActions.setPreciseModeDropdownAnchorBounds(value)

    fun setTrackIgnoreModeDropdownExpanded(value: Boolean) = trackActions.setIgnoreModeDropdownExpanded(value)

    fun setTrackIgnoreModeDropdownAnchorBounds(value: IntRect?) = trackActions.setIgnoreModeDropdownAnchorBounds(value)

    fun refreshTrackAppList() = trackActions.refreshAppListForTrackSheet()

    fun requestDeleteTrackedItem(item: GitHubTrackedApp) = trackActions.requestDeleteItem(item)

    fun ignoreCurrentTrackedVersion(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
    ) = trackActions.ignoreCurrentTrackedVersion(item, itemState)

    fun dismissPendingDeleteItem() {
        if (!env.state.deleteInProgress) {
            env.state.pendingDeleteItem = null
        }
    }

    fun scanPackageNameFromRepo() = trackActions.scanPackageNameFromRepo()

    fun scanRepoUrlFromPackage() = trackActions.scanRepoUrlFromPackage()

    fun selectRepoScanCandidate(candidate: GitHubPackageRepositoryScanCandidate) = trackActions.selectRepoScanCandidate(candidate)

    fun applyTrackSheet() = trackActions.applyTrackSheet()

    fun confirmDeletePendingItem() = trackActions.confirmDeletePendingItem()

    fun dismissTrackImportPreview() {
        env.state.dismissTrackImportPreview()
    }

    fun confirmTrackImportPreview(
        importInProgress: Boolean,
        onConfirmTrackImport: () -> Unit,
    ) {
        val preview = env.state.pendingTrackImportPreview ?: return
        if (!preview.canImport) {
            env.state.dismissTrackImportPreview()
            return
        }
        if (importInProgress) return
        onConfirmTrackImport()
    }

    suspend fun handlePackageChangedEvent(event: AppPackageChangedEvent) = packageChangedActions.handle(event)
}
