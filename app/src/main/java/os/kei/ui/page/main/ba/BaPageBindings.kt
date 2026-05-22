package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs

internal fun buildBaSettingsSheetState(
    draft: BaPageSettingsDraftState,
    calendarRefreshIntervalHours: Int,
): BaSettingsSheetState =
    BaSettingsSheetState(
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

internal fun buildBaNotificationSettingsSheetState(draft: BaPageNotificationDraftState): BaNotificationSettingsSheetState =
    BaNotificationSettingsSheetState(
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

internal fun buildSavedBaNotificationSettingsSheetState(office: BaOfficeController): BaNotificationSettingsSheetState {
    val calendarPoolNotifications = BaSettingsPersistenceRepository.loadCalendarPoolNotificationSettings()
    return BaNotificationSettingsSheetState(
        apNotifyEnabled = office.apNotifyEnabled,
        cafeApNotifyEnabled = office.cafeApNotifyEnabled,
        arenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled,
        cafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled,
        calendarUpcomingNotifyEnabled = calendarPoolNotifications.calendarUpcomingNotifyEnabled,
        calendarEndingNotifyEnabled = calendarPoolNotifications.calendarEndingNotifyEnabled,
        poolUpcomingNotifyEnabled = calendarPoolNotifications.poolUpcomingNotifyEnabled,
        poolEndingNotifyEnabled = calendarPoolNotifications.poolEndingNotifyEnabled,
        calendarPoolChangeNotifyEnabled = calendarPoolNotifications.calendarPoolChangeNotifyEnabled,
        calendarPoolNotifyLeadHours = calendarPoolNotifications.calendarPoolNotifyLeadHours,
        apNotifyThresholdText = office.apNotifyThreshold.toString(),
        cafeApNotifyThresholdText = office.cafeApNotifyThreshold.toString(),
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

    val persisted =
        BaSettingsPersistenceRepository.persistSettingsDraft(
            sheetState = settingsSheetState,
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
        enabled = persisted.idIndependentByServer,
    )

    if (persisted.turningEndedActivitiesOn) {
        if (BaSettingsPersistenceRepository.calendarCacheIsBlank(ui.serverIndex)) {
            onRefreshCalendar(true)
        }
    }

    if (persisted.turningImagesOn) {
        val calendarHasImage = BaSettingsPersistenceRepository.hasAnyImageInCalendarCache(ui.serverIndex)
        val poolHasImage = BaSettingsPersistenceRepository.hasAnyImageInPoolCache(ui.serverIndex)
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
    val persisted = BaSettingsPersistenceRepository.persistNotificationSettingsDraft(notificationSettingsSheetState)

    office.apNotifyEnabled = notificationSettingsSheetState.apNotifyEnabled
    office.cafeApNotifyEnabled = persisted.cafeApNotifyEnabled
    office.arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled
    office.cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled
    office.apNotifyThreshold = persisted.savedThreshold
    office.cafeApNotifyThreshold = persisted.savedCafeApThreshold
    if (!office.cafeApNotifyEnabled) {
        office.cafeApLastNotifiedLevel = -1
        BaSettingsPersistenceRepository.resetCafeApLastNotifiedLevel()
    } else if (!previousCafeApNotifyEnabled ||
        previousCafeApNotifyThreshold != office.cafeApNotifyThreshold
    ) {
        office.cafeApLastNotifiedLevel = -1
        BaSettingsPersistenceRepository.resetCafeApLastNotifiedLevel()
    }
    if (!office.arenaRefreshNotifyEnabled) {
        office.arenaRefreshLastNotifiedSlotMs = 0L
        BaSettingsPersistenceRepository.resetArenaRefreshLastNotifiedSlot()
    } else if (!previousArenaRefreshNotifyEnabled) {
        val baselineSlotMs =
            currentArenaRefreshSlotMs(
                nowMs = System.currentTimeMillis(),
                serverIndex = ui.serverIndex,
            )
        office.arenaRefreshLastNotifiedSlotMs = baselineSlotMs
        BaSettingsPersistenceRepository.saveArenaRefreshLastNotifiedSlot(baselineSlotMs)
    }
    if (!office.cafeVisitNotifyEnabled) {
        office.cafeVisitLastNotifiedSlotMs = 0L
        BaSettingsPersistenceRepository.resetCafeVisitLastNotifiedSlot()
    } else if (!previousCafeVisitNotifyEnabled) {
        val baselineSlotMs =
            currentCafeStudentRefreshSlotMs(
                nowMs = System.currentTimeMillis(),
                serverIndex = ui.serverIndex,
            )
        office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
        BaSettingsPersistenceRepository.saveCafeVisitLastNotifiedSlot(baselineSlotMs)
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
): BaPageContentActions =
    BaOfficeActionCoordinator(
        context = context,
        office = office,
        ui = ui,
        onRefreshCalendar = onRefreshCalendar,
        onRefreshPool = onRefreshPool,
        onOpenCalendarLink = onOpenCalendarLink,
        onOpenPoolStudentGuide = onOpenPoolStudentGuide,
        onOpenGuideCatalog = onOpenGuideCatalog,
    ).buildContentActions()

internal fun applyBaCalendarRefreshInterval(
    ui: BaPageUiController,
    hours: Int,
    calendarLastSyncMs: Long,
    onRefreshCalendar: () -> Unit,
    onRefreshPool: () -> Unit,
) {
    val persisted =
        BaSettingsPersistenceRepository.persistRefreshInterval(
            hours = hours,
            calendarLastSyncMs = calendarLastSyncMs,
        )
    ui.calendarRefreshIntervalHours = persisted.hours
    if (persisted.shouldRefresh) {
        onRefreshCalendar()
        onRefreshPool()
    }
}
