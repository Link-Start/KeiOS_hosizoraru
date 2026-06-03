package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import os.kei.R
import os.kei.feature.github.domain.GitHubRefreshBeginPolicy
import os.kei.feature.github.domain.GitHubRefreshRuntimeStore
import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.domain.GitHubRefreshSource
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchScheduler
import os.kei.feature.github.domain.GitHubTrackedRefreshPlanner
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.ui.page.main.github.OverviewRefreshState
import os.kei.ui.page.main.github.VersionCheckUi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val GITHUB_MISSING_CHECK_STATE_REFRESH_PARALLELISM = 4

internal data class GitHubItemRefreshRequest(
    val item: GitHubTrackedApp,
    val forceRefresh: Boolean = false,
    val profilePurposeOverride: GitHubRepositoryProfilePurpose? = null
)

internal class GitHubBackgroundRefreshCoordinator(
    private val env: GitHubPageActionEnvironment,
    private val actionsRunRefreshCoordinator: GitHubActionsRecommendedRunRefreshCoordinator,
    private val refreshItem: suspend (GitHubItemRefreshRequest) -> Unit,
    private val persistCheckCache: suspend (Set<String>) -> Unit
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val repository get() = env.repository
    private val backgroundJobs = ConcurrentHashMap.newKeySet<Job>()

    fun cancel() {
        backgroundJobs.forEach { it.cancel() }
        backgroundJobs.clear()
    }

    fun hasActiveJobs(): Boolean = backgroundJobs.any { it.isActive }

    suspend fun refreshRequestedTracksIfNeeded(): Boolean {
        val activeItems = state.trackedItems.toList()
        val validTrackIds = activeItems.mapTo(LinkedHashSet()) { it.id }
        if (validTrackIds.isEmpty()) return false
        val requestedIds = repository.consumeTrackRefreshRequests(validTrackIds)
        if (requestedIds.isEmpty()) return false
        val requestedItems = selectActiveTrackedRefreshTargets(
            requestedTrackIds = requestedIds,
            activeItems = activeItems,
        )
        requestedItems.forEach(::markItemChecking)
        refreshItemsInBackground(
            items = requestedItems,
            forceRefresh = true,
            refreshScope = GitHubRefreshScope.RequestedTracked,
            maxConcurrency = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(
                requestedItems.size
            )
        )
        return true
    }

    fun refreshPartialMissingCheckStatesIfNeeded(): Boolean {
        val missingItems = GitHubTrackedRefreshPlanner.selectPartialMissingCheckStateItems(
            trackedItems = state.trackedItems.toList(),
            cachedTrackIds = state.checkStates.keys
        )
        if (missingItems.isEmpty()) return false
        missingItems.forEach(::markItemChecking)
        refreshItemsInBackground(
            items = missingItems,
            refreshScope = GitHubRefreshScope.MissingCache,
            profilePurposeOverride = GitHubRepositoryProfilePurpose.VersionCheckFast,
            maxConcurrency = missingItems.size.coerceAtMost(
                GITHUB_MISSING_CHECK_STATE_REFRESH_PARALLELISM
            )
        )
        return true
    }

    private fun refreshItemsInBackground(
        items: List<GitHubTrackedApp>,
        forceRefresh: Boolean = false,
        refreshScope: GitHubRefreshScope,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        maxConcurrency: Int = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(items.size)
    ) {
        if (items.isEmpty()) return
        var runtimeSessionId = 0L
        val job = scope.launch {
            val runtimeSession =
                GitHubRefreshRuntimeStore.begin(
                    scope = refreshScope,
                    source = GitHubRefreshSource.Page,
                    totalTrackedCount = state.trackedItems.size,
                    targetCount = items.size,
                    policy = GitHubRefreshBeginPolicy.SkipWhenRunning,
                )
            runtimeSessionId = runtimeSession?.id ?: 0L
            if (runtimeSession != null) {
                state.refreshSessionId = runtimeSession.id
                state.refreshTargetIds = items.mapTo(HashSet()) { it.id }
                state.refreshProgress = 0f
                state.overviewRefreshState = OverviewRefreshState.Refreshing
            }
            val concurrency = items.size.coerceAtMost(maxConcurrency.coerceAtLeast(1))
            val directApkSemaphore = Semaphore(
                GitHubTrackedRefreshBatchScheduler.directApkConcurrency(concurrency)
            )
            val workItems = GitHubTrackedRefreshBatchScheduler.buildFairRefreshOrder(items)
            val nextWorkIndex = AtomicInteger(0)
            val progressMutex = Mutex()
            var completedCount = 0
            var updatableCount = 0
            var preReleaseUpdateCount = 0
            var failedCount = 0
            supervisorScope {
                List(concurrency) {
                    launch {
                        while (true) {
                            val workIndex = nextWorkIndex.getAndIncrement()
                            if (workIndex >= workItems.size) break
                            val item = workItems[workIndex].item
                            val request = GitHubItemRefreshRequest(
                                item = item,
                                forceRefresh = forceRefresh,
                                profilePurposeOverride = profilePurposeOverride
                            )
                            if (item.isDirectApkTrack()) {
                                directApkSemaphore.withPermit {
                                    refreshItem(request)
                                }
                            } else {
                                refreshItem(request)
                            }
                            if (runtimeSession != null) {
                                progressMutex.withLock {
                                    val itemState = state.checkStates[item.id] ?: VersionCheckUi()
                                    if (itemState.hasUpdate == true) updatableCount += 1
                                    if (itemState.hasPreReleaseUpdate) preReleaseUpdateCount += 1
                                    if (itemState.failed) failedCount += 1
                                    completedCount += 1
                                    state.refreshProgress = completedCount.toFloat() / items.size.toFloat()
                                    GitHubRefreshRuntimeStore.progress(
                                        sessionId = runtimeSession.id,
                                        completedCount = completedCount,
                                        updatableCount = updatableCount,
                                        preReleaseUpdateCount = preReleaseUpdateCount,
                                        failedCount = failedCount,
                                    )
                                }
                            }
                        }
                    }
                }.joinAll()
            }
            persistCheckCache(items.mapTo(HashSet()) { it.id })
            actionsRunRefreshCoordinator.refreshItems(items)
            if (runtimeSession != null) {
                GitHubRefreshRuntimeStore.complete(
                    sessionId = runtimeSession.id,
                    completedCount = completedCount,
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
                state.refreshProgress = 1f
                if (state.refreshSessionId == runtimeSession.id) {
                    state.refreshSessionId = 0L
                    state.refreshTargetIds = emptySet()
                }
            }
        }
        backgroundJobs.add(job)
        job.invokeOnCompletion { cause ->
            if (cause != null && runtimeSessionId > 0L) {
                val current = GitHubRefreshRuntimeStore.state.value
                if (current.sessionId == runtimeSessionId && current.running) {
                    GitHubRefreshRuntimeStore.cancel(
                        sessionId = runtimeSessionId,
                        completedCount = current.completedCount,
                        updatableCount = current.updatableCount,
                        preReleaseUpdateCount = current.preReleaseUpdateCount,
                        failedCount = current.failedCount,
                    )
                }
                if (state.refreshSessionId == runtimeSessionId) {
                    state.refreshSessionId = 0L
                    state.refreshTargetIds = emptySet()
                    state.refreshProgress = 0f
                }
            }
            backgroundJobs.remove(job)
        }
    }

    private fun markItemChecking(item: GitHubTrackedApp) {
        state.checkStates[item.id] = (state.checkStates[item.id] ?: VersionCheckUi()).copy(
            loading = true,
            message = context.getString(R.string.github_msg_checking)
        )
    }
}
