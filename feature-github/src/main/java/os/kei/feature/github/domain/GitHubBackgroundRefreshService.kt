package os.kei.feature.github.domain

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.log.AppLogger
import os.kei.feature.github.data.local.GitHubInstalledAppRepository
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubCheckCacheEntry
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubRefreshSchedulerDiagnostics
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseStatus

private const val GITHUB_BACKGROUND_REFRESH_TAG = "GitHubBackgroundRefresh"
private const val GITHUB_BACKGROUND_ITEM_TIMEOUT_MS = 15_000L
private const val GITHUB_BACKGROUND_BATCH_TIMEOUT_MS = 3L * 60L * 1000L
private const val GITHUB_SHORTCUT_BATCH_TIMEOUT_MS = 4L * 60L * 1000L

internal fun GitHubTrackedRefreshBatchResult.backgroundPersistableCacheEntries(): Map<String, GitHubCheckCacheEntry> {
    val failedTrackIds = failures.mapTo(HashSet()) { it.trackId }
    return cacheEntries.filterKeys { trackId -> trackId !in failedTrackIds }
}

data class GitHubBackgroundTickResult(
    val refreshResult: GitHubTrackedRefreshBatchResult? = null,
    val actionsNotificationCount: Int = 0,
    val retryRecommended: Boolean = false,
)

sealed interface GitHubShortcutRefreshExecution {
    data object NoTrackedItems : GitHubShortcutRefreshExecution

    data class Completed(
        val result: GitHubTrackedRefreshBatchResult,
        val actionsNotificationCount: Int,
    ) : GitHubShortcutRefreshExecution
}

class GitHubBackgroundRefreshService(
    private val actionsService: GitHubActionsService = GitHubActionsService(),
    private val refreshHistoryService: GitHubRefreshHistoryService = GitHubRefreshHistoryService(),
) {
    private val mutex = Mutex()

    suspend fun runDueRefresh(
        context: Context,
        onRefreshStart: (GitHubRefreshRuntimeSession, Int, Int) -> Unit = { _, _, _ -> },
        onRefreshProgress: suspend (GitHubRefreshRuntimeSession, GitHubTrackedRefreshBatchProgress) -> Unit = { _, _ -> },
        onActionsUpdateAvailable: suspend (GitHubActionsRecommendedRunSnapshot) -> Boolean,
        schedulerDiagnostics: GitHubRefreshSchedulerDiagnostics = GitHubRefreshSchedulerDiagnostics(),
    ): GitHubBackgroundTickResult =
        mutex.withLock {
            val snapshot = withContext(AppDispatchers.githubLocal) { GitHubTrackStore.loadSnapshot() }
            val tracked = snapshot.items
            if (tracked.isEmpty()) return@withLock GitHubBackgroundTickResult()

            val nowMs = System.currentTimeMillis()
            val trackedUpdateTargetItems = selectGitHubBackgroundReleaseTargets(snapshot, nowMs)
            val actionsTargetItems =
                selectActionsUpdateTargets(
                    snapshot = snapshot,
                    nowMs = nowMs,
                )
            if (trackedUpdateTargetItems.isEmpty() && actionsTargetItems.isEmpty()) {
                return@withLock GitHubBackgroundTickResult()
            }

            val refreshResult =
                if (trackedUpdateTargetItems.isNotEmpty()) {
                    val batchEvaluator =
                        GitHubTrackedRefreshBatchEvaluator(
                            trackedItems = trackedUpdateTargetItems,
                            existingRepositoryProfileProvider = { item ->
                                snapshot.checkCache[item.id]?.repositoryProfile ?: snapshot.profileCache[item.id]
                            },
                        )
                    val runtimeSession =
                        GitHubRefreshRuntimeStore.begin(
                            scope = GitHubRefreshScope.DueTracked,
                            source = GitHubRefreshSource.BackgroundTick,
                            totalTrackedCount = tracked.size,
                            targetCount = trackedUpdateTargetItems.size,
                            targetTrackIds = trackedUpdateTargetItems.map { it.id },
                            policy = GitHubRefreshBeginPolicy.SkipWhenRunning,
                            nowMs = nowMs,
                        )
                    if (runtimeSession == null) {
                        AppLogger.i(
                            GITHUB_BACKGROUND_REFRESH_TAG,
                            "skip due refresh because another github refresh session is running",
                        )
                        null
                    } else {
                        onRefreshStart(runtimeSession, trackedUpdateTargetItems.size, tracked.size)
                        val checkpointWriter =
                            GitHubBackgroundRefreshCheckpointWriter(
                                persist = { entries ->
                                    persistBackgroundCheckpoint(
                                        entries = entries,
                                        fallbackRefreshTimestamp = snapshot.lastRefreshMs,
                                    )
                                },
                            )
                        try {
                            runCatching {
                                GitHubTrackedRefreshBatchRunner.run(
                                    trackedItems = trackedUpdateTargetItems,
                                    // The path that runs while the phone is asleep on cellular is
                                    // exactly the one whose connection kind is worth recording.
                                    networkState = GitHubRefreshNetworkKind.of(context),
                                    refreshTimestampMs = nowMs,
                                    maxConcurrency = GitHubTrackedRefreshBatchScheduler
                                        .backgroundRefreshConcurrency(trackedUpdateTargetItems.size),
                                    itemTimeoutMs = { GITHUB_BACKGROUND_ITEM_TIMEOUT_MS },
                                    batchTimeoutMs = GITHUB_BACKGROUND_BATCH_TIMEOUT_MS,
                                    retryPolicy = GitHubTrackedRefreshRetryPolicy(
                                        maxAttempts = 2,
                                        retryDelayMs = 350L,
                                    ),
                                    transientNetworkFailureAbortThreshold = 3,
                                    onProgress = { progress ->
                                        GitHubRefreshRuntimeStore.progress(
                                            sessionId = runtimeSession.id,
                                            completedCount = progress.current,
                                            updatableCount = progress.updatableCount,
                                            preReleaseUpdateCount = progress.preReleaseUpdateCount,
                                            failedCount = progress.failedCount,
                                        )
                                        onRefreshProgress(runtimeSession, progress)
                                    },
                                    onItemResult = { item, check, _ ->
                                        if (check.status != GitHubTrackedReleaseStatus.Failed) {
                                            runCatching {
                                                checkpointWriter.append(
                                                    trackId = item.id,
                                                    entry =
                                                        GitHubReleaseCheckService
                                                            .run { check.toCacheEntry() }
                                                            .copy(checkedAtMillis = System.currentTimeMillis()),
                                                )
                                            }.onFailure { error ->
                                                AppLogger.w(
                                                    GITHUB_BACKGROUND_REFRESH_TAG,
                                                    "failed to checkpoint refreshed item=${item.id}",
                                                    error,
                                                )
                                            }
                                        }
                                    },
                                ) { item ->
                                    batchEvaluator.evaluateTrackedApp(
                                        context = context,
                                        item = item,
                                        profilePurposeOverride = GitHubRepositoryProfilePurpose.VersionCheckFast,
                                    )
                                }
                            }.onFailure {
                                cancelRuntimeSession(runtimeSession)
                            }.getOrThrow()
                                .also { result ->
                                    AppLogger.d(
                                        GITHUB_BACKGROUND_REFRESH_TAG,
                                        "tick refreshed target=${result.totalCount}/${tracked.size} " +
                                            "elapsed=${result.performance.elapsedMs}ms " +
                                            "p50=${result.performance.p50ItemMs}ms " +
                                            "p95=${result.performance.p95ItemMs}ms " +
                                            "updatable=${result.updatableCount} " +
                                            "prerelease=${result.preReleaseUpdateCount} " +
                                            "failed=${result.failedCount}",
                                    )
                                    logTrackedRefreshFailures(result.failures)
                                    val sharedNetworkFailure = result.requiresInfrastructureRetry
                                    if (sharedNetworkFailure) {
                                        AppLogger.i(
                                            GITHUB_BACKGROUND_REFRESH_TAG,
                                            "defer background batch target=${result.totalCount} because the " +
                                                "transient network failure breaker opened",
                                        )
                                    } else {
                                        persistBackgroundRefreshResult(
                                            snapshot = snapshot,
                                            result = result,
                                            allTrackedItemsTargeted = trackedUpdateTargetItems.size == tracked.size,
                                        )
                                    }
                                    refreshHistoryService.recordCompleted(
                                        session = runtimeSession,
                                        totalTrackedCount = tracked.size,
                                        result = result,
                                        startedAtMillis = nowMs,
                                        schedulerDiagnostics =
                                            if (sharedNetworkFailure) {
                                                schedulerDiagnostics.copy(
                                                    stopReason = "shared_network_unavailable",
                                                    rescheduled = true,
                                                )
                                            } else {
                                                schedulerDiagnostics
                                            },
                                    )
                                    GitHubRefreshRuntimeStore.complete(
                                        sessionId = runtimeSession.id,
                                        completedCount = result.totalCount,
                                        updatableCount = result.updatableCount,
                                        preReleaseUpdateCount = result.preReleaseUpdateCount,
                                        failedCount = result.failedCount,
                                    )
                                }
                        } finally {
                            withContext(NonCancellable) {
                                runCatching { checkpointWriter.flush() }
                                    .onFailure { error ->
                                        AppLogger.w(
                                            GITHUB_BACKGROUND_REFRESH_TAG,
                                            "failed to flush background refresh checkpoint",
                                            error,
                                        )
                                    }
                            }
                        }
                    }
                } else {
                    null
                }
            val actionsNotificationCount =
                if (refreshResult?.requiresInfrastructureRetry == true) {
                    0
                } else {
                    handleActionsUpdates(
                        snapshot = snapshot,
                        nowMs = nowMs,
                        targetItems = actionsTargetItems,
                        onActionsUpdateAvailable = onActionsUpdateAvailable,
                    )
                }
            GitHubBackgroundTickResult(
                refreshResult = refreshResult,
                actionsNotificationCount = actionsNotificationCount,
                retryRecommended = refreshResult?.requiresInfrastructureRetry == true,
            )
        }

    suspend fun runShortcutRefresh(
        context: Context,
        onStart: (GitHubRefreshRuntimeSession, Int, Int) -> Unit,
        onProgress: suspend (GitHubRefreshRuntimeSession, GitHubTrackedRefreshBatchProgress) -> Unit,
        onActionsUpdateAvailable: suspend (GitHubActionsRecommendedRunSnapshot) -> Boolean,
    ): GitHubShortcutRefreshExecution =
        mutex.withLock {
            val nowMs = System.currentTimeMillis()
            val snapshot = withContext(AppDispatchers.githubLocal) { GitHubTrackStore.loadSnapshot() }
            val tracked = snapshot.items
            if (tracked.isEmpty()) return@withLock GitHubShortcutRefreshExecution.NoTrackedItems

            val runtimeSession =
                checkNotNull(
                    GitHubRefreshRuntimeStore.begin(
                        scope = GitHubRefreshScope.ShortcutAllTracked,
                        source = GitHubRefreshSource.Shortcut,
                        totalTrackedCount = tracked.size,
                        targetCount = tracked.size,
                        targetTrackIds = tracked.map { it.id },
                        nowMs = nowMs,
                    ),
                )
            prepareShortcutRefreshCaches()
            onStart(runtimeSession, tracked.size, tracked.size)
            val batchEvaluator =
                GitHubTrackedRefreshBatchEvaluator(
                    trackedItems = tracked,
                    existingRepositoryProfileProvider = { item ->
                        snapshot.checkCache[item.id]?.repositoryProfile ?: snapshot.profileCache[item.id]
                    },
                )
            val result =
                runCatching {
                    GitHubTrackedRefreshBatchRunner.run(
                        trackedItems = tracked,
                        networkState = GitHubRefreshNetworkKind.of(context),
                        refreshTimestampMs = nowMs,
                        batchTimeoutMs = GITHUB_SHORTCUT_BATCH_TIMEOUT_MS,
                        onProgress = { progress ->
                            GitHubRefreshRuntimeStore.progress(
                                sessionId = runtimeSession.id,
                                completedCount = progress.current,
                                updatableCount = progress.updatableCount,
                                preReleaseUpdateCount = progress.preReleaseUpdateCount,
                                failedCount = progress.failedCount,
                            )
                            onProgress(runtimeSession, progress)
                        },
                    ) { item ->
                        batchEvaluator.evaluateTrackedApp(
                            context = context,
                            item = item,
                            forceRefresh = true,
                        )
                    }
                }.onFailure {
                    cancelRuntimeSession(runtimeSession)
                }.getOrThrow()
            AppLogger.i(
                GITHUB_BACKGROUND_REFRESH_TAG,
                "shortcut refreshed total=${result.totalCount} elapsed=${result.performance.elapsedMs}ms " +
                    "p50=${result.performance.p50ItemMs}ms p95=${result.performance.p95ItemMs}ms " +
                    "updatable=${result.updatableCount} prerelease=${result.preReleaseUpdateCount} failed=${result.failedCount}",
            )
            logTrackedRefreshFailures(result.failures)
            persistRefreshResult(snapshot = snapshot, result = result)
            refreshHistoryService.recordCompleted(
                session = runtimeSession,
                totalTrackedCount = tracked.size,
                result = result,
                startedAtMillis = nowMs,
            )
            GitHubRefreshRuntimeStore.complete(
                sessionId = runtimeSession.id,
                completedCount = result.totalCount,
                updatableCount = result.updatableCount,
                preReleaseUpdateCount = result.preReleaseUpdateCount,
                failedCount = result.failedCount,
            )
            val actionsNotificationCount =
                handleActionsUpdates(
                    snapshot = snapshot,
                    nowMs = nowMs,
                    targetItems = snapshot.items.filter { it.checkActionsUpdates },
                    onActionsUpdateAvailable = onActionsUpdateAvailable,
                )
            GitHubShortcutRefreshExecution.Completed(
                result = result,
                actionsNotificationCount = actionsNotificationCount,
            )
        }

    private fun cancelRuntimeSession(session: GitHubRefreshRuntimeSession) {
        val current = GitHubRefreshRuntimeStore.state.value
        GitHubRefreshRuntimeStore.cancel(
            sessionId = session.id,
            completedCount = current.completedCount,
            updatableCount = current.updatableCount,
            preReleaseUpdateCount = current.preReleaseUpdateCount,
            failedCount = current.failedCount,
        )
    }

    private suspend fun prepareShortcutRefreshCaches() {
        withContext(AppDispatchers.githubLocal) {
            GitHubInstalledAppRepository.invalidateCache()
            GitHubReleaseStrategyRegistry.clearAllCaches()
            GitHubTrackStore.clearCheckCache()
        }
    }

    private suspend fun persistRefreshResult(
        snapshot: GitHubTrackSnapshot,
        result: GitHubTrackedRefreshBatchResult,
        replaceCache: Boolean = true,
    ) {
        withContext(AppDispatchers.githubLocal) {
            val nextRefreshTimestamp =
                if (replaceCache) {
                    result.refreshTimestampMs
                } else {
                    snapshot.lastRefreshMs
                }
            val resolvedRefreshTimestamp =
                if (replaceCache) {
                    GitHubTrackStore.saveCheckCache(result.cacheEntries, nextRefreshTimestamp)
                } else {
                    GitHubTrackStore.mergeCheckCache(result.cacheEntries, nextRefreshTimestamp)
                }
            GitHubTrackStoreSignals.notifyChanged(
                resolvedRefreshTimestamp.takeIf { it > 0L } ?: result.refreshTimestampMs
            )
        }
    }

    private suspend fun persistBackgroundRefreshResult(
        snapshot: GitHubTrackSnapshot,
        result: GitHubTrackedRefreshBatchResult,
        allTrackedItemsTargeted: Boolean,
    ) {
        val successfulEntries = result.backgroundPersistableCacheEntries()
        if (successfulEntries.isEmpty()) return
        val successfulResult = result.copy(cacheEntries = successfulEntries)
        persistRefreshResult(
            snapshot = snapshot,
            result = successfulResult,
            replaceCache = allTrackedItemsTargeted && successfulEntries.size == result.cacheEntries.size,
        )
    }

    private suspend fun persistBackgroundCheckpoint(
        entries: Map<String, GitHubCheckCacheEntry>,
        fallbackRefreshTimestamp: Long,
    ) {
        withContext(AppDispatchers.githubLocal) {
            val resolvedRefreshTimestamp =
                GitHubTrackStore.mergeCheckCache(entries, fallbackRefreshTimestamp)
            GitHubTrackStoreSignals.notifyChanged(
                resolvedRefreshTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }
    }

    private fun logTrackedRefreshFailures(failures: List<GitHubTrackedRefreshFailure>) {
        failures.forEach { failure ->
            AppLogger.w(
                GITHUB_BACKGROUND_REFRESH_TAG,
                "tracked refresh failed ${failure.logSummary()}",
            )
        }
    }

    private suspend fun handleActionsUpdates(
        snapshot: GitHubTrackSnapshot,
        nowMs: Long,
        targetItems: List<GitHubTrackedApp>,
        onActionsUpdateAvailable: suspend (GitHubActionsRecommendedRunSnapshot) -> Boolean,
    ): Int {
        val enabledItems = snapshot.items.filter { it.checkActionsUpdates }
        val refreshService = GitHubActionsRecommendedRunRefreshService(source = actionsService)
        if (enabledItems.isEmpty() || targetItems.isEmpty()) {
            refreshService.refreshItems(
                items = emptyList(),
                lookupConfig = snapshot.lookupConfig,
                retainTrackIds = enabledItems.mapTo(HashSet()) { it.id },
                nowMs = nowMs,
            )
            return 0
        }

        val result =
            refreshService.refreshItems(
                items = targetItems,
                lookupConfig = snapshot.lookupConfig,
                maxConcurrency = 2,
                retainTrackIds = enabledItems.mapTo(HashSet()) { it.id },
                nowMs = nowMs,
            )
        result.outcomes
            .filter { !it.succeeded }
            .forEach { outcome ->
                AppLogger.w(
                    GITHUB_BACKGROUND_REFRESH_TAG,
                    "actions update check failed item=${outcome.item.id}: ${outcome.errorMessage}",
                )
            }
        val notifiedCount =
            result.newerSnapshots.count { snapshot ->
                onActionsUpdateAvailable(snapshot)
            }

        if (notifiedCount > 0) {
            AppLogger.i(
                GITHUB_BACKGROUND_REFRESH_TAG,
                "actions update check notified=$notifiedCount checked=${result.checkedCount} " +
                    "succeeded=${result.succeededCount} failed=${result.failedCount} enabled=${enabledItems.size}",
            )
        }
        return notifiedCount
    }

    private suspend fun selectActionsUpdateTargets(
        snapshot: GitHubTrackSnapshot,
        nowMs: Long,
    ): List<GitHubTrackedApp> {
        val enabledItems = snapshot.items.filter { it.checkActionsUpdates }
        if (enabledItems.isEmpty()) return emptyList()
        val previousById =
            withContext(AppDispatchers.githubLocal) {
                actionsService.loadRecommendedRunSnapshots()
            }
        return selectGitHubBackgroundActionsTargets(
            items = enabledItems,
            previousById = previousById,
            refreshIntervalHours = snapshot.refreshIntervalHours,
            nowMs = nowMs,
        )
    }
}
