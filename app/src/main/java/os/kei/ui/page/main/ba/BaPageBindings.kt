package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CoroutineScope
import os.kei.ui.page.main.ba.support.BaAccountId

internal fun buildBaSettingsSheetState(
    draft: BaPageSettingsDraftState,
): BaSettingsSheetState =
    BaSettingsSheetState(
        cafeLevel = draft.cafeLevel,
        mediaAdaptiveRotationEnabled = draft.mediaAdaptiveRotationEnabled,
        mediaSaveCustomEnabled = draft.mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri = draft.mediaSaveFixedTreeUri,
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
    officeState: BaOfficeState,
    routeState: BaPageRouteState,
    clockState: BaPageClockState,
    serverOptions: List<String>,
    cafeLevelOptions: List<Int>,
): BaPageContentState {
    val popupState = routeState.popupState
    return BaPageContentState(
        isPageActive = isPageActive,
        officeState = officeState,
        clockState = clockState,
        accountUiState = routeState.accountUiState,
        serverOptions = serverOptions,
        cafeLevelOptions = cafeLevelOptions,
        serverIndex = routeState.serverIndex,
        showCafeLevelPopup = popupState.showCafeLevelPopup,
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
    accountIdProvider: () -> BaAccountId?,
    onSettingsCafeLevelChange: (Int) -> Unit,
    onCafeLevelPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onCafeLevelPopupChange: (Boolean) -> Unit,
    onAccountSelected: (BaAccountId) -> Unit,
    onEditAccount: (BaAccountId) -> Unit,
    onRefreshCalendar: () -> Unit,
    onRefreshPool: () -> Unit,
    onOpenCalendarLink: (String) -> Unit,
    onOpenPoolStudentGuide: (String) -> Unit,
): BaPageContentActions =
    BaOfficeActionCoordinator(
        context = context,
        office = office,
        scope = scope,
        serverIndexProvider = serverIndexProvider,
        accountIdProvider = accountIdProvider,
        onSettingsCafeLevelChange = onSettingsCafeLevelChange,
        onCafeLevelPopupAnchorBoundsChange = onCafeLevelPopupAnchorBoundsChange,
        onCafeLevelPopupChange = onCafeLevelPopupChange,
        onAccountSelected = onAccountSelected,
        onEditAccount = onEditAccount,
        onRefreshCalendar = onRefreshCalendar,
        onRefreshPool = onRefreshPool,
        onOpenCalendarLink = onOpenCalendarLink,
        onOpenPoolStudentGuide = onOpenPoolStudentGuide,
    ).buildContentActions()
