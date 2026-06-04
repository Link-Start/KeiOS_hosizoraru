package os.kei.mcp.domain.notification

import android.app.Notification
import os.kei.mcp.notification.McpNotificationPayload
import os.kei.mcp.framework.notification.builder.NotificationRenderStyle

interface SessionNotifier {
    data class NotificationBuildResult(
        val notification: Notification,
        val style: NotificationRenderStyle,
        val useXiaomiMagic: Boolean
    )

    fun build(payload: McpNotificationPayload): NotificationBuildResult
}

