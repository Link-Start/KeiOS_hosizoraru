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
    val useXiaomiMagic: Boolean,
    val primaryActionLabel: String? = null,
    val secondaryActionLabel: String? = null,
    val showSecondaryActionWhenStopped: Boolean = false,
    val outerGlow: Boolean = true,
    val overrideTitle: String? = null,
    val overrideContent: String? = null,
    val overrideOnlineText: String? = null,
    val overrideShortText: String? = null,
    val overrideProgressPercent: Int? = null,
    val deadlineAtMs: Long? = null,
    val miFocusOrderId: String? = null,
    val targetBaAccountId: String? = null,
)

internal object McpNotificationSnapshotStore {
    private val lock = Any()
    private val snapshotsById = mutableMapOf<Int, McpNotificationSnapshot>()

    fun get(notificationId: Int): McpNotificationSnapshot? {
        return synchronized(lock) {
            snapshotsById[notificationId]
        }
    }

    fun put(notificationId: Int, snapshot: McpNotificationSnapshot) {
        synchronized(lock) {
            snapshotsById[notificationId] = snapshot
        }
    }

    fun putIfChanged(notificationId: Int, snapshot: McpNotificationSnapshot): Boolean {
        return synchronized(lock) {
            val previous = snapshotsById[notificationId]
            if (previous == snapshot) {
                false
            } else {
                snapshotsById[notificationId] = snapshot
                true
            }
        }
    }

    fun entries(): List<Pair<Int, McpNotificationSnapshot>> {
        return synchronized(lock) {
            snapshotsById.entries.map { it.key to it.value }
        }
    }

    fun clear(notificationId: Int) {
        synchronized(lock) {
            snapshotsById.remove(notificationId)
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
