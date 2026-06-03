package os.kei.core.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import os.kei.core.platform.AndroidPlatformVersions
import os.kei.feature.github.data.local.GitHubTrackSnapshot
import os.kei.feature.github.domain.GitHubBackgroundScheduleSnapshot
import os.kei.feature.github.domain.GitHubTrackService
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.actionsUpdateIntervalMs
import os.kei.feature.github.model.updateIntervalMs
import os.kei.ui.page.main.ba.support.BASettingsStore

object AppBackgroundScheduler {
    private val githubTrackService = GitHubTrackService()

    fun scheduleAll(context: Context) {
        scheduleGitHubRefresh(context)
        scheduleBaApThreshold(context)
    }

    fun scheduleGitHubRefresh(context: Context) {
        val appContext = context.applicationContext
        val scheduleSnapshot = githubTrackService.loadBackgroundScheduleSnapshotBlocking()
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pending = AppBackgroundTickReceiver.githubTickPendingIntent(appContext)
        val schedule = buildGitHubRefreshSchedule(
            scheduleSnapshot = scheduleSnapshot,
            nowMs = System.currentTimeMillis(),
        )
        if (schedule == null) {
            alarmManager.cancel(pending)
            pending.cancel()
            return
        }
        scheduleWithAlarmManager(alarmManager, schedule, pending)
    }

    internal fun buildGitHubRefreshSchedule(
        scheduleSnapshot: GitHubBackgroundScheduleSnapshot,
        nowMs: Long,
    ): BackgroundAlarmSchedule? {
        val snapshot = scheduleSnapshot.trackSnapshot
        return AppBackgroundSchedulePolicy.nextGitHubRefreshSchedule(
            trackedItemCount = snapshot.items.size,
            lastRefreshMs = snapshot.lastRefreshMs,
            refreshIntervalHours = snapshot.refreshIntervalHours,
            nextTrackedUpdateDueAtMs = nextGitHubTrackedUpdateDueAtMs(
                snapshot = snapshot,
                nowMs = nowMs,
            ),
            nextActionsUpdateDueAtMs = nextGitHubActionsUpdateDueAtMs(
                snapshot = snapshot,
                previousById = scheduleSnapshot.actionsRecommendedRunsByTrackId,
                nowMs = nowMs,
            ),
            nowMs = nowMs,
        )
    }

    private fun nextGitHubTrackedUpdateDueAtMs(
        snapshot: GitHubTrackSnapshot,
        nowMs: Long
    ): Long? {
        if (snapshot.items.isEmpty()) return null
        return snapshot.items.minOfOrNull { item ->
            val checkedAtMillis = snapshot.checkCache[item.id]?.checkedAtMillis
                ?.takeIf { it > 0L }
                ?: snapshot.lastRefreshMs
            if (checkedAtMillis > 0L) {
                checkedAtMillis + item.updateIntervalMs(snapshot.refreshIntervalHours)
            } else {
                nowMs + AppBackgroundSchedulePolicy.MIN_ALARM_DELAY_MS
            }
        }
    }

    private fun nextGitHubActionsUpdateDueAtMs(
        snapshot: GitHubTrackSnapshot,
        previousById: Map<String, GitHubActionsRecommendedRunSnapshot>,
        nowMs: Long
    ): Long? {
        val enabledItems = snapshot.items.filter { it.checkActionsUpdates }
        if (enabledItems.isEmpty()) return null
        return enabledItems.minOfOrNull { item ->
            val checkedAtMillis = previousById[item.id]?.checkedAtMillis ?: 0L
            if (checkedAtMillis > 0L) {
                checkedAtMillis + item.actionsUpdateIntervalMs(snapshot.refreshIntervalHours)
            } else {
                nowMs + AppBackgroundSchedulePolicy.MIN_ALARM_DELAY_MS
            }
        }
    }

    fun scheduleBaApThreshold(context: Context) {
        val appContext = context.applicationContext
        val reminderSnapshots = BASettingsStore.loadReminderSnapshots()
        val snapshots = reminderSnapshots.map { it.snapshot }
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pending = AppBackgroundTickReceiver.baApTickPendingIntent(appContext)
        if (!AppBackgroundSchedulePolicy.hasEnabledBaReminder(snapshots)) {
            alarmManager.cancel(pending)
            pending.cancel()
            BASettingsStore.resetReminderRuntimeForAccounts(reminderSnapshots.map { it.accountId })
            return
        }
        val schedule = AppBackgroundSchedulePolicy.nextBaReminderSchedule(
            snapshots = snapshots,
            nowMs = System.currentTimeMillis()
        )
        if (schedule == null) {
            alarmManager.cancel(pending)
            pending.cancel()
            return
        }
        scheduleWithAlarmManager(alarmManager, schedule, pending)
    }

    internal fun onTickHandled(context: Context, action: String) {
        when (action) {
            AppBackgroundTickReceiver.ACTION_GITHUB_TICK -> scheduleGitHubRefresh(context)
            AppBackgroundTickReceiver.ACTION_BA_AP_TICK -> scheduleBaApThreshold(context)
        }
    }

    private fun scheduleWithAlarmManager(
        alarmManager: AlarmManager,
        schedule: BackgroundAlarmSchedule,
        pendingIntent: PendingIntent
    ) {
        val nowMs = System.currentTimeMillis()
        val triggerAtMillis = schedule.triggerAtMillis.coerceAtLeast(
            nowMs + AppBackgroundSchedulePolicy.MIN_ALARM_DELAY_MS
        )
        alarmManager.cancel(pendingIntent)
        if (AndroidPlatformVersions.isAtLeastAndroid17) {
            scheduleAndroid17Alarm(alarmManager, schedule, triggerAtMillis, nowMs, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun scheduleAndroid17Alarm(
        alarmManager: AlarmManager,
        schedule: BackgroundAlarmSchedule,
        triggerAtMillis: Long,
        nowMs: Long,
        pendingIntent: PendingIntent,
    ) {
        val alarmType = when (schedule.workload) {
            BackgroundAlarmWorkload.RoutineSync -> AlarmManager.RTC
            BackgroundAlarmWorkload.UserReminder -> AlarmManager.RTC_WAKEUP
        }
        when (schedule.precision) {
            BackgroundAlarmPrecision.Prompt -> alarmManager.set(
                alarmType,
                triggerAtMillis,
                pendingIntent
            )

            BackgroundAlarmPrecision.Windowed -> alarmManager.setWindow(
                alarmType,
                triggerAtMillis,
                AppBackgroundSchedulePolicy.android17WindowLength(
                    triggerAtMillis = triggerAtMillis,
                    nowMs = nowMs
                ),
                pendingIntent
            )
        }
    }
}
