package os.kei.feature.github.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp

interface GitHubActionsRecommendedRunRefreshSource {
    fun loadRecommendedRunSnapshot(trackId: String): GitHubActionsRecommendedRunSnapshot?

    fun loadRecommendedRunSnapshots(): Map<String, GitHubActionsRecommendedRunSnapshot>

    suspend fun fetchRecommendedRunSnapshot(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        previousWorkflowId: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Result<GitHubActionsRecommendedRunSnapshot>

    fun saveRecommendedRunSnapshot(snapshot: GitHubActionsRecommendedRunSnapshot)

    fun removeRecommendedRunSnapshot(trackId: String)

    fun retainRecommendedRunSnapshots(trackIds: Set<String>)
}

data class GitHubActionsRecommendedRunRefreshOutcome(
    val item: GitHubTrackedApp,
    val previous: GitHubActionsRecommendedRunSnapshot?,
    val current: GitHubActionsRecommendedRunSnapshot?,
    val errorMessage: String = "",
) {
    val succeeded: Boolean
        get() = current != null

    val newerThanPrevious: Boolean
        get() = previous != null && current?.isNewerThan(previous) == true
}

data class GitHubActionsRecommendedRunRefreshResult(
    val outcomes: List<GitHubActionsRecommendedRunRefreshOutcome>,
) {
    val checkedCount: Int
        get() = outcomes.size

    val succeededCount: Int
        get() = outcomes.count { it.succeeded }

    val failedCount: Int
        get() = outcomes.count { !it.succeeded }

    val newerSnapshots: List<GitHubActionsRecommendedRunSnapshot>
        get() = outcomes.mapNotNull { outcome ->
            outcome.current?.takeIf { outcome.newerThanPrevious }
        }
}

class GitHubActionsRecommendedRunRefreshService(
    private val source: GitHubActionsRecommendedRunRefreshSource = GitHubActionsService(),
    private val networkDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val localDispatcher: CoroutineDispatcher = AppDispatchers.githubLocal,
) {
    suspend fun refreshItems(
        items: List<GitHubTrackedApp>,
        lookupConfig: GitHubLookupConfig,
        maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
        retainTrackIds: Set<String>? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): GitHubActionsRecommendedRunRefreshResult {
        retainTrackIds?.let { trackIds ->
            withContext(localDispatcher) {
                source.retainRecommendedRunSnapshots(trackIds)
            }
        }
        val targets = items.filter { it.checkActionsUpdates }
        if (targets.isEmpty()) {
            return GitHubActionsRecommendedRunRefreshResult(emptyList())
        }
        val outcomes =
            GitHubExecution.mapOrderedBounded(
                items = targets,
                maxConcurrency = maxConcurrency,
                dispatcher = networkDispatcher,
            ) { item ->
                refreshItem(
                    item = item,
                    lookupConfig = lookupConfig,
                    nowMs = nowMs,
                )
            }
        return GitHubActionsRecommendedRunRefreshResult(outcomes)
    }

    private suspend fun refreshItem(
        item: GitHubTrackedApp,
        lookupConfig: GitHubLookupConfig,
        nowMs: Long,
    ): GitHubActionsRecommendedRunRefreshOutcome {
        val previous =
            withContext(localDispatcher) {
                source.loadRecommendedRunSnapshot(item.id)
            }
        return source
            .fetchRecommendedRunSnapshot(
                item = item,
                lookupConfig = lookupConfig,
                previousWorkflowId = previous?.workflowId,
                nowMs = nowMs,
            ).fold(
                onSuccess = { current ->
                    withContext(localDispatcher) {
                        source.saveRecommendedRunSnapshot(current)
                    }
                    GitHubActionsRecommendedRunRefreshOutcome(
                        item = item,
                        previous = previous,
                        current = current,
                    )
                },
                onFailure = { error ->
                    GitHubActionsRecommendedRunRefreshOutcome(
                        item = item,
                        previous = previous,
                        current = null,
                        errorMessage = error.message.orEmpty().ifBlank { error.javaClass.simpleName },
                    )
                },
            )
    }

    companion object {
        const val DEFAULT_MAX_CONCURRENCY = 2
    }
}
