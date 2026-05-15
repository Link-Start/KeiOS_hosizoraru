package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import os.kei.ui.page.main.state.PageRouteState
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaPageSnapshot

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
    val showCalendarIntervalPopup: Boolean,
)

@Stable
internal data class BaPageSettingsDraftState(
    val cafeLevel: Int,
    val mediaAdaptiveRotationEnabled: Boolean,
    val mediaSaveCustomEnabled: Boolean,
    val mediaSaveFixedTreeUri: String,
    val idIndependentByServer: Boolean,
    val showEndedPools: Boolean,
    val showEndedActivities: Boolean,
    val showCalendarPoolImages: Boolean,
)

@Stable
internal data class BaPageNotificationDraftState(
    val apNotifyEnabled: Boolean,
    val cafeApNotifyEnabled: Boolean,
    val arenaRefreshNotifyEnabled: Boolean,
    val cafeVisitNotifyEnabled: Boolean,
    val calendarUpcomingNotifyEnabled: Boolean,
    val calendarEndingNotifyEnabled: Boolean,
    val poolUpcomingNotifyEnabled: Boolean,
    val poolEndingNotifyEnabled: Boolean,
    val calendarPoolChangeNotifyEnabled: Boolean,
    val calendarPoolNotifyLeadHours: Int,
    val apNotifyThresholdText: String,
    val cafeApNotifyThresholdText: String,
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


internal class BaPageUiController(snapshot: BaPageSnapshot) {
    var showSettingsSheet by mutableStateOf(false)
    var showNotificationSettingsSheet by mutableStateOf(false)
    var showDebugSheet by mutableStateOf(false)
    var showOverviewServerPopup by mutableStateOf(false)
    var showCafeLevelPopup by mutableStateOf(false)
    var overviewServerPopupAnchorBounds by mutableStateOf<IntRect?>(null)
    var cafeLevelPopupAnchorBounds by mutableStateOf<IntRect?>(null)
    var showCalendarIntervalPopup by mutableStateOf(false)
    var serverIndex by mutableIntStateOf(snapshot.serverIndex)
    private val uiNowMsState = mutableLongStateOf(System.currentTimeMillis())
    private val uiMinuteMsState = mutableLongStateOf(System.currentTimeMillis())
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
    var baCalendarReloadSignal by mutableIntStateOf(0)
    var baPoolReloadSignal by mutableIntStateOf(0)
    var showEndedPools by mutableStateOf(snapshot.showEndedPools)
    var showEndedActivities by mutableStateOf(snapshot.showEndedActivities)
    var showCalendarPoolImages by mutableStateOf(snapshot.showCalendarPoolImages)
    var mediaAdaptiveRotationEnabled by mutableStateOf(snapshot.mediaAdaptiveRotationEnabled)
    var mediaSaveCustomEnabled by mutableStateOf(snapshot.mediaSaveCustomEnabled)
    var mediaSaveFixedTreeUri by mutableStateOf(snapshot.mediaSaveFixedTreeUri)
    var idIndependentByServer by mutableStateOf(snapshot.idIndependentByServer)
    var calendarRefreshIntervalHours by mutableIntStateOf(snapshot.calendarRefreshIntervalHours)
    var calendarHydrationReady by mutableStateOf(false)
    var poolHydrationReady by mutableStateOf(false)
    var sheetCafeLevel by mutableIntStateOf(snapshot.cafeLevel)
    var sheetApNotifyEnabled by mutableStateOf(snapshot.apNotifyEnabled)
    var sheetCafeApNotifyEnabled by mutableStateOf(snapshot.cafeApNotifyEnabled)
    var sheetArenaRefreshNotifyEnabled by mutableStateOf(snapshot.arenaRefreshNotifyEnabled)
    var sheetCafeVisitNotifyEnabled by mutableStateOf(snapshot.cafeVisitNotifyEnabled)
    var sheetCalendarUpcomingNotifyEnabled by mutableStateOf(snapshot.calendarUpcomingNotifyEnabled)
    var sheetCalendarEndingNotifyEnabled by mutableStateOf(snapshot.calendarEndingNotifyEnabled)
    var sheetPoolUpcomingNotifyEnabled by mutableStateOf(snapshot.poolUpcomingNotifyEnabled)
    var sheetPoolEndingNotifyEnabled by mutableStateOf(snapshot.poolEndingNotifyEnabled)
    var sheetCalendarPoolChangeNotifyEnabled by mutableStateOf(snapshot.calendarPoolChangeNotifyEnabled)
    var sheetCalendarPoolNotifyLeadHours by mutableIntStateOf(snapshot.calendarPoolNotifyLeadHours)
    var debugUseRealCalendarPoolData by mutableStateOf(true)
    var sheetApNotifyThresholdText by mutableStateOf(snapshot.apNotifyThreshold.toString())
    var sheetCafeApNotifyThresholdText by mutableStateOf(snapshot.cafeApNotifyThreshold.toString())
    var sheetMediaAdaptiveRotationEnabled by mutableStateOf(snapshot.mediaAdaptiveRotationEnabled)
    var sheetMediaSaveCustomEnabled by mutableStateOf(snapshot.mediaSaveCustomEnabled)
    var sheetMediaSaveFixedTreeUri by mutableStateOf(snapshot.mediaSaveFixedTreeUri)
    var sheetIdIndependentByServer by mutableStateOf(snapshot.idIndependentByServer)
    var sheetShowEndedPools by mutableStateOf(snapshot.showEndedPools)
    var sheetShowEndedActivities by mutableStateOf(snapshot.showEndedActivities)
    var sheetShowCalendarPoolImages by mutableStateOf(snapshot.showCalendarPoolImages)
    var consumedScrollToTopSignal by mutableIntStateOf(0)

    fun routeState(
        calendarUiState: BaCalendarUiState,
        poolUiState: BaPoolUiState,
    ): BaPageRouteState {
        return BaPageRouteState(
            showSettingsSheet = showSettingsSheet,
            showNotificationSettingsSheet = showNotificationSettingsSheet,
            showDebugSheet = showDebugSheet,
            popupState = popupState(),
            serverIndex = serverIndex,
            baCalendarReloadSignal = baCalendarReloadSignal,
            baPoolReloadSignal = baPoolReloadSignal,
            calendarUiState = calendarUiState,
            poolUiState = poolUiState,
            showEndedPools = showEndedPools,
            showEndedActivities = showEndedActivities,
            showCalendarPoolImages = showCalendarPoolImages,
            mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
            mediaSaveCustomEnabled = mediaSaveCustomEnabled,
            mediaSaveFixedTreeUri = mediaSaveFixedTreeUri,
            idIndependentByServer = idIndependentByServer,
            calendarRefreshIntervalHours = calendarRefreshIntervalHours,
            calendarHydrationReady = calendarHydrationReady,
            poolHydrationReady = poolHydrationReady,
            settingsDraftState = settingsDraftState(),
            notificationDraftState = notificationDraftState(),
            debugUseRealCalendarPoolData = debugUseRealCalendarPoolData,
            consumedScrollToTopSignal = consumedScrollToTopSignal,
        )
    }

    fun clockState(): BaPageClockState {
        return BaPageClockState(
            uiNowMs = uiNowMsState,
            uiMinuteMs = uiMinuteMsState,
        )
    }

    private fun popupState(): BaPagePopupState {
        return BaPagePopupState(
            showOverviewServerPopup = showOverviewServerPopup,
            showCafeLevelPopup = showCafeLevelPopup,
            overviewServerPopupAnchorBounds = overviewServerPopupAnchorBounds,
            cafeLevelPopupAnchorBounds = cafeLevelPopupAnchorBounds,
            showCalendarIntervalPopup = showCalendarIntervalPopup,
        )
    }

    fun settingsDraftState(): BaPageSettingsDraftState {
        return BaPageSettingsDraftState(
            cafeLevel = sheetCafeLevel,
            mediaAdaptiveRotationEnabled = sheetMediaAdaptiveRotationEnabled,
            mediaSaveCustomEnabled = sheetMediaSaveCustomEnabled,
            mediaSaveFixedTreeUri = sheetMediaSaveFixedTreeUri,
            idIndependentByServer = sheetIdIndependentByServer,
            showEndedPools = sheetShowEndedPools,
            showEndedActivities = sheetShowEndedActivities,
            showCalendarPoolImages = sheetShowCalendarPoolImages,
        )
    }

    fun notificationDraftState(): BaPageNotificationDraftState {
        return BaPageNotificationDraftState(
            apNotifyEnabled = sheetApNotifyEnabled,
            cafeApNotifyEnabled = sheetCafeApNotifyEnabled,
            arenaRefreshNotifyEnabled = sheetArenaRefreshNotifyEnabled,
            cafeVisitNotifyEnabled = sheetCafeVisitNotifyEnabled,
            calendarUpcomingNotifyEnabled = sheetCalendarUpcomingNotifyEnabled,
            calendarEndingNotifyEnabled = sheetCalendarEndingNotifyEnabled,
            poolUpcomingNotifyEnabled = sheetPoolUpcomingNotifyEnabled,
            poolEndingNotifyEnabled = sheetPoolEndingNotifyEnabled,
            calendarPoolChangeNotifyEnabled = sheetCalendarPoolChangeNotifyEnabled,
            calendarPoolNotifyLeadHours = sheetCalendarPoolNotifyLeadHours,
            apNotifyThresholdText = sheetApNotifyThresholdText,
            cafeApNotifyThresholdText = sheetCafeApNotifyThresholdText,
        )
    }

    fun refreshCalendar(force: Boolean = false) {
        if (force) baCalendarReloadSignal += 1
    }

    fun refreshPool(force: Boolean = false) {
        if (force) baPoolReloadSignal += 1
    }

    fun openSettingsSheet(office: BaOfficeController) {
        showOverviewServerPopup = false
        showCafeLevelPopup = false
        sheetCafeLevel = office.cafeLevel
        sheetMediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled
        sheetMediaSaveCustomEnabled = mediaSaveCustomEnabled
        sheetMediaSaveFixedTreeUri = mediaSaveFixedTreeUri
        sheetIdIndependentByServer = idIndependentByServer
        sheetShowEndedPools = showEndedPools
        sheetShowEndedActivities = showEndedActivities
        sheetShowCalendarPoolImages = showCalendarPoolImages
        showSettingsSheet = true
    }

    fun closeSettingsSheet(office: BaOfficeController) {
        showSettingsSheet = false
        showCafeLevelPopup = false
        sheetCafeLevel = office.cafeLevel
        sheetMediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled
        sheetMediaSaveCustomEnabled = mediaSaveCustomEnabled
        sheetMediaSaveFixedTreeUri = mediaSaveFixedTreeUri
        sheetIdIndependentByServer = idIndependentByServer
        sheetShowEndedPools = showEndedPools
        sheetShowEndedActivities = showEndedActivities
        sheetShowCalendarPoolImages = showCalendarPoolImages
    }

    fun openNotificationSettingsSheet(office: BaOfficeController) {
        showOverviewServerPopup = false
        showCafeLevelPopup = false
        loadNotificationDraft(office)
        showNotificationSettingsSheet = true
    }

    fun closeNotificationSettingsSheet(office: BaOfficeController) {
        showNotificationSettingsSheet = false
        loadNotificationDraft(office)
    }

    fun openDebugSheet() {
        showOverviewServerPopup = false
        showCafeLevelPopup = false
        showDebugSheet = true
    }

    fun closeDebugSheet() {
        showDebugSheet = false
    }

    private fun loadNotificationDraft(office: BaOfficeController) {
        sheetApNotifyEnabled = office.apNotifyEnabled
        sheetCafeApNotifyEnabled = office.cafeApNotifyEnabled
        sheetArenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled
        sheetCafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled
        sheetCalendarUpcomingNotifyEnabled = BASettingsStore.loadCalendarUpcomingNotifyEnabled()
        sheetCalendarEndingNotifyEnabled = BASettingsStore.loadCalendarEndingNotifyEnabled()
        sheetPoolUpcomingNotifyEnabled = BASettingsStore.loadPoolUpcomingNotifyEnabled()
        sheetPoolEndingNotifyEnabled = BASettingsStore.loadPoolEndingNotifyEnabled()
        sheetCalendarPoolChangeNotifyEnabled = BASettingsStore.loadCalendarPoolChangeNotifyEnabled()
        sheetCalendarPoolNotifyLeadHours = BASettingsStore.loadCalendarPoolNotifyLeadHours()
        sheetApNotifyThresholdText = office.apNotifyThreshold.toString()
        sheetCafeApNotifyThresholdText = office.cafeApNotifyThreshold.toString()
    }
}

@Composable
internal fun rememberBaPageUiController(snapshot: BaPageSnapshot): BaPageUiController {
    return remember(snapshot) { BaPageUiController(snapshot) }
}
