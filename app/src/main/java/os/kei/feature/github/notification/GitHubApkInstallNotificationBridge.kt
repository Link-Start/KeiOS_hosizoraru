package os.kei.feature.github.notification

import android.app.Notification
import android.content.Context
import os.kei.mcp.notification.McpNotificationDispatchMode
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowState
import os.kei.ui.page.main.github.install.GitHubApkInstallPhase

internal object GitHubApkInstallNotificationBridge {
    @Volatile
    private var lastDispatchSnapshot: InstallNotificationDispatchSnapshot? = null

    fun dispatch(
        context: Context,
        notificationId: Int,
        notification: Notification,
        state: GitHubApkInstallFlowState,
        useXiaomiMagic: Boolean
    ) {
        McpNotificationHelper.dispatchNotification(
            context = context,
            notificationId = notificationId,
            notification = notification,
            dispatchMode = resolveDispatchMode(
                state = state,
                useXiaomiMagic = useXiaomiMagic
            )
        )
    }

    internal fun resolveDispatchMode(
        state: GitHubApkInstallFlowState,
        useXiaomiMagic: Boolean
    ): McpNotificationDispatchMode {
        val snapshot = InstallNotificationDispatchSnapshot(
            sessionId = state.sessionId,
            phase = state.phase,
            progressPercent = state.downloadProgressPercentForDispatch()
        )
        val previous = lastDispatchSnapshot
        lastDispatchSnapshot = snapshot
        if (!useXiaomiMagic) return McpNotificationDispatchMode.Plain
        return if (previous == null ||
            previous.sessionId != snapshot.sessionId ||
            previous.phase != snapshot.phase ||
            previous.usesProgressTemplate != snapshot.usesProgressTemplate
        ) {
            McpNotificationDispatchMode.Pulse
        } else {
            McpNotificationDispatchMode.Update
        }
    }

    internal fun resetDispatchState() {
        lastDispatchSnapshot = null
    }
}

private data class InstallNotificationDispatchSnapshot(
    val sessionId: Long,
    val phase: GitHubApkInstallPhase,
    val progressPercent: Int?
) {
    val usesProgressTemplate: Boolean
        get() = progressPercent != null
}

private fun GitHubApkInstallFlowState.downloadProgressPercentForDispatch(): Int? {
    if (!showsDeterminateDownloadProgress) return null
    return stageProgressPercent.coerceIn(0, 99)
}
