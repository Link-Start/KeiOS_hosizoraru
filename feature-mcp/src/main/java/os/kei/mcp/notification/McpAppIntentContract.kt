package os.kei.mcp.notification

object McpAppIntentContract {
    const val MAIN_ACTIVITY_CLASS_NAME = "os.kei.MainActivity"
    const val EXTRA_TARGET_BOTTOM_PAGE = "os.kei.extra.TARGET_BOTTOM_PAGE"
    const val EXTRA_BA_ACCOUNT_ID = "os.kei.extra.BA_ACCOUNT_ID"
    const val TARGET_BOTTOM_PAGE_MCP = "Mcp"
    const val TARGET_BOTTOM_PAGE_BA = "Ba"
}

internal object McpNotificationActionContract {
    const val NOTIFICATION_ACTION_RECEIVER_CLASS_NAME =
        "os.kei.feature.notification.NotificationActionReceiver"
    const val MI_FOCUS_ACTION_RECEIVER_CLASS_NAME =
        "os.kei.feature.notification.MiFocusNotificationActionReceiver"
    const val ACTION_STOP_MCP_SERVER = "os.kei.notification.action.STOP_MCP_SERVER"
    const val ACTION_MI_FOCUS_MARK_READ = "os.kei.focus.notification.action.MARK_READ"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
}
