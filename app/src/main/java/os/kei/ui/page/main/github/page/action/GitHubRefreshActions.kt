package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import os.kei.feature.github.domain.GitHubRefreshRuntimeStore
import os.kei.feature.github.domain.GitHubRefreshRuntimeState
import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.domain.GitHubRefreshSource
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.resolvedRefreshTimestamp
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
    private val singleItemActions =
        GitHubSingleRefreshActions(
            owner = this,
            assetActions = assetActions,
            actionsRunRefreshCoordinator = actionsRunRefreshCoordinator,
        )
    private val backgroundRefreshCoordinator =
        GitHubBackgroundRefreshCoordinator(
            env = env,
            actionsRunRefreshCoordinator = actionsRunRefreshCoordinator,
            refreshItem = { request ->
                singleItemActions.refreshItemNow(
                    item = request.item,
                    showToastOnError = false,
                    keepCurrentVisualWhileRefreshing = true,
                    profilePurposeOverride = request.profilePurposeOverride,
                    forceRefresh = request.forceRefresh,
                    persistAfterUpdate = false,
                    refreshActionsAfterUpdate = false,
                )
            },
            persistCheckCache = { targetIds -> mergeCheckCacheNow(targetIds) },
        )
    private val batchActions =
        GitHubRefreshBatchActions(
            owner = this,
            assetActions = assetActions,
            backgroundRefreshCoordinator = backgroundRefreshCoordinator,
            actionsRunRefreshCoordinator = actionsRunRefreshCoordinator,
        )

    fun persistCheckCache(refreshTimestamp: Long? = null) {
        val states = buildCheckCacheEntries()
        val resolvedRefreshTimestamp =
            states.resolvedRefreshTimestamp(refreshTimestamp ?: state.lastRefreshMs)
        scope.launch {
            state.lastRefreshMs = repository.saveCheckCache(states, resolvedRefreshTimestamp)
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
        val runtimeState =
            GitHubRefreshRuntimeStore.state.value
                .takeIf { it.sessionId == state.refreshSessionId && state.refreshSessionId > 0L }
        val refreshSessionId = state.refreshSessionId
        val refreshScope = runtimeState?.scope ?: GitHubRefreshScope.AllTracked
        val refreshSource = runtimeState?.source ?: GitHubRefreshSource.Page
        val totalTrackedCount = runtimeState?.totalTrackedCount ?: state.trackedItems.size
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
            if (refreshSessionId > 0L) {
                GitHubRefreshRuntimeStore.cancel(
                    sessionId = refreshSessionId,
                    completedCount = checkedCount,
                    updatableCount = updatableCount,
                    preReleaseUpdateCount = preReleaseUpdateCount,
                    failedCount = failedCount,
                )
            }
            scope.launch {
                repository.notifyRefreshCancelled(
                    context = context,
                    current = checkedCount,
                    total = trackedCount,
                    preReleaseUpdateCount = preReleaseUpdateCount,
                    updatableCount = updatableCount,
                    failedCount = failedCount,
                    sessionId = refreshSessionId,
                    scope = refreshScope,
                    source = refreshSource,
                    totalTrackedCount = totalTrackedCount,
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
        state.refreshSessionId = 0L
        state.refreshTargetIds = emptySet()
        launchDeferredTrackStoreSyncIfNeeded()
        env.toast(reason)
    }

    fun applyRefreshRuntimeDisplay(runtime: GitHubRefreshRuntimeState) {
        if (runtime.running) {
            state.overviewRefreshState = OverviewRefreshState.Refreshing
            state.refreshProgress = runtime.progressFraction.coerceIn(0f, 1f)
            return
        }
        if (state.overviewRefreshState != OverviewRefreshState.Refreshing) return
        if (state.refreshAllJob?.isActive == true || backgroundRefreshCoordinator.hasActiveJobs()) return
        state.refreshProgress = 0f
        state.overviewRefreshState = cachedOrIdleOverviewState()
    }

    private fun cachedOrIdleOverviewState(): OverviewRefreshState {
        if (state.trackedItems.isEmpty()) return OverviewRefreshState.Idle
        val hasCachedForTracked =
            state.trackedItems.any { item ->
                state.checkStates.containsKey(item.id)
            }
        return if (hasCachedForTracked) {
            OverviewRefreshState.Cached
        } else {
            OverviewRefreshState.Idle
        }
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
                refreshTrackedBatch(
                    targets = targets,
                    showToast = false,
                    refreshScope = GitHubRefreshScope.DueTracked,
                )
            }
            return
        }
        val refreshedRequestedTracks =
            backgroundRefreshCoordinator.refreshRequestedTracksIfNeeded()
        val refreshedMissingTracks =
            backgroundRefreshCoordinator.refreshPartialMissingCheckStatesIfNeeded()
        when {
            refreshedRequestedTracks || refreshedMissingTracks -> {
                state.overviewRefreshState = OverviewRefreshState.Refreshing
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
            backgroundRefreshCoordinator.hasActiveJobs() -> {
                state.overviewRefreshState = OverviewRefreshState.Refreshing
            }

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
            env.viewModel.removeTrackedCardExpansion(trackId)
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
                singleItemActions.refreshItem(item = item, showToastOnError = false)
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
        singleItemActions.refreshItem(
            item = item,
            showToastOnError = showToastOnError,
            keepCurrentVisualWhileRefreshing = keepCurrentVisualWhileRefreshing,
            profilePurposeOverride = profilePurposeOverride,
            forceRefresh = forceRefresh,
            onUpdated = onUpdated,
        )

    suspend fun refreshItemNow(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = false,
        keepCurrentVisualWhileRefreshing: Boolean = false,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        persistAfterUpdate: Boolean = true,
        refreshActionsAfterUpdate: Boolean = true,
        onUpdated: ((VersionCheckUi) -> Unit)? = null,
    ) = singleItemActions.refreshItemNow(
        item = item,
        showToastOnError = showToastOnError,
        keepCurrentVisualWhileRefreshing = keepCurrentVisualWhileRefreshing,
        profilePurposeOverride = profilePurposeOverride,
        forceRefresh = forceRefresh,
        persistAfterUpdate = persistAfterUpdate,
        refreshActionsAfterUpdate = refreshActionsAfterUpdate,
        onUpdated = onUpdated,
    )

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
            refreshScope = GitHubRefreshScope.AllTracked,
            onFinished = onFinished,
        )
    }

    fun refreshTrackedBatch(
        targets: List<GitHubTrackedApp>,
        showToast: Boolean = true,
        forceRefresh: Boolean = false,
        refreshScope: GitHubRefreshScope = GitHubRefreshScope.VisibleTracked,
        onFinished: (() -> Unit)? = null,
    ) {
        batchActions.refreshTrackedBatchInternal(
            requestedTargetIds = targets.map { it.id },
            showToast = showToast,
            forceRefresh = forceRefresh,
            clearAllCheckCache = false,
            updateGlobalRefreshTimestamp = false,
            refreshScope = refreshScope,
            onFinished = onFinished,
        )
    }
}
