package os.kei.mcp.notification

import os.kei.mcp.framework.notification.builder.NotificationRenderStyle

internal data class McpNotificationSnapshot(
    val serverName: String,
    val running: Boolean,
    val port: Int,
    val path: String,
    val clients: Int,
    val ongoing: Boolean,
    val onlyAlertOnce: Boolean,
    val style: NotificationRenderStyle,
    val useXiaomiMagic: Boolean
)

internal object McpNotificationSnapshotStore {
    @Volatile
    private var keepAliveSnapshot: McpNotificationSnapshot? = null

    @Volatile
    private var baApSnapshot: McpNotificationSnapshot? = null

    @Volatile
    private var baCafeVisitSnapshot: McpNotificationSnapshot? = null

    @Volatile
    private var baArenaRefreshSnapshot: McpNotificationSnapshot? = null

    fun get(notificationId: Int): McpNotificationSnapshot? {
        return when (notificationId) {
            McpNotificationHelper.KEEPALIVE_NOTIFICATION_ID -> keepAliveSnapshot
            McpNotificationHelper.BA_AP_NOTIFICATION_ID -> baApSnapshot
            McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID -> baCafeVisitSnapshot
            McpNotificationHelper.BA_ARENA_REFRESH_NOTIFICATION_ID -> baArenaRefreshSnapshot
            else -> null
        }
    }

    fun put(notificationId: Int, snapshot: McpNotificationSnapshot) {
        when (notificationId) {
            McpNotificationHelper.KEEPALIVE_NOTIFICATION_ID -> keepAliveSnapshot = snapshot
            McpNotificationHelper.BA_AP_NOTIFICATION_ID -> baApSnapshot = snapshot
            McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID -> baCafeVisitSnapshot = snapshot
            McpNotificationHelper.BA_ARENA_REFRESH_NOTIFICATION_ID -> baArenaRefreshSnapshot =
                snapshot
        }
    }

    fun clear(notificationId: Int) {
        when (notificationId) {
            McpNotificationHelper.KEEPALIVE_NOTIFICATION_ID -> keepAliveSnapshot = null
            McpNotificationHelper.BA_AP_NOTIFICATION_ID -> baApSnapshot = null
            McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID -> baCafeVisitSnapshot = null
            McpNotificationHelper.BA_ARENA_REFRESH_NOTIFICATION_ID -> baArenaRefreshSnapshot = null
        }
    }
}
