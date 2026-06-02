package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CoroutineScope

internal fun buildBaSettingsSheetState(
    draft: BaPageSettingsDraftState,
): BaSettingsSheetState =
    BaSettingsSheetState(
        cafeLevel = draft.cafeLevel,
        mediaAdaptiveRotationEnabled = draft.mediaAdaptiveRotationEnabled,
        mediaSaveCustomEnabled = draft.mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri = draft.mediaSaveFixedTreeUri,
        idIndependentByServer = draft.idIndependentByServer,
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
    officeState: BaOfficeState,
    routeState: BaPageRouteState,
    clockState: BaPageClockState,
    serverOptions: List<String>,
    cafeLevelOptions: List<Int>,
): BaPageContentState {
    val popupState = routeState.popupState
    return BaPageContentState(
        isPageActive = isPageActive,
        officeOverviewTitle = officeOverviewTitle,
        officeState = officeState,
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
        baCalendarRefreshing = routeState.calendarUiState.refreshing,
        baCalendarError = routeState.calendarUiState.error,
        baCalendarLastSyncMs = routeState.calendarUiState.lastSyncMs,
        showEndedActivities = routeState.showEndedActivities,
        showCalendarPoolImages = routeState.showCalendarPoolImages,
        baPoolEntries = routeState.poolUiState.entries,
        baPoolLoading = routeState.poolUiState.loading,
        baPoolRefreshing = routeState.poolUiState.refreshing,
        baPoolError = routeState.poolUiState.error,
        baPoolLastSyncMs = routeState.poolUiState.lastSyncMs,
        showEndedPools = routeState.showEndedPools,
    )
}

internal fun buildBaPageContentActions(
    context: Context,
    office: BaOfficeController,
    scope: CoroutineScope,
    serverIndexProvider: () -> Int,
    onServerSelected: (Int) -> Unit,
    onSettingsCafeLevelChange: (Int) -> Unit,
    onOverviewServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onOverviewServerPopupChange: (Boolean) -> Unit,
    onCafeLevelPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onCafeLevelPopupChange: (Boolean) -> Unit,
    onRefreshCalendar: () -> Unit,
    onRefreshPool: () -> Unit,
    onOpenCalendarLink: (String) -> Unit,
    onOpenPoolStudentGuide: (String) -> Unit,
    onOpenGuideCatalog: () -> Unit,
): BaPageContentActions =
    BaOfficeActionCoordinator(
        context = context,
        office = office,
        scope = scope,
        serverIndexProvider = serverIndexProvider,
        onServerSelected = onServerSelected,
        onSettingsCafeLevelChange = onSettingsCafeLevelChange,
        onOverviewServerPopupAnchorBoundsChange = onOverviewServerPopupAnchorBoundsChange,
        onOverviewServerPopupChange = onOverviewServerPopupChange,
        onCafeLevelPopupAnchorBoundsChange = onCafeLevelPopupAnchorBoundsChange,
        onCafeLevelPopupChange = onCafeLevelPopupChange,
        onRefreshCalendar = onRefreshCalendar,
        onRefreshPool = onRefreshPool,
        onOpenCalendarLink = onOpenCalendarLink,
        onOpenPoolStudentGuide = onOpenPoolStudentGuide,
        onOpenGuideCatalog = onOpenGuideCatalog,
    ).buildContentActions()
