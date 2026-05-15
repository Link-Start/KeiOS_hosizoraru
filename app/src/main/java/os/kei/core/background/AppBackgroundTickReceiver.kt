package os.kei.core.background

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicBoolean

class AppBackgroundTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_GITHUB_TICK && action != ACTION_BA_AP_TICK) return
        val rescheduled = AtomicBoolean(false)
        val rescheduleOnce: suspend (Context) -> Unit = { appContext ->
            if (rescheduled.compareAndSet(false, true)) {
                AppBackgroundScheduler.onTickHandled(appContext, action)
            }
        }
        BackgroundAsyncReceiverRunner.launch(
            receiver = this,
            context = context,
            tag = TAG,
            timeoutMs = timeoutForAction(action),
            onTimeout = rescheduleOnce
        ) { appContext ->
            try {
                when (action) {
                    ACTION_GITHUB_TICK -> AppForegroundInfoHandler.handleGitHubTick(appContext)
                    ACTION_BA_AP_TICK -> AppForegroundInfoHandler.handleBaApTick(appContext)
                }
            } finally {
                rescheduleOnce(appContext)
            }
        }
    }

    companion object {
        const val ACTION_GITHUB_TICK = "os.kei.background.action.GITHUB_TICK"
        const val ACTION_BA_AP_TICK = "os.kei.background.action.BA_AP_TICK"
        private const val REQUEST_CODE_GITHUB_TICK = 42001
        private const val REQUEST_CODE_BA_AP_TICK = 42002
        private const val GITHUB_TICK_TIMEOUT_MS = 45_000L
        private const val BA_AP_TICK_TIMEOUT_MS = 12_000L
        private const val TAG = "AppBackgroundTickReceiver"

        private fun timeoutForAction(action: String): Long {
            return when (action) {
                ACTION_GITHUB_TICK -> GITHUB_TICK_TIMEOUT_MS
                ACTION_BA_AP_TICK -> BA_AP_TICK_TIMEOUT_MS
                else -> GITHUB_TICK_TIMEOUT_MS
            }
        }

        fun githubTickPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AppBackgroundTickReceiver::class.java).apply {
                action = ACTION_GITHUB_TICK
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_GITHUB_TICK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun baApTickPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AppBackgroundTickReceiver::class.java).apply {
                action = ACTION_BA_AP_TICK
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BA_AP_TICK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
