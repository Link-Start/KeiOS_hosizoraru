package os.kei.core.background

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.log.AppLogger
import os.kei.feature.github.domain.GitHubBackgroundRefreshService
import os.kei.feature.github.domain.GitHubShortcutRefreshExecution
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchProgress
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
    private const val GITHUB_BACKGROUND_PROGRESS_NOTIFY_MIN_TOTAL = 4
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_BATCH_SIZE = 2
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_MIN_INTERVAL_MS = 500L
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_INTERVAL_MS = 850L

    private val githubRefreshService = GitHubBackgroundRefreshService()
    private val baApTickMutex = Mutex()

    suspend fun handleGitHubTick(context: Context) {
        val progressNotifier = GitHubRefreshProgressNotifier(
            context = context,
            minTotalForInitialProgress = GITHUB_BACKGROUND_PROGRESS_NOTIFY_MIN_TOTAL,
        )
        val result =
            githubRefreshService.runDueRefresh(
                context = context,
                onRefreshStart = progressNotifier::notifyInitial,
                onRefreshProgress = progressNotifier::notifyProgress,
                onActionsUpdateAvailable = { snapshot ->
                    GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                        context = context,
                        snapshot = snapshot,
                    )
                },
            )
        val refreshResult = result.refreshResult
        if (refreshResult?.hasNotifiableOutcome == true) {
            notifyGitHubRefreshCompletedOrCancel(
                context = context,
                total = refreshResult.totalCount,
                preReleaseUpdateCount = refreshResult.preReleaseUpdateCount,
                updatableCount = refreshResult.updatableCount,
                failedCount = refreshResult.failedCount,
            )
        } else if (progressNotifier.didNotify) {
            runCatching { GitHubRefreshNotificationHelper.cancel(context) }
                .onFailure { error ->
                    AppLogger.w(
                        "AppForegroundInfoHandler",
                        "github refresh quiet tick notification cancel failed",
                        error
                    )
                }
        }
    }

    internal suspend fun handleGitHubShortcutRefresh(context: Context): AppShortcutGitHubRefreshResult {
        val progressNotifier = GitHubRefreshProgressNotifier(context = context)
        return when (
            val execution =
                githubRefreshService.runShortcutRefresh(
                    context = context,
                    onStart = progressNotifier::notifyInitial,
                    onProgress = progressNotifier::notifyProgress,
                    onActionsUpdateAvailable = { snapshot ->
                        GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                            context = context,
                            snapshot = snapshot,
                        )
                    },
                )
        ) {
            GitHubShortcutRefreshExecution.NoTrackedItems -> {
                runCatching { GitHubRefreshNotificationHelper.cancel(context) }
                    .onFailure { error ->
                        AppLogger.w(
                            "AppForegroundInfoHandler",
                            "github refresh empty shortcut notification cancel failed",
                            error
                        )
                    }
                AppShortcutGitHubRefreshResult.NoTrackedItems
            }

            is GitHubShortcutRefreshExecution.Completed -> {
                val result = execution.result
                notifyGitHubRefreshCompletedOrCancel(
                    context = context,
                    total = result.totalCount,
                    preReleaseUpdateCount = result.preReleaseUpdateCount,
                    updatableCount = result.updatableCount,
                    failedCount = result.failedCount,
                )
                AppBackgroundScheduler.scheduleGitHubRefresh(context)
                AppShortcutGitHubRefreshResult.Completed
            }
        }
    }

    private fun notifyGitHubRefreshCompletedOrCancel(
        context: Context,
        total: Int,
        preReleaseUpdateCount: Int,
        updatableCount: Int,
        failedCount: Int,
    ) {
        val posted =
            runCatching {
                GitHubRefreshNotificationHelper.notifyCompleted(
                    context = context,
                    total = total,
                    preReleaseUpdateCount = preReleaseUpdateCount,
                    updatableCount = updatableCount,
                    failedCount = failedCount,
                )
            }.getOrElse { error ->
                AppLogger.w(
                    "AppForegroundInfoHandler",
                    "github refresh completed notification failed",
                    error
                )
                false
            }
        if (!posted) {
            runCatching { GitHubRefreshNotificationHelper.cancel(context) }
                .onFailure { error ->
                    AppLogger.w(
                        "AppForegroundInfoHandler",
                        "github refresh stale notification cancel failed",
                        error
                    )
                }
        }
    }

    private class GitHubRefreshProgressNotifier(
        private val context: Context,
        private val minTotalForInitialProgress: Int = 1,
    ) {
        private val mutex = Mutex()
        private var lastNotifyAtMs = 0L
        @Volatile
        var didNotify: Boolean = false
            private set

        fun notifyInitial(total: Int) {
            if (total < minTotalForInitialProgress) return
            lastNotifyAtMs = System.currentTimeMillis()
            runCatching {
                GitHubRefreshNotificationHelper.notifyProgress(
                    context = context,
                    current = 0,
                    total = total,
                    preReleaseUpdateCount = 0,
                    updatableCount = 0,
                    failedCount = 0
                )
            }.onSuccess { posted ->
                if (posted) didNotify = true
            }.onFailure { error ->
                AppLogger.w(
                    "AppForegroundInfoHandler",
                    "github refresh initial progress notification failed",
                    error
                )
            }
        }

        suspend fun notifyProgress(progress: GitHubTrackedRefreshBatchProgress) {
            if (progress.total < minTotalForInitialProgress) return
            val shouldNotify = mutex.withLock {
                if (progress.current >= progress.total) return@withLock false
                val nowMs = System.currentTimeMillis()
                val elapsedMs = (nowMs - lastNotifyAtMs).coerceAtLeast(0L)
                val shouldEmit =
                    progress.current == 1 ||
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
            }.onSuccess { posted ->
                if (posted) didNotify = true
            }.onFailure { error ->
                AppLogger.w(
                    "AppForegroundInfoHandler",
                    "github refresh progress notification failed",
                    error
                )
            }
        }
    }

    suspend fun handleBaApTick(context: Context) {
        baApTickMutex.withLock {
            val snapshot = withContext(AppDispatchers.mcpServer) { BASettingsStore.loadSnapshot() }
            val shouldHandleApNotify = snapshot.apNotifyEnabled
            val shouldHandleCafeApNotify = snapshot.cafeApNotifyEnabled
            val shouldHandleArenaRefreshNotify = snapshot.arenaRefreshNotifyEnabled
            val shouldHandleCafeVisitNotify = snapshot.cafeVisitNotifyEnabled
            if (!shouldHandleApNotify &&
                !shouldHandleCafeApNotify &&
                !shouldHandleArenaRefreshNotify &&
                !shouldHandleCafeVisitNotify
            ) {
                withContext(AppDispatchers.mcpServer) {
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
                withContext(AppDispatchers.mcpServer) {
                    BASettingsStore.saveApLastNotifiedLevel(-1)
                }
            }

            if (shouldHandleCafeApNotify) {
                handleBaCafeApThresholdTick(context = context, snapshot = snapshot, nowMs = nowMs)
            } else {
                withContext(AppDispatchers.mcpServer) {
                    BASettingsStore.saveCafeApLastNotifiedLevel(-1)
                }
            }

            if (shouldHandleArenaRefreshNotify) {
                handleBaArenaRefreshTick(context = context, snapshot = snapshot, nowMs = nowMs)
            } else {
                withContext(AppDispatchers.mcpServer) {
                    BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(0L)
                }
            }

            if (shouldHandleCafeVisitNotify) {
                handleBaCafeVisitTick(context = context, snapshot = snapshot, nowMs = nowMs)
            } else {
                withContext(AppDispatchers.mcpServer) {
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
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveCafeApLastNotifiedLevel(notification.currentDisplay)
            }
        }
    }

    private suspend fun persistBaCafeApReminderPlan(plan: BaCafeApReminderPlan) {
        if (plan.shouldSaveCafe) {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveBaRuntimeState(
                    cafeStoredAp = plan.nextStoredAp,
                    cafeLastHourMs = plan.nextCafeLastHourMs,
                    notifyHomeOverview = false,
                )
            }
        }
        if (plan.resetLastNotifiedLevel) {
            withContext(AppDispatchers.mcpServer) {
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
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveApLastNotifiedLevel(notification.currentDisplay)
            }
        }
    }

    private suspend fun persistBaApReminderPlan(plan: BaApReminderPlan) {
        if (plan.shouldSaveAp) {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveBaRuntimeState(
                    apCurrent = plan.nextAp,
                    apRegenBaseMs = plan.nextApRegenBaseMs,
                    notifyHomeOverview = false,
                )
            }
        }
        if (plan.resetLastNotifiedLevel) {
            withContext(AppDispatchers.mcpServer) {
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
                withContext(AppDispatchers.mcpServer) { BASettingsStore.saveCafeVisitLastNotifiedSlotMs(0L) }
            }

            is BaSlotReminderPlan.SeedBaseline -> {
                withContext(AppDispatchers.mcpServer) { BASettingsStore.saveCafeVisitLastNotifiedSlotMs(plan.slotMs) }
            }

            is BaSlotReminderPlan.Notify -> {
                val sent = BaCafeVisitNotificationDispatcher.send(
                    context = context,
                    serverIndex = snapshot.serverIndex,
                    slotMs = plan.slotMs
                )
                if (sent) {
                    withContext(AppDispatchers.mcpServer) { BASettingsStore.saveCafeVisitLastNotifiedSlotMs(plan.slotMs) }
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
                withContext(AppDispatchers.mcpServer) { BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(0L) }
            }

            is BaSlotReminderPlan.SeedBaseline -> {
                withContext(AppDispatchers.mcpServer) { BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(plan.slotMs) }
            }

            is BaSlotReminderPlan.Notify -> {
                val sent = BaArenaRefreshNotificationDispatcher.send(
                    context = context,
                    serverIndex = snapshot.serverIndex,
                    slotMs = plan.slotMs
                )
                if (sent) {
                    withContext(AppDispatchers.mcpServer) { BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(plan.slotMs) }
                }
            }
        }
    }

}

internal enum class AppShortcutGitHubRefreshResult {
    Completed,
    NoTrackedItems
}
