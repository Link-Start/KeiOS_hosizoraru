package os.kei.core.notification.live.builder

import android.app.Application
import android.app.PendingIntent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.core.notification.live.LiveNotificationPayload
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NotificationActionRoutingTest {
    @Test
    @Config(sdk = [36])
    fun `modern live update routes secondary action to mark read intent`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val (openPendingIntent, markReadPendingIntent, dismissPendingIntent) = pendingIntents(context)

        val modern =
            ModernNotificationBuilder(context).build(
                payload(
                    context = context,
                    openPendingIntent = openPendingIntent,
                    markReadPendingIntent = markReadPendingIntent,
                    dismissPendingIntent = dismissPendingIntent,
                ),
            )

        assertEquals(markReadPendingIntent, modern.actions[1].actionIntent)
        assertEquals(dismissPendingIntent, modern.deleteIntent)
    }

    @Test
    @Config(sdk = [35])
    fun `legacy live update routes secondary action to mark read intent`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val (openPendingIntent, markReadPendingIntent, dismissPendingIntent) = pendingIntents(context)

        val legacy =
            LegacyNotificationBuilder(context).build(
                payload(
                    context = context,
                    openPendingIntent = openPendingIntent,
                    markReadPendingIntent = markReadPendingIntent,
                    dismissPendingIntent = dismissPendingIntent,
                ),
            )

        assertEquals(markReadPendingIntent, legacy.actions[1].actionIntent)
        assertEquals(dismissPendingIntent, legacy.deleteIntent)
    }

    @Test
    @Config(sdk = [35])
    fun `super island routes stop action to mark read intent`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val (openPendingIntent, markReadPendingIntent, dismissPendingIntent) = pendingIntents(context)

        val island =
            MiIslandNotificationBuilder(context).build(
                payload(
                    context = context,
                    openPendingIntent = openPendingIntent,
                    markReadPendingIntent = markReadPendingIntent,
                    dismissPendingIntent = dismissPendingIntent,
                ),
            )

        assertEquals(markReadPendingIntent, island.focusAction("mcp_action_stop").actionIntent)
        assertEquals(dismissPendingIntent, island.deleteIntent)
    }

    private fun pendingIntents(context: Application): Triple<PendingIntent, PendingIntent, PendingIntent> =
        Triple(
            testPendingIntent(context, 243_221, "os.kei.test.OPEN_BA_AP"),
            testPendingIntent(context, 243_222, "os.kei.test.MARK_BA_AP_READ", broadcast = true),
            testPendingIntent(context, 243_223, "os.kei.test.DISMISS_BA_AP", broadcast = true),
        )

    private fun payload(
        context: Application,
        openPendingIntent: PendingIntent,
        markReadPendingIntent: PendingIntent,
        dismissPendingIntent: PendingIntent,
    ): NotificationPayload =
        testNotificationPayload(
            LiveNotificationPayload(
                serverName = LiveNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = 128,
                path = "120",
                clients = 240,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = openPendingIntent,
                stopPendingIntent = markReadPendingIntent,
                deletePendingIntent = dismissPendingIntent,
                focusOpenPendingIntent = openPendingIntent,
                notificationId = 243_220,
                miFocusOrderId = "ba-ap-routing",
            ),
            channelId = "test_notification_action_routing",
        )
}
