package os.kei.ui.page.main.ba

import android.content.Context
import android.content.pm.PackageManager
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.notification.McpNotificationPayload

internal object BaApNotificationDispatcher {
    fun send(
        context: Context,
        currentDisplay: Int,
        limitDisplay: Int,
        thresholdDisplay: Int,
        notificationId: Int = McpNotificationHelper.BA_AP_NOTIFICATION_ID,
    ): Boolean {
        val notificationsGranted =
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (!notificationsGranted) return false

        return runCatching {
            McpNotificationHelper.notifyStandaloneEvent(
                context = context,
                notificationId = notificationId,
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = currentDisplay,
                path = thresholdDisplay.toString(),
                clients = limitDisplay
            )
        }.isSuccess
    }

    fun refreshIfActive(
        context: Context,
        currentDisplay: Int,
        limitDisplay: Int,
        thresholdDisplay: Int,
        notificationId: Int = McpNotificationHelper.BA_AP_NOTIFICATION_ID,
    ): Boolean {
        val notificationsGranted =
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (!notificationsGranted) return false

        return runCatching {
            McpNotificationHelper.refreshStandaloneEventIfActive(
                context = context,
                notificationId = notificationId,
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = currentDisplay,
                path = thresholdDisplay.toString(),
                clients = limitDisplay,
                ongoing = true,
                onlyAlertOnce = true
            )
        }.getOrDefault(false)
    }
}
