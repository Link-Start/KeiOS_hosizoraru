package os.kei.feature.github.domain

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import java.util.concurrent.atomic.AtomicInteger

private const val DEFAULT_GITHUB_TRACKED_REFRESH_CONCURRENCY = 4

internal data class GitHubTrackedRefreshBatchResult(
    val totalCount: Int,
    val cacheEntries: Map<String, GitHubCheckCacheEntry>,
    val refreshTimestampMs: Long,
    val updatableCount: Int,
    val preReleaseUpdateCount: Int,
    val failedCount: Int,
    val performance: GitHubTrackedRefreshBatchPerformance = GitHubTrackedRefreshBatchPerformance()
) {
    val hasNotifiableOutcome: Boolean
        get() = updatableCount > 0 || preReleaseUpdateCount > 0 || failedCount > 0
}

internal data class GitHubTrackedRefreshBatchPerformance(
    val elapsedMs: Long = 0L,
    val p50ItemMs: Long = 0L,
    val p95ItemMs: Long = 0L,
    val maxItemMs: Long = 0L
)

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
                failedCount = 0,
                performance = GitHubTrackedRefreshBatchPerformance()
            )
        }

        val batchStartNs = System.nanoTime()
        val concurrency = trackedItems.size.coerceAtMost(maxConcurrency.coerceAtLeast(1))
        val nextIndex = AtomicInteger(0)
        val results = arrayOfNulls<GitHubTrackedRefreshItemResult>(trackedItems.size)
        coroutineScope {
            List(concurrency) {
                async(dispatcher) {
                    while (true) {
                        val index = nextIndex.getAndIncrement()
                        if (index >= trackedItems.size) break
                        val item = trackedItems[index]
                        val itemStartNs = System.nanoTime()
                        val check = runCatching { evaluator(item) }
                            .getOrElse { error -> failedCheck(error) }
                        results[index] = GitHubTrackedRefreshItemResult(
                            item = item,
                            check = check,
                            elapsedMs = elapsedMsSince(itemStartNs)
                        )
                        yield()
                    }
                }
            }.awaitAll()
        }
        val checks = results.map { result ->
            checkNotNull(result) { "Tracked refresh result was not produced" }
        }

        var updatableCount = 0
        var preReleaseUpdateCount = 0
        var failedCount = 0
        val cacheEntries = LinkedHashMap<String, GitHubCheckCacheEntry>(trackedItems.size)
        checks.forEach { result ->
            val item = result.item
            val check = result.check
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
            failedCount = failedCount,
            performance = buildPerformance(
                batchStartNs = batchStartNs,
                itemElapsedMs = checks.map { it.elapsedMs }
            )
        )
    }

    private fun buildPerformance(
        batchStartNs: Long,
        itemElapsedMs: List<Long>
    ): GitHubTrackedRefreshBatchPerformance {
        val sorted = itemElapsedMs.sorted()
        return GitHubTrackedRefreshBatchPerformance(
            elapsedMs = elapsedMsSince(batchStartNs),
            p50ItemMs = percentile(sorted, 50),
            p95ItemMs = percentile(sorted, 95),
            maxItemMs = sorted.lastOrNull() ?: 0L
        )
    }

    private fun percentile(sorted: List<Long>, percentile: Int): Long {
        if (sorted.isEmpty()) return 0L
        val index = (((sorted.size * percentile.coerceIn(1, 100)) + 99) / 100 - 1)
            .coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun elapsedMsSince(startNs: Long): Long {
        return ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
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

    private data class GitHubTrackedRefreshItemResult(
        val item: GitHubTrackedApp,
        val check: GitHubTrackedReleaseCheck,
        val elapsedMs: Long
    )
}
