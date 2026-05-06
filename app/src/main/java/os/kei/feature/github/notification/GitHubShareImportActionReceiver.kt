package os.kei.feature.github.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.kei.feature.github.data.local.GitHubShareImportPreviewStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import kotlin.concurrent.thread

class GitHubShareImportActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CANCEL_SHARE_IMPORT) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        thread(name = "github-share-import-cancel") {
            try {
                GitHubShareImportPreviewStore.clearActiveFlow()
                GitHubTrackStore.savePendingShareImportTrack(null)
                GitHubTrackStoreSignals.notifyChanged()
                GitHubShareImportNotificationHelper.notifyCancelled(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_SHARE_IMPORT = "os.kei.github.share_import.action.CANCEL"
    }
}
