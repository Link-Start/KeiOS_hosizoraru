package os.kei.feature.github.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.feature.notification.NotificationActionReceiver
import os.kei.mcp.framework.notification.NotificationHelper
import os.kei.ui.page.main.github.share.GitHubShareImportActivity

object GitHubShareImportNotificationHelper {
    const val CHANNEL_ID = "github_share_import_channel_v1"
    const val NOTIFICATION_ID = 38991
    private const val ICON_RES_ID = R.drawable.ic_github_invertocat_island_blue
    private const val REQUEST_OPEN_FLOW = 2301
    private const val REQUEST_OPEN_GITHUB = 2302
    private const val REQUEST_MARK_READ = 2303

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.github_share_import_notify_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.github_share_import_notify_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

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
        assetName: String
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Delivering,
                owner = owner,
                repo = repo,
                assetName = assetName
            )
        )
    }

    fun notifyWaitingInstall(
        context: Context,
        owner: String,
        repo: String,
        releaseTag: String,
        assetName: String,
        remainingMinutes: Int
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.WaitingInstall,
                owner = owner,
                repo = repo,
                releaseTag = releaseTag,
                assetName = assetName,
                count = remainingMinutes.coerceAtLeast(0)
            )
        )
    }

    fun notifyInstallDetected(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String,
        packageName: String
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.InstallDetected,
                owner = owner,
                repo = repo,
                appLabel = appLabel,
                packageName = packageName
            )
        )
    }

    fun notifyAddingTrack(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.AddingTrack,
                owner = owner,
                repo = repo,
                appLabel = appLabel
            )
        )
    }

    fun notifyAdded(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Added,
                owner = owner,
                repo = repo,
                appLabel = appLabel
            )
        )
    }

    fun notifyAlreadyTracked(
        context: Context,
        owner: String,
        repo: String,
        appLabel: String
    ) {
        notifyState(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.AlreadyTracked,
                owner = owner,
                repo = repo,
                appLabel = appLabel
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
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    internal fun notifyState(
        context: Context,
        state: GitHubShareImportNotificationState
    ): Boolean {
        if (!notificationsGranted(context)) return false
        ensureChannel(context)
        val helper = NotificationHelper(context)
        val notification = if (helper.isModernLiveUpdateEligible) {
            buildModernLiveUpdateNotification(context, state)
        } else {
            buildLegacyLiveUpdateNotification(context, state)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    internal fun buildModernLiveUpdateNotification(
        context: Context,
        state: GitHubShareImportNotificationState
    ): Notification {
        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(100)
                        .setColor(state.phase.progressColor)
                )
            )
            .setStyledByProgress(true)
            .setProgress(state.phase.progressPercent)
        return baseBuilder(context, state)
            .setRequestPromotedOngoing(state.phase.ongoing)
            .setStyle(progressStyle)
            .setShortCriticalText(context.getString(state.phase.shortTextRes))
            .build()
    }

    internal fun buildLegacyLiveUpdateNotification(
        context: Context,
        state: GitHubShareImportNotificationState
    ): Notification {
        return baseBuilder(context, state)
            .setColorized(true)
            .setColor(state.phase.progressColor)
            .setSubText(context.getString(state.phase.shortTextRes))
            .setProgress(100, state.phase.progressPercent, false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun baseBuilder(
        context: Context,
        state: GitHubShareImportNotificationState
    ): NotificationCompat.Builder {
        val openPendingIntent = if (state.phase.openGitHubPage) {
            buildOpenGitHubPendingIntent(context)
        } else {
            buildOpenFlowPendingIntent(context)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(ICON_RES_ID)
            .setContentTitle(context.getString(state.phase.titleRes))
            .setContentText(resolveContent(context, state))
            .setContentIntent(openPendingIntent)
            .setCategory(
                if (state.phase.ongoing) {
                    NotificationCompat.CATEGORY_PROGRESS
                } else {
                    NotificationCompat.CATEGORY_STATUS
                }
            )
            .setOngoing(state.phase.ongoing)
            .setOnlyAlertOnce(state.phase.ongoing)
            .setSilent(state.phase.ongoing)
            .setAutoCancel(false)
            .addAction(0, context.getString(R.string.common_open), openPendingIntent)
        if (!state.phase.ongoing) {
            builder.addAction(
                0,
                context.getString(R.string.common_acknowledge),
                buildMarkReadPendingIntent(context)
            )
        }
        return builder
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
                R.string.github_share_import_notify_content_waiting_install,
                projectLabel,
                state.assetName.ifBlank { context.getString(R.string.github_share_import_pending_label_asset) },
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
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
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
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_MARK_READ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

internal data class GitHubShareImportNotificationState(
    val phase: GitHubShareImportNotificationPhase,
    val owner: String = "",
    val repo: String = "",
    val releaseTag: String = "",
    val assetName: String = "",
    val appLabel: String = "",
    val packageName: String = "",
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
        get() = appLabel.ifBlank { packageName }.ifBlank { projectLabel }
}

internal enum class GitHubShareImportNotificationPhase(
    @param:StringRes val titleRes: Int,
    @param:StringRes val shortTextRes: Int,
    val progressPercent: Int,
    val progressColor: Int,
    val ongoing: Boolean,
    val openGitHubPage: Boolean
) {
    Resolving(
        titleRes = R.string.github_share_import_notify_title_resolving,
        shortTextRes = R.string.github_share_import_notify_short_resolving,
        progressPercent = 12,
        progressColor = 0xFF2563EB.toInt(),
        ongoing = true,
        openGitHubPage = false
    ),
    AssetReady(
        titleRes = R.string.github_share_import_notify_title_asset_ready,
        shortTextRes = R.string.github_share_import_notify_short_asset_ready,
        progressPercent = 32,
        progressColor = 0xFF7C3AED.toInt(),
        ongoing = false,
        openGitHubPage = false
    ),
    Delivering(
        titleRes = R.string.github_share_import_notify_title_delivering,
        shortTextRes = R.string.github_share_import_notify_short_delivering,
        progressPercent = 52,
        progressColor = 0xFF2563EB.toInt(),
        ongoing = true,
        openGitHubPage = false
    ),
    WaitingInstall(
        titleRes = R.string.github_share_import_notify_title_waiting_install,
        shortTextRes = R.string.github_share_import_notify_short_waiting_install,
        progressPercent = 72,
        progressColor = 0xFFF59E0B.toInt(),
        ongoing = true,
        openGitHubPage = false
    ),
    InstallDetected(
        titleRes = R.string.github_share_import_notify_title_install_detected,
        shortTextRes = R.string.github_share_import_notify_short_install_detected,
        progressPercent = 86,
        progressColor = 0xFF059669.toInt(),
        ongoing = true,
        openGitHubPage = false
    ),
    AddingTrack(
        titleRes = R.string.github_share_import_notify_title_adding_track,
        shortTextRes = R.string.github_share_import_notify_short_adding_track,
        progressPercent = 94,
        progressColor = 0xFF2563EB.toInt(),
        ongoing = true,
        openGitHubPage = false
    ),
    Added(
        titleRes = R.string.github_share_import_notify_title_added,
        shortTextRes = R.string.github_share_import_notify_short_added,
        progressPercent = 100,
        progressColor = 0xFF059669.toInt(),
        ongoing = false,
        openGitHubPage = true
    ),
    AlreadyTracked(
        titleRes = R.string.github_share_import_notify_title_already_tracked,
        shortTextRes = R.string.github_share_import_notify_short_already_tracked,
        progressPercent = 100,
        progressColor = 0xFF64748B.toInt(),
        ongoing = false,
        openGitHubPage = true
    ),
    Failed(
        titleRes = R.string.github_share_import_notify_title_failed,
        shortTextRes = R.string.github_share_import_notify_short_failed,
        progressPercent = 100,
        progressColor = 0xFFDC2626.toInt(),
        ongoing = false,
        openGitHubPage = false
    ),
    Cancelled(
        titleRes = R.string.github_share_import_notify_title_cancelled,
        shortTextRes = R.string.github_share_import_notify_short_cancelled,
        progressPercent = 100,
        progressColor = 0xFF64748B.toInt(),
        ongoing = false,
        openGitHubPage = false
    )
}
