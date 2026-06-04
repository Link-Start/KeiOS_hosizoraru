package os.kei.mcp.framework.notification.builder

import android.app.Notification

interface SessionNotificationBuilder {
    fun build(payload: NotificationPayload): Notification
}
