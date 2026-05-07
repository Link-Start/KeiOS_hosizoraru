package os.kei.feature.github.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
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
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.notification.McpNotificationPayload
import os.kei.ui.page.main.github.share.GitHubShareImportActivity

private const val GITHUB_SHARE_IMPORT_MI_ISLAND_SUCCESS_COLOR = "#22C55E"
private const val GITHUB_SHARE_IMPORT_MI_ISLAND_DANGER_COLOR = "#EF4444"
private const val GITHUB_SHARE_IMPORT_MI_ISLAND_NEUTRAL_COLOR = "#64748B"

object GitHubShareImportNotificationHelper {
    const val NOTIFICATION_ID = 38991
    private const val REQUEST_OPEN_FLOW = 2301
    private const val REQUEST_OPEN_GITHUB = 2302
    private const val REQUEST_MARK_READ = 2303
    private const val REQUEST_CANCEL_IMPORT = 2304
    private const val REQUEST_REFRESH_IMPORT = 2305
    private const val REQUEST_CONFIRM_IMPORT = 2306

    fun notifyResolving(context: Context, sourceLabel: String) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Resolving,
                primaryLabel = sourceLabel
            )
        )
    }

    fun notifyAssetReady(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String,
        assetCount: Int
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.AssetReady,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                count = assetCount.coerceAtLeast(0)
            )
        )
    }

    fun notifyDelivering(
        context: Context,
        owner: String,
        repo: String,
        assetName: String,
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Delivering,
                owner = owner,
                repo = repo,
                assetName = assetName,
                targetDisplayName = targetDisplayName
            )
        )
    }

    fun notifyWaitingInstall(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String,
        assetName: String,
        packageName: String,
        remainingMinutes: Int,
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.WaitingInstall,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                packageName = packageName,
                targetDisplayName = targetDisplayName,
                count = remainingMinutes.coerceAtLeast(0)
            )
        )
    }

    fun notifyInstallDetected(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String,
        packageName: String,
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.InstallDetected,
                owner = owner,
                repo = repo,
                appLabel = appLabel,
                packageName = packageName,
                targetDisplayName = targetDisplayName
            )
        )
    }

    fun notifyAddingTrack(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String,
        packageName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.AddingTrack,
                owner = owner,
                repo = repo,
                appLabel = appLabel,
                packageName = packageName,
                targetDisplayName = targetDisplayName
            )
        )
    }

    fun notifyAdded(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String,
        packageName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Added,
                owner = owner,
                repo = repo,
                appLabel = appLabel,
                packageName = packageName,
                targetDisplayName = targetDisplayName
            )
        )
    }

    fun notifyAlreadyTracked(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String,
        packageName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.AlreadyTracked,
                owner = owner,
                repo = repo,
                appLabel = appLabel,
                packageName = packageName,
                targetDisplayName = targetDisplayName
            )
        )
    }

    fun notifyFailed(context: Context, reason: String) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Failed,
                primaryLabel = reason
            )
        )
    }

    fun notifyCancelled(context: Context) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Cancelled
            )
        )
    }

    fun cancel(context: Context) {
        McpNotificationHelper.cancelNotification(context, NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    internal fun notifyState(
        context: Context,
        state: GitHubShareImportNotificationState
    ): Boolean {
        if (!notificationsGranted(context)) return false
        McpNotificationHelper.ensureChannel(context)
        val buildResult = buildFrameworkNotificationResult(context, state)
        McpNotificationHelper.dispatchNotification(
            context = context,
            notificationId = NOTIFICATION_ID,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        return true
    }

    internal fun buildFrameworkLiveUpdateNotification(
        context: Context,
        state: GitHubShareImportNotificationState
    ): Notification {
        val helper = NotificationHelper(context)
        val payload = NotificationPayload(
            state = buildPayload(context, state),
            settings = UserSettings(miIslandOuterGlow = false),
            environment = EnvironmentContext(
                channelId = McpNotificationHelper.LIVE_CHANNEL_ID,
                isHyperOS = helper.isHyperOS,
                preferOemLiveIconLayout = helper.preferOemLiveIconLayout
            )
        )
        return if (helper.isModernLiveUpdateEligible) {
            ModernNotificationBuilder(context).build(payload)
        } else {
            LegacyNotificationBuilder(context).build(payload)
        }
    }

    internal fun buildFrameworkMiIslandNotification(
        context: Context,
        state: GitHubShareImportNotificationState
    ): Notification {
        val helper = NotificationHelper(context)
        val payload = NotificationPayload(
            state = buildPayload(context, state),
            settings = UserSettings(miIslandOuterGlow = true),
            environment = EnvironmentContext(
                channelId = helper.resolveChannel(NotificationRenderStyle.MI_ISLAND),
                isHyperOS = helper.isHyperOS,
                preferOemLiveIconLayout = helper.preferOemLiveIconLayout
            ),
            semanticIconBitmap = resolveSemanticIconBitmap(context, state),
            miIslandProgressColorOverride = state.phase.miIslandProgressColor
        )
        return MiIslandNotificationBuilder(context).build(payload)
    }

    private fun buildFrameworkNotificationResult(
        context: Context,
        state: GitHubShareImportNotificationState
    ): ShareImportNotificationBuildResult {
        val helper = NotificationHelper(context)
        val preferSuperIsland = UiPrefs.isSuperIslandNotificationEnabled(defaultValue = false)
        val useMiIsland = preferSuperIsland && helper.isSupportMiIsland
        return if (useMiIsland) {
            ShareImportNotificationBuildResult(
                notification = buildFrameworkMiIslandNotification(context, state),
                useXiaomiMagic = UiPrefs.isSuperIslandBypassRestrictionEnabled(defaultValue = false)
            )
        } else {
            ShareImportNotificationBuildResult(
                notification = buildFrameworkLiveUpdateNotification(context, state),
                useXiaomiMagic = false
            )
        }
    }

    private fun buildPayload(
        context: Context,
        state: GitHubShareImportNotificationState
    ): McpNotificationPayload {
        val liveUpdateActive = state.phase.ongoing || state.phase.promotedLiveUpdate
        val openPendingIntent = buildPrimaryPendingIntent(context, state.phase)
        val cancelImportEnabled = state.phase.cancelActionEnabled
        val secondaryPendingIntent = if (state.phase.ongoing) {
            if (cancelImportEnabled) {
                buildCancelImportPendingIntent(context)
            } else {
                openPendingIntent
            }
        } else {
            buildMarkReadPendingIntent(context)
        }
        val shortText = context.getString(state.phase.shortTextRes)
        val content = resolveContent(context, state)
        val islandTitle = state.compactIslandTitle(shortText)
        return McpNotificationPayload(
            serverName = McpNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
            running = liveUpdateActive,
            port = state.phase.progressPercent,
            path = content,
            clients = if (state.phase.ongoing) 1 else 0,
            ongoing = liveUpdateActive,
            onlyAlertOnce = true,
            openPendingIntent = openPendingIntent,
            stopPendingIntent = secondaryPendingIntent,
            focusOpenPendingIntent = openPendingIntent,
            primaryActionLabel = context.getString(state.phase.primaryActionRes),
            secondaryActionLabel = if (state.phase.ongoing) {
                if (cancelImportEnabled) {
                    context.getString(R.string.github_share_import_pending_action_cancel)
                } else {
                    ""
                }
            } else {
                context.getString(R.string.common_mark_read)
            },
            showSecondaryActionWhenStopped = true,
            overrideTitle = context.getString(state.phase.titleRes),
            overrideContent = content,
            overrideOnlineText = islandTitle.ifBlank { shortText },
            overrideShortText = shortText,
            overrideProgressPercent = state.phase.progressPercent
        )
    }

    private fun resolveSemanticIconBitmap(
        context: Context,
        state: GitHubShareImportNotificationState
    ) = state.packageName.trim()
        .takeIf { it.isNotBlank() }
        ?.let { packageName -> AppIconCache.getOrLoad(context, packageName) }

    private fun buildPrimaryPendingIntent(
        context: Context,
        phase: GitHubShareImportNotificationPhase
    ): PendingIntent {
        return when {
            phase.refreshActionEnabled -> buildRefreshImportPendingIntent(context)
            phase.confirmActionEnabled -> buildConfirmImportPendingIntent(context)
            phase.openGitHubPage -> buildOpenGitHubPendingIntent(context)
            else -> buildOpenFlowPendingIntent(context)
        }
    }

    private fun resolveContent(
        context: Context,
        state: GitHubShareImportNotificationState
    ): String {
        val projectLabel = state.projectLabel
        return when (state.phase) {
            GitHubShareImportNotificationPhase.Resolving -> context.getString(
                R.string.github_share_import_notify_content_resolving,
                state.primaryLabel.ifBlank { projectLabel }
            )

            GitHubShareImportNotificationPhase.AssetReady -> context.getString(
                R.string.github_share_import_notify_content_asset_ready,
                projectLabel,
                state.releaseTag.ifBlank { context.getString(R.string.github_asset_target_latest) },
                state.count.coerceAtLeast(0)
            )

            GitHubShareImportNotificationPhase.Delivering -> context.getString(
                R.string.github_share_import_notify_content_delivering,
                projectLabel,
                state.assetName.ifBlank { context.getString(R.string.github_share_import_pending_label_asset) }
            )

            GitHubShareImportNotificationPhase.WaitingInstall -> context.getString(
                if (state.packageName.isNotBlank()) {
                    R.string.github_share_import_notify_content_waiting_install_exact
                } else {
                    R.string.github_share_import_notify_content_waiting_install
                },
                projectLabel,
                state.packageName.ifBlank {
                    state.assetName.ifBlank {
                        context.getString(R.string.github_share_import_pending_label_asset)
                    }
                },
                state.count.coerceAtLeast(0)
            )

            GitHubShareImportNotificationPhase.InstallDetected -> context.getString(
                R.string.github_share_import_notify_content_install_detected,
                state.appDisplayLabel,
                state.packageName.ifBlank { projectLabel }
            )

            GitHubShareImportNotificationPhase.AddingTrack -> context.getString(
                R.string.github_share_import_notify_content_adding_track,
                state.appDisplayLabel,
                projectLabel
            )

            GitHubShareImportNotificationPhase.Added -> context.getString(
                R.string.github_share_import_notify_content_added,
                state.appDisplayLabel,
                projectLabel
            )

            GitHubShareImportNotificationPhase.AlreadyTracked -> context.getString(
                R.string.github_share_import_notify_content_already_tracked,
                state.appDisplayLabel,
                projectLabel
            )

            GitHubShareImportNotificationPhase.Failed -> state.primaryLabel.ifBlank {
                context.getString(R.string.github_share_import_error_resolve_failed)
            }

            GitHubShareImportNotificationPhase.Cancelled -> context.getString(
                R.string.github_share_import_notify_content_cancelled
            )
        }
    }

    private fun notificationsGranted(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun buildOpenFlowPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GitHubShareImportActivity::class.java).apply {
            action = GitHubShareImportActivity.ACTION_RESUME_SHARE_IMPORT
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
            action = GitHubShareImportActionReceiver.ACTION_MARK_READ_SHARE_IMPORT
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
            action = GitHubShareImportActionReceiver.ACTION_CANCEL_SHARE_IMPORT
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
            action = GitHubShareImportActionReceiver.ACTION_REFRESH_SHARE_IMPORT
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
            action = GitHubShareImportActionReceiver.ACTION_CONFIRM_SHARE_IMPORT
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CONFIRM_IMPORT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

private data class ShareImportNotificationBuildResult(
    val notification: Notification,
    val useXiaomiMagic: Boolean
)

internal data class GitHubShareImportNotificationState(
    val phase: GitHubShareImportNotificationPhase,
    val owner: String = "",
    val repo: String = "",
    val releaseTag: String = "",
    val assetName: String = "",
    val appLabel: String = "",
    val packageName: String = "",
    val targetDisplayName: String = "",
    val primaryLabel: String = "",
    val count: Int = 0
) {
    val projectLabel: String
        get() {
            val normalizedOwner = owner.trim()
            val normalizedRepo = repo.trim()
            return when {
                normalizedOwner.isNotBlank() && normalizedRepo.isNotBlank() ->
                    "$normalizedOwner/$normalizedRepo"

                normalizedRepo.isNotBlank() -> normalizedRepo
                normalizedOwner.isNotBlank() -> normalizedOwner
                primaryLabel.isNotBlank() -> primaryLabel
                else -> "GitHub"
            }
        }

    val appDisplayLabel: String
        get() = appLabel
            .ifBlank { targetDisplayName }
            .ifBlank { packageName }
            .ifBlank { projectLabel }

    val compactMetaLabel: String
        get() = appLabel
            .ifBlank { targetDisplayName }
            .ifBlank { packageName }
            .ifBlank {
                when {
                    owner.isNotBlank() || repo.isNotBlank() -> projectLabel
                    assetName.isNotBlank() -> assetName
                    releaseTag.isNotBlank() -> releaseTag
                    else -> primaryLabel
                }
            }

    fun compactIslandTitle(shortText: String): String {
        return shortText
    }
}

internal enum class GitHubShareImportNotificationPhase(
    @param:StringRes val titleRes: Int,
    @param:StringRes val shortTextRes: Int,
    @param:StringRes val primaryActionRes: Int,
    val progressPercent: Int,
    val ongoing: Boolean,
    val openGitHubPage: Boolean,
    val cancelActionEnabled: Boolean = false,
    val refreshActionEnabled: Boolean = false,
    val confirmActionEnabled: Boolean = false,
    val promotedLiveUpdate: Boolean = false,
    val miIslandProgressColor: String? = null
) {
    Resolving(
        titleRes = R.string.github_share_import_notify_title_resolving,
        shortTextRes = R.string.github_share_import_notify_short_resolving,
        primaryActionRes = R.string.github_share_import_notify_action_view_progress,
        progressPercent = 12,
        ongoing = true,
        openGitHubPage = false
    ),
    AssetReady(
        titleRes = R.string.github_share_import_notify_title_asset_ready,
        shortTextRes = R.string.github_share_import_notify_short_asset_ready,
        primaryActionRes = R.string.github_share_import_notify_action_select_apk,
        progressPercent = 32,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true
    ),
    Delivering(
        titleRes = R.string.github_share_import_notify_title_delivering,
        shortTextRes = R.string.github_share_import_notify_short_delivering,
        primaryActionRes = R.string.github_share_import_notify_action_view_progress,
        progressPercent = 52,
        ongoing = true,
        openGitHubPage = false
    ),
    WaitingInstall(
        titleRes = R.string.github_share_import_notify_title_waiting_install,
        shortTextRes = R.string.github_share_import_notify_short_waiting_install,
        primaryActionRes = R.string.github_share_import_notify_action_view_status,
        progressPercent = 72,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true,
        refreshActionEnabled = true
    ),
    InstallDetected(
        titleRes = R.string.github_share_import_notify_title_install_detected,
        shortTextRes = R.string.github_share_import_notify_short_install_detected,
        primaryActionRes = R.string.github_share_import_notify_action_confirm_track,
        progressPercent = 86,
        ongoing = true,
        openGitHubPage = false,
        cancelActionEnabled = true,
        confirmActionEnabled = true
    ),
    AddingTrack(
        titleRes = R.string.github_share_import_notify_title_adding_track,
        shortTextRes = R.string.github_share_import_notify_short_adding_track,
        primaryActionRes = R.string.github_share_import_notify_action_view_progress,
        progressPercent = 94,
        ongoing = true,
        openGitHubPage = false
    ),
    Added(
        titleRes = R.string.github_share_import_notify_title_added,
        shortTextRes = R.string.github_share_import_notify_short_added,
        primaryActionRes = R.string.github_share_import_notify_action_view_tracking,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_SUCCESS_COLOR
    ),
    AlreadyTracked(
        titleRes = R.string.github_share_import_notify_title_already_tracked,
        shortTextRes = R.string.github_share_import_notify_short_already_tracked,
        primaryActionRes = R.string.github_share_import_notify_action_view_tracking,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_SUCCESS_COLOR
    ),
    Failed(
        titleRes = R.string.github_share_import_notify_title_failed,
        shortTextRes = R.string.github_share_import_notify_short_failed,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_DANGER_COLOR
    ),
    Cancelled(
        titleRes = R.string.github_share_import_notify_title_cancelled,
        shortTextRes = R.string.github_share_import_notify_short_cancelled,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        progressPercent = 100,
        ongoing = false,
        openGitHubPage = true,
        promotedLiveUpdate = true,
        miIslandProgressColor = GITHUB_SHARE_IMPORT_MI_ISLAND_NEUTRAL_COLOR
    )
}
