package os.kei.core.notification.live.builder

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt
import os.kei.core.notification.focus.MiFocusProtocolActionInfo
import os.kei.core.notification.focus.MiFocusProtocolNotification
import os.kei.core.notification.focus.MiFocusProtocolTemplateV3
import os.kei.core.notification.R
import os.kei.core.notification.identity.NotificationAppIconResolver
import os.kei.core.log.AppLogger
import os.kei.core.notification.live.LiveNotificationPayload
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
        COUNTDOWN_DIGIT,
        FIXED_DIGIT
    }

    private enum class IslandSmallTemplateKind {
        ICON,
        PROGRESS_ICON,
        ICON_TEXT
    }

    private enum class IslandExpandedProgressKind {
        NONE,
        BAR
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
        val expandedProgressKind: IslandExpandedProgressKind = IslandExpandedProgressKind.NONE,
        val progressPercent: Int = 0,
        val progressColor: String = BA_AP_PROGRESS_COLOR,
        val progressTrackColor: String = BA_AP_PROGRESS_TRACK_COLOR,
        val notificationAccentColor: String? = null,
        val primaryActionColor: String = HIGHLIGHT_BG_COLOR
    ) {
        val effectiveRequestPromotedOngoing: Boolean
            get() = notificationOngoing && requestPromotedOngoing
    }

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
        private const val WEBDAV_ACCENT_COLOR = "#2563EB"
        private const val WEBDAV_NEEDS_REVIEW_COLOR = "#F59E0B"
        private const val WEBDAV_SUCCESS_COLOR = "#22C55E"
        private const val WEBDAV_DANGER_COLOR = "#E25B6A"
        private const val GITHUB_SHARE_IMPORT_ACTION_PRIMARY_COLOR = "#2563EB"
        private const val GITHUB_SHARE_IMPORT_ACTION_SECONDARY_BG_COLOR = "#DBEAFE"
        private const val GITHUB_SHARE_IMPORT_ACTION_SECONDARY_BG_COLOR_DARK = "#1E3A8A"
        private const val GITHUB_SHARE_IMPORT_ACTION_SECONDARY_TITLE_COLOR = "#1D4ED8"
        private const val GITHUB_SHARE_IMPORT_ACTION_SECONDARY_TITLE_COLOR_DARK = "#DBEAFE"
        private const val GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_BG_COLOR = "#E5E7EB"
        private const val GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_BG_COLOR_DARK = "#334155"
        private const val GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR = "#475569"
        private const val GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR_DARK = "#CBD5E1"
        /**
         * Completion green, the same value [WEBDAV_SUCCESS_COLOR] carries and the one
         * `readme/MI_FOCUS_NOTIFICATION_TEMPLATES.md` names for "完成 / 正向结果". Spelled out per feature
         * rather than shared, because a later decision to re-tone one feature should not silently retone
         * the other.
         */
        private const val BA_DAILY_DONE_ACCENT_COLOR = "#22C55E"
        private val ISLAND_ICON_RES_ID_BA_DAILY_DONE = R.drawable.ic_ba_daily_done_island
        private val ISLAND_ICON_RES_ID_AP = R.drawable.ic_ba_ap_island_notification
        private val ISLAND_ICON_RES_ID_BA_CAFE_VISIT = R.drawable.ic_ba_tea_party_island
        private val ISLAND_ICON_RES_ID_BA_ARENA_REFRESH = R.drawable.ic_ba_arena_coin_island
        private val ISLAND_ICON_RES_ID_BA_CALENDAR_POOL =
            R.drawable.ic_ba_calendar_live_update
        private val ISLAND_ICON_RES_ID_GITHUB_SHARE_IMPORT =
            R.drawable.ic_github_invertocat_island_blue
        private val ISLAND_ART_RES_ID_BA_ARENA_REFRESH = R.drawable.item_icon_arenacoin
    }

    override fun build(payload: NotificationPayload): Notification {
        val state = payload.state
        val isBlueArchiveCafeAp = LiveNotificationPayload.isBaCafeApServerName(state.serverName)
        val isBlueArchiveAp = LiveNotificationPayload.isBaApServerName(state.serverName) || isBlueArchiveCafeAp
        val isBlueArchiveCafeVisit = LiveNotificationPayload.isBaCafeVisitServerName(state.serverName)
        val isBlueArchiveArenaRefresh = LiveNotificationPayload.isBaArenaRefreshServerName(state.serverName)
        val isBlueArchiveCalendarPool =
            LiveNotificationPayload.isBaCalendarPoolServerName(state.serverName)
        val isGitHubShareImport =
            LiveNotificationPayload.isGitHubShareImportServerName(state.serverName)
        val isWebDavSync =
            LiveNotificationPayload.isWebDavSyncServerName(state.serverName)
        val isBlueArchiveDailyDone =
            LiveNotificationPayload.isBaDailyDoneServerName(state.serverName)
        val isBlueArchiveNotification =
            isBlueArchiveAp ||
                    isBlueArchiveCafeVisit ||
                    isBlueArchiveArenaRefresh ||
                    isBlueArchiveCalendarPool ||
                    isBlueArchiveDailyDone
        val islandIconResId = when {
            isBlueArchiveAp -> ISLAND_ICON_RES_ID_AP
            isBlueArchiveCafeVisit -> ISLAND_ICON_RES_ID_BA_CAFE_VISIT
            isBlueArchiveArenaRefresh -> ISLAND_ICON_RES_ID_BA_ARENA_REFRESH
            isBlueArchiveCalendarPool -> ISLAND_ICON_RES_ID_BA_CALENDAR_POOL
            isBlueArchiveDailyDone -> ISLAND_ICON_RES_ID_BA_DAILY_DONE
            isGitHubShareImport -> ISLAND_ICON_RES_ID_GITHUB_SHARE_IMPORT
            else -> NotificationAppIconResolver.smallIconResId()
        }
        val shortCriticalText = resolveShortCriticalText(
            state = state,
            isBlueArchiveDailyDone = isBlueArchiveDailyDone,
            isBlueArchiveAp = isBlueArchiveAp,
            isBlueArchiveCafeVisit = isBlueArchiveCafeVisit,
            isBlueArchiveArenaRefresh = isBlueArchiveArenaRefresh,
            isBlueArchiveCalendarPool = isBlueArchiveCalendarPool,
            isGitHubShareImport = isGitHubShareImport,
            isWebDavSync = isWebDavSync
        )
        val presentation = resolvePresentation(
            state = state,
            isBlueArchiveDailyDone = isBlueArchiveDailyDone,
            isBlueArchiveAp = isBlueArchiveAp,
            isBlueArchiveCafeVisit = isBlueArchiveCafeVisit,
            isBlueArchiveArenaRefresh = isBlueArchiveArenaRefresh,
            isBlueArchiveCalendarPool = isBlueArchiveCalendarPool,
            isGitHubShareImport = isGitHubShareImport,
            isWebDavSync = isWebDavSync,
            miIslandProgressColorOverride = payload.miIslandProgressColorOverride
        )
        val resolvedAllowFloat = resolveAllowFloat(
            state = state,
            presentation = presentation,
            settings = payload.settings
        )
        val notificationTitle = state.title(context)
        val notificationContent = state.content(context)
        AppLogger.d(TAG) {
            "build server=${state.serverName} notificationId=${state.notificationId} " +
                "running=${state.running} ongoing=${state.ongoing} allowFloat=$resolvedAllowFloat " +
                "rawAllowFloat=${presentation.allowFloat} firstFloat=${payload.settings.miIslandFirstFloat} " +
                "finishFloat=${payload.settings.miIslandFinishFloat} updatable=${presentation.focusUpdatable} " +
                "showNotification=${presentation.focusShowNotification} " +
                "promoted=${presentation.effectiveRequestPromotedOngoing} " +
                "rawPromoted=${presentation.requestPromotedOngoing} " +
                "progress=${presentation.progressPercent} big=${presentation.bigTemplateKind} " +
                "small=${presentation.smallTemplateKind} channel=${payload.environment.channelId}"
        }
        val isCalendarPoolCountdown =
            isBlueArchiveCalendarPool && state.running && state.deadlineAtMs != null
        val isCalendarPoolUpdate =
            isBlueArchiveCalendarPool && state.running && state.deadlineAtMs == null
        val builder = NotificationCompat.Builder(context, payload.environment.channelId)
            .setSmallIcon(islandIconResId)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent.ifBlank { " " })
            .setContentIntent(state.openPendingIntent)
            .setCategory(
                when {
            isBlueArchiveAp && state.running -> NotificationCompat.CATEGORY_PROGRESS
            isCalendarPoolCountdown -> NotificationCompat.CATEGORY_PROGRESS
            isGitHubShareImport && state.running -> NotificationCompat.CATEGORY_PROGRESS
            isWebDavSync && state.running -> NotificationCompat.CATEGORY_PROGRESS
            !isBlueArchiveNotification && state.running -> NotificationCompat.CATEGORY_SERVICE
            else -> NotificationCompat.CATEGORY_STATUS
        }
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(presentation.notificationOngoing)
            .setOnlyAlertOnce(state.onlyAlertOnce)
            .setAutoCancel(isCalendarPoolUpdate && !presentation.notificationOngoing)
            .setRequestPromotedOngoing(presentation.effectiveRequestPromotedOngoing)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .applyDeadline(state.deadlineAtMs)

        val hasCustomDeleteAction = state.deletePendingIntent != state.stopPendingIntent
        if (state.stopPendingIntent != state.openPendingIntent &&
            (!presentation.notificationOngoing || hasCustomDeleteAction)
        ) {
            builder.setDeleteIntent(state.deletePendingIntent)
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
        if (presentation.expandedProgressKind != IslandExpandedProgressKind.NONE) {
            builder.setProgress(100, presentation.progressPercent.coerceIn(0, 100), false)
        }
        buildFocusExtras(payload, islandIconResId)?.let(builder::addExtras)
        return builder.build()
    }

    private fun buildFocusExtras(payload: NotificationPayload, islandIconResId: Int) = runCatching {
        val state = payload.state
        val isBlueArchiveCafeAp = LiveNotificationPayload.isBaCafeApServerName(state.serverName)
        val isBlueArchiveAp = LiveNotificationPayload.isBaApServerName(state.serverName) || isBlueArchiveCafeAp
        val isBlueArchiveCafeVisit = LiveNotificationPayload.isBaCafeVisitServerName(state.serverName)
        val isBlueArchiveArenaRefresh = LiveNotificationPayload.isBaArenaRefreshServerName(state.serverName)
        val isBlueArchiveCalendarPool =
            LiveNotificationPayload.isBaCalendarPoolServerName(state.serverName)
        val isGitHubShareImport =
            LiveNotificationPayload.isGitHubShareImportServerName(state.serverName)
        val isWebDavSync =
            LiveNotificationPayload.isWebDavSyncServerName(state.serverName)
        val isBlueArchiveDailyDone =
            LiveNotificationPayload.isBaDailyDoneServerName(state.serverName)
        val isBlueArchiveNotification =
            isBlueArchiveAp ||
                    isBlueArchiveCafeVisit ||
                    isBlueArchiveArenaRefresh ||
                    isBlueArchiveCalendarPool ||
                    isBlueArchiveDailyDone
        val presentation = resolvePresentation(
            state = state,
            isBlueArchiveDailyDone = isBlueArchiveDailyDone,
            isBlueArchiveAp = isBlueArchiveAp,
            isBlueArchiveCafeVisit = isBlueArchiveCafeVisit,
            isBlueArchiveArenaRefresh = isBlueArchiveArenaRefresh,
            isBlueArchiveCalendarPool = isBlueArchiveCalendarPool,
            isGitHubShareImport = isGitHubShareImport,
            isWebDavSync = isWebDavSync,
            miIslandProgressColorOverride = payload.miIslandProgressColorOverride
        )
        val useSemanticIcon = isBlueArchiveNotification || isGitHubShareImport
        // The arena slots carry the in-game coin art. The v1.11.0 negative-inset resource
        // wrapper rendered blank once SystemUI started rasterizing V3 pics, so the art
        // ships as a bitmap icon, which the pic pipeline draws without drawable inflation.
        val arenaArtIcon = if (isBlueArchiveArenaRefresh) loadArenaCoinArtIcon() else null
        val lightLogoIcon = arenaArtIcon ?: if (useSemanticIcon) {
            Icon.createWithResource(context, islandIconResId)
        } else {
            Icon.createWithResource(context, islandIconResId).setTint(Color.BLACK)
        }
        val darkLogoIcon = arenaArtIcon ?: if (useSemanticIcon) {
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
        val displayIcon =
            payload.semanticIconBitmap
                ?.let(Icon::createWithBitmap)
                ?: arenaArtIcon
                ?: if (useSemanticIcon) {
                    Icon.createWithResource(context, islandIconResId)
                } else {
                    NotificationAppIconResolver.largeIconBitmap(context)
                        ?.let(Icon::createWithBitmap)
                        ?: Icon.createWithResource(context, islandIconResId)
                }
        val resolvedAllowFloat = resolveAllowFloat(
            state = state,
            presentation = presentation,
            settings = payload.settings
        )

        MiFocusProtocolNotification.buildV3 {
            val lightLogoKey = createPicture("key_logo_light", lightLogoIcon)
            val darkLogoKey = createPicture("key_logo_dark", darkLogoIcon)
            val displayIconKey = createPicture("key_logo_display", displayIcon)

            islandFirstFloat = payload.settings.miIslandFirstFloat
            enableFloat = resolvedAllowFloat
            updatable = presentation.focusUpdatable
            timeout = payload.settings.miIslandFocusTimeoutMinutes
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
                islandTimeout = payload.settings.miIslandTimeoutSeconds
                highlightColor = presentation.notificationAccentColor
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
                                    showHighlightColor = presentation.notificationAccentColor != null
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

                        IslandBigTemplateKind.FIXED_DIGIT -> {
                            fixedWidthDigitInfo {
                                content = context.getString(R.string.mcp_clients_label)
                                digit = presentation.compactTitle
                                showHighlightColor = true
                                turnAnim = true
                            }
                        }

                        IslandBigTemplateKind.TEXT -> {
                            imageTextInfoRight {
                                type = 3
                                textInfo {
                                    title = presentation.compactTitle
                                    showHighlightColor = presentation.notificationAccentColor != null
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

                        IslandSmallTemplateKind.ICON_TEXT -> {
                            imageTextInfoRight {
                                type = 6
                                picInfo {
                                    type = 4
                                    pic = displayIconKey
                                }
                                textInfo {
                                    title = presentation.compactTitle
                                    showHighlightColor = true
                                    narrowFont = true
                                }
                            }
                        }
                    }
                }
            }

            baseInfo {
                type = 2
                title = resolveFocusTitle(state)
                val resolvedSpecialTitle =
                    state.miFocusSpecialTitle
                        ?: if (
                            state.running &&
                            !isBlueArchiveNotification &&
                            !isGitHubShareImport &&
                            !isWebDavSync
                        ) {
                            state.statusText(context)
                        } else {
                            null
                        }
                resolvedSpecialTitle
                    ?.let { compactFocusText(it, maxLength = 12) }
                    ?.takeIf { it.isNotBlank() }
                    ?.let { versionLabel ->
                        specialTitle = versionLabel
                        colorSpecialTitle = HIGHLIGHT_TITLE_COLOR
                        colorSpecialTitleDark = HIGHLIGHT_TITLE_COLOR
                        colorSpecialBg = presentation.notificationAccentColor
                    }
                content = resolveFocusContent(
                    state = state,
                    presentation = presentation,
                    isGitHubShareImport = isGitHubShareImport
                ).ifBlank { " " }
                colorTitle = resolveFocusTitleColor(presentation)
                colorTitleDark = resolveFocusTitleColor(presentation)
                colorContent = resolveFocusContentColor(isGitHubShareImport)
                colorContentDark = resolveFocusContentColor(isGitHubShareImport)
            }

            when (presentation.expandedProgressKind) {
                IslandExpandedProgressKind.NONE -> Unit

                IslandExpandedProgressKind.BAR -> {
                    progressInfo {
                        progress = presentation.progressPercent.coerceIn(0, 100)
                        isCCW = true
                        colorProgress = presentation.progressColor
                        colorProgressEnd = presentation.progressColor
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
        AppLogger.e(TAG, "Build local Focus protocol extras failed", it)
    }.getOrNull()

    private fun loadArenaCoinArtIcon(): Icon? =
        runCatching {
            BitmapFactory.decodeResource(context.resources, ISLAND_ART_RES_ID_BA_ARENA_REFRESH)
                ?.let(Icon::createWithBitmap)
        }.onFailure {
            AppLogger.w(TAG) { "Decode arena coin art failed, falling back to vector icon" }
        }.getOrNull()

    private fun resolvePresentation(
        state: LiveNotificationPayload,
        isBlueArchiveDailyDone: Boolean,
        isBlueArchiveAp: Boolean,
        isBlueArchiveCafeVisit: Boolean,
        isBlueArchiveArenaRefresh: Boolean,
        isBlueArchiveCalendarPool: Boolean,
        isGitHubShareImport: Boolean,
        isWebDavSync: Boolean,
        miIslandProgressColorOverride: String? = null
    ): IslandPresentation {
        // Terminal by construction, and the only entry here that is. A daily-done notification is posted
        // *after* the template has already been applied, so there is no session to report on: no progress
        // bar (the doc's "这些阶段不写模拟进度"), not ongoing, and no promoted-ongoing request — that one
        // requires FLAG_ONGOING_EVENT, and asking for it on a finished event is what makes HyperOS retire
        // the island and leave a plain notification behind.
        //
        // `focusUpdatable` with a stable order id from the dispatcher means a second run rewrites the first
        // island rather than stacking a second one, which matches the single fixed notification id.
        if (isBlueArchiveDailyDone) {
            return IslandPresentation(
                allowFloat = true,
                showTextButtons = true,
                bigTemplateKind = IslandBigTemplateKind.TEXT,
                smallTemplateKind = IslandSmallTemplateKind.ICON,
                compactTitle = context.getString(R.string.ba_daily_done_notification_island_text),
                notificationOngoing = false,
                requestPromotedOngoing = false,
                focusUpdatable = true,
                focusShowNotification = true,
                expandedProgressKind = IslandExpandedProgressKind.NONE,
                notificationAccentColor = BA_DAILY_DONE_ACCENT_COLOR,
                primaryActionColor = BA_DAILY_DONE_ACCENT_COLOR
            )
        }
        if (isBlueArchiveAp && state.running) {
            return IslandPresentation(
                allowFloat = true,
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
                expandedProgressKind = IslandExpandedProgressKind.BAR,
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
                expandedProgressKind = IslandExpandedProgressKind.NONE,
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
                requestPromotedOngoing = state.ongoing,
                focusUpdatable = true,
                focusShowNotification = true,
                expandedProgressKind = if (useProgressTemplate) {
                    IslandExpandedProgressKind.BAR
                } else {
                    IslandExpandedProgressKind.NONE
                },
                progressPercent = progressPercent,
                progressColor = progressColor,
                notificationAccentColor = progressColor,
                primaryActionColor = GITHUB_SHARE_IMPORT_ACTION_PRIMARY_COLOR
            )
        }
        if (isWebDavSync && state.running) {
            val progressPercent = state.overrideProgressPercent
                ?.coerceIn(0, 100)
                ?: state.port.coerceIn(0, 100)
            val progressColor = miIslandProgressColorOverride
                ?: state.overrideAccentColor
                ?: WEBDAV_ACCENT_COLOR
            return IslandPresentation(
                allowFloat = true,
                showTextButtons = true,
                bigTemplateKind = IslandBigTemplateKind.PROGRESS_TEXT,
                smallTemplateKind = IslandSmallTemplateKind.PROGRESS_ICON,
                compactTitle = resolveCompactTitle(
                    raw = state.onlineText(context),
                    fallback = state.shortText,
                    maxTextLength = 8
                ),
                compactContent = state.shortText.takeIf { it.isNotBlank() },
                notificationOngoing = state.ongoing,
                requestPromotedOngoing = state.ongoing,
                focusUpdatable = true,
                focusShowNotification = true,
                expandedProgressKind = IslandExpandedProgressKind.BAR,
                progressPercent = progressPercent,
                progressColor = progressColor,
                notificationAccentColor = progressColor,
                primaryActionColor = progressColor
            )
        }
        if (isWebDavSync) {
            val accentColor = state.overrideAccentColor
                ?: resolveWebDavTerminalAccentColor(state)
            return IslandPresentation(
                allowFloat = true,
                showTextButtons = true,
                compactTitle = resolveCompactTitle(
                    raw = state.onlineText(context),
                    fallback = state.statusText(context),
                    maxTextLength = 8
                ),
                compactContent = state.shortText.takeIf { it.isNotBlank() },
                notificationOngoing = state.ongoing,
                requestPromotedOngoing = state.ongoing,
                focusUpdatable = true,
                focusShowNotification = true,
                notificationAccentColor = accentColor,
                primaryActionColor = accentColor
            )
        }
        if (state.running) {
            return IslandPresentation(
                allowFloat = false,
                showTextButtons = false,
                bigTemplateKind = IslandBigTemplateKind.FIXED_DIGIT,
                smallTemplateKind = IslandSmallTemplateKind.ICON_TEXT,
                compactTitle = state.clients.coerceAtLeast(0).toString(),
                notificationOngoing = state.ongoing,
                requestPromotedOngoing = state.ongoing,
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

    private fun resolveAllowFloat(
        state: LiveNotificationPayload,
        presentation: IslandPresentation,
        settings: UserSettings
    ): Boolean {
        if (!presentation.allowFloat) return false
        if (!state.running) return settings.miIslandFinishFloat
        return if (shouldFloatRunningState(state, presentation)) {
            settings.miIslandFirstFloat
        } else {
            false
        }
    }

    private fun shouldFloatRunningState(
        state: LiveNotificationPayload,
        presentation: IslandPresentation
    ): Boolean {
        if (!state.onlyAlertOnce && presentation.notificationOngoing) return true
        return !presentation.notificationOngoing &&
                !presentation.effectiveRequestPromotedOngoing
    }

    private fun resolveShortCriticalText(
        state: LiveNotificationPayload,
        isBlueArchiveDailyDone: Boolean,
        isBlueArchiveAp: Boolean,
        isBlueArchiveCafeVisit: Boolean,
        isBlueArchiveArenaRefresh: Boolean,
        isBlueArchiveCalendarPool: Boolean,
        isGitHubShareImport: Boolean,
        isWebDavSync: Boolean
    ): String? {
        return when {
            // Ahead of the `!state.running` arm on purpose. This notification is dispatched with
            // `running = true` so the shared pipeline treats it as a real event rather than a stopped
            // service, which previously dropped it into the trailing `else` and put MCP's
            // server-online string in the island.
            isBlueArchiveDailyDone ->
                context.getString(R.string.ba_daily_done_notification_island_text)
            isWebDavSync -> state.shortText.ifBlank { state.onlineText(context) }
            !state.running -> state.statusText(context)
            isBlueArchiveAp -> context.getString(R.string.ba_notification_ap_island_text)
            isBlueArchiveCalendarPool -> state.shortText
            isGitHubShareImport -> state.onlineText(context)
            isBlueArchiveCafeVisit || isBlueArchiveArenaRefresh -> state.onlineText(context)
            else -> state.onlineText(context)
        }
            .takeIf { it.isNotBlank() }
            ?.let { compactFocusText(it, maxLength = 8) }
    }

    private fun resolveIslandActions(
        state: LiveNotificationPayload,
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

    private fun MiFocusProtocolActionInfo.applyIslandActionStyle(
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
        state: LiveNotificationPayload,
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

    private fun resolveDefaultEndpointSummary(state: LiveNotificationPayload): String? {
        if (!state.running) return null
        val path = state.path.trim().ifBlank { "/mcp" }
        val port = state.port.coerceAtLeast(0)
        return if (path.length <= 12) {
            "$port $path"
        } else {
            port.toString()
        }
    }

    private fun resolveFocusTitle(state: LiveNotificationPayload): String {
        val raw = state.miFocusTitle?.takeIf { it.isNotBlank() } ?: state.title(context)
        return compactFocusText(raw, maxLength = 18)
    }

    private fun resolveFocusContent(
        state: LiveNotificationPayload,
        presentation: IslandPresentation,
        isGitHubShareImport: Boolean
    ): String {
        val raw = state.miFocusContent?.takeIf { it.isNotBlank() } ?: state.content(context)
        val fallback = listOfNotNull(
            presentation.compactTitle.takeIf { it.isNotBlank() },
            presentation.compactContent?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        return compactFocusText(
            raw = raw.ifBlank { fallback },
            maxLength = if (isGitHubShareImport) 28 else 34
        )
    }

    private fun resolveFocusTitleColor(presentation: IslandPresentation): String? {
        return presentation.notificationAccentColor
    }

    private fun resolveFocusContentColor(isGitHubShareImport: Boolean): String? {
        return if (isGitHubShareImport) {
            GITHUB_SHARE_IMPORT_ACTION_NEUTRAL_TITLE_COLOR
        } else {
            null
        }
    }

    private fun resolveWebDavTerminalAccentColor(state: LiveNotificationPayload): String {
        val text = listOf(
            state.onlineText(context),
            state.shortText,
            state.content(context),
        ).joinToString(separator = " ").lowercase()
        return when {
            listOf("fail", "failed", "error", "失败").any(text::contains) -> WEBDAV_DANGER_COLOR
            listOf("review", "需处理", "conflict").any(text::contains) -> WEBDAV_NEEDS_REVIEW_COLOR
            listOf("success", "完成", "done").any(text::contains) -> WEBDAV_SUCCESS_COLOR
            else -> WEBDAV_ACCENT_COLOR
        }
    }

    private fun resolveApProgressPercent(state: LiveNotificationPayload): Int {
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

    private fun compactFocusText(raw: String, maxLength: Int): String {
        val trimmed = raw.trim()
        if (trimmed.length <= maxLength) return trimmed
        val end = (maxLength - 3).coerceAtLeast(1)
        return trimmed.take(end).trimEnd() + "..."
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

    private fun MiFocusProtocolTemplateV3.focusShowNotification(show: Boolean?) {
        if (show != null) {
            isShowNotification = show
        }
    }
}
