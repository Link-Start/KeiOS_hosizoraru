package os.kei.core.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.kei.MainActivity
import os.kei.MainActivityIntentRouting
import os.kei.core.background.BackgroundAsyncReceiverRunner

class AppShortcutActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_HANDLE_SHORTCUT) return
        val request = AppShortcutActionRequest.fromIntent(intent) ?: return
        BackgroundAsyncReceiverRunner.launch(
            receiver = this,
            context = context,
            tag = TAG
        ) { appContext ->
            AppShortcutActionHandler.handle(appContext, request)
        }
    }

    companion object {
        const val ACTION_HANDLE_SHORTCUT = "os.kei.shortcut.action.HANDLE"
        private const val TAG = "AppShortcutAction"
    }
}

internal data class AppShortcutActionRequest(
    val targetBottomPage: String,
    val mcpServerAction: String?,
    val shortcutAction: String?
) {
    companion object {
        fun fromIntent(intent: Intent): AppShortcutActionRequest? {
            return fromRaw(
                rawTargetBottomPage = intent.getStringExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE),
                rawMcpServerAction = intent.getStringExtra(MainActivity.EXTRA_MCP_SERVER_ACTION),
                rawShortcutAction = intent.getStringExtra(MainActivity.EXTRA_SHORTCUT_ACTION)
            )
        }

        fun fromRaw(
            rawTargetBottomPage: String?,
            rawMcpServerAction: String?,
            rawShortcutAction: String?
        ): AppShortcutActionRequest? {
            val route = MainActivityIntentRouting.sanitize(
                rawTargetBottomPage = rawTargetBottomPage,
                rawMcpServerAction = rawMcpServerAction,
                rawShortcutAction = rawShortcutAction
            ) ?: return null
            return AppShortcutActionRequest(
                targetBottomPage = route.targetBottomPage,
                mcpServerAction = route.mcpServerAction,
                shortcutAction = route.shortcutAction
            )
        }
    }
}
