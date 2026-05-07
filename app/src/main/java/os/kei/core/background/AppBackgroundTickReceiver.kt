package os.kei.core.background

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppBackgroundTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_GITHUB_TICK && action != ACTION_BA_AP_TICK) return
        BackgroundAsyncReceiverRunner.launch(
            receiver = this,
            context = context,
            tag = TAG
        ) { appContext ->
            try {
                when (action) {
                    ACTION_GITHUB_TICK -> AppForegroundInfoHandler.handleGitHubTick(appContext)
                    ACTION_BA_AP_TICK -> AppForegroundInfoHandler.handleBaApTick(appContext)
                }
            } finally {
                AppBackgroundScheduler.onTickHandled(appContext, action)
            }
        }
    }

    companion object {
        const val ACTION_GITHUB_TICK = "os.kei.background.action.GITHUB_TICK"
        const val ACTION_BA_AP_TICK = "os.kei.background.action.BA_AP_TICK"
        private const val REQUEST_CODE_GITHUB_TICK = 42001
        private const val REQUEST_CODE_BA_AP_TICK = 42002
        private const val TAG = "AppBackgroundTickReceiver"

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
