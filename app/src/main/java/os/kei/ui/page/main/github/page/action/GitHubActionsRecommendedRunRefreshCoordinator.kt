package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.core.log.AppLogger
import os.kei.feature.github.domain.GitHubActionsRecommendedRunRefreshService
import os.kei.feature.github.domain.GitHubActionsService
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.notification.GitHubActionsUpdateNotificationHelper
import java.util.concurrent.ConcurrentHashMap

private const val GITHUB_ACTIONS_RECOMMENDED_RUN_REFRESH_PARALLELISM = 2
private const val GITHUB_ACTIONS_RECOMMENDED_RUN_REFRESH_TAG = "GitHubActionsRunRefresh"

internal class GitHubActionsRecommendedRunRefreshCoordinator(
    private val env: GitHubPageActionEnvironment
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state
    private val actionsRepository get() = env.actionsRepository
    private val itemJobs = ConcurrentHashMap<String, Job>()
    private val refreshService =
        GitHubActionsRecommendedRunRefreshService(source = actionsRepository)

    fun cancel() {
        state.actionsRecommendedRunRefreshJob?.cancel()
        state.actionsRecommendedRunRefreshJob = null
        itemJobs.values.forEach { it.cancel() }
        itemJobs.clear()
    }

    fun refreshItems(items: List<GitHubTrackedApp>) {
        cancel()
        val activeItemsById = state.trackedItems.associateBy { it.id }
        val targets = items.mapNotNull { item ->
            activeItemsById[item.id]?.takeIf { it.checkActionsUpdates }
        }
        if (targets.isEmpty()) return
        val lookupConfig = state.lookupConfig
        val job = scope.launch {
            try {
                val result =
                    refreshService.refreshItems(
                        items = targets,
                        lookupConfig = lookupConfig,
                        maxConcurrency = GITHUB_ACTIONS_RECOMMENDED_RUN_REFRESH_PARALLELISM,
                    )
                result.outcomes.forEach { outcome ->
                    applyRefreshOutcome(
                        item = outcome.item,
                        current = outcome.current,
                        errorMessage = outcome.errorMessage,
                    )
                    if (outcome.newerThanPrevious) {
                        outcome.current?.let { current ->
                            GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                                context = context,
                                snapshot = current,
                            )
                        }
                    }
                }
            } finally {
                if (state.actionsRecommendedRunRefreshJob === coroutineContext[Job]) {
                    state.actionsRecommendedRunRefreshJob = null
                }
            }
        }
        state.actionsRecommendedRunRefreshJob = job
    }

    fun refreshItemInBackground(item: GitHubTrackedApp) {
        val trackId = item.id
        val activeItem = state.trackedItems.firstOrNull { it.id == trackId } ?: return
        itemJobs.remove(trackId)?.cancel()
        val job = scope.launch {
            refreshItem(activeItem)
        }
        itemJobs[trackId] = job
        job.invokeOnCompletion {
            if (itemJobs[trackId] === job) {
                itemJobs.remove(trackId)
            }
        }
    }

    suspend fun refreshItem(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig = state.lookupConfig,
        service: GitHubActionsService = actionsRepository,
    ) {
        if (!item.checkActionsUpdates) {
            service.removeRecommendedRunSnapshot(item.id)
            state.actionsRecommendedRunSnapshots.remove(item.id)
            return
        }
        val result =
            GitHubActionsRecommendedRunRefreshService(source = service)
                .refreshItems(
                    items = listOf(item),
                    lookupConfig = lookupConfig,
                    maxConcurrency = 1,
                )
        result.outcomes.firstOrNull()?.let { outcome ->
            applyRefreshOutcome(
                item = outcome.item,
                current = outcome.current,
                errorMessage = outcome.errorMessage,
            )
            if (outcome.newerThanPrevious) {
                outcome.current?.let { current ->
                    GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                        context = context,
                        snapshot = current,
                    )
                }
            }
        }
    }

    private fun applyRefreshOutcome(
        item: GitHubTrackedApp,
        current: GitHubActionsRecommendedRunSnapshot?,
        errorMessage: String,
    ) {
        val activeItem = state.trackedItems.firstOrNull { it.id == item.id } ?: return
        if (!activeItem.checkActionsUpdates) return
        if (current != null) {
            state.actionsRecommendedRunSnapshots[item.id] = current
        } else if (errorMessage.isNotBlank()) {
            AppLogger.w(
                GITHUB_ACTIONS_RECOMMENDED_RUN_REFRESH_TAG,
                "actions recommended run refresh failed item=${item.id}: $errorMessage",
            )
        }
    }
}
