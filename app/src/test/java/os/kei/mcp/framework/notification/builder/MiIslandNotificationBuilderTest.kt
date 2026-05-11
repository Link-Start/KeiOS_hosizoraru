package os.kei.mcp.framework.notification.builder

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.graphics.toColorInt
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import os.kei.MainActivity
import os.kei.R
import os.kei.mcp.notification.McpNotificationPayload
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = MiIslandNotificationBuilderTestApp::class,
    sdk = [35]
)
class MiIslandNotificationBuilderTest {
    @Test
    fun `focus open action keeps plain activity pending intent`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 501,
            action = "os.kei.test.OPEN_NOTIFICATION"
        )
        val focusOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 502,
            action = "os.kei.test.OPEN_FOCUS"
        )
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            503,
            Intent("os.kei.test.STOP_MCP").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = "KeiOS MCP",
                running = false,
                port = 8080,
                path = "/mcp",
                clients = 0,
                ongoing = false,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = stopPendingIntent,
                focusOpenPendingIntent = focusOpenPendingIntent
            ),
            settings = UserSettings(miIslandOuterGlow = true),
            environment = EnvironmentContext(
                channelId = "test_mi_island_channel",
                isHyperOS = true
            )
        )

        val notification = MiIslandNotificationBuilder(context).build(payload)
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals(notificationOpenPendingIntent, notification.contentIntent)
        assertEquals(focusOpenPendingIntent, focusOpenAction.actionIntent)
        assertEquals(stopPendingIntent, focusStopAction.actionIntent)
        assertTrue(focusParam.contains("mcp_action_open"))
        assertTrue(focusParam.contains("mcp_action_stop"))
    }

    @Test
    fun `ba ap progress island title uses current ap value`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 601,
            action = "os.kei.test.OPEN_BA_AP"
        )
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            602,
            Intent("os.kei.test.MARK_BA_AP_READ").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = 128,
                path = "120",
                clients = 240,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = stopPendingIntent,
                focusOpenPendingIntent = notificationOpenPendingIntent
            ),
            settings = UserSettings(miIslandOuterGlow = true),
            environment = EnvironmentContext(
                channelId = "test_mi_island_channel",
                isHyperOS = true
            )
        )

        val notification = MiIslandNotificationBuilder(context).build(payload)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(
            actual = focusParam.contains("\"title\":\"128\""),
            message = "AP progress island title should show current AP. focusParam=$focusParam"
        )
        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"progress\":53"))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#4DA3FF\""))
        assertFalse(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
    }

    @Test
    fun `calendar pool island uses countdown digit template and acknowledge action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 701,
            action = "os.kei.test.OPEN_BA_CALENDAR_POOL"
        )
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            702,
            Intent("os.kei.test.MARK_BA_CALENDAR_POOL_READ").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.BA_CALENDAR_POOL_SERVER_NAME,
                running = true,
                port = 72,
                path = "Event starts soon",
                clients = 1,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = stopPendingIntent,
                focusOpenPendingIntent = notificationOpenPendingIntent,
                secondaryActionLabel = "知道了",
                overrideTitle = "活动即将开始",
                overrideContent = "测试活动 将在 05-06 04:00 开始",
                overrideOnlineText = "活动即将开始",
                overrideShortText = "活动即将开始",
                overrideProgressPercent = 72,
                deadlineAtMs = 1778007600000L
            ),
            settings = UserSettings(miIslandOuterGlow = true),
            environment = EnvironmentContext(
                channelId = "test_mi_island_channel",
                isHyperOS = true
            )
        )

        val notification = MiIslandNotificationBuilder(context).build(payload)
        val focusStopAction = notification.focusAction("mcp_action_stop")
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals(stopPendingIntent, focusStopAction.actionIntent)
        assertTrue(focusParam.contains("sameWidthDigitInfo"))
        assertTrue(focusParam.contains("\"timerType\":-1"))
        assertTrue(focusParam.contains("\"timerWhen\":1778007600000"))
        assertTrue(focusParam.contains("\"timerSystemCurrent\""))
        assertTrue(focusParam.contains("mcp_action_stop"))
    }

    @Test
    fun `github share import island uses progress and notification action labels`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 801,
            action = "os.kei.test.OPEN_GITHUB_SHARE_IMPORT"
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            802,
            Intent("os.kei.test.CANCEL_GITHUB_SHARE_IMPORT").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val appIconBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
                running = true,
                port = 72,
                path = "owner/repo · demo.app · exact match · 12 min left",
                clients = 1,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = cancelPendingIntent,
                focusOpenPendingIntent = notificationOpenPendingIntent,
                primaryActionLabel = "Check install",
                secondaryActionLabel = "Cancel linkage",
                showSecondaryActionWhenStopped = true,
                overrideTitle = "Waiting for install",
                overrideContent = "owner/repo · demo.app · exact match · 12 min left",
                overrideOnlineText = "Install",
                overrideShortText = "Install",
                overrideProgressPercent = 72
            ),
            settings = UserSettings(miIslandOuterGlow = true),
            environment = EnvironmentContext(
                channelId = "test_mi_island_channel",
                isHyperOS = true
            ),
            semanticIconBitmap = appIconBitmap
        )

        val notification = MiIslandNotificationBuilder(context).build(payload)
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")
        val focusDisplayIcon = notification.focusPicture("key_logo_display")
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertEquals(notificationOpenPendingIntent, focusOpenAction.actionIntent)
        assertEquals(cancelPendingIntent, focusStopAction.actionIntent)
        assertEquals("Check install", focusOpenAction.title.toString())
        assertEquals("Cancel linkage", focusStopAction.title.toString())
        assertTrue(focusParam.contains("\"title\":\"Install\""))
        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"colorReach\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
        assertTrue(focusParam.contains("\"actionBgColorDark\":\"#FF6B7C\""))
        assertTrue(focusParam.contains("\"title\":\"Install\""))
        assertTrue(focusParam.contains("demo.app"))
        assertFalse(focusParam.contains("\"content\":\"demo.app\""))
        assertTrue(focusParam.contains("\"progress\":72"))
        assertTrue(focusParam.contains("\"picDark\":\"key_logo_display\""))
        val renderedBitmap = Shadows.shadowOf(focusDisplayIcon).bitmap
        assertNotNull(renderedBitmap)
        assertEquals(appIconBitmap.width, renderedBitmap.width)
        assertEquals(appIconBitmap.height, renderedBitmap.height)
    }

    @Test
    fun `github share import direct install action uses light blue secondary button`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 806,
            action = "os.kei.test.OPEN_GITHUB_SHARE_IMPORT_DIRECT_INSTALL"
        )
        val sendInstallPendingIntent = PendingIntent.getBroadcast(
            context,
            807,
            Intent("os.kei.test.SEND_GITHUB_SHARE_IMPORT_INSTALL")
                .setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
                running = true,
                port = 32,
                path = "owner/repo · asset ready",
                clients = 1,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = sendInstallPendingIntent,
                focusOpenPendingIntent = notificationOpenPendingIntent,
                primaryActionLabel = "Open flow",
                secondaryActionLabel = context.getString(
                    R.string.github_share_import_notify_action_send_install
                ),
                showSecondaryActionWhenStopped = true,
                overrideTitle = "Asset ready",
                overrideContent = "owner/repo · asset ready",
                overrideOnlineText = "APK",
                overrideShortText = "APK",
                overrideProgressPercent = 32
            ),
            settings = UserSettings(miIslandOuterGlow = true),
            environment = EnvironmentContext(
                channelId = "test_mi_island_channel",
                isHyperOS = true
            )
        )

        val notification = MiIslandNotificationBuilder(context).build(payload)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(focusParam.contains("\"actionBgColor\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#DBEAFE\""))
        assertTrue(focusParam.contains("\"actionBgColorDark\":\"#1E3A8A\""))
        assertTrue(focusParam.contains("\"actionTitleColor\":\"#1D4ED8\""))
        assertTrue(focusParam.contains("\"actionTitleColorDark\":\"#DBEAFE\""))
        assertFalse(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
    }

    @Test
    fun `github share import success island uses compact completed text`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 811,
            action = "os.kei.test.OPEN_GITHUB_SHARE_IMPORT_SUCCESS"
        )
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            812,
            Intent("os.kei.test.MARK_GITHUB_SHARE_IMPORT_READ").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
                running = true,
                port = 100,
                path = "Demo was added to owner/repo tracking",
                clients = 0,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = markReadPendingIntent,
                focusOpenPendingIntent = notificationOpenPendingIntent,
                primaryActionLabel = "View tracking",
                secondaryActionLabel = "Mark read",
                showSecondaryActionWhenStopped = true,
                overrideTitle = "GitHub tracking added",
                overrideContent = "Demo was added to owner/repo tracking",
                overrideOnlineText = "Tracked",
                overrideShortText = "Tracked",
                overrideProgressPercent = 100
            ),
            settings = UserSettings(miIslandOuterGlow = true),
            environment = EnvironmentContext(
                channelId = "test_mi_island_channel",
                isHyperOS = true
            ),
            miIslandProgressColorOverride = "#22C55E"
        )

        val notification = MiIslandNotificationBuilder(context).build(payload)
        val focusParam = notification.extras.getString("miui.focus.param").orEmpty()

        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"Tracked\""))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertEquals("#22C55E".toColorInt(), notification.color)
        assertTrue(focusParam.contains("\"actionBgColor\":\"#2563EB\""))
        assertFalse(focusParam.contains("\"actionTitle\":\"Mark read\",\"actionBgColor\""))
        assertFalse(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
        assertTrue(focusParam.contains("mcp_action_open"))
        assertTrue(focusParam.contains("mcp_action_stop"))
    }

    private fun buildOpenPendingIntent(
        context: Application,
        requestCode: Int,
        action: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            setAction(action)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_MCP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun Notification.focusAction(key: String): Notification.Action {
        val actions = extras.getBundle("miui.focus.actions")
        assertNotNull(actions, "Focus actions bundle should be present")
        return actions.getActionCompat(key)
    }

    private fun Notification.focusPicture(key: String): Icon {
        val pics = extras.getBundle("miui.focus.pics")
        assertNotNull(pics, "Focus pictures bundle should be present")
        return pics.getPictureCompat(key)
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getActionCompat(key: String): Notification.Action {
        return getParcelable<Notification.Action>(key)
            ?: error("Missing focus action: $key")
    }

    @Suppress("DEPRECATION")
    private fun Bundle.getPictureCompat(key: String): Icon {
        return getParcelable<Icon>(key)
            ?: error("Missing focus picture: $key")
    }
}

class MiIslandNotificationBuilderTestApp : Application()
