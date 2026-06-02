package os.kei.ui.page.main.ba

import android.content.Context
import android.content.pm.PackageManager
import os.kei.R
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.notification.McpNotificationPayload

internal object BaApNotificationDispatcher {
    fun send(
        context: Context,
        currentDisplay: Int,
        limitDisplay: Int,
        thresholdDisplay: Int,
        notificationId: Int = McpNotificationHelper.BA_AP_NOTIFICATION_ID,
        accountDisplayName: String = "",
    ): Boolean {
        val notificationsGranted =
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (!notificationsGranted) return false

        return runCatching {
            val content = context.getString(
                R.string.mcp_notification_content_ap,
                currentDisplay,
                thresholdDisplay.toString(),
                limitDisplay,
            )
            McpNotificationHelper.notifyStandaloneEvent(
                context = context,
                notificationId = notificationId,
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = currentDisplay,
                path = thresholdDisplay.toString(),
                clients = limitDisplay,
                overrideTitle = context.getString(R.string.ba_ap_notification_title),
                overrideContent = baAccountNotificationContent(
                    context = context,
                    accountDisplayName = accountDisplayName,
                    content = content,
                ),
            )
        }.getOrDefault(false)
    }

    fun refreshIfActive(
        context: Context,
        currentDisplay: Int,
        limitDisplay: Int,
        thresholdDisplay: Int,
        notificationId: Int = McpNotificationHelper.BA_AP_NOTIFICATION_ID,
        accountDisplayName: String = "",
    ): Boolean {
        val notificationsGranted =
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (!notificationsGranted) return false

        return runCatching {
            val content = context.getString(
                R.string.mcp_notification_content_ap,
                currentDisplay,
                thresholdDisplay.toString(),
                limitDisplay,
            )
            McpNotificationHelper.refreshStandaloneEventIfActive(
                context = context,
                notificationId = notificationId,
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = currentDisplay,
                path = thresholdDisplay.toString(),
                clients = limitDisplay,
                ongoing = true,
                onlyAlertOnce = true,
                overrideTitle = context.getString(R.string.ba_ap_notification_title),
                overrideContent = baAccountNotificationContent(
                    context = context,
                    accountDisplayName = accountDisplayName,
                    content = content,
                ),
            )
        }.getOrDefault(false)
    }
}
