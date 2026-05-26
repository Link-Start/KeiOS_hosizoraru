package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs

internal class BaOfficeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val defaultSnapshot = BaPageSnapshot()
    private val _chromeUiState = MutableStateFlow(BaOfficeChromeUiState())
    val chromeUiState: StateFlow<BaOfficeChromeUiState> = _chromeUiState.asStateFlow()
    private val _syncUiState = MutableStateFlow(BaOfficeSyncUiState())
    val syncUiState: StateFlow<BaOfficeSyncUiState> = _syncUiState.asStateFlow()
    private val _serverUiState = MutableStateFlow(BaOfficeServerUiState(defaultSnapshot.serverIndex))
    val serverUiState: StateFlow<BaOfficeServerUiState> = _serverUiState.asStateFlow()
    private val _runtimeUiState = MutableStateFlow(defaultSnapshot.toRuntimeUiState())
    val runtimeUiState: StateFlow<BaOfficeRuntimeUiState> = _runtimeUiState.asStateFlow()
    private val _settingsDraftUiState = MutableStateFlow(BaOfficeSettingsDraftUiState(defaultSnapshot.toSettingsDraftState()))
    val settingsDraftUiState: StateFlow<BaOfficeSettingsDraftUiState> = _settingsDraftUiState.asStateFlow()
    private val _notificationDraftUiState =
        MutableStateFlow(
            BaOfficeNotificationDraftUiState(
                draft = defaultSnapshot.toNotificationDraftState(),
                savedDraft = defaultSnapshot.toNotificationDraftState(),
            ),
        )
    val notificationDraftUiState: StateFlow<BaOfficeNotificationDraftUiState> = _notificationDraftUiState.asStateFlow()
    val pageUiState: StateFlow<BaOfficePageUiState> =
        combine(
            chromeUiState,
            syncUiState,
            serverUiState,
            runtimeUiState,
            settingsDraftUiState,
            notificationDraftUiState,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val chrome = values[0] as BaOfficeChromeUiState
            val sync = values[1] as BaOfficeSyncUiState
            val server = values[2] as BaOfficeServerUiState
            val runtime = values[3] as BaOfficeRuntimeUiState
            val settingsDraft = values[4] as BaOfficeSettingsDraftUiState
            val notificationDraft = values[5] as BaOfficeNotificationDraftUiState
            BaOfficePageUiState(
                chromeUiState = chrome,
                syncUiState = sync,
                serverUiState = server,
                runtimeUiState = runtime,
                settingsDraftUiState = settingsDraft,
                notificationDraftUiState = notificationDraft,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue =
                BaOfficePageUiState(
                    chromeUiState = _chromeUiState.value,
                    syncUiState = _syncUiState.value,
                    serverUiState = _serverUiState.value,
                    runtimeUiState = _runtimeUiState.value,
                    settingsDraftUiState = _settingsDraftUiState.value,
                    notificationDraftUiState = _notificationDraftUiState.value,
                ),
        )
    private val _events = MutableSharedFlow<BaOfficeEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<BaOfficeEvent> = _events.asSharedFlow()
    val office: BaOfficeController = BaOfficeController(defaultSnapshot)

    init {
        viewModelScope.launch {
            val snapshot = BaOfficeRepository.loadSnapshotAsync()
            if (office.matchesSnapshot(defaultSnapshot)) {
                office.applySnapshot(snapshot)
            }
            _serverUiState.value = BaOfficeServerUiState(snapshot.serverIndex)
            _runtimeUiState.value = snapshot.toRuntimeUiState()
            _settingsDraftUiState.value = BaOfficeSettingsDraftUiState(snapshot.toSettingsDraftState())
            _notificationDraftUiState.value =
                BaOfficeNotificationDraftUiState(
                    draft = snapshot.toNotificationDraftState(),
                    savedDraft = snapshot.toNotificationDraftState(),
                )
        }
    }

    fun clearListScrollState() {
        viewModelScope.launch {
            BaOfficeRepository.clearListScrollStateAsync()
        }
    }

    fun showSettingsSheet(currentDraft: BaPageSettingsDraftState) {
        _settingsDraftUiState.value = BaOfficeSettingsDraftUiState(currentDraft)
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(showSettingsSheet = true)
        }
    }

    fun hideSettingsSheet(currentDraft: BaPageSettingsDraftState) {
        _settingsDraftUiState.value = BaOfficeSettingsDraftUiState(currentDraft)
        _chromeUiState.update { state ->
            state.copy(
                showSettingsSheet = false,
                settingsRefreshIntervalDropdownExpanded = false,
                settingsRefreshIntervalDropdownAnchorBounds = null,
            )
        }
    }

    fun showNotificationSettingsSheet() {
        val savedDraft = notificationRuntimeDraft(_notificationDraftUiState.value.savedDraft)
        _notificationDraftUiState.value =
            BaOfficeNotificationDraftUiState(
                draft = savedDraft,
                savedDraft = savedDraft,
            )
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(showNotificationSettingsSheet = true)
        }
    }

    fun hideNotificationSettingsSheet() {
        _notificationDraftUiState.update { state ->
            state.copy(draft = state.savedDraft)
        }
        _chromeUiState.update { state ->
            state.copy(
                showNotificationSettingsSheet = false,
                notificationLeadDropdownExpanded = false,
                notificationLeadDropdownAnchorBounds = null,
            )
        }
    }

    fun showDebugSheet() {
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(showDebugSheet = true)
        }
    }

    fun hideDebugSheet() {
        _chromeUiState.update { state ->
            state.copy(showDebugSheet = false)
        }
    }

    fun updateDebugUseRealCalendarPoolData(enabled: Boolean) {
        _chromeUiState.update { state ->
            if (state.debugUseRealCalendarPoolData == enabled) {
                state
            } else {
                state.copy(debugUseRealCalendarPoolData = enabled)
            }
        }
    }

    fun updateOverviewServerPopupExpanded(expanded: Boolean) {
        _chromeUiState.update { state ->
            if (state.showOverviewServerPopup == expanded) {
                state
            } else {
                state.copy(showOverviewServerPopup = expanded)
            }
        }
    }

    fun updateOverviewServerPopupAnchorBounds(bounds: IntRect?) {
        _chromeUiState.update { state ->
            if (state.overviewServerPopupAnchorBounds == bounds) {
                state
            } else {
                state.copy(overviewServerPopupAnchorBounds = bounds)
            }
        }
    }

    fun updateCafeLevelPopupExpanded(expanded: Boolean) {
        _chromeUiState.update { state ->
            if (state.showCafeLevelPopup == expanded) {
                state
            } else {
                state.copy(showCafeLevelPopup = expanded)
            }
        }
    }

    fun updateCafeLevelPopupAnchorBounds(bounds: IntRect?) {
        _chromeUiState.update { state ->
            if (state.cafeLevelPopupAnchorBounds == bounds) {
                state
            } else {
                state.copy(cafeLevelPopupAnchorBounds = bounds)
            }
        }
    }

    fun updateConsumedScrollToTopSignal(signal: Int) {
        _chromeUiState.update { state ->
            if (state.consumedScrollToTopSignal == signal) {
                state
            } else {
                state.copy(consumedScrollToTopSignal = signal)
            }
        }
    }

    fun updateSettingsRefreshIntervalDropdownExpanded(expanded: Boolean) {
        _chromeUiState.update { state ->
            if (state.settingsRefreshIntervalDropdownExpanded == expanded) {
                state
            } else {
                state.copy(settingsRefreshIntervalDropdownExpanded = expanded)
            }
        }
    }

    fun updateSettingsRefreshIntervalDropdownAnchorBounds(bounds: IntRect?) {
        _chromeUiState.update { state ->
            if (state.settingsRefreshIntervalDropdownAnchorBounds == bounds) {
                state
            } else {
                state.copy(settingsRefreshIntervalDropdownAnchorBounds = bounds)
            }
        }
    }

    fun updateNotificationLeadDropdownExpanded(expanded: Boolean) {
        _chromeUiState.update { state ->
            if (state.notificationLeadDropdownExpanded == expanded) {
                state
            } else {
                state.copy(notificationLeadDropdownExpanded = expanded)
            }
        }
    }

    fun updateNotificationLeadDropdownAnchorBounds(bounds: IntRect?) {
        _chromeUiState.update { state ->
            if (state.notificationLeadDropdownAnchorBounds == bounds) {
                state
            } else {
                state.copy(notificationLeadDropdownAnchorBounds = bounds)
            }
        }
    }

    fun updateSettingsDraft(transform: (BaPageSettingsDraftState) -> BaPageSettingsDraftState) {
        _settingsDraftUiState.update { state ->
            val nextDraft = transform(state.draft)
            if (nextDraft == state.draft) state else state.copy(draft = nextDraft)
        }
    }

    fun updateNotificationDraft(transform: (BaPageNotificationDraftState) -> BaPageNotificationDraftState) {
        _notificationDraftUiState.update { state ->
            val nextDraft = transform(state.draft)
            if (nextDraft == state.draft) state else state.copy(draft = nextDraft)
        }
    }

    fun normalizeApNotifyThresholdText() {
        updateNotificationDraft { draft ->
            val normalized = draft.apNotifyThresholdText.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 120
            draft.copy(apNotifyThresholdText = normalized.toString())
        }
    }

    fun normalizeCafeApNotifyThresholdText() {
        updateNotificationDraft { draft ->
            val normalized = draft.cafeApNotifyThresholdText.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 120
            draft.copy(cafeApNotifyThresholdText = normalized.toString())
        }
    }

    fun refreshCalendar(force: Boolean = false) {
        if (!force) return
        _syncUiState.update { state ->
            state.copy(calendarReloadSignal = state.calendarReloadSignal + 1)
        }
    }

    fun refreshPool(force: Boolean = false) {
        if (!force) return
        _syncUiState.update { state ->
            state.copy(poolReloadSignal = state.poolReloadSignal + 1)
        }
    }

    fun markCalendarPoolHydrationReady() {
        _syncUiState.update { state ->
            if (state.calendarHydrationReady && state.poolHydrationReady) {
                state
            } else {
                state.copy(
                    calendarHydrationReady = true,
                    poolHydrationReady = true,
                )
            }
        }
    }

    fun selectServer(index: Int) {
        val selected = index.coerceIn(0, 2)
        _serverUiState.update { state ->
            if (state.serverIndex == selected) {
                state
            } else {
                state.copy(serverIndex = selected)
            }
        }
        refreshCalendar(force = true)
        refreshPool(force = true)
        viewModelScope.launch {
            BaOfficeRepository.saveServerIndexAsync(selected)
            if (office.idIndependentByServer) {
                office.applyIdentity(BaOfficeRepository.loadIdentityForServer(selected))
            }
            resetCafeVisitBaselineIfNeeded(selected)
            resetArenaRefreshBaselineIfNeeded(selected)
            AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
        }
    }

    fun restoreServerFromStore() {
        viewModelScope.launch {
            val savedServerIndex = BaOfficeRepository.loadServerIndexAsync()
            if (savedServerIndex == _serverUiState.value.serverIndex) return@launch
            if (office.idIndependentByServer) {
                office.applyIdentity(BaOfficeRepository.loadIdentityForServer(savedServerIndex))
            }
            _serverUiState.update { state ->
                state.copy(serverIndex = savedServerIndex)
            }
            refreshCalendar(force = true)
            refreshPool(force = true)
        }
    }

    fun saveSettings(
        sheetState: BaSettingsSheetState,
        currentShowEndedActivities: Boolean,
        currentShowCalendarPoolImages: Boolean,
        serverIndex: Int,
    ) {
        viewModelScope.launch {
            try {
                val persisted =
                    BaSettingsPersistenceRepository.persistSettingsDraftAsync(
                        sheetState = sheetState,
                        currentShowEndedActivities = currentShowEndedActivities,
                        currentShowCalendarPoolImages = currentShowCalendarPoolImages,
                    )
                office.cafeLevel = persisted.savedCafeLevel
                val clampUpdate = office.clampCafeStoredToCapUpdate()
                office
                    .applyIdIndependentByServer(
                        serverIndex = serverIndex,
                        enabled = persisted.idIndependentByServer,
                    ).persistAsync()
                _runtimeUiState.update { state ->
                    state.copy(
                        showEndedPools = persisted.showEndedPools,
                        showEndedActivities = persisted.showEndedActivities,
                        showCalendarPoolImages = persisted.showCalendarPoolImages,
                        mediaAdaptiveRotationEnabled = persisted.mediaAdaptiveRotationEnabled,
                        mediaSaveCustomEnabled = persisted.mediaSaveCustomEnabled,
                        mediaSaveFixedTreeUri = persisted.mediaSaveFixedTreeUri,
                        idIndependentByServer = persisted.idIndependentByServer,
                    )
                }
                _settingsDraftUiState.value =
                    BaOfficeSettingsDraftUiState(
                        BaPageSettingsDraftState(
                            cafeLevel = persisted.savedCafeLevel,
                            mediaAdaptiveRotationEnabled = persisted.mediaAdaptiveRotationEnabled,
                            mediaSaveCustomEnabled = persisted.mediaSaveCustomEnabled,
                            mediaSaveFixedTreeUri = persisted.mediaSaveFixedTreeUri,
                            idIndependentByServer = persisted.idIndependentByServer,
                            showEndedPools = persisted.showEndedPools,
                            showEndedActivities = persisted.showEndedActivities,
                            showCalendarPoolImages = persisted.showCalendarPoolImages,
                        ),
                    )

                val refreshCalendarForEnded =
                    persisted.turningEndedActivitiesOn &&
                        BaSettingsPersistenceRepository.calendarCacheIsBlankAsync(serverIndex)
                val refreshCalendarForImages =
                    persisted.turningImagesOn &&
                        !BaSettingsPersistenceRepository.hasAnyImageInCalendarCacheAsync(serverIndex)
                val refreshPoolForImages =
                    persisted.turningImagesOn &&
                        !BaSettingsPersistenceRepository.hasAnyImageInPoolCacheAsync(serverIndex)
                AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
                _events.emit(
                    BaOfficeEvent.SettingsSaved(
                        persisted = persisted,
                        clampUpdate = clampUpdate,
                        runtimeUpdate = office.applyRuntimeTick(),
                        refreshCalendar = refreshCalendarForEnded || refreshCalendarForImages,
                        refreshPool = refreshPoolForImages,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun saveNotificationSettings(
        sheetState: BaNotificationSettingsSheetState,
        serverIndex: Int,
    ) {
        viewModelScope.launch {
            try {
                val previousCafeApNotifyEnabled = office.cafeApNotifyEnabled
                val previousCafeApNotifyThreshold = office.cafeApNotifyThreshold
                val previousArenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled
                val previousCafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled
                val persisted = BaSettingsPersistenceRepository.persistNotificationSettingsDraftAsync(sheetState)

                office.apNotifyEnabled = sheetState.apNotifyEnabled
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
                if (!office.cafeApNotifyEnabled ||
                    previousCafeApNotifyThreshold != office.cafeApNotifyThreshold ||
                    !previousCafeApNotifyEnabled
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
                            serverIndex = serverIndex,
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
                            serverIndex = serverIndex,
                        )
                    office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
                    BaSettingsPersistenceRepository.saveCafeVisitLastNotifiedSlotAsync(baselineSlotMs)
                }

                AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
                _notificationDraftUiState.value =
                    BaOfficeNotificationDraftUiState(
                        draft = savedDraft,
                        savedDraft = savedDraft,
                    )
                _events.emit(
                    BaOfficeEvent.NotificationSettingsSaved(
                        savedDraft = savedDraft,
                        runtimeUpdate = office.applyRuntimeTick(),
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun saveRefreshInterval(
        hours: Int,
        calendarLastSyncMs: Long,
    ) {
        viewModelScope.launch {
            try {
                val persisted =
                    BaSettingsPersistenceRepository.persistRefreshIntervalAsync(
                        hours = hours,
                        calendarLastSyncMs = calendarLastSyncMs,
                    )
                _runtimeUiState.update { state ->
                    state.copy(calendarRefreshIntervalHours = persisted.hours)
                }
                _events.emit(
                    BaOfficeEvent.RefreshIntervalSaved(
                        hours = persisted.hours,
                        shouldRefresh = persisted.shouldRefresh,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    private suspend fun resetCafeVisitBaselineIfNeeded(serverIndex: Int) {
        if (!office.cafeVisitNotifyEnabled) return
        val baselineSlotMs =
            currentCafeStudentRefreshSlotMs(
                nowMs = System.currentTimeMillis(),
                serverIndex = serverIndex,
            )
        office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
        BaOfficeRepository.saveCafeVisitLastNotifiedSlotMsAsync(baselineSlotMs)
    }

    private suspend fun resetArenaRefreshBaselineIfNeeded(serverIndex: Int) {
        if (!office.arenaRefreshNotifyEnabled) return
        val baselineSlotMs =
            currentArenaRefreshSlotMs(
                nowMs = System.currentTimeMillis(),
                serverIndex = serverIndex,
            )
        office.arenaRefreshLastNotifiedSlotMs = baselineSlotMs
        BaOfficeRepository.saveArenaRefreshLastNotifiedSlotMsAsync(baselineSlotMs)
    }

    private fun notificationRuntimeDraft(base: BaPageNotificationDraftState): BaPageNotificationDraftState =
        base.copy(
            apNotifyEnabled = office.apNotifyEnabled,
            cafeApNotifyEnabled = office.cafeApNotifyEnabled,
            arenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled,
            cafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled,
            apNotifyThresholdText = office.apNotifyThreshold.toString(),
            cafeApNotifyThresholdText = office.cafeApNotifyThreshold.toString(),
        )
}
