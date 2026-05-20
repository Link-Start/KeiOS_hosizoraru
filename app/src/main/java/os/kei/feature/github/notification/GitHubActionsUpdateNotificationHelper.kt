package os.kei.feature.github.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import os.kei.core.notification.focus.MiFocusPictureRef
import os.kei.core.notification.focus.MiFocusPictureSource
import os.kei.core.prefs.UiPrefs
import os.kei.feature.github.data.local.AppIconCache
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.notification.MiFocusNotificationActions
import os.kei.mcp.framework.notification.NotificationHelper
import os.kei.mcp.notification.McpNotificationHelper

object GitHubActionsUpdateNotificationHelper {
    const val NOTIFICATION_ID = 38991
    private const val NOTIFICATION_ID_BASE = 389_910_000
    private const val NOTIFICATION_ID_RANGE = 1_000_000
    private const val GITHUB_ACTIONS_COLOR = "#3B82F6"
    private val ICON_RES_ID = R.drawable.ic_github_invertocat_island_blue

    fun notifyUpdateAvailable(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        onlyAlertOnce: Boolean = false,
    ): Boolean {
        if (!notificationsGranted(context)) return false
        GitHubRefreshNotificationHelper.ensureChannel(context)
        val notificationId = notificationId(snapshot)
        val buildResult = buildNotification(context, snapshot, onlyAlertOnce, notificationId)
        McpNotificationHelper.dispatchNotification(
            context = context,
            notificationId = notificationId,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic,
        )
        return true
    }

    internal fun notificationId(snapshot: GitHubActionsRecommendedRunSnapshot): Int {
        val key =
            snapshot.trackId
                .ifBlank { "${snapshot.owner}/${snapshot.repo}" }
                .trim()
        return NOTIFICATION_ID_BASE + Math.floorMod(key.hashCode(), NOTIFICATION_ID_RANGE)
    }

    private data class NotificationBuildResult(
        val notification: Notification,
        val useXiaomiMagic: Boolean,
    )

    private fun buildNotification(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        onlyAlertOnce: Boolean,
        notificationId: Int,
    ): NotificationBuildResult {
        val helper = NotificationHelper(context)
        val preferSuperIsland = UiPrefs.isSuperIslandNotificationEnabled(defaultValue = false)
        val useMiIsland = preferSuperIsland && helper.isSupportMiIsland
        val notification =
            if (useMiIsland) {
                buildMiIslandNotification(context, snapshot, onlyAlertOnce, notificationId)
            } else {
                buildFrameworkNotification(context, snapshot, onlyAlertOnce, notificationId)
            }
        return NotificationBuildResult(
            notification = notification,
            useXiaomiMagic =
                useMiIsland &&
                    UiPrefs.isSuperIslandBypassRestrictionEnabled(defaultValue = false),
        )
    }

    private fun buildFrameworkNotification(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        onlyAlertOnce: Boolean,
        notificationId: Int,
    ): Notification {
        val title = title(context)
        val content = content(context, snapshot)
        val openPendingIntent = buildOpenPendingIntent(context, snapshot, notificationId)
        val markReadPendingIntent = buildMarkReadPendingIntent(context, notificationId)
        val appIconBitmap = resolveTrackedAppIconBitmap(context, snapshot)
        return NotificationCompat
            .Builder(context, GitHubRefreshNotificationHelper.CHANNEL_ID)
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
            .setOngoing(false)
            .setSilent(onlyAlertOnce)
            .setShortCriticalText(snapshot.runLabel)
            .setDeleteIntent(markReadPendingIntent)
            .addAction(0, context.getString(R.string.common_open), openPendingIntent)
            .addAction(
                0,
                context.getString(R.string.common_mark_read),
                markReadPendingIntent,
            ).apply {
                appIconBitmap?.let(::setLargeIcon)
            }.build()
    }

    private fun buildMiIslandNotification(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        onlyAlertOnce: Boolean,
        notificationId: Int,
    ): Notification {
        val title = title(context)
        val content = content(context, snapshot)
        val openPendingIntent = buildOpenPendingIntent(context, snapshot, notificationId)
        val markReadPendingIntent = buildMarkReadPendingIntent(context, notificationId)
        val appIconBitmap = resolveTrackedAppIconBitmap(context, snapshot)
        val builder =
            NotificationCompat
                .Builder(context, GitHubRefreshNotificationHelper.CHANNEL_ID)
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
                .setOngoing(false)
                .setSilent(onlyAlertOnce)
                .setRequestPromotedOngoing(false)
                .setShortCriticalText(snapshot.runLabel)
                .setDeleteIntent(markReadPendingIntent)
                .addAction(0, context.getString(R.string.common_open), openPendingIntent)
                .addAction(
                    0,
                    context.getString(R.string.common_mark_read),
                    markReadPendingIntent,
                ).apply {
                    appIconBitmap?.let(::setLargeIcon)
                }

        runCatching {
            val trackedAppPicture = appIconBitmap?.let(MiFocusPictureSource::BitmapValue)
            MiFocusNotificationTemplate.build(
                context = context,
                spec =
                    MiFocusNotificationSpec(
                        title = title,
                        content = content,
                        displayIconResId = ICON_RES_ID,
                        displayPictureSource =
                            trackedAppPicture
                                ?: MiFocusPictureSource.Resource(ICON_RES_ID),
                        expandedPictureSource =
                            trackedAppPicture
                                ?: MiFocusPictureSource.Resource(ICON_RES_ID),
                        island =
                            MiFocusIslandSpec.summaryText(
                                title = snapshot.runLabel,
                            ),
                        expanded =
                            MiFocusExpandedSpec(
                                components =
                                    listOf(
                                        MiFocusExpandedComponent.Base(
                                            text =
                                                MiFocusExpandedText(
                                                    title = title,
                                                    content = content.ifBlank { " " },
                                                ),
                                        ),
                                        MiFocusExpandedComponent.Picture(
                                            pic = MiFocusPictureRef.Expanded,
                                            picDark = MiFocusPictureRef.Expanded,
                                            type = 1,
                                        ),
                                        MiFocusExpandedComponent.TextButtons(
                                            actions =
                                                listOf(
                                                    MiFocusNotificationAction(
                                                        key = "github_actions_update_open",
                                                        title = context.getString(R.string.common_open),
                                                        pendingIntent = openPendingIntent,
                                                        isHighlighted = true,
                                                        backgroundColor = GITHUB_ACTIONS_COLOR,
                                                        backgroundColorDark = GITHUB_ACTIONS_COLOR,
                                                        collapsePanel = true,
                                                    ),
                                                    MiFocusNotificationAction(
                                                        key = "github_actions_update_read",
                                                        title = context.getString(R.string.common_mark_read),
                                                        pendingIntent = markReadPendingIntent,
                                                        collapsePanel = true,
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        allowFloat = true,
                        islandFirstFloat = true,
                        updatable = true,
                        outerGlow = true,
                        ticker = title,
                        compactTicker = snapshot.runLabel,
                        notifyId = notificationId.toString(),
                        orderId = snapshot.trackId,
                    ),
            )
        }.getOrNull()?.let(builder::addExtras)
        return builder.build()
    }

    private fun resolveTrackedAppIconBitmap(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
    ): Bitmap? {
        val packageName =
            snapshot.trackId
                .substringAfter('|', missingDelimiterValue = "")
                .trim()
        if (packageName.isBlank()) return null
        return AppIconCache.getOrLoad(context, packageName)
    }

    private fun title(context: Context): String = context.getString(R.string.github_actions_update_notification_title)

    private fun content(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
    ): String =
        context.getString(
            R.string.github_actions_update_notification_content,
            snapshot.appLabel.ifBlank { "${snapshot.owner}/${snapshot.repo}" },
            snapshot.runLabel,
            snapshot.workflowName.ifBlank { snapshot.workflowPath },
        )

    private fun buildOpenPendingIntent(
        context: Context,
        snapshot: GitHubActionsRecommendedRunSnapshot,
        notificationId: Int,
    ): PendingIntent {
        val openIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                putExtra(MainActivity.EXTRA_TARGET_BOTTOM_PAGE, MainActivity.TARGET_BOTTOM_PAGE_GITHUB)
                putExtra(MainActivity.EXTRA_GITHUB_ACTIONS_TRACK_ID, snapshot.trackId)
            }
        return PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildMarkReadPendingIntent(
        context: Context,
        notificationId: Int,
    ): PendingIntent = MiFocusNotificationActions.markReadPendingIntent(context, notificationId)

    @SuppressLint("InlinedApi")
    private fun notificationsGranted(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
