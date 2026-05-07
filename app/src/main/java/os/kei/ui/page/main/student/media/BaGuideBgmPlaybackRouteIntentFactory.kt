package os.kei.ui.page.main.student

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import os.kei.MainActivity
import os.kei.core.log.AppLogger

internal const val BA_GUIDE_BGM_PLAYBACK_PENDING_INTENT_REQUEST_CODE = 0x0B6105

internal object BaGuideBgmPlaybackRouteIntentFactory {
    private const val TAG = "BaBgmPlaybackRoute"
    private const val ACTION_OPEN_BGM_PLAYBACK = "os.kei.action.OPEN_BA_BGM_PLAYBACK"

    fun createIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_BGM_PLAYBACK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_BA)
            putExtra(
                MainActivity.EXTRA_SHORTCUT_ACTION,
                MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK
            )
        }
    }

    fun createPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            BA_GUIDE_BGM_PLAYBACK_PENDING_INTENT_REQUEST_CODE,
            createIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun send(context: Context): Boolean {
        return runCatching {
            createPendingIntent(context).send()
            true
        }.getOrElse { throwable ->
            AppLogger.w(TAG, "open BGM playback route failed", throwable)
            false
        }
    }
}
