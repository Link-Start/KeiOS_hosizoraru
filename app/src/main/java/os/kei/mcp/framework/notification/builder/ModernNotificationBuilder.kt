package os.kei.mcp.framework.notification.builder

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import os.kei.R
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
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .applyProgressStyle(spec)
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
            }
            .build()
    }

    private fun NotificationCompat.Builder.applyProgressStyle(
        spec: ModernNotificationSpec
    ): NotificationCompat.Builder {
        return if (spec.showProgress) {
            setStyle(buildProgressStyle(spec))
                .setProgress(100, spec.progressPercent.coerceIn(0, 100), false)
        } else {
            setStyle(null)
                .setProgress(0, 0, false)
        }
    }

    private fun NotificationCompat.Builder.applyDeadline(deadlineAtMs: Long?): NotificationCompat.Builder {
        if (deadlineAtMs == null) return this
        return setWhen(deadlineAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
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
