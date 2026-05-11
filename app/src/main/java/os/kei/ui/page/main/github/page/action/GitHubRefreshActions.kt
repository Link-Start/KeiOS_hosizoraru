package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import os.kei.R
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.domain.GitHubActionsUpdateCheckService
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.forTrackedItem
import os.kei.feature.github.model.githubCheckSourceSignature
import os.kei.feature.github.notification.GitHubActionsUpdateNotificationHelper
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseUiStateStore
import os.kei.ui.page.main.github.share.toShareImportAttachCandidate
import os.kei.ui.page.main.github.share.toShareImportPreview
import os.kei.ui.page.main.github.share.toShareImportResult
import os.kei.ui.page.main.github.share.toShareImportTrack
import os.kei.ui.page.main.github.state.toCacheEntry
import os.kei.ui.page.main.github.state.toUi

private const val GITHUB_REFRESH_ALL_PARALLELISM = 4
private const val GITHUB_REFRESH_UI_BATCH_SIZE = 4
private const val GITHUB_REFRESH_PROGRESS_NOTIFY_BATCH_SIZE = 2
private const val GITHUB_REFRESH_PROGRESS_NOTIFY_MIN_INTERVAL_MS = 500L
private const val GITHUB_REFRESH_PROGRESS_NOTIFY_INTERVAL_MS = 850L
internal const val GITHUB_TRACK_MUTATION_IMMEDIATE_REFRESH_LIMIT = 8
private const val GITHUB_TRACK_MUTATION_REFRESH_PARALLELISM = 2

private data class GitHubRefreshProgressSnapshot(
    val current: Int,
    val total: Int,
    val preReleaseUpdateCount: Int,
    val updatableCount: Int,
    val failedCount: Int
)

internal class GitHubRefreshActions(
    private val env: GitHubPageActionEnvironment,
    private val assetActions: GitHubAssetActions
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val repository get() = env.repository
    private val appListReloadMutex = Mutex()

    fun persistCheckCache(refreshTimestamp: Long = state.lastRefreshMs) {
        val states = buildCheckCacheEntries()
        scope.launch {
            repository.saveCheckCache(states, refreshTimestamp)
        }
    }

    fun cancelRefreshAll(reason: String? = null) {
        if (state.refreshAllJob?.isActive != true) return
        state.refreshAllJob?.cancel()
        state.refreshAllJob = null
        val trackedCount = state.trackedItems.size
        if (trackedCount > 0) {
            val checkedCount = (state.refreshProgress * trackedCount.toFloat()).toInt()
                .coerceIn(0, trackedCount)
            val updatableCount = state.trackedItems.count { state.checkStates[it.id]?.hasUpdate == true }
            val preReleaseUpdateCount =
                state.trackedItems.count { state.checkStates[it.id]?.hasPreReleaseUpdate == true }
            val failedCount = state.trackedItems.count { state.checkStates[it.id]?.failed == true }
            scope.launch {
                repository.notifyRefreshCancelled(
                    context = context,
                    current = checkedCount,
                    total = trackedCount,
                    preReleaseUpdateCount = preReleaseUpdateCount,
                    updatableCount = updatableCount,
                    failedCount = failedCount
                )
            }
        } else {
            repository.cancelRefreshNotification(context)
        }
        state.overviewRefreshState = if (state.trackedItems.isEmpty()) {
            OverviewRefreshState.Idle
        } else if (state.checkStates.isNotEmpty()) {
            OverviewRefreshState.Cached
        } else {
            OverviewRefreshState.Idle
        }
        state.refreshProgress = 0f
        env.toast(reason)
    }

    suspend fun reloadApps(forceRefresh: Boolean = false) {
        appListReloadMutex.withLock {
            state.appListRefreshing = true
            try {
                state.appList = repository.queryInstalledLaunchableApps(
                    context = context,
                    forceRefresh = forceRefresh
                )
                val trackedPackages = state.trackedItems
                    .map { it.packageName.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                repository.preloadAppIcons(
                    context = context,
                    packageNames = trackedPackages
                )
                state.appListLoaded = true
            } finally {
                state.appListRefreshing = false
            }
        }
    }

    suspend fun initializeWarmSnapshot() {
        applyTrackSnapshot(repository.loadTrackSnapshot())
        val hasTracked = state.trackedItems.isNotEmpty()
        val hasCachedForTracked = state.trackedItems.any { item ->
            state.checkStates.containsKey(item.id)
        }
        state.overviewRefreshState = when {
            !hasTracked -> OverviewRefreshState.Idle
            hasCachedForTracked -> OverviewRefreshState.Cached
            else -> OverviewRefreshState.Idle
        }
    }

    suspend fun initializePageActiveWork() {
        repository.scheduleGitHubRefresh(context)
        reloadApps(forceRefresh = false)
        val refreshedRequestedTracks = refreshRequestedTracksIfNeeded()
        val hasTracked = state.trackedItems.isNotEmpty()
        val hasCachedForTracked = state.trackedItems.any { item ->
            state.checkStates.containsKey(item.id)
        }
        val stale = hasTracked && state.lastRefreshMs > 0L &&
            (System.currentTimeMillis() - state.lastRefreshMs) >=
            state.refreshIntervalHours * 60L * 60L * 1000L
        when {
            refreshedRequestedTracks -> state.overviewRefreshState = OverviewRefreshState.Cached
            !hasCachedForTracked && hasTracked -> refreshAllTracked(showToast = false)
            stale -> refreshAllTracked(showToast = false)
            hasCachedForTracked -> state.overviewRefreshState = OverviewRefreshState.Cached
            else -> state.overviewRefreshState = OverviewRefreshState.Idle
        }
    }

    suspend fun syncSnapshotFromStore(
        forceRefreshApps: Boolean = true,
        consumeRequestedRefreshes: Boolean = true
    ) {
        applyTrackSnapshot(repository.loadTrackSnapshot())
        if (forceRefreshApps) {
            reloadApps(forceRefresh = true)
        }
        if (consumeRequestedRefreshes) {
            refreshRequestedTracksIfNeeded()
        }
        val hasTracked = state.trackedItems.isNotEmpty()
        val hasCachedForTracked = state.trackedItems.any { item ->
            state.checkStates.containsKey(item.id)
        }
        state.overviewRefreshState = when {
            !hasTracked -> OverviewRefreshState.Idle
            hasCachedForTracked -> OverviewRefreshState.Cached
            else -> OverviewRefreshState.Idle
        }
    }

    suspend fun handleTrackMutationRefresh(
        affectedTrackIds: Set<String>,
        removedTrackIds: Set<String>
    ) {
        val previousItemsById = state.trackedItems.associateBy { it.id }
        val previousCheckStatesById = state.checkStates.toMap()
        val staleAssetTrackIds = (affectedTrackIds + removedTrackIds)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val staleAssetTargets = staleAssetTrackIds.mapNotNull { trackId ->
            previousItemsById[trackId]?.let { item ->
                item to (previousCheckStatesById[trackId] ?: VersionCheckUi())
            }
        }
        assetActions.clearApkAssetStatesAndCachesNow(
            targets = staleAssetTargets,
            clearItemIds = staleAssetTrackIds
        )
        removedTrackIds.forEach { trackId ->
            state.checkStates.remove(trackId)
            state.trackedCardExpanded.remove(trackId)
            state.trackedAddedAtById.remove(trackId)
        }
        syncSnapshotFromStore(
            forceRefreshApps = true,
            consumeRequestedRefreshes = false
        )
        val trackedById = state.trackedItems.associateBy { it.id }
        val focusTrackId = affectedTrackIds
            .asSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it in trackedById }
        val immediateIds = selectImmediateTrackMutationRefreshIds(
            affectedTrackIds = affectedTrackIds,
            validTrackIds = trackedById.keys
        )
        if (immediateIds.isEmpty()) {
            focusTrackId?.let(state::requestTrackCardFocus)
            return
        }
        repository.consumeTrackRefreshRequests(immediateIds.toSet())
        val semaphore = Semaphore(GITHUB_TRACK_MUTATION_REFRESH_PARALLELISM)
        supervisorScope {
            immediateIds.mapNotNull { trackedById[it] }.map { item ->
                launch {
                    semaphore.withPermit {
                        refreshItemNow(
                            item = item,
                            showToastOnError = false,
                            keepCurrentVisualWhileRefreshing = true,
                            profilePurposeOverride = GitHubRepositoryProfilePurpose.VersionCheckFast
                        )
                    }
                }
            }.joinAll()
        }
        val hasCachedForTracked = state.trackedItems.any { item ->
            state.checkStates.containsKey(item.id)
        }
        state.overviewRefreshState = if (hasCachedForTracked) {
            OverviewRefreshState.Cached
        } else {
            OverviewRefreshState.Idle
        }
        focusTrackId?.let(state::requestTrackCardFocus)
    }

    suspend fun syncLocalAppStateWithInstalledApps(forceRefreshApps: Boolean = true) {
        if (forceRefreshApps) {
            reloadApps(forceRefresh = true)
        }
        val trackedSnapshot = state.trackedItems.toList()
        if (trackedSnapshot.isEmpty()) return

        trackedSnapshot.forEach { item ->
            val packageName = item.packageName.trim()
            if (packageName.isBlank()) return@forEach

            val latestLocalVersionInfo = runCatching {
                repository.localVersionInfoOrNull(context, packageName)
            }.getOrNull()
            val installed = latestLocalVersionInfo != null

            val cachedState = state.checkStates[item.id] ?: return@forEach
            val cachedUninstalled = cachedState.isLocalAppUninstalled()
            val cachedVersionCode = cachedState.localVersionCode
            val latestVersionCode = latestLocalVersionInfo?.versionCode ?: -1L
            val shouldRefresh = when {
                installed && cachedUninstalled -> true
                !installed && !cachedUninstalled -> true
                installed && cachedVersionCode != latestVersionCode -> true
                else -> false
            }
            if (shouldRefresh) {
                refreshItem(item = item, showToastOnError = false)
            }
        }
    }

    fun refreshItem(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = false,
        keepCurrentVisualWhileRefreshing: Boolean = false,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        onUpdated: ((VersionCheckUi) -> Unit)? = null
    ): Job {
        return scope.launch {
            refreshItemNow(
                item = item,
                showToastOnError = showToastOnError,
                keepCurrentVisualWhileRefreshing = keepCurrentVisualWhileRefreshing,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh,
                onUpdated = onUpdated
            )
        }
    }

    suspend fun refreshItemNow(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = false,
        keepCurrentVisualWhileRefreshing: Boolean = false,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        onUpdated: ((VersionCheckUi) -> Unit)? = null
    ) {
        val previousState = state.checkStates[item.id] ?: VersionCheckUi()
        assetActions.clearApkAssetCacheNow(
            item = item,
            itemState = previousState,
            allowLatestReleaseFallback = true
        )
        val checkingMessage = context.getString(R.string.github_msg_checking)
        state.checkStates[item.id] = if (keepCurrentVisualWhileRefreshing) {
            previousState.copy(message = checkingMessage)
        } else {
            previousState.copy(
                loading = true,
                message = checkingMessage
            )
        }
        val itemState = resolveItemState(
            item = item,
            profilePurposeOverride = profilePurposeOverride,
            forceRefresh = forceRefresh
        )
        if (state.trackedItems.none { it.id == item.id }) return
        if (showToastOnError && itemState.failed) {
            env.toast(itemState.message)
        }
        state.checkStates[item.id] = itemState
        persistCheckCacheNow()
        refreshActionsRunSnapshotForItem(item)
        onUpdated?.invoke(itemState)
    }

    fun refreshAllTracked(
        showToast: Boolean = true,
        onFinished: (() -> Unit)? = null
    ) {
        val snapshot = state.trackedItems.toList()
        if (snapshot.isEmpty()) {
            if (showToast) {
                env.toast(R.string.github_toast_no_checkable_item)
            }
            state.overviewRefreshState = OverviewRefreshState.Idle
            state.refreshProgress = 0f
            repository.cancelRefreshNotification(context)
            return
        }
        state.refreshAllJob?.cancel()
        state.refreshAllJob = scope.launch {
            val previousCheckStatesById = state.checkStates.toMap()
            assetActions.clearApkAssetCachesForTargetsNow(
                targets = snapshot.map { item ->
                    item to (previousCheckStatesById[item.id] ?: VersionCheckUi())
                },
                allowLatestReleaseFallback = true
            )
            repository.clearCheckCache()
            state.lastRefreshMs = 0L
            state.overviewRefreshState = OverviewRefreshState.Refreshing
            state.refreshProgress = 0f
            val totalCount = snapshot.size
            var updatableCount = 0
            var preReleaseUpdateCount = 0
            var failedCount = 0
            repository.notifyRefreshProgress(
                context = context,
                current = 0,
                total = totalCount,
                preReleaseUpdateCount = 0,
                updatableCount = 0,
                failedCount = 0
            )
            snapshot.forEach { item ->
                state.checkStates[item.id] = VersionCheckUi(
                    loading = true,
                    message = context.getString(R.string.github_msg_checking)
                )
            }
            val progressMutex = Mutex()
            val semaphore = Semaphore(GITHUB_REFRESH_ALL_PARALLELISM)
            var completedCount = 0
            var lastProgressNotifyAtMs = System.currentTimeMillis()
            val pendingUiResults = mutableListOf<Pair<GitHubTrackedApp, VersionCheckUi>>()
            supervisorScope {
                snapshot.map { item ->
                    launch {
                        val itemState = semaphore.withPermit {
                            runCatching {
                                resolveItemState(item)
                            }.getOrElse { throwable ->
                                VersionCheckUi(
                                    failed = true,
                                    message = throwable.message ?: throwable.javaClass.simpleName
                                )
                            }
                        }
                        if (item.checkActionsUpdates) {
                            refreshActionsRunSnapshotForItem(item)
                        }
                        var progressNotifySnapshot: GitHubRefreshProgressSnapshot? = null
                        var failedToasts = emptyList<Pair<GitHubTrackedApp, VersionCheckUi>>()
                        progressMutex.withLock {
                            if (itemState.hasUpdate == true) {
                                updatableCount += 1
                            }
                            if (itemState.hasPreReleaseUpdate) {
                                preReleaseUpdateCount += 1
                            }
                            if (itemState.failed) {
                                failedCount += 1
                            }
                            completedCount += 1
                            pendingUiResults += item to itemState
                            val nowMs = System.currentTimeMillis()
                            val progressNotifyAgeMs = nowMs - lastProgressNotifyAtMs
                            val shouldNotifyProgress =
                                completedCount < totalCount &&
                                    (
                                        progressNotifyAgeMs >= GITHUB_REFRESH_PROGRESS_NOTIFY_INTERVAL_MS ||
                                            (
                                                completedCount % GITHUB_REFRESH_PROGRESS_NOTIFY_BATCH_SIZE == 0 &&
                                                    progressNotifyAgeMs >=
                                                    GITHUB_REFRESH_PROGRESS_NOTIFY_MIN_INTERVAL_MS
                                                )
                                        )
                            if (shouldNotifyProgress) {
                                lastProgressNotifyAtMs = nowMs
                                progressNotifySnapshot = GitHubRefreshProgressSnapshot(
                                    current = completedCount,
                                    total = totalCount,
                                    preReleaseUpdateCount = preReleaseUpdateCount,
                                    updatableCount = updatableCount,
                                    failedCount = failedCount
                                )
                            }
                            val shouldFlushUi = pendingUiResults.size >= GITHUB_REFRESH_UI_BATCH_SIZE ||
                                completedCount == totalCount
                            if (shouldFlushUi) {
                                pendingUiResults.forEach { (pendingItem, pendingState) ->
                                    if (state.trackedItems.any { tracked -> tracked.id == pendingItem.id }) {
                                        state.checkStates[pendingItem.id] = pendingState
                                    }
                                }
                                state.refreshProgress = completedCount.toFloat() / snapshot.size.toFloat()
                                if (showToast) {
                                    failedToasts = pendingUiResults.filter { (_, pendingState) ->
                                        pendingState.failed
                                    }
                                }
                                pendingUiResults.clear()
                            }
                        }
                        progressNotifySnapshot?.let { progress ->
                            repository.notifyRefreshProgress(
                                context = context,
                                current = progress.current,
                                total = progress.total,
                                preReleaseUpdateCount = progress.preReleaseUpdateCount,
                                updatableCount = progress.updatableCount,
                                failedCount = progress.failedCount
                            )
                        }
                        failedToasts.forEach { (failedItem, failedState) ->
                            env.toast(
                                R.string.github_toast_repo_message,
                                failedItem.owner,
                                failedItem.repo,
                                failedState.message
                            )
                        }
                    }
                }.joinAll()
            }
            state.overviewRefreshState = if (failedCount > 0) {
                OverviewRefreshState.Failed
            } else {
                OverviewRefreshState.Completed
            }
            state.lastRefreshMs = System.currentTimeMillis()
            state.refreshProgress = 1f
            persistCheckCacheNow(state.lastRefreshMs)
            onFinished?.invoke()
            repository.notifyRefreshCompleted(
                context = context,
                total = totalCount,
                preReleaseUpdateCount = preReleaseUpdateCount,
                updatableCount = updatableCount,
                failedCount = failedCount
            )
            state.refreshAllJob = null
        }
    }

    private suspend fun refreshRequestedTracksIfNeeded(): Boolean {
        val trackedById = state.trackedItems.associateBy { it.id }
        if (trackedById.isEmpty()) return false
        val requestedIds = repository.consumeTrackRefreshRequests(trackedById.keys)
        if (requestedIds.isEmpty()) return false
        requestedIds.forEach { trackId ->
            val item = trackedById[trackId] ?: return@forEach
            refreshItem(item = item, showToastOnError = false)
        }
        return true
    }

    private suspend fun persistCheckCacheNow(refreshTimestamp: Long = state.lastRefreshMs) {
        repository.saveCheckCache(buildCheckCacheEntries(), refreshTimestamp)
    }

    private fun buildCheckCacheEntries() =
        state.trackedItems.associate { item ->
            val itemState = state.checkStates[item.id] ?: VersionCheckUi()
            item.id to itemState.toCacheEntry()
        }

    private suspend fun resolveItemState(
        item: GitHubTrackedApp,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false
    ): VersionCheckUi {
        return repository.evaluateTrackedApp(
            context = context,
            item = item,
            profilePurposeOverride = profilePurposeOverride,
            forceRefresh = forceRefresh
        )
    }

    private suspend fun applyTrackSnapshot(trackSnapshot: GitHubTrackSnapshot) {
        val previousItemsById = state.trackedItems.associateBy { it.id }
        val previousCheckStatesById = state.checkStates.toMap()
        val activeStrategyId = trackSnapshot.lookupConfig.selectedStrategy.storageId
        val snapshotAssetSourceSignature = state.buildAssetSourceSignature(trackSnapshot.lookupConfig)
        val incomingItemsById = trackSnapshot.items.associateBy { it.id }
        val changedAssetTrackIds =
            (previousItemsById.keys - incomingItemsById.keys) +
                    incomingItemsById
                        .filter { (trackId, incomingItem) ->
                            previousItemsById[trackId]?.let { previousItem ->
                                previousItem != incomingItem
                            } == true
                        }
                        .keys
        val assetSignatureChanged =
            state.assetSourceSignature.isNotBlank() &&
                    state.assetSourceSignature != snapshotAssetSourceSignature
        state.lookupConfig = trackSnapshot.lookupConfig
        state.selectedStrategyInput = trackSnapshot.lookupConfig.selectedStrategy
        state.selectedActionsStrategyInput = trackSnapshot.lookupConfig.actionsStrategy
        state.githubApiTokenInput = trackSnapshot.lookupConfig.apiToken
        state.checkAllTrackedPreReleasesInput = trackSnapshot.lookupConfig.checkAllTrackedPreReleases
        state.aggressiveApkFilteringInput = trackSnapshot.lookupConfig.aggressiveApkFiltering
        state.preciseApkVersionEnabledInput = trackSnapshot.lookupConfig.preciseApkVersionEnabled
        state.profileDepthInput = trackSnapshot.lookupConfig.profileDepth
        state.shareImportLinkageEnabledInput = trackSnapshot.lookupConfig.shareImportLinkageEnabled
        state.shareImportFlowModeInput = trackSnapshot.lookupConfig.shareImportFlowMode
        state.appManagedShareInstallEnabledInput =
            trackSnapshot.lookupConfig.appManagedShareInstallEnabled
        state.onlineShareTargetPackageInput = trackSnapshot.lookupConfig.onlineShareTargetPackage
        state.preferredDownloaderPackageInput = trackSnapshot.lookupConfig.preferredDownloaderPackage
        state.refreshIntervalHours = trackSnapshot.refreshIntervalHours
        when {
            assetSignatureChanged -> {
                assetActions.clearAllApkAssetStateAndCacheNow()
            }

            changedAssetTrackIds.isNotEmpty() -> {
                val staleAssetTargets = changedAssetTrackIds.mapNotNull { trackId ->
                    previousItemsById[trackId]?.let { item ->
                        item to (previousCheckStatesById[trackId] ?: VersionCheckUi())
                    }
                }
                assetActions.clearApkAssetStatesAndCachesNow(
                    targets = staleAssetTargets,
                    clearItemIds = changedAssetTrackIds
                )
            }
        }
        state.assetSourceSignature = snapshotAssetSourceSignature

        state.trackedItems.clear()
        state.trackedItems.addAll(trackSnapshot.items)
        val validItemIds = trackSnapshot.items.map { it.id }.toSet()
        state.retainTrackedUiState(validItemIds)
        GitHubTrackedReleaseUiStateStore.retain(validItemIds)
        state.trackedFirstInstallAtByPackage.clear()
        state.trackedFirstInstallAtByPackage.putAll(trackSnapshot.trackedFirstInstallAtByPackage)
        state.retainTrackedFirstInstallAtByTrackedItems()
        state.trackedAddedAtById.clear()
        state.trackedAddedAtById.putAll(trackSnapshot.trackedAddedAtById)
        state.retainTrackedAddedAtByTrackedItems()
        state.actionsRecommendedRunSnapshots.clear()
        trackSnapshot.items.filter { it.checkActionsUpdates }.forEach { item ->
            GitHubActionsRecommendedRunStore.load(item.id)?.let { snapshot ->
                state.actionsRecommendedRunSnapshots[item.id] = snapshot
            }
        }
        state.pendingShareImportTrack = trackSnapshot.pendingShareImportTrack?.toShareImportTrack()
        state.pendingShareImportPreview = GitHubShareImportFlowStore
            .loadActivePreview()
            ?.toShareImportPreview()
        state.pendingShareImportAttachCandidate = GitHubShareImportFlowStore
            .loadActiveAttachCandidate()
            ?.toShareImportAttachCandidate()
        state.pendingShareImportResult = GitHubShareImportFlowStore
            .loadActiveResult()
            ?.toShareImportResult()

        val cachedStates = trackSnapshot.checkCache
        state.checkStates.clear()
        trackSnapshot.items.forEach { item ->
            val itemLookupConfig = trackSnapshot.lookupConfig.forTrackedItem(item)
            val activeCheckSignature = itemLookupConfig.githubCheckSourceSignature()
            cachedStates[item.id]
                ?.takeIf { cache ->
                    val sourceId = cache.sourceStrategyId.ifBlank {
                        GitHubLookupStrategyOption.AtomFeed.storageId
                    }
                    when {
                        cache.sourceConfigSignature.isNotBlank() ->
                            cache.sourceConfigSignature == activeCheckSignature

                        itemLookupConfig.preciseApkVersionEnabled -> false
                        else -> sourceId == activeStrategyId
                    }
                }
                ?.let { cached ->
                    state.checkStates[item.id] = cached.toUi()
                }
        }
        state.lastRefreshMs = if (state.checkStates.isNotEmpty()) {
            trackSnapshot.lastRefreshMs
        } else {
            0L
        }
    }

    private suspend fun refreshActionsRunSnapshotForItem(item: GitHubTrackedApp) {
        if (!item.checkActionsUpdates) {
            GitHubActionsRecommendedRunStore.remove(item.id)
            state.actionsRecommendedRunSnapshots.remove(item.id)
            return
        }
        val previous = GitHubActionsRecommendedRunStore.load(item.id)
        val current = GitHubActionsUpdateCheckService().fetchRecommendedRunSnapshot(
            item = item,
            lookupConfig = state.lookupConfig,
            previousWorkflowId = previous?.workflowId
        ).getOrNull() ?: return
        GitHubActionsRecommendedRunStore.save(current)
        state.actionsRecommendedRunSnapshots[item.id] = current
        if (previous != null && current.isNewerThan(previous)) {
            GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                context = context,
                snapshot = current
            )
        }
    }
}

internal fun selectImmediateTrackMutationRefreshIds(
    affectedTrackIds: Set<String>,
    validTrackIds: Set<String>,
    limit: Int = GITHUB_TRACK_MUTATION_IMMEDIATE_REFRESH_LIMIT
): List<String> {
    if (affectedTrackIds.isEmpty() || validTrackIds.isEmpty() || limit <= 0) return emptyList()
    return affectedTrackIds
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && it in validTrackIds }
        .distinct()
        .take(limit)
        .toList()
}
