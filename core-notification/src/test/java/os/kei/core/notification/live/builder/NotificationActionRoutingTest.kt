package os.kei.core.notification.live.builder

import android.app.Application
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.core.notification.live.LiveNotificationPayload
import kotlin.test.assertEquals

/**
 * A BA AP notification has three different intents: open, mark read (the secondary action) and
 * dismiss (swipe away). Every builder must keep them apart; `deletePendingIntent` defaults to the
 * stop intent, so a builder that ignored a custom one would quietly mark AP read on every swipe.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class NotificationActionRoutingTest {
    @Test
    fun `every builder routes the secondary action to mark read and a swipe to dismiss`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val openPendingIntent = testPendingIntent(context, 243_221, "os.kei.test.OPEN_BA_AP")
        val markReadPendingIntent =
            testPendingIntent(context, 243_222, "os.kei.test.MARK_BA_AP_READ", broadcast = true)
        val dismissPendingIntent =
            testPendingIntent(context, 243_223, "os.kei.test.DISMISS_BA_AP", broadcast = true)
        val payload =
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

        listOf<Triple<String, SessionNotificationBuilder, (Notification) -> Notification.Action>>(
            Triple("modern", ModernNotificationBuilder(context)) { it.actions[1] },
            Triple("legacy", LegacyNotificationBuilder(context)) { it.actions[1] },
            Triple("super island", MiIslandNotificationBuilder(context)) { it.focusAction("mcp_action_stop") },
        ).forEach { (label, builder, secondaryAction) ->
            val notification = builder.build(payload)

            assertEquals(markReadPendingIntent, secondaryAction(notification).actionIntent, label)
            assertEquals(dismissPendingIntent, notification.deleteIntent, label)
        }
    }
}
