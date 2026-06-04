package os.kei.mcp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import os.kei.feature.mcp.R

internal object McpNotificationChannels {
    private const val LEGACY_CHANNEL_ID = "mcp_keepalive_channel"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            val legacy = manager.getNotificationChannel(LEGACY_CHANNEL_ID)
            if (legacy != null && legacy.importance < NotificationManager.IMPORTANCE_HIGH) {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            }
        }
        if (manager.getNotificationChannel(McpNotificationHelper.CHANNEL_ID) == null) {
            val keepalive = NotificationChannel(
                McpNotificationHelper.CHANNEL_ID,
                context.getString(R.string.mcp_keepalive_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.mcp_keepalive_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(keepalive)
        }
        if (manager.getNotificationChannel(McpNotificationHelper.FOREGROUND_SERVICE_CHANNEL_ID) == null) {
            val foregroundShell = NotificationChannel(
                McpNotificationHelper.FOREGROUND_SERVICE_CHANNEL_ID,
                context.getString(R.string.mcp_keepalive_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.mcp_keepalive_service_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(foregroundShell)
        }
        if (manager.getNotificationChannel(McpNotificationHelper.LIVE_CHANNEL_ID) == null) {
            val liveUpdate = NotificationChannel(
                McpNotificationHelper.LIVE_CHANNEL_ID,
                context.getString(R.string.mcp_live_update_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.mcp_live_update_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(liveUpdate)
        }
    }
}
