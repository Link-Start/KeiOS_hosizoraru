package os.kei.feature.github.domain

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.log.AppLogger
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
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

class GitHubBackgroundRefreshService {
    private val mutex = Mutex()

    suspend fun runDueRefresh(
        context: Context,
        onRefreshStart: (Int) -> Unit = {},
        onRefreshProgress: suspend (GitHubTrackedRefreshBatchProgress) -> Unit = {},
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
                    onRefreshStart(trackedUpdateTargetItems.size)
                    GitHubTrackedRefreshBatchRunner
                        .run(
                            trackedItems = trackedUpdateTargetItems,
                            refreshTimestampMs = nowMs,
                            maxConcurrency = GitHubTrackedRefreshBatchScheduler
                                .backgroundRefreshConcurrency(trackedUpdateTargetItems.size),
                            onProgress = onRefreshProgress,
                        ) { item ->
                            GitHubReleaseCheckService.evaluateTrackedApp(
                                context = context,
                                item = item,
                                profilePurposeOverride = GitHubRepositoryProfilePurpose.VersionCheckFast,
                            )
                        }
                        .also { result ->
                            AppLogger.d(
                                GITHUB_BACKGROUND_REFRESH_TAG,
                                "tick refreshed total=${result.totalCount} " +
                                    "elapsed=${result.performance.elapsedMs}ms " +
                                    "p50=${result.performance.p50ItemMs}ms " +
                                    "p95=${result.performance.p95ItemMs}ms " +
                                    "updatable=${result.updatableCount} " +
                                    "prerelease=${result.preReleaseUpdateCount} " +
                                    "failed=${result.failedCount}",
                            )
                            persistRefreshResult(
                                snapshot = snapshot,
                                result = result,
                                replaceCache = trackedUpdateTargetItems.size == tracked.size,
                            )
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
        onStart: (Int) -> Unit,
        onProgress: suspend (GitHubTrackedRefreshBatchProgress) -> Unit,
        onActionsUpdateAvailable: suspend (GitHubActionsRecommendedRunSnapshot) -> Boolean,
    ): GitHubShortcutRefreshExecution =
        mutex.withLock {
            val nowMs = System.currentTimeMillis()
            val snapshot = withContext(AppDispatchers.githubLocal) { GitHubTrackStore.loadSnapshot() }
            val tracked = snapshot.items
            if (tracked.isEmpty()) return@withLock GitHubShortcutRefreshExecution.NoTrackedItems

            prepareShortcutRefreshCaches()
            onStart(tracked.size)
            val result =
                GitHubTrackedRefreshBatchRunner.run(
                    trackedItems = tracked,
                    refreshTimestampMs = nowMs,
                    onProgress = onProgress,
                ) { item ->
                    GitHubReleaseCheckService.evaluateTrackedApp(
                        context = context,
                        item = item,
                        forceRefresh = true,
                    )
                }
            AppLogger.i(
                GITHUB_BACKGROUND_REFRESH_TAG,
                "shortcut refreshed total=${result.totalCount} elapsed=${result.performance.elapsedMs}ms " +
                    "p50=${result.performance.p50ItemMs}ms p95=${result.performance.p95ItemMs}ms " +
                    "updatable=${result.updatableCount} prerelease=${result.preReleaseUpdateCount} failed=${result.failedCount}",
            )
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
            val nextCache =
                if (replaceCache) {
                    result.cacheEntries
                } else {
                    result.cacheEntries
                }
            val nextRefreshTimestamp =
                if (replaceCache) {
                    result.refreshTimestampMs
                } else {
                    snapshot.lastRefreshMs
                }
            val resolvedRefreshTimestamp =
                if (replaceCache) {
                    GitHubTrackStore.saveCheckCache(nextCache, nextRefreshTimestamp)
                } else {
                    GitHubTrackStore.mergeCheckCache(nextCache, nextRefreshTimestamp)
                }
            GitHubTrackStoreSignals.notifyChanged(
                resolvedRefreshTimestamp.takeIf { it > 0L } ?: result.refreshTimestampMs
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
        withContext(AppDispatchers.githubLocal) {
            GitHubActionsRecommendedRunStore.retain(enabledItems.map { it.id }.toSet())
        }
        if (enabledItems.isEmpty() || targetItems.isEmpty()) return 0

        val service = GitHubActionsUpdateCheckService()
        val previousById =
            withContext(AppDispatchers.githubLocal) {
                GitHubActionsRecommendedRunStore.loadAll()
            }
        val notifiedCount =
            GitHubExecution
                .mapOrderedBounded(
                    items = targetItems,
                    maxConcurrency = 2,
                ) { item ->
                    val previous = previousById[item.id]
                    val current =
                        service
                            .fetchRecommendedRunSnapshot(
                                item = item,
                                lookupConfig = snapshot.lookupConfig,
                                previousWorkflowId = previous?.workflowId,
                                nowMs = nowMs,
                            )
                            .getOrElse { error ->
                                AppLogger.w(
                                    GITHUB_BACKGROUND_REFRESH_TAG,
                                    "actions update check failed item=${item.id}: ${error.message}",
                                )
                                return@mapOrderedBounded false
                            }
                    withContext(AppDispatchers.githubLocal) {
                        GitHubActionsRecommendedRunStore.save(current)
                    }
                    previous != null && current.isNewerThan(previous) && onActionsUpdateAvailable(current)
                }
                .count { it }

        if (notifiedCount > 0) {
            AppLogger.i(
                GITHUB_BACKGROUND_REFRESH_TAG,
                "actions update check notified=$notifiedCount checked=${targetItems.size} enabled=${enabledItems.size}",
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
                GitHubActionsRecommendedRunStore.loadAll()
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
