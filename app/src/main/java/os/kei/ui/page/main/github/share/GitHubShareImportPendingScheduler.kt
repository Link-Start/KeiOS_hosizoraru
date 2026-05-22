package os.kei.ui.page.main.github.share

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.notification.GitHubShareImportActionReceiver

internal object GitHubShareImportPendingScheduler {
    private const val REQUEST_CODE_SHARE_IMPORT_TICK = 42003
    private const val FIRST_TICK_DELAY_MS = 15_000L
    private const val REFRESH_TICK_DELAY_MS = 60_000L

    fun scheduleNext(
        context: Context,
        clock: GitHubShareImportClock = GitHubSystemShareImportClock,
    ) {
        val appContext = context.applicationContext
        val pending = GitHubTrackStore.loadPendingShareImportTrack()
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = tickPendingIntent(appContext)
        if (pending == null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            return
        }
        val now = clock.nowMs()
        val age = (now - pending.armedAtMillis).coerceAtLeast(0L)
        val expiresAt = pending.armedAtMillis + shareImportTrackMaxAgeMs
        val delay = if (age < FIRST_TICK_DELAY_MS) FIRST_TICK_DELAY_MS else REFRESH_TICK_DELAY_MS
        val triggerAt =
            (now + delay).coerceAtMost(expiresAt).coerceAtLeast(now + FIRST_TICK_DELAY_MS)
        alarmManager.cancel(pendingIntent)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent,
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = tickPendingIntent(appContext)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, GitHubShareImportActionReceiver::class.java).apply {
                action = GitHubShareImportActionReceiver.actionRefreshShareImport(context)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SHARE_IMPORT_TICK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
