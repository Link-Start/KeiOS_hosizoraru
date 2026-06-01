package os.kei.ui.page.main.os.shell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R

internal const val BUILT_IN_SHELL_GET_STATUS_BAR_NOTIFICATION_ICON_CARD_ID =
    "builtin-shell-get-status-bar-notification-icon"
internal const val BUILT_IN_SHELL_SET_STATUS_BAR_NOTIFICATION_ICON_9_CARD_ID =
    "builtin-shell-set-status-bar-notification-icon-9"
internal const val BUILT_IN_SHELL_HIDE_GESTURE_LINE_CARD_ID =
    "builtin-shell-hide-gesture-line"

internal const val BUILT_IN_SHELL_GET_STATUS_BAR_NOTIFICATION_ICON_COMMAND =
    "settings get system status_bar_show_notification_icon"
internal const val BUILT_IN_SHELL_SET_STATUS_BAR_NOTIFICATION_ICON_9_COMMAND =
    "settings put system status_bar_show_notification_icon 9"
internal const val BUILT_IN_SHELL_HIDE_GESTURE_LINE_COMMAND =
    "settings put global hide_gesture_line 1"

internal val BUILT_IN_SHELL_COMMAND_CARD_IDS =
    setOf(
        BUILT_IN_SHELL_GET_STATUS_BAR_NOTIFICATION_ICON_CARD_ID,
        BUILT_IN_SHELL_SET_STATUS_BAR_NOTIFICATION_ICON_9_CARD_ID,
        BUILT_IN_SHELL_HIDE_GESTURE_LINE_CARD_ID,
    )

@Composable
internal fun rememberBuiltInShellCommandCards(): List<OsShellCommandCard> {
    val getNotificationIconTitle =
        stringResource(R.string.os_shell_builtin_get_status_bar_notification_icon_title)
    val setNotificationIcon9Title =
        stringResource(R.string.os_shell_builtin_set_status_bar_notification_icon_9_title)
    val hideGestureLineTitle =
        stringResource(R.string.os_shell_builtin_hide_gesture_line_title)
    val hideGestureLineSubtitle =
        stringResource(R.string.os_shell_builtin_hide_gesture_line_subtitle)

    return remember(
        getNotificationIconTitle,
        setNotificationIcon9Title,
        hideGestureLineTitle,
        hideGestureLineSubtitle,
    ) {
        buildBuiltInShellCommandCards(
            getNotificationIconTitle = getNotificationIconTitle,
            setNotificationIcon9Title = setNotificationIcon9Title,
            hideGestureLineTitle = hideGestureLineTitle,
            hideGestureLineSubtitle = hideGestureLineSubtitle,
        )
    }
}

internal fun buildBuiltInShellCommandCards(context: Context): List<OsShellCommandCard> =
    buildBuiltInShellCommandCards(
        getNotificationIconTitle =
            context.getString(R.string.os_shell_builtin_get_status_bar_notification_icon_title),
        setNotificationIcon9Title =
            context.getString(R.string.os_shell_builtin_set_status_bar_notification_icon_9_title),
        hideGestureLineTitle =
            context.getString(R.string.os_shell_builtin_hide_gesture_line_title),
        hideGestureLineSubtitle =
            context.getString(R.string.os_shell_builtin_hide_gesture_line_subtitle),
    )

private fun buildBuiltInShellCommandCards(
    getNotificationIconTitle: String,
    setNotificationIcon9Title: String,
    hideGestureLineTitle: String,
    hideGestureLineSubtitle: String,
): List<OsShellCommandCard> =
    listOf(
        builtInShellCommandCard(
            id = BUILT_IN_SHELL_GET_STATUS_BAR_NOTIFICATION_ICON_CARD_ID,
            title = getNotificationIconTitle,
            command = BUILT_IN_SHELL_GET_STATUS_BAR_NOTIFICATION_ICON_COMMAND,
        ),
        builtInShellCommandCard(
            id = BUILT_IN_SHELL_SET_STATUS_BAR_NOTIFICATION_ICON_9_CARD_ID,
            title = setNotificationIcon9Title,
            command = BUILT_IN_SHELL_SET_STATUS_BAR_NOTIFICATION_ICON_9_COMMAND,
        ),
        builtInShellCommandCard(
            id = BUILT_IN_SHELL_HIDE_GESTURE_LINE_CARD_ID,
            title = hideGestureLineTitle,
            subtitle = hideGestureLineSubtitle,
            command = BUILT_IN_SHELL_HIDE_GESTURE_LINE_COMMAND,
        ),
    )

internal fun isBuiltInShellCommandCard(card: OsShellCommandCard): Boolean = card.id in BUILT_IN_SHELL_COMMAND_CARD_IDS

internal fun builtInShellCommandCardIdForCommand(command: String): String? {
    val normalized = command.trim().replace(Regex("\\s+"), " ")
    return when (normalized) {
        BUILT_IN_SHELL_GET_STATUS_BAR_NOTIFICATION_ICON_COMMAND -> {
            BUILT_IN_SHELL_GET_STATUS_BAR_NOTIFICATION_ICON_CARD_ID
        }

        BUILT_IN_SHELL_SET_STATUS_BAR_NOTIFICATION_ICON_9_COMMAND -> {
            BUILT_IN_SHELL_SET_STATUS_BAR_NOTIFICATION_ICON_9_CARD_ID
        }

        BUILT_IN_SHELL_HIDE_GESTURE_LINE_COMMAND -> {
            BUILT_IN_SHELL_HIDE_GESTURE_LINE_CARD_ID
        }

        else -> {
            null
        }
    }
}

private fun builtInShellCommandCard(
    id: String,
    title: String,
    subtitle: String = "",
    command: String,
): OsShellCommandCard =
    OsShellCommandCard(
        id = id,
        visible = true,
        title = title,
        subtitle = subtitle,
        command = command,
        runOutput = "",
        lastRunAtMillis = 0L,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )
