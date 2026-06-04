package os.kei.mcp.framework.notification.builder

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt
import com.xzakota.hyper.notification.focus.FocusNotification
import com.xzakota.hyper.notification.focus.template.FocusTemplateV3
import os.kei.feature.mcp.R
import os.kei.core.log.AppLogger
import os.kei.mcp.notification.McpNotificationPayload
import kotlin.math.roundToInt

class MiIslandNotificationBuilder(
    private val context: Context
) : SessionNotificationBuilder {
    private data class IslandAction(
        val key: String,
        val title: String,
        val pendingIntent: PendingIntent,
        val style: IslandActionStyle = IslandActionStyle.Plain
    )

    private sealed interface IslandActionStyle {
        data object Plain : IslandActionStyle
        data class Primary(val backgroundColor: String) : IslandActionStyle
        data class Tonal(
            val backgroundColor: String,
            val backgroundColorDark: String,
            val titleColor: String,
            val titleColorDark: String
        ) : IslandActionStyle
        data object Danger : IslandActionStyle
    }

    private enum class IslandBigTemplateKind {
        TEXT,
        PROGRESS_TEXT,
        COUNTDOWN_DIGIT
    }

    private enum class IslandSmallTemplateKind {
        ICON,
        PROGRESS_ICON
    }

    private data class IslandPresentation(
        val allowFloat: Boolean,
        val showTextButtons: Boolean,
        val bigTemplateKind: IslandBigTemplateKind = IslandBigTemplateKind.TEXT,
        val smallTemplateKind: IslandSmallTemplateKind = IslandSmallTemplateKind.ICON,
        val compactTitle: String,
        val compactContent: String? = null,
        val deadlineAtMs: Long? = null,
        val notificationOngoing: Boolean,
        val requestPromotedOngoing: Boolean,
        val focusUpdatable: Boolean,
        val focusShowNotification: Boolean? = null,
        val showExpandedProgress: Boolean = false,
        val progressPercent: Int = 0,
        val progressColor: String = BA_AP_PROGRESS_COLOR,
        val progressTrackColor: String = BA_AP_PROGRESS_TRACK_COLOR,
        val notificationAccentColor: String? = null,
        val primaryActionColor: String = HIGHLIGHT_BG_COLOR
    )

    private companion object {
        private const val TAG = "McpMiIslandBuilder"
        private const val MI_FOCUS_DEFAULT_BUSINESS = "keios"
        private const val HIGHLIGHT_BG_COLOR = "#006EFF"
        private const val HIGHLIGHT_TITLE_COLOR = "#FFFFFF"
        private const val DANGER_BG_COLOR = "#E25B6A"
        private const val DANGER_BG_COLOR_DARK = "#FF6B7C"
        private const val DANGER_TITLE_COLOR = "#FFFFFF"
        private const val MCP_RUNNING_ACCENT_COLOR = "#1A73E8"
        private const val BA_AP_PROGRESS_COLOR = "#4DA3FF"
        private const val BA_AP_PROGRESS_TRACK_COLOR = "#374151"
        private const val BA_EVENT_ACCENT_COLOR = "#4DA3FF"
        private const val GITHUB_SHARE_IMPORT_ACCENT_COLOR = "#2563EB"
        private const val GITHUB_SHARE_IMPORT_ACTION_PRIMARY_COLOR = "#2563EB"
        private const val GITHUB_SHARE_IMPORT_ACTION_SECONDARY_BG_COLOR = "#DBEAFE"
        private const val GITHUB_SHARE_IMPORT_ACTION_SECONDARY_BG_COLOR_DARK = "#1E3A8A"
        private const val GITHUB_SHARE_IMPORT_ACTION_SECONDARY_TITLE_COLOR = "#1D4ED8"
        private const val GITHUB_SHARE_IMPORT_ACTION_SECONDARY_TITLE_COLOR_DARK = "#DBEAFE"
        private const val GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_BG_COLOR = "#E5E7EB"
        private const val GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_BG_COLOR_DARK = "#334155"
        private const val GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR = "#475569"
        private const val GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR_DARK = "#CBD5E1"
        private val ISLAND_ICON_RES_ID_DEFAULT = R.drawable.ic_kei_logo_island
        private val ISLAND_ICON_RES_ID_AP = R.drawable.ic_ba_ap_island_notification
        private val ISLAND_ICON_RES_ID_BA_CAFE_VISIT = R.drawable.ic_ba_tea_party_island
        private val ISLAND_ICON_RES_ID_BA_ARENA_REFRESH = R.drawable.ic_ba_arena_coin_island
        private val ISLAND_ICON_RES_ID_BA_CALENDAR_POOL =
            R.drawable.ic_ba_calendar_live_update
        private val ISLAND_ICON_RES_ID_GITHUB_SHARE_IMPORT =
            R.drawable.ic_github_invertocat_island_blue
    }

    override fun build(payload: NotificationPayload): Notification {
        val state = payload.state
        val isBlueArchiveCafeAp = McpNotificationPayload.isBaCafeApServerName(state.serverName)
        val isBlueArchiveAp = McpNotificationPayload.isBaApServerName(state.serverName) || isBlueArchiveCafeAp
        val isBlueArchiveCafeVisit = McpNotificationPayload.isBaCafeVisitServerName(state.serverName)
        val isBlueArchiveArenaRefresh = McpNotificationPayload.isBaArenaRefreshServerName(state.serverName)
        val isBlueArchiveCalendarPool =
            McpNotificationPayload.isBaCalendarPoolServerName(state.serverName)
        val isGitHubShareImport =
            McpNotificationPayload.isGitHubShareImportServerName(state.serverName)
        val isBlueArchiveNotification =
            isBlueArchiveAp ||
                    isBlueArchiveCafeVisit ||
                    isBlueArchiveArenaRefresh ||
                    isBlueArchiveCalendarPool
        val islandIconResId = when {
            isBlueArchiveAp -> ISLAND_ICON_RES_ID_AP
            isBlueArchiveCafeVisit -> ISLAND_ICON_RES_ID_BA_CAFE_VISIT
            isBlueArchiveArenaRefresh -> ISLAND_ICON_RES_ID_BA_ARENA_REFRESH
            isBlueArchiveCalendarPool -> ISLAND_ICON_RES_ID_BA_CALENDAR_POOL
            isGitHubShareImport -> ISLAND_ICON_RES_ID_GITHUB_SHARE_IMPORT
            else -> ISLAND_ICON_RES_ID_DEFAULT
        }
        val shortCriticalText = resolveShortCriticalText(
            state = state,
            isBlueArchiveAp = isBlueArchiveAp,
            isBlueArchiveCafeVisit = isBlueArchiveCafeVisit,
            isBlueArchiveArenaRefresh = isBlueArchiveArenaRefresh,
            isBlueArchiveCalendarPool = isBlueArchiveCalendarPool,
            isGitHubShareImport = isGitHubShareImport
        )
        val presentation = resolvePresentation(
            state = state,
            isBlueArchiveAp = isBlueArchiveAp,
            isBlueArchiveCafeVisit = isBlueArchiveCafeVisit,
            isBlueArchiveArenaRefresh = isBlueArchiveArenaRefresh,
            isBlueArchiveCalendarPool = isBlueArchiveCalendarPool,
            isGitHubShareImport = isGitHubShareImport,
            miIslandProgressColorOverride = payload.miIslandProgressColorOverride
        )
        val isCalendarPoolCountdown =
            isBlueArchiveCalendarPool && state.running && state.deadlineAtMs != null
        val isCalendarPoolUpdate =
            isBlueArchiveCalendarPool && state.running && state.deadlineAtMs == null
        val builder = NotificationCompat.Builder(context, payload.environment.channelId)
            .setSmallIcon(islandIconResId)
            .setContentTitle(state.title(context))
            .setContentText(state.content(context).ifBlank { " " })
            .setContentIntent(state.openPendingIntent)
            .setCategory(
                when {
                    isBlueArchiveAp && state.running -> NotificationCompat.CATEGORY_PROGRESS
                    isCalendarPoolCountdown -> NotificationCompat.CATEGORY_PROGRESS
                    isGitHubShareImport && state.running -> NotificationCompat.CATEGORY_PROGRESS
                    !isBlueArchiveNotification && state.running -> NotificationCompat.CATEGORY_SERVICE
                    else -> NotificationCompat.CATEGORY_STATUS
                }
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(presentation.notificationOngoing)
            .setOnlyAlertOnce(state.onlyAlertOnce)
            .setAutoCancel(isCalendarPoolUpdate && !presentation.notificationOngoing)
            .setRequestPromotedOngoing(presentation.requestPromotedOngoing)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .applyDeadline(state.deadlineAtMs)

        if (!presentation.notificationOngoing && state.stopPendingIntent != state.openPendingIntent) {
            builder.setDeleteIntent(state.stopPendingIntent)
        }
        presentation.notificationAccentColor?.let { accentColor ->
            builder
                .setColorized(true)
                .setColor(accentColor.toColorInt())
        }
        shortCriticalText?.let(builder::setShortCriticalText)
        if (!isBlueArchiveNotification) {
            resolveIslandActions(
                state = state,
                isGitHubShareImport = isGitHubShareImport,
                forFocusExtras = false,
                primaryActionColor = presentation.primaryActionColor
            ).forEach { action ->
                val pendingIntent = if (action.key == "mcp_action_open") {
                    state.openPendingIntent
                } else {
                    action.pendingIntent
                }
                builder.addAction(0, action.title, pendingIntent)
            }
        }
        if (presentation.showExpandedProgress) {
            builder.setProgress(100, presentation.progressPercent.coerceIn(0, 100), false)
        }
        buildFocusExtras(payload, islandIconResId)?.let(builder::addExtras)
        return builder.build()
    }

    private fun buildFocusExtras(payload: NotificationPayload, islandIconResId: Int) = runCatching {
        val state = payload.state
        val isBlueArchiveCafeAp = McpNotificationPayload.isBaCafeApServerName(state.serverName)
        val isBlueArchiveAp = McpNotificationPayload.isBaApServerName(state.serverName) || isBlueArchiveCafeAp
        val isBlueArchiveCafeVisit = McpNotificationPayload.isBaCafeVisitServerName(state.serverName)
        val isBlueArchiveArenaRefresh = McpNotificationPayload.isBaArenaRefreshServerName(state.serverName)
        val isBlueArchiveCalendarPool =
            McpNotificationPayload.isBaCalendarPoolServerName(state.serverName)
        val isGitHubShareImport =
            McpNotificationPayload.isGitHubShareImportServerName(state.serverName)
        val isBlueArchiveNotification =
            isBlueArchiveAp ||
                    isBlueArchiveCafeVisit ||
                    isBlueArchiveArenaRefresh ||
                    isBlueArchiveCalendarPool
        val presentation = resolvePresentation(
            state = state,
            isBlueArchiveAp = isBlueArchiveAp,
            isBlueArchiveCafeVisit = isBlueArchiveCafeVisit,
            isBlueArchiveArenaRefresh = isBlueArchiveArenaRefresh,
            isBlueArchiveCalendarPool = isBlueArchiveCalendarPool,
            isGitHubShareImport = isGitHubShareImport,
            miIslandProgressColorOverride = payload.miIslandProgressColorOverride
        )
        val useSemanticIcon = isBlueArchiveNotification || isGitHubShareImport
        val lightLogoIcon = if (useSemanticIcon) {
            Icon.createWithResource(context, islandIconResId)
        } else {
            Icon.createWithResource(context, islandIconResId).setTint(Color.BLACK)
        }
        val darkLogoIcon = if (useSemanticIcon) {
            Icon.createWithResource(context, islandIconResId)
        } else {
            Icon.createWithResource(context, islandIconResId).setTint(Color.WHITE)
        }
        val actions = resolveIslandActions(
            state = state,
            isGitHubShareImport = isGitHubShareImport,
            forFocusExtras = true,
            primaryActionColor = presentation.primaryActionColor
        )
        val displayIcon = payload.semanticIconBitmap?.let { bitmap ->
            Icon.createWithBitmap(bitmap)
        }
            ?: Icon.createWithResource(context, islandIconResId)

        FocusNotification.buildV3 {
            val lightLogoKey = createPicture("key_logo_light", lightLogoIcon)
            val darkLogoKey = createPicture("key_logo_dark", darkLogoIcon)
            val displayIconKey = createPicture("key_logo_display", displayIcon)

            islandFirstFloat = true
            enableFloat = presentation.allowFloat
            updatable = presentation.focusUpdatable
            business = MI_FOCUS_DEFAULT_BUSINESS
            notifyId = state.notificationId.takeIf { it > 0 }?.toString()
            orderId = state.miFocusOrderId
            focusShowNotification(presentation.focusShowNotification)
            ticker = state.title(context)
            tickerPic = lightLogoKey
            tickerPicDark = darkLogoKey
            if (payload.settings.miIslandOuterGlow) {
                outEffectSrc = "outer_glow"
            }

            island {
                islandProperty = 1
                bigIslandArea {
                    imageTextInfoLeft {
                        type = 1
                        picInfo {
                            type = 1
                            pic = displayIconKey
                        }
                    }
                    when (presentation.bigTemplateKind) {
                        IslandBigTemplateKind.PROGRESS_TEXT -> {
                            progressTextInfo {
                                progressInfo {
                                    progress = presentation.progressPercent.coerceIn(0, 100)
                                    isCCW = true
                                    colorReach = presentation.progressColor
                                    colorUnReach = presentation.progressTrackColor
                                }
                                textInfo {
                                    title = presentation.compactTitle
                                    content = presentation.compactContent
                                    narrowFont = shouldUseNarrowFont(presentation)
                                }
                            }
                        }

                        IslandBigTemplateKind.COUNTDOWN_DIGIT -> {
                            sameWidthDigitInfo {
                                content = presentation.compactTitle
                                showHighlightColor = true
                                val now = System.currentTimeMillis()
                                val deadlineAtMs = presentation.deadlineAtMs ?: now
                                timerInfo {
                                    timerType = -1
                                    timerWhen = deadlineAtMs
                                    timerTotal = (deadlineAtMs - now).coerceAtLeast(0L)
                                    timerSystemCurrent = now
                                }
                            }
                        }

                        IslandBigTemplateKind.TEXT -> {
                            imageTextInfoRight {
                                type = 3
                                textInfo {
                                    title = presentation.compactTitle
                                    content = presentation.compactContent
                                    narrowFont = shouldUseNarrowFont(presentation)
                                }
                            }
                        }
                    }
                }
                smallIslandArea {
                    when (presentation.smallTemplateKind) {
                        IslandSmallTemplateKind.PROGRESS_ICON -> {
                            combinePicInfo {
                                picInfo {
                                    type = 1
                                    pic = displayIconKey
                                }
                                progressInfo {
                                    progress = presentation.progressPercent.coerceIn(0, 100)
                                    isCCW = true
                                    colorReach = presentation.progressColor
                                    colorUnReach = presentation.progressTrackColor
                                }
                            }
                        }

                        IslandSmallTemplateKind.ICON -> {
                            picInfo {
                                type = 1
                                pic = displayIconKey
                            }
                        }
                    }
                }
            }

            baseInfo {
                type = 2
                title = state.title(context)
                content = state.content(context).ifBlank { " " }
            }

            if (presentation.showExpandedProgress) {
                multiProgressInfo {
                    progress = presentation.progressPercent.coerceIn(0, 100)
                    color = presentation.progressColor
                    title = presentation.compactTitle
                    content = resolveExpandedProgressContent(
                        state = state,
                        presentation = presentation,
                        isGitHubShareImport = isGitHubShareImport
                    )
                    if (isGitHubShareImport) {
                        colorTitle = presentation.progressColor
                        colorTitleDark = presentation.progressColor
                        colorContent = GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR
                        colorContentDark = GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR_DARK
                    }
                }
            }

            picInfo {
                type = 1
                pic = displayIconKey
                picDark = displayIconKey
            }

            if (presentation.showTextButtons && actions.isNotEmpty()) {
                textButton {
                    actions.take(2).forEach { actionItem ->
                        addActionInfo {
                            type = 2
                            val nativeAction = Notification.Action.Builder(
                                Icon.createWithResource(context, islandIconResId),
                                actionItem.title,
                                actionItem.pendingIntent
                            ).build()
                            action = createAction(actionItem.key, nativeAction)
                            actionTitle = actionItem.title
                            clickWithCollapse = true
                            applyIslandActionStyle(actionItem.style)
                        }
                    }
                }
            }
        }
    }.onFailure {
        AppLogger.e(TAG, "Build FocusNotification extras failed", it)
    }.getOrNull()

    private fun resolvePresentation(
        state: McpNotificationPayload,
        isBlueArchiveAp: Boolean,
        isBlueArchiveCafeVisit: Boolean,
        isBlueArchiveArenaRefresh: Boolean,
        isBlueArchiveCalendarPool: Boolean,
        isGitHubShareImport: Boolean,
        miIslandProgressColorOverride: String? = null
    ): IslandPresentation {
        if (isBlueArchiveAp && state.running) {
            return IslandPresentation(
                allowFloat = false,
                showTextButtons = true,
                bigTemplateKind = IslandBigTemplateKind.PROGRESS_TEXT,
                smallTemplateKind = IslandSmallTemplateKind.PROGRESS_ICON,
                compactTitle = resolveCompactTitle(
                    raw = state.port.coerceAtLeast(0).toString(),
                    fallback = context.getString(R.string.ba_notification_ap_island_text)
                ),
                compactContent = context.getString(R.string.ba_notification_ap_island_text),
                notificationOngoing = true,
                requestPromotedOngoing = true,
                focusUpdatable = true,
                focusShowNotification = true,
                showExpandedProgress = true,
                progressPercent = resolveApProgressPercent(state),
                notificationAccentColor = BA_AP_PROGRESS_COLOR,
                primaryActionColor = BA_AP_PROGRESS_COLOR
            )
        }
        if (isBlueArchiveCafeVisit && state.running) {
            return IslandPresentation(
                allowFloat = true,
                showTextButtons = true,
                compactTitle = context.getString(R.string.ba_cafe_visit_notification_island_text),
                notificationOngoing = false,
                requestPromotedOngoing = false,
                focusUpdatable = true,
                focusShowNotification = true,
                notificationAccentColor = BA_EVENT_ACCENT_COLOR,
                primaryActionColor = BA_EVENT_ACCENT_COLOR
            )
        }
        if (isBlueArchiveArenaRefresh && state.running) {
            return IslandPresentation(
                allowFloat = true,
                showTextButtons = true,
                compactTitle = context.getString(R.string.ba_arena_refresh_notification_island_text),
                notificationOngoing = false,
                requestPromotedOngoing = false,
                focusUpdatable = true,
                focusShowNotification = true,
                notificationAccentColor = BA_EVENT_ACCENT_COLOR,
                primaryActionColor = BA_EVENT_ACCENT_COLOR
            )
        }
        if (isBlueArchiveCalendarPool && state.running) {
            val progressPercent = state.overrideProgressPercent?.coerceIn(0, 100) ?: 100
            val hasCountdown = state.deadlineAtMs != null
            return IslandPresentation(
                allowFloat = true,
                showTextButtons = true,
                bigTemplateKind = if (hasCountdown) {
                    IslandBigTemplateKind.COUNTDOWN_DIGIT
                } else {
                    IslandBigTemplateKind.TEXT
                },
                smallTemplateKind = if (hasCountdown) {
                    IslandSmallTemplateKind.PROGRESS_ICON
                } else {
                    IslandSmallTemplateKind.ICON
                },
                compactTitle = resolveCompactTitle(
                    raw = state.shortText,
                    fallback = context.getString(R.string.common_status_running)
                ),
                compactContent =
                    state.onlineText(context).takeIf {
                        !hasCountdown && it.isNotBlank() && it != state.shortText
                    },
                deadlineAtMs = state.deadlineAtMs,
                notificationOngoing = state.ongoing,
                requestPromotedOngoing = state.ongoing,
                focusUpdatable = true,
                focusShowNotification = true,
                showExpandedProgress = hasCountdown,
                progressPercent = progressPercent,
                progressColor = BA_EVENT_ACCENT_COLOR,
                notificationAccentColor = BA_EVENT_ACCENT_COLOR,
                primaryActionColor = BA_EVENT_ACCENT_COLOR
            )
        }
        if (isGitHubShareImport && state.running) {
            val progressPercent = state.overrideProgressPercent
                ?.coerceIn(0, 100)
                ?: state.port.coerceIn(0, 100)
            val progressColor = miIslandProgressColorOverride
                ?: GITHUB_SHARE_IMPORT_ACCENT_COLOR
            val useProgressTemplate = state.clients > 0 && state.overrideProgressPercent != null
            return IslandPresentation(
                allowFloat = state.clients <= 0,
                showTextButtons = true,
                bigTemplateKind = if (useProgressTemplate) {
                    IslandBigTemplateKind.PROGRESS_TEXT
                } else {
                    IslandBigTemplateKind.TEXT
                },
                smallTemplateKind = if (useProgressTemplate) {
                    IslandSmallTemplateKind.PROGRESS_ICON
                } else {
                    IslandSmallTemplateKind.ICON
                },
                compactTitle = resolveCompactTitle(
                    raw = state.onlineText(context),
                    fallback = state.shortText,
                    maxTextLength = 10
                ),
                compactContent = state.shortText
                    .takeIf {
                        it.isNotBlank() &&
                                it != state.onlineText(context)
                    },
                notificationOngoing = state.ongoing,
                requestPromotedOngoing = true,
                focusUpdatable = true,
                focusShowNotification = true,
                showExpandedProgress = useProgressTemplate,
                progressPercent = progressPercent,
                progressColor = progressColor,
                notificationAccentColor = progressColor,
                primaryActionColor = GITHUB_SHARE_IMPORT_ACTION_PRIMARY_COLOR
            )
        }
        if (state.running) {
            return IslandPresentation(
                allowFloat = false,
                showTextButtons = false,
                compactTitle = resolveCompactTitle(
                    raw = state.onlineText(context),
                    fallback = context.getString(R.string.common_status_running)
                ),
                notificationOngoing = state.ongoing,
                requestPromotedOngoing = true,
                focusUpdatable = true,
                compactContent = resolveDefaultEndpointSummary(state),
                notificationAccentColor = MCP_RUNNING_ACCENT_COLOR
            )
        }
        return IslandPresentation(
            allowFloat = true,
            showTextButtons = true,
            compactTitle = resolveCompactTitle(
                raw = state.statusText(context),
                fallback = context.getString(R.string.common_acknowledge)
            ),
            notificationOngoing = state.ongoing,
            requestPromotedOngoing = state.ongoing,
            focusUpdatable = true,
            focusShowNotification = true
        )
    }

    private fun resolveShortCriticalText(
        state: McpNotificationPayload,
        isBlueArchiveAp: Boolean,
        isBlueArchiveCafeVisit: Boolean,
        isBlueArchiveArenaRefresh: Boolean,
        isBlueArchiveCalendarPool: Boolean,
        isGitHubShareImport: Boolean
    ): String? {
        return when {
            !state.running -> state.statusText(context)
            isBlueArchiveAp -> context.getString(R.string.ba_notification_ap_island_text)
            isBlueArchiveCalendarPool -> state.shortText
            isGitHubShareImport -> state.onlineText(context)
            isBlueArchiveCafeVisit || isBlueArchiveArenaRefresh -> state.onlineText(context)
            else -> state.onlineText(context)
        }.takeIf { it.isNotBlank() }
    }

    private fun resolveIslandActions(
        state: McpNotificationPayload,
        isGitHubShareImport: Boolean,
        forFocusExtras: Boolean,
        primaryActionColor: String
    ): List<IslandAction> {
        val actions = mutableListOf(
            IslandAction(
                key = "mcp_action_open",
                title = if (isGitHubShareImport) {
                    state.primaryActionTitle(context)
                } else {
                    context.getString(R.string.common_open)
                },
                pendingIntent = state.focusOpenPendingIntent,
                style = IslandActionStyle.Primary(primaryActionColor)
            )
        )
        val showSecondaryAction = when {
            isGitHubShareImport ->
                state.stopPendingIntent != state.openPendingIntent &&
                        (state.running || state.showSecondaryActionWhenStopped)

            forFocusExtras -> true
            else -> state.running
        }
        if (showSecondaryAction) {
            actions += IslandAction(
                key = "mcp_action_stop",
                title = state.stopActionTitle(context),
                pendingIntent = state.stopPendingIntent,
                style = resolveSecondaryActionStyle(
                    state = state,
                    isGitHubShareImport = isGitHubShareImport
                )
            )
        }
        return actions
    }

    private fun com.xzakota.hyper.notification.focus.model.ActionInfo.applyIslandActionStyle(
        style: IslandActionStyle
    ) {
        when (style) {
            IslandActionStyle.Plain -> Unit

            is IslandActionStyle.Primary -> {
                actionBgColor = style.backgroundColor
                actionBgColorDark = style.backgroundColor
                actionTitleColor = HIGHLIGHT_TITLE_COLOR
                actionTitleColorDark = HIGHLIGHT_TITLE_COLOR
            }

            is IslandActionStyle.Tonal -> {
                actionBgColor = style.backgroundColor
                actionBgColorDark = style.backgroundColorDark
                actionTitleColor = style.titleColor
                actionTitleColorDark = style.titleColorDark
            }

            IslandActionStyle.Danger -> {
                actionBgColor = DANGER_BG_COLOR
                actionBgColorDark = DANGER_BG_COLOR_DARK
                actionTitleColor = DANGER_TITLE_COLOR
                actionTitleColorDark = DANGER_TITLE_COLOR
            }
        }
    }

    private fun resolveSecondaryActionStyle(
        state: McpNotificationPayload,
        isGitHubShareImport: Boolean
    ): IslandActionStyle {
        val secondaryActionLabel = state.secondaryActionLabel?.trim().orEmpty()
        val cancelLinkageLabel = context.getString(
            R.string.github_share_import_pending_action_cancel
        )
        if (!isGitHubShareImport) return IslandActionStyle.Plain
        if (secondaryActionLabel == cancelLinkageLabel) return IslandActionStyle.Danger
        val installActionLabels = setOf(
            context.getString(R.string.github_share_import_notify_action_send_install),
            context.getString(R.string.github_share_import_notify_action_continue_install),
            context.getString(R.string.github_share_import_notify_action_confirm_track),
            context.getString(R.string.github_page_install_confirm_action_install)
        )
        if (secondaryActionLabel in installActionLabels) {
            return IslandActionStyle.Tonal(
                backgroundColor = GITHUB_SHARE_IMPORT_ACTION_SECONDARY_BG_COLOR,
                backgroundColorDark = GITHUB_SHARE_IMPORT_ACTION_SECONDARY_BG_COLOR_DARK,
                titleColor = GITHUB_SHARE_IMPORT_ACTION_SECONDARY_TITLE_COLOR,
                titleColorDark = GITHUB_SHARE_IMPORT_ACTION_SECONDARY_TITLE_COLOR_DARK
            )
        }
        if (secondaryActionLabel == context.getString(R.string.common_refresh)) {
            return IslandActionStyle.Tonal(
                backgroundColor = GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_BG_COLOR,
                backgroundColorDark = GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_BG_COLOR_DARK,
                titleColor = GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR,
                titleColorDark = GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR_DARK
            )
        }
        return IslandActionStyle.Plain
    }

    private fun resolveDefaultEndpointSummary(state: McpNotificationPayload): String? {
        if (!state.running) return null
        val path = state.path.trim().ifBlank { "/mcp" }
        val port = state.port.coerceAtLeast(0)
        return if (path.length <= 12) {
            "$port $path"
        } else {
            port.toString()
        }
    }

    private fun resolveExpandedProgressContent(
        state: McpNotificationPayload,
        presentation: IslandPresentation,
        isGitHubShareImport: Boolean
    ): String {
        val fullContent = state.content(context).trim()
        if (isGitHubShareImport && presentation.showExpandedProgress && fullContent.isNotBlank()) {
            return fullContent
        }
        return presentation.compactContent ?: state.shortText
    }

    private fun resolveApProgressPercent(state: McpNotificationPayload): Int {
        if (!state.running) return 0
        val limit = state.clients.coerceAtLeast(1)
        val current = state.port.coerceAtLeast(0).coerceAtMost(limit)
        return ((current.toFloat() / limit.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun resolveCompactTitle(
        raw: String,
        fallback: String,
        maxTextLength: Int = 4
    ): String {
        val trimmed = raw.trim()
        val fallbackTrimmed = fallback.trim()
        if (trimmed.isBlank()) return fallbackTrimmed
        val isNumeric = trimmed.all(Char::isDigit)
        if (isNumeric && trimmed.length <= 3) return trimmed
        if (isNumeric) return fallbackTrimmed
        if (trimmed.length <= maxTextLength) return trimmed
        return fallbackTrimmed.ifBlank { trimmed }
    }

    private fun shouldUseNarrowFont(presentation: IslandPresentation): Boolean {
        return presentation.compactTitle.length >= 6 ||
                (presentation.compactContent?.length ?: 0) >= 12
    }

    private fun NotificationCompat.Builder.applyDeadline(deadlineAtMs: Long?): NotificationCompat.Builder {
        if (deadlineAtMs == null) return this
        return setWhen(deadlineAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
    }

    private fun FocusTemplateV3.focusShowNotification(show: Boolean?) {
        if (show != null) {
            isShowNotification = show
        }
    }
}
