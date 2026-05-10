package os.kei.core.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ShizukuInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_COMMIT_RESULT) return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        if (requestId.isBlank()) return
        ShizukuInstallCommitResultStore.complete(requestId, intent)
    }

    companion object {
        const val ACTION_INSTALL_COMMIT_RESULT = "os.kei.action.SHIZUKU_INSTALL_COMMIT_RESULT"
        const val EXTRA_REQUEST_ID = "os.kei.extra.SHIZUKU_INSTALL_REQUEST_ID"
    }
}
