package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.log.AppLogger
import os.kei.feature.github.domain.GitHubRefreshHistoryService
import os.kei.feature.github.domain.GitHubRefreshRuntimeStore
import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.domain.GitHubRefreshSource
import os.kei.feature.github.domain.GitHubTrackedRefreshFailure
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchEvaluator
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchRunner
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchScheduler
import os.kei.feature.github.model.GitHubRefreshHistoryOutcome
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.state.toUi

internal class GitHubRefreshBatchActions(
    private val owner: GitHubRefreshActions,
    private val backgroundRefreshCoordinator: GitHubBackgroundRefreshCoordinator,
    private val actionsRunRefreshCoordinator: GitHubActionsRecommendedRunRefreshCoordinator,
) {
    private val context get() = owner.context
    private val durableScope get() = owner.durableScope
    private val state get() = owner.state
    private val repository get() = owner.repository
    private val clock get() = owner.clock
    private val refreshHistoryService = GitHubRefreshHistoryService()

    fun refreshTrackedBatchInternal(
        requestedTargetIds: List<String>,
        showToast: Boolean,
        forceRefresh: Boolean,
        clearAllCheckCache: Boolean,
        updateGlobalRefreshTimestamp: Boolean,
        refreshScope: GitHubRefreshScope,
        onFinished: (() -> Unit)? = null,
    ) {
        val refreshPlan = planGitHubTrackedBatchRefresh(
            requestedTargetIds = requestedTargetIds,
            activeItems = state.trackedItems.toList(),
            refreshScope = refreshScope,
            updateGlobalRefreshTimestamp = updateGlobalRefreshTimestamp,
        )
        val snapshot = refreshPlan.targets
        val targetIds = refreshPlan.targetIds
        if (snapshot.isEmpty()) {
            if (showToast) {
                owner.env.toast(R.string.github_toast_no_checkable_item)
            }
            if (clearAllCheckCache && state.trackedItems.isEmpty()) {
                state.overviewRefreshState = OverviewRefreshState.Idle
                state.refreshProgress = 0f
                state.refreshTargetIds = emptySet()
                repository.cancelRefreshNotification(
                    context = context,
                    sessionId = state.refreshSessionId,
                )
            }
            return
        }
        backgroundRefreshCoordinator.cancel(
            reason = GitHubRefreshCancelSignal.SUPERSEDED_BY_PAGE_BATCH_REASON,
        )
        actionsRunRefreshCoordinator.cancel()
        state.refreshAllJob?.cancel(
            CancellationException(GitHubRefreshCancelSignal.SUPERSEDED_BY_PAGE_BATCH_REASON),
        )
        AppLogger.d("GitHubRefreshActions") {
            "enqueue page refresh scope=${refreshPlan.refreshScope} target=${snapshot.size}/${state.trackedItems.size} " +
                "force=$forceRefresh clearAll=$clearAllCheckCache updateGlobal=$updateGlobalRefreshTimestamp"
        }
        state.refreshAllJob =
            durableScope.launch {
                val refreshStartedAtMs = clock.nowMs()
                val runtimeSession =
                    checkNotNull(
                        GitHubRefreshRuntimeStore.begin(
                            scope = refreshPlan.refreshScope,
                            source = GitHubRefreshSource.Page,
                            totalTrackedCount = state.trackedItems.size,
                            targetCount = snapshot.size,
                            targetTrackIds = targetIds,
                            nowMs = refreshStartedAtMs,
                        ),
                    )
                AppLogger.d("GitHubRefreshActions") {
                    "begin page refresh session=${runtimeSession.id} scope=${runtimeSession.scope} " +
                        "source=${runtimeSession.source} target=${snapshot.size}/${state.trackedItems.size}"
                }
                state.refreshSessionId = runtimeSession.id
                val previousCheckStatesById = state.checkStates.toMap()
                val totalCount = snapshot.size
                var updatableCount = 0
                var preReleaseUpdateCount = 0
                var failedCount = 0
                var completedCount = 0
                try {
                    state.refreshTargetIds = targetIds.toSet()
                    if (clearAllCheckCache) {
                        repository.clearCheckCache()
                        state.lastRefreshMs = 0L
                    }
                    state.overviewRefreshState = OverviewRefreshState.Refreshing
                    state.refreshProgress = 0f
                    repository.notifyRefreshProgress(
                        context = context,
                        current = 0,
                        total = totalCount,
                        preReleaseUpdateCount = 0,
                        updatableCount = 0,
                        failedCount = 0,
                        sessionId = runtimeSession.id,
                        scope = runtimeSession.scope,
                        source = runtimeSession.source,
                        totalTrackedCount = state.trackedItems.size,
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
                    val fdroidConcurrency = GitHubTrackedRefreshBatchScheduler.fdroidConcurrency(concurrency)
                    val batchEvaluator =
                        GitHubTrackedRefreshBatchEvaluator(
                            trackedItems = snapshot,
                            existingRepositoryProfileProvider = { item ->
                                previousCheckStatesById[item.id]?.repositoryProfile
                            },
                        )
                    var lastProgressNotifyAtMs = clock.nowMs()
                    val pendingUiResults = mutableListOf<Pair<GitHubTrackedApp, VersionCheckUi>>()
                    val batchResult = GitHubTrackedRefreshBatchRunner.run(
                        context = context,
                        items = snapshot,
                        refreshTimestampMs = clock.nowMs(),
                        maxConcurrency = concurrency,
                        batchTimeoutMs = GITHUB_REFRESH_PAGE_BATCH_TIMEOUT_MS,
                        onItemResult = { item, check, _ ->
                            val previousState = previousCheckStatesById[item.id] ?: VersionCheckUi()
                            val itemState =
                                owner
                                    .mergeDirectApkRemoteFallback(
                                        item = item,
                                        resolvedState = check.toUi(),
                                        previousState = previousState,
                                    ).copy(checkedAtMillis = clock.nowMs())
                            progressMutex.withLock {
                                pendingUiResults += item to itemState
                            }
                        },
                        onProgress = { progress ->
                            var progressNotifySnapshot: GitHubRefreshProgressSnapshot? = null
                            var failedToasts = emptyList<Pair<GitHubTrackedApp, VersionCheckUi>>()
                            var totalTrackedCountForNotification = 0
                            withContext(Dispatchers.Main.immediate) {
                                progressMutex.withLock {
                                    totalTrackedCountForNotification = state.trackedItems.size
                                    completedCount = progress.current
                                    updatableCount = progress.updatableCount
                                    preReleaseUpdateCount = progress.preReleaseUpdateCount
                                    failedCount = progress.failedCount
                                    GitHubRefreshRuntimeStore.progress(
                                        sessionId = runtimeSession.id,
                                        completedCount = completedCount,
                                        updatableCount = updatableCount,
                                        preReleaseUpdateCount = preReleaseUpdateCount,
                                        failedCount = failedCount,
                                    )
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
                            }
                            progressNotifySnapshot?.let { progressSnapshot ->
                                repository.notifyRefreshProgress(
                                    context = context,
                                    current = progressSnapshot.current,
                                    total = progressSnapshot.total,
                                    preReleaseUpdateCount = progressSnapshot.preReleaseUpdateCount,
                                    updatableCount = progressSnapshot.updatableCount,
                                    failedCount = progressSnapshot.failedCount,
                                    sessionId = runtimeSession.id,
                                    scope = runtimeSession.scope,
                                    source = runtimeSession.source,
                                    totalTrackedCount = totalTrackedCountForNotification,
                                )
                            }
                            if (failedToasts.isNotEmpty()) {
                                withContext(Dispatchers.Main.immediate) {
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
                        },
                        evaluator = { _, item ->
                            batchEvaluator.evaluateTrackedApp(
                                context = context,
                                item = item,
                                forceRefresh = forceRefresh,
                            )
                        },
                    )
                    updatableCount = batchResult.updatableCount
                    preReleaseUpdateCount = batchResult.preReleaseUpdateCount
                    failedCount = batchResult.failedCount
                    repository.notifyRefreshProgress(
                        context = context,
                        current = totalCount,
                        total = totalCount,
                        preReleaseUpdateCount = preReleaseUpdateCount,
                        updatableCount = updatableCount,
                        failedCount = failedCount,
                        sessionId = runtimeSession.id,
                        scope = runtimeSession.scope,
                        source = runtimeSession.source,
                        totalTrackedCount = state.trackedItems.size,
                    )
                    GitHubRefreshRuntimeStore.complete(
                        sessionId = runtimeSession.id,
                        completedCount = totalCount,
                        updatableCount = updatableCount,
                        preReleaseUpdateCount = preReleaseUpdateCount,
                        failedCount = failedCount,
                    )
                    state.overviewRefreshState =
                        if (failedCount > 0) {
                            OverviewRefreshState.Failed
                        } else {
                            OverviewRefreshState.Completed
                        }
                    if (refreshPlan.updateGlobalRefreshTimestamp) {
                        state.lastRefreshMs = clock.nowMs()
                    }
                    state.refreshProgress = 1f
                    if (clearAllCheckCache || refreshPlan.updateGlobalRefreshTimestamp) {
                        owner.persistCheckCacheNow()
                    } else {
                        owner.mergeCheckCacheNow(targetIds = targetIds.toSet())
                    }
                    refreshHistoryService.recordCompleted(
                        session = runtimeSession,
                        totalTrackedCount = state.trackedItems.size,
                        result = batchResult,
                        startedAtMillis = refreshStartedAtMs,
                    )
                    onFinished?.invoke()
                    repository.notifyRefreshCompleted(
                        context = context,
                        total = totalCount,
                        preReleaseUpdateCount = preReleaseUpdateCount,
                        updatableCount = updatableCount,
                        failedCount = failedCount,
                        sessionId = runtimeSession.id,
                        scope = runtimeSession.scope,
                        source = runtimeSession.source,
                        totalTrackedCount = state.trackedItems.size,
                    )
                    AppLogger.i(
                        "GitHubRefreshActions",
                        "github page refresh completed target=$totalCount/${state.trackedItems.size} " +
                            "elapsed=${elapsedMsSince(refreshStartNs)}ms " +
                            "concurrency=$concurrency directConcurrency=$directApkConcurrency " +
                            "fdroidConcurrency=$fdroidConcurrency " +
                            "updatable=$updatableCount prerelease=$preReleaseUpdateCount failed=$failedCount",
                    )
                    logTrackedRefreshFailures(batchResult.failures)
                    actionsRunRefreshCoordinator.refreshItems(snapshot)
                    if (owner.consumeDeferredTrackStoreSyncAfterRefresh()) {
                        owner.syncSnapshotFromStore(forceRefreshApps = false)
                    }
                } catch (error: CancellationException) {
                    AppLogger.d("GitHubRefreshActions") {
                        "cancel page refresh session=${runtimeSession.id} scope=${runtimeSession.scope} " +
                            "completed=$completedCount/$totalCount reason=${error.message.orEmpty()}"
                    }
                    restoreInterruptedItemStates(
                        snapshot = snapshot,
                        previousCheckStatesById = previousCheckStatesById,
                    )
                    withContext(NonCancellable) {
                        refreshHistoryService.recordRuntimeState(
                            runtime = GitHubRefreshRuntimeStore.state.value,
                            outcome = GitHubRefreshHistoryOutcome.Cancelled,
                            note = error.message.orEmpty(),
                        )
                        GitHubRefreshRuntimeStore.cancel(
                            sessionId = runtimeSession.id,
                            completedCount = completedCount,
                            updatableCount = updatableCount,
                            preReleaseUpdateCount = preReleaseUpdateCount,
                            failedCount = failedCount,
                        )
                        when (GitHubRefreshCancelSignal.presentation(error.message)) {
                            GitHubRefreshCancelPresentation.Visible ->
                                repository.notifyRefreshCancelled(
                                    context = context,
                                    current = completedCount,
                                    total = totalCount,
                                    preReleaseUpdateCount = preReleaseUpdateCount,
                                    updatableCount = updatableCount,
                                    failedCount = failedCount,
                                    sessionId = runtimeSession.id,
                                    scope = runtimeSession.scope,
                                    source = runtimeSession.source,
                                    totalTrackedCount = state.trackedItems.size,
                                )

                            GitHubRefreshCancelPresentation.SilentTakeover -> Unit

                            GitHubRefreshCancelPresentation.SilentDismiss ->
                                repository.cancelRefreshNotification(
                                    context = context,
                                    sessionId = runtimeSession.id,
                                )
                        }
                    }
                    throw error
                } catch (error: Throwable) {
                    val failedMessage = error.message?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName
                    val terminalFailedCount = failedCount.coerceAtLeast(1)
                    restoreInterruptedItemStates(
                        snapshot = snapshot,
                        previousCheckStatesById = previousCheckStatesById,
                        failedMessage = failedMessage,
                    )
                    refreshHistoryService.recordRuntimeState(
                        runtime = GitHubRefreshRuntimeStore.state.value,
                        outcome = GitHubRefreshHistoryOutcome.Failed,
                        note = failedMessage,
                    )
                    GitHubRefreshRuntimeStore.cancel(
                        sessionId = runtimeSession.id,
                        completedCount = completedCount,
                        updatableCount = updatableCount,
                        preReleaseUpdateCount = preReleaseUpdateCount,
                        failedCount = terminalFailedCount,
                    )
                    repository.notifyRefreshCompleted(
                        context = context,
                        total = totalCount,
                        preReleaseUpdateCount = preReleaseUpdateCount,
                        updatableCount = updatableCount,
                        failedCount = terminalFailedCount,
                        sessionId = runtimeSession.id,
                        scope = runtimeSession.scope,
                        source = runtimeSession.source,
                        totalTrackedCount = state.trackedItems.size,
                    )
                    if (state.refreshSessionId == runtimeSession.id) {
                        state.overviewRefreshState = OverviewRefreshState.Failed
                        state.refreshProgress = 0f
                    }
                    AppLogger.w("GitHubRefreshActions", "github page refresh failed", error)
                } finally {
                    if (state.refreshSessionId == runtimeSession.id) {
                        state.refreshSessionId = 0L
                        state.refreshTargetIds = emptySet()
                        state.refreshAllJob = null
                    }
                    owner.env.viewModel.refreshHistoryUnreadCount()
                }
        }
    }

    private fun restoreInterruptedItemStates(
        snapshot: List<GitHubTrackedApp>,
        previousCheckStatesById: Map<String, VersionCheckUi>,
        failedMessage: String? = null,
    ) {
        snapshot.forEach { item ->
            val currentState = state.checkStates[item.id]
            if (currentState?.loading != true) return@forEach
            val previousState = previousCheckStatesById[item.id] ?: VersionCheckUi()
            state.checkStates[item.id] =
                if (failedMessage == null) {
                    previousState
                } else {
                    previousState.copy(
                        loading = false,
                        failed = true,
                        message = failedMessage,
                        checkedAtMillis = clock.nowMs(),
                    )
                }
        }
    }

    private fun logTrackedRefreshFailures(failures: List<GitHubTrackedRefreshFailure>) {
        failures.forEach { failure ->
            AppLogger.w(
                "GitHubRefreshActions",
                "github page refresh failed ${failure.logSummary()}",
            )
        }
    }
}
