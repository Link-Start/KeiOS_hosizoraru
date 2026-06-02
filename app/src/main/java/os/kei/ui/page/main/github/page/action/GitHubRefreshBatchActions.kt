package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import os.kei.R
import os.kei.core.log.AppLogger
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchScheduler
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi
import java.util.concurrent.atomic.AtomicInteger

internal class GitHubRefreshBatchActions(
    private val owner: GitHubRefreshActions,
    private val assetActions: GitHubAssetActions,
    private val backgroundRefreshCoordinator: GitHubBackgroundRefreshCoordinator,
    private val actionsRunRefreshCoordinator: GitHubActionsRecommendedRunRefreshCoordinator,
) {
    private val context get() = owner.context
    private val scope get() = owner.scope
    private val state get() = owner.state
    private val repository get() = owner.repository
    private val clock get() = owner.clock

    fun refreshTrackedBatchInternal(
        requestedTargetIds: List<String>,
        showToast: Boolean,
        forceRefresh: Boolean,
        clearAllCheckCache: Boolean,
        updateGlobalRefreshTimestamp: Boolean,
        onFinished: (() -> Unit)? = null,
    ) {
        val activeItemsById = state.trackedItems.associateBy { it.id }
        val targetIds =
            selectActiveTrackedRefreshTargetIds(
                requestedTrackIds = requestedTargetIds,
                validTrackIds = activeItemsById.keys,
            )
        val snapshot = targetIds.mapNotNull { activeItemsById[it] }
        if (snapshot.isEmpty()) {
            if (showToast) {
                owner.env.toast(R.string.github_toast_no_checkable_item)
            }
            if (clearAllCheckCache && state.trackedItems.isEmpty()) {
                state.overviewRefreshState = OverviewRefreshState.Idle
                state.refreshProgress = 0f
                state.refreshTargetIds = emptySet()
                repository.cancelRefreshNotification(context)
            }
            return
        }
        backgroundRefreshCoordinator.cancel()
        actionsRunRefreshCoordinator.cancel()
        state.refreshAllJob?.cancel()
        state.refreshAllJob =
            scope.launch {
                val previousCheckStatesById = state.checkStates.toMap()
                state.refreshTargetIds = targetIds.toSet()
                assetActions.clearApkAssetCachesForTargetsNow(
                    targets =
                        snapshot.map { item ->
                            item to (previousCheckStatesById[item.id] ?: VersionCheckUi())
                        },
                    allowLatestReleaseFallback = true,
                )
                if (clearAllCheckCache) {
                    repository.clearCheckCache()
                    state.lastRefreshMs = 0L
                }
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
                    failedCount = 0,
                )
                snapshot.forEach { item ->
                    state.checkStates[item.id] =
                        VersionCheckUi(
                            loading = true,
                            message = context.getString(R.string.github_msg_checking),
                        )
                }
                val refreshStartNs = System.nanoTime()
                val progressMutex = Mutex()
                val concurrency = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(snapshot.size)
                val directApkConcurrency = GitHubTrackedRefreshBatchScheduler.directApkConcurrency(concurrency)
                val directApkSemaphore = Semaphore(directApkConcurrency)
                val workItems = GitHubTrackedRefreshBatchScheduler.buildFairRefreshOrder(snapshot)
                val nextWorkIndex = AtomicInteger(0)
                var completedCount = 0
                var lastProgressNotifyAtMs = clock.nowMs()
                val pendingUiResults = mutableListOf<Pair<GitHubTrackedApp, VersionCheckUi>>()
                supervisorScope {
                    List(concurrency) {
                        launch {
                            while (true) {
                                val workIndex = nextWorkIndex.getAndIncrement()
                                if (workIndex >= workItems.size) break
                                val item = workItems[workIndex].item
                                val resolved =
                                    runCatching {
                                        if (item.isDirectApkTrack()) {
                                            directApkSemaphore.withPermit {
                                                owner.resolveItemState(
                                                    item = item,
                                                    forceRefresh = forceRefresh,
                                                )
                                            }
                                        } else {
                                            owner.resolveItemState(
                                                item = item,
                                                forceRefresh = forceRefresh,
                                            )
                                        }
                                    }.getOrElse { throwable ->
                                        VersionCheckUi(
                                            failed = true,
                                            message =
                                                GitHubTrackedReleaseStatus.Failed.failureMessage(
                                                    throwable.message ?: throwable.javaClass.simpleName,
                                                ),
                                        )
                                    }
                                val previousState = previousCheckStatesById[item.id] ?: VersionCheckUi()
                                val itemState =
                                    owner
                                        .mergeDirectApkRemoteFallback(
                                            item = item,
                                            resolvedState = resolved,
                                            previousState = previousState,
                                        ).copy(checkedAtMillis = clock.nowMs())
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
                                    val nowMs = clock.nowMs()
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
                                        progressNotifySnapshot =
                                            GitHubRefreshProgressSnapshot(
                                                current = completedCount,
                                                total = totalCount,
                                                preReleaseUpdateCount = preReleaseUpdateCount,
                                                updatableCount = updatableCount,
                                                failedCount = failedCount,
                                            )
                                    }
                                    val shouldFlushUi =
                                        pendingUiResults.size >= GITHUB_REFRESH_UI_BATCH_SIZE ||
                                            completedCount == totalCount
                                    if (shouldFlushUi) {
                                        val activeTrackIds = state.trackedItems.mapTo(HashSet()) { it.id }
                                        pendingUiResults.forEach { (pendingItem, pendingState) ->
                                            if (pendingItem.id in activeTrackIds) {
                                                state.checkStates[pendingItem.id] = pendingState
                                            }
                                        }
                                        state.refreshProgress = completedCount.toFloat() / snapshot.size.toFloat()
                                        if (showToast) {
                                            failedToasts =
                                                pendingUiResults.filter { (_, pendingState) ->
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
                                        failedCount = progress.failedCount,
                                    )
                                }
                                failedToasts.forEach { (failedItem, failedState) ->
                                    owner.env.toast(
                                        R.string.github_toast_repo_message,
                                        failedItem.owner,
                                        failedItem.repo,
                                        failedState.message,
                                    )
                                }
                            }
                        }
                    }.joinAll()
                }
                state.overviewRefreshState =
                    if (failedCount > 0) {
                        OverviewRefreshState.Failed
                    } else {
                        OverviewRefreshState.Completed
                    }
                if (updateGlobalRefreshTimestamp) {
                    state.lastRefreshMs = clock.nowMs()
                }
                state.refreshProgress = 1f
                if (clearAllCheckCache || updateGlobalRefreshTimestamp) {
                    owner.persistCheckCacheNow()
                } else {
                    owner.mergeCheckCacheNow(targetIds = targetIds.toSet())
                }
                onFinished?.invoke()
                repository.notifyRefreshCompleted(
                    context = context,
                    total = totalCount,
                    preReleaseUpdateCount = preReleaseUpdateCount,
                    updatableCount = updatableCount,
                    failedCount = failedCount,
                )
                AppLogger.i(
                    "GitHubRefreshActions",
                    "github page refresh completed total=$totalCount " +
                        "elapsed=${elapsedMsSince(refreshStartNs)}ms " +
                        "concurrency=$concurrency directConcurrency=$directApkConcurrency " +
                        "updatable=$updatableCount prerelease=$preReleaseUpdateCount failed=$failedCount",
                )
                state.refreshTargetIds = emptySet()
                state.refreshAllJob = null
                actionsRunRefreshCoordinator.refreshItems(snapshot)
                if (owner.consumeDeferredTrackStoreSyncAfterRefresh()) {
                    owner.syncSnapshotFromStore(forceRefreshApps = false)
                }
            }
    }
}
