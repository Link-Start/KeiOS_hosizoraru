package os.kei.mcp.framework.notification.builder

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import os.kei.MainActivity
import os.kei.mcp.notification.McpNotificationPayload
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(
    application = NotificationSemanticIconBuilderTestApp::class,
    sdk = [35]
)
class NotificationSemanticIconBuilderTest {
    @Test
    fun `modern live update expanded icon prefers semantic app icon`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val appIconBitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        val notification = ModernNotificationBuilder(context).build(
            payload = payload(
                context = context,
                semanticIconBitmap = appIconBitmap
            )
        )

        assertLargeIconBitmap(appIconBitmap, notification.getLargeIcon())
    }

    @Test
    fun `legacy live update expanded icon prefers semantic app icon`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val appIconBitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        val notification = LegacyNotificationBuilder(context).build(
            payload = payload(
                context = context,
                semanticIconBitmap = appIconBitmap
            )
        )

        assertLargeIconBitmap(appIconBitmap, notification.getLargeIcon())
    }

    @Test
    fun `modern ba calendar pool terminal update is dismissible text notification`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val openPendingIntent = openPendingIntent(context, 9601)
        val stopPendingIntent = openPendingIntent(context, 9602)
        val notification = ModernNotificationBuilder(context).build(
            payload = NotificationPayload(
                state = McpNotificationPayload(
                    serverName = McpNotificationPayload.BA_CALENDAR_POOL_SERVER_NAME,
                    running = true,
                    port = 0,
                    path = "活动 1 项 · 卡池 1 项",
                    clients = 1,
                    ongoing = false,
                    onlyAlertOnce = true,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = stopPendingIntent,
                    secondaryActionLabel = "知道了",
                    overrideTitle = "日服活动/卡池已更新",
                    overrideContent = "活动 1 项 · 卡池 1 项",
                    overrideOnlineText = "活动卡池",
                    overrideShortText = "更新",
                    overrideProgressPercent = 0,
                    deadlineAtMs = null
                ),
                settings = UserSettings(miIslandOuterGlow = false),
                environment = EnvironmentContext(
                    channelId = "test_live_update_channel",
                    isHyperOS = false
                )
            )
        )

        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.FLAG_AUTO_CANCEL, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertEquals(stopPendingIntent, notification.deleteIntent)
        assertEquals(NotificationCompat.CATEGORY_STATUS, notification.category)
        assertEquals(
            "androidx.core.app.NotificationCompat\$BigTextStyle",
            notification.extras.getString("androidx.core.app.extra.COMPAT_TEMPLATE")
        )
    }

    private fun payload(
        context: Application,
        semanticIconBitmap: Bitmap
    ) = NotificationPayload(
        state = McpNotificationPayload(
            serverName = McpNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
            running = true,
            port = 72,
            path = "owner/repo · demo.app · exact match · 12 min left",
            clients = 1,
            ongoing = true,
            onlyAlertOnce = true,
            openPendingIntent = openPendingIntent(context, 9501),
            stopPendingIntent = openPendingIntent(context, 9502),
            focusOpenPendingIntent = openPendingIntent(context, 9503),
            overrideTitle = "Waiting for install",
            overrideContent = "owner/repo · demo.app · exact match · 12 min left",
            overrideOnlineText = "Install",
            overrideShortText = "Install",
            overrideProgressPercent = 72
        ),
        settings = UserSettings(miIslandOuterGlow = false),
        environment = EnvironmentContext(
            channelId = "test_live_update_channel",
            isHyperOS = true
        ),
        semanticIconBitmap = semanticIconBitmap
    )

    private fun openPendingIntent(context: Application, requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun assertLargeIconBitmap(
        expected: Bitmap,
        actualIcon: android.graphics.drawable.Icon?
    ) {
        assertNotNull(actualIcon)
        val actualBitmap = Shadows.shadowOf(actualIcon).bitmap
        assertNotNull(actualBitmap)
        assertEquals(expected.width, actualBitmap.width)
        assertEquals(expected.height, actualBitmap.height)
    }
}

class NotificationSemanticIconBuilderTestApp : Application()
