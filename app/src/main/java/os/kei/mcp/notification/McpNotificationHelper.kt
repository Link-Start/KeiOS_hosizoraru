package os.kei.mcp.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.PendingIntentLaunchOptionsCompat
import os.kei.core.log.AppLogger
import os.kei.feature.notification.MiFocusNotificationActions
import os.kei.feature.notification.NotificationActionReceiver
import os.kei.mcp.domain.notification.SessionNotifier
import os.kei.mcp.framework.notification.NotificationHelper
import os.kei.mcp.framework.notification.SessionNotifierImpl
import os.kei.mcp.service.McpKeepAliveService

object McpNotificationHelper {
    private const val TAG = "McpNotificationHelper"
    const val CHANNEL_ID = "mcp_keepalive_channel_v2"
    const val LIVE_CHANNEL_ID = "mcp_live_update_channel_v1"
    const val FOREGROUND_SERVICE_CHANNEL_ID = "mcp_keepalive_service_channel_v1"
    const val KEEPALIVE_FOREGROUND_NOTIFICATION_ID = 38887
    const val KEEPALIVE_NOTIFICATION_ID = 38888
    const val BA_AP_NOTIFICATION_ID = 38889
    const val BA_CAFE_VISIT_NOTIFICATION_ID = 38890
    const val BA_ARENA_REFRESH_NOTIFICATION_ID = 38891
    const val BA_CAFE_AP_NOTIFICATION_ID = 38892
    private const val TEST_NOTIFICATION_ID = KEEPALIVE_NOTIFICATION_ID
    private const val ACTION_DISMISS = "os.kei.mcp.keepalive.DISMISS"
    private const val EXTRA_NOTIFICATION_ID = "notification_id"
    private enum class SecondaryActionMode {
        DEFAULT,
        MARK_READ
    }

    fun ensureChannel(context: Context) {
        McpNotificationChannels.ensure(context)
    }

    fun refreshCurrentNotificationStyle(context: Context) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        McpNotificationSnapshotStore.entries().forEach { (notificationId, snapshot) ->
            refreshCachedNotificationIfActive(
                context = context,
                manager = manager,
                notificationId = notificationId,
                snapshot = snapshot
            )
        }
    }

    fun buildForegroundNotification(
        context: Context,
        serverName: String,
        running: Boolean,
        port: Int,
        path: String,
        clients: Int,
        ongoing: Boolean,
        onlyAlertOnce: Boolean = true,
        notificationId: Int = KEEPALIVE_NOTIFICATION_ID,
    ): Notification {
        return buildForegroundNotificationResult(
            context = context,
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = ongoing,
            onlyAlertOnce = onlyAlertOnce,
            notificationId = notificationId
        ).notification
    }

    fun buildForegroundBootstrapNotification(
        context: Context,
        serverName: String,
        running: Boolean,
        port: Int,
        path: String,
        clients: Int,
        notificationId: Int = KEEPALIVE_NOTIFICATION_ID
    ): Notification {
        val isBlueArchiveNotification = McpNotificationPayload.isBaNotificationServerName(serverName)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(
                MainActivity.EXTRA_TARGET_BOTTOM_PAGE,
                if (isBlueArchiveNotification) {
                    MainActivity.TARGET_BOTTOM_PAGE_BA
                } else {
                    MainActivity.TARGET_BOTTOM_PAGE_MCP
                }
            )
        }
        val openPendingIntent = PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            310_100 + notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val payload = McpNotificationPayload(
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = true,
            onlyAlertOnce = true,
            openPendingIntent = openPendingIntent,
            stopPendingIntent = openPendingIntent
        )
        return NotificationCompat.Builder(context, FOREGROUND_SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kei_logo_live_update)
            .setContentTitle(payload.title(context))
            .setContentText(payload.content(context).ifBlank { " " })
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun buildForegroundNotificationResult(
        context: Context,
        serverName: String,
        running: Boolean,
        port: Int,
        path: String,
        clients: Int,
        ongoing: Boolean,
        onlyAlertOnce: Boolean,
        secondaryActionMode: SecondaryActionMode = SecondaryActionMode.DEFAULT,
        notificationId: Int = KEEPALIVE_NOTIFICATION_ID,
        primaryActionLabel: String? = null,
        secondaryActionLabelOverride: String? = null,
        showSecondaryActionWhenStopped: Boolean = false,
        outerGlow: Boolean = true,
        overrideTitle: String? = null,
        overrideContent: String? = null,
        overrideOnlineText: String? = null,
        overrideShortText: String? = null,
        overrideProgressPercent: Int? = null,
        deadlineAtMs: Long? = null,
        miFocusOrderId: String? = null
    ): SessionNotifier.NotificationBuildResult {
        val isBlueArchiveNotification =
            McpNotificationPayload.isBaNotificationServerName(serverName)
        val openRequestCode = 110_100 + notificationId
        val focusOpenRequestCode = 410_100 + notificationId
        val secondaryRequestCode = 110_200 + notificationId
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(
                MainActivity.EXTRA_TARGET_BOTTOM_PAGE,
                if (isBlueArchiveNotification) {
                    MainActivity.TARGET_BOTTOM_PAGE_BA
                } else {
                    MainActivity.TARGET_BOTTOM_PAGE_MCP
                }
            )
        }
        val focusOpenPendingIntent = PendingIntent.getActivity(
            context,
            focusOpenRequestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPendingIntent = PendingIntentLaunchOptionsCompat.getUserVisibleActivity(
            context,
            openRequestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (stopPendingIntent, resolvedSecondaryActionLabel) = if (secondaryActionMode == SecondaryActionMode.MARK_READ) {
            MiFocusNotificationActions.markReadPendingIntent(
                context = context,
                notificationId = notificationId,
                requestCode = 210_200 + notificationId
            ) to context.getString(
                if (isBlueArchiveNotification) {
                    R.string.common_mark_read
                } else {
                    R.string.common_acknowledge
                }
            )
        } else if (isBlueArchiveNotification) {
            val dismissIntent = Intent(context, McpKeepAliveService::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            PendingIntent.getService(
                context,
                secondaryRequestCode,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ) to context.getString(R.string.common_mark_read)
        } else {
            val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_STOP_MCP_SERVER
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            PendingIntent.getBroadcast(
                context,
                secondaryRequestCode,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ) to context.getString(R.string.mcp_action_stop_service)
        }
        val effectiveSecondaryActionLabel =
            secondaryActionLabelOverride?.takeIf { it.isNotBlank() } ?: resolvedSecondaryActionLabel

        val payload = McpNotificationPayload(
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = ongoing,
            onlyAlertOnce = onlyAlertOnce,
            openPendingIntent = openPendingIntent,
            stopPendingIntent = stopPendingIntent,
            focusOpenPendingIntent = focusOpenPendingIntent,
            primaryActionLabel = primaryActionLabel,
            secondaryActionLabel = effectiveSecondaryActionLabel,
            showSecondaryActionWhenStopped = showSecondaryActionWhenStopped,
            outerGlow = outerGlow,
            overrideTitle = overrideTitle,
            overrideContent = overrideContent,
            overrideOnlineText = overrideOnlineText,
            overrideShortText = overrideShortText,
            overrideProgressPercent = overrideProgressPercent,
            deadlineAtMs = deadlineAtMs,
            notificationId = notificationId,
            miFocusOrderId = miFocusOrderId ?: buildMiFocusOrderId(serverName, notificationId)
        )
        val notifier = SessionNotifierImpl(NotificationHelper(context))
        return notifier.build(payload)
    }

    fun notifyTest(
        context: Context,
        serverName: String,
        running: Boolean,
        port: Int,
        path: String,
        clients: Int
    ) {
        val isBlueArchiveAp = McpNotificationPayload.isBaApServerName(serverName)
        val isBlueArchiveCafeAp = McpNotificationPayload.isBaCafeApServerName(serverName)
        val isBlueArchiveCafeVisit = McpNotificationPayload.isBaCafeVisitServerName(serverName)
        val isBlueArchiveArenaRefresh =
            McpNotificationPayload.isBaArenaRefreshServerName(serverName)
        val isBlueArchiveNotification =
            isBlueArchiveAp || isBlueArchiveCafeAp || isBlueArchiveCafeVisit || isBlueArchiveArenaRefresh
        val runningForNotification = if (isBlueArchiveNotification) running else true
        val baNotificationId = when {
            isBlueArchiveAp -> BA_AP_NOTIFICATION_ID
            isBlueArchiveCafeAp -> BA_CAFE_AP_NOTIFICATION_ID
            isBlueArchiveCafeVisit -> BA_CAFE_VISIT_NOTIFICATION_ID
            isBlueArchiveArenaRefresh -> BA_ARENA_REFRESH_NOTIFICATION_ID
            else -> TEST_NOTIFICATION_ID
        }
        notifyStandaloneEvent(
            context = context,
            notificationId = baNotificationId,
            serverName = serverName,
            running = runningForNotification,
            port = port,
            path = path,
            clients = clients,
            ongoing = runningForNotification,
            onlyAlertOnce = false
        )
    }

    fun notifyStandaloneEvent(
        context: Context,
        notificationId: Int,
        serverName: String,
        running: Boolean,
        port: Int,
        path: String,
        clients: Int,
        ongoing: Boolean = running,
        onlyAlertOnce: Boolean = false,
        primaryActionLabel: String? = null,
        secondaryActionLabel: String? = null,
        showSecondaryActionWhenStopped: Boolean = false,
        outerGlow: Boolean = true,
        overrideTitle: String? = null,
        overrideContent: String? = null,
        overrideOnlineText: String? = null,
        overrideShortText: String? = null,
        overrideProgressPercent: Int? = null,
        deadlineAtMs: Long? = null,
        miFocusOrderId: String? = null
    ): Boolean {
        ensureChannel(context)
        val buildResult = buildForegroundNotificationResult(
            context = context,
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = ongoing,
            onlyAlertOnce = onlyAlertOnce,
            secondaryActionMode = SecondaryActionMode.MARK_READ,
            notificationId = notificationId,
            primaryActionLabel = primaryActionLabel,
            secondaryActionLabelOverride = secondaryActionLabel,
            showSecondaryActionWhenStopped = showSecondaryActionWhenStopped,
            outerGlow = outerGlow,
            overrideTitle = overrideTitle,
            overrideContent = overrideContent,
            overrideOnlineText = overrideOnlineText,
            overrideShortText = overrideShortText,
            overrideProgressPercent = overrideProgressPercent,
            deadlineAtMs = deadlineAtMs,
            miFocusOrderId = miFocusOrderId
        )
        val snapshot = McpNotificationSnapshot(
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = ongoing,
            onlyAlertOnce = onlyAlertOnce,
            style = buildResult.style,
            useXiaomiMagic = buildResult.useXiaomiMagic,
            primaryActionLabel = primaryActionLabel,
            secondaryActionLabel = secondaryActionLabel,
            showSecondaryActionWhenStopped = showSecondaryActionWhenStopped,
            outerGlow = outerGlow,
            overrideTitle = overrideTitle,
            overrideContent = overrideContent,
            overrideOnlineText = overrideOnlineText,
            overrideShortText = overrideShortText,
            overrideProgressPercent = overrideProgressPercent,
            deadlineAtMs = deadlineAtMs,
            miFocusOrderId = miFocusOrderId
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        if (
            McpNotificationSnapshotStore.get(notificationId) == snapshot &&
            manager != null &&
            isNotificationActive(manager, notificationId)
        ) {
            return true
        }
        val dispatched = notifyWithResolvedDispatcher(
            context = context,
            notificationId = notificationId,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        if (dispatched) {
            McpNotificationSnapshotStore.put(notificationId, snapshot)
        }
        return dispatched
    }

    fun refreshStandaloneEventIfActive(
        context: Context,
        notificationId: Int,
        serverName: String,
        running: Boolean,
        port: Int,
        path: String,
        clients: Int,
        ongoing: Boolean = running,
        onlyAlertOnce: Boolean = true,
        primaryActionLabel: String? = null,
        secondaryActionLabel: String? = null,
        showSecondaryActionWhenStopped: Boolean = false,
        outerGlow: Boolean = true,
        overrideTitle: String? = null,
        overrideContent: String? = null,
        overrideOnlineText: String? = null,
        overrideShortText: String? = null,
        overrideProgressPercent: Int? = null,
        deadlineAtMs: Long? = null,
        miFocusOrderId: String? = null
    ): Boolean {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!isNotificationActive(manager, notificationId)) return false
        val buildResult = buildForegroundNotificationResult(
            context = context,
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = ongoing,
            onlyAlertOnce = onlyAlertOnce,
            secondaryActionMode = SecondaryActionMode.MARK_READ,
            notificationId = notificationId,
            primaryActionLabel = primaryActionLabel,
            secondaryActionLabelOverride = secondaryActionLabel,
            showSecondaryActionWhenStopped = showSecondaryActionWhenStopped,
            outerGlow = outerGlow,
            overrideTitle = overrideTitle,
            overrideContent = overrideContent,
            overrideOnlineText = overrideOnlineText,
            overrideShortText = overrideShortText,
            overrideProgressPercent = overrideProgressPercent,
            deadlineAtMs = deadlineAtMs,
            miFocusOrderId = miFocusOrderId
        )
        val snapshot = McpNotificationSnapshot(
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = ongoing,
            onlyAlertOnce = onlyAlertOnce,
            style = buildResult.style,
            useXiaomiMagic = buildResult.useXiaomiMagic,
            primaryActionLabel = primaryActionLabel,
            secondaryActionLabel = secondaryActionLabel,
            showSecondaryActionWhenStopped = showSecondaryActionWhenStopped,
            outerGlow = outerGlow,
            overrideTitle = overrideTitle,
            overrideContent = overrideContent,
            overrideOnlineText = overrideOnlineText,
            overrideShortText = overrideShortText,
            overrideProgressPercent = overrideProgressPercent,
            deadlineAtMs = deadlineAtMs,
            miFocusOrderId = miFocusOrderId
        )
        if (McpNotificationSnapshotStore.get(notificationId) == snapshot) return true
        val dispatched = notifyWithResolvedDispatcher(
            context = context,
            notificationId = notificationId,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        if (dispatched) {
            McpNotificationSnapshotStore.put(notificationId, snapshot)
        }
        return dispatched
    }

    fun refreshForegroundAsIsland(
        context: Context,
        notificationId: Int = KEEPALIVE_NOTIFICATION_ID,
        serverName: String,
        running: Boolean,
        port: Int,
        path: String,
        clients: Int,
        onlyAlertOnce: Boolean = true
    ) {
        ensureChannel(context)
        val buildResult = buildForegroundNotificationResult(
            context = context,
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = true,
            onlyAlertOnce = onlyAlertOnce,
            notificationId = notificationId
        )
        val snapshot = McpNotificationSnapshot(
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = true,
            onlyAlertOnce = onlyAlertOnce,
            style = buildResult.style,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        val dispatched = notifyWithResolvedDispatcher(
            context = context,
            notificationId = notificationId,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        if (dispatched) {
            McpNotificationSnapshotStore.put(notificationId, snapshot)
        }
    }

    fun refreshForegroundPulse(
        context: Context,
        notificationId: Int = KEEPALIVE_NOTIFICATION_ID,
        serverName: String,
        running: Boolean,
        port: Int,
        path: String,
        clients: Int
    ) {
        ensureChannel(context)
        val buildResult = buildForegroundNotificationResult(
            context = context,
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = true,
            onlyAlertOnce = true,
            notificationId = notificationId
        )
        val snapshot = McpNotificationSnapshot(
            serverName = serverName,
            running = running,
            port = port,
            path = path,
            clients = clients,
            ongoing = true,
            onlyAlertOnce = true,
            style = buildResult.style,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        val dispatched = notifyWithResolvedDispatcher(
            context = context,
            notificationId = notificationId,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        if (dispatched) {
            McpNotificationSnapshotStore.put(notificationId, snapshot)
        }
    }

    fun restoreXiaomiNetworkIfNeeded(context: Context) {
        McpXiaomiMagicDispatcher.restoreNetworkIfNeeded(context)
    }

    private fun refreshCachedNotificationIfActive(
        context: Context,
        manager: NotificationManager,
        notificationId: Int,
        snapshot: McpNotificationSnapshot?
    ) {
        if (snapshot == null || !isNotificationActive(manager, notificationId)) return
        val buildResult = buildForegroundNotificationResult(
            context = context,
            serverName = snapshot.serverName,
            running = snapshot.running,
            port = snapshot.port,
            path = snapshot.path,
            clients = snapshot.clients,
            ongoing = snapshot.ongoing,
            onlyAlertOnce = snapshot.onlyAlertOnce,
            secondaryActionMode = if (McpNotificationPayload.isBaNotificationServerName(snapshot.serverName)) {
                SecondaryActionMode.MARK_READ
            } else {
                secondaryActionModeFor(notificationId)
            },
            notificationId = notificationId,
            primaryActionLabel = snapshot.primaryActionLabel,
            secondaryActionLabelOverride = snapshot.secondaryActionLabel,
            showSecondaryActionWhenStopped = snapshot.showSecondaryActionWhenStopped,
            outerGlow = snapshot.outerGlow,
            overrideTitle = snapshot.overrideTitle,
            overrideContent = snapshot.overrideContent,
            overrideOnlineText = snapshot.overrideOnlineText,
            overrideShortText = snapshot.overrideShortText,
            overrideProgressPercent = snapshot.overrideProgressPercent,
            deadlineAtMs = snapshot.deadlineAtMs,
            miFocusOrderId = snapshot.miFocusOrderId
        )
        val nextSnapshot = snapshot.copy(
            style = buildResult.style,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        if (snapshot == nextSnapshot) return
        val dispatched = notifyWithResolvedDispatcher(
            context = context,
            notificationId = notificationId,
            notification = buildResult.notification,
            useXiaomiMagic = buildResult.useXiaomiMagic
        )
        if (dispatched) {
            McpNotificationSnapshotStore.put(notificationId, nextSnapshot)
        }
    }

    private fun isNotificationActive(manager: NotificationManager, notificationId: Int): Boolean {
        return McpNotificationActiveStateCache.isActive(notificationId) {
            runCatching {
                manager.activeNotifications.any { it.id == notificationId }
            }.getOrDefault(false)
        }
    }

    private fun secondaryActionModeFor(notificationId: Int): SecondaryActionMode {
        return when (notificationId) {
            BA_AP_NOTIFICATION_ID,
            BA_CAFE_AP_NOTIFICATION_ID,
            BA_CAFE_VISIT_NOTIFICATION_ID,
            BA_ARENA_REFRESH_NOTIFICATION_ID -> SecondaryActionMode.MARK_READ

            else -> SecondaryActionMode.DEFAULT
        }
    }

    private fun buildMiFocusOrderId(serverName: String, notificationId: Int): String {
        val normalizedServerName = serverName.trim().ifBlank { "keios" }
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "notification" }
        return "$normalizedServerName-$notificationId"
    }

    private fun notifyWithResolvedDispatcher(
        context: Context,
        notificationId: Int,
        notification: Notification,
        useXiaomiMagic: Boolean
    ): Boolean {
        val dispatched = if (useXiaomiMagic) {
            McpXiaomiMagicDispatcher.notify(
                context = context,
                notificationId = notificationId,
                notification = notification
            )
        } else {
            val notificationManager = NotificationManagerCompat.from(context)
            if (McpXiaomiMagicDispatcher.canUseCommand()) {
                restoreXiaomiNetworkIfNeeded(context)
            }
            notifySafely(context, notificationManager, notificationId, notification)
        }
        if (dispatched) {
            McpNotificationActiveStateCache.markActive(notificationId, active = true)
        }
        return dispatched
    }

    fun dispatchNotification(
        context: Context,
        notificationId: Int,
        notification: Notification,
        useXiaomiMagic: Boolean
    ): Boolean {
        return notifyWithResolvedDispatcher(
            context = context,
            notificationId = notificationId,
            notification = notification,
            useXiaomiMagic = useXiaomiMagic
        )
    }

    fun cancelNotification(
        context: Context,
        notificationId: Int
    ) {
        McpNotificationSnapshotStore.clear(notificationId)
        McpNotificationActiveStateCache.markActive(notificationId, active = false)
        if (McpXiaomiMagicDispatcher.canUseCommand()) {
            restoreXiaomiNetworkIfNeeded(context)
        }
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    internal fun canPostNotifications(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    internal fun notifySafely(
        context: Context,
        notificationManager: NotificationManagerCompat,
        notificationId: Int,
        notification: Notification
    ): Boolean {
        if (!canPostNotifications(context)) return false
        return runCatching {
            notificationManager.notify(notificationId, notification)
            true
        }.getOrElse { throwable ->
            AppLogger.e(TAG, "Notification dispatch failed id=$notificationId", throwable)
            false
        }
    }
}
