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
    fun `github apk install download island uses progress template and stop action labels`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 821,
            action = "os.kei.test.OPEN_GITHUB_APK_INSTALL"
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            822,
            Intent("os.kei.test.CANCEL_GITHUB_APK_INSTALL").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_APK_INSTALL_SERVER_NAME,
                running = true,
                port = 62,
                path = "正在安装 demo.app",
                clients = 1,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = cancelPendingIntent,
                focusOpenPendingIntent = notificationOpenPendingIntent,
                primaryActionLabel = "打开 Sheet",
                secondaryActionLabel = "停止",
                showSecondaryActionWhenStopped = true,
                overrideTitle = "正在下载 APK",
                overrideContent = "正在下载 demo.apk",
                overrideOnlineText = "下载 62%",
                overrideShortText = "下载 62%",
                overrideProgressPercent = 62
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

        assertEquals(notificationOpenPendingIntent, focusOpenAction.actionIntent)
        assertEquals(cancelPendingIntent, focusStopAction.actionIntent)
        assertEquals("打开 Sheet", focusOpenAction.title.toString())
        assertEquals("停止", focusStopAction.title.toString())
        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"progress\":62"))
        assertTrue(focusParam.contains("\"islandFirstFloat\":false"))
        assertTrue(focusParam.contains("\"enableFloat\":false"))
        assertTrue(focusParam.contains("demo.apk"))
    }

    @Test
    fun `github apk install installing island uses status template and stop action labels`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 831,
            action = "os.kei.test.OPEN_GITHUB_APK_INSTALL_STATUS"
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            832,
            Intent("os.kei.test.CANCEL_GITHUB_APK_INSTALL_STATUS").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_APK_INSTALL_SERVER_NAME,
                running = true,
                port = 5,
                path = "正在安装 demo.app",
                clients = 1,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = cancelPendingIntent,
                focusOpenPendingIntent = notificationOpenPendingIntent,
                primaryActionLabel = "打开 Sheet",
                secondaryActionLabel = "停止",
                showSecondaryActionWhenStopped = true,
                overrideTitle = "正在安装",
                overrideContent = "正在安装 demo.app",
                overrideOnlineText = "安装",
                overrideShortText = "安装",
                overrideProgressPercent = null
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

        assertEquals(notificationOpenPendingIntent, focusOpenAction.actionIntent)
        assertEquals(cancelPendingIntent, focusStopAction.actionIntent)
        assertEquals("打开 Sheet", focusOpenAction.title.toString())
        assertEquals("停止", focusStopAction.title.toString())
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertTrue(focusParam.contains("\"enableFloat\":false"))
        assertTrue(focusParam.contains("demo.app"))
        assertEquals(Notification.CATEGORY_STATUS, notification.category)
    }

    @Test
    fun `github apk install checking island uses stable status template and stop action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val notificationOpenPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 843,
            action = "os.kei.test.OPEN_GITHUB_APK_INSTALL_CHECKING"
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            844,
            Intent("os.kei.test.CANCEL_GITHUB_APK_INSTALL_CHECKING").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_APK_INSTALL_SERVER_NAME,
                running = true,
                port = 5,
                path = "正在检查 demo.apk",
                clients = 1,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = notificationOpenPendingIntent,
                stopPendingIntent = cancelPendingIntent,
                focusOpenPendingIntent = notificationOpenPendingIntent,
                primaryActionLabel = "打开 Sheet",
                secondaryActionLabel = "停止",
                showSecondaryActionWhenStopped = true,
                overrideTitle = "正在检查 APK",
                overrideContent = "正在检查 demo.apk",
                overrideOnlineText = "检查",
                overrideShortText = "检查",
                overrideProgressPercent = null
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

        assertEquals(notificationOpenPendingIntent, focusOpenAction.actionIntent)
        assertEquals(cancelPendingIntent, focusStopAction.actionIntent)
        assertEquals("打开 Sheet", focusOpenAction.title.toString())
        assertEquals("停止", focusStopAction.title.toString())
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"检查\""))
        assertTrue(focusParam.contains("\"islandFirstFloat\":false"))
        assertTrue(focusParam.contains("\"enableFloat\":false"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertEquals(Notification.CATEGORY_STATUS, notification.category)
    }

    @Test
    fun `github apk install ready island uses install and cancel actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val installPendingIntent = PendingIntent.getBroadcast(
            context,
            841,
            Intent("os.kei.test.INSTALL_GITHUB_APK_INSTALL_READY").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            842,
            Intent("os.kei.test.CANCEL_GITHUB_APK_INSTALL_READY").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_APK_INSTALL_SERVER_NAME,
                running = true,
                port = 4,
                path = "demo.apk 需要确认后安装",
                clients = 1,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = installPendingIntent,
                stopPendingIntent = cancelPendingIntent,
                focusOpenPendingIntent = installPendingIntent,
                primaryActionLabel = "安装",
                secondaryActionLabel = "取消",
                showSecondaryActionWhenStopped = true,
                overrideTitle = "等待确认安装",
                overrideContent = "demo.apk 需要确认后安装",
                overrideOnlineText = "确认",
                overrideShortText = "确认",
                overrideProgressPercent = null,
                focusAllowFloat = true
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

        assertEquals(installPendingIntent, focusOpenAction.actionIntent)
        assertEquals(cancelPendingIntent, focusStopAction.actionIntent)
        assertEquals("安装", focusOpenAction.title.toString())
        assertEquals("取消", focusStopAction.title.toString())
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"确认\""))
        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertTrue(focusParam.contains("demo.apk 需要确认后安装"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertEquals(Notification.CATEGORY_STATUS, notification.category)
    }

    @Test
    fun `github apk install pending user action island opens system confirmation`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            845,
            Intent("os.kei.test.CONFIRM_GITHUB_APK_INSTALL_PENDING").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            846,
            Intent("os.kei.test.CANCEL_GITHUB_APK_INSTALL_PENDING").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_APK_INSTALL_SERVER_NAME,
                running = true,
                port = 7,
                path = "等待系统确认",
                clients = 1,
                ongoing = true,
                onlyAlertOnce = true,
                openPendingIntent = confirmPendingIntent,
                stopPendingIntent = cancelPendingIntent,
                focusOpenPendingIntent = confirmPendingIntent,
                primaryActionLabel = "打开系统确认",
                secondaryActionLabel = "停止",
                showSecondaryActionWhenStopped = true,
                overrideTitle = "等待系统确认",
                overrideContent = "等待系统确认",
                overrideOnlineText = "确认",
                overrideShortText = "确认",
                overrideProgressPercent = null,
                focusAllowFloat = true
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

        assertEquals(confirmPendingIntent, focusOpenAction.actionIntent)
        assertEquals(cancelPendingIntent, focusStopAction.actionIntent)
        assertEquals("打开系统确认", focusOpenAction.title.toString())
        assertEquals("停止", focusStopAction.title.toString())
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"确认\""))
        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertEquals(Notification.CATEGORY_STATUS, notification.category)
    }

    @Test
    fun `github apk install success island floats completion actions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val openPendingIntent = buildOpenPendingIntent(
            context = context,
            requestCode = 847,
            action = "os.kei.test.OPEN_GITHUB_APK_INSTALL_SUCCESS"
        )
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            848,
            Intent("os.kei.test.MARK_GITHUB_APK_INSTALL_SUCCESS").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_APK_INSTALL_SERVER_NAME,
                running = false,
                port = 0,
                path = "demo.app installed",
                clients = 0,
                ongoing = false,
                onlyAlertOnce = false,
                openPendingIntent = openPendingIntent,
                stopPendingIntent = markReadPendingIntent,
                focusOpenPendingIntent = openPendingIntent,
                primaryActionLabel = "打开 Sheet",
                secondaryActionLabel = "标为已读",
                showSecondaryActionWhenStopped = true,
                overrideTitle = "已安装",
                overrideContent = "demo.app installed",
                overrideOnlineText = "完成",
                overrideShortText = "完成",
                overrideProgressPercent = null,
                focusAllowFloat = true
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

        assertEquals(openPendingIntent, focusOpenAction.actionIntent)
        assertEquals(markReadPendingIntent, focusStopAction.actionIntent)
        assertEquals("打开 Sheet", focusOpenAction.title.toString())
        assertEquals("标为已读", focusStopAction.title.toString())
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"完成\""))
        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("\"progress\":"))
        assertEquals(Notification.CATEGORY_STATUS, notification.category)
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
