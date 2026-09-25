package os.kei.ui.page.main.ba

import androidx.compose.runtime.mutableLongStateOf

internal fun testBaPageRouteState(
    calendarUiState: BaCalendarUiState = BaCalendarUiState(),
    poolUiState: BaPoolUiState = BaPoolUiState(),
    runtimeUiState: BaOfficeRuntimeUiState = BaOfficeRuntimeUiState(),
): BaPageRouteState =
    buildBaPageRouteState(
        calendarUiState = calendarUiState,
        poolUiState = poolUiState,
        chromeUiState = BaOfficeChromeUiState(),
        syncUiState = BaOfficeSyncUiState(),
        accountUiState = BaOfficeAccountUiState(),
        serverUiState = BaOfficeServerUiState(),
        runtimeUiState = runtimeUiState,
        settingsDraftUiState = BaOfficeSettingsDraftUiState(),
        notificationDraftUiState = BaOfficeNotificationDraftUiState(),
    )

internal fun testBaPageClockState(): BaPageClockState =
    BaPageClockState(
        uiNowMs = mutableLongStateOf(0L),
        uiMinuteMs = mutableLongStateOf(0L),
    )
