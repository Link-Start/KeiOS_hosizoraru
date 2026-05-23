package os.kei.ui.page.main.ba

internal data class BaPagePresentationState(
    val routeState: BaPageRouteState,
    val settingsSheetState: BaSettingsSheetState,
    val notificationSettingsSheetState: BaNotificationSettingsSheetState,
    val savedSettingsDraftState: BaPageSettingsDraftState,
    val savedSettingsSheetState: BaSettingsSheetState,
    val savedNotificationSettingsSheetState: BaNotificationSettingsSheetState,
    val pageContentState: BaPageContentState,
)

internal fun buildBaPagePresentationState(
    isPageActive: Boolean,
    officeOverviewTitle: String,
    office: BaOfficeController,
    calendarUiState: BaCalendarUiState,
    poolUiState: BaPoolUiState,
    officePageUiState: BaOfficePageUiState,
    clockState: BaPageClockState,
    serverOptions: List<String>,
    cafeLevelOptions: List<Int>,
): BaPagePresentationState {
    val routeState =
        buildBaPageRouteState(
            calendarUiState = calendarUiState,
            poolUiState = poolUiState,
            chromeUiState = officePageUiState.chromeUiState,
            syncUiState = officePageUiState.syncUiState,
            serverUiState = officePageUiState.serverUiState,
            runtimeUiState = officePageUiState.runtimeUiState,
            settingsDraftUiState = officePageUiState.settingsDraftUiState,
            notificationDraftUiState = officePageUiState.notificationDraftUiState,
        )
    val settingsSheetState =
        buildBaSettingsSheetState(
            draft = routeState.settingsDraftState,
            calendarRefreshIntervalHours = routeState.calendarRefreshIntervalHours,
        )
    val notificationSettingsSheetState =
        buildBaNotificationSettingsSheetState(
            draft = routeState.notificationDraftState,
        )
    val savedSettingsDraftState =
        buildBaSavedSettingsDraftState(
            office = office,
            routeState = routeState,
        )
    val savedSettingsSheetState =
        buildBaSettingsSheetState(
            draft = savedSettingsDraftState,
            calendarRefreshIntervalHours = routeState.calendarRefreshIntervalHours,
        )
    val savedNotificationSettingsSheetState =
        buildBaNotificationSettingsSheetState(
            officePageUiState.notificationDraftUiState.savedDraft,
        )
    val pageContentState =
        buildBaPageContentState(
            isPageActive = isPageActive,
            officeOverviewTitle = officeOverviewTitle,
            office = office,
            routeState = routeState,
            clockState = clockState,
            serverOptions = serverOptions,
            cafeLevelOptions = cafeLevelOptions,
        )
    return BaPagePresentationState(
        routeState = routeState,
        settingsSheetState = settingsSheetState,
        notificationSettingsSheetState = notificationSettingsSheetState,
        savedSettingsDraftState = savedSettingsDraftState,
        savedSettingsSheetState = savedSettingsSheetState,
        savedNotificationSettingsSheetState = savedNotificationSettingsSheetState,
        pageContentState = pageContentState,
    )
}

internal fun buildBaSavedSettingsDraftState(
    office: BaOfficeController,
    routeState: BaPageRouteState,
): BaPageSettingsDraftState =
    BaPageSettingsDraftState(
        cafeLevel = office.cafeLevel,
        mediaAdaptiveRotationEnabled = routeState.mediaAdaptiveRotationEnabled,
        mediaSaveCustomEnabled = routeState.mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri = routeState.mediaSaveFixedTreeUri,
        idIndependentByServer = routeState.idIndependentByServer,
        showEndedActivities = routeState.showEndedActivities,
        showEndedPools = routeState.showEndedPools,
        showCalendarPoolImages = routeState.showCalendarPoolImages,
    )
