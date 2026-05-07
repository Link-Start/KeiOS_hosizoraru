package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs

internal fun buildBaSettingsSheetState(
    draft: BaPageSettingsDraftState,
    calendarRefreshIntervalHours: Int,
): BaSettingsSheetState {
    return BaSettingsSheetState(
        cafeLevel = draft.cafeLevel,
        mediaAdaptiveRotationEnabled = draft.mediaAdaptiveRotationEnabled,
        mediaSaveCustomEnabled = draft.mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri = draft.mediaSaveFixedTreeUri,
        idIndependentByServer = draft.idIndependentByServer,
        showEndedActivities = draft.showEndedActivities,
        showEndedPools = draft.showEndedPools,
        showCalendarPoolImages = draft.showCalendarPoolImages,
        calendarRefreshIntervalHours = calendarRefreshIntervalHours,
    )
}

internal fun buildBaNotificationSettingsSheetState(
    draft: BaPageNotificationDraftState,
): BaNotificationSettingsSheetState {
    return BaNotificationSettingsSheetState(
        apNotifyEnabled = draft.apNotifyEnabled,
        arenaRefreshNotifyEnabled = draft.arenaRefreshNotifyEnabled,
        cafeVisitNotifyEnabled = draft.cafeVisitNotifyEnabled,
        calendarUpcomingNotifyEnabled = draft.calendarUpcomingNotifyEnabled,
        calendarEndingNotifyEnabled = draft.calendarEndingNotifyEnabled,
        poolUpcomingNotifyEnabled = draft.poolUpcomingNotifyEnabled,
        poolEndingNotifyEnabled = draft.poolEndingNotifyEnabled,
        calendarPoolChangeNotifyEnabled = draft.calendarPoolChangeNotifyEnabled,
        calendarPoolNotifyLeadHours = draft.calendarPoolNotifyLeadHours,
        apNotifyThresholdText = draft.apNotifyThresholdText,
    )
}

internal fun buildBaPageContentState(
    isPageActive: Boolean,
    officeOverviewTitle: String,
    office: BaOfficeController,
    routeState: BaPageRouteState,
    serverOptions: List<String>,
    cafeLevelOptions: List<Int>,
): BaPageContentState {
    val popupState = routeState.popupState
    return BaPageContentState(
        isPageActive = isPageActive,
        officeOverviewTitle = officeOverviewTitle,
        officeState = office.state(),
        uiNowMs = routeState.uiNowMs,
        serverOptions = serverOptions,
        cafeLevelOptions = cafeLevelOptions,
        serverIndex = routeState.serverIndex,
        showOverviewServerPopup = popupState.showOverviewServerPopup,
        showCafeLevelPopup = popupState.showCafeLevelPopup,
        overviewServerPopupAnchorBounds = popupState.overviewServerPopupAnchorBounds,
        cafeLevelPopupAnchorBounds = popupState.cafeLevelPopupAnchorBounds,
        baCalendarEntries = routeState.calendarUiState.entries,
        baCalendarLoading = routeState.calendarUiState.loading,
        baCalendarError = routeState.calendarUiState.error,
        baCalendarLastSyncMs = routeState.calendarUiState.lastSyncMs,
        showEndedActivities = routeState.showEndedActivities,
        showCalendarPoolImages = routeState.showCalendarPoolImages,
        baPoolEntries = routeState.poolUiState.entries,
        baPoolLoading = routeState.poolUiState.loading,
        baPoolError = routeState.poolUiState.error,
        baPoolLastSyncMs = routeState.poolUiState.lastSyncMs,
        showEndedPools = routeState.showEndedPools,
    )
}

internal fun saveBaPageSettings(
    context: Context,
    office: BaOfficeController,
    ui: BaPageUiController,
    settingsSheetState: BaSettingsSheetState,
    onRefreshCalendar: (Boolean) -> Unit,
    onRefreshPool: (Boolean) -> Unit,
) {
    office.applyCafeStorage()

    val persisted = persistBaSettingsDraft(
        sheetState = settingsSheetState,
        currentCafeLevel = office.cafeLevel,
        currentShowEndedActivities = ui.showEndedActivities,
        currentShowCalendarPoolImages = ui.showCalendarPoolImages,
    )

    office.cafeLevel = persisted.savedCafeLevel
    office.clampCafeStoredToCap()
    ui.showEndedPools = persisted.showEndedPools
    ui.showEndedActivities = persisted.showEndedActivities
    ui.showCalendarPoolImages = persisted.showCalendarPoolImages
    ui.mediaAdaptiveRotationEnabled = persisted.mediaAdaptiveRotationEnabled
    ui.mediaSaveCustomEnabled = persisted.mediaSaveCustomEnabled
    ui.mediaSaveFixedTreeUri = persisted.mediaSaveFixedTreeUri
    ui.idIndependentByServer = persisted.idIndependentByServer
    office.applyIdIndependentByServer(
        serverIndex = ui.serverIndex,
        enabled = persisted.idIndependentByServer
    )

    if (persisted.turningEndedActivitiesOn) {
        val (calendarCacheRaw, _) = BASettingsStore.loadCalendarCache(ui.serverIndex)
        if (calendarCacheRaw.isBlank()) onRefreshCalendar(true)
    }

    if (persisted.turningImagesOn) {
        val calendarHasImage = hasAnyImageInBaCalendarCache(ui.serverIndex)
        val poolHasImage = hasAnyImageInBaPoolCache(ui.serverIndex)
        if (!calendarHasImage) onRefreshCalendar(true)
        if (!poolHasImage) onRefreshPool(true)
    }

    office.applyApRegen()
    AppBackgroundScheduler.scheduleBaApThreshold(context)
    ui.closeSettingsSheet(office)
}

internal fun saveBaNotificationSettings(
    context: Context,
    office: BaOfficeController,
    ui: BaPageUiController,
    notificationSettingsSheetState: BaNotificationSettingsSheetState,
) {
    val previousArenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled
    val previousCafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled
    val persisted = persistBaNotificationSettingsDraft(notificationSettingsSheetState)

    office.apNotifyEnabled = notificationSettingsSheetState.apNotifyEnabled
    office.arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled
    office.cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled
    office.apNotifyThreshold = persisted.savedThreshold
    if (!office.arenaRefreshNotifyEnabled) {
        office.arenaRefreshLastNotifiedSlotMs = 0L
        BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(0L)
    } else if (!previousArenaRefreshNotifyEnabled) {
        val baselineSlotMs = currentArenaRefreshSlotMs(
            nowMs = System.currentTimeMillis(),
            serverIndex = ui.serverIndex
        )
        office.arenaRefreshLastNotifiedSlotMs = baselineSlotMs
        BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(baselineSlotMs)
    }
    if (!office.cafeVisitNotifyEnabled) {
        office.cafeVisitLastNotifiedSlotMs = 0L
        BASettingsStore.saveCafeVisitLastNotifiedSlotMs(0L)
    } else if (!previousCafeVisitNotifyEnabled) {
        val baselineSlotMs = currentCafeStudentRefreshSlotMs(
            nowMs = System.currentTimeMillis(),
            serverIndex = ui.serverIndex
        )
        office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
        BASettingsStore.saveCafeVisitLastNotifiedSlotMs(baselineSlotMs)
    }

    office.applyApRegen()
    AppBackgroundScheduler.scheduleBaApThreshold(context)
    ui.closeNotificationSettingsSheet(office)
}

internal fun buildBaPageContentActions(
    context: Context,
    office: BaOfficeController,
    ui: BaPageUiController,
    onRefreshCalendar: () -> Unit,
    onRefreshPool: () -> Unit,
    onOpenCalendarLink: (String) -> Unit,
    onOpenPoolStudentGuide: (String) -> Unit,
    onOpenGuideCatalog: () -> Unit,
): BaPageContentActions {
    return BaPageContentActions(
        onApCurrentInputChange = { office.apCurrentInput = it },
        onApCurrentDone = {
            val finalValue = office.apCurrentInput.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 0
            office.updateCurrentAp(finalValue, markSync = true)
            AppBackgroundScheduler.scheduleBaApThreshold(context)
            office.apCurrentInput = finalValue.toString()
        },
        onApLimitInputChange = { office.apLimitInput = it },
        onApLimitDone = {
            val finalValue = office.apLimitInput.toIntOrNull()?.coerceIn(0, BA_AP_LIMIT_MAX)
                ?: BA_AP_LIMIT_MAX
            office.updateApLimit(finalValue)
            office.applyApRegen()
            AppBackgroundScheduler.scheduleBaApThreshold(context)
            office.apLimitInput = finalValue.toString()
        },
        onOverviewServerPopupAnchorBoundsChange = { ui.overviewServerPopupAnchorBounds = it },
        onOverviewServerPopupChange = { ui.showOverviewServerPopup = it },
        onCafeLevelPopupAnchorBoundsChange = { ui.cafeLevelPopupAnchorBounds = it },
        onCafeLevelPopupChange = { ui.showCafeLevelPopup = it },
        onCafeLevelChange = { level ->
            val normalized = level.coerceIn(1, 10)
            office.applyCafeStorage()
            office.cafeLevel = normalized
            office.clampCafeStoredToCap()
            BASettingsStore.saveCafeLevel(normalized)
            ui.sheetCafeLevel = normalized
            ui.showCafeLevelPopup = false
        },
        onServerSelected = { selected ->
            ui.serverIndex = selected
            BASettingsStore.saveServerIndex(selected)
            office.loadIdForServer(selected)
            if (office.cafeVisitNotifyEnabled) {
                val baselineSlotMs = currentCafeStudentRefreshSlotMs(
                    nowMs = System.currentTimeMillis(),
                    serverIndex = selected
                )
                office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
                BASettingsStore.saveCafeVisitLastNotifiedSlotMs(baselineSlotMs)
            }
            if (office.arenaRefreshNotifyEnabled) {
                val baselineSlotMs = currentArenaRefreshSlotMs(
                    nowMs = System.currentTimeMillis(),
                    serverIndex = selected
                )
                office.arenaRefreshLastNotifiedSlotMs = baselineSlotMs
                BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(baselineSlotMs)
            }
            onRefreshCalendar()
            onRefreshPool()
            AppBackgroundScheduler.scheduleBaApThreshold(context)
            ui.showOverviewServerPopup = false
        },
        onClaimCafeStoredAp = {
            office.claimCafeStoredAp(context)
            AppBackgroundScheduler.scheduleBaApThreshold(context)
        },
        onTouchHead = { office.touchHead(ui.serverIndex) },
        onForceResetHeadpatCooldown = { office.forceResetHeadpatCooldown() },
        onUseInviteTicket1 = { office.useInviteTicket1() },
        onForceResetInviteTicket1Cooldown = { office.forceResetInviteTicket1Cooldown() },
        onUseInviteTicket2 = { office.useInviteTicket2() },
        onForceResetInviteTicket2Cooldown = { office.forceResetInviteTicket2Cooldown() },
        onRefreshCalendar = onRefreshCalendar,
        onOpenCalendarLink = onOpenCalendarLink,
        onRefreshPool = onRefreshPool,
        onOpenPoolStudentGuide = onOpenPoolStudentGuide,
        onOpenGuideCatalog = onOpenGuideCatalog,
        onIdNicknameInputChange = { office.idNicknameInput = it },
        onSaveIdNickname = { office.saveIdNicknameFromInput(ui.serverIndex) },
        onIdFriendCodeInputChange = { office.idFriendCodeInput = it },
        onSaveIdFriendCode = { office.saveIdFriendCodeFromInput(context, ui.serverIndex) },
    )
}

internal fun applyBaCalendarRefreshInterval(
    ui: BaPageUiController,
    hours: Int,
    calendarLastSyncMs: Long,
    onRefreshCalendar: () -> Unit,
    onRefreshPool: () -> Unit,
) {
    ui.calendarRefreshIntervalHours = hours
    BASettingsStore.saveCalendarRefreshIntervalHours(hours)
    val elapsed = (System.currentTimeMillis() - calendarLastSyncMs).coerceAtLeast(0L)
    if (calendarLastSyncMs <= 0L || elapsed >= hours * 60L * 60L * 1000L) {
        onRefreshCalendar()
        onRefreshPool()
    }
}
