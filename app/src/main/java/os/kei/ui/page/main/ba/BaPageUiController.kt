package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntRect
import os.kei.ui.page.main.state.PageRouteState

@Stable
internal data class BaPageClockState(
    val uiNowMs: LongState,
    val uiMinuteMs: LongState,
)

@Stable
internal data class BaPagePopupState(
    val showOverviewServerPopup: Boolean,
    val showCafeLevelPopup: Boolean,
    val overviewServerPopupAnchorBounds: IntRect?,
    val cafeLevelPopupAnchorBounds: IntRect?,
)

@Stable
internal data class BaPageRouteState(
    val showSettingsSheet: Boolean,
    val showNotificationSettingsSheet: Boolean,
    val showDebugSheet: Boolean,
    val popupState: BaPagePopupState,
    val serverIndex: Int,
    val baCalendarReloadSignal: Int,
    val baPoolReloadSignal: Int,
    val calendarUiState: BaCalendarUiState,
    val poolUiState: BaPoolUiState,
    val showEndedPools: Boolean,
    val showEndedActivities: Boolean,
    val showCalendarPoolImages: Boolean,
    val mediaAdaptiveRotationEnabled: Boolean,
    val mediaSaveCustomEnabled: Boolean,
    val mediaSaveFixedTreeUri: String,
    val idIndependentByServer: Boolean,
    val calendarRefreshIntervalHours: Int,
    val calendarHydrationReady: Boolean,
    val poolHydrationReady: Boolean,
    val settingsDraftState: BaPageSettingsDraftState,
    val notificationDraftState: BaPageNotificationDraftState,
    val debugUseRealCalendarPoolData: Boolean,
    val consumedScrollToTopSignal: Int,
) : PageRouteState

internal fun buildBaPageRouteState(
    calendarUiState: BaCalendarUiState,
    poolUiState: BaPoolUiState,
    chromeUiState: BaOfficeChromeUiState,
    syncUiState: BaOfficeSyncUiState,
    serverUiState: BaOfficeServerUiState,
    runtimeUiState: BaOfficeRuntimeUiState,
    settingsDraftUiState: BaOfficeSettingsDraftUiState,
    notificationDraftUiState: BaOfficeNotificationDraftUiState,
): BaPageRouteState =
    BaPageRouteState(
        showSettingsSheet = chromeUiState.showSettingsSheet,
        showNotificationSettingsSheet = chromeUiState.showNotificationSettingsSheet,
        showDebugSheet = chromeUiState.showDebugSheet,
        popupState =
            BaPagePopupState(
                showOverviewServerPopup = chromeUiState.showOverviewServerPopup,
                showCafeLevelPopup = chromeUiState.showCafeLevelPopup,
                overviewServerPopupAnchorBounds = chromeUiState.overviewServerPopupAnchorBounds,
                cafeLevelPopupAnchorBounds = chromeUiState.cafeLevelPopupAnchorBounds,
            ),
        serverIndex = serverUiState.serverIndex,
        baCalendarReloadSignal = syncUiState.calendarReloadSignal,
        baPoolReloadSignal = syncUiState.poolReloadSignal,
        calendarUiState = calendarUiState,
        poolUiState = poolUiState,
        showEndedPools = runtimeUiState.showEndedPools,
        showEndedActivities = runtimeUiState.showEndedActivities,
        showCalendarPoolImages = runtimeUiState.showCalendarPoolImages,
        mediaAdaptiveRotationEnabled = runtimeUiState.mediaAdaptiveRotationEnabled,
        mediaSaveCustomEnabled = runtimeUiState.mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri = runtimeUiState.mediaSaveFixedTreeUri,
        idIndependentByServer = runtimeUiState.idIndependentByServer,
        calendarRefreshIntervalHours = runtimeUiState.calendarRefreshIntervalHours,
        calendarHydrationReady = syncUiState.calendarHydrationReady,
        poolHydrationReady = syncUiState.poolHydrationReady,
        settingsDraftState = settingsDraftUiState.draft,
        notificationDraftState = notificationDraftUiState.draft,
        debugUseRealCalendarPoolData = chromeUiState.debugUseRealCalendarPoolData,
        consumedScrollToTopSignal = chromeUiState.consumedScrollToTopSignal,
    )

internal class BaPageUiController {
    private val uiNowMsState = mutableLongStateOf(System.currentTimeMillis())
    private val uiMinuteMsState = mutableLongStateOf(System.currentTimeMillis())
    private val clockState =
        BaPageClockState(
            uiNowMs = uiNowMsState,
            uiMinuteMs = uiMinuteMsState,
        )
    var uiNowMs: Long
        get() = uiNowMsState.longValue
        set(value) {
            uiNowMsState.longValue = value
        }
    var uiMinuteMs: Long
        get() = uiMinuteMsState.longValue
        set(value) {
            uiMinuteMsState.longValue = value
        }

    fun clockState(): BaPageClockState = clockState
}

@Composable
internal fun rememberBaPageUiController(): BaPageUiController = remember { BaPageUiController() }
