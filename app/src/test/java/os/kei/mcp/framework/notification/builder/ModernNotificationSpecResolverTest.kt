package os.kei.mcp.framework.notification.builder

import android.app.PendingIntent
import org.junit.Test
import os.kei.R
import os.kei.mcp.notification.McpNotificationPayload
import sun.misc.Unsafe
import kotlin.test.assertEquals

class ModernNotificationSpecResolverTest {
    @Test
    fun `default running session uses capped client progress`() {
        val spec = ModernNotificationSpecResolver.resolve(
            createState(
                serverName = "Local MCP",
                running = true,
                port = 8080,
                clients = 3,
                ongoing = true
            )
        )

        assertEquals(ModernNotificationKind.DEFAULT, spec.kind)
        assertEquals(72, spec.progressPercent)
        assertEquals(ModernShortCriticalMode.SHORT_TEXT, spec.shortCriticalMode)
        assertEquals(true, spec.ongoing)
        assertEquals(true, spec.requestPromotedOngoing)
    }

    @Test
    fun `blue archive ap uses ratio progress`() {
        val spec = ModernNotificationSpecResolver.resolve(
            createState(
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = 4,
                clients = 8,
                ongoing = false
            )
        )

        assertEquals(ModernNotificationKind.BA_AP, spec.kind)
        assertEquals(50, spec.progressPercent)
        assertEquals(ModernShortCriticalMode.SHORT_TEXT, spec.shortCriticalMode)
        assertEquals(true, spec.ongoing)
    }

    @Test
    fun `cafe visit keeps full progress and online text`() {
        val spec = ModernNotificationSpecResolver.resolve(
            createState(
                serverName = McpNotificationPayload.BA_CAFE_VISIT_SERVER_NAME,
                running = true,
                port = 0,
                clients = 0,
                ongoing = false
            )
        )

        assertEquals(ModernNotificationKind.BA_CAFE_VISIT, spec.kind)
        assertEquals(100, spec.progressPercent)
        assertEquals(ModernShortCriticalMode.ONLINE_TEXT, spec.shortCriticalMode)
        assertEquals(true, spec.requestPromotedOngoing)
    }

    @Test
    fun `stopped session clears live update emphasis`() {
        val spec = ModernNotificationSpecResolver.resolve(
            createState(
                serverName = "Local MCP",
                running = false,
                port = 8080,
                clients = 5,
                ongoing = false
            )
        )

        assertEquals(0, spec.progressPercent)
        assertEquals(ModernShortCriticalMode.NONE, spec.shortCriticalMode)
        assertEquals(false, spec.ongoing)
        assertEquals(false, spec.requestPromotedOngoing)
    }

    @Test
    fun `arena refresh uses semantic compact icon for oem live layout`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = McpNotificationPayload.BA_ARENA_REFRESH_SERVER_NAME,
                running = true,
                port = 0,
                clients = 0,
                ongoing = true
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(R.drawable.ic_ba_arena_coin_island, spec.iconResId)
        assertEquals(R.drawable.ic_ba_arena_coin_live_update, spec.expandedIconResId)
        assertEquals(R.drawable.ic_ba_arena_coin_live_update, spec.trackerIconResId)
    }

    @Test
    fun `cafe visit uses semantic compact icon for oem live layout`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = McpNotificationPayload.BA_CAFE_VISIT_SERVER_NAME,
                running = true,
                port = 0,
                clients = 0,
                ongoing = true
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(R.drawable.ic_ba_tea_party_island, spec.iconResId)
        assertEquals(R.drawable.ic_ba_tea_party_live_update, spec.expandedIconResId)
        assertEquals(R.drawable.ic_ba_tea_party_live_update, spec.trackerIconResId)
    }

    @Test
    fun `ap uses semantic compact icon for oem live layout`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = 154,
                clients = 240,
                ongoing = true
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(R.drawable.ic_ba_ap_island_notification, spec.iconResId)
        assertEquals(R.drawable.ic_ba_ap_live_update, spec.expandedIconResId)
        assertEquals(R.drawable.ic_ba_ap_live_update, spec.trackerIconResId)
    }

    @Test
    fun `arena refresh keeps semantic status icon for standard live layout`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = McpNotificationPayload.BA_ARENA_REFRESH_SERVER_NAME,
                running = true,
                port = 0,
                clients = 0,
                ongoing = true
            ),
            preferOemLiveIconLayout = false
        )

        assertEquals(R.drawable.ic_ba_arena_coin_island, spec.iconResId)
        assertEquals(R.drawable.ic_ba_arena_coin_live_update, spec.expandedIconResId)
        assertEquals(R.drawable.ic_ba_arena_coin_live_update, spec.trackerIconResId)
    }

    @Test
    fun `ap keeps semantic status icon for standard live layout`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = McpNotificationPayload.BA_AP_SERVER_NAME,
                running = true,
                port = 36,
                clients = 240,
                ongoing = true
            ),
            preferOemLiveIconLayout = false
        )

        assertEquals(R.drawable.ic_ba_ap_island_notification, spec.iconResId)
        assertEquals(R.drawable.ic_ba_ap_live_update, spec.expandedIconResId)
        assertEquals(R.drawable.ic_ba_ap_live_update, spec.trackerIconResId)
    }

    @Test
    fun `calendar pool uses calendar semantic icons and override progress`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = McpNotificationPayload.BA_CALENDAR_POOL_SERVER_NAME,
                running = true,
                port = 0,
                clients = 1,
                ongoing = true,
                overrideProgressPercent = 67
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(ModernNotificationKind.BA_CALENDAR_POOL, spec.kind)
        assertEquals(67, spec.progressPercent)
        assertEquals(R.drawable.ic_ba_calendar_live_update, spec.iconResId)
        assertEquals(R.drawable.ic_ba_calendar_live_update, spec.expandedIconResId)
        assertEquals(R.drawable.ic_ba_calendar_live_update, spec.trackerIconResId)
        assertEquals(ModernShortCriticalMode.SHORT_TEXT, spec.shortCriticalMode)
    }

    @Test
    fun `github share import uses github semantic icon and override progress`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = McpNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
                running = true,
                port = 72,
                clients = 1,
                ongoing = true,
                overrideProgressPercent = 72
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(ModernNotificationKind.GITHUB_SHARE_IMPORT, spec.kind)
        assertEquals(72, spec.progressPercent)
        assertEquals(R.drawable.ic_github_invertocat_island_blue, spec.iconResId)
        assertEquals(R.drawable.ic_github_invertocat_island_blue, spec.expandedIconResId)
        assertEquals(R.drawable.ic_github_invertocat_island_blue, spec.trackerIconResId)
        assertEquals(ModernShortCriticalMode.SHORT_TEXT, spec.shortCriticalMode)
        assertEquals(true, spec.requestPromotedOngoing)
    }

    @Test
    fun `default notification keeps standard app status icon`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = "KeiOS MCP",
                running = true,
                port = 0,
                clients = 0,
                ongoing = true
            ),
            preferOemLiveIconLayout = false
        )

        assertEquals(R.drawable.ic_kei_notification_small, spec.iconResId)
        assertEquals(null, spec.expandedIconResId)
        assertEquals(null, spec.trackerIconResId)
    }

    @Test
    fun `default notification keeps oem app icon without expanded icon`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = "KeiOS MCP",
                running = true,
                port = 0,
                clients = 0,
                ongoing = true
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(R.drawable.ic_kei_logo_live_update, spec.iconResId)
        assertEquals(null, spec.expandedIconResId)
        assertEquals(null, spec.trackerIconResId)
    }

    private fun createState(
        serverName: String,
        running: Boolean,
        port: Int,
        clients: Int,
        ongoing: Boolean,
        overrideProgressPercent: Int? = null
    ): McpNotificationPayload {
        val pendingIntent = createFakePendingIntent()
        return McpNotificationPayload(
            serverName = serverName,
            running = running,
            port = port,
            path = "demo",
            clients = clients,
            ongoing = ongoing,
            onlyAlertOnce = true,
            openPendingIntent = pendingIntent,
            stopPendingIntent = pendingIntent,
            overrideProgressPercent = overrideProgressPercent
        )
    }

    private fun createFakePendingIntent(): PendingIntent {
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = unsafeField.get(null) as Unsafe
        return unsafe.allocateInstance(PendingIntent::class.java) as PendingIntent
    }
}
