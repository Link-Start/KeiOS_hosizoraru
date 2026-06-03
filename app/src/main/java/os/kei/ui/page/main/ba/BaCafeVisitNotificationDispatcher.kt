package os.kei.ui.page.main.ba

import android.content.Context
import android.content.pm.PackageManager
import os.kei.R
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.notification.McpNotificationPayload
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.baServerLabelRes
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone
import java.util.Calendar

internal object BaCafeVisitNotificationDispatcher {
    fun send(
        context: Context,
        serverIndex: Int,
        slotMs: Long,
        notificationId: Int = McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID,
        accountDisplayName: String = "",
        accountId: BaAccountId? = null,
    ): Boolean {
        val notificationsGranted =
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (!notificationsGranted) return false
        val detailLine = buildVisitDetailLine(
            context = context,
            serverIndex = serverIndex,
            slotMs = slotMs
        )

        return runCatching {
            McpNotificationHelper.notifyStandaloneEvent(
                context = context,
                notificationId = notificationId,
                serverName = McpNotificationPayload.BA_CAFE_VISIT_SERVER_NAME,
                running = true,
                port = 0,
                path = detailLine,
                clients = 0,
                overrideContent = baAccountNotificationContent(
                    context = context,
                    accountDisplayName = accountDisplayName,
                    content = detailLine,
                ),
                targetBaAccountId = accountId?.value,
            )
        }.getOrDefault(false)
    }

    private fun buildVisitDetailLine(
        context: Context,
        serverIndex: Int,
        slotMs: Long,
    ): String {
        val timeZone = serverRefreshTimeZone(serverIndex)
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = slotMs.coerceAtLeast(0L)
        }
        val slotHour = calendar.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
        return context.getString(
            R.string.ba_cafe_visit_notification_content_detail,
            context.getString(baServerLabelRes(serverIndex)),
            slotHour
        )
    }
}
