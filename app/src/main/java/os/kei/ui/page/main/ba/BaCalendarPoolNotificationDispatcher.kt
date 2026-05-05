package os.kei.ui.page.main.ba

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.baCalendarKindLabel
import os.kei.ui.page.main.ba.support.baPoolTagLabel
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone

internal object BaCalendarPoolNotificationDispatcher {
    private const val CALENDAR_UPCOMING_NOTIFICATION_ID = 38910
    private const val CALENDAR_ENDING_NOTIFICATION_ID = 38911
    private const val POOL_UPCOMING_NOTIFICATION_ID = 38912
    private const val POOL_ENDING_NOTIFICATION_ID = 38913
    private const val CHANGE_NOTIFICATION_ID = 38914

    fun sendCalendarUpcoming(
        context: Context,
        serverIndex: Int,
        entry: BaCalendarEntry,
    ): Boolean {
        return send(
            context = context,
            notificationId = CALENDAR_UPCOMING_NOTIFICATION_ID,
            title = context.getString(R.string.ba_calendar_notify_upcoming_title),
            content = context.getString(
                R.string.ba_calendar_notify_upcoming_content,
                entry.title.ifBlank {
                    context.baCalendarKindLabel(entry.kindId, entry.kindName)
                },
                formatBaDateTimeNoYearInTimeZone(
                    entry.beginAtMs,
                    serverRefreshTimeZone(serverIndex)
                )
            )
        )
    }

    fun sendCalendarEnding(
        context: Context,
        serverIndex: Int,
        entry: BaCalendarEntry,
    ): Boolean {
        return send(
            context = context,
            notificationId = CALENDAR_ENDING_NOTIFICATION_ID,
            title = context.getString(R.string.ba_calendar_notify_ending_title),
            content = context.getString(
                R.string.ba_calendar_notify_ending_content,
                entry.title.ifBlank {
                    context.baCalendarKindLabel(entry.kindId, entry.kindName)
                },
                formatBaDateTimeNoYearInTimeZone(entry.endAtMs, serverRefreshTimeZone(serverIndex))
            )
        )
    }

    fun sendPoolUpcoming(
        context: Context,
        serverIndex: Int,
        entry: BaPoolEntry,
    ): Boolean {
        return send(
            context = context,
            notificationId = POOL_UPCOMING_NOTIFICATION_ID,
            title = context.getString(R.string.ba_pool_notify_upcoming_title),
            content = context.getString(
                R.string.ba_pool_notify_upcoming_content,
                entry.name.ifBlank { context.baPoolTagLabel(entry.tagId, entry.tagName) },
                formatBaDateTimeNoYearInTimeZone(
                    entry.startAtMs,
                    serverRefreshTimeZone(serverIndex)
                )
            )
        )
    }

    fun sendPoolEnding(
        context: Context,
        serverIndex: Int,
        entry: BaPoolEntry,
    ): Boolean {
        return send(
            context = context,
            notificationId = POOL_ENDING_NOTIFICATION_ID,
            title = context.getString(R.string.ba_pool_notify_ending_title),
            content = context.getString(
                R.string.ba_pool_notify_ending_content,
                entry.name.ifBlank { context.baPoolTagLabel(entry.tagId, entry.tagName) },
                formatBaDateTimeNoYearInTimeZone(entry.endAtMs, serverRefreshTimeZone(serverIndex))
            )
        )
    }

    fun sendDataChanged(
        context: Context,
        calendarChangeCount: Int,
        poolChangeCount: Int,
    ): Boolean {
        return send(
            context = context,
            notificationId = CHANGE_NOTIFICATION_ID,
            title = context.getString(R.string.ba_calendar_pool_notify_change_title),
            content = context.getString(
                R.string.ba_calendar_pool_notify_change_content,
                calendarChangeCount.coerceAtLeast(0),
                poolChangeCount.coerceAtLeast(0)
            )
        )
    }

    private fun notificationsGranted(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun send(
        context: Context,
        notificationId: Int,
        title: String,
        content: String,
    ): Boolean {
        if (!notificationsGranted(context)) return false
        McpNotificationHelper.ensureChannel(context)
        val notification = NotificationCompat.Builder(context, McpNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kei_logo_live_update)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openBaPendingIntent(context, notificationId))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    private fun openBaPendingIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_BA)
        }
        return PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            520_100 + notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
