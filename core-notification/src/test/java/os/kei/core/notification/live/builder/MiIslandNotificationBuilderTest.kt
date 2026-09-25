package os.kei.core.notification.live.builder

import android.app.Application
import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.core.graphics.toColorInt
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import os.kei.core.notification.R
import os.kei.core.notification.live.LiveNotificationPayload
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35]
)
class MiIslandNotificationBuilderTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun build(payload: NotificationPayload): Notification =
        MiIslandNotificationBuilder(context).build(payload)

    @Test
    fun `running mcp service uses fixed client count summary`() {
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = "My MCP",
                    running = true,
                    port = 8080,
                    path = "/mcp",
                    clients = 3,
                    ongoing = true,
                    onlyAlertOnce = true,
                    openPendingIntent = testPendingIntent(context, 491, "os.kei.test.OPEN_RUNNING_MCP"),
                    stopPendingIntent =
                        testPendingIntent(context, 492, "os.kei.test.STOP_RUNNING_MCP", broadcast = true),
                )
            )
        )
        val focusJson = notification.focusJson()
        val bigIsland =
            focusJson.getJSONObject("param_island").getJSONObject("bigIslandArea")
        val smallIsland =
            focusJson.getJSONObject("param_island").getJSONObject("smallIslandArea")
        val baseInfo = focusJson.getJSONObject("baseInfo")

        assertEquals("3", bigIsland.getJSONObject("fixedWidthDigitInfo").getString("digit"))
        assertEquals(
            context.getString(R.string.mcp_clients_label),
            bigIsland.getJSONObject("fixedWidthDigitInfo").getString("content"),
        )
        assertEquals(6, smallIsland.getJSONObject("imageTextInfoRight").getInt("type"))
        assertEquals(
            "3",
            smallIsland.getJSONObject("imageTextInfoRight")
                .getJSONObject("textInfo")
                .getString("title"),
        )
        assertEquals("My MCP", baseInfo.getString("title"))
        assertEquals(context.getString(R.string.mcp_status_running), baseInfo.getString("specialTitle"))
        assertTrue(baseInfo.getString("content").contains("8080"))
        assertTrue(baseInfo.getString("content").contains("/mcp"))
        assertFalse(focusJson.toString().contains("progressInfo"))
    }

    @Test
    fun `focus open action keeps plain activity pending intent`() {
        val notificationOpenPendingIntent =
            testPendingIntent(context, 501, "os.kei.test.OPEN_NOTIFICATION")
        val focusOpenPendingIntent = testPendingIntent(context, 502, "os.kei.test.OPEN_FOCUS")
        val stopPendingIntent =
            testPendingIntent(context, 503, "os.kei.test.STOP_MCP", broadcast = true)
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = "KeiOS MCP",
                    running = false,
                    port = 8080,
                    path = "/mcp",
                    clients = 0,
                    ongoing = false,
                    onlyAlertOnce = true,
                    openPendingIntent = notificationOpenPendingIntent,
                    stopPendingIntent = stopPendingIntent,
                    focusOpenPendingIntent = focusOpenPendingIntent,
                    notificationId = 38888,
                    miFocusOrderId = "mcp_keepalive"
                )
            )
        )
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")
        val focusParam = notification.focusParam()

        assertEquals(notificationOpenPendingIntent, notification.contentIntent)
        assertEquals(focusOpenPendingIntent, focusOpenAction.actionIntent)
        assertEquals(stopPendingIntent, focusStopAction.actionIntent)
        assertTrue(focusParam.contains("mcp_action_open"))
        assertTrue(focusParam.contains("mcp_action_stop"))
        assertTrue(focusParam.contains("\"clickWithCollapse\":true"))
        assertTrue(focusParam.contains("\"business\":\"keios\""))
        assertTrue(focusParam.contains("\"notifyId\":\"38888\""))
        assertTrue(focusParam.contains("\"orderId\":\"mcp_keepalive\""))
    }

    @Test
    fun `first and finish float follow user settings`() {
        val cases = listOf(
            // label, finish float setting, expected enableFloat token
            Triple("first float off, finish float on", true, "\"enableFloat\":true"),
            Triple("first and finish float off", false, "\"enableFloat\":false"),
        )
        cases.forEachIndexed { index, (label, finishFloat, expectedEnableFloat) ->
            val openPendingIntent =
                testPendingIntent(context, 511 + index * 10, "os.kei.test.OPEN_NOTIFICATION_FLOAT_$index")
            val notification = build(
                testNotificationPayload(
                    LiveNotificationPayload(
                        serverName = "KeiOS MCP",
                        running = false,
                        port = 8080,
                        path = "/mcp",
                        clients = 0,
                        ongoing = false,
                        onlyAlertOnce = true,
                        openPendingIntent = openPendingIntent,
                        stopPendingIntent = testPendingIntent(
                            context,
                            512 + index * 10,
                            "os.kei.test.STOP_MCP_FLOAT_$index",
                            broadcast = true,
                        ),
                        focusOpenPendingIntent = openPendingIntent,
                        notificationId = 38889 + index,
                        miFocusOrderId = "mcp_keepalive_float_$index"
                    ),
                    settings =
                        UserSettings(
                            miIslandOuterGlow = true,
                            miIslandFirstFloat = false,
                            miIslandFinishFloat = finishFloat,
                        ),
                )
            )
            val focusParam = notification.focusParam()

            assertTrue(focusParam.contains("\"islandFirstFloat\":false"), "$label: focusParam=$focusParam")
            assertTrue(focusParam.contains(expectedEnableFloat), "$label: focusParam=$focusParam")
        }
    }

    @Test
    fun `ba ap progress island title uses current ap value`() {
        val openPendingIntent = testPendingIntent(context, 601, "os.kei.test.OPEN_BA_AP")
        val notification = build(
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
                    stopPendingIntent =
                        testPendingIntent(context, 602, "os.kei.test.MARK_BA_AP_READ", broadcast = true),
                    focusOpenPendingIntent = openPendingIntent
                )
            )
        )
        val focusParam = notification.focusParam()

        assertTrue(
            actual = focusParam.contains("\"title\":\"128\""),
            message = "AP progress island title should show current AP. focusParam=$focusParam"
        )
        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"progress\":53"))
        assertTrue(focusParam.contains("\"colorProgress\":\"#4DA3FF\""))
        assertFalse(focusParam.contains("multiProgressInfo"))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#4DA3FF\""))
        assertTrue(focusParam.contains("\"enableFloat\":false"))
        assertFalse(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
    }

    @Test
    fun `ba ap first alert enables island float`() {
        val openPendingIntent = testPendingIntent(context, 611, "os.kei.test.OPEN_BA_AP_FIRST_ALERT")
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.BA_AP_SERVER_NAME,
                    running = true,
                    port = 128,
                    path = "120",
                    clients = 240,
                    ongoing = true,
                    onlyAlertOnce = false,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = testPendingIntent(
                        context,
                        612,
                        "os.kei.test.MARK_BA_AP_FIRST_ALERT_READ",
                        broadcast = true,
                    ),
                    focusOpenPendingIntent = openPendingIntent
                )
            )
        )
        val focusParam = notification.focusParam()

        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertTrue(focusParam.contains("\"islandFirstFloat\":true"))
    }

    @Test
    fun `ba cafe visit event enables island float`() {
        val openPendingIntent = testPendingIntent(context, 621, "os.kei.test.OPEN_BA_CAFE_VISIT")
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.BA_CAFE_VISIT_SERVER_NAME,
                    running = true,
                    port = 0,
                    path = "学生访问刷新",
                    clients = 0,
                    ongoing = false,
                    onlyAlertOnce = false,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = testPendingIntent(
                        context,
                        622,
                        "os.kei.test.MARK_BA_CAFE_VISIT_READ",
                        broadcast = true,
                    ),
                    focusOpenPendingIntent = openPendingIntent
                )
            )
        )
        val focusParam = notification.focusParam()

        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"highlightColor\":\"#4DA3FF\""))
    }

    @Test
    fun `ba arena refresh registers game coin art bitmap and floatable event`() {
        val openPendingIntent = testPendingIntent(context, 631, "os.kei.test.OPEN_BA_ARENA_REFRESH")
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.BA_ARENA_REFRESH_SERVER_NAME,
                    running = true,
                    port = 0,
                    path = "日服 14:00 竞技场已刷新",
                    clients = 0,
                    ongoing = false,
                    onlyAlertOnce = false,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = testPendingIntent(
                        context,
                        632,
                        "os.kei.test.MARK_BA_ARENA_REFRESH_READ",
                        broadcast = true,
                    ),
                    focusOpenPendingIntent = openPendingIntent,
                    notificationId = 38891,
                    miFocusOrderId = "bluearchive_arena_refresh-38891",
                )
            )
        )
        val displayIcon = notification.focusPicture("key_logo_display")
        val tickerIcon = notification.focusPicture("key_logo_light")
        val focusParam = notification.focusParam()

        // The island and ticker slots carry the in-game arena coin art as a bitmap; a
        // resource icon here would regress to the drawn live-update glyph (v1.11.0 parity).
        assertEquals(Icon.TYPE_BITMAP, displayIcon.type)
        assertEquals(Icon.TYPE_BITMAP, tickerIcon.type)
        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertTrue(focusParam.contains("\"islandFirstFloat\":true"))
        assertTrue(focusParam.contains("bluearchive_arena_refresh"))
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"highlightColor\":\"#4DA3FF\""))
    }

    @Test
    fun `calendar pool island uses countdown digit template and acknowledge action`() {
        val openPendingIntent = testPendingIntent(context, 701, "os.kei.test.OPEN_BA_CALENDAR_POOL")
        val stopPendingIntent =
            testPendingIntent(context, 702, "os.kei.test.MARK_BA_CALENDAR_POOL_READ", broadcast = true)
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.BA_CALENDAR_POOL_SERVER_NAME,
                    running = true,
                    port = 72,
                    path = "Event starts soon",
                    clients = 1,
                    ongoing = true,
                    onlyAlertOnce = false,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = stopPendingIntent,
                    focusOpenPendingIntent = openPendingIntent,
                    secondaryActionLabel = "知道了",
                    overrideTitle = "活动即将开始",
                    overrideContent = "测试活动 将在 05-06 04:00 开始",
                    overrideOnlineText = "开始",
                    overrideShortText = "活动",
                    overrideProgressPercent = 72,
                    miFocusTitle = "活动即将开始",
                    miFocusSpecialTitle = "日服",
                    miFocusContent = "测试活动 · 05-06 04:00",
                    deadlineAtMs = 1778007600000L
                )
            )
        )
        val focusStopAction = notification.focusAction("mcp_action_stop")
        val focusParam = notification.focusParam()

        assertEquals(stopPendingIntent, focusStopAction.actionIntent)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(focusParam.contains("sameWidthDigitInfo"))
        assertTrue(focusParam.contains("\"content\":\"活动\""))
        assertTrue(focusParam.contains("\"timerType\":-1"))
        assertTrue(focusParam.contains("\"timerWhen\":1778007600000"))
        assertTrue(focusParam.contains("\"timerSystemCurrent\""))
        assertTrue(focusParam.contains("\"title\":\"活动即将开始\""))
        assertTrue(focusParam.contains("\"specialTitle\":\"日服\""))
        assertTrue(focusParam.contains("测试活动 · 05-06 04:00"))
        assertEquals("活动即将开始", notification.extras.getString(Notification.EXTRA_TITLE))
        assertFalse(focusParam.contains("multiProgressInfo"))
        assertFalse(focusParam.contains("\"colorProgress\""))
        assertTrue(focusParam.contains("mcp_action_stop"))
        assertTrue(focusParam.contains("\"enableFloat\":true"))
    }

    @Test
    fun `calendar pool changed island uses compact terminal text`() {
        val openPendingIntent = testPendingIntent(context, 711, "os.kei.test.OPEN_BA_POOL_CHANGE")
        val stopPendingIntent =
            testPendingIntent(context, 712, "os.kei.test.MARK_BA_POOL_CHANGE_READ", broadcast = true)
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.BA_CALENDAR_POOL_SERVER_NAME,
                    running = true,
                    port = 0,
                    path = "卡池变动 1 项",
                    clients = 1,
                    ongoing = false,
                    onlyAlertOnce = true,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = stopPendingIntent,
                    focusOpenPendingIntent = openPendingIntent,
                    secondaryActionLabel = "知道了",
                    overrideTitle = "日服卡池已更新",
                    overrideContent = "卡池变动 1 项",
                    overrideOnlineText = "卡池",
                    overrideShortText = "更新",
                    overrideProgressPercent = 0,
                    deadlineAtMs = null
                )
            )
        )
        val focusParam = notification.focusParam()

        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(stopPendingIntent, notification.deleteIntent)
        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"更新\""))
        assertFalse(focusParam.contains("\"content\":\"卡池\""))
        assertTrue(focusParam.contains("\"enableFloat\":true"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertFalse(focusParam.contains("multiProgressInfo"))
    }

    @Test
    fun `non ongoing webdav event stays floatable without promoted ongoing request`() {
        val openPendingIntent = testPendingIntent(context, 721, "os.kei.test.OPEN_WEBDAV_EVENT")
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.WEBDAV_SYNC_SERVER_NAME,
                    running = true,
                    port = 100,
                    path = "sync",
                    clients = 1,
                    ongoing = false,
                    onlyAlertOnce = true,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent =
                        testPendingIntent(context, 722, "os.kei.test.MARK_WEBDAV_EVENT_READ", broadcast = true),
                    focusOpenPendingIntent = openPendingIntent,
                    overrideTitle = "WebDAV sync complete",
                    overrideContent = "Synced 1/1",
                    overrideOnlineText = "Complete",
                    overrideShortText = "1/1",
                    overrideProgressPercent = 100,
                    miFocusTitle = "Sync",
                    miFocusSpecialTitle = "Done",
                    miFocusContent = "1/1",
                    notificationId = 38891,
                    miFocusOrderId = "webdav-sync"
                )
            )
        )
        val focusJson = notification.focusJson()

        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notification.extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue(focusJson.getBoolean("enableFloat"))
        assertEquals("Sync", focusJson.getJSONObject("baseInfo").getString("title"))
        assertEquals("Done", focusJson.getJSONObject("baseInfo").getString("specialTitle"))
        assertEquals("1/1", focusJson.getJSONObject("baseInfo").getString("content"))
        assertEquals(
            "WebDAV sync complete",
            notification.extras.getString(Notification.EXTRA_TITLE),
        )
    }

    @Test
    fun `running webdav sync uses one continuous progress bar`() {
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.WEBDAV_SYNC_SERVER_NAME,
                    running = true,
                    port = 40,
                    path = "upload",
                    clients = 5,
                    ongoing = true,
                    onlyAlertOnce = true,
                    openPendingIntent = testPendingIntent(context, 731, "os.kei.test.OPEN_WEBDAV_RUNNING"),
                    stopPendingIntent =
                        testPendingIntent(context, 732, "os.kei.test.MARK_WEBDAV_RUNNING_READ", broadcast = true),
                    overrideTitle = "WebDAV sync",
                    overrideContent = "Uploading 2/5",
                    overrideOnlineText = "Upload",
                    overrideShortText = "2/5",
                    overrideProgressPercent = 40
                )
            )
        )
        val focusParam = notification.focusParam()

        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"colorProgress\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"progress\":40"))
        assertFalse(focusParam.contains("multiProgressInfo"))
    }

    @Test
    fun `github share import island uses progress and notification action labels`() {
        val openPendingIntent = testPendingIntent(context, 801, "os.kei.test.OPEN_GITHUB_SHARE_IMPORT")
        val cancelPendingIntent =
            testPendingIntent(context, 802, "os.kei.test.CANCEL_GITHUB_SHARE_IMPORT", broadcast = true)
        val appIconBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
                    running = true,
                    port = 72,
                    path = "owner/repo · demo.app · exact match · 12 min left",
                    clients = 1,
                    ongoing = true,
                    onlyAlertOnce = true,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = cancelPendingIntent,
                    focusOpenPendingIntent = openPendingIntent,
                    primaryActionLabel = "Check install",
                    secondaryActionLabel = "Cancel linkage",
                    showSecondaryActionWhenStopped = true,
                    overrideTitle = "Waiting for install",
                    overrideContent = "owner/repo · demo.app · exact match · 12 min left",
                    overrideOnlineText = "Install",
                    overrideShortText = "Install",
                    overrideProgressPercent = 72
                ),
                semanticIconBitmap = appIconBitmap
            )
        )
        val focusOpenAction = notification.focusAction("mcp_action_open")
        val focusStopAction = notification.focusAction("mcp_action_stop")
        val focusDisplayIcon = notification.focusPicture("key_logo_display")
        val focusParam = notification.focusParam()

        assertEquals(openPendingIntent, focusOpenAction.actionIntent)
        assertEquals(cancelPendingIntent, focusStopAction.actionIntent)
        assertEquals("Check install", focusOpenAction.title.toString())
        assertEquals("Cancel linkage", focusStopAction.title.toString())
        assertTrue(focusParam.contains("\"title\":\"Install\""))
        assertTrue(focusParam.contains("progressTextInfo"))
        assertTrue(focusParam.contains("combinePicInfo"))
        assertTrue(focusParam.contains("\"colorReach\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"colorProgress\":\"#2563EB\""))
        assertFalse(focusParam.contains("multiProgressInfo"))
        assertTrue(focusParam.contains("\"highlightColor\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"showHighlightColor\":true"))
        assertTrue(focusParam.contains("\"colorContent\":\"#475569\""))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
        assertTrue(focusParam.contains("\"actionBgColorDark\":\"#FF6B7C\""))
        assertTrue(focusParam.contains("\"title\":\"Install\""))
        assertTrue(focusParam.contains("demo.app"))
        assertTrue(focusParam.contains("\"progress\":72"))
        assertTrue(focusParam.contains("\"picDark\":\"key_logo_display\""))
        val renderedBitmap = Shadows.shadowOf(focusDisplayIcon).bitmap
        assertNotNull(renderedBitmap)
        assertEquals(appIconBitmap.width, renderedBitmap.width)
        assertEquals(appIconBitmap.height, renderedBitmap.height)
    }

    @Test
    fun `github share import direct install action uses light blue secondary button`() {
        val openPendingIntent =
            testPendingIntent(context, 806, "os.kei.test.OPEN_GITHUB_SHARE_IMPORT_DIRECT_INSTALL")
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
                    running = true,
                    port = 32,
                    path = "owner/repo · asset ready",
                    clients = 1,
                    ongoing = true,
                    onlyAlertOnce = true,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = testPendingIntent(
                        context,
                        807,
                        "os.kei.test.SEND_GITHUB_SHARE_IMPORT_INSTALL",
                        broadcast = true,
                    ),
                    focusOpenPendingIntent = openPendingIntent,
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
                )
            )
        )
        val focusParam = notification.focusParam()

        assertTrue(focusParam.contains("\"actionBgColor\":\"#2563EB\""))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#DBEAFE\""))
        assertTrue(focusParam.contains("\"actionBgColorDark\":\"#1E3A8A\""))
        assertTrue(focusParam.contains("\"actionTitleColor\":\"#1D4ED8\""))
        assertTrue(focusParam.contains("\"actionTitleColorDark\":\"#DBEAFE\""))
        assertFalse(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
    }

    @Test
    fun `github share import success island uses compact completed text`() {
        val openPendingIntent =
            testPendingIntent(context, 811, "os.kei.test.OPEN_GITHUB_SHARE_IMPORT_SUCCESS")
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
                    running = true,
                    port = 100,
                    path = "Demo was added to owner/repo tracking",
                    clients = 0,
                    ongoing = true,
                    onlyAlertOnce = true,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = testPendingIntent(
                        context,
                        812,
                        "os.kei.test.MARK_GITHUB_SHARE_IMPORT_READ",
                        broadcast = true,
                    ),
                    focusOpenPendingIntent = openPendingIntent,
                    primaryActionLabel = "View tracking",
                    secondaryActionLabel = "Mark read",
                    showSecondaryActionWhenStopped = true,
                    overrideTitle = "GitHub tracking added",
                    overrideContent = "Demo was added to owner/repo tracking",
                    overrideOnlineText = "Tracked",
                    overrideShortText = "Tracked",
                    overrideProgressPercent = 100
                ),
                miIslandProgressColorOverride = "#22C55E"
            )
        )
        val focusParam = notification.focusParam()

        assertTrue(focusParam.contains("imageTextInfoRight"))
        assertTrue(focusParam.contains("\"title\":\"Tracked\""))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertFalse(focusParam.contains("combinePicInfo"))
        assertEquals("#22C55E".toColorInt(), notification.color)
        assertTrue(focusParam.contains("\"highlightColor\":\"#22C55E\""))
        assertTrue(focusParam.contains("\"showHighlightColor\":true"))
        assertTrue(focusParam.contains("\"actionBgColor\":\"#2563EB\""))
        assertFalse(focusParam.contains("\"actionTitle\":\"Mark read\",\"actionBgColor\""))
        assertFalse(focusParam.contains("\"actionBgColor\":\"#E25B6A\""))
        assertTrue(focusParam.contains("mcp_action_open"))
        assertTrue(focusParam.contains("mcp_action_stop"))
    }

    @Test
    fun `ba daily done island shows a terminal short word instead of the mcp server summary`() {
        val openPendingIntent = testPendingIntent(context, 741, "os.kei.test.OPEN_BA_DAILY_DONE")
        val detail = "已标记 2 个账号，开启 2 个制造槽"
        val notification = build(
            testNotificationPayload(
                LiveNotificationPayload(
                    serverName = LiveNotificationPayload.BA_DAILY_DONE_SERVER_NAME,
                    // Dispatched as a live event that is already finished: running, never ongoing. This pair
                    // is what used to drop it into the generic MCP shaping.
                    running = true,
                    port = 0,
                    path = detail,
                    clients = 0,
                    ongoing = false,
                    onlyAlertOnce = false,
                    openPendingIntent = openPendingIntent,
                    stopPendingIntent = openPendingIntent,
                    overrideTitle = "日常完成",
                    overrideContent = detail,
                    overrideShortText = "完成",
                    miFocusTitle = "日常完成",
                    miFocusSpecialTitle = "日常",
                    miFocusContent = detail,
                    miFocusOrderId = "ba-daily-done",
                )
            )
        )
        val focusParam = notification.focusParam()
        val focusJson = notification.focusJson()
        val bigIsland = focusJson.getJSONObject("param_island").getJSONObject("bigIslandArea")
        val baseInfo = focusJson.getJSONObject("baseInfo")

        // Terminal short word, and `type = 3` is what the template doc recommends for one.
        val bigText = bigIsland.getJSONObject("imageTextInfoRight")
        assertEquals(3, bigText.getInt("type"))
        assertEquals(
            context.getString(R.string.ba_daily_done_notification_island_text),
            bigText.getJSONObject("textInfo").getString("title"),
        )

        // The regression this test exists for: a finished daily-done run announced itself with the MCP
        // server-status wording, because no discriminator claimed it.
        assertFalse(focusParam.contains(context.getString(R.string.mcp_status_running)))
        assertFalse(focusParam.contains(context.getString(R.string.mcp_clients_label)))

        assertEquals("日常完成", baseInfo.getString("title"))
        assertEquals("日常", baseInfo.getString("specialTitle"))
        assertEquals(detail, baseInfo.getString("content"))

        // Nothing is in flight, so no progress of any kind is drawn and the notification stays
        // dismissible rather than asking to be a promoted ongoing.
        assertFalse(focusParam.contains("progressInfo"))
        assertFalse(focusParam.contains("progressTextInfo"))
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)

        // Completion green, agreeing with the icon's ticks.
        assertEquals("#22C55E".toColorInt(), notification.color)
        assertTrue(focusParam.contains("\"highlightColor\":\"#22C55E\""))
    }
}
