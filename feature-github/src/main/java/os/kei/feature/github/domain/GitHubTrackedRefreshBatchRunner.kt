package os.kei.feature.github.domain

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.io.NetworkCallGauge
import os.kei.core.io.NetworkTimingScope
import os.kei.core.io.NetworkTimingSummary
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isFdroidRepositoryTrack
import os.kei.feature.github.model.isGitBackedRepositoryTrack
import kotlin.coroutines.coroutineContext

private const val DEFAULT_MAX_ITEM_ATTEMPTS = 2
private const val DEFAULT_RETRY_DELAY_MS = 450L

data class GitHubTrackedRefreshBatchResult(
    val totalCount: Int,
    val cacheEntries: Map<String, GitHubCheckCacheEntry>,
    val refreshTimestampMs: Long,
    val updatableCount: Int,
    val preReleaseUpdateCount: Int,
    val failedCount: Int,
    val failures: List<GitHubTrackedRefreshFailure> = emptyList(),
    val performance: GitHubTrackedRefreshBatchPerformance = GitHubTrackedRefreshBatchPerformance(),
    val transientNetworkAbort: Boolean = false,
) {
    val hasNotifiableOutcome: Boolean
        get() = updatableCount > 0 || preReleaseUpdateCount > 0 || failedCount > 0

    val isSharedTransientNetworkFailure: Boolean
        get() =
            totalCount > 0 &&
                failedCount == totalCount &&
                failures.size == totalCount &&
                failures.all { failure ->
                    failure.diagnostics.category == GitHubRefreshFailureClassifier.CATEGORY_NETWORK_ERROR ||
                        failure.diagnostics.category == GitHubRefreshFailureClassifier.CATEGORY_TIMEOUT
                }

    val requiresInfrastructureRetry: Boolean
        get() = transientNetworkAbort || isSharedTransientNetworkFailure
}

data class GitHubTrackedRefreshBatchPerformance(
    val elapsedMs: Long = 0L,
    val p50ItemMs: Long = 0L,
    val p95ItemMs: Long = 0L,
    val maxItemMs: Long = 0L,
    val maxConcurrency: Int = 0,
    /**
     * How many HTTP calls were ever really in the air at once during this batch.
     *
     * [maxConcurrency] is a setting; this is what happened. They were silently different for months
     * — a request used to hold the thread that started it, so a batch that asked for sixteen got ten
     * and nothing counted the difference. A figure from somebody's actual phone is the only place
     * that gap shows up, and an emulator cannot fake it.
     */
    val peakConcurrentCalls: Int = 0,
    /** Wifi, cellular, ethernet or none, as the device reported it when the batch finished. */
    val networkKind: String = "",
    val networkMetered: Boolean = false,
    val directApkConcurrency: Int = 0,
    val fdroidConcurrency: Int = 0,
    val repositoryItemCount: Int = 0,
    val directApkItemCount: Int = 0,
    val fdroidItemCount: Int = 0,
    val otherItemCount: Int = 0,
    val slowItems: List<GitHubTrackedRefreshSlowItem> = emptyList(),
)

data class GitHubTrackedRefreshBatchProgress(
    val current: Int,
    val total: Int,
    val updatableCount: Int,
    val preReleaseUpdateCount: Int,
    val failedCount: Int
)

data class GitHubTrackedRefreshRetryPolicy(
    val maxAttempts: Int = DEFAULT_MAX_ITEM_ATTEMPTS,
    val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
) {
    val safeMaxAttempts: Int
        get() = maxAttempts.coerceAtLeast(1)

    val safeRetryDelayMs: Long
        get() = retryDelayMs.coerceAtLeast(0L)

    companion object {
        val None: GitHubTrackedRefreshRetryPolicy =
            GitHubTrackedRefreshRetryPolicy(maxAttempts = 1, retryDelayMs = 0L)
    }
}

data class GitHubTrackedRefreshSlowItem(
    val trackId: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val sourceMode: String,
    val elapsedMs: Long,
    val status: String,
    val message: String,
    val strategyId: String = "",
    val localVersionElapsedMs: Long = 0L,
    val snapshotElapsedMs: Long = 0L,
    val snapshotFromCache: Boolean = false,
    val profileElapsedMs: Long = 0L,
    val profileFromCache: Boolean = false,
    val preciseApkElapsedMs: Long = 0L,
    val preciseApkRequested: Boolean = false,
    val comparisonElapsedMs: Long = 0L,
    val unclassifiedElapsedMs: Long = 0L,
    val fallbackStrategyId: String = "",
    /** @see GitHubReleaseCheckDiagnostics.network */
    val network: NetworkTimingSummary = NetworkTimingSummary(),
)

object GitHubTrackedRefreshBatchRunner {
    suspend fun run(
        context: Context,
        items: List<GitHubTrackedApp>,
        refreshTimestampMs: Long = System.currentTimeMillis(),
        maxConcurrency: Int = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(items.size),
        dispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
        itemTimeoutMs: (GitHubTrackedApp) -> Long = ::defaultItemTimeoutMs,
        batchTimeoutMs: Long = 0L,
        retryPolicy: GitHubTrackedRefreshRetryPolicy = GitHubTrackedRefreshRetryPolicy(),
        transientNetworkFailureAbortThreshold: Int = 0,
        onProgress: suspend (GitHubTrackedRefreshBatchProgress) -> Unit = {},
        onItemResult: suspend (GitHubTrackedApp, GitHubTrackedReleaseCheck, Long) -> Unit = { _, _, _ -> },
        evaluator: (suspend (Context, GitHubTrackedApp) -> GitHubTrackedReleaseCheck)? = null
    ): GitHubTrackedRefreshBatchResult {
        val batchEvaluator = GitHubTrackedRefreshBatchEvaluator(items)
        return run(
            networkState = GitHubRefreshNetworkKind.of(context),
            trackedItems = items,
            refreshTimestampMs = refreshTimestampMs,
            maxConcurrency = maxConcurrency,
            dispatcher = dispatcher,
            itemTimeoutMs = itemTimeoutMs,
            batchTimeoutMs = batchTimeoutMs,
            retryPolicy = retryPolicy,
            transientNetworkFailureAbortThreshold = transientNetworkFailureAbortThreshold,
            onProgress = onProgress,
            onItemResult = onItemResult,
            evaluator = { item ->
                evaluator?.invoke(context, item)
                    ?: batchEvaluator.evaluateTrackedApp(context, item)
            }
        )
    }

    suspend fun run(
        trackedItems: List<GitHubTrackedApp>,
        networkState: GitHubRefreshNetworkState = GitHubRefreshNetworkState(),
        refreshTimestampMs: Long = System.currentTimeMillis(),
        maxConcurrency: Int = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(trackedItems.size),
        dispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
        itemTimeoutMs: (GitHubTrackedApp) -> Long = ::defaultItemTimeoutMs,
        batchTimeoutMs: Long = 0L,
        retryPolicy: GitHubTrackedRefreshRetryPolicy = GitHubTrackedRefreshRetryPolicy(),
        transientNetworkFailureAbortThreshold: Int = 0,
        onProgress: suspend (GitHubTrackedRefreshBatchProgress) -> Unit = {},
        onItemResult: suspend (GitHubTrackedApp, GitHubTrackedReleaseCheck, Long) -> Unit = { _, _, _ -> },
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
                failures = emptyList(),
                performance = GitHubTrackedRefreshBatchPerformance()
            )
        }

        val batchStartNs = System.nanoTime()
        // Only scoped calls are counted, so a download running alongside cannot inflate the reading.
        NetworkCallGauge.resetPeak()
        val concurrency = trackedItems.size.coerceAtMost(maxConcurrency.coerceAtLeast(1))
        val batchDeadlineNs = batchDeadlineNs(
            batchStartNs = batchStartNs,
            batchTimeoutMs = batchTimeoutMs,
        )
        val batchTimedOut = AtomicBoolean(false)
        val transientNetworkAbort = AtomicBoolean(false)
        val consecutiveTransientNetworkFailures = AtomicInteger(0)
        val workItems = GitHubTrackedRefreshBatchScheduler.buildFairRefreshOrder(trackedItems)
        val directApkPermits = Semaphore(
            permits = GitHubTrackedRefreshBatchScheduler.directApkConcurrency(concurrency)
        )
        val fdroidPermits = Semaphore(
            permits = GitHubTrackedRefreshBatchScheduler.fdroidConcurrency(concurrency)
        )
        val nextIndex = AtomicInteger(0)
        val progressMutex = Mutex()
        var completedCount = 0
        var updatableCount = 0
        var preReleaseUpdateCount = 0
        var failedCount = 0
        val results = arrayOfNulls<GitHubTrackedRefreshItemResult>(trackedItems.size)
        suspend fun publishResult(result: GitHubTrackedRefreshItemResult): GitHubTrackedRefreshBatchProgress {
            onItemResult(result.item, result.check, result.elapsedMs)
            val progress = progressMutex.withLock {
                if (result.check.hasUpdate == true) updatableCount += 1
                if (result.check.hasPreReleaseUpdate) preReleaseUpdateCount += 1
                if (result.check.status == GitHubTrackedReleaseStatus.Failed) failedCount += 1
                completedCount += 1
                GitHubTrackedRefreshBatchProgress(
                    current = completedCount,
                    total = trackedItems.size,
                    updatableCount = updatableCount,
                    preReleaseUpdateCount = preReleaseUpdateCount,
                    failedCount = failedCount
                )
            }
            onProgress(progress)
            return progress
        }
        val workersCompleted =
            runGitHubRefreshWorkersWithBatchTimeout(batchTimeoutMs) {
                coroutineScope {
                    List(concurrency) {
                        async(dispatcher) {
                            while (true) {
                                if (transientNetworkAbort.get()) break
                                if (isBatchDeadlineReached(batchDeadlineNs)) {
                                    batchTimedOut.set(true)
                                    break
                                }
                                val index = nextIndex.getAndIncrement()
                                if (index >= workItems.size) break
                                ensureActive()
                                val workItem = workItems[index]
                                val item = workItem.item
                                val itemStartNs = System.nanoTime()
                                // Every call this item makes lands here, however deep in the
                                // strategy it was issued.
                                val itemNetwork = NetworkTimingScope()
                                val check = withContext(itemNetwork) {
                                    evaluateWithRetry(
                                        item = item,
                                        timeoutMs = itemTimeoutMs(item),
                                        retryPolicy = retryPolicy,
                                    ) {
                                        runCatching {
                                            evaluateWithTimeout(
                                                item = item,
                                                timeoutMs = itemTimeoutMs(item),
                                            ) {
                                                when {
                                                    item.isDirectApkTrack() -> {
                                                        directApkPermits.withPermit { evaluator(item) }
                                                    }

                                                    item.isFdroidRepositoryTrack() -> {
                                                        fdroidPermits.withPermit { evaluator(item) }
                                                    }

                                                    else -> {
                                                        evaluator(item)
                                                    }
                                                }
                                            }
                                        }.getOrElse { error ->
                                            if (error is CancellationException) throw error
                                            failedCheck(error)
                                        }
                                    }
                                }
                                val itemElapsedMs = elapsedMsSince(itemStartNs)
                                val result = GitHubTrackedRefreshItemResult(
                                    item = item,
                                    check = check.copy(
                                        diagnostics = check.diagnostics.copy(
                                            network = itemNetwork.summary(),
                                        ),
                                    ),
                                    elapsedMs = itemElapsedMs
                                )
                                if (check.isTransientNetworkFailure()) {
                                    val failureCount = consecutiveTransientNetworkFailures.incrementAndGet()
                                    if (
                                        transientNetworkFailureAbortThreshold > 0 &&
                                        failureCount >= transientNetworkFailureAbortThreshold
                                    ) {
                                        transientNetworkAbort.set(true)
                                    }
                                } else {
                                    consecutiveTransientNetworkFailures.set(0)
                                }
                                results[workItem.originalIndex] = result
                                publishResult(result)
                                yield()
                            }
                        }
                    }.awaitAll()
                }
            }
        if (!workersCompleted) {
            batchTimedOut.set(true)
        }
        results.forEachIndexed { index, result ->
            if (result == null) {
                val item = trackedItems[index]
                val timedOutResult =
                    GitHubTrackedRefreshItemResult(
                        item = item,
                        check =
                            if (transientNetworkAbort.get()) {
                                networkDeferredCheck(item)
                            } else if (batchTimedOut.get() && batchTimeoutMs > 0L) {
                                batchTimedOutCheck(
                                    item = item,
                                    timeoutMs = batchTimeoutMs,
                                )
                            } else {
                                batchIncompleteCheck(item)
                            },
                        elapsedMs = elapsedMsSince(batchStartNs),
                    )
                results[index] = timedOutResult
                publishResult(timedOutResult)
            }
        }
        val checks = results.mapIndexed { index, result ->
            result ?: GitHubTrackedRefreshItemResult(
                item = trackedItems[index],
                check = batchIncompleteCheck(trackedItems[index]),
                elapsedMs = elapsedMsSince(batchStartNs),
            )
        }
        val finalUpdatableCount = checks.count { result -> result.check.hasUpdate == true }
        val finalPreReleaseUpdateCount = checks.count { result -> result.check.hasPreReleaseUpdate }
        val finalFailedCount = checks.count { result ->
            result.check.status == GitHubTrackedReleaseStatus.Failed
        }

        val cacheEntries = LinkedHashMap<String, GitHubCheckCacheEntry>(trackedItems.size)
        checks.forEach { result ->
            val item = result.item
            val check = result.check
            cacheEntries[item.id] = GitHubReleaseCheckService
                .run { check.toCacheEntry() }
                .copy(checkedAtMillis = refreshTimestampMs)
        }
        val failures =
            checks.mapNotNull { result ->
                val check = result.check
                if (check.status == GitHubTrackedReleaseStatus.Failed) {
                    GitHubTrackedRefreshFailure.from(
                        item = result.item,
                        message = check.message,
                        elapsedMs = result.elapsedMs,
                        diagnostics = check.failureDiagnostics,
                    )
                } else {
                    null
                }
            }

        return GitHubTrackedRefreshBatchResult(
            totalCount = trackedItems.size,
            cacheEntries = cacheEntries,
            refreshTimestampMs = refreshTimestampMs,
            updatableCount = finalUpdatableCount,
            preReleaseUpdateCount = finalPreReleaseUpdateCount,
            failedCount = finalFailedCount,
            failures = failures,
            transientNetworkAbort = transientNetworkAbort.get(),
            performance = buildPerformance(
                batchStartNs = batchStartNs,
                itemResults = checks,
                maxConcurrency = concurrency,
                peakConcurrentCalls = NetworkCallGauge.peakConcurrentCalls(),
                networkState = networkState,
                directApkConcurrency = GitHubTrackedRefreshBatchScheduler.directApkConcurrency(concurrency),
                fdroidConcurrency = GitHubTrackedRefreshBatchScheduler.fdroidConcurrency(concurrency),
            )
        )
    }

    private fun buildPerformance(
        batchStartNs: Long,
        itemResults: List<GitHubTrackedRefreshItemResult>,
        maxConcurrency: Int,
        peakConcurrentCalls: Int,
        networkState: GitHubRefreshNetworkState,
        directApkConcurrency: Int,
        fdroidConcurrency: Int,
    ): GitHubTrackedRefreshBatchPerformance {
        val itemElapsedMs = itemResults.map { it.elapsedMs }
        val sorted = itemElapsedMs.sorted()
        return GitHubTrackedRefreshBatchPerformance(
            elapsedMs = elapsedMsSince(batchStartNs),
            p50ItemMs = percentile(sorted, 50),
            p95ItemMs = percentile(sorted, 95),
            maxItemMs = sorted.lastOrNull() ?: 0L,
            maxConcurrency = maxConcurrency,
            peakConcurrentCalls = peakConcurrentCalls,
            networkKind = networkState.kind,
            networkMetered = networkState.metered,
            directApkConcurrency = directApkConcurrency,
            fdroidConcurrency = fdroidConcurrency,
            repositoryItemCount = itemResults.count { result -> result.item.isGitBackedRepositoryTrack() },
            directApkItemCount = itemResults.count { result -> result.item.isDirectApkTrack() },
            fdroidItemCount = itemResults.count { result -> result.item.isFdroidRepositoryTrack() },
            otherItemCount = itemResults.count { result ->
                !result.item.isGitBackedRepositoryTrack() &&
                    !result.item.isDirectApkTrack() &&
                    !result.item.isFdroidRepositoryTrack()
            },
            slowItems =
                itemResults
                    .sortedByDescending { result -> result.elapsedMs }
                    .take(SLOW_ITEM_HISTORY_LIMIT)
                    .map { result -> result.toSlowItem() },
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

    private fun batchDeadlineNs(
        batchStartNs: Long,
        batchTimeoutMs: Long,
    ): Long {
        if (batchTimeoutMs <= 0L) return Long.MAX_VALUE
        val timeoutNs = batchTimeoutMs.saturatingMsToNs()
        if (batchStartNs > Long.MAX_VALUE - timeoutNs) return Long.MAX_VALUE
        return batchStartNs + timeoutNs
    }

    private fun isBatchDeadlineReached(deadlineNs: Long): Boolean =
        deadlineNs != Long.MAX_VALUE && System.nanoTime() >= deadlineNs

    private fun failedCheck(error: Throwable): GitHubTrackedReleaseCheck {
        val detail = error.message?.takeIf { it.isNotBlank() }
            ?: error.javaClass.simpleName
        return GitHubTrackedReleaseCheck(
            strategyId = "",
            localVersion = "",
            localVersionCode = -1L,
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(detail),
            failureDiagnostics = GitHubRefreshFailureClassifier.from(error),
        )
    }

    private suspend fun evaluateWithRetry(
        item: GitHubTrackedApp,
        timeoutMs: Long,
        retryPolicy: GitHubTrackedRefreshRetryPolicy,
        evaluateOnce: suspend () -> GitHubTrackedReleaseCheck,
    ): GitHubTrackedReleaseCheck {
        val maxAttempts = retryPolicy.safeMaxAttempts
        var attempt = 1
        while (true) {
            coroutineContext.ensureActive()
            val check = evaluateOnce()
            if (attempt >= maxAttempts || !check.isRetryableRefreshFailure()) {
                return check.withAttemptSuffix(
                    attempt = attempt,
                    maxAttempts = maxAttempts,
                    timeoutMs = timeoutMs,
                )
            }
            delay(retryPolicy.safeRetryDelayMs * attempt)
            attempt += 1
        }
    }

    private suspend fun evaluateWithTimeout(
        item: GitHubTrackedApp,
        timeoutMs: Long,
        evaluator: suspend () -> GitHubTrackedReleaseCheck
    ): GitHubTrackedReleaseCheck {
        val boundedTimeoutMs = timeoutMs.coerceAtLeast(0L)
        if (boundedTimeoutMs == 0L) return evaluator()
        return withTimeoutOrNull(boundedTimeoutMs) {
            evaluator()
        } ?: timedOutCheck(item, boundedTimeoutMs)
    }

    private fun timedOutCheck(
        item: GitHubTrackedApp,
        timeoutMs: Long
    ): GitHubTrackedReleaseCheck {
        val seconds = ((timeoutMs + 999L) / 1_000L).coerceAtLeast(1L)
        return GitHubTrackedReleaseCheck(
            strategyId = "",
            localVersion = "",
            localVersionCode = -1L,
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(
                "Timed out after ${seconds}s (${item.owner}/${item.repo})"
            ),
            failureDiagnostics = GitHubRefreshFailureClassifier.timeout(item.sourceMode.storageId),
        )
    }

    private fun batchTimedOutCheck(
        item: GitHubTrackedApp,
        timeoutMs: Long,
    ): GitHubTrackedReleaseCheck {
        val seconds = ((timeoutMs + 999L) / 1_000L).coerceAtLeast(1L)
        return GitHubTrackedReleaseCheck(
            strategyId = "",
            localVersion = "",
            localVersionCode = -1L,
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(
                "Batch timed out after ${seconds}s before ${item.owner}/${item.repo} could refresh"
            ),
            failureDiagnostics = GitHubRefreshFailureClassifier.timeout(item.sourceMode.storageId),
        )
    }

    private fun batchIncompleteCheck(item: GitHubTrackedApp): GitHubTrackedReleaseCheck =
        GitHubTrackedReleaseCheck(
            strategyId = "",
            localVersion = "",
            localVersionCode = -1L,
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(
                "Batch stopped before ${item.owner}/${item.repo} could refresh"
            ),
            failureDiagnostics = GitHubRefreshFailureClassifier.cancelled(item.sourceMode.storageId),
        )

    private fun networkDeferredCheck(item: GitHubTrackedApp): GitHubTrackedReleaseCheck =
        GitHubTrackedReleaseCheck(
            strategyId = "",
            localVersion = "",
            localVersionCode = -1L,
            status = GitHubTrackedReleaseStatus.Failed,
            message = GitHubTrackedReleaseStatus.Failed.failureMessage(
                "Background batch deferred until network recovery (${item.owner}/${item.repo})",
            ),
            failureDiagnostics = GitHubRefreshFailureClassifier.network(item.sourceMode.storageId),
        )

    private fun GitHubTrackedReleaseCheck.isTransientNetworkFailure(): Boolean =
        status == GitHubTrackedReleaseStatus.Failed &&
            (failureDiagnostics.category == GitHubRefreshFailureClassifier.CATEGORY_NETWORK_ERROR ||
                failureDiagnostics.category == GitHubRefreshFailureClassifier.CATEGORY_TIMEOUT)

    private fun GitHubTrackedReleaseCheck.isRetryableRefreshFailure(): Boolean {
        if (status != GitHubTrackedReleaseStatus.Failed) return false
        when (failureDiagnostics.category) {
            GitHubRefreshFailureClassifier.CATEGORY_RESPONSE_TOO_LARGE,
            GitHubRefreshFailureClassifier.CATEGORY_RATE_LIMITED,
            GitHubRefreshFailureClassifier.CATEGORY_PARSE_ERROR,
            GitHubRefreshFailureClassifier.CATEGORY_CANCELLED,
            -> return false

            GitHubRefreshFailureClassifier.CATEGORY_TIMEOUT,
            GitHubRefreshFailureClassifier.CATEGORY_NETWORK_ERROR,
            -> return true
        }
        val lower = message.lowercase()
        if (
            "rate limited" in lower ||
            "http 401" in lower ||
            "http 403" in lower ||
            "http 404" in lower ||
            "invalid or expired" in lower
        ) {
            return false
        }
        return "timed out" in lower ||
            "timeout" in lower ||
            "connection" in lower ||
            "network" in lower ||
            "closed" in lower ||
            "reset" in lower ||
            "unavailable" in lower ||
            "temporarily" in lower ||
            "socket" in lower ||
            "unexpected end" in lower ||
            "http 500" in lower ||
            "http 502" in lower ||
            "http 503" in lower ||
            "http 504" in lower
    }

    private fun GitHubTrackedReleaseCheck.withAttemptSuffix(
        attempt: Int,
        maxAttempts: Int,
        timeoutMs: Long,
    ): GitHubTrackedReleaseCheck {
        if (status != GitHubTrackedReleaseStatus.Failed || attempt <= 1 || maxAttempts <= 1) return this
        val seconds = ((timeoutMs + 999L) / 1_000L).coerceAtLeast(1L)
        return copy(
            message = "$message (attempt $attempt/$maxAttempts, timeout ${seconds}s)",
        )
    }

    private fun defaultItemTimeoutMs(item: GitHubTrackedApp): Long =
        when {
            item.isFdroidRepositoryTrack() -> FDROID_REFRESH_ITEM_TIMEOUT_MS
            item.isDirectApkTrack() -> DIRECT_APK_REFRESH_ITEM_TIMEOUT_MS
            else -> REPOSITORY_REFRESH_ITEM_TIMEOUT_MS
        }

    private data class GitHubTrackedRefreshItemResult(
        val item: GitHubTrackedApp,
        val check: GitHubTrackedReleaseCheck,
        val elapsedMs: Long
    )

    private fun GitHubTrackedRefreshItemResult.toSlowItem(): GitHubTrackedRefreshSlowItem =
        GitHubTrackedRefreshSlowItem(
            trackId = item.id,
            owner = item.owner,
            repo = item.repo,
            packageName = item.packageName,
            appLabel = item.appLabel,
            sourceMode = item.sourceMode.storageId,
            elapsedMs = elapsedMs,
            status = check.status.name,
            message = check.message,
            strategyId = check.strategyId,
            localVersionElapsedMs = check.diagnostics.localVersionElapsedMs,
            snapshotElapsedMs = check.diagnostics.snapshotElapsedMs,
            snapshotFromCache = check.diagnostics.snapshotFromCache,
            profileElapsedMs = check.diagnostics.profileElapsedMs,
            profileFromCache = check.diagnostics.profileFromCache,
            preciseApkElapsedMs = check.diagnostics.preciseApkElapsedMs,
            preciseApkRequested = check.diagnostics.preciseApkRequested,
            comparisonElapsedMs = check.diagnostics.comparisonElapsedMs,
            unclassifiedElapsedMs = computeUnclassifiedElapsedMs(
                elapsedMs = elapsedMs,
                check = check,
            ),
            fallbackStrategyId = check.diagnostics.fallbackStrategyId,
            network = check.diagnostics.network,
        )

    private fun computeUnclassifiedElapsedMs(
        elapsedMs: Long,
        check: GitHubTrackedReleaseCheck,
    ): Long {
        val stageElapsedMs =
            check.diagnostics.localVersionElapsedMs.coerceAtLeast(0L) +
                check.diagnostics.snapshotElapsedMs.coerceAtLeast(0L) +
                check.diagnostics.profileElapsedMs.coerceAtLeast(0L) +
                check.diagnostics.preciseApkElapsedMs.coerceAtLeast(0L) +
                check.diagnostics.comparisonElapsedMs.coerceAtLeast(0L)
        return (elapsedMs.coerceAtLeast(0L) - stageElapsedMs).coerceAtLeast(0L)
    }

    private const val REPOSITORY_REFRESH_ITEM_TIMEOUT_MS = 35_000L
    private const val DIRECT_APK_REFRESH_ITEM_TIMEOUT_MS = 45_000L
    private const val FDROID_REFRESH_ITEM_TIMEOUT_MS = 45_000L
    private const val SLOW_ITEM_HISTORY_LIMIT = 5
}

private fun Long.saturatingMsToNs(): Long {
    val value = coerceAtLeast(0L)
    val multiplier = 1_000_000L
    if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE
    return value * multiplier
}
