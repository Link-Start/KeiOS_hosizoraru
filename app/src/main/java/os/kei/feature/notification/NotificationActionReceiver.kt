package os.kei.feature.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import os.kei.core.background.BackgroundAsyncReceiverRunner
import os.kei.mcp.server.McpServerRuntimeRegistry
import os.kei.mcp.service.McpKeepAliveService

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_MARK_READ -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
                if (notificationId == Int.MIN_VALUE) return
                NotificationManagerCompat.from(context).cancel(notificationId)
            }

            ACTION_STOP_MCP_SERVER -> {
                BackgroundAsyncReceiverRunner.launch(
                    receiver = this,
                    context = context,
                    tag = TAG
                ) { appContext ->
                    Log.i(TAG, "Received MCP stop action from notification")
                    val stopped = runCatching {
                        McpServerRuntimeRegistry.stopCurrentServer()
                    }.getOrElse { throwable ->
                        Log.e(TAG, "Failed to stop MCP server from notification", throwable)
                        false
                    }
                    if (!stopped) {
                        Log.w(
                            TAG,
                            "MCP stop requested from notification but runtime registry is empty"
                        )
                        runCatching { McpKeepAliveService.stop(appContext) }
                            .onFailure { throwable ->
                                Log.e(TAG, "Failed to stop MCP keepalive fallback", throwable)
                            }
                    } else {
                        Log.i(TAG, "MCP server stopped from notification")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationActionRcvr"
        const val ACTION_MARK_READ = "os.kei.notification.action.MARK_READ"
        const val ACTION_STOP_MCP_SERVER = "os.kei.notification.action.STOP_MCP_SERVER"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
