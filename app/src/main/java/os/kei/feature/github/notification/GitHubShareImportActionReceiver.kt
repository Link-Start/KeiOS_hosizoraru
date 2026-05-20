package os.kei.feature.github.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.kei.R
import os.kei.core.background.BackgroundAsyncReceiverRunner
import os.kei.feature.github.install.GitHubPageManagedInstallConfirmRegistry
import os.kei.ui.page.main.github.share.GitHubShareImportDeliveryRunner
import os.kei.ui.page.main.github.share.GitHubShareImportFlowCoordinator

class GitHubShareImportActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val action = intent?.action ?: return
        if (action !in supportedActions(context)) return
        BackgroundAsyncReceiverRunner.launch(
            receiver = this,
            context = context,
            tag = TAG,
        ) { appContext ->
            when (action) {
                actionCancelShareImport(appContext) -> {
                    GitHubShareImportFlowCoordinator.cancelActiveFlow(appContext)
                }

                actionMarkReadShareImport(appContext) -> {
                    GitHubShareImportFlowCoordinator.markRead(appContext)
                }

                actionRefreshShareImport(appContext) -> {
                    GitHubShareImportFlowCoordinator.refreshPendingInstall(appContext)
                }

                actionSendInstallShareImport(appContext) -> {
                    GitHubShareImportDeliveryRunner.launchCurrentDeliveryAction(appContext)
                }

                actionConfirmShareImport(appContext) -> {
                    GitHubShareImportFlowCoordinator.confirmActiveAttachCandidate(appContext)
                }

                actionConfirmPageInstall(appContext) -> {
                    val consumed = GitHubPageManagedInstallConfirmRegistry.confirm(appContext)
                    if (!consumed) {
                        GitHubShareImportNotificationHelper.notifyPageInstallFailed(
                            context = appContext,
                            reason = appContext.getString(R.string.github_page_install_confirm_expired),
                        )
                    }
                }
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
        const val ACTION_CONFIRM_PAGE_INSTALL =
            "os.kei.github.share_import.action.CONFIRM_PAGE_INSTALL"

        private const val TAG = "GitHubShareImportAction"
        private const val ACTION_CONFIRM_PAGE_INSTALL_SUFFIX =
            ".github.share_import.action.CONFIRM_PAGE_INSTALL"

        fun actionCancelShareImport(context: Context): String = "${context.packageName}.github.share_import.action.CANCEL"

        fun actionMarkReadShareImport(context: Context): String = "${context.packageName}.github.share_import.action.MARK_READ"

        fun actionRefreshShareImport(context: Context): String = "${context.packageName}.github.share_import.action.REFRESH"

        fun actionSendInstallShareImport(context: Context): String = "${context.packageName}.github.share_import.action.SEND_INSTALL"

        fun actionConfirmShareImport(context: Context): String = "${context.packageName}.github.share_import.action.CONFIRM"

        fun actionConfirmPageInstall(context: Context): String = context.packageName + ACTION_CONFIRM_PAGE_INSTALL_SUFFIX

        private fun supportedActions(context: Context): Set<String> =
            setOf(
                actionCancelShareImport(context),
                actionMarkReadShareImport(context),
                actionRefreshShareImport(context),
                actionSendInstallShareImport(context),
                actionConfirmShareImport(context),
                actionConfirmPageInstall(context),
            )
    }
}
