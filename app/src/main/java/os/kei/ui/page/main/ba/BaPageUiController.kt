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
import os.kei.ui.page.main.ba.support.BaPageSnapshot
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

internal class BaPageUiController(
    snapshot: BaPageSnapshot,
) {
    var showOverviewServerPopup by mutableStateOf(false)
    var showCafeLevelPopup by mutableStateOf(false)
    var overviewServerPopupAnchorBounds by mutableStateOf<IntRect?>(null)
    var cafeLevelPopupAnchorBounds by mutableStateOf<IntRect?>(null)
    var showCalendarIntervalPopup by mutableStateOf(false)
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
    var showEndedPools by mutableStateOf(snapshot.showEndedPools)
    var showEndedActivities by mutableStateOf(snapshot.showEndedActivities)
    var showCalendarPoolImages by mutableStateOf(snapshot.showCalendarPoolImages)
    var mediaAdaptiveRotationEnabled by mutableStateOf(snapshot.mediaAdaptiveRotationEnabled)
    var mediaSaveCustomEnabled by mutableStateOf(snapshot.mediaSaveCustomEnabled)
    var mediaSaveFixedTreeUri by mutableStateOf(snapshot.mediaSaveFixedTreeUri)
    var idIndependentByServer by mutableStateOf(snapshot.idIndependentByServer)
    var calendarRefreshIntervalHours by mutableIntStateOf(snapshot.calendarRefreshIntervalHours)
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
    var sheetApNotifyThresholdText by mutableStateOf(snapshot.apNotifyThreshold.toString())
    var sheetCafeApNotifyThresholdText by mutableStateOf(snapshot.cafeApNotifyThreshold.toString())
    var sheetMediaAdaptiveRotationEnabled by mutableStateOf(snapshot.mediaAdaptiveRotationEnabled)
    var sheetMediaSaveCustomEnabled by mutableStateOf(snapshot.mediaSaveCustomEnabled)
    var sheetMediaSaveFixedTreeUri by mutableStateOf(snapshot.mediaSaveFixedTreeUri)
    var sheetIdIndependentByServer by mutableStateOf(snapshot.idIndependentByServer)
    var sheetShowEndedPools by mutableStateOf(snapshot.showEndedPools)
    var sheetShowEndedActivities by mutableStateOf(snapshot.showEndedActivities)
    var sheetShowCalendarPoolImages by mutableStateOf(snapshot.showCalendarPoolImages)
    private var savedNotificationDraft by mutableStateOf(snapshot.toNotificationDraftState())
    var consumedScrollToTopSignal by mutableIntStateOf(0)

    fun matchesSnapshot(snapshot: BaPageSnapshot): Boolean =
        showEndedPools == snapshot.showEndedPools &&
            showEndedActivities == snapshot.showEndedActivities &&
            showCalendarPoolImages == snapshot.showCalendarPoolImages &&
            mediaAdaptiveRotationEnabled == snapshot.mediaAdaptiveRotationEnabled &&
            mediaSaveCustomEnabled == snapshot.mediaSaveCustomEnabled &&
            mediaSaveFixedTreeUri == snapshot.mediaSaveFixedTreeUri &&
            idIndependentByServer == snapshot.idIndependentByServer &&
            calendarRefreshIntervalHours == snapshot.calendarRefreshIntervalHours &&
            sheetCafeLevel == snapshot.cafeLevel &&
            sheetApNotifyEnabled == snapshot.apNotifyEnabled &&
            sheetCafeApNotifyEnabled == snapshot.cafeApNotifyEnabled &&
            sheetArenaRefreshNotifyEnabled == snapshot.arenaRefreshNotifyEnabled &&
            sheetCafeVisitNotifyEnabled == snapshot.cafeVisitNotifyEnabled &&
            sheetCalendarUpcomingNotifyEnabled == snapshot.calendarUpcomingNotifyEnabled &&
            sheetCalendarEndingNotifyEnabled == snapshot.calendarEndingNotifyEnabled &&
            sheetPoolUpcomingNotifyEnabled == snapshot.poolUpcomingNotifyEnabled &&
            sheetPoolEndingNotifyEnabled == snapshot.poolEndingNotifyEnabled &&
            sheetCalendarPoolChangeNotifyEnabled == snapshot.calendarPoolChangeNotifyEnabled &&
            sheetCalendarPoolNotifyLeadHours == snapshot.calendarPoolNotifyLeadHours &&
            sheetApNotifyThresholdText == snapshot.apNotifyThreshold.toString() &&
            sheetCafeApNotifyThresholdText == snapshot.cafeApNotifyThreshold.toString() &&
            sheetMediaAdaptiveRotationEnabled == snapshot.mediaAdaptiveRotationEnabled &&
            sheetMediaSaveCustomEnabled == snapshot.mediaSaveCustomEnabled &&
            sheetMediaSaveFixedTreeUri == snapshot.mediaSaveFixedTreeUri &&
            sheetIdIndependentByServer == snapshot.idIndependentByServer &&
            sheetShowEndedPools == snapshot.showEndedPools &&
            sheetShowEndedActivities == snapshot.showEndedActivities &&
            sheetShowCalendarPoolImages == snapshot.showCalendarPoolImages &&
            savedNotificationDraft == snapshot.toNotificationDraftState()

    fun applySnapshot(snapshot: BaPageSnapshot) {
        showEndedPools = snapshot.showEndedPools
        showEndedActivities = snapshot.showEndedActivities
        showCalendarPoolImages = snapshot.showCalendarPoolImages
        mediaAdaptiveRotationEnabled = snapshot.mediaAdaptiveRotationEnabled
        mediaSaveCustomEnabled = snapshot.mediaSaveCustomEnabled
        mediaSaveFixedTreeUri = snapshot.mediaSaveFixedTreeUri
        idIndependentByServer = snapshot.idIndependentByServer
        calendarRefreshIntervalHours = snapshot.calendarRefreshIntervalHours
        sheetCafeLevel = snapshot.cafeLevel
        sheetApNotifyEnabled = snapshot.apNotifyEnabled
        sheetCafeApNotifyEnabled = snapshot.cafeApNotifyEnabled
        sheetArenaRefreshNotifyEnabled = snapshot.arenaRefreshNotifyEnabled
        sheetCafeVisitNotifyEnabled = snapshot.cafeVisitNotifyEnabled
        sheetCalendarUpcomingNotifyEnabled = snapshot.calendarUpcomingNotifyEnabled
        sheetCalendarEndingNotifyEnabled = snapshot.calendarEndingNotifyEnabled
        sheetPoolUpcomingNotifyEnabled = snapshot.poolUpcomingNotifyEnabled
        sheetPoolEndingNotifyEnabled = snapshot.poolEndingNotifyEnabled
        sheetCalendarPoolChangeNotifyEnabled = snapshot.calendarPoolChangeNotifyEnabled
        sheetCalendarPoolNotifyLeadHours = snapshot.calendarPoolNotifyLeadHours
        sheetApNotifyThresholdText = snapshot.apNotifyThreshold.toString()
        sheetCafeApNotifyThresholdText = snapshot.cafeApNotifyThreshold.toString()
        sheetMediaAdaptiveRotationEnabled = snapshot.mediaAdaptiveRotationEnabled
        sheetMediaSaveCustomEnabled = snapshot.mediaSaveCustomEnabled
        sheetMediaSaveFixedTreeUri = snapshot.mediaSaveFixedTreeUri
        sheetIdIndependentByServer = snapshot.idIndependentByServer
        sheetShowEndedPools = snapshot.showEndedPools
        sheetShowEndedActivities = snapshot.showEndedActivities
        sheetShowCalendarPoolImages = snapshot.showCalendarPoolImages
        savedNotificationDraft = snapshot.toNotificationDraftState()
    }

    fun routeState(
        calendarUiState: BaCalendarUiState,
        poolUiState: BaPoolUiState,
        chromeUiState: BaOfficeChromeUiState,
        syncUiState: BaOfficeSyncUiState,
        serverUiState: BaOfficeServerUiState,
    ): BaPageRouteState =
        BaPageRouteState(
            showSettingsSheet = chromeUiState.showSettingsSheet,
            showNotificationSettingsSheet = chromeUiState.showNotificationSettingsSheet,
            showDebugSheet = chromeUiState.showDebugSheet,
            popupState = popupState(),
            serverIndex = serverUiState.serverIndex,
            baCalendarReloadSignal = syncUiState.calendarReloadSignal,
            baPoolReloadSignal = syncUiState.poolReloadSignal,
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
            calendarHydrationReady = syncUiState.calendarHydrationReady,
            poolHydrationReady = syncUiState.poolHydrationReady,
            settingsDraftState = settingsDraftState(),
            notificationDraftState = notificationDraftState(),
            debugUseRealCalendarPoolData = chromeUiState.debugUseRealCalendarPoolData,
            consumedScrollToTopSignal = consumedScrollToTopSignal,
        )

    fun clockState(): BaPageClockState =
        BaPageClockState(
            uiNowMs = uiNowMsState,
            uiMinuteMs = uiMinuteMsState,
        )

    private fun popupState(): BaPagePopupState =
        BaPagePopupState(
            showOverviewServerPopup = showOverviewServerPopup,
            showCafeLevelPopup = showCafeLevelPopup,
            overviewServerPopupAnchorBounds = overviewServerPopupAnchorBounds,
            cafeLevelPopupAnchorBounds = cafeLevelPopupAnchorBounds,
            showCalendarIntervalPopup = showCalendarIntervalPopup,
        )

    fun settingsDraftState(): BaPageSettingsDraftState =
        BaPageSettingsDraftState(
            cafeLevel = sheetCafeLevel,
            mediaAdaptiveRotationEnabled = sheetMediaAdaptiveRotationEnabled,
            mediaSaveCustomEnabled = sheetMediaSaveCustomEnabled,
            mediaSaveFixedTreeUri = sheetMediaSaveFixedTreeUri,
            idIndependentByServer = sheetIdIndependentByServer,
            showEndedPools = sheetShowEndedPools,
            showEndedActivities = sheetShowEndedActivities,
            showCalendarPoolImages = sheetShowCalendarPoolImages,
        )

    fun notificationDraftState(): BaPageNotificationDraftState =
        BaPageNotificationDraftState(
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

    fun savedNotificationDraftState(): BaPageNotificationDraftState = savedNotificationDraft

    fun applySavedNotificationDraft(draft: BaPageNotificationDraftState) {
        savedNotificationDraft = draft
        applyNotificationDraft(draft)
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
    }

    fun closeSettingsSheet(office: BaOfficeController) {
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
    }

    fun closeNotificationSettingsSheet() {
        applyNotificationDraft(savedNotificationDraft)
    }

    fun openDebugSheet() {
        showOverviewServerPopup = false
        showCafeLevelPopup = false
    }

    private fun loadNotificationDraft(office: BaOfficeController) {
        savedNotificationDraft =
            savedNotificationDraft.copy(
                apNotifyEnabled = office.apNotifyEnabled,
                cafeApNotifyEnabled = office.cafeApNotifyEnabled,
                arenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled,
                cafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled,
                apNotifyThresholdText = office.apNotifyThreshold.toString(),
                cafeApNotifyThresholdText = office.cafeApNotifyThreshold.toString(),
            )
        applyNotificationDraft(savedNotificationDraft)
    }

    private fun applyNotificationDraft(draft: BaPageNotificationDraftState) {
        sheetApNotifyEnabled = draft.apNotifyEnabled
        sheetCafeApNotifyEnabled = draft.cafeApNotifyEnabled
        sheetArenaRefreshNotifyEnabled = draft.arenaRefreshNotifyEnabled
        sheetCafeVisitNotifyEnabled = draft.cafeVisitNotifyEnabled
        sheetCalendarUpcomingNotifyEnabled = draft.calendarUpcomingNotifyEnabled
        sheetCalendarEndingNotifyEnabled = draft.calendarEndingNotifyEnabled
        sheetPoolUpcomingNotifyEnabled = draft.poolUpcomingNotifyEnabled
        sheetPoolEndingNotifyEnabled = draft.poolEndingNotifyEnabled
        sheetCalendarPoolChangeNotifyEnabled = draft.calendarPoolChangeNotifyEnabled
        sheetCalendarPoolNotifyLeadHours = draft.calendarPoolNotifyLeadHours
        sheetApNotifyThresholdText = draft.apNotifyThresholdText
        sheetCafeApNotifyThresholdText = draft.cafeApNotifyThresholdText
    }
}

@Composable
internal fun rememberBaPageUiController(snapshot: BaPageSnapshot): BaPageUiController = remember(snapshot) { BaPageUiController(snapshot) }

private fun BaPageSnapshot.toNotificationDraftState(): BaPageNotificationDraftState =
    BaPageNotificationDraftState(
        apNotifyEnabled = apNotifyEnabled,
        cafeApNotifyEnabled = cafeApNotifyEnabled,
        arenaRefreshNotifyEnabled = arenaRefreshNotifyEnabled,
        cafeVisitNotifyEnabled = cafeVisitNotifyEnabled,
        calendarUpcomingNotifyEnabled = calendarUpcomingNotifyEnabled,
        calendarEndingNotifyEnabled = calendarEndingNotifyEnabled,
        poolUpcomingNotifyEnabled = poolUpcomingNotifyEnabled,
        poolEndingNotifyEnabled = poolEndingNotifyEnabled,
        calendarPoolChangeNotifyEnabled = calendarPoolChangeNotifyEnabled,
        calendarPoolNotifyLeadHours = calendarPoolNotifyLeadHours,
        apNotifyThresholdText = apNotifyThreshold.toString(),
        cafeApNotifyThresholdText = cafeApNotifyThreshold.toString(),
    )
