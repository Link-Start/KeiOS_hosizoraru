package os.kei.core.notification.live.builder

import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import org.junit.Test
import os.kei.core.notification.R
import os.kei.core.notification.live.LiveNotificationPayload
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
        assertEquals(true, spec.showProgressStyle)
        assertEquals(NotificationCompat.CATEGORY_PROGRESS, spec.category)
    }

    @Test
    fun `blue archive ap uses ratio progress`() {
        val spec = ModernNotificationSpecResolver.resolve(
            createState(
                serverName = LiveNotificationPayload.BA_AP_SERVER_NAME,
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
    fun `cafe visit is a one shot status event`() {
        val spec = ModernNotificationSpecResolver.resolve(
            createState(
                serverName = LiveNotificationPayload.BA_CAFE_VISIT_SERVER_NAME,
                running = true,
                port = 0,
                clients = 0,
                ongoing = false
            )
        )

        assertEquals(ModernNotificationKind.BA_CAFE_VISIT, spec.kind)
        assertEquals(100, spec.progressPercent)
        assertEquals(ModernShortCriticalMode.ONLINE_TEXT, spec.shortCriticalMode)
        assertEquals(false, spec.ongoing)
        assertEquals(false, spec.requestPromotedOngoing)
        assertEquals(false, spec.showProgressStyle)
        assertEquals(NotificationCompat.CATEGORY_STATUS, spec.category)
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
                serverName = LiveNotificationPayload.BA_ARENA_REFRESH_SERVER_NAME,
                running = true,
                port = 0,
                clients = 0,
                ongoing = false
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(R.drawable.ic_ba_arena_coin_island, spec.iconResId)
        assertEquals(R.drawable.ic_ba_arena_coin_live_update, spec.expandedIconResId)
        assertEquals(R.drawable.ic_ba_arena_coin_live_update, spec.trackerIconResId)
        assertEquals(false, spec.ongoing)
        assertEquals(false, spec.requestPromotedOngoing)
        assertEquals(false, spec.showProgressStyle)
        assertEquals(NotificationCompat.CATEGORY_STATUS, spec.category)
    }

    @Test
    fun `ap keeps semantic status icon for standard live layout`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = LiveNotificationPayload.BA_AP_SERVER_NAME,
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
                serverName = LiveNotificationPayload.BA_CALENDAR_POOL_SERVER_NAME,
                running = true,
                port = 0,
                clients = 1,
                ongoing = true,
                overrideProgressPercent = 67,
                deadlineAtMs = 1_778_007_600_000L
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(ModernNotificationKind.BA_CALENDAR_POOL, spec.kind)
        assertEquals(67, spec.progressPercent)
        assertEquals(R.drawable.ic_ba_calendar_live_update, spec.iconResId)
        assertEquals(R.drawable.ic_ba_calendar_live_update, spec.expandedIconResId)
        assertEquals(R.drawable.ic_ba_calendar_live_update, spec.trackerIconResId)
        assertEquals(ModernShortCriticalMode.SHORT_TEXT, spec.shortCriticalMode)
        assertEquals(true, spec.showProgressStyle)
        assertEquals(true, spec.requestPromotedOngoing)
    }

    @Test
    fun `calendar pool terminal update uses status presentation`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = LiveNotificationPayload.BA_CALENDAR_POOL_SERVER_NAME,
                running = true,
                port = 0,
                clients = 1,
                ongoing = false,
                overrideProgressPercent = 0,
                deadlineAtMs = null
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(ModernNotificationKind.BA_CALENDAR_POOL, spec.kind)
        assertEquals(0, spec.progressPercent)
        assertEquals(ModernShortCriticalMode.SHORT_TEXT, spec.shortCriticalMode)
        assertEquals(false, spec.ongoing)
        assertEquals(false, spec.requestPromotedOngoing)
        assertEquals(false, spec.showProgressStyle)
    }

    @Test
    fun `github share import uses github semantic icon and override progress`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = LiveNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
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
        assertEquals(true, spec.showProgressStyle)
        assertEquals(0xFF2563EB.toInt(), spec.progressColor)
    }

    @Test
    fun `github share import falls back to phase progress when override is absent`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = LiveNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
                running = true,
                port = 88,
                clients = 1,
                ongoing = true
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(ModernNotificationKind.GITHUB_SHARE_IMPORT, spec.kind)
        assertEquals(88, spec.progressPercent)
        assertEquals(false, spec.showProgressStyle)
        assertEquals(0xFF2563EB.toInt(), spec.progressColor)
    }

    @Test
    fun `webdav sync uses override progress and accent color`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = LiveNotificationPayload.WEBDAV_SYNC_SERVER_NAME,
                running = true,
                port = 45,
                clients = 6,
                ongoing = true,
                overrideProgressPercent = 45,
                overrideAccentColor = "#F59E0B"
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(ModernNotificationKind.WEBDAV_SYNC, spec.kind)
        assertEquals(45, spec.progressPercent)
        assertEquals(R.drawable.ic_kei_notification_small, spec.iconResId)
        assertEquals(null, spec.expandedIconResId)
        assertEquals(null, spec.trackerIconResId)
        assertEquals(ModernShortCriticalMode.SHORT_TEXT, spec.shortCriticalMode)
        assertEquals(true, spec.showProgressStyle)
        assertEquals(0xFFF59E0B.toInt(), spec.progressColor)
    }

    @Test
    fun `webdav terminal update uses status presentation`() {
        val spec = ModernNotificationSpecResolver.resolve(
            state = createState(
                serverName = LiveNotificationPayload.WEBDAV_SYNC_SERVER_NAME,
                running = false,
                port = 100,
                clients = 6,
                ongoing = false,
                overrideProgressPercent = 100,
                overrideAccentColor = "#22C55E"
            ),
            preferOemLiveIconLayout = true
        )

        assertEquals(ModernNotificationKind.WEBDAV_SYNC, spec.kind)
        assertEquals(0, spec.progressPercent)
        assertEquals(ModernShortCriticalMode.NONE, spec.shortCriticalMode)
        assertEquals(false, spec.ongoing)
        assertEquals(false, spec.showProgressStyle)
        assertEquals(0xFF22C55E.toInt(), spec.progressColor)
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
    fun `ba daily done live update is a dismissible status, not a promoted ongoing progress bar`() {
        val spec =
            ModernNotificationSpecResolver.resolve(
                state =
                    createState(
                        serverName = LiveNotificationPayload.BA_DAILY_DONE_SERVER_NAME,
                        // The dispatcher's real pair: a live event that has already finished.
                        running = true,
                        port = 0,
                        clients = 0,
                        ongoing = false,
                    ),
            )

        // Before daily-done joined the one-shot BA events, `ongoing` was `running || state.ongoing` and
        // `requestPromotedOngoing` tracked it, so a finished run arrived as an un-dismissible
        // ONGOING_EVENT|PROMOTED_ONGOING ProgressStyle notification with a bar at 100%.
        assertEquals(false, spec.ongoing)
        assertEquals(false, spec.requestPromotedOngoing)
        assertEquals(false, spec.showProgressStyle)
        assertEquals(NotificationCompat.CATEGORY_STATUS, spec.category)

        // Semantic icon rather than the app identity icon.
        assertEquals(R.drawable.ic_ba_daily_done_island, spec.iconResId)
        assertEquals(R.drawable.ic_ba_daily_done_live_update, spec.expandedIconResId)

        // A terminal short word comes from the dispatcher's overrideShortText; ONLINE_TEXT would pull the
        // server-status wording in instead.
        assertEquals(ModernShortCriticalMode.SHORT_TEXT, spec.shortCriticalMode)
    }

    private fun createState(
        serverName: String,
        running: Boolean,
        port: Int,
        clients: Int,
        ongoing: Boolean,
        overrideProgressPercent: Int? = null,
        deadlineAtMs: Long? = null,
        overrideAccentColor: String? = null
    ): LiveNotificationPayload {
        val pendingIntent = createFakePendingIntent()
        return LiveNotificationPayload(
            serverName = serverName,
            running = running,
            port = port,
            path = "demo",
            clients = clients,
            ongoing = ongoing,
            onlyAlertOnce = true,
            openPendingIntent = pendingIntent,
            stopPendingIntent = pendingIntent,
            overrideProgressPercent = overrideProgressPercent,
            overrideAccentColor = overrideAccentColor,
            deadlineAtMs = deadlineAtMs
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
