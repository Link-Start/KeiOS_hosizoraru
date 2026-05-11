package os.kei.ui.page.main.github.page

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.system.AppPackageChangedEvent
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
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
import os.kei.ui.page.main.github.section.GitHubOverviewUiStateStore
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseUiStateStore
import os.kei.ui.page.main.github.section.defaultGitHubOverviewEntries
import os.kei.ui.page.main.github.section.orDefaultGitHubOverviewEntries
import os.kei.ui.page.main.github.share.GitHubShareImportActivity
import os.kei.ui.page.main.github.share.GitHubShareImportResult
import os.kei.ui.page.main.github.share.GitHubShareImportResultKind
import os.kei.ui.page.main.github.share.toShareImportResult

internal class GitHubPageActions(
    context: Context,
    scope: CoroutineScope,
    state: GitHubPageState,
    repository: GitHubPageRepository,
    systemDmOption: DownloaderOption,
    openLinkFailureMessage: String
) {
    private val env = GitHubPageActionEnvironment(
        context = context,
        scope = scope,
        state = state,
        repository = repository,
        systemDmOption = systemDmOption,
        openLinkFailureMessage = openLinkFailureMessage
    )
    private val assetActions = GitHubAssetActions(env)
    private val refreshActions = GitHubRefreshActions(env, assetActions)
    private val actionsActions = GitHubActionsActions(env)
    private val configActions = GitHubConfigActions(env, refreshActions, assetActions)
    private val trackActions = GitHubTrackActions(env, refreshActions, assetActions)

    private val minHandleIntervalMs = 1200L
    private val pendingShareImportTrackMaxAgeMs = 25 * 60 * 1000L
    private val handledAtByPackage = mutableMapOf<String, Long>()
    private val packageUpdateActions = setOf(
        Intent.ACTION_PACKAGE_ADDED,
        Intent.ACTION_PACKAGE_REMOVED,
        Intent.ACTION_PACKAGE_FULLY_REMOVED,
        Intent.ACTION_PACKAGE_REPLACED,
        Intent.ACTION_PACKAGE_CHANGED
    )

    fun openStrategySheet() = configActions.openStrategySheet()

    fun closeStrategySheet() = configActions.closeStrategySheet()

    fun openCheckLogicSheet() = configActions.openCheckLogicSheet()

    fun closeCheckLogicSheet() = configActions.closeCheckLogicSheet()

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

    fun setOverviewExpanded(value: Boolean) {
        env.state.overviewExpanded = value
        GitHubOverviewUiStateStore.setExpanded(value)
    }

    fun setTrackedStableVersionExpanded(itemId: String, value: Boolean) {
        env.state.trackedStableVersionExpanded[itemId] = value
        GitHubTrackedReleaseUiStateStore.setStableVersionExpanded(itemId, value)
    }

    fun setTrackedLocalVersionExpanded(itemId: String, value: Boolean) {
        env.state.trackedLocalVersionExpanded[itemId] = value
        GitHubTrackedReleaseUiStateStore.setLocalVersionExpanded(itemId, value)
    }

    fun setTrackedPreReleaseVersionExpanded(itemId: String, value: Boolean) {
        env.state.trackedPreReleaseVersionExpanded[itemId] = value
        GitHubTrackedReleaseUiStateStore.setPreReleaseVersionExpanded(itemId, value)
    }

    fun openOverviewEntrySheet() {
        env.state.showOverviewEntrySheet = true
        env.state.overviewExpanded = true
        GitHubOverviewUiStateStore.setExpanded(true)
    }

    fun closeOverviewEntrySheet() {
        env.state.showOverviewEntrySheet = false
    }

    fun setOverviewEntryVisible(entry: GitHubOverviewEntry, visible: Boolean) {
        val current = env.state.overviewVisibleEntries.orDefaultGitHubOverviewEntries()
        val next = if (visible) {
            current + entry
        } else {
            (current - entry).ifEmpty { setOf(entry) }
        }
        env.state.overviewVisibleEntries = next
        GitHubOverviewUiStateStore.setVisibleEntries(next)
    }

    fun resetOverviewEntries() {
        val defaults = defaultGitHubOverviewEntries()
        env.state.overviewVisibleEntries = defaults
        GitHubOverviewUiStateStore.setVisibleEntries(defaults)
    }

    fun refreshActionsRunStatus(runId: Long) = actionsActions.refreshActionsRunStatus(runId)

    fun downloadActionsArtifact(runId: Long, artifactId: Long) =
        actionsActions.downloadActionsArtifact(runId = runId, artifactId = artifactId)

    fun shareActionsArtifact(runId: Long, artifactId: Long) =
        actionsActions.shareActionsArtifact(runId = runId, artifactId = artifactId)

    fun openSelectedActionsRun() = actionsActions.openSelectedActionsRun()

    suspend fun reloadApps(forceRefresh: Boolean = false) =
        refreshActions.reloadApps(forceRefresh = forceRefresh)

    suspend fun initializeWarmSnapshot() = refreshActions.initializeWarmSnapshot()

    suspend fun initializePageActiveWork() = refreshActions.initializePageActiveWork()

    suspend fun syncTrackSnapshotFromStore(forceRefreshApps: Boolean = true) =
        refreshActions.syncSnapshotFromStore(forceRefreshApps)

    fun handleTrackMutationRefresh(
        affectedTrackIds: Set<String>,
        removedTrackIds: Set<String>
    ) {
        env.scope.launch {
            refreshActions.handleTrackMutationRefresh(
                affectedTrackIds = affectedTrackIds,
                removedTrackIds = removedTrackIds
            )
        }
    }

    suspend fun syncActiveShareImportFlowFromStore() {
        val flow = env.repository.loadActiveShareImportFlow()
        env.state.pendingShareImportPreview = flow.preview
        env.state.pendingShareImportTrack = flow.pendingTrack
        env.state.pendingShareImportAttachCandidate = flow.attachCandidate
        env.state.pendingShareImportResult = flow.result
    }

    suspend fun syncLocalAppStateOnPageActive() {
        refreshActions.syncLocalAppStateWithInstalledApps(forceRefreshApps = true)
    }

    fun refreshAllTracked(showToast: Boolean = true) {
        env.scope.launch {
            refreshActions.reloadApps(forceRefresh = true)
            refreshActions.refreshAllTracked(showToast = showToast) {
                val expandedItemIds = env.state.apkAssetExpanded
                    .filterValues { it }
                    .keys
                    .toSet()
                if (expandedItemIds.isEmpty()) return@refreshAllTracked

                env.state.trackedItems.forEach { item ->
                    if (item.id !in expandedItemIds) return@forEach
                    val itemState = env.state.checkStates[item.id] ?: return@forEach
                    val includeAllAssets = env.state.apkAssetIncludeAll[item.id] == true
                    if (canLoadApkAssets(item, itemState)) {
                        assetActions.clearApkAssetCache(item, itemState)
                        assetActions.loadApkAssets(
                            item = item,
                            itemState = itemState,
                            toggleOnlyWhenCached = false,
                            includeAllAssets = includeAllAssets
                        )
                    } else {
                        assetActions.clearApkAssetUiState(item.id)
                    }
                }
            }
        }
    }

    fun refreshTrackedItem(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = true,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false
    ) {
        if (env.state.trackedItems.none { it.id == item.id }) return
        if (env.state.itemRefreshLoading[item.id] == true) return
        if (env.state.checkStates[item.id]?.loading == true) return
        env.scope.launch {
            env.state.itemRefreshLoading[item.id] = true
            try {
                refreshActions.reloadApps(forceRefresh = true)
                val wasAssetExpanded = env.state.apkAssetExpanded[item.id] == true
                val includeAllAssets = env.state.apkAssetIncludeAll[item.id] == true
                val previousState = env.state.checkStates[item.id] ?: VersionCheckUi()
                assetActions.clearApkAssetCache(item, previousState)
                refreshActions.refreshItemNow(
                    item = item,
                    showToastOnError = showToastOnError,
                    keepCurrentVisualWhileRefreshing = true,
                    profilePurposeOverride = profilePurposeOverride,
                    forceRefresh = forceRefresh
                ) { updatedState ->
                    if (wasAssetExpanded && canLoadApkAssets(item, updatedState)) {
                        assetActions.clearApkAssetCache(item, updatedState)
                        assetActions.loadApkAssets(
                            item = item,
                            itemState = updatedState,
                            toggleOnlyWhenCached = false,
                            includeAllAssets = includeAllAssets
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
    }

    fun refreshFailedTrackedItems(showToast: Boolean = true) {
        val failedItems = env.state.trackedItems.filter { item ->
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
            failedItems.forEach { item ->
                if (env.state.trackedItems.any { it.id == item.id }) {
                    refreshTrackedItem(item = item, showToastOnError = showToast)
                }
            }
        }
    }

    fun runStrategyBenchmark() = configActions.runStrategyBenchmark()

    fun runCredentialCheck() = configActions.runCredentialCheck()

    fun handleInstalledOnlineShareTargetsChanged(
        installedOnlineShareTargets: List<OnlineShareTargetOption>
    ) = configActions.handleInstalledOnlineShareTargetsChanged(installedOnlineShareTargets)

    fun currentTrackStoreSignalVersion(): Long = env.repository.currentTrackStoreSignalVersion()

    fun trackStoreSignalVersions() = env.repository.trackStoreSignalVersions()

    fun buildAppListPermissionIntent() = env.repository.buildAppListPermissionIntent(env.context)

    fun applyLookupConfig() = configActions.applyLookupConfig()

    fun applyCheckLogicSheet(installedOnlineShareTargets: List<OnlineShareTargetOption>) =
        configActions.applyCheckLogicSheet(installedOnlineShareTargets)

    suspend fun previewTrackedItemsImport(raw: String) = configActions.previewTrackedItemsImport(raw)

    suspend fun applyTrackedItemsImport(preview: GitHubTrackImportPreview) =
        configActions.applyTrackedItemsImport(preview)

    suspend fun importTrackedItemsJson(raw: String) = configActions.importTrackedItemsJson(raw)

    fun cancelPendingShareImportTrack(showToast: Boolean = true) {
        val hadPending = env.state.pendingShareImportTrack != null
        val cancelledResult = buildCancelledShareImportResult()
        clearPendingShareImportTrack(cancelledResult)
        if (hadPending && showToast) {
            env.toast(R.string.github_toast_share_import_pending_cancelled)
        }
    }

    fun openShareImportFlow() {
        val intent = Intent(env.context, GitHubShareImportActivity::class.java).apply {
            action = GitHubShareImportActivity.ACTION_RESUME_SHARE_IMPORT
            putExtra(GitHubShareImportActivity.EXTRA_FORCE_SHEET, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        env.context.startActivity(intent)
    }

    fun cancelActiveShareImportFlow(showToast: Boolean = true) {
        val hadActiveFlow = env.state.pendingShareImportPreview != null ||
                env.state.pendingShareImportTrack != null ||
                env.state.pendingShareImportAttachCandidate != null
        val cancelledResult = buildCancelledShareImportResult()
        env.state.pendingShareImportPreview = null
        env.state.pendingShareImportTrack = null
        env.state.pendingShareImportAttachCandidate = null
        env.state.pendingShareImportResult = cancelledResult
        env.scope.launch {
            env.repository.clearActiveShareImportFlow()
            if (cancelledResult != null) {
                env.repository.saveShareImportResult(cancelledResult)
            }
            GitHubShareImportNotificationHelper.notifyCancelled(env.context)
        }
        if (hadActiveFlow && showToast) {
            env.toast(R.string.github_toast_share_import_pending_cancelled)
        }
    }

    fun focusShareImportResult() {
        val result = env.state.pendingShareImportResult ?: return
        env.state.showFailedOnly = false
        val query = result.appDisplayLabel
            .ifBlank { result.packageName }
            .ifBlank { result.repo }
            .ifBlank { result.owner }
        if (query.isNotBlank()) {
            env.state.trackedSearch = query
        }
    }

    fun dismissShareImportResult(showToast: Boolean = false) {
        val hadResult = env.state.pendingShareImportResult != null
        env.state.pendingShareImportResult = null
        env.scope.launch {
            env.repository.clearShareImportResult()
            GitHubShareImportNotificationHelper.cancel(env.context)
        }
        if (hadResult && showToast) {
            env.toast(R.string.common_mark_read)
        }
    }

    fun trimExpiredPendingShareImportTrack(nowMillis: Long = System.currentTimeMillis()) {
        clearExpiredPendingShareImportTrack(nowMillis)
    }

    fun openExternalUrl(url: String, failureMessage: String = env.openLinkFailureMessage) =
        assetActions.openExternalUrl(url = url, failureMessage = failureMessage)

    fun shareApkLink(asset: GitHubReleaseAssetFile) = assetActions.shareApkLink(asset)

    fun openApkInDownloader(asset: GitHubReleaseAssetFile) = assetActions.openApkInDownloader(asset)

    fun openApkInfo(asset: GitHubReleaseAssetFile) = assetActions.openApkInfo(asset)

    fun refreshApkInfo(asset: GitHubReleaseAssetFile) =
        assetActions.openApkInfo(asset, forceRefresh = true)

    fun clearApkAssetUiState(itemId: String) = assetActions.clearApkAssetUiState(itemId)

    fun clearApkAssetCache(item: GitHubTrackedApp, itemState: VersionCheckUi) =
        assetActions.clearApkAssetCache(item, itemState)

    fun collapseApkAssetPanel(item: GitHubTrackedApp, itemState: VersionCheckUi) =
        assetActions.clearApkAssetStateAndCache(item, itemState)

    fun collapseTrackedCard(item: GitHubTrackedApp, itemState: VersionCheckUi) =
        assetActions.clearApkAssetStateAndCache(item, itemState)

    fun loadApkAssets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        toggleOnlyWhenCached: Boolean = true,
        includeAllAssets: Boolean = false
    ) = assetActions.loadApkAssets(
        item = item,
        itemState = itemState,
        toggleOnlyWhenCached = toggleOnlyWhenCached,
        includeAllAssets = includeAllAssets
    )

    fun loadReleaseNotes(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        clearCache: Boolean = false
    ) = assetActions.loadReleaseNotes(
        item = item,
        itemState = itemState,
        clearCache = clearCache
    )

    fun loadReleaseNotesTargets(
        item: GitHubTrackedApp,
        itemState: VersionCheckUi,
        forceRefresh: Boolean = false
    ) = assetActions.loadReleaseNotesTargets(
        item = item,
        itemState = itemState,
        forceRefresh = forceRefresh
    )

    fun selectReleaseNotesTarget(
        item: GitHubTrackedApp,
        target: GitHubReleaseNotesTarget
    ) = assetActions.selectReleaseNotesTarget(
        item = item,
        target = target
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

    fun selectRepoScanCandidate(candidate: GitHubPackageRepositoryScanCandidate) =
        trackActions.selectRepoScanCandidate(candidate)

    fun applyTrackSheet() = trackActions.applyTrackSheet()

    fun confirmDeletePendingItem() = trackActions.confirmDeletePendingItem()

    suspend fun handlePackageChangedEvent(event: AppPackageChangedEvent) {
        val packageName = event.packageName.trim()
        if (packageName.isBlank()) return
        if (event.action !in packageUpdateActions) return
        if (event.replacing && event.action == Intent.ACTION_PACKAGE_REMOVED) return

        val matchedItems = env.state.trackedItems.filter { it.packageName == packageName }
        if (matchedItems.isEmpty()) return

        val lastHandledAt = handledAtByPackage[packageName] ?: 0L
        if ((event.atMillis - lastHandledAt).coerceAtLeast(0L) < minHandleIntervalMs) {
            return
        }
        handledAtByPackage[packageName] = event.atMillis

        refreshActions.reloadApps(forceRefresh = true)
        val uninstallAction = event.action == Intent.ACTION_PACKAGE_REMOVED ||
            event.action == Intent.ACTION_PACKAGE_FULLY_REMOVED
        matchedItems.forEach { item ->
            val wasAssetExpanded = env.state.apkAssetExpanded[item.id] == true
            val includeAllAssets = env.state.apkAssetIncludeAll[item.id] == true
            val previousState = env.state.checkStates[item.id] ?: VersionCheckUi()
            if (uninstallAction) {
                env.state.checkStates[item.id] = previousState.copy(
                    loading = true,
                    localVersion = "",
                    localVersionCode = -1L,
                    message = env.string(R.string.github_msg_checking)
                )
            }
            assetActions.clearApkAssetCache(item, previousState)
            refreshActions.refreshItem(item = item, showToastOnError = false) { updatedState ->
                if (wasAssetExpanded && canLoadApkAssets(item, updatedState)) {
                    assetActions.clearApkAssetCache(item, updatedState)
                    assetActions.loadApkAssets(
                        item = item,
                        itemState = updatedState,
                        toggleOnlyWhenCached = false,
                        includeAllAssets = includeAllAssets
                    )
                } else if (wasAssetExpanded) {
                    assetActions.clearApkAssetUiState(item.id)
                } else {
                    assetActions.clearApkAssetRuntimeState(item.id)
                }
            }
        }
    }

    private fun canLoadApkAssets(item: GitHubTrackedApp, itemState: VersionCheckUi): Boolean {
        return item.alwaysShowLatestReleaseDownloadButton ||
            itemState.hasUpdate == true ||
            itemState.recommendsPreRelease ||
            itemState.hasPreReleaseUpdate
    }

    private fun clearExpiredPendingShareImportTrack(nowMillis: Long) {
        val pending = env.state.pendingShareImportTrack ?: return
        val age = (nowMillis - pending.armedAtMillis).coerceAtLeast(0L)
        if (age <= pendingShareImportTrackMaxAgeMs) return
        clearPendingShareImportTrack(
            pending.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = env.string(R.string.github_share_import_notify_content_cancelled)
            )
        )
    }

    private fun clearPendingShareImportTrack(cancelledResult: GitHubShareImportResult? = null) {
        env.state.pendingShareImportTrack = null
        env.state.pendingShareImportResult = cancelledResult
        env.scope.launch {
            env.repository.clearPendingShareImportTrack()
            if (cancelledResult != null) {
                env.repository.saveShareImportResult(cancelledResult)
            }
            GitHubShareImportNotificationHelper.notifyCancelled(env.context)
        }
    }

    private fun buildCancelledShareImportResult(): GitHubShareImportResult? {
        val message = env.string(R.string.github_share_import_notify_content_cancelled)
        env.state.pendingShareImportAttachCandidate?.let { candidate ->
            return candidate.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = message
            )
        }
        env.state.pendingShareImportTrack?.let { pending ->
            return pending.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = message
            )
        }
        env.state.pendingShareImportPreview?.let { preview ->
            return preview.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = message
            )
        }
        return null
    }

}
