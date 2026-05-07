package os.kei.core.background

import android.content.Context
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.domain.GitHubReleaseCheckService
import os.kei.feature.github.domain.GitHubTrackedRefreshBatchRunner
import os.kei.feature.github.notification.GitHubRefreshNotificationHelper
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.BaApReminderPlan
import os.kei.ui.page.main.ba.BaApNotificationDispatcher
import os.kei.ui.page.main.ba.BaArenaRefreshNotificationDispatcher
import os.kei.ui.page.main.ba.BaCafeVisitNotificationDispatcher
import os.kei.ui.page.main.ba.BaReminderCoordinator
import os.kei.ui.page.main.ba.BaSlotReminderPlan
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AppForegroundInfoHandler {
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

            withContext(Dispatchers.IO) {
                GitHubTrackStore.saveCheckCache(result.cacheEntries, result.refreshTimestampMs)
            }
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

    suspend fun handleBaApTick(context: Context) {
        baApTickMutex.withLock {
            val snapshot = withContext(Dispatchers.IO) { BASettingsStore.loadSnapshot() }
            val shouldHandleApNotify = snapshot.apNotifyEnabled
            val shouldHandleArenaRefreshNotify = snapshot.arenaRefreshNotifyEnabled
            val shouldHandleCafeVisitNotify = snapshot.cafeVisitNotifyEnabled
            if (!shouldHandleApNotify && !shouldHandleArenaRefreshNotify && !shouldHandleCafeVisitNotify) {
                withContext(Dispatchers.IO) {
                    BASettingsStore.saveApLastNotifiedLevel(-1)
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
