package os.kei.mcp.notification

import android.app.PendingIntent
import android.content.Context
import os.kei.R

data class McpNotificationPayload(
    val serverName: String,
    val running: Boolean,
    val port: Int,
    val path: String,
    val clients: Int,
    val ongoing: Boolean,
    val onlyAlertOnce: Boolean,
    val openPendingIntent: PendingIntent,
    val stopPendingIntent: PendingIntent,
    val focusOpenPendingIntent: PendingIntent = openPendingIntent,
    val primaryActionLabel: String? = null,
    val secondaryActionLabel: String? = null,
    val showSecondaryActionWhenStopped: Boolean = false,
    val outerGlow: Boolean = true,
    val overrideTitle: String? = null,
    val overrideContent: String? = null,
    val overrideOnlineText: String? = null,
    val overrideShortText: String? = null,
    val overrideProgressPercent: Int? = null,
    val deadlineAtMs: Long? = null
) {
    companion object {
        const val BA_AP_SERVER_NAME = "BlueArchive AP"
        const val BA_CAFE_AP_SERVER_NAME = "BlueArchive Cafe AP"
        const val BA_CAFE_VISIT_SERVER_NAME = "BlueArchive Cafe Visit"
        const val BA_ARENA_REFRESH_SERVER_NAME = "BlueArchive Arena Refresh"
        const val BA_CALENDAR_POOL_SERVER_NAME = "BlueArchive Calendar Pool"
        const val GITHUB_SHARE_IMPORT_SERVER_NAME = "GitHub Share Import"
        const val BA_CAFE_VISIT_PATH = "student_visit"
        const val BA_ARENA_REFRESH_PATH = "arena_refresh"

        fun isBaApServerName(serverName: String): Boolean {
            return serverName.trim() == BA_AP_SERVER_NAME
        }

        fun isBaCafeApServerName(serverName: String): Boolean {
            return serverName.trim() == BA_CAFE_AP_SERVER_NAME
        }

        fun isBaCafeVisitServerName(serverName: String): Boolean {
            return serverName.trim() == BA_CAFE_VISIT_SERVER_NAME
        }

        fun isBaArenaRefreshServerName(serverName: String): Boolean {
            return serverName.trim() == BA_ARENA_REFRESH_SERVER_NAME
        }

        fun isBaCalendarPoolServerName(serverName: String): Boolean {
            return serverName.trim() == BA_CALENDAR_POOL_SERVER_NAME
        }

        fun isGitHubShareImportServerName(serverName: String): Boolean {
            return serverName.trim() == GITHUB_SHARE_IMPORT_SERVER_NAME
        }

        fun isBaNotificationServerName(serverName: String): Boolean {
            return isBaApServerName(serverName) ||
                isBaCafeApServerName(serverName) ||
                isBaCafeVisitServerName(serverName) ||
                    isBaArenaRefreshServerName(serverName) ||
                    isBaCalendarPoolServerName(serverName)
        }
    }

    private val normalizedServerName: String
        get() = serverName.trim().ifBlank { "KeiOS MCP" }

    private val isBlueArchiveAp: Boolean
        get() = isBaApServerName(normalizedServerName)

    private val isBlueArchiveCafeVisit: Boolean
        get() = isBaCafeVisitServerName(normalizedServerName)

    private val isBlueArchiveCafeAp: Boolean
        get() = isBaCafeApServerName(normalizedServerName)

    private val isBlueArchiveArenaRefresh: Boolean
        get() = isBaArenaRefreshServerName(normalizedServerName)

    fun title(context: Context): String {
        overrideTitle?.takeIf { it.isNotBlank() }?.let { return it }
        return if (running) {
            if (isBlueArchiveCafeAp) {
                context.getString(R.string.ba_cafe_ap_notification_title)
            } else if (isBlueArchiveCafeVisit) {
                context.getString(R.string.ba_cafe_visit_notification_title)
            } else if (isBlueArchiveArenaRefresh) {
                context.getString(R.string.ba_arena_refresh_notification_title)
            } else {
                normalizedServerName
            }
        } else {
            context.getString(R.string.mcp_notification_title_stopped, normalizedServerName)
        }
    }

    fun content(context: Context): String {
        overrideContent?.takeIf { it.isNotBlank() }?.let { return it }
        return if (running) {
            if (isBlueArchiveCafeAp) {
                context.getString(R.string.ba_cafe_ap_notification_content, port, path, clients)
            } else if (isBlueArchiveCafeVisit) {
                if (path.isBlank() || path == BA_CAFE_VISIT_PATH) {
                    context.getString(R.string.ba_cafe_visit_notification_content)
                } else {
                    path
                }
            } else if (isBlueArchiveArenaRefresh) {
                if (path.isBlank() || path == BA_ARENA_REFRESH_PATH) {
                    context.getString(R.string.ba_arena_refresh_notification_content)
                } else {
                    path
                }
            } else if (isBlueArchiveAp) {
                context.getString(R.string.mcp_notification_content_ap, port, path, clients)
            } else {
                context.getString(R.string.mcp_notification_content_default, port, path, clients)
            }
        } else {
            context.getString(R.string.mcp_notification_content_tap_restart)
        }
    }

    fun statusText(context: Context): String {
        return if (running) {
            context.getString(R.string.mcp_status_running)
        } else {
            context.getString(R.string.mcp_status_stopped)
        }
    }

    fun onlineText(context: Context): String {
        overrideOnlineText?.takeIf { it.isNotBlank() }?.let { return it }
        return if (isBlueArchiveCafeAp) {
            context.getString(R.string.ba_cafe_ap_notification_island_text)
        } else if (isBlueArchiveCafeVisit) {
            context.getString(R.string.ba_cafe_visit_notification_island_text)
        } else if (isBlueArchiveArenaRefresh) {
            context.getString(R.string.ba_arena_refresh_notification_island_text)
        } else if (isBlueArchiveAp) {
            context.getString(R.string.mcp_online_text_ap_limit, clients)
        } else {
            context.getString(R.string.mcp_online_text_clients, clients)
        }
    }

    val shortText: String
        get() = overrideShortText?.takeIf { it.isNotBlank() } ?: if (running) {
            if (isBlueArchiveAp || isBlueArchiveCafeAp) {
                "${port.coerceAtLeast(0)}/${clients.coerceAtLeast(0)}"
            } else {
                "${statusDot} $clients"
            }
        } else {
            "${statusDot} 0"
        }

    val statusDot: String
        get() = when {
            !running -> "○"
            clients > 0 -> "●"
            else -> "◐"
        }

    fun stopActionTitle(context: Context): String {
        secondaryActionLabel?.takeIf { it.isNotBlank() }?.let { return it }
        return if (isBlueArchiveAp ||
            isBlueArchiveCafeAp ||
            isBlueArchiveCafeVisit ||
            isBlueArchiveArenaRefresh
        ) {
            context.getString(R.string.common_mark_read)
        } else {
            context.getString(R.string.mcp_action_toggle_service)
        }
    }

    fun primaryActionTitle(context: Context): String {
        primaryActionLabel?.takeIf { it.isNotBlank() }?.let { return it }
        return context.getString(R.string.common_open)
    }

    val expandedTitle: String
        get() = overrideTitle?.takeIf { it.isNotBlank() } ?: normalizedServerName

    fun expandedContent(context: Context): String {
        overrideContent?.takeIf { it.isNotBlank() }?.let { return it }
        return if (running) {
            if (isBlueArchiveCafeAp) {
                context.getString(R.string.ba_cafe_ap_notification_content, port, path, clients)
            } else if (isBlueArchiveCafeVisit) {
                if (path.isBlank() || path == BA_CAFE_VISIT_PATH) {
                    context.getString(R.string.ba_cafe_visit_notification_content)
                } else {
                    path
                }
            } else if (isBlueArchiveArenaRefresh) {
                if (path.isBlank() || path == BA_ARENA_REFRESH_PATH) {
                    context.getString(R.string.ba_arena_refresh_notification_content)
                } else {
                    path
                }
            } else if (isBlueArchiveAp) {
                context.getString(R.string.mcp_notification_content_ap, port, path, clients)
            } else {
                context.getString(R.string.mcp_notification_content_default, port, path, clients)
            }
        } else {
            context.getString(R.string.mcp_notification_service_not_running)
        }
    }
}
