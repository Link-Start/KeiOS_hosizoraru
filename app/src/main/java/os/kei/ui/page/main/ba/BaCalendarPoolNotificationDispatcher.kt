package os.kei.ui.page.main.ba

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.feature.notification.NotificationActionReceiver
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.baCalendarKindLabel
import os.kei.ui.page.main.ba.support.baPoolTagLabel
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone

internal object BaCalendarPoolNotificationDispatcher {
    const val CHANNEL_ID = "ba_calendar_pool_live_channel_v1"
    private const val CALENDAR_UPCOMING_NOTIFICATION_ID_BASE = 389_100_000
    private const val CALENDAR_ENDING_NOTIFICATION_ID_BASE = 390_100_000
    private const val POOL_UPCOMING_NOTIFICATION_ID_BASE = 391_100_000
    private const val POOL_ENDING_NOTIFICATION_ID_BASE = 392_100_000
    private const val CHANGE_NOTIFICATION_ID = 38914
    private const val ICON_RES_ID = R.drawable.ic_ba_calendar_live_update
    private const val LIVE_PROGRESS_COLOR = 0xFF2563EB.toInt()
    private const val MAX_VISIBLE_NAMES = 3

    fun sendCalendarUpcoming(
        context: Context,
        serverIndex: Int,
        entry: BaCalendarEntry,
    ): Boolean {
        return sendCalendarUpcomingGroup(context, serverIndex, listOf(entry))
    }

    fun sendCalendarUpcomingGroup(
        context: Context,
        serverIndex: Int,
        entries: List<BaCalendarEntry>,
    ): Boolean {
        val normalizedEntries = entries.sortedBy { it.title }
        val notifyAtMs = normalizedEntries.firstOrNull()?.beginAtMs ?: return false
        return sendLiveUpdate(
            context = context,
            notificationId = groupedNotificationId(
                CALENDAR_UPCOMING_NOTIFICATION_ID_BASE,
                notifyAtMs
            ),
            title = context.getString(R.string.ba_calendar_notify_upcoming_title),
            content = context.getString(
                R.string.ba_calendar_notify_upcoming_content,
                summarizeCalendarEntries(context, normalizedEntries),
                formatBaDateTimeNoYearInTimeZone(
                    notifyAtMs,
                    serverRefreshTimeZone(serverIndex)
                )
            ),
            shortText = context.getString(R.string.ba_debug_action_calendar_upcoming_notification)
        )
    }

    fun sendCalendarEnding(
        context: Context,
        serverIndex: Int,
        entry: BaCalendarEntry,
    ): Boolean {
        return sendCalendarEndingGroup(context, serverIndex, listOf(entry))
    }

    fun sendCalendarEndingGroup(
        context: Context,
        serverIndex: Int,
        entries: List<BaCalendarEntry>,
    ): Boolean {
        val normalizedEntries = entries.sortedBy { it.title }
        val notifyAtMs = normalizedEntries.firstOrNull()?.endAtMs ?: return false
        return sendLiveUpdate(
            context = context,
            notificationId = groupedNotificationId(
                CALENDAR_ENDING_NOTIFICATION_ID_BASE,
                notifyAtMs
            ),
            title = context.getString(R.string.ba_calendar_notify_ending_title),
            content = context.getString(
                R.string.ba_calendar_notify_ending_content,
                summarizeCalendarEntries(context, normalizedEntries),
                formatBaDateTimeNoYearInTimeZone(notifyAtMs, serverRefreshTimeZone(serverIndex))
            ),
            shortText = context.getString(R.string.ba_debug_action_calendar_ending_notification)
        )
    }

    fun sendPoolUpcoming(
        context: Context,
        serverIndex: Int,
        entry: BaPoolEntry,
    ): Boolean {
        return sendPoolUpcomingGroup(context, serverIndex, listOf(entry))
    }

    fun sendPoolUpcomingGroup(
        context: Context,
        serverIndex: Int,
        entries: List<BaPoolEntry>,
    ): Boolean {
        val normalizedEntries = entries.sortedBy { it.name }
        val notifyAtMs = normalizedEntries.firstOrNull()?.startAtMs ?: return false
        return sendLiveUpdate(
            context = context,
            notificationId = groupedNotificationId(POOL_UPCOMING_NOTIFICATION_ID_BASE, notifyAtMs),
            title = context.getString(R.string.ba_pool_notify_upcoming_title),
            content = context.getString(
                R.string.ba_pool_notify_upcoming_content,
                summarizePoolEntries(context, normalizedEntries),
                formatBaDateTimeNoYearInTimeZone(
                    notifyAtMs,
                    serverRefreshTimeZone(serverIndex)
                )
            ),
            shortText = context.getString(R.string.ba_debug_action_pool_upcoming_notification)
        )
    }

    fun sendPoolEnding(
        context: Context,
        serverIndex: Int,
        entry: BaPoolEntry,
    ): Boolean {
        return sendPoolEndingGroup(context, serverIndex, listOf(entry))
    }

    fun sendPoolEndingGroup(
        context: Context,
        serverIndex: Int,
        entries: List<BaPoolEntry>,
    ): Boolean {
        val normalizedEntries = entries.sortedBy { it.name }
        val notifyAtMs = normalizedEntries.firstOrNull()?.endAtMs ?: return false
        return sendLiveUpdate(
            context = context,
            notificationId = groupedNotificationId(POOL_ENDING_NOTIFICATION_ID_BASE, notifyAtMs),
            title = context.getString(R.string.ba_pool_notify_ending_title),
            content = context.getString(
                R.string.ba_pool_notify_ending_content,
                summarizePoolEntries(context, normalizedEntries),
                formatBaDateTimeNoYearInTimeZone(notifyAtMs, serverRefreshTimeZone(serverIndex))
            ),
            shortText = context.getString(R.string.ba_debug_action_pool_ending_notification)
        )
    }

    fun sendDataChanged(
        context: Context,
        calendarChangeCount: Int,
        poolChangeCount: Int,
        detail: String = "",
    ): Boolean {
        val baseContent = context.getString(
            R.string.ba_calendar_pool_notify_change_content,
            calendarChangeCount.coerceAtLeast(0),
            poolChangeCount.coerceAtLeast(0)
        )
        return sendLiveUpdate(
            context = context,
            notificationId = CHANGE_NOTIFICATION_ID,
            title = context.getString(R.string.ba_calendar_pool_notify_change_title),
            content = detail.takeIf { it.isNotBlank() }?.let { "$baseContent · $it" }
                ?: baseContent,
            shortText = context.getString(R.string.ba_debug_action_calendar_pool_change_notification)
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.ba_calendar_pool_live_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.ba_calendar_pool_live_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
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
    private fun sendLiveUpdate(
        context: Context,
        notificationId: Int,
        title: String,
        content: String,
        shortText: String,
    ): Boolean {
        if (!notificationsGranted(context)) return false
        ensureChannel(context)
        val openPendingIntent = openBaPendingIntent(context, notificationId)
        val acknowledgePendingIntent = acknowledgePendingIntent(context, notificationId)
        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(NotificationCompat.ProgressStyle.Segment(100).setColor(LIVE_PROGRESS_COLOR))
            )
            .setStyledByProgress(true)
            .setProgress(100)
            .setProgressTrackerIcon(IconCompat.createWithResource(context, ICON_RES_ID))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(ICON_RES_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(progressStyle)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setSilent(false)
            .setColorized(true)
            .setColor(LIVE_PROGRESS_COLOR)
            .setRequestPromotedOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShortCriticalText(shortText)
            .addAction(0, context.getString(R.string.common_open), openPendingIntent)
            .addAction(0, context.getString(R.string.common_acknowledge), acknowledgePendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    private fun summarizeCalendarEntries(
        context: Context,
        entries: List<BaCalendarEntry>,
    ): String {
        return summarizeNames(
            context = context,
            names = entries.map { entry ->
                entry.title.ifBlank {
                    context.baCalendarKindLabel(entry.kindId, entry.kindName)
                }
            }
        )
    }

    private fun summarizePoolEntries(
        context: Context,
        entries: List<BaPoolEntry>,
    ): String {
        return summarizeNames(
            context = context,
            names = entries.map { entry ->
                entry.name.ifBlank { context.baPoolTagLabel(entry.tagId, entry.tagName) }
            }
        )
    }

    private fun summarizeNames(
        context: Context,
        names: List<String>,
    ): String {
        val visibleNames = names
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_VISIBLE_NAMES)
        val separator = context.getString(R.string.ba_calendar_pool_notify_name_separator)
        val visibleText = visibleNames.joinToString(separator = separator)
        val remainingCount = (names.size - visibleNames.size).coerceAtLeast(0)
        return if (remainingCount > 0) {
            context.getString(
                R.string.ba_calendar_pool_notify_name_list_more,
                visibleText,
                remainingCount
            )
        } else {
            visibleText
        }
    }

    private fun groupedNotificationId(baseId: Int, notifyAtMs: Long): Int {
        val timeBucketHash = (notifyAtMs / 60_000L).hashCode().and(0x7fffffff) % 900_000
        return baseId + timeBucketHash
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

    private fun acknowledgePendingIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            521_100 + notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
