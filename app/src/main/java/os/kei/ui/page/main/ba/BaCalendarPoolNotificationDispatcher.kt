package os.kei.ui.page.main.ba

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.feature.notification.NotificationActionReceiver
import os.kei.mcp.framework.notification.NotificationHelper
import os.kei.mcp.framework.notification.SessionNotifierImpl
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.notification.McpNotificationPayload
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.baCalendarKindLabel
import os.kei.ui.page.main.ba.support.baPoolTagLabel
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone

internal object BaCalendarPoolNotificationDispatcher {
    private const val CALENDAR_UPCOMING_NOTIFICATION_ID_BASE = 389_100_000
    private const val CALENDAR_ENDING_NOTIFICATION_ID_BASE = 390_100_000
    private const val POOL_UPCOMING_NOTIFICATION_ID_BASE = 391_100_000
    private const val POOL_ENDING_NOTIFICATION_ID_BASE = 392_100_000
    private const val CHANGE_NOTIFICATION_ID = 38914
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
            shortText = context.getString(R.string.ba_debug_action_calendar_upcoming_notification),
            deadlineAtMs = notifyAtMs,
            progressPercent = resolveDeadlineProgressPercent(notifyAtMs)
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
            shortText = context.getString(R.string.ba_debug_action_calendar_ending_notification),
            deadlineAtMs = notifyAtMs,
            progressPercent = resolveDeadlineProgressPercent(notifyAtMs)
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
            shortText = context.getString(R.string.ba_debug_action_pool_upcoming_notification),
            deadlineAtMs = notifyAtMs,
            progressPercent = resolveDeadlineProgressPercent(notifyAtMs)
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
            shortText = context.getString(R.string.ba_debug_action_pool_ending_notification),
            deadlineAtMs = notifyAtMs,
            progressPercent = resolveDeadlineProgressPercent(notifyAtMs)
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
            shortText = context.getString(R.string.ba_debug_action_calendar_pool_change_notification),
            deadlineAtMs = null,
            progressPercent = 100
        )
    }

    private fun notificationsGranted(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun sendLiveUpdate(
        context: Context,
        notificationId: Int,
        title: String,
        content: String,
        shortText: String,
        deadlineAtMs: Long?,
        progressPercent: Int,
    ): Boolean {
        if (!notificationsGranted(context)) return false
        McpNotificationHelper.ensureChannel(context)
        val helper = NotificationHelper(context)
        val openPendingIntent = openBaPendingIntent(context, notificationId)
        val focusOpenPendingIntent = focusOpenBaPendingIntent(context, notificationId)
        val acknowledgePendingIntent = acknowledgePendingIntent(context, notificationId)
        val payload = McpNotificationPayload(
            serverName = McpNotificationPayload.BA_CALENDAR_POOL_SERVER_NAME,
            running = true,
            port = progressPercent.coerceIn(0, 100),
            path = content,
            clients = 1,
            ongoing = true,
            onlyAlertOnce = false,
            openPendingIntent = openPendingIntent,
            stopPendingIntent = acknowledgePendingIntent,
            focusOpenPendingIntent = focusOpenPendingIntent,
            secondaryActionLabel = context.getString(R.string.common_acknowledge),
            overrideTitle = title,
            overrideContent = content,
            overrideOnlineText = shortText,
            overrideShortText = shortText,
            overrideProgressPercent = progressPercent.coerceIn(0, 100),
            deadlineAtMs = deadlineAtMs
        )
        val buildResult = SessionNotifierImpl(helper).build(payload)
        McpNotificationHelper.dispatchNotification(
            context = context,
            notificationId = notificationId,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
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

    private fun resolveDeadlineProgressPercent(deadlineAtMs: Long): Int {
        val nowMs = System.currentTimeMillis()
        val leadMs = BASettingsStore.loadCalendarPoolNotifyLeadHours()
            .coerceAtLeast(1) * 60L * 60L * 1000L
        val windowStartMs = deadlineAtMs - leadMs
        return (((nowMs - windowStartMs).coerceAtLeast(0L).toFloat() / leadMs.toFloat()) * 100f)
            .toInt()
            .coerceIn(1, 99)
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

    private fun focusOpenBaPendingIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_BA)
        }
        return PendingIntent.getActivity(
            context,
            522_100 + notificationId,
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
