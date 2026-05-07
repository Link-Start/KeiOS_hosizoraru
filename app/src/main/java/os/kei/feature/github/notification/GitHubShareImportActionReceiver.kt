package os.kei.feature.github.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.kei.core.background.BackgroundAsyncReceiverRunner
import os.kei.ui.page.main.github.share.GitHubShareImportFlowCoordinator

class GitHubShareImportActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in supportedActions) return
        BackgroundAsyncReceiverRunner.launch(
            receiver = this,
            context = context,
            tag = TAG
        ) { appContext ->
            when (action) {
                ACTION_CANCEL_SHARE_IMPORT ->
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(appContext)

                ACTION_MARK_READ_SHARE_IMPORT ->
                    GitHubShareImportFlowCoordinator.markRead(appContext)

                ACTION_REFRESH_SHARE_IMPORT ->
                    GitHubShareImportFlowCoordinator.refreshPendingInstall(appContext)

                ACTION_SEND_INSTALL_SHARE_IMPORT ->
                    GitHubShareImportFlowCoordinator.sendActivePreviewAssetToInstaller(appContext)

                ACTION_CONFIRM_SHARE_IMPORT ->
                    GitHubShareImportFlowCoordinator.confirmActiveAttachCandidate(appContext)
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_SHARE_IMPORT = "os.kei.github.share_import.action.CANCEL"
        const val ACTION_MARK_READ_SHARE_IMPORT = "os.kei.github.share_import.action.MARK_READ"
        const val ACTION_REFRESH_SHARE_IMPORT = "os.kei.github.share_import.action.REFRESH"
        const val ACTION_SEND_INSTALL_SHARE_IMPORT =
            "os.kei.github.share_import.action.SEND_INSTALL"
        const val ACTION_CONFIRM_SHARE_IMPORT = "os.kei.github.share_import.action.CONFIRM"

        private const val TAG = "GitHubShareImportAction"

        private val supportedActions = setOf(
            ACTION_CANCEL_SHARE_IMPORT,
            ACTION_MARK_READ_SHARE_IMPORT,
            ACTION_REFRESH_SHARE_IMPORT,
            ACTION_SEND_INSTALL_SHARE_IMPORT,
            ACTION_CONFIRM_SHARE_IMPORT
        )
    }
}
