package os.kei.feature.github.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.ui.page.main.github.share.GitHubShareImportActivity

internal object GitHubShareImportNotificationActions {
    private const val REQUEST_OPEN_FLOW = 2301
    private const val REQUEST_OPEN_GITHUB = 2302
    private const val REQUEST_MARK_READ = 2303
    private const val REQUEST_CANCEL_IMPORT = 2304
    private const val REQUEST_REFRESH_IMPORT = 2305
    private const val REQUEST_CONFIRM_IMPORT = 2306
    private const val REQUEST_SEND_INSTALL = 2307
    private const val REQUEST_CONFIRM_PAGE_INSTALL = 2308

    fun buildOpenPendingIntent(
        context: Context,
        state: GitHubShareImportNotificationState
    ): PendingIntent {
        return if (state.phase.openGitHubPage) {
            buildOpenGitHubPendingIntent(context)
        } else {
            buildOpenFlowPendingIntent(context)
        }
    }

    fun buildSecondaryPendingIntent(
        context: Context,
        state: GitHubShareImportNotificationState,
        openPendingIntent: PendingIntent
    ): PendingIntent {
        if (!state.phase.ongoing) return buildMarkReadPendingIntent(context)
        return when {
            state.phase == GitHubShareImportNotificationPhase.AssetReady &&
                    state.sendInstallActionEnabled -> buildSendInstallPendingIntent(context)

            state.phase == GitHubShareImportNotificationPhase.InstallReady ->
                buildSendInstallPendingIntent(context)

            state.phase == GitHubShareImportNotificationPhase.PageInstallConfirm &&
                    state.pageInstallConfirmActionEnabled ->
                buildConfirmPageInstallPendingIntent(context)

            state.phase.refreshActionEnabled -> buildRefreshImportPendingIntent(context)
            state.phase.confirmActionEnabled -> buildConfirmImportPendingIntent(context)
            state.phase.cancelActionEnabled -> buildCancelImportPendingIntent(context)
            else -> openPendingIntent
        }
    }

    @StringRes
    fun primaryActionLabelRes(state: GitHubShareImportNotificationState): Int {
        if (state.phase == GitHubShareImportNotificationPhase.PageInstallConfirm) {
            return R.string.github_page_install_notify_action_view_confirm
        }
        if (state.phase.openGitHubPage) return state.primaryActionRes
        return R.string.github_share_import_notify_action_open_flow
    }

    fun secondaryActionLabel(
        context: Context,
        state: GitHubShareImportNotificationState
    ): String {
        if (!state.phase.ongoing) return context.getString(R.string.common_mark_read)
        return when {
            state.phase == GitHubShareImportNotificationPhase.AssetReady &&
                    state.sendInstallActionEnabled ->
                context.getString(R.string.github_share_import_notify_action_send_install)

            state.phase == GitHubShareImportNotificationPhase.InstallReady ->
                context.getString(R.string.github_share_import_notify_action_continue_install)

            state.phase == GitHubShareImportNotificationPhase.PageInstallConfirm &&
                    state.pageInstallConfirmActionEnabled ->
                context.getString(R.string.github_page_install_confirm_action_install)

            state.phase.refreshActionEnabled -> context.getString(R.string.common_refresh)
            state.phase.confirmActionEnabled ->
                context.getString(R.string.github_share_import_notify_action_confirm_track)

            state.phase.cancelActionEnabled ->
                context.getString(R.string.github_share_import_pending_action_cancel)

            else -> ""
        }
    }

    private fun buildOpenFlowPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActivity::class.java).apply {
            action = GitHubShareImportActivity.ACTION_RESUME_SHARE_IMPORT
            putExtra(GitHubShareImportActivity.EXTRA_FORCE_SHEET, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            REQUEST_OPEN_FLOW,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildOpenGitHubPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_GITHUB)
        }
        return PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            REQUEST_OPEN_GITHUB,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildMarkReadPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActionReceiver::class.java).apply {
            action = GitHubShareImportActionReceiver.actionMarkReadShareImport(context)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_MARK_READ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCancelImportPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActionReceiver::class.java).apply {
            action = GitHubShareImportActionReceiver.actionCancelShareImport(context)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CANCEL_IMPORT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildRefreshImportPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActionReceiver::class.java).apply {
            action = GitHubShareImportActionReceiver.actionRefreshShareImport(context)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH_IMPORT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildConfirmImportPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActionReceiver::class.java).apply {
            action = GitHubShareImportActionReceiver.actionConfirmShareImport(context)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CONFIRM_IMPORT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSendInstallPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActionReceiver::class.java).apply {
            action = GitHubShareImportActionReceiver.actionSendInstallShareImport(context)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_SEND_INSTALL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildConfirmPageInstallPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_GITHUB)
            putExtra(
                MainActivity.EXTRA_SHORTCUT_ACTION,
                MainActivity.SHORTCUT_ACTION_GITHUB_CONFIRM_MANAGED_INSTALL
            )
        }
        return PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            REQUEST_CONFIRM_PAGE_INSTALL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
