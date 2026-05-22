package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    scope: CoroutineScope,
    runtimePersistenceCoordinator: BaRuntimePersistenceCoordinator,
    settingsSheetState: BaSettingsSheetState,
    onRefreshCalendar: (Boolean) -> Unit,
    onRefreshPool: (Boolean) -> Unit,
) {
    runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
    scope.launch {
        val persisted =
            BaSettingsPersistenceRepository.persistSettingsDraftAsync(
                sheetState = settingsSheetState,
                currentShowEndedActivities = ui.showEndedActivities,
                currentShowCalendarPoolImages = ui.showCalendarPoolImages,
            )

        office.cafeLevel = persisted.savedCafeLevel
        runtimePersistenceCoordinator.submit(office.clampCafeStoredToCapUpdate())
        ui.showEndedPools = persisted.showEndedPools
        ui.showEndedActivities = persisted.showEndedActivities
        ui.showCalendarPoolImages = persisted.showCalendarPoolImages
        ui.mediaAdaptiveRotationEnabled = persisted.mediaAdaptiveRotationEnabled
        ui.mediaSaveCustomEnabled = persisted.mediaSaveCustomEnabled
        ui.mediaSaveFixedTreeUri = persisted.mediaSaveFixedTreeUri
        ui.idIndependentByServer = persisted.idIndependentByServer
        office
            .applyIdIndependentByServer(
                serverIndex = ui.serverIndex,
                enabled = persisted.idIndependentByServer,
            ).persistAsync()

        if (persisted.turningEndedActivitiesOn) {
            if (BaSettingsPersistenceRepository.calendarCacheIsBlankAsync(ui.serverIndex)) {
                onRefreshCalendar(true)
            }
        }

        if (persisted.turningImagesOn) {
            val calendarHasImage = BaSettingsPersistenceRepository.hasAnyImageInCalendarCacheAsync(ui.serverIndex)
            val poolHasImage = BaSettingsPersistenceRepository.hasAnyImageInPoolCacheAsync(ui.serverIndex)
            if (!calendarHasImage) onRefreshCalendar(true)
            if (!poolHasImage) onRefreshPool(true)
        }

        runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
        AppBackgroundScheduler.scheduleBaApThreshold(context)
        ui.closeSettingsSheet(office)
    }
}

internal fun saveBaNotificationSettings(
    context: Context,
    office: BaOfficeController,
    ui: BaPageUiController,
    scope: CoroutineScope,
    runtimePersistenceCoordinator: BaRuntimePersistenceCoordinator,
    notificationSettingsSheetState: BaNotificationSettingsSheetState,
) {
    runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
    val previousCafeApNotifyEnabled = office.cafeApNotifyEnabled
    val previousCafeApNotifyThreshold = office.cafeApNotifyThreshold
    val previousArenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled
    val previousCafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled
    scope.launch {
        val persisted = BaSettingsPersistenceRepository.persistNotificationSettingsDraftAsync(notificationSettingsSheetState)

        office.apNotifyEnabled = notificationSettingsSheetState.apNotifyEnabled
        office.cafeApNotifyEnabled = persisted.cafeApNotifyEnabled
        office.arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled
        office.cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled
        office.apNotifyThreshold = persisted.savedThreshold
        office.cafeApNotifyThreshold = persisted.savedCafeApThreshold
        val savedDraft =
            BaPageNotificationDraftState(
                apNotifyEnabled = office.apNotifyEnabled,
                cafeApNotifyEnabled = office.cafeApNotifyEnabled,
                arenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled,
                cafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled,
                calendarUpcomingNotifyEnabled = persisted.calendarUpcomingNotifyEnabled,
                calendarEndingNotifyEnabled = persisted.calendarEndingNotifyEnabled,
                poolUpcomingNotifyEnabled = persisted.poolUpcomingNotifyEnabled,
                poolEndingNotifyEnabled = persisted.poolEndingNotifyEnabled,
                calendarPoolChangeNotifyEnabled = persisted.calendarPoolChangeNotifyEnabled,
                calendarPoolNotifyLeadHours = persisted.calendarPoolNotifyLeadHours,
                apNotifyThresholdText = office.apNotifyThreshold.toString(),
                cafeApNotifyThresholdText = office.cafeApNotifyThreshold.toString(),
            )
        if (!office.cafeApNotifyEnabled) {
            office.cafeApLastNotifiedLevel = -1
            BaSettingsPersistenceRepository.resetCafeApLastNotifiedLevelAsync()
        } else if (!previousCafeApNotifyEnabled ||
            previousCafeApNotifyThreshold != office.cafeApNotifyThreshold
        ) {
            office.cafeApLastNotifiedLevel = -1
            BaSettingsPersistenceRepository.resetCafeApLastNotifiedLevelAsync()
        }
        if (!office.arenaRefreshNotifyEnabled) {
            office.arenaRefreshLastNotifiedSlotMs = 0L
            BaSettingsPersistenceRepository.resetArenaRefreshLastNotifiedSlotAsync()
        } else if (!previousArenaRefreshNotifyEnabled) {
            val baselineSlotMs =
                currentArenaRefreshSlotMs(
                    nowMs = System.currentTimeMillis(),
                    serverIndex = ui.serverIndex,
                )
            office.arenaRefreshLastNotifiedSlotMs = baselineSlotMs
            BaSettingsPersistenceRepository.saveArenaRefreshLastNotifiedSlotAsync(baselineSlotMs)
        }
        if (!office.cafeVisitNotifyEnabled) {
            office.cafeVisitLastNotifiedSlotMs = 0L
            BaSettingsPersistenceRepository.resetCafeVisitLastNotifiedSlotAsync()
        } else if (!previousCafeVisitNotifyEnabled) {
            val baselineSlotMs =
                currentCafeStudentRefreshSlotMs(
                    nowMs = System.currentTimeMillis(),
                    serverIndex = ui.serverIndex,
                )
            office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
            BaSettingsPersistenceRepository.saveCafeVisitLastNotifiedSlotAsync(baselineSlotMs)
        }

        runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
        AppBackgroundScheduler.scheduleBaApThreshold(context)
        ui.applySavedNotificationDraft(savedDraft)
        ui.closeNotificationSettingsSheet()
    }
}

internal fun buildBaPageContentActions(
    context: Context,
    office: BaOfficeController,
    ui: BaPageUiController,
    scope: CoroutineScope,
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
        scope = scope,
        onRefreshCalendar = onRefreshCalendar,
        onRefreshPool = onRefreshPool,
        onOpenCalendarLink = onOpenCalendarLink,
        onOpenPoolStudentGuide = onOpenPoolStudentGuide,
        onOpenGuideCatalog = onOpenGuideCatalog,
    ).buildContentActions()

internal fun applyBaCalendarRefreshInterval(
    ui: BaPageUiController,
    scope: CoroutineScope,
    hours: Int,
    calendarLastSyncMs: Long,
    onRefreshCalendar: () -> Unit,
    onRefreshPool: () -> Unit,
) {
    scope.launch {
        val persisted =
            BaSettingsPersistenceRepository.persistRefreshIntervalAsync(
                hours = hours,
                calendarLastSyncMs = calendarLastSyncMs,
            )
        ui.calendarRefreshIntervalHours = persisted.hours
        if (persisted.shouldRefresh) {
            onRefreshCalendar()
            onRefreshPool()
        }
    }
}
