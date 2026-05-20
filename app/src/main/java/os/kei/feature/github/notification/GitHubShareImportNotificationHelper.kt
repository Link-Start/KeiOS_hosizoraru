package os.kei.feature.github.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.Formatter
import os.kei.R
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

object GitHubShareImportNotificationHelper {
    const val NOTIFICATION_ID = 38991

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
        assetCount: Int,
        sendInstallActionEnabled: Boolean = false
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.AssetReady,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                count = assetCount.coerceAtLeast(0),
                sendInstallActionEnabled = sendInstallActionEnabled
            )
        )
    }

    fun notifyDelivering(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String = "",
        assetName: String,
        versionName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Delivering,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                versionName = versionName,
                targetDisplayName = targetDisplayName
            )
        )
    }

    fun notifyInstalling(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String = "",
        assetName: String,
        progressPercent: Int,
        appLabel: String = "",
        packageName: String = "",
        versionName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Installing,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                appLabel = appLabel,
                packageName = packageName,
                versionName = versionName,
                targetDisplayName = targetDisplayName,
                progressPercentOverride = progressPercent.coerceIn(0, 100)
            )
        )
    }

    fun notifyInstallDownloading(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String = "",
        assetName: String,
        progressPercent: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        appLabel: String = "",
        packageName: String = "",
        versionName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.InstallDownloading,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                appLabel = appLabel,
                packageName = packageName,
                versionName = versionName,
                targetDisplayName = targetDisplayName,
                progressPercentOverride = progressPercent.coerceIn(0, 100),
                downloadedBytes = downloadedBytes.coerceAtLeast(0L),
                totalBytes = totalBytes
            )
        )
    }

    fun notifyInstallCommitting(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String = "",
        assetName: String,
        appLabel: String = "",
        packageName: String = "",
        versionName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.InstallCommitting,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                appLabel = appLabel,
                packageName = packageName,
                versionName = versionName,
                targetDisplayName = targetDisplayName
            )
        )
    }

    fun notifyInstallReady(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String = "",
        assetName: String,
        appLabel: String = "",
        packageName: String = "",
        versionName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.InstallReady,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                appLabel = appLabel,
                packageName = packageName,
                versionName = versionName,
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
        versionName: String = "",
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
                versionName = versionName,
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
        versionName: String = "",
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
                versionName = versionName,
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
        versionName: String = "",
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
                versionName = versionName,
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
        versionName: String = "",
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
                versionName = versionName,
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
        versionName: String = "",
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
                versionName = versionName,
                targetDisplayName = targetDisplayName
            )
        )
    }

    fun notifyPageInstallConfirm(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String = "",
        assetName: String = "",
        appLabel: String = "",
        packageName: String = "",
        versionName: String = "",
        targetDisplayName: String = "",
        confirmActionEnabled: Boolean = false
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.PageInstallConfirm,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                appLabel = appLabel,
                packageName = packageName,
                versionName = versionName,
                targetDisplayName = targetDisplayName,
                pageInstallConfirmActionEnabled = confirmActionEnabled
            )
        )
    }

    fun notifyPageInstallCompleted(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String = "",
        assetName: String = "",
        appLabel: String = "",
        packageName: String = "",
        versionName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.PageInstallCompleted,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                appLabel = appLabel,
                packageName = packageName,
                versionName = versionName,
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

    fun notifyPageInstallFailed(
        context: Context,
        reason: String,
        owner: String = "",
        repo: String = "",
        packageName: String = "",
        targetDisplayName: String = ""
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.PageInstallFailed,
                owner = owner,
                repo = repo,
                packageName = packageName,
                targetDisplayName = targetDisplayName,
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
            ),
            semanticIconBitmap = resolveSemanticIconBitmap(context, state)
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
        val openPendingIntent = GitHubShareImportNotificationActions.buildOpenPendingIntent(
            context = context,
            state = state
        )
        val primaryActionLabel = context.getString(
            GitHubShareImportNotificationActions.primaryActionLabelRes(state)
        )
        val secondaryPendingIntent =
            GitHubShareImportNotificationActions.buildSecondaryPendingIntent(
            context = context,
            state = state,
            openPendingIntent = openPendingIntent
        )
        val shortText = context.getString(state.phase.shortTextRes)
        val content = resolveContent(context, state)
        val islandTitle = state.compactIslandTitle(shortText)
        val islandSubtitle = state.compactIslandSubtitle(shortText, islandTitle)
        val progressPercent = state.resolvedProgressPercent
        val overrideProgressPercent = progressPercent.takeIf {
            state.phase.progressTemplateEnabled
        }
        return McpNotificationPayload(
            serverName = McpNotificationPayload.GITHUB_SHARE_IMPORT_SERVER_NAME,
            running = liveUpdateActive,
            port = progressPercent,
            path = content,
            clients = if (state.phase.ongoing && state.phase.progressTemplateEnabled) 1 else 0,
            ongoing = liveUpdateActive,
            onlyAlertOnce = true,
            openPendingIntent = openPendingIntent,
            stopPendingIntent = secondaryPendingIntent,
            focusOpenPendingIntent = openPendingIntent,
            primaryActionLabel = primaryActionLabel,
            secondaryActionLabel = GitHubShareImportNotificationActions.secondaryActionLabel(
                context,
                state
            ),
            showSecondaryActionWhenStopped = true,
            overrideTitle = context.getString(state.phase.titleRes),
            overrideContent = content,
            overrideOnlineText = islandTitle.ifBlank { shortText },
            overrideShortText = islandSubtitle.ifBlank { shortText },
            overrideProgressPercent = overrideProgressPercent,
            notificationId = NOTIFICATION_ID,
            miFocusOrderId = "github_share_import"
        )
    }

    private fun resolveSemanticIconBitmap(
        context: Context,
        state: GitHubShareImportNotificationState
    ) = state.packageName.trim()
        .takeIf { it.isNotBlank() }
        ?.let { packageName -> AppIconCache.getOrLoad(context, packageName) }

    private fun resolveContent(
        context: Context,
        state: GitHubShareImportNotificationState
    ): String {
        val projectLabel = state.projectLabel
        val projectDisplayLabel = state.projectDisplayLabel
        val targetLabel = state.targetWithVersionLabel
        return when (state.phase) {
            GitHubShareImportNotificationPhase.Resolving -> context.getString(
                R.string.github_share_import_notify_content_resolving,
                state.primaryLabel.ifBlank { projectDisplayLabel }
            )

            GitHubShareImportNotificationPhase.AssetReady -> context.getString(
                R.string.github_share_import_notify_content_asset_ready,
                projectDisplayLabel,
                state.releaseTag.ifBlank { context.getString(R.string.github_asset_target_latest) },
                state.count.coerceAtLeast(0)
            )

            GitHubShareImportNotificationPhase.Delivering -> context.getString(
                R.string.github_share_import_notify_content_delivering,
                projectDisplayLabel,
                targetLabel
            )

            GitHubShareImportNotificationPhase.InstallDownloading -> context.getString(
                R.string.github_share_import_notify_content_install_downloading,
                targetLabel,
                formatDownloadProgress(context, state.downloadedBytes, state.totalBytes)
            )

            GitHubShareImportNotificationPhase.Installing -> context.getString(
                R.string.github_share_import_notify_content_installing,
                targetLabel
            )

            GitHubShareImportNotificationPhase.InstallReady -> context.getString(
                R.string.github_share_import_notify_content_install_ready,
                targetLabel
            )

            GitHubShareImportNotificationPhase.InstallCommitting -> context.getString(
                R.string.github_share_import_notify_content_install_committing,
                targetLabel
            )

            GitHubShareImportNotificationPhase.WaitingInstall -> context.getString(
                if (state.packageName.isNotBlank()) {
                    R.string.github_share_import_notify_content_waiting_install_exact
                } else {
                    R.string.github_share_import_notify_content_waiting_install
                },
                targetLabel,
                state.count.coerceAtLeast(0)
            )

            GitHubShareImportNotificationPhase.InstallDetected -> context.getString(
                R.string.github_share_import_notify_content_install_detected,
                state.appDisplayLabel,
                state.installDetectedProjectLabel
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

            GitHubShareImportNotificationPhase.PageInstallConfirm -> context.getString(
                R.string.github_page_install_notify_content_confirm,
                state.appDisplayLabel,
                projectLabel
            )

            GitHubShareImportNotificationPhase.PageInstallCompleted -> context.getString(
                R.string.github_page_install_notify_content_completed,
                state.appDisplayLabel,
                projectLabel
            )

            GitHubShareImportNotificationPhase.Failed -> state.primaryLabel.ifBlank {
                context.getString(R.string.github_share_import_error_resolve_failed)
            }

            GitHubShareImportNotificationPhase.PageInstallFailed -> state.primaryLabel.ifBlank {
                context.getString(R.string.github_share_import_error_app_managed_install_failed)
            }

            GitHubShareImportNotificationPhase.Cancelled -> context.getString(
                R.string.github_share_import_notify_content_cancelled
            )
        }
    }

    private fun formatDownloadProgress(
        context: Context,
        downloadedBytes: Long,
        totalBytes: Long
    ): String {
        val downloaded = Formatter.formatFileSize(context, downloadedBytes.coerceAtLeast(0L))
        if (totalBytes > 0L) {
            return context.getString(
                R.string.github_share_import_notify_download_progress_known,
                downloaded,
                Formatter.formatFileSize(context, totalBytes)
            )
        }
        return context.getString(
            R.string.github_share_import_notify_download_progress_unknown,
            downloaded
        )
    }

    private fun notificationsGranted(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}

private data class ShareImportNotificationBuildResult(
    val notification: Notification,
    val useXiaomiMagic: Boolean
)
