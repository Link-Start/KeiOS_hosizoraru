package os.kei.core.background

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.core.log.AppLogger
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
import os.kei.feature.github.data.local.GitHubReleaseAssetCacheStore
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseStrategyRegistry
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.domain.GitHubActionsUpdateCheckService
import os.kei.feature.github.domain.GitHubReleaseCheckService
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchProgress
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchResult
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchRunner
import os.kei.feature.github.notification.GitHubActionsUpdateNotificationHelper
import os.kei.feature.github.notification.GitHubRefreshNotificationHelper
import os.kei.ui.page.main.ba.BaApNotificationDispatcher
import os.kei.ui.page.main.ba.BaApReminderPlan
import os.kei.ui.page.main.ba.BaArenaRefreshNotificationDispatcher
import os.kei.ui.page.main.ba.BaCafeApNotificationDispatcher
import os.kei.ui.page.main.ba.BaCafeApReminderPlan
import os.kei.ui.page.main.ba.BaCafeVisitNotificationDispatcher
import os.kei.ui.page.main.ba.BaReminderCoordinator
import os.kei.ui.page.main.ba.BaSlotReminderPlan
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaPageSnapshot

object AppForegroundInfoHandler {
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_BATCH_SIZE = 2
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_MIN_INTERVAL_MS = 500L
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_INTERVAL_MS = 850L

    private val githubTickMutex = Mutex()
    private val baApTickMutex = Mutex()

    suspend fun handleGitHubTick(context: Context) {
        githubTickMutex.withLock {
            val snapshot = withContext(Dispatchers.IO) { GitHubTrackStore.loadSnapshot() }
            val tracked = snapshot.items
            if (tracked.isEmpty()) return

            val intervalMs = snapshot.refreshIntervalHours.coerceIn(1, 12) * 60L * 60L * 1000L
            val nowMs = System.currentTimeMillis()
            if (snapshot.lastRefreshMs > 0L &&
                (nowMs - snapshot.lastRefreshMs).coerceAtLeast(0L) < intervalMs
            ) {
                return
            }

            val result = GitHubTrackedRefreshBatchRunner.run(
                trackedItems = tracked,
                refreshTimestampMs = nowMs
            ) { item ->
                GitHubReleaseCheckService.evaluateTrackedApp(context, item)
            }
            AppLogger.d(
                "AppForegroundInfoHandler",
                "github tick refreshed total=${result.totalCount} elapsed=${result.performance.elapsedMs}ms " +
                        "p50=${result.performance.p50ItemMs}ms p95=${result.performance.p95ItemMs}ms " +
                        "updatable=${result.updatableCount} prerelease=${result.preReleaseUpdateCount} failed=${result.failedCount}"
            )

            persistGitHubRefreshResult(result)
            handleGitHubActionsUpdates(
                context = context,
                snapshot = snapshot,
                nowMs = nowMs
            )
            if (result.hasNotifiableOutcome) {
                GitHubRefreshNotificationHelper.notifyCompleted(
                    context = context,
                    total = result.totalCount,
                    preReleaseUpdateCount = result.preReleaseUpdateCount,
                    updatableCount = result.updatableCount,
                    failedCount = result.failedCount
                )
            }
        }
    }

    internal suspend fun handleGitHubShortcutRefresh(context: Context): AppShortcutGitHubRefreshResult {
        githubTickMutex.withLock {
            val nowMs = System.currentTimeMillis()
            val snapshot = withContext(Dispatchers.IO) { GitHubTrackStore.loadSnapshot() }
            val tracked = snapshot.items
            if (tracked.isEmpty()) {
                GitHubRefreshNotificationHelper.cancel(context)
                return AppShortcutGitHubRefreshResult.NoTrackedItems
            }

            prepareGitHubShortcutRefreshCaches()
            val progressNotifier = GitHubShortcutRefreshProgressNotifier(context = context)
            progressNotifier.notifyInitial(total = tracked.size)
            val result = GitHubTrackedRefreshBatchRunner.run(
                trackedItems = tracked,
                refreshTimestampMs = nowMs,
                onProgress = progressNotifier::notifyProgress
            ) { item ->
                GitHubReleaseCheckService.evaluateTrackedApp(
                    context = context,
                    item = item,
                    forceRefresh = true
                )
            }
            AppLogger.i(
                "AppForegroundInfoHandler",
                "github shortcut refreshed total=${result.totalCount} elapsed=${result.performance.elapsedMs}ms " +
                        "p50=${result.performance.p50ItemMs}ms p95=${result.performance.p95ItemMs}ms " +
                        "updatable=${result.updatableCount} prerelease=${result.preReleaseUpdateCount} failed=${result.failedCount}"
            )
            persistGitHubRefreshResult(result)
            handleGitHubActionsUpdates(
                context = context,
                snapshot = snapshot,
                nowMs = nowMs
            )
            GitHubRefreshNotificationHelper.notifyCompleted(
                context = context,
                total = result.totalCount,
                preReleaseUpdateCount = result.preReleaseUpdateCount,
                updatableCount = result.updatableCount,
                failedCount = result.failedCount
            )
            AppBackgroundScheduler.scheduleGitHubRefresh(context)
            return AppShortcutGitHubRefreshResult.Completed
        }
    }

    private suspend fun prepareGitHubShortcutRefreshCaches() {
        withContext(Dispatchers.IO) {
            GitHubVersionUtils.invalidateInstalledLaunchableAppsCache()
            GitHubReleaseStrategyRegistry.clearAllCaches()
            GitHubTrackStore.clearCheckCache()
            GitHubReleaseAssetCacheStore.clearAll()
        }
    }

    private suspend fun persistGitHubRefreshResult(result: GitHubTrackedRefreshBatchResult) {
        withContext(Dispatchers.IO) {
            GitHubTrackStore.saveCheckCache(result.cacheEntries, result.refreshTimestampMs)
            GitHubTrackStoreSignals.notifyChanged(result.refreshTimestampMs)
        }
    }

    private class GitHubShortcutRefreshProgressNotifier(
        private val context: Context
    ) {
        private val mutex = Mutex()
        private var lastNotifyAtMs = 0L

        fun notifyInitial(total: Int) {
            GitHubRefreshNotificationHelper.notifyProgress(
                context = context,
                current = 0,
                total = total,
                preReleaseUpdateCount = 0,
                updatableCount = 0,
                failedCount = 0
            )
        }

        suspend fun notifyProgress(progress: GitHubTrackedRefreshBatchProgress) {
            val shouldNotify = mutex.withLock {
                if (progress.current >= progress.total) return@withLock false
                val nowMs = System.currentTimeMillis()
                val elapsedMs = (nowMs - lastNotifyAtMs).coerceAtLeast(0L)
                val shouldEmit = progress.current == 1 ||
                        elapsedMs >= GITHUB_SHORTCUT_PROGRESS_NOTIFY_INTERVAL_MS ||
                        (
                                progress.current % GITHUB_SHORTCUT_PROGRESS_NOTIFY_BATCH_SIZE == 0 &&
                                        elapsedMs >= GITHUB_SHORTCUT_PROGRESS_NOTIFY_MIN_INTERVAL_MS
                                )
                if (shouldEmit) {
                    lastNotifyAtMs = nowMs
                }
                shouldEmit
            }
            if (!shouldNotify) return
            runCatching {
                GitHubRefreshNotificationHelper.notifyProgress(
                    context = context,
                    current = progress.current,
                    total = progress.total,
                    preReleaseUpdateCount = progress.preReleaseUpdateCount,
                    updatableCount = progress.updatableCount,
                    failedCount = progress.failedCount
                )
            }.onFailure { error ->
                AppLogger.w(
                    "AppForegroundInfoHandler",
                    "github shortcut progress notification failed",
                    error
                )
            }
        }
    }

    private suspend fun handleGitHubActionsUpdates(
        context: Context,
        snapshot: GitHubTrackSnapshot,
        nowMs: Long
    ) {
        val enabledItems = snapshot.items.filter { it.checkActionsUpdates }
        withContext(Dispatchers.IO) {
            GitHubActionsRecommendedRunStore.retain(enabledItems.map { it.id }.toSet())
        }
        if (enabledItems.isEmpty()) return

        val service = GitHubActionsUpdateCheckService()
        val notifiedCount = GitHubExecution.mapOrderedBounded(
            items = enabledItems,
            maxConcurrency = 2
        ) { item ->
            val previous = withContext(Dispatchers.IO) {
                GitHubActionsRecommendedRunStore.load(item.id)
            }
            val current = service.fetchRecommendedRunSnapshot(
                item = item,
                lookupConfig = snapshot.lookupConfig,
                previousWorkflowId = previous?.workflowId,
                nowMs = nowMs
            ).getOrElse { error ->
                AppLogger.w(
                    "AppForegroundInfoHandler",
                    "github actions update check failed item=${item.id}: ${error.message}"
                )
                return@mapOrderedBounded false
            }
            withContext(Dispatchers.IO) {
                GitHubActionsRecommendedRunStore.save(current)
            }
            if (previous != null && current.isNewerThan(previous)) {
                GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                    context = context,
                    snapshot = current
                )
            } else {
                false
            }
        }.count { it }

        if (notifiedCount > 0) {
            AppLogger.i(
                "AppForegroundInfoHandler",
                "github actions update check notified=$notifiedCount total=${enabledItems.size}"
            )
        }
    }

    suspend fun handleBaApTick(context: Context) {
        baApTickMutex.withLock {
            val snapshot = withContext(Dispatchers.IO) { BASettingsStore.loadSnapshot() }
            val shouldHandleApNotify = snapshot.apNotifyEnabled
            val shouldHandleCafeApNotify = snapshot.cafeApNotifyEnabled
            val shouldHandleArenaRefreshNotify = snapshot.arenaRefreshNotifyEnabled
            val shouldHandleCafeVisitNotify = snapshot.cafeVisitNotifyEnabled
            if (!shouldHandleApNotify &&
                !shouldHandleCafeApNotify &&
                !shouldHandleArenaRefreshNotify &&
                !shouldHandleCafeVisitNotify
            ) {
                withContext(Dispatchers.IO) {
                    BASettingsStore.saveApLastNotifiedLevel(-1)
                    BASettingsStore.saveCafeApLastNotifiedLevel(-1)
                    BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(0L)
                    BASettingsStore.saveCafeVisitLastNotifiedSlotMs(0L)
                }
                return
            }

            val nowMs = System.currentTimeMillis()
            if (shouldHandleApNotify) {
                handleBaApThresholdTick(context = context, snapshot = snapshot, nowMs = nowMs)
            } else {
                withContext(Dispatchers.IO) {
                    BASettingsStore.saveApLastNotifiedLevel(-1)
                }
            }

            if (shouldHandleCafeApNotify) {
                handleBaCafeApThresholdTick(context = context, snapshot = snapshot, nowMs = nowMs)
            } else {
                withContext(Dispatchers.IO) {
                    BASettingsStore.saveCafeApLastNotifiedLevel(-1)
                }
            }

            if (shouldHandleArenaRefreshNotify) {
                handleBaArenaRefreshTick(context = context, snapshot = snapshot, nowMs = nowMs)
            } else {
                withContext(Dispatchers.IO) {
                    BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(0L)
                }
            }

            if (shouldHandleCafeVisitNotify) {
                handleBaCafeVisitTick(context = context, snapshot = snapshot, nowMs = nowMs)
            } else {
                withContext(Dispatchers.IO) {
                    BASettingsStore.saveCafeVisitLastNotifiedSlotMs(0L)
                }
            }
        }
    }

    private suspend fun handleBaCafeApThresholdTick(
        context: Context,
        snapshot: BaPageSnapshot,
        nowMs: Long,
    ) {
        val plan = BaReminderCoordinator.evaluateCafeApThreshold(snapshot = snapshot, nowMs = nowMs)
        persistBaCafeApReminderPlan(plan)
        val notification = plan.notification ?: return
        val sent = BaCafeApNotificationDispatcher.send(
            context = context,
            currentDisplay = notification.currentDisplay,
            limitDisplay = notification.limitDisplay,
            thresholdDisplay = notification.thresholdDisplay
        )
        if (sent) {
            withContext(Dispatchers.IO) {
                BASettingsStore.saveCafeApLastNotifiedLevel(notification.currentDisplay)
            }
        }
    }

    private suspend fun persistBaCafeApReminderPlan(plan: BaCafeApReminderPlan) {
        if (plan.shouldSaveCafe) {
            withContext(Dispatchers.IO) {
                BASettingsStore.saveCafeStoredAp(plan.nextStoredAp)
                BASettingsStore.saveCafeLastHourMs(plan.nextCafeLastHourMs)
            }
        }
        if (plan.resetLastNotifiedLevel) {
            withContext(Dispatchers.IO) {
                BASettingsStore.saveCafeApLastNotifiedLevel(-1)
            }
        }
    }

    private suspend fun handleBaApThresholdTick(
        context: Context,
        snapshot: BaPageSnapshot,
        nowMs: Long,
    ) {
        val plan = BaReminderCoordinator.evaluateApThreshold(snapshot = snapshot, nowMs = nowMs)
        persistBaApReminderPlan(plan)
        val notification = plan.notification ?: return
        val sent = BaApNotificationDispatcher.send(
            context = context,
            currentDisplay = notification.currentDisplay,
            limitDisplay = notification.limitDisplay,
            thresholdDisplay = notification.thresholdDisplay
        )
        if (sent) {
            withContext(Dispatchers.IO) {
                BASettingsStore.saveApLastNotifiedLevel(notification.currentDisplay)
            }
        }
    }

    private suspend fun persistBaApReminderPlan(plan: BaApReminderPlan) {
        if (plan.shouldSaveAp) {
            withContext(Dispatchers.IO) {
                BASettingsStore.saveApCurrent(plan.nextAp)
                BASettingsStore.saveApRegenBaseMs(plan.nextApRegenBaseMs)
            }
        }
        if (plan.resetLastNotifiedLevel) {
            withContext(Dispatchers.IO) {
                BASettingsStore.saveApLastNotifiedLevel(-1)
            }
        }
    }

    private suspend fun handleBaCafeVisitTick(
        context: Context,
        snapshot: BaPageSnapshot,
        nowMs: Long,
    ) {
        when (val plan = BaReminderCoordinator.evaluateCafeVisit(snapshot = snapshot, nowMs = nowMs)) {
            BaSlotReminderPlan.None -> Unit
            BaSlotReminderPlan.Reset -> {
                withContext(Dispatchers.IO) { BASettingsStore.saveCafeVisitLastNotifiedSlotMs(0L) }
            }

            is BaSlotReminderPlan.SeedBaseline -> {
                withContext(Dispatchers.IO) { BASettingsStore.saveCafeVisitLastNotifiedSlotMs(plan.slotMs) }
            }

            is BaSlotReminderPlan.Notify -> {
                val sent = BaCafeVisitNotificationDispatcher.send(
                    context = context,
                    serverIndex = snapshot.serverIndex,
                    slotMs = plan.slotMs
                )
                if (sent) {
                    withContext(Dispatchers.IO) { BASettingsStore.saveCafeVisitLastNotifiedSlotMs(plan.slotMs) }
                }
            }
        }
    }

    private suspend fun handleBaArenaRefreshTick(
        context: Context,
        snapshot: BaPageSnapshot,
        nowMs: Long,
    ) {
        when (val plan = BaReminderCoordinator.evaluateArenaRefresh(snapshot = snapshot, nowMs = nowMs)) {
            BaSlotReminderPlan.None -> Unit
            BaSlotReminderPlan.Reset -> {
                withContext(Dispatchers.IO) { BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(0L) }
            }

            is BaSlotReminderPlan.SeedBaseline -> {
                withContext(Dispatchers.IO) { BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(plan.slotMs) }
            }

            is BaSlotReminderPlan.Notify -> {
                val sent = BaArenaRefreshNotificationDispatcher.send(
                    context = context,
                    serverIndex = snapshot.serverIndex,
                    slotMs = plan.slotMs
                )
                if (sent) {
                    withContext(Dispatchers.IO) { BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(plan.slotMs) }
                }
            }
        }
    }

}

internal enum class AppShortcutGitHubRefreshResult {
    Completed,
    NoTrackedItems
}
