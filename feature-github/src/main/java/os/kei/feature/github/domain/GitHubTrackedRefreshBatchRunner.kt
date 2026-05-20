package os.kei.feature.github.domain

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.isDirectApkTrack

data class GitHubTrackedRefreshBatchResult(
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

data class GitHubTrackedRefreshBatchPerformance(
    val elapsedMs: Long = 0L,
    val p50ItemMs: Long = 0L,
    val p95ItemMs: Long = 0L,
    val maxItemMs: Long = 0L
)

data class GitHubTrackedRefreshBatchProgress(
    val current: Int,
    val total: Int,
    val updatableCount: Int,
    val preReleaseUpdateCount: Int,
    val failedCount: Int
)

object GitHubTrackedRefreshBatchRunner {
    suspend fun run(
        context: Context,
        items: List<GitHubTrackedApp>,
        refreshTimestampMs: Long = System.currentTimeMillis(),
        maxConcurrency: Int = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(items.size),
        dispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
        onProgress: suspend (GitHubTrackedRefreshBatchProgress) -> Unit = {},
        evaluator: suspend (Context, GitHubTrackedApp) -> GitHubTrackedReleaseCheck =
            GitHubReleaseCheckService::evaluateTrackedApp
    ): GitHubTrackedRefreshBatchResult {
        return run(
            trackedItems = items,
            refreshTimestampMs = refreshTimestampMs,
            maxConcurrency = maxConcurrency,
            dispatcher = dispatcher,
            onProgress = onProgress,
            evaluator = { item -> evaluator(context, item) }
        )
    }

    suspend fun run(
        trackedItems: List<GitHubTrackedApp>,
        refreshTimestampMs: Long = System.currentTimeMillis(),
        maxConcurrency: Int = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(trackedItems.size),
        dispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
        onProgress: suspend (GitHubTrackedRefreshBatchProgress) -> Unit = {},
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
        val workItems = GitHubTrackedRefreshBatchScheduler.buildFairRefreshOrder(trackedItems)
        val directApkPermits = Semaphore(
            permits = GitHubTrackedRefreshBatchScheduler.directApkConcurrency(concurrency)
        )
        val nextIndex = AtomicInteger(0)
        val progressMutex = Mutex()
        var completedCount = 0
        var updatableCount = 0
        var preReleaseUpdateCount = 0
        var failedCount = 0
        val results = arrayOfNulls<GitHubTrackedRefreshItemResult>(trackedItems.size)
        coroutineScope {
            List(concurrency) {
                async(dispatcher) {
                    while (true) {
                        val index = nextIndex.getAndIncrement()
                        if (index >= workItems.size) break
                        val workItem = workItems[index]
                        val item = workItem.item
                        val itemStartNs = System.nanoTime()
                        val check = runCatching {
                            if (item.isDirectApkTrack()) {
                                directApkPermits.withPermit { evaluator(item) }
                            } else {
                                evaluator(item)
                            }
                        }.getOrElse { error ->
                            if (error is CancellationException) throw error
                            failedCheck(error)
                        }
                        results[workItem.originalIndex] = GitHubTrackedRefreshItemResult(
                            item = item,
                            check = check,
                            elapsedMs = elapsedMsSince(itemStartNs)
                        )
                        progressMutex.withLock {
                            if (check.hasUpdate == true) updatableCount += 1
                            if (check.hasPreReleaseUpdate) preReleaseUpdateCount += 1
                            if (check.status == GitHubTrackedReleaseStatus.Failed) failedCount += 1
                            completedCount += 1
                            onProgress(
                                GitHubTrackedRefreshBatchProgress(
                                    current = completedCount,
                                    total = trackedItems.size,
                                    updatableCount = updatableCount,
                                    preReleaseUpdateCount = preReleaseUpdateCount,
                                    failedCount = failedCount
                                )
                            )
                        }
                        yield()
                    }
                }
            }.awaitAll()
        }
        val checks = results.map { result ->
            checkNotNull(result) { "Tracked refresh result was not produced" }
        }

        val cacheEntries = LinkedHashMap<String, GitHubCheckCacheEntry>(trackedItems.size)
        checks.forEach { result ->
            val item = result.item
            val check = result.check
            cacheEntries[item.id] = GitHubReleaseCheckService
                .run { check.toCacheEntry() }
                .copy(checkedAtMillis = refreshTimestampMs)
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
