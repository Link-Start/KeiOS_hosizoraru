package os.kei.feature.github.domain

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus

private const val DEFAULT_GITHUB_TRACKED_REFRESH_CONCURRENCY = 4

internal data class GitHubTrackedRefreshBatchResult(
    val totalCount: Int,
    val cacheEntries: Map<String, GitHubCheckCacheEntry>,
    val refreshTimestampMs: Long,
    val updatableCount: Int,
    val preReleaseUpdateCount: Int,
    val failedCount: Int
) {
    val hasNotifiableOutcome: Boolean
        get() = updatableCount > 0 || preReleaseUpdateCount > 0 || failedCount > 0
}

internal object GitHubTrackedRefreshBatchRunner {
    suspend fun run(
        context: Context,
        items: List<GitHubTrackedApp>,
        refreshTimestampMs: Long = System.currentTimeMillis(),
        maxConcurrency: Int = DEFAULT_GITHUB_TRACKED_REFRESH_CONCURRENCY,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        evaluator: suspend (Context, GitHubTrackedApp) -> GitHubTrackedReleaseCheck =
            GitHubReleaseCheckService::evaluateTrackedApp
    ): GitHubTrackedRefreshBatchResult {
        return run(
            trackedItems = items,
            refreshTimestampMs = refreshTimestampMs,
            maxConcurrency = maxConcurrency,
            dispatcher = dispatcher,
            evaluator = { item -> evaluator(context, item) }
        )
    }

    suspend fun run(
        trackedItems: List<GitHubTrackedApp>,
        refreshTimestampMs: Long = System.currentTimeMillis(),
        maxConcurrency: Int = DEFAULT_GITHUB_TRACKED_REFRESH_CONCURRENCY,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        evaluator: suspend (GitHubTrackedApp) -> GitHubTrackedReleaseCheck
    ): GitHubTrackedRefreshBatchResult {
        if (trackedItems.isEmpty()) {
            return GitHubTrackedRefreshBatchResult(
                totalCount = 0,
                cacheEntries = emptyMap(),
                refreshTimestampMs = refreshTimestampMs,
                updatableCount = 0,
                preReleaseUpdateCount = 0,
                failedCount = 0
            )
        }

        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val checks = coroutineScope {
            trackedItems.map { item ->
                async(dispatcher) {
                    item to semaphore.withPermit {
                        runCatching { evaluator(item) }
                            .getOrElse { error -> failedCheck(error) }
                    }
                }
            }.awaitAll()
        }

        var updatableCount = 0
        var preReleaseUpdateCount = 0
        var failedCount = 0
        val cacheEntries = LinkedHashMap<String, GitHubCheckCacheEntry>(trackedItems.size)
        checks.forEach { (item, check) ->
            if (check.hasUpdate == true) updatableCount += 1
            if (check.hasPreReleaseUpdate) preReleaseUpdateCount += 1
            if (check.status == GitHubTrackedReleaseStatus.Failed) failedCount += 1
            cacheEntries[item.id] = GitHubReleaseCheckService.run { check.toCacheEntry() }
        }

        return GitHubTrackedRefreshBatchResult(
            totalCount = trackedItems.size,
            cacheEntries = cacheEntries,
            refreshTimestampMs = refreshTimestampMs,
            updatableCount = updatableCount,
            preReleaseUpdateCount = preReleaseUpdateCount,
            failedCount = failedCount
        )
    }

    private fun failedCheck(error: Throwable): GitHubTrackedReleaseCheck {
        val detail = error.message?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName
        return GitHubTrackedReleaseCheck(
            strategyId = "",
            localVersion = "",
            localVersionCode = -1L,
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(detail)
        )
    }
}
