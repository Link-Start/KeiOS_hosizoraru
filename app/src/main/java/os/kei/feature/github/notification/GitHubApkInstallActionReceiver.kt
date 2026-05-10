package os.kei.feature.github.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.kei.MainActivity
import os.kei.core.background.BackgroundAsyncReceiverRunner
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowCoordinator

class GitHubApkInstallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in supportedActions) return
        when (action) {
            ACTION_OPEN_INSTALL_SHEET -> {
                GitHubApkInstallFlowCoordinator.showSheet()
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        putExtra(
                            MainActivity.EXTRA_TARGET_BOTTOM_PAGE,
                            MainActivity.TARGET_BOTTOM_PAGE_GITHUB
                        )
                    }
                )
            }

            else -> BackgroundAsyncReceiverRunner.launch(
                receiver = this,
                context = context,
                tag = TAG
            ) {
                when (action) {
                    ACTION_CANCEL_INSTALL -> GitHubApkInstallFlowCoordinator.cancel(context)
                    ACTION_CONFIRM_INSTALL -> GitHubApkInstallFlowCoordinator.confirmInstall()
                    ACTION_LAUNCH_PENDING_USER_ACTION ->
                        GitHubApkInstallFlowCoordinator.launchPendingUserAction(context)

                    ACTION_MARK_READ_INSTALL -> GitHubApkInstallFlowCoordinator.markRead(context)
                    ACTION_RETRY_INSTALL -> GitHubApkInstallFlowCoordinator.retry(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_INSTALL_SHEET = "os.kei.github.apk_install.action.OPEN_SHEET"
        const val ACTION_CANCEL_INSTALL = "os.kei.github.apk_install.action.CANCEL"
        const val ACTION_CONFIRM_INSTALL = "os.kei.github.apk_install.action.CONFIRM"
        const val ACTION_LAUNCH_PENDING_USER_ACTION =
            "os.kei.github.apk_install.action.LAUNCH_PENDING_USER_ACTION"
        const val ACTION_MARK_READ_INSTALL = "os.kei.github.apk_install.action.MARK_READ"
        const val ACTION_RETRY_INSTALL = "os.kei.github.apk_install.action.RETRY"

        private const val TAG = "GitHubApkInstallAction"

        private val supportedActions = setOf(
            ACTION_OPEN_INSTALL_SHEET,
            ACTION_CANCEL_INSTALL,
            ACTION_CONFIRM_INSTALL,
            ACTION_LAUNCH_PENDING_USER_ACTION,
            ACTION_MARK_READ_INSTALL,
            ACTION_RETRY_INSTALL
        )
    }
}
