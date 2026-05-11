package os.kei.feature.github.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.kei.core.background.BackgroundAsyncReceiverRunner
import os.kei.ui.page.main.github.share.GitHubShareImportDeliveryRunner
import os.kei.ui.page.main.github.share.GitHubShareImportFlowCoordinator

class GitHubShareImportActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in supportedActions(context)) return
        BackgroundAsyncReceiverRunner.launch(
            receiver = this,
            context = context,
            tag = TAG
        ) { appContext ->
            when (action) {
                ACTION_CANCEL_SHARE_IMPORT,
                actionCancelShareImport(appContext) ->
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(appContext)

                ACTION_MARK_READ_SHARE_IMPORT,
                actionMarkReadShareImport(appContext) ->
                    GitHubShareImportFlowCoordinator.markRead(appContext)

                ACTION_REFRESH_SHARE_IMPORT,
                actionRefreshShareImport(appContext) ->
                    GitHubShareImportFlowCoordinator.refreshPendingInstall(appContext)

                ACTION_SEND_INSTALL_SHARE_IMPORT,
                actionSendInstallShareImport(appContext) ->
                    GitHubShareImportDeliveryRunner.launchActivePreviewDelivery(appContext)

                ACTION_CONFIRM_SHARE_IMPORT,
                actionConfirmShareImport(appContext) ->
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

        fun actionCancelShareImport(context: Context): String =
            "${context.packageName}.github.share_import.action.CANCEL"

        fun actionMarkReadShareImport(context: Context): String =
            "${context.packageName}.github.share_import.action.MARK_READ"

        fun actionRefreshShareImport(context: Context): String =
            "${context.packageName}.github.share_import.action.REFRESH"

        fun actionSendInstallShareImport(context: Context): String =
            "${context.packageName}.github.share_import.action.SEND_INSTALL"

        fun actionConfirmShareImport(context: Context): String =
            "${context.packageName}.github.share_import.action.CONFIRM"

        private fun supportedActions(context: Context): Set<String> = setOf(
            ACTION_CANCEL_SHARE_IMPORT,
            ACTION_MARK_READ_SHARE_IMPORT,
            ACTION_REFRESH_SHARE_IMPORT,
            ACTION_SEND_INSTALL_SHARE_IMPORT,
            ACTION_CONFIRM_SHARE_IMPORT,
            actionCancelShareImport(context),
            actionMarkReadShareImport(context),
            actionRefreshShareImport(context),
            actionSendInstallShareImport(context),
            actionConfirmShareImport(context)
        )
    }
}
