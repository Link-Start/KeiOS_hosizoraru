package os.kei.feature.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.kei.mcp.notification.McpNotificationHelper

class MiFocusNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != ACTION_MARK_READ) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId == Int.MIN_VALUE) return
        McpNotificationHelper.cancelNotification(context, notificationId)
    }

    companion object {
        const val ACTION_MARK_READ = "os.kei.focus.notification.action.MARK_READ"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
