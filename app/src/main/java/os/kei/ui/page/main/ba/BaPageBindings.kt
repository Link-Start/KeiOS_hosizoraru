package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.BASettingsStore
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
        cafeApNotifyEnabled = draft.cafeApNotifyEnabled,
        arenaRefreshNotifyEnabled = draft.arenaRefreshNotifyEnabled,
        cafeVisitNotifyEnabled = draft.cafeVisitNotifyEnabled,
        calendarUpcomingNotifyEnabled = draft.calendarUpcomingNotifyEnabled,
        calendarEndingNotifyEnabled = draft.calendarEndingNotifyEnabled,
        poolUpcomingNotifyEnabled = draft.poolUpcomingNotifyEnabled,
        poolEndingNotifyEnabled = draft.poolEndingNotifyEnabled,
        calendarPoolChangeNotifyEnabled = draft.calendarPoolChangeNotifyEnabled,
        calendarPoolNotifyLeadHours = draft.calendarPoolNotifyLeadHours,
        apNotifyThresholdText = draft.apNotifyThresholdText,
        cafeApNotifyThresholdText = draft.cafeApNotifyThresholdText,
    )
}

internal fun buildBaPageContentState(
    isPageActive: Boolean,
    officeOverviewTitle: String,
    office: BaOfficeController,
    routeState: BaPageRouteState,
    clockState: BaPageClockState,
    serverOptions: List<String>,
    cafeLevelOptions: List<Int>,
): BaPageContentState {
    val popupState = routeState.popupState
    return BaPageContentState(
        isPageActive = isPageActive,
        officeOverviewTitle = officeOverviewTitle,
        officeState = office.state(),
        clockState = clockState,
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
    office.applyRuntimeTickAndPersist()

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

    office.applyRuntimeTickAndPersist()
    AppBackgroundScheduler.scheduleBaApThreshold(context)
    ui.closeSettingsSheet(office)
}

internal fun saveBaNotificationSettings(
    context: Context,
    office: BaOfficeController,
    ui: BaPageUiController,
    notificationSettingsSheetState: BaNotificationSettingsSheetState,
) {
    office.applyRuntimeTickAndPersist()
    val previousCafeApNotifyEnabled = office.cafeApNotifyEnabled
    val previousCafeApNotifyThreshold = office.cafeApNotifyThreshold
    val previousArenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled
    val previousCafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled
    val persisted = persistBaNotificationSettingsDraft(notificationSettingsSheetState)

    office.apNotifyEnabled = notificationSettingsSheetState.apNotifyEnabled
    office.cafeApNotifyEnabled = persisted.cafeApNotifyEnabled
    office.arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled
    office.cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled
    office.apNotifyThreshold = persisted.savedThreshold
    office.cafeApNotifyThreshold = persisted.savedCafeApThreshold
    if (!office.cafeApNotifyEnabled) {
        office.cafeApLastNotifiedLevel = -1
        BASettingsStore.saveCafeApLastNotifiedLevel(-1)
    } else if (!previousCafeApNotifyEnabled ||
        previousCafeApNotifyThreshold != office.cafeApNotifyThreshold
    ) {
        office.cafeApLastNotifiedLevel = -1
        BASettingsStore.saveCafeApLastNotifiedLevel(-1)
    }
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

    office.applyRuntimeTickAndPersist()
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
    return BaOfficeActionCoordinator(
        context = context,
        office = office,
        ui = ui,
        onRefreshCalendar = onRefreshCalendar,
        onRefreshPool = onRefreshPool,
        onOpenCalendarLink = onOpenCalendarLink,
        onOpenPoolStudentGuide = onOpenPoolStudentGuide,
        onOpenGuideCatalog = onOpenGuideCatalog,
    ).buildContentActions()
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
