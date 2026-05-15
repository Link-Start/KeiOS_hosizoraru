package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
import os.kei.feature.github.domain.GitHubActionsUpdateCheckService
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.notification.GitHubActionsUpdateNotificationHelper
import java.util.concurrent.atomic.AtomicInteger

private const val GITHUB_ACTIONS_RECOMMENDED_RUN_REFRESH_PARALLELISM = 2

internal class GitHubActionsRecommendedRunRefreshCoordinator(
    private val env: GitHubPageActionEnvironment
) {
    private val context get() = env.context
    private val scope get() = env.scope
    private val state get() = env.state

    fun cancel() {
        state.actionsRecommendedRunRefreshJob?.cancel()
        state.actionsRecommendedRunRefreshJob = null
    }

    fun refreshItems(items: List<GitHubTrackedApp>) {
        val targets = items.filter { it.checkActionsUpdates }
        if (targets.isEmpty()) return
        cancel()
        val lookupConfig = state.lookupConfig
        val job = scope.launch {
            val service = GitHubActionsUpdateCheckService()
            val nextIndex = AtomicInteger(0)
            val concurrency = targets.size.coerceAtMost(
                GITHUB_ACTIONS_RECOMMENDED_RUN_REFRESH_PARALLELISM
            )
            try {
                supervisorScope {
                    List(concurrency) {
                        launch {
                            while (true) {
                                val index = nextIndex.getAndIncrement()
                                if (index >= targets.size) break
                                refreshItem(
                                    item = targets[index],
                                    lookupConfig = lookupConfig,
                                    service = service
                                )
                            }
                        }
                    }.joinAll()
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
        scope.launch {
            refreshItem(item)
        }
    }

    suspend fun refreshItem(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig = state.lookupConfig,
        service: GitHubActionsUpdateCheckService = GitHubActionsUpdateCheckService()
    ) {
        if (!item.checkActionsUpdates) {
            withContext(Dispatchers.IO) {
                GitHubActionsRecommendedRunStore.remove(item.id)
            }
            state.actionsRecommendedRunSnapshots.remove(item.id)
            return
        }
        val previous = withContext(Dispatchers.IO) {
            GitHubActionsRecommendedRunStore.load(item.id)
        }
        val current = service.fetchRecommendedRunSnapshot(
            item = item,
            lookupConfig = lookupConfig,
            previousWorkflowId = previous?.workflowId
        ).getOrNull() ?: return
        withContext(Dispatchers.IO) {
            GitHubActionsRecommendedRunStore.save(current)
        }
        state.actionsRecommendedRunSnapshots[item.id] = current
        if (previous != null && current.isNewerThan(previous)) {
            GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                context = context,
                snapshot = current
            )
        }
    }
}
