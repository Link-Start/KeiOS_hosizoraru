package os.kei.ui.page.main.github.page

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.system.AppPackageChangedEvent
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.page.action.GitHubActionsActions
import os.kei.ui.page.main.github.page.action.GitHubAssetActions
import os.kei.ui.page.main.github.page.action.GitHubConfigActions
import os.kei.ui.page.main.github.page.action.GitHubPageActionEnvironment
import os.kei.ui.page.main.github.page.action.GitHubRefreshActions
import os.kei.ui.page.main.github.page.action.GitHubTrackActions
import os.kei.ui.page.main.github.query.DownloaderOption
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.github.section.GitHubOverviewEntry
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseUiStateStore

internal class GitHubPageActions(
    context: Context,
    scope: CoroutineScope,
    state: GitHubPageState,
    repository: GitHubPageRepository,
    systemDmOption: DownloaderOption,
    openLinkFailureMessage: String,
) {
    private val env =
        GitHubPageActionEnvironment(
            context = context,
            scope = scope,
            state = state,
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
    private val overviewActions = GitHubOverviewActionFacade(state)
    private val shareImportActions = GitHubShareImportActionFacade(env)
    private val packageChangedActions = GitHubPackageChangedActionFacade(env, refreshActions, assetActions)

    private companion object {
        const val RETRY_FAILED_TRACKED_PARALLELISM = 3
    }

    fun openStrategySheet() = configActions.openStrategySheet()

    fun dispose() {
        assetActions.dispose()
    }

    fun closeStrategySheet() = configActions.closeStrategySheet()

    fun openCheckLogicSheet() = configActions.openCheckLogicSheet()

    fun closeCheckLogicSheet() = configActions.closeCheckLogicSheet()

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

    fun setOverviewExpanded(value: Boolean) = overviewActions.setOverviewExpanded(value)

    fun setTrackedStableVersionExpanded(
        itemId: String,
        value: Boolean,
    ) {
        env.state.trackedStableVersionExpanded[itemId] = value
        GitHubTrackedReleaseUiStateStore.setStableVersionExpanded(itemId, value)
    }

    fun setTrackedLocalVersionExpanded(
        itemId: String,
        value: Boolean,
    ) {
        env.state.trackedLocalVersionExpanded[itemId] = value
        GitHubTrackedReleaseUiStateStore.setLocalVersionExpanded(itemId, value)
    }

    fun setTrackedPreReleaseVersionExpanded(
        itemId: String,
        value: Boolean,
    ) {
        env.state.trackedPreReleaseVersionExpanded[itemId] = value
        GitHubTrackedReleaseUiStateStore.setPreReleaseVersionExpanded(itemId, value)
    }

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

    suspend fun reloadApps(forceRefresh: Boolean = false) = refreshActions.reloadApps(forceRefresh = forceRefresh)

    suspend fun initializeWarmSnapshot() = refreshActions.initializeWarmSnapshot()

    suspend fun initializePageActiveWork() = refreshActions.initializePageActiveWork()

    suspend fun syncTrackSnapshotFromStore(forceRefreshApps: Boolean = true) = refreshActions.syncSnapshotFromStore(forceRefreshApps)

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
    ) {
        env.scope.launch {
            refreshActions.reloadApps(forceRefresh = true)
            refreshActions.refreshAllTracked(
                showToast = showToast,
                forceRefresh = forceRefresh,
            ) {
                reloadExpandedAssetPanelsAfterRefresh()
            }
        }
    }

    fun refreshVisibleTracked(
        items: List<GitHubTrackedApp>,
        showToast: Boolean = true,
        forceRefresh: Boolean = true,
    ) {
        if (items.isEmpty()) {
            refreshActions.refreshTrackedBatch(
                targets = emptyList(),
                showToast = showToast,
                forceRefresh = forceRefresh,
            )
            return
        }
        val targetIds = items.mapTo(LinkedHashSet()) { it.id }
        env.scope.launch {
            refreshActions.reloadApps(forceRefresh = true)
            refreshActions.refreshTrackedBatch(
                targets = items,
                showToast = showToast,
                forceRefresh = forceRefresh,
            ) {
                reloadExpandedAssetPanelsAfterRefresh(targetIds = targetIds)
            }
        }
    }

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
        refreshTrackedItem(
            item = item,
            showToastOnError = false,
            profilePurposeOverride = GitHubRepositoryProfilePurpose.DetailFull,
            forceRefresh = false,
        )
    }

    private fun reloadExpandedAssetPanelsAfterRefresh(targetIds: Set<String>? = null) {
        val expandedItemIds =
            env.state.apkAssetExpanded
                .filterValues { it }
                .keys
                .filterTo(HashSet()) { targetIds == null || it in targetIds }
        if (expandedItemIds.isEmpty()) return

        env.state.trackedItems.forEach { item ->
            if (item.id !in expandedItemIds) return@forEach
            val itemState = env.state.checkStates[item.id] ?: return@forEach
            val includeAllAssets = env.state.apkAssetIncludeAll[item.id] == true
            if (canLoadApkAssets(item, itemState, env.context)) {
                assetActions.clearApkAssetCache(item, itemState)
                assetActions.loadApkAssets(
                    item = item,
                    itemState = itemState,
                    toggleOnlyWhenCached = false,
                    includeAllAssets = includeAllAssets,
                    allowLatestReleaseFallback = itemState.isLocalAppUninstalled(),
                )
            } else {
                assetActions.clearApkAssetUiState(item.id)
            }
        }
    }

    fun refreshTrackedItem(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = true,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = true,
    ) {
        env.scope.launch {
            refreshTrackedItemNow(
                item = item,
                showToastOnError = showToastOnError,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh,
                reloadAppsBeforeRefresh = true,
            )
        }
    }

    private suspend fun refreshTrackedItemNow(
        item: GitHubTrackedApp,
        showToastOnError: Boolean,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        reloadAppsBeforeRefresh: Boolean,
    ) {
        if (env.state.trackedItems.none { it.id == item.id }) return
        if (env.state.itemRefreshLoading[item.id] == true) return
        if (env.state.checkStates[item.id]?.loading == true) return
        env.state.itemRefreshLoading[item.id] = true
        try {
            if (reloadAppsBeforeRefresh) {
                refreshActions.reloadApps(forceRefresh = true)
            }
            val wasAssetExpanded = env.state.apkAssetExpanded[item.id] == true
            val includeAllAssets = env.state.apkAssetIncludeAll[item.id] == true
            val previousState = env.state.checkStates[item.id] ?: VersionCheckUi()
            assetActions.clearApkAssetCache(item, previousState)
            refreshActions.refreshItemNow(
                item = item,
                showToastOnError = showToastOnError,
                keepCurrentVisualWhileRefreshing = true,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh,
            ) { updatedState ->
                if (wasAssetExpanded && canLoadApkAssets(item, updatedState, env.context)) {
                    assetActions.clearApkAssetCache(item, updatedState)
                    assetActions.loadApkAssets(
                        item = item,
                        itemState = updatedState,
                        toggleOnlyWhenCached = false,
                        includeAllAssets = includeAllAssets,
                        allowLatestReleaseFallback = updatedState.isLocalAppUninstalled(),
                    )
                } else if (wasAssetExpanded) {
                    assetActions.clearApkAssetUiState(item.id)
                } else {
                    assetActions.clearApkAssetRuntimeState(item.id)
                }
                env.state.requestTrackCardFocus(item.id)
            }
        } finally {
            env.state.itemRefreshLoading.remove(item.id)
        }
    }

    fun refreshFailedTrackedItems(showToast: Boolean = true) {
        val failedItems =
            env.state.trackedItems.filter { item ->
                env.state.checkStates[item.id]?.failed == true
            }
        if (failedItems.isEmpty()) {
            if (showToast) {
                env.toast(R.string.github_toast_no_checkable_item)
            }
            return
        }
        env.scope.launch {
            refreshActions.reloadApps(forceRefresh = true)
            coroutineScope {
                failedItems.chunked(RETRY_FAILED_TRACKED_PARALLELISM).forEach { chunk ->
                    chunk
                        .map { item ->
                            launch {
                                if (env.state.trackedItems.any { it.id == item.id }) {
                                    refreshTrackedItemNow(
                                        item = item,
                                        showToastOnError = showToast,
                                        reloadAppsBeforeRefresh = false,
                                    )
                                }
                            }
                        }.joinAll()
                }
            }
        }
    }

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

    fun trimExpiredPendingShareImportTrack(nowMillis: Long = System.currentTimeMillis()) =
        shareImportActions.trimExpiredPendingTrack(nowMillis)

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

    fun refreshTrackAppList() = trackActions.refreshAppListForTrackSheet()

    fun requestDeleteTrackedItem(item: GitHubTrackedApp) = trackActions.requestDeleteItem(item)

    fun scanPackageNameFromRepo() = trackActions.scanPackageNameFromRepo()

    fun scanRepoUrlFromPackage() = trackActions.scanRepoUrlFromPackage()

    fun selectRepoScanCandidate(candidate: GitHubPackageRepositoryScanCandidate) = trackActions.selectRepoScanCandidate(candidate)

    fun applyTrackSheet() = trackActions.applyTrackSheet()

    fun confirmDeletePendingItem() = trackActions.confirmDeletePendingItem()

    suspend fun handlePackageChangedEvent(event: AppPackageChangedEvent) = packageChangedActions.handle(event)
}
