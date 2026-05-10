package os.kei.mcp.framework.notification.builder

import androidx.core.app.NotificationCompat
import os.kei.R
import os.kei.mcp.notification.McpNotificationPayload
import kotlin.math.roundToInt

internal enum class ModernNotificationKind {
    DEFAULT,
    BA_AP,
    BA_CAFE_VISIT,
    BA_ARENA_REFRESH,
    BA_CALENDAR_POOL,
    GITHUB_SHARE_IMPORT,
    GITHUB_APK_INSTALL
}

internal enum class ModernShortCriticalMode {
    NONE,
    SHORT_TEXT,
    ONLINE_TEXT
}

internal data class ModernNotificationSpec(
    val kind: ModernNotificationKind,
    val iconResId: Int,
    val expandedIconResId: Int?,
    val trackerIconResId: Int?,
    val progressPercent: Int,
    val progressColor: Int,
    val category: String,
    val shortCriticalMode: ModernShortCriticalMode,
    val ongoing: Boolean,
    val requestPromotedOngoing: Boolean
)

internal object ModernNotificationSpecResolver {
    private const val PROGRESS_ACTIVE_COLOR = 0xFF2E7D32.toInt()
    private const val PROGRESS_IDLE_COLOR = 0xFF64748B.toInt()
    private val ICON_DEFAULT = R.drawable.ic_kei_notification_small
    private val ICON_DEFAULT_OEM = R.drawable.ic_kei_logo_live_update
    private val ICON_BA_AP = R.drawable.ic_ba_ap_island_notification
    private val ICON_BA_CAFE_VISIT = R.drawable.ic_ba_tea_party_island
    private val ICON_BA_ARENA_REFRESH = R.drawable.ic_ba_arena_coin_island
    private val ICON_BA_CALENDAR_POOL = R.drawable.ic_ba_calendar_live_update
    private val ICON_GITHUB_SHARE_IMPORT = R.drawable.ic_github_invertocat_island_blue
    private val CONTENT_ICON_AP = R.drawable.ic_ba_ap_live_update
    private val CONTENT_ICON_BA_CAFE_VISIT = R.drawable.ic_ba_tea_party_live_update
    private val CONTENT_ICON_BA_ARENA_REFRESH = R.drawable.ic_ba_arena_coin_live_update
    private val CONTENT_ICON_BA_CALENDAR_POOL = R.drawable.ic_ba_calendar_live_update
    private val CONTENT_ICON_GITHUB_SHARE_IMPORT = R.drawable.ic_github_invertocat_island_blue

    fun resolve(
        state: McpNotificationPayload,
        preferOemLiveIconLayout: Boolean = false
    ): ModernNotificationSpec {
        val kind = resolveKind(state.serverName)
        val isRunning = state.running
        return ModernNotificationSpec(
            kind = kind,
            iconResId = resolveIcon(kind, preferOemLiveIconLayout),
            expandedIconResId = resolveExpandedIcon(kind),
            trackerIconResId = resolveTrackerIcon(kind),
            progressPercent = resolveProgressPercent(state = state, kind = kind),
            progressColor = if (isRunning) PROGRESS_ACTIVE_COLOR else PROGRESS_IDLE_COLOR,
            category = if (isRunning) {
                NotificationCompat.CATEGORY_PROGRESS
            } else {
                NotificationCompat.CATEGORY_STATUS
            },
            shortCriticalMode = if (isRunning) resolveShortCriticalMode(kind) else ModernShortCriticalMode.NONE,
            ongoing = isRunning || state.ongoing,
            requestPromotedOngoing = isRunning || state.ongoing
        )
    }

    private fun resolveKind(serverName: String): ModernNotificationKind {
        return when {
            McpNotificationPayload.isBaApServerName(serverName) -> ModernNotificationKind.BA_AP
            McpNotificationPayload.isBaCafeVisitServerName(serverName) -> ModernNotificationKind.BA_CAFE_VISIT
            McpNotificationPayload.isBaArenaRefreshServerName(serverName) -> ModernNotificationKind.BA_ARENA_REFRESH
            McpNotificationPayload.isBaCalendarPoolServerName(serverName) -> ModernNotificationKind.BA_CALENDAR_POOL
            McpNotificationPayload.isGitHubShareImportServerName(serverName) -> ModernNotificationKind.GITHUB_SHARE_IMPORT
            McpNotificationPayload.isGitHubApkInstallServerName(serverName) -> ModernNotificationKind.GITHUB_APK_INSTALL
            else -> ModernNotificationKind.DEFAULT
        }
    }

    private fun resolveIcon(
        kind: ModernNotificationKind,
        preferOemLiveIconLayout: Boolean
    ): Int {
        return when (kind) {
            ModernNotificationKind.DEFAULT -> if (preferOemLiveIconLayout) {
                ICON_DEFAULT_OEM
            } else {
                ICON_DEFAULT
            }

            ModernNotificationKind.BA_AP,
            ModernNotificationKind.BA_CAFE_VISIT,
            ModernNotificationKind.BA_ARENA_REFRESH,
            ModernNotificationKind.BA_CALENDAR_POOL,
            ModernNotificationKind.GITHUB_SHARE_IMPORT,
            ModernNotificationKind.GITHUB_APK_INSTALL -> resolveSemanticCompactIcon(kind)
        }
    }

    private fun resolveSemanticCompactIcon(kind: ModernNotificationKind): Int {
        return when (kind) {
            ModernNotificationKind.BA_AP -> ICON_BA_AP
            ModernNotificationKind.BA_CAFE_VISIT -> ICON_BA_CAFE_VISIT
            ModernNotificationKind.BA_ARENA_REFRESH -> ICON_BA_ARENA_REFRESH
            ModernNotificationKind.BA_CALENDAR_POOL -> ICON_BA_CALENDAR_POOL
            ModernNotificationKind.GITHUB_SHARE_IMPORT,
            ModernNotificationKind.GITHUB_APK_INSTALL -> ICON_GITHUB_SHARE_IMPORT
            ModernNotificationKind.DEFAULT -> ICON_DEFAULT_OEM
        }
    }

    private fun resolveExpandedIcon(kind: ModernNotificationKind): Int? {
        return resolveContentIcon(kind)
    }

    private fun resolveTrackerIcon(kind: ModernNotificationKind): Int? {
        return resolveContentIcon(kind)
    }

    private fun resolveContentIcon(kind: ModernNotificationKind): Int? {
        return when (kind) {
            ModernNotificationKind.DEFAULT -> null
            ModernNotificationKind.BA_AP -> CONTENT_ICON_AP
            ModernNotificationKind.BA_CAFE_VISIT -> CONTENT_ICON_BA_CAFE_VISIT
            ModernNotificationKind.BA_ARENA_REFRESH -> CONTENT_ICON_BA_ARENA_REFRESH
            ModernNotificationKind.BA_CALENDAR_POOL -> CONTENT_ICON_BA_CALENDAR_POOL
            ModernNotificationKind.GITHUB_SHARE_IMPORT,
            ModernNotificationKind.GITHUB_APK_INSTALL -> CONTENT_ICON_GITHUB_SHARE_IMPORT
        }
    }

    private fun resolveShortCriticalMode(kind: ModernNotificationKind): ModernShortCriticalMode {
        return when (kind) {
            ModernNotificationKind.BA_CAFE_VISIT,
            ModernNotificationKind.BA_ARENA_REFRESH -> ModernShortCriticalMode.ONLINE_TEXT

            ModernNotificationKind.DEFAULT,
            ModernNotificationKind.BA_AP,
            ModernNotificationKind.BA_CALENDAR_POOL,
            ModernNotificationKind.GITHUB_SHARE_IMPORT,
            ModernNotificationKind.GITHUB_APK_INSTALL -> ModernShortCriticalMode.SHORT_TEXT
        }
    }

    private fun resolveProgressPercent(
        state: McpNotificationPayload,
        kind: ModernNotificationKind
    ): Int {
        if (!state.running) return 0
        return when (kind) {
            ModernNotificationKind.BA_CAFE_VISIT,
            ModernNotificationKind.BA_ARENA_REFRESH -> 100

            ModernNotificationKind.BA_CALENDAR_POOL,
            ModernNotificationKind.GITHUB_SHARE_IMPORT,
            ModernNotificationKind.GITHUB_APK_INSTALL -> {
                state.overrideProgressPercent?.coerceIn(0, 100) ?: 100
            }

            ModernNotificationKind.BA_AP -> {
                val apLimit = state.clients.coerceAtLeast(1)
                val apCurrent = state.port.coerceAtLeast(0).coerceAtMost(apLimit)
                ((apCurrent.toFloat() / apLimit.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)
            }

            ModernNotificationKind.DEFAULT -> {
                (state.clients.coerceAtLeast(0) * 24)
                    .coerceIn(8, 100)
            }
        }
    }
}
