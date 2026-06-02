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
import os.kei.ui.page.main.ba.BaAccountNotificationKind
import os.kei.ui.page.main.ba.BaApNotificationDispatcher
import os.kei.ui.page.main.ba.BaApReminderPlan
import os.kei.ui.page.main.ba.BaArenaRefreshNotificationDispatcher
import os.kei.ui.page.main.ba.BaCafeApNotificationDispatcher
import os.kei.ui.page.main.ba.BaCafeApReminderPlan
import os.kei.ui.page.main.ba.BaCafeVisitNotificationDispatcher
import os.kei.ui.page.main.ba.BaReminderCoordinator
import os.kei.ui.page.main.ba.BaSlotReminderPlan
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountReminderSnapshot
import os.kei.ui.page.main.ba.support.BaPageSnapshot

object AppForegroundInfoHandler {
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_BATCH_SIZE = 2
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_MIN_INTERVAL_MS = 500L
    private const val GITHUB_SHORTCUT_PROGRESS_NOTIFY_INTERVAL_MS = 850L

    private val githubRefreshService = GitHubBackgroundRefreshService()
    private val baApTickMutex = Mutex()

    suspend fun handleGitHubTick(context: Context) {
        val result =
            githubRefreshService.runDueRefresh(
                context = context,
                onActionsUpdateAvailable = { snapshot ->
                    GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                        context = context,
                        snapshot = snapshot,
                    )
                },
            )
        val refreshResult = result.refreshResult
        if (refreshResult?.hasNotifiableOutcome == true) {
            GitHubRefreshNotificationHelper.notifyCompleted(
                context = context,
                total = refreshResult.totalCount,
                preReleaseUpdateCount = refreshResult.preReleaseUpdateCount,
                updatableCount = refreshResult.updatableCount,
                failedCount = refreshResult.failedCount,
            )
        }
    }

    internal suspend fun handleGitHubShortcutRefresh(context: Context): AppShortcutGitHubRefreshResult {
        val progressNotifier = GitHubShortcutRefreshProgressNotifier(context = context)
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
                GitHubRefreshNotificationHelper.cancel(context)
                AppShortcutGitHubRefreshResult.NoTrackedItems
            }

            is GitHubShortcutRefreshExecution.Completed -> {
                val result = execution.result
                GitHubRefreshNotificationHelper.notifyCompleted(
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

    suspend fun handleBaApTick(context: Context) {
        baApTickMutex.withLock {
            val reminderSnapshots =
                withContext(AppDispatchers.mcpServer) {
                    BASettingsStore.loadReminderSnapshots()
                }
            if (!AppBackgroundSchedulePolicy.hasEnabledBaReminder(reminderSnapshots.map { it.snapshot })) {
                resetReminderRuntimeForAccounts(reminderSnapshots)
                return
            }

            val nowMs = System.currentTimeMillis()
            reminderSnapshots.forEach { reminderSnapshot ->
                handleBaReminderTick(
                    context = context,
                    reminderSnapshot = reminderSnapshot,
                    nowMs = nowMs,
                )
            }
        }
    }

    private suspend fun resetReminderRuntimeForAccounts(reminderSnapshots: List<BaAccountReminderSnapshot>) {
        withContext(AppDispatchers.mcpServer) {
            BASettingsStore.resetReminderRuntimeForAccounts(reminderSnapshots.map { it.accountId })
        }
    }

    private suspend fun handleBaReminderTick(
        context: Context,
        reminderSnapshot: BaAccountReminderSnapshot,
        nowMs: Long,
    ) {
        val accountId = reminderSnapshot.accountId
        val snapshot = reminderSnapshot.snapshot

        if (snapshot.apNotifyEnabled) {
            handleBaApThresholdTick(
                context = context,
                accountId = accountId,
                snapshot = snapshot,
                nowMs = nowMs,
            )
        } else {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountApLastNotifiedLevel(accountId, -1)
            }
        }

        if (snapshot.cafeApNotifyEnabled) {
            handleBaCafeApThresholdTick(
                context = context,
                accountId = accountId,
                snapshot = snapshot,
                nowMs = nowMs,
            )
        } else {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountCafeApLastNotifiedLevel(accountId, -1)
            }
        }

        if (snapshot.arenaRefreshNotifyEnabled) {
            handleBaArenaRefreshTick(
                context = context,
                accountId = accountId,
                snapshot = snapshot,
                nowMs = nowMs,
            )
        } else {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountArenaRefreshLastNotifiedSlotMs(accountId, 0L)
            }
        }

        if (snapshot.cafeVisitNotifyEnabled) {
            handleBaCafeVisitTick(
                context = context,
                accountId = accountId,
                snapshot = snapshot,
                nowMs = nowMs,
            )
        } else {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountCafeVisitLastNotifiedSlotMs(accountId, 0L)
            }
        }
    }

    private suspend fun handleBaCafeApThresholdTick(
        context: Context,
        accountId: BaAccountId,
        snapshot: BaPageSnapshot,
        nowMs: Long,
    ) {
        val plan = BaReminderCoordinator.evaluateCafeApThreshold(snapshot = snapshot, nowMs = nowMs)
        persistBaCafeApReminderPlan(accountId = accountId, plan = plan)
        val notification = plan.notification ?: return
        val sent = BaCafeApNotificationDispatcher.send(
            context = context,
            currentDisplay = notification.currentDisplay,
            limitDisplay = notification.limitDisplay,
            thresholdDisplay = notification.thresholdDisplay,
            notificationId = BaAccountNotificationKind.CafeAp.notificationId(accountId),
        )
        if (sent) {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountCafeApLastNotifiedLevel(
                    accountId = accountId,
                    level = notification.currentDisplay,
                )
            }
        }
    }

    private suspend fun persistBaCafeApReminderPlan(
        accountId: BaAccountId,
        plan: BaCafeApReminderPlan,
    ) {
        if (plan.shouldSaveCafe) {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountBaRuntimeState(
                    accountId = accountId,
                    cafeStoredAp = plan.nextStoredAp,
                    cafeLastHourMs = plan.nextCafeLastHourMs,
                    notifyHomeOverview = false,
                )
            }
        }
        if (plan.resetLastNotifiedLevel) {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountCafeApLastNotifiedLevel(
                    accountId = accountId,
                    level = -1,
                )
            }
        }
    }

    private suspend fun handleBaApThresholdTick(
        context: Context,
        accountId: BaAccountId,
        snapshot: BaPageSnapshot,
        nowMs: Long,
    ) {
        val plan = BaReminderCoordinator.evaluateApThreshold(snapshot = snapshot, nowMs = nowMs)
        persistBaApReminderPlan(accountId = accountId, plan = plan)
        val notification = plan.notification ?: return
        val sent = BaApNotificationDispatcher.send(
            context = context,
            currentDisplay = notification.currentDisplay,
            limitDisplay = notification.limitDisplay,
            thresholdDisplay = notification.thresholdDisplay,
            notificationId = BaAccountNotificationKind.Ap.notificationId(accountId),
        )
        if (sent) {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountApLastNotifiedLevel(
                    accountId = accountId,
                    level = notification.currentDisplay,
                )
            }
        }
    }

    private suspend fun persistBaApReminderPlan(
        accountId: BaAccountId,
        plan: BaApReminderPlan,
    ) {
        if (plan.shouldSaveAp) {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountBaRuntimeState(
                    accountId = accountId,
                    apCurrent = plan.nextAp,
                    apRegenBaseMs = plan.nextApRegenBaseMs,
                    notifyHomeOverview = false,
                )
            }
        }
        if (plan.resetLastNotifiedLevel) {
            withContext(AppDispatchers.mcpServer) {
                BASettingsStore.saveAccountApLastNotifiedLevel(
                    accountId = accountId,
                    level = -1,
                )
            }
        }
    }

    private suspend fun handleBaCafeVisitTick(
        context: Context,
        accountId: BaAccountId,
        snapshot: BaPageSnapshot,
        nowMs: Long,
    ) {
        when (val plan = BaReminderCoordinator.evaluateCafeVisit(snapshot = snapshot, nowMs = nowMs)) {
            BaSlotReminderPlan.None -> Unit
            BaSlotReminderPlan.Reset -> {
                withContext(AppDispatchers.mcpServer) {
                    BASettingsStore.saveAccountCafeVisitLastNotifiedSlotMs(accountId, 0L)
                }
            }

            is BaSlotReminderPlan.SeedBaseline -> {
                withContext(AppDispatchers.mcpServer) {
                    BASettingsStore.saveAccountCafeVisitLastNotifiedSlotMs(accountId, plan.slotMs)
                }
            }

            is BaSlotReminderPlan.Notify -> {
                val sent = BaCafeVisitNotificationDispatcher.send(
                    context = context,
                    serverIndex = snapshot.serverIndex,
                    slotMs = plan.slotMs,
                    notificationId = BaAccountNotificationKind.CafeVisit.notificationId(accountId),
                )
                if (sent) {
                    withContext(AppDispatchers.mcpServer) {
                        BASettingsStore.saveAccountCafeVisitLastNotifiedSlotMs(accountId, plan.slotMs)
                    }
                }
            }
        }
    }

    private suspend fun handleBaArenaRefreshTick(
        context: Context,
        accountId: BaAccountId,
        snapshot: BaPageSnapshot,
        nowMs: Long,
    ) {
        when (val plan = BaReminderCoordinator.evaluateArenaRefresh(snapshot = snapshot, nowMs = nowMs)) {
            BaSlotReminderPlan.None -> Unit
            BaSlotReminderPlan.Reset -> {
                withContext(AppDispatchers.mcpServer) {
                    BASettingsStore.saveAccountArenaRefreshLastNotifiedSlotMs(accountId, 0L)
                }
            }

            is BaSlotReminderPlan.SeedBaseline -> {
                withContext(AppDispatchers.mcpServer) {
                    BASettingsStore.saveAccountArenaRefreshLastNotifiedSlotMs(accountId, plan.slotMs)
                }
            }

            is BaSlotReminderPlan.Notify -> {
                val sent = BaArenaRefreshNotificationDispatcher.send(
                    context = context,
                    serverIndex = snapshot.serverIndex,
                    slotMs = plan.slotMs,
                    notificationId = BaAccountNotificationKind.ArenaRefresh.notificationId(accountId),
                )
                if (sent) {
                    withContext(AppDispatchers.mcpServer) {
                        BASettingsStore.saveAccountArenaRefreshLastNotifiedSlotMs(accountId, plan.slotMs)
                    }
                }
            }
        }
    }

}

internal enum class AppShortcutGitHubRefreshResult {
    Completed,
    NoTrackedItems
}
