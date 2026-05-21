package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import os.kei.R
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi

internal const val GITHUB_TRACK_MUTATION_IMMEDIATE_REFRESH_LIMIT = 8
private const val GITHUB_TRACK_MUTATION_REFRESH_PARALLELISM = 2

internal class GitHubRefreshActions(
    internal val env: GitHubPageActionEnvironment,
    internal val assetActions: GitHubAssetActions,
) {
    internal val context get() = env.context
    internal val scope get() = env.scope
    internal val state get() = env.state
    internal val repository get() = env.repository
    internal val clock get() = env.clock
    private val localAppSyncActions = GitHubLocalAppSyncActions(env)
    private val actionsRunRefreshCoordinator = GitHubActionsRecommendedRunRefreshCoordinator(env)
    private val backgroundRefreshCoordinator =
        GitHubBackgroundRefreshCoordinator(
            env = env,
            actionsRunRefreshCoordinator = actionsRunRefreshCoordinator,
            refreshItem = { request ->
                refreshItemNow(
                    item = request.item,
                    showToastOnError = false,
                    keepCurrentVisualWhileRefreshing = true,
                    profilePurposeOverride = request.profilePurposeOverride,
                    forceRefresh = request.forceRefresh,
                    persistAfterUpdate = false,
                    refreshActionsAfterUpdate = false,
                )
            },
            persistCheckCache = { persistCheckCacheNow() },
        )
    private val batchActions =
        GitHubRefreshBatchActions(
            owner = this,
            assetActions = assetActions,
            backgroundRefreshCoordinator = backgroundRefreshCoordinator,
            actionsRunRefreshCoordinator = actionsRunRefreshCoordinator,
        )

    fun persistCheckCache(refreshTimestamp: Long = state.lastRefreshMs) {
        val states = buildCheckCacheEntries()
        scope.launch {
            repository.saveCheckCache(states, refreshTimestamp)
        }
    }

    fun cancelRefreshAll(reason: String? = null) {
        backgroundRefreshCoordinator.cancel()
        actionsRunRefreshCoordinator.cancel()
        if (state.refreshAllJob?.isActive != true) return
        state.refreshAllJob?.cancel()
        state.refreshAllJob = null
        val activeRefreshIds =
            state.refreshTargetIds.takeIf { it.isNotEmpty() }
                ?: state.trackedItems.mapTo(HashSet()) { it.id }
        val trackedCount = activeRefreshIds.size
        if (trackedCount > 0) {
            val checkedCount =
                (state.refreshProgress * trackedCount.toFloat())
                    .toInt()
                    .coerceIn(0, trackedCount)
            val updatableCount =
                state.trackedItems.count {
                    it.id in activeRefreshIds && state.checkStates[it.id]?.hasUpdate == true
                }
            val preReleaseUpdateCount =
                state.trackedItems.count {
                    it.id in activeRefreshIds && state.checkStates[it.id]?.hasPreReleaseUpdate == true
                }
            val failedCount =
                state.trackedItems.count {
                    it.id in activeRefreshIds && state.checkStates[it.id]?.failed == true
                }
            scope.launch {
                repository.notifyRefreshCancelled(
                    context = context,
                    current = checkedCount,
                    total = trackedCount,
                    preReleaseUpdateCount = preReleaseUpdateCount,
                    updatableCount = updatableCount,
                    failedCount = failedCount,
                )
            }
        } else {
            repository.cancelRefreshNotification(context)
        }
        state.overviewRefreshState =
            if (state.trackedItems.isEmpty()) {
                OverviewRefreshState.Idle
            } else if (state.checkStates.isNotEmpty()) {
                OverviewRefreshState.Cached
            } else {
                OverviewRefreshState.Idle
            }
        state.refreshProgress = 0f
        state.refreshTargetIds = emptySet()
        launchDeferredTrackStoreSyncIfNeeded()
        env.toast(reason)
    }

    suspend fun reloadApps(
        forceRefresh: Boolean = false,
        includeSystemApps: Boolean = state.lookupConfig.scanSystemAppsByDefault,
    ) = localAppSyncActions.reloadApps(
        forceRefresh = forceRefresh,
        includeSystemApps = includeSystemApps,
    )

    suspend fun initializeWarmSnapshot() {
        applyTrackSnapshot(repository.loadTrackSnapshot())
        val hasTracked = state.trackedItems.isNotEmpty()
        val hasCachedForTracked =
            state.trackedItems.any { item ->
                state.checkStates.containsKey(item.id)
            }
        state.overviewRefreshState =
            when {
                !hasTracked -> OverviewRefreshState.Idle
                hasCachedForTracked -> OverviewRefreshState.Cached
                else -> OverviewRefreshState.Idle
            }
    }

    suspend fun initializePageActiveWork() {
        repository.scheduleGitHubRefresh(context)
        reloadApps(forceRefresh = false)
        val hasTracked = state.trackedItems.isNotEmpty()
        val hasCachedForTracked =
            state.trackedItems.any { item ->
                state.checkStates.containsKey(item.id)
            }
        val dueItems =
            selectDueTrackedUpdateItems(
                trackedItems = state.trackedItems.toList(),
                checkedAtMillisById =
                    state.checkStates.mapValues { (_, value) ->
                        value.checkedAtMillis
                    },
                lastRefreshMs = state.lastRefreshMs,
                refreshIntervalHours = state.refreshIntervalHours,
                nowMs = clock.nowMs(),
            )
        if (dueItems.isNotEmpty() || (!hasCachedForTracked && hasTracked)) {
            val targets = dueItems.ifEmpty { state.trackedItems.toList() }
            repository.consumeTrackRefreshRequests(targets.mapTo(HashSet()) { it.id })
            if (targets.size == state.trackedItems.size) {
                refreshAllTracked(showToast = false)
            } else {
                refreshTrackedBatch(targets = targets, showToast = false)
            }
            return
        }
        val refreshedRequestedTracks =
            backgroundRefreshCoordinator.refreshRequestedTracksIfNeeded()
        val refreshedMissingTracks =
            backgroundRefreshCoordinator.refreshPartialMissingCheckStatesIfNeeded()
        when {
            refreshedRequestedTracks || refreshedMissingTracks -> {
                state.overviewRefreshState = OverviewRefreshState.Cached
            }

            hasCachedForTracked -> {
                state.overviewRefreshState = OverviewRefreshState.Cached
            }

            else -> {
                state.overviewRefreshState = OverviewRefreshState.Idle
            }
        }
    }

    suspend fun syncSnapshotFromStore(
        forceRefreshApps: Boolean = true,
        consumeRequestedRefreshes: Boolean = true,
    ) {
        if (state.refreshAllJob?.isActive == true) {
            state.deferredTrackStoreSyncAfterRefresh = true
            return
        }
        applyTrackSnapshot(repository.loadTrackSnapshot())
        if (forceRefreshApps) {
            reloadApps(forceRefresh = true)
        }
        if (consumeRequestedRefreshes) {
            backgroundRefreshCoordinator.refreshRequestedTracksIfNeeded()
        }
        val refreshedMissingTracks =
            if (forceRefreshApps) {
                backgroundRefreshCoordinator.refreshPartialMissingCheckStatesIfNeeded()
            } else {
                false
            }
        val hasTracked = state.trackedItems.isNotEmpty()
        val hasCachedForTracked =
            state.trackedItems.any { item ->
                state.checkStates.containsKey(item.id)
            }
        when {
            !hasTracked -> {
                state.overviewRefreshState = OverviewRefreshState.Idle
            }

            hasCachedForTracked || refreshedMissingTracks -> {
                state.overviewRefreshState = OverviewRefreshState.Cached
            }

            else -> {
                state.overviewRefreshState = OverviewRefreshState.Idle
            }
        }
    }

    suspend fun handleTrackMutationRefresh(
        affectedTrackIds: Set<String>,
        removedTrackIds: Set<String>,
    ) {
        val previousItemsById = state.trackedItems.associateBy { it.id }
        val previousCheckStatesById = state.checkStates.toMap()
        val staleAssetTrackIds =
            (affectedTrackIds + removedTrackIds)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        val staleAssetTargets =
            staleAssetTrackIds.mapNotNull { trackId ->
                previousItemsById[trackId]?.let { item ->
                    item to (previousCheckStatesById[trackId] ?: VersionCheckUi())
                }
            }
        assetActions.clearApkAssetStatesAndCachesNow(
            targets = staleAssetTargets,
            clearItemIds = staleAssetTrackIds,
        )
        removedTrackIds.forEach { trackId ->
            state.checkStates.remove(trackId)
            state.trackedCardExpanded.remove(trackId)
            state.trackedAddedAtById.remove(trackId)
            state.trackedModifiedAtById.remove(trackId)
        }
        syncSnapshotFromStore(
            forceRefreshApps = true,
            consumeRequestedRefreshes = false,
        )
        val trackedById = state.trackedItems.associateBy { it.id }
        val focusTrackId =
            affectedTrackIds
                .asSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && it in trackedById }
        val immediateIds =
            selectImmediateTrackMutationRefreshIds(
                affectedTrackIds = affectedTrackIds,
                validTrackIds = trackedById.keys,
            )
        if (immediateIds.isEmpty()) {
            focusTrackId?.let(state::requestTrackCardFocus)
            return
        }
        repository.consumeTrackRefreshRequests(immediateIds.toSet())
        val semaphore = Semaphore(GITHUB_TRACK_MUTATION_REFRESH_PARALLELISM)
        supervisorScope {
            immediateIds
                .mapNotNull { trackedById[it] }
                .map { item ->
                    launch {
                        semaphore.withPermit {
                            refreshItemNow(
                                item = item,
                                showToastOnError = false,
                                keepCurrentVisualWhileRefreshing = true,
                                profilePurposeOverride = GitHubRepositoryProfilePurpose.VersionCheckFast,
                                forceRefresh = true,
                            )
                        }
                    }
                }.joinAll()
        }
        val hasCachedForTracked =
            state.trackedItems.any { item ->
                state.checkStates.containsKey(item.id)
            }
        state.overviewRefreshState =
            if (hasCachedForTracked) {
                OverviewRefreshState.Cached
            } else {
                OverviewRefreshState.Idle
            }
        focusTrackId?.let(state::requestTrackCardFocus)
    }

    suspend fun syncLocalAppStateWithInstalledApps(forceRefreshApps: Boolean = true) =
        localAppSyncActions.syncLocalAppStateWithInstalledApps(
            forceRefreshApps = forceRefreshApps,
            onShouldRefreshItem = { item ->
                refreshItem(item = item, showToastOnError = false)
            },
        )

    fun refreshItem(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = false,
        keepCurrentVisualWhileRefreshing: Boolean = false,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        onUpdated: ((VersionCheckUi) -> Unit)? = null,
    ): Job =
        scope.launch {
            refreshItemNow(
                item = item,
                showToastOnError = showToastOnError,
                keepCurrentVisualWhileRefreshing = keepCurrentVisualWhileRefreshing,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh,
                onUpdated = onUpdated,
            )
        }

    suspend fun refreshItemNow(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = false,
        keepCurrentVisualWhileRefreshing: Boolean = false,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        persistAfterUpdate: Boolean = true,
        refreshActionsAfterUpdate: Boolean = true,
        onUpdated: ((VersionCheckUi) -> Unit)? = null,
    ) {
        val previousState = state.checkStates[item.id] ?: VersionCheckUi()
        assetActions.clearApkAssetCacheNow(
            item = item,
            itemState = previousState,
            allowLatestReleaseFallback = true,
        )
        val checkingMessage = context.getString(R.string.github_msg_checking)
        state.checkStates[item.id] =
            if (keepCurrentVisualWhileRefreshing) {
                previousState.copy(message = checkingMessage)
            } else {
                previousState.copy(
                    loading = true,
                    message = checkingMessage,
                )
            }
        val itemState =
            mergeDirectApkRemoteFallback(
                item = item,
                resolvedState =
                    resolveItemState(
                        item = item,
                        profilePurposeOverride = profilePurposeOverride,
                        forceRefresh = forceRefresh,
                    ),
                previousState = previousState,
            ).copy(checkedAtMillis = clock.nowMs())
        if (state.trackedItems.none { it.id == item.id }) return
        if (showToastOnError && itemState.failed) {
            env.toast(itemState.message)
        }
        state.checkStates[item.id] = itemState
        if (persistAfterUpdate) persistCheckCacheNow()
        if (refreshActionsAfterUpdate) actionsRunRefreshCoordinator.refreshItemInBackground(item)
        onUpdated?.invoke(itemState)
    }

    fun refreshAllTracked(
        showToast: Boolean = true,
        forceRefresh: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        batchActions.refreshTrackedBatchInternal(
            requestedTargetIds = state.trackedItems.map { it.id },
            showToast = showToast,
            forceRefresh = forceRefresh,
            clearAllCheckCache = true,
            updateGlobalRefreshTimestamp = true,
            onFinished = onFinished,
        )
    }

    fun refreshTrackedBatch(
        targets: List<GitHubTrackedApp>,
        showToast: Boolean = true,
        forceRefresh: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        batchActions.refreshTrackedBatchInternal(
            requestedTargetIds = targets.map { it.id },
            showToast = showToast,
            forceRefresh = forceRefresh,
            clearAllCheckCache = false,
            updateGlobalRefreshTimestamp = false,
            onFinished = onFinished,
        )
    }
}
