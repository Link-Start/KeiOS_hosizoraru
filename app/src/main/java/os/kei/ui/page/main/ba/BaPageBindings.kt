package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.CoroutineScope

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
