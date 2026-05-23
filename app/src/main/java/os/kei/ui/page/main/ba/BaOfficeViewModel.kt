package os.kei.ui.page.main.ba

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs

internal data class BaOfficeServerUiState(
    val serverIndex: Int = BaPageSnapshot().serverIndex,
)

internal data class BaOfficeChromeUiState(
    val showSettingsSheet: Boolean = false,
    val showNotificationSettingsSheet: Boolean = false,
    val showDebugSheet: Boolean = false,
    val debugUseRealCalendarPoolData: Boolean = true,
)

internal data class BaOfficeSyncUiState(
    val calendarReloadSignal: Int = 0,
    val poolReloadSignal: Int = 0,
    val calendarHydrationReady: Boolean = false,
    val poolHydrationReady: Boolean = false,
)

internal data class BaOfficeRuntimeUiState(
    val showEndedPools: Boolean = BaPageSnapshot().showEndedPools,
    val showEndedActivities: Boolean = BaPageSnapshot().showEndedActivities,
    val showCalendarPoolImages: Boolean = BaPageSnapshot().showCalendarPoolImages,
    val mediaAdaptiveRotationEnabled: Boolean = BaPageSnapshot().mediaAdaptiveRotationEnabled,
    val mediaSaveCustomEnabled: Boolean = BaPageSnapshot().mediaSaveCustomEnabled,
    val mediaSaveFixedTreeUri: String = BaPageSnapshot().mediaSaveFixedTreeUri,
    val idIndependentByServer: Boolean = BaPageSnapshot().idIndependentByServer,
    val calendarRefreshIntervalHours: Int = BaPageSnapshot().calendarRefreshIntervalHours,
)

internal data class BaOfficeSettingsDraftUiState(
    val draft: BaPageSettingsDraftState = BaPageSnapshot().toSettingsDraftState(),
)

internal data class BaOfficeNotificationDraftUiState(
    val draft: BaPageNotificationDraftState = BaPageSnapshot().toNotificationDraftState(),
    val savedDraft: BaPageNotificationDraftState = BaPageSnapshot().toNotificationDraftState(),
)

internal sealed interface BaOfficeEvent {
    data class SettingsSaved(
        val persisted: BaSettingsPersistenceResult,
        val clampUpdate: BaRuntimePersistenceUpdate?,
        val runtimeUpdate: BaRuntimePersistenceUpdate?,
        val refreshCalendar: Boolean,
        val refreshPool: Boolean,
    ) : BaOfficeEvent

    data class NotificationSettingsSaved(
        val savedDraft: BaPageNotificationDraftState,
        val runtimeUpdate: BaRuntimePersistenceUpdate?,
    ) : BaOfficeEvent

    data class RefreshIntervalSaved(
        val hours: Int,
        val shouldRefresh: Boolean,
    ) : BaOfficeEvent

    data class OperationFailed(
        val error: Throwable,
    ) : BaOfficeEvent
}

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
            state.copy(showSettingsSheet = true)
        }
    }

    fun hideSettingsSheet(currentDraft: BaPageSettingsDraftState) {
        _settingsDraftUiState.value = BaOfficeSettingsDraftUiState(currentDraft)
        _chromeUiState.update { state ->
            state.copy(showSettingsSheet = false)
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
            state.copy(showNotificationSettingsSheet = true)
        }
    }

    fun hideNotificationSettingsSheet() {
        _notificationDraftUiState.update { state ->
            state.copy(draft = state.savedDraft)
        }
        _chromeUiState.update { state ->
            state.copy(showNotificationSettingsSheet = false)
        }
    }

    fun showDebugSheet() {
        _chromeUiState.update { state ->
            state.copy(showDebugSheet = true)
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

private fun BaPageSnapshot.toRuntimeUiState(): BaOfficeRuntimeUiState =
    BaOfficeRuntimeUiState(
        showEndedPools = showEndedPools,
        showEndedActivities = showEndedActivities,
        showCalendarPoolImages = showCalendarPoolImages,
        mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
        mediaSaveCustomEnabled = mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri = mediaSaveFixedTreeUri,
        idIndependentByServer = idIndependentByServer,
        calendarRefreshIntervalHours = calendarRefreshIntervalHours,
    )

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
