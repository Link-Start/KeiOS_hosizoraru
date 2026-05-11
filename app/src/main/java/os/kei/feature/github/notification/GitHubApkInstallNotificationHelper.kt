package os.kei.feature.github.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.core.log.AppLogger
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
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.notification.McpNotificationPayload
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowState
import os.kei.ui.page.main.github.install.GitHubApkInstallPhase

internal object GitHubApkInstallNotificationHelper {
    private const val TAG = "GitHubApkInstallNotify"
    const val NOTIFICATION_ID = 38992
    private const val REQUEST_OPEN = 2401
    private const val REQUEST_CANCEL = 2402
    private const val REQUEST_MARK_READ = 2403
    private const val REQUEST_RETRY = 2404
    private const val REQUEST_CONFIRM_INSTALL = 2405
    private const val REQUEST_LAUNCH_PENDING_USER_ACTION = 2406
    private const val REQUEST_PREPARE_INSTALL = 2407

    internal data class NotificationBuildResult(
        val notification: Notification,
        val style: NotificationRenderStyle,
        val useXiaomiMagic: Boolean
    )

    fun notify(context: Context, state: GitHubApkInstallFlowState): Boolean {
        if (!state.active) return false
        return runCatching {
            notifyState(context.applicationContext, state)
        }.getOrElse { error ->
            AppLogger.e(TAG, "Dispatch APK install notification failed", error)
            false
        }
    }

    fun cancel(context: Context) {
        GitHubApkInstallNotificationBridge.resetDispatchState()
        McpNotificationHelper.cancelNotification(context, NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    internal fun notifyState(context: Context, state: GitHubApkInstallFlowState): Boolean {
        if (!notificationsGranted(context)) return false
        McpNotificationHelper.ensureChannel(context)
        val buildResult = buildNotificationResult(context, state) ?: return false
        GitHubApkInstallNotificationBridge.dispatch(
            context = context,
            notificationId = NOTIFICATION_ID,
            notification = buildResult.notification,
            state = state,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        return true
    }

    internal fun buildNotificationResult(
        context: Context,
        state: GitHubApkInstallFlowState
    ): NotificationBuildResult? {
        val helper = NotificationHelper(context)
        val preferSuperIsland = UiPrefs.isSuperIslandNotificationEnabled(defaultValue = false)
        val style = if (preferSuperIsland && helper.isSupportMiIsland) {
            NotificationRenderStyle.MI_ISLAND
        } else if (helper.isModernLiveUpdateEligible) {
            NotificationRenderStyle.LIVE_UPDATE
        } else {
            NotificationRenderStyle.LEGACY
        }
        AppLogger.i(
            TAG,
            "buildNotificationResult preferSuperIsland=$preferSuperIsland " +
                    "supportMiIsland=${helper.isSupportMiIsland} " +
                    "focusPermission=${helper.hasMiIslandPermission} style=$style"
        )
        val miIsland = style == NotificationRenderStyle.MI_ISLAND
        val payload = buildPayload(
            context = context,
            state = state,
            helper = helper,
            miIsland = miIsland
        )
        val notification = when (style) {
            NotificationRenderStyle.MI_ISLAND -> MiIslandNotificationBuilder(context).build(payload)
            NotificationRenderStyle.LIVE_UPDATE -> ModernNotificationBuilder(context).build(payload)
            NotificationRenderStyle.LEGACY -> LegacyNotificationBuilder(context).build(payload)
        }
        if (miIsland && !notification.hasGitHubApkInstallFocusContract()) {
            AppLogger.e(
                TAG,
                "GitHub APK install MiIsland notification missing Focus extras; phase=${state.phase}"
            )
            return null
        }
        return NotificationBuildResult(
            notification = notification,
            style = style,
            useXiaomiMagic = miIsland &&
                    UiPrefs.isSuperIslandBypassRestrictionEnabled(defaultValue = false)
        )
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

    internal fun buildFrameworkMiIslandNotification(
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
        val progressPercent = state.installNotificationProgressPercentOrNull()
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
                overrideProgressPercent = progressPercent,
                focusAllowFloat = state.phase.focusAllowFloat
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

    internal fun resetDispatchStateForTest() {
        GitHubApkInstallNotificationBridge.resetDispatchState()
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
            GitHubApkInstallPhase.RemoteReady -> buildReceiverPendingIntent(
                context = context,
                requestCode = REQUEST_PREPARE_INSTALL,
                action = GitHubApkInstallActionReceiver.ACTION_PREPARE_INSTALL
            )

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
            GitHubApkInstallPhase.RemoteReady ->
                context.getString(R.string.github_apk_install_action_prepare_install)

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

            GitHubApkInstallPhase.RemoteResolving,
            GitHubApkInstallPhase.RemoteReady,
            GitHubApkInstallPhase.ReadyToInstall -> context.getString(R.string.common_cancel)
            else -> context.getString(R.string.common_stop)
        }
    }

    private fun installContent(context: Context, state: GitHubApkInstallFlowState): String {
        val name = state.selectedCandidateName
            .ifBlank { state.asset?.name.orEmpty() }
            .ifBlank { state.request.displayLabel }
        return when (state.phase) {
            GitHubApkInstallPhase.RemoteResolving -> context.getString(
                R.string.github_apk_install_notify_content_remote_resolving,
                name
            )

            GitHubApkInstallPhase.RemoteReady -> context.getString(
                R.string.github_apk_install_notify_content_remote_ready,
                name
            )

            GitHubApkInstallPhase.Downloading -> context.getString(
                R.string.github_apk_install_notify_content_downloading,
                name
            )

            GitHubApkInstallPhase.SelectingApk -> context.getString(
                R.string.github_apk_install_notify_content_selecting,
                state.candidates.size
            )

            GitHubApkInstallPhase.InspectingLocal -> context.getString(
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
                PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun Notification.hasGitHubApkInstallFocusContract(): Boolean {
        return extras.getString("miui.focus.param").orEmpty().isNotBlank() &&
                extras.getBundle("miui.focus.actions") != null
    }
}

private val GitHubApkInstallPhase.running: Boolean
    get() = this in setOf(
        GitHubApkInstallPhase.RemoteResolving,
        GitHubApkInstallPhase.RemoteReady,
        GitHubApkInstallPhase.Downloading,
        GitHubApkInstallPhase.SelectingApk,
        GitHubApkInstallPhase.InspectingLocal,
        GitHubApkInstallPhase.ReadyToInstall,
        GitHubApkInstallPhase.Installing,
        GitHubApkInstallPhase.PendingUserAction
    )

private val GitHubApkInstallPhase.titleRes: Int
    get() = when (this) {
        GitHubApkInstallPhase.RemoteResolving -> R.string.github_apk_install_notify_title_remote_resolving
        GitHubApkInstallPhase.RemoteReady -> R.string.github_apk_install_notify_title_remote_ready
        GitHubApkInstallPhase.Downloading -> R.string.github_apk_install_notify_title_downloading
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_notify_title_selecting
        GitHubApkInstallPhase.InspectingLocal -> R.string.github_apk_install_notify_title_inspecting
        GitHubApkInstallPhase.ReadyToInstall -> R.string.github_apk_install_notify_title_review
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_notify_title_installing
        GitHubApkInstallPhase.PendingUserAction -> R.string.github_apk_install_notify_title_pending_user_action
        GitHubApkInstallPhase.Success -> R.string.github_apk_install_notify_title_success
        GitHubApkInstallPhase.Failed -> R.string.github_apk_install_notify_title_failed
        GitHubApkInstallPhase.Cancelled -> R.string.github_apk_install_notify_title_cancelled
        GitHubApkInstallPhase.Idle -> R.string.github_apk_install_notify_title_installing
    }

private val GitHubApkInstallPhase.focusAllowFloat: Boolean
    get() = this in setOf(
        GitHubApkInstallPhase.RemoteReady,
        GitHubApkInstallPhase.SelectingApk,
        GitHubApkInstallPhase.ReadyToInstall,
        GitHubApkInstallPhase.PendingUserAction,
        GitHubApkInstallPhase.Success,
        GitHubApkInstallPhase.Failed,
        GitHubApkInstallPhase.Cancelled
    )

private val GitHubApkInstallPhase.shortTextRes: Int
    get() = when (this) {
        GitHubApkInstallPhase.RemoteResolving -> R.string.github_apk_install_notify_short_resolving
        GitHubApkInstallPhase.RemoteReady -> R.string.github_apk_install_notify_short_ready
        GitHubApkInstallPhase.Downloading -> R.string.github_apk_install_notify_short_downloading
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_notify_short_selecting
        GitHubApkInstallPhase.InspectingLocal -> R.string.github_apk_install_notify_short_inspecting
        GitHubApkInstallPhase.ReadyToInstall -> R.string.github_apk_install_notify_short_review
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_notify_short_installing
        GitHubApkInstallPhase.PendingUserAction -> R.string.github_apk_install_notify_short_pending
        GitHubApkInstallPhase.Success -> R.string.github_apk_install_notify_short_success
        GitHubApkInstallPhase.Failed -> R.string.github_apk_install_notify_short_failed
        GitHubApkInstallPhase.Cancelled -> R.string.github_apk_install_notify_short_cancelled
        GitHubApkInstallPhase.Idle -> R.string.github_apk_install_notify_short_installing
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
        GitHubApkInstallPhase.RemoteResolving -> 1
        GitHubApkInstallPhase.RemoteReady -> 2
        GitHubApkInstallPhase.Downloading -> 3
        GitHubApkInstallPhase.SelectingApk -> 4
        GitHubApkInstallPhase.InspectingLocal -> 5
        GitHubApkInstallPhase.ReadyToInstall -> 6
        GitHubApkInstallPhase.Installing,
        GitHubApkInstallPhase.PendingUserAction -> 7

        GitHubApkInstallPhase.Success -> 8
        GitHubApkInstallPhase.Failed,
        GitHubApkInstallPhase.Cancelled,
        GitHubApkInstallPhase.Idle -> 0
    }
