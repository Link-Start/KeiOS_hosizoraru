package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import os.kei.R
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchScheduler
import os.kei.feature.github.domain.GitHubTrackedRefreshPlanner
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
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
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        maxConcurrency: Int = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(items.size)
    ) {
        if (items.isEmpty()) return
        val job = scope.launch {
            val concurrency = items.size.coerceAtMost(maxConcurrency.coerceAtLeast(1))
            val directApkSemaphore = Semaphore(
                GitHubTrackedRefreshBatchScheduler.directApkConcurrency(concurrency)
            )
            val workItems = GitHubTrackedRefreshBatchScheduler.buildFairRefreshOrder(items)
            val nextWorkIndex = AtomicInteger(0)
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
                        }
                    }
                }.joinAll()
            }
            persistCheckCache(items.mapTo(HashSet()) { it.id })
            actionsRunRefreshCoordinator.refreshItems(items)
        }
        backgroundJobs.add(job)
        job.invokeOnCompletion {
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
