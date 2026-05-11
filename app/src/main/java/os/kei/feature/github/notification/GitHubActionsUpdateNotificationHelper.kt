package os.kei.feature.github.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.core.notification.focus.MiFocusExpandedComponent
import os.kei.core.notification.focus.MiFocusExpandedSpec
import os.kei.core.notification.focus.MiFocusExpandedText
import os.kei.core.notification.focus.MiFocusIslandSpec
import os.kei.core.notification.focus.MiFocusNotificationAction
import os.kei.core.notification.focus.MiFocusNotificationSpec
import os.kei.core.notification.focus.MiFocusNotificationTemplate
import os.kei.core.prefs.UiPrefs
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.notification.NotificationActionReceiver
import os.kei.mcp.framework.notification.NotificationHelper
import os.kei.mcp.notification.McpNotificationHelper

object GitHubActionsUpdateNotificationHelper {
    const val NOTIFICATION_ID = 38991
    private const val GITHUB_ACTIONS_COLOR = "#3B82F6"
    private val ICON_RES_ID = R.drawable.ic_github_invertocat_island_blue

    fun notifyUpdateAvailable(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        onlyAlertOnce: Boolean = false
    ): Boolean {
        if (!notificationsGranted(context)) return false
        GitHubRefreshNotificationHelper.ensureChannel(context)
        val buildResult = buildNotification(context, snapshot, onlyAlertOnce)
        McpNotificationHelper.dispatchNotification(
            context = context,
            notificationId = NOTIFICATION_ID,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        return true
    }

    fun notifyDebug(context: Context): Boolean {
        val snapshot = GitHubActionsRecommendedRunSnapshot(
            trackId = "debug/actions|os.kei.debug",
            owner = "debug",
            repo = "actions",
            appLabel = context.getString(R.string.github_actions_update_debug_app_label),
            workflowId = 1424L,
            workflowName = context.getString(R.string.github_actions_update_debug_workflow),
            workflowPath = ".github/workflows/android.yml",
            runId = 1424L,
            runNumber = 1424L,
            runAttempt = 1,
            runDisplayName = context.getString(R.string.github_actions_update_debug_run_name),
            headBranch = "main",
            headSha = "debug",
            event = "workflow_dispatch",
            status = "completed",
            conclusion = "success",
            htmlUrl = "",
            artifactCount = 2,
            androidArtifactCount = 1,
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
            checkedAtMillis = System.currentTimeMillis()
        )
        return notifyUpdateAvailable(context, snapshot)
    }

    private data class NotificationBuildResult(
        val notification: Notification,
        val useXiaomiMagic: Boolean
    )

    private fun buildNotification(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        onlyAlertOnce: Boolean
    ): NotificationBuildResult {
        val helper = NotificationHelper(context)
        val preferSuperIsland = UiPrefs.isSuperIslandNotificationEnabled(defaultValue = false)
        val useMiIsland = preferSuperIsland && helper.isSupportMiIsland
        val notification = if (useMiIsland) {
            buildMiIslandNotification(context, snapshot, onlyAlertOnce)
        } else {
            buildFrameworkNotification(context, snapshot, onlyAlertOnce)
        }
        return NotificationBuildResult(
            notification = notification,
            useXiaomiMagic = useMiIsland &&
                    UiPrefs.isSuperIslandBypassRestrictionEnabled(defaultValue = false)
        )
    }

    private fun buildFrameworkNotification(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        onlyAlertOnce: Boolean
    ): Notification {
        val title = title(context)
        val content = content(context, snapshot)
        val openPendingIntent = buildOpenPendingIntent(context)
        return NotificationCompat.Builder(context, GitHubRefreshNotificationHelper.CHANNEL_ID)
            .setSmallIcon(ICON_RES_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColorized(true)
            .setColor(GITHUB_ACTIONS_COLOR.toColorInt())
            .setOnlyAlertOnce(onlyAlertOnce)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSilent(onlyAlertOnce)
            .setShortCriticalText(snapshot.runLabel)
            .addAction(0, context.getString(R.string.common_open), openPendingIntent)
            .addAction(
                0,
                context.getString(R.string.common_mark_read),
                buildMarkReadPendingIntent(context)
            )
            .build()
    }

    private fun buildMiIslandNotification(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        onlyAlertOnce: Boolean
    ): Notification {
        val title = title(context)
        val content = content(context, snapshot)
        val openPendingIntent = buildOpenPendingIntent(context)
        val builder =
            NotificationCompat.Builder(context, GitHubRefreshNotificationHelper.CHANNEL_ID)
                .setSmallIcon(ICON_RES_ID)
                .setContentTitle(title)
                .setContentText(content.ifBlank { " " })
                .setContentIntent(openPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setColorized(true)
                .setColor(GITHUB_ACTIONS_COLOR.toColorInt())
                .setOnlyAlertOnce(onlyAlertOnce)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSilent(onlyAlertOnce)
                .setRequestPromotedOngoing(true)
                .setShortCriticalText(snapshot.runLabel)
                .addAction(0, context.getString(R.string.common_open), openPendingIntent)
                .addAction(
                    0,
                    context.getString(R.string.common_mark_read),
                    buildMarkReadPendingIntent(context)
                )

        runCatching {
            MiFocusNotificationTemplate.build(
                context = context,
                spec = MiFocusNotificationSpec(
                    title = title,
                    content = content,
                    displayIconResId = ICON_RES_ID,
                    island = MiFocusIslandSpec.summaryText(
                        title = snapshot.runLabel,
                        content = context.getString(R.string.github_actions_update_island_content)
                    ),
                    expanded = MiFocusExpandedSpec(
                        components = listOf(
                            MiFocusExpandedComponent.Base(
                                text = MiFocusExpandedText(
                                    title = title,
                                    content = content.ifBlank { " " }
                                )
                            ),
                            MiFocusExpandedComponent.TextButtons(
                                actions = listOf(
                                    MiFocusNotificationAction(
                                        key = "github_actions_update_open",
                                        title = context.getString(R.string.common_open),
                                        pendingIntent = openPendingIntent
                                    ),
                                    MiFocusNotificationAction(
                                        key = "github_actions_update_read",
                                        title = context.getString(R.string.common_mark_read),
                                        pendingIntent = buildMarkReadPendingIntent(context)
                                    )
                                )
                            )
                        )
                    ),
                    allowFloat = true,
                    islandFirstFloat = true,
                    updatable = true,
                    outerGlow = true,
                    ticker = title,
                    compactTicker = snapshot.runLabel,
                    notifyId = NOTIFICATION_ID.toString(),
                    orderId = snapshot.trackId
                )
            )
        }.getOrNull()?.let(builder::addExtras)
        return builder.build()
    }

    private fun title(context: Context): String {
        return context.getString(R.string.github_actions_update_notification_title)
    }

    private fun content(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot
    ): String {
        return context.getString(
            R.string.github_actions_update_notification_content,
            snapshot.appLabel.ifBlank { "${snapshot.owner}/${snapshot.repo}" },
            snapshot.runLabel,
            snapshot.workflowName.ifBlank { snapshot.workflowPath }
        )
    }

    private fun buildOpenPendingIntent(context: Context): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_GITHUB)
        }
        return PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            39091,
            openIntent,
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
            39092,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("InlinedApi")
    private fun notificationsGranted(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
