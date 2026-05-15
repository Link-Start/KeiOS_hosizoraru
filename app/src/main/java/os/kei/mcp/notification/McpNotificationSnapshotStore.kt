package os.kei.mcp.notification

import os.kei.mcp.framework.notification.builder.NotificationRenderStyle

internal data class McpNotificationSnapshot(
    val serverName: String,
    val running: Boolean,
    val port: Int,
    val path: String,
    val clients: Int,
    val ongoing: Boolean,
    val onlyAlertOnce: Boolean,
    val style: NotificationRenderStyle,
    val useXiaomiMagic: Boolean
)

internal object McpNotificationSnapshotStore {
    @Volatile
    private var keepAliveSnapshot: McpNotificationSnapshot? = null

    @Volatile
    private var baApSnapshot: McpNotificationSnapshot? = null

    @Volatile
    private var baCafeVisitSnapshot: McpNotificationSnapshot? = null

    @Volatile
    private var baCafeApSnapshot: McpNotificationSnapshot? = null

    @Volatile
    private var baArenaRefreshSnapshot: McpNotificationSnapshot? = null

    fun get(notificationId: Int): McpNotificationSnapshot? {
        return when (notificationId) {
            McpNotificationHelper.KEEPALIVE_NOTIFICATION_ID -> keepAliveSnapshot
            McpNotificationHelper.BA_AP_NOTIFICATION_ID -> baApSnapshot
            McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID -> baCafeVisitSnapshot
            McpNotificationHelper.BA_CAFE_AP_NOTIFICATION_ID -> baCafeApSnapshot
            McpNotificationHelper.BA_ARENA_REFRESH_NOTIFICATION_ID -> baArenaRefreshSnapshot
            else -> null
        }
    }

    fun put(notificationId: Int, snapshot: McpNotificationSnapshot) {
        when (notificationId) {
            McpNotificationHelper.KEEPALIVE_NOTIFICATION_ID -> keepAliveSnapshot = snapshot
            McpNotificationHelper.BA_AP_NOTIFICATION_ID -> baApSnapshot = snapshot
            McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID -> baCafeVisitSnapshot = snapshot
            McpNotificationHelper.BA_CAFE_AP_NOTIFICATION_ID -> baCafeApSnapshot = snapshot
            McpNotificationHelper.BA_ARENA_REFRESH_NOTIFICATION_ID -> baArenaRefreshSnapshot =
                snapshot
        }
    }

    fun putIfChanged(notificationId: Int, snapshot: McpNotificationSnapshot): Boolean {
        val previous = get(notificationId)
        if (previous == snapshot) return false
        put(notificationId, snapshot)
        return true
    }

    fun clear(notificationId: Int) {
        when (notificationId) {
            McpNotificationHelper.KEEPALIVE_NOTIFICATION_ID -> keepAliveSnapshot = null
            McpNotificationHelper.BA_AP_NOTIFICATION_ID -> baApSnapshot = null
            McpNotificationHelper.BA_CAFE_VISIT_NOTIFICATION_ID -> baCafeVisitSnapshot = null
            McpNotificationHelper.BA_CAFE_AP_NOTIFICATION_ID -> baCafeApSnapshot = null
            McpNotificationHelper.BA_ARENA_REFRESH_NOTIFICATION_ID -> baArenaRefreshSnapshot = null
        }
    }
}

internal object McpNotificationActiveStateCache {
    private const val ACTIVE_CACHE_TTL_MS = 1_200L
    private val lock = Any()
    private val cachedActiveById = mutableMapOf<Int, McpNotificationActiveCacheEntry>()

    fun isActive(
        notificationId: Int,
        nowMs: Long = System.currentTimeMillis(),
        probe: () -> Boolean
    ): Boolean {
        synchronized(lock) {
            cachedActiveById[notificationId]
                ?.takeIf { nowMs - it.checkedAtMs <= ACTIVE_CACHE_TTL_MS }
                ?.let { return it.active }
        }
        val active = probe()
        synchronized(lock) {
            cachedActiveById[notificationId] = McpNotificationActiveCacheEntry(
                active = active,
                checkedAtMs = nowMs
            )
        }
        return active
    }

    fun markActive(notificationId: Int, active: Boolean) {
        synchronized(lock) {
            cachedActiveById[notificationId] = McpNotificationActiveCacheEntry(
                active = active,
                checkedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun clear(notificationId: Int) {
        synchronized(lock) {
            cachedActiveById.remove(notificationId)
        }
    }
}

private data class McpNotificationActiveCacheEntry(
    val active: Boolean,
    val checkedAtMs: Long
)
