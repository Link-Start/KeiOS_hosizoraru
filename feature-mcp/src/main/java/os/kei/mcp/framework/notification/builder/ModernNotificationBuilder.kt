package os.kei.mcp.framework.notification.builder

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import os.kei.feature.mcp.R
import os.kei.mcp.notification.McpNotificationPayload

class ModernNotificationBuilder(
    private val context: Context
) : SessionNotificationBuilder {
    private val baseNotificationBuilder by lazy {
        NotificationCompat.Builder(context, os.kei.mcp.notification.McpNotificationHelper.LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kei_logo_live_update)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
    }

    override fun build(payload: NotificationPayload): Notification {
        val state = payload.state
        val spec = ModernNotificationSpecResolver.resolve(
            state = state,
            preferOemLiveIconLayout = payload.environment.preferOemLiveIconLayout
        )
        val isDismissibleCalendarPoolUpdate =
            spec.kind == ModernNotificationKind.BA_CALENDAR_POOL &&
                    !spec.showProgressStyle &&
                    !spec.ongoing
        return baseNotificationBuilder
            .clearActions()
            // Prevent state leakage between updates.
            .setContentText(null)
            .setOnlyAlertOnce(state.onlyAlertOnce)
            .setSilent(true)
            .setOngoing(spec.ongoing)
            .setRequestPromotedOngoing(spec.requestPromotedOngoing)
            .setSmallIcon(spec.iconResId)
            .setLargeIcon(
                payload.semanticIconBitmap
                    ?: NotificationLargeIconFactory.create(context, spec.expandedIconResId)
            )
            .setContentTitle(state.title(context))
            .setContentText(state.content(context).ifBlank { " " })
            .setContentIntent(state.openPendingIntent)
            .setCategory(spec.category)
            .setAutoCancel(isDismissibleCalendarPoolUpdate)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(buildStyle(spec, state))
            .applyDeadline(state.deadlineAtMs)
            .also { builder ->
                if (state.running) {
                    resolveShortCriticalText(spec, state)?.let(builder::setShortCriticalText)
                }
                val showSecondaryAction = state.stopPendingIntent != state.openPendingIntent &&
                        (state.running || state.showSecondaryActionWhenStopped)
                builder.addAction(0, state.primaryActionTitle(context), state.openPendingIntent)
                if (showSecondaryAction) {
                    builder.addAction(0, state.stopActionTitle(context), state.stopPendingIntent)
                }
                if (isDismissibleCalendarPoolUpdate && showSecondaryAction) {
                    builder.setDeleteIntent(state.stopPendingIntent)
                }
            }
            .build()
    }

    private fun NotificationCompat.Builder.applyDeadline(deadlineAtMs: Long?): NotificationCompat.Builder {
        if (deadlineAtMs == null) {
            return setShowWhen(true)
                .setUsesChronometer(false)
                .setChronometerCountDown(false)
        }
        return setWhen(deadlineAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
    }

    private fun buildStyle(
        spec: ModernNotificationSpec,
        state: McpNotificationPayload
    ): NotificationCompat.Style {
        if (!spec.showProgressStyle) {
            return NotificationCompat.BigTextStyle()
                .bigText(state.content(context).ifBlank { " " })
        }
        return buildProgressStyle(spec)
    }

    private fun buildProgressStyle(spec: ModernNotificationSpec): NotificationCompat.ProgressStyle {
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(100)
                        .setColor(spec.progressColor)
                )
            )
            .setStyledByProgress(true)
            .setProgress(spec.progressPercent)
            .setProgressTrackerIcon(
                spec.trackerIconResId?.let { resId ->
                    IconCompat.createWithResource(context, resId)
                }
            )
    }

    private fun resolveShortCriticalText(
        spec: ModernNotificationSpec,
        state: McpNotificationPayload
    ): String? {
        return when (spec.shortCriticalMode) {
            ModernShortCriticalMode.NONE -> null
            ModernShortCriticalMode.SHORT_TEXT -> state.shortText
            ModernShortCriticalMode.ONLINE_TEXT -> state.onlineText(context)
        }
    }
}
