package os.kei.feature.github.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.core.prefs.UiPrefs
import os.kei.feature.github.data.local.AppIconCache
import os.kei.mcp.framework.notification.NotificationHelper
import os.kei.mcp.framework.notification.builder.EnvironmentContext
import os.kei.mcp.framework.notification.builder.LegacyNotificationBuilder
import os.kei.mcp.framework.notification.builder.MiIslandNotificationBuilder
import os.kei.mcp.framework.notification.builder.ModernNotificationBuilder
import os.kei.mcp.framework.notification.builder.NotificationPayload
import os.kei.mcp.framework.notification.builder.NotificationRenderStyle
import os.kei.mcp.framework.notification.builder.UserSettings
import os.kei.mcp.notification.McpNotificationDispatchMode
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.notification.McpNotificationPayload
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowState
import os.kei.ui.page.main.github.install.GitHubApkInstallPhase

internal object GitHubApkInstallNotificationHelper {
    const val NOTIFICATION_ID = 38992
    private const val REQUEST_OPEN = 2401
    private const val REQUEST_CANCEL = 2402
    private const val REQUEST_MARK_READ = 2403
    private const val REQUEST_RETRY = 2404
    private const val REQUEST_CONFIRM_INSTALL = 2405
    private const val REQUEST_LAUNCH_PENDING_USER_ACTION = 2406

    @Volatile
    private var lastDispatchSnapshot: InstallNotificationDispatchSnapshot? = null

    fun notify(context: Context, state: GitHubApkInstallFlowState): Boolean {
        if (!state.active) return false
        return notifyState(context.applicationContext, state)
    }

    fun cancel(context: Context) {
        lastDispatchSnapshot = null
        McpNotificationHelper.cancelNotification(context, NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    internal fun notifyState(context: Context, state: GitHubApkInstallFlowState): Boolean {
        if (!notificationsGranted(context)) return false
        McpNotificationHelper.ensureChannel(context)
        val helper = NotificationHelper(context)
        val preferSuperIsland = UiPrefs.isSuperIslandNotificationEnabled(defaultValue = false)
        val useMiIsland = preferSuperIsland && helper.isSupportMiIsland
        val notification = if (useMiIsland) {
            buildMiIslandNotification(context, state)
        } else {
            buildLiveUpdateNotification(context, state)
        }
        McpNotificationHelper.dispatchNotification(
            context = context,
            notificationId = NOTIFICATION_ID,
            notification = notification,
            dispatchMode = resolveDispatchMode(
                state = state,
                useXiaomiMagic = useMiIsland &&
                        UiPrefs.isSuperIslandBypassRestrictionEnabled(defaultValue = false)
            )
        )
        return true
    }

    private fun buildLiveUpdateNotification(
        context: Context,
        state: GitHubApkInstallFlowState
    ): Notification {
        return buildFrameworkLiveUpdateNotification(context, state)
    }

    internal fun buildFrameworkLiveUpdateNotification(
        context: Context,
        state: GitHubApkInstallFlowState
    ): Notification {
        val helper = NotificationHelper(context)
        return if (helper.isModernLiveUpdateEligible) {
            ModernNotificationBuilder(context).build(buildPayload(context, state, helper, false))
        } else {
            LegacyNotificationBuilder(context).build(buildPayload(context, state, helper, false))
        }
    }

    private fun buildMiIslandNotification(
        context: Context,
        state: GitHubApkInstallFlowState
    ): Notification {
        val helper = NotificationHelper(context)
        return MiIslandNotificationBuilder(context).build(
            buildPayload(context, state, helper, true)
        )
    }

    private fun buildPayload(
        context: Context,
        state: GitHubApkInstallFlowState,
        helper: NotificationHelper,
        miIsland: Boolean
    ): NotificationPayload {
        val openIntent = buildOpenPendingIntent(context)
        val primaryIntent = buildPrimaryPendingIntent(context, state, openIntent)
        val running = state.phase.running
        val content = installContent(context, state)
        val progressPercent = state.downloadProgressPercentOrNull()
        val onlineText = installOnlineText(context, state, progressPercent)
        return NotificationPayload(
            state = McpNotificationPayload(
                serverName = McpNotificationPayload.GITHUB_APK_INSTALL_SERVER_NAME,
                running = running,
                port = progressPercent ?: state.phase.stageOrdinal,
                path = content,
                clients = if (running) 1 else 0,
                ongoing = running,
                onlyAlertOnce = true,
                openPendingIntent = openIntent,
                stopPendingIntent = buildSecondaryPendingIntent(context, state),
                focusOpenPendingIntent = primaryIntent,
                primaryActionPendingIntent = primaryIntent,
                primaryActionLabel = primaryActionLabel(context, state),
                secondaryActionLabel = secondaryActionLabel(context, state),
                showSecondaryActionWhenStopped = true,
                overrideTitle = context.getString(state.phase.titleRes),
                overrideContent = content,
                overrideOnlineText = onlineText,
                overrideShortText = onlineText,
                overrideProgressPercent = progressPercent
            ),
            settings = UserSettings(miIslandOuterGlow = miIsland),
            environment = EnvironmentContext(
                channelId = if (miIsland) {
                    helper.resolveChannel(NotificationRenderStyle.MI_ISLAND)
                } else {
                    McpNotificationHelper.LIVE_CHANNEL_ID
                },
                isHyperOS = helper.isHyperOS,
                preferOemLiveIconLayout = helper.preferOemLiveIconLayout
            ),
            semanticIconBitmap = state.packageName.takeIf { it.isNotBlank() }
                ?.let { packageName -> AppIconCache.getOrLoad(context, packageName) }
        )
    }

    internal fun resolveDispatchMode(
        state: GitHubApkInstallFlowState,
        useXiaomiMagic: Boolean
    ): McpNotificationDispatchMode {
        val snapshot = InstallNotificationDispatchSnapshot(
            sessionId = state.sessionId,
            phase = state.phase,
            hasDownloadProgress = state.downloadProgressPercentOrNull() != null
        )
        val previous = lastDispatchSnapshot
        lastDispatchSnapshot = snapshot
        if (!useXiaomiMagic) return McpNotificationDispatchMode.Plain
        return if (
            snapshot.phase == GitHubApkInstallPhase.Downloading &&
            previous?.sessionId == snapshot.sessionId &&
            previous.phase == snapshot.phase &&
            previous.hasDownloadProgress == snapshot.hasDownloadProgress
        ) {
            McpNotificationDispatchMode.Update
        } else {
            McpNotificationDispatchMode.Pulse
        }
    }

    internal fun resetDispatchStateForTest() {
        lastDispatchSnapshot = null
    }

    private fun buildOpenPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_GITHUB)
            putExtra(
                MainActivity.EXTRA_SHORTCUT_ACTION,
                MainActivity.SHORTCUT_ACTION_GITHUB_OPEN_APK_INSTALL_SHEET
            )
        }
        return PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildPrimaryPendingIntent(
        context: Context,
        state: GitHubApkInstallFlowState,
        openPendingIntent: PendingIntent
    ): PendingIntent {
        return when (state.phase) {
            GitHubApkInstallPhase.ReadyToInstall -> buildReceiverPendingIntent(
                context = context,
                requestCode = REQUEST_CONFIRM_INSTALL,
                action = GitHubApkInstallActionReceiver.ACTION_CONFIRM_INSTALL
            )

            GitHubApkInstallPhase.PendingUserAction -> buildReceiverPendingIntent(
                context = context,
                requestCode = REQUEST_LAUNCH_PENDING_USER_ACTION,
                action = GitHubApkInstallActionReceiver.ACTION_LAUNCH_PENDING_USER_ACTION
            )

            else -> openPendingIntent
        }
    }

    private fun buildSecondaryPendingIntent(
        context: Context,
        state: GitHubApkInstallFlowState
    ): PendingIntent {
        val action = when (state.phase) {
            GitHubApkInstallPhase.Failed -> GitHubApkInstallActionReceiver.ACTION_RETRY_INSTALL
            GitHubApkInstallPhase.Success,
            GitHubApkInstallPhase.Cancelled -> GitHubApkInstallActionReceiver.ACTION_MARK_READ_INSTALL

            else -> GitHubApkInstallActionReceiver.ACTION_CANCEL_INSTALL
        }
        val requestCode = when (action) {
            GitHubApkInstallActionReceiver.ACTION_RETRY_INSTALL -> REQUEST_RETRY
            GitHubApkInstallActionReceiver.ACTION_MARK_READ_INSTALL -> REQUEST_MARK_READ
            else -> REQUEST_CANCEL
        }
        return buildReceiverPendingIntent(
            context = context,
            requestCode = requestCode,
            action = action
        )
    }

    private fun buildReceiverPendingIntent(
        context: Context,
        requestCode: Int,
        action: String
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, GitHubApkInstallActionReceiver::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun primaryActionLabel(context: Context, state: GitHubApkInstallFlowState): String {
        return when (state.phase) {
            GitHubApkInstallPhase.ReadyToInstall ->
                context.getString(R.string.github_apk_install_action_install)

            GitHubApkInstallPhase.PendingUserAction ->
                context.getString(R.string.github_apk_install_action_open_system_confirm)

            else -> context.getString(R.string.github_apk_install_notify_action_open_sheet)
        }
    }

    private fun secondaryActionLabel(context: Context, state: GitHubApkInstallFlowState): String {
        return when (state.phase) {
            GitHubApkInstallPhase.Failed -> context.getString(R.string.github_apk_install_action_retry)
            GitHubApkInstallPhase.Success,
            GitHubApkInstallPhase.Cancelled -> context.getString(R.string.common_mark_read)

            GitHubApkInstallPhase.ReadyToInstall -> context.getString(R.string.common_cancel)
            else -> context.getString(R.string.common_stop)
        }
    }

    private fun installContent(context: Context, state: GitHubApkInstallFlowState): String {
        val name = state.selectedCandidateName
            .ifBlank { state.asset?.name.orEmpty() }
            .ifBlank { state.request.displayLabel }
        return when (state.phase) {
            GitHubApkInstallPhase.Downloading -> context.getString(
                R.string.github_apk_install_notify_content_downloading,
                name
            )

            GitHubApkInstallPhase.SelectingApk -> context.getString(
                R.string.github_apk_install_notify_content_selecting,
                state.candidates.size
            )

            GitHubApkInstallPhase.Inspecting -> context.getString(
                R.string.github_apk_install_notify_content_inspecting,
                name
            )

            GitHubApkInstallPhase.ReadyToInstall -> context.getString(
                R.string.github_apk_install_notify_content_review,
                name
            )

            GitHubApkInstallPhase.Installing -> context.getString(
                R.string.github_apk_install_notify_content_installing,
                state.packageName.ifBlank { name }
            )

            GitHubApkInstallPhase.PendingUserAction -> context.getString(
                R.string.github_apk_install_notify_content_pending_user_action
            )

            GitHubApkInstallPhase.Success -> context.getString(
                R.string.github_apk_install_notify_content_success,
                state.packageName.ifBlank { name }
            )

            GitHubApkInstallPhase.Failed -> state.message.ifBlank {
                context.getString(R.string.github_apk_install_notify_content_failed)
            }

            GitHubApkInstallPhase.Cancelled -> context.getString(
                R.string.github_apk_install_notify_content_cancelled
            )

            GitHubApkInstallPhase.Idle -> ""
        }
    }

    private fun notificationsGranted(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}

private data class InstallNotificationDispatchSnapshot(
    val sessionId: Long,
    val phase: GitHubApkInstallPhase,
    val hasDownloadProgress: Boolean
)

private val GitHubApkInstallPhase.running: Boolean
    get() = this in setOf(
        GitHubApkInstallPhase.Downloading,
        GitHubApkInstallPhase.SelectingApk,
        GitHubApkInstallPhase.Inspecting,
        GitHubApkInstallPhase.ReadyToInstall,
        GitHubApkInstallPhase.Installing,
        GitHubApkInstallPhase.PendingUserAction
    )

private val GitHubApkInstallPhase.titleRes: Int
    get() = when (this) {
        GitHubApkInstallPhase.Downloading -> R.string.github_apk_install_notify_title_downloading
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_notify_title_selecting
        GitHubApkInstallPhase.Inspecting -> R.string.github_apk_install_notify_title_inspecting
        GitHubApkInstallPhase.ReadyToInstall -> R.string.github_apk_install_notify_title_review
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_notify_title_installing
        GitHubApkInstallPhase.PendingUserAction -> R.string.github_apk_install_notify_title_pending_user_action
        GitHubApkInstallPhase.Success -> R.string.github_apk_install_notify_title_success
        GitHubApkInstallPhase.Failed -> R.string.github_apk_install_notify_title_failed
        GitHubApkInstallPhase.Cancelled -> R.string.github_apk_install_notify_title_cancelled
        GitHubApkInstallPhase.Idle -> R.string.github_apk_install_notify_title_installing
    }

private val GitHubApkInstallPhase.shortTextRes: Int
    get() = when (this) {
        GitHubApkInstallPhase.Downloading -> R.string.github_apk_install_notify_short_downloading
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_notify_short_selecting
        GitHubApkInstallPhase.Inspecting -> R.string.github_apk_install_notify_short_inspecting
        GitHubApkInstallPhase.ReadyToInstall -> R.string.github_apk_install_notify_short_review
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_notify_short_installing
        GitHubApkInstallPhase.PendingUserAction -> R.string.github_apk_install_notify_short_pending
        GitHubApkInstallPhase.Success -> R.string.github_apk_install_notify_short_success
        GitHubApkInstallPhase.Failed -> R.string.github_apk_install_notify_short_failed
        GitHubApkInstallPhase.Cancelled -> R.string.github_apk_install_notify_short_cancelled
        GitHubApkInstallPhase.Idle -> R.string.github_apk_install_notify_short_installing
    }

private fun GitHubApkInstallFlowState.downloadProgressPercentOrNull(): Int? {
    if (!showsDeterminateDownloadProgress) return null
    return stageProgressPercent.coerceIn(0, 99)
}

private fun installOnlineText(
    context: Context,
    state: GitHubApkInstallFlowState,
    progressPercent: Int?
): String {
    val phaseText = context.getString(state.phase.shortTextRes)
    return progressPercent?.let { percent ->
        context.getString(
            R.string.github_apk_install_notify_short_with_progress,
            phaseText,
            percent
        )
    } ?: phaseText
}

private val GitHubApkInstallPhase.stageOrdinal: Int
    get() = when (this) {
        GitHubApkInstallPhase.Downloading -> 1
        GitHubApkInstallPhase.SelectingApk -> 2
        GitHubApkInstallPhase.Inspecting -> 3
        GitHubApkInstallPhase.ReadyToInstall -> 4
        GitHubApkInstallPhase.Installing,
        GitHubApkInstallPhase.PendingUserAction -> 5

        GitHubApkInstallPhase.Success -> 6
        GitHubApkInstallPhase.Failed,
        GitHubApkInstallPhase.Cancelled,
        GitHubApkInstallPhase.Idle -> 0
    }
