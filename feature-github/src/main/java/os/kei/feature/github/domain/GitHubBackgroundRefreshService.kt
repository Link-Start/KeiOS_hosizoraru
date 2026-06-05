package os.kei.feature.github.domain

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.log.AppLogger
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.actionsUpdateIntervalMs
import os.kei.feature.github.model.excludesAutomaticReleaseRefresh
import os.kei.feature.github.model.updateIntervalMs

private const val GITHUB_BACKGROUND_REFRESH_TAG = "GitHubBackgroundRefresh"

data class GitHubBackgroundTickResult(
    val refreshResult: GitHubTrackedRefreshBatchResult? = null,
    val actionsNotificationCount: Int = 0,
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
) {
    private val mutex = Mutex()

    suspend fun runDueRefresh(
        context: Context,
        onRefreshStart: (GitHubRefreshRuntimeSession, Int, Int) -> Unit = { _, _, _ -> },
        onRefreshProgress: suspend (GitHubRefreshRuntimeSession, GitHubTrackedRefreshBatchProgress) -> Unit = { _, _ -> },
        onActionsUpdateAvailable: suspend (GitHubActionsRecommendedRunSnapshot) -> Boolean,
    ): GitHubBackgroundTickResult =
        mutex.withLock {
            val snapshot = withContext(AppDispatchers.githubLocal) { GitHubTrackStore.loadSnapshot() }
            val tracked = snapshot.items
            if (tracked.isEmpty()) return@withLock GitHubBackgroundTickResult()

            val nowMs = System.currentTimeMillis()
            val trackedUpdateTargetItems =
                selectTrackedUpdateTargets(
                    snapshot = snapshot,
                    nowMs = nowMs,
                )
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
                    val runtimeSession =
                        GitHubRefreshRuntimeStore.begin(
                            scope = GitHubRefreshScope.DueTracked,
                            source = GitHubRefreshSource.BackgroundTick,
                            totalTrackedCount = tracked.size,
                            targetCount = trackedUpdateTargetItems.size,
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
                        runCatching {
                            GitHubTrackedRefreshBatchRunner
                                .run(
                                    trackedItems = trackedUpdateTargetItems,
                                    refreshTimestampMs = nowMs,
                                    maxConcurrency = GitHubTrackedRefreshBatchScheduler
                                        .backgroundRefreshConcurrency(trackedUpdateTargetItems.size),
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
                                ) { item ->
                                    GitHubReleaseCheckService.evaluateTrackedApp(
                                        context = context,
                                        item = item,
                                        profilePurposeOverride = GitHubRepositoryProfilePurpose.VersionCheckFast,
                                    )
                                }
                        }.onFailure {
                            cancelRuntimeSession(runtimeSession)
                        }.getOrThrow()
                            .also { result ->
                                GitHubRefreshRuntimeStore.complete(
                                    sessionId = runtimeSession.id,
                                    completedCount = result.totalCount,
                                    updatableCount = result.updatableCount,
                                    preReleaseUpdateCount = result.preReleaseUpdateCount,
                                    failedCount = result.failedCount,
                                )
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
                                persistRefreshResult(
                                    snapshot = snapshot,
                                    result = result,
                                    replaceCache = trackedUpdateTargetItems.size == tracked.size,
                                )
                            }
                    }
                } else {
                    null
                }
            val actionsNotificationCount =
                handleActionsUpdates(
                    snapshot = snapshot,
                    nowMs = nowMs,
                    targetItems = actionsTargetItems,
                    onActionsUpdateAvailable = onActionsUpdateAvailable,
                )
            GitHubBackgroundTickResult(
                refreshResult = refreshResult,
                actionsNotificationCount = actionsNotificationCount,
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
                        nowMs = nowMs,
                    ),
                )
            prepareShortcutRefreshCaches()
            onStart(runtimeSession, tracked.size, tracked.size)
            val result =
                runCatching {
                    GitHubTrackedRefreshBatchRunner.run(
                        trackedItems = tracked,
                        refreshTimestampMs = nowMs,
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
                        GitHubReleaseCheckService.evaluateTrackedApp(
                            context = context,
                            item = item,
                            forceRefresh = true,
                        )
                    }
                }.onFailure {
                    cancelRuntimeSession(runtimeSession)
                }.getOrThrow()
            GitHubRefreshRuntimeStore.complete(
                sessionId = runtimeSession.id,
                completedCount = result.totalCount,
                updatableCount = result.updatableCount,
                preReleaseUpdateCount = result.preReleaseUpdateCount,
                failedCount = result.failedCount,
            )
            AppLogger.i(
                GITHUB_BACKGROUND_REFRESH_TAG,
                "shortcut refreshed total=${result.totalCount} elapsed=${result.performance.elapsedMs}ms " +
                    "p50=${result.performance.p50ItemMs}ms p95=${result.performance.p95ItemMs}ms " +
                    "updatable=${result.updatableCount} prerelease=${result.preReleaseUpdateCount} failed=${result.failedCount}",
            )
            logTrackedRefreshFailures(result.failures)
            persistRefreshResult(snapshot = snapshot, result = result)
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
            GitHubVersionUtils.invalidateInstalledLaunchableAppsCache()
            GitHubReleaseStrategyRegistry.clearAllCaches()
            GitHubTrackStore.clearCheckCache()
            GitHubReleaseAssetCacheStore.clearAll()
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

    private fun selectTrackedUpdateTargets(
        snapshot: GitHubTrackSnapshot,
        nowMs: Long,
    ): List<GitHubTrackedApp> {
        if (snapshot.items.isEmpty()) return emptyList()
        return snapshot.items.filter { item ->
            if (item.excludesAutomaticReleaseRefresh()) return@filter false
            val checkedAtMillis =
                snapshot.checkCache[item.id]?.checkedAtMillis
                    ?.takeIf { it > 0L }
                    ?: snapshot.lastRefreshMs
            checkedAtMillis <= 0L ||
                (nowMs - checkedAtMillis).coerceAtLeast(0L) >=
                item.updateIntervalMs(snapshot.refreshIntervalHours)
        }
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
        return enabledItems.filter { item ->
            shouldCheckActionsUpdate(
                item = item,
                previous = previousById[item.id],
                refreshIntervalHours = snapshot.refreshIntervalHours,
                nowMs = nowMs,
            )
        }
    }

    private fun shouldCheckActionsUpdate(
        item: GitHubTrackedApp,
        previous: GitHubActionsRecommendedRunSnapshot?,
        refreshIntervalHours: Int,
        nowMs: Long,
    ): Boolean {
        val checkedAtMillis = previous?.checkedAtMillis ?: 0L
        if (checkedAtMillis <= 0L) return true
        val intervalMs = item.actionsUpdateIntervalMs(refreshIntervalHours)
        return (nowMs - checkedAtMillis).coerceAtLeast(0L) >= intervalMs
    }
}
