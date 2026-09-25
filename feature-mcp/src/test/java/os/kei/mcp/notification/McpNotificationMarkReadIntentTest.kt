package os.kei.mcp.notification

import android.app.PendingIntent
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import os.kei.core.notification.live.LiveNotificationPayload
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
)
class McpNotificationMarkReadIntentTest {
    @Test
    fun `mark read and dismiss production PendingIntents have distinct identities`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationId = 243_220
        val markRead =
            McpNotificationInteractionIntents.markReadPendingIntent(
                context = context,
                notificationId = notificationId,
                serverName = LiveNotificationPayload.BA_AP_SERVER_NAME,
                targetBaAccountId = "cn-main",
            )
        val dismiss =
            McpNotificationInteractionIntents.dismissPendingIntent(
                context = context,
                notificationId = notificationId,
                serverName = LiveNotificationPayload.BA_AP_SERVER_NAME,
                targetBaAccountId = "cn-main",
            )

        assertNotEquals(markRead, dismiss)
        assertEquals(210_200 + notificationId, shadowOf(markRead).requestCode)
        assertEquals(310_200 + notificationId, shadowOf(dismiss).requestCode)
        assertEquals(McpNotificationMarkReadContract.ACTION, shadowOf(markRead).savedIntent.action)
        assertEquals(McpNotificationDismissContract.ACTION, shadowOf(dismiss).savedIntent.action)
    }

    @Test
    fun `ordinary AP production PendingIntent updates current account metadata`() {
        assertProductionPendingIntentMetadataUpdate(
            notificationId = 243_220,
            serverName = LiveNotificationPayload.BA_AP_SERVER_NAME,
        )
    }

    @Test
    fun `cafe AP production PendingIntent updates current account metadata`() {
        assertProductionPendingIntentMetadataUpdate(
            notificationId = 243_221,
            serverName = LiveNotificationPayload.BA_CAFE_AP_SERVER_NAME,
        )
    }

    @Test
    fun `production PendingIntent upgrades old ID only action extras`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationId = 243_222
        val requestCode = 210_200 + notificationId
        val legacyIntent =
            Intent().apply {
                setClassName(
                    context.packageName,
                    McpNotificationActionContract.MI_FOCUS_ACTION_RECEIVER_CLASS_NAME,
                )
                action = McpNotificationMarkReadContract.ACTION
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(McpNotificationMarkReadContract.EXTRA_NOTIFICATION_ID, notificationId)
            }
        val legacy =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                legacyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val updated =
            McpNotificationInteractionIntents.markReadPendingIntent(
                context = context,
                notificationId = notificationId,
                serverName = LiveNotificationPayload.BA_AP_SERVER_NAME,
                targetBaAccountId = "cn-main",
            )
        val shadow = shadowOf(updated)
        val currentIntent = shadow.savedIntent

        assertEquals(legacy, updated)
        assertEquals(requestCode, shadow.requestCode)
        assertEquals(
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            shadow.flags,
        )
        assertEquals(
            LiveNotificationPayload.BA_AP_SERVER_NAME,
            currentIntent.getStringExtra(McpNotificationMarkReadContract.EXTRA_SERVER_NAME),
        )
        assertEquals(
            "cn-main",
            currentIntent.getStringExtra(McpNotificationMarkReadContract.EXTRA_TARGET_BA_ACCOUNT_ID),
        )
    }

    @Test
    fun `dismiss PendingIntent updates current account metadata`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationId = 243_223
        val first =
            McpNotificationInteractionIntents.dismissPendingIntent(
                context = context,
                notificationId = notificationId,
                serverName = LiveNotificationPayload.BA_CAFE_AP_SERVER_NAME,
                targetBaAccountId = "cn-old",
            )
        val updated =
            McpNotificationInteractionIntents.dismissPendingIntent(
                context = context,
                notificationId = notificationId,
                serverName = LiveNotificationPayload.BA_CAFE_AP_SERVER_NAME,
                targetBaAccountId = "cn-new",
            )
        val shadow = shadowOf(updated)
        val currentIntent = shadow.savedIntent

        assertEquals(first, updated)
        assertEquals(310_200 + notificationId, shadow.requestCode)
        assertEquals(
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            shadow.flags,
        )
        assertEquals(McpNotificationDismissContract.ACTION, currentIntent.action)
        assertEquals(
            notificationId,
            currentIntent.getIntExtra(McpNotificationDismissContract.EXTRA_NOTIFICATION_ID, -1),
        )
        assertEquals(
            LiveNotificationPayload.BA_CAFE_AP_SERVER_NAME,
            currentIntent.getStringExtra(McpNotificationDismissContract.EXTRA_SERVER_NAME),
        )
        assertEquals(
            "cn-new",
            currentIntent.getStringExtra(McpNotificationDismissContract.EXTRA_TARGET_BA_ACCOUNT_ID),
        )
    }

    private fun assertProductionPendingIntentMetadataUpdate(
        notificationId: Int,
        serverName: String,
    ) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val first =
            McpNotificationInteractionIntents.markReadPendingIntent(
                context = context,
                notificationId = notificationId,
                serverName = serverName,
                targetBaAccountId = "cn-old",
            )
        val updated =
            McpNotificationInteractionIntents.markReadPendingIntent(
                context = context,
                notificationId = notificationId,
                serverName = serverName,
                targetBaAccountId = "cn-new",
            )
        val shadow = shadowOf(updated)
        val currentIntent = shadow.savedIntent

        assertEquals(first, updated)
        assertEquals(210_200 + notificationId, shadow.requestCode)
        assertEquals(
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            shadow.flags,
        )
        assertEquals(
            ComponentName(
                context.packageName,
                McpNotificationActionContract.MI_FOCUS_ACTION_RECEIVER_CLASS_NAME,
            ),
            currentIntent.component,
        )
        assertEquals(McpNotificationMarkReadContract.ACTION, currentIntent.action)
        assertTrue(currentIntent.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0)
        assertEquals(
            notificationId,
            currentIntent.getIntExtra(McpNotificationMarkReadContract.EXTRA_NOTIFICATION_ID, -1),
        )
        assertEquals(
            serverName,
            currentIntent.getStringExtra(McpNotificationMarkReadContract.EXTRA_SERVER_NAME),
        )
        assertEquals(
            "cn-new",
            currentIntent.getStringExtra(McpNotificationMarkReadContract.EXTRA_TARGET_BA_ACCOUNT_ID),
        )
    }
}
