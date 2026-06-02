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
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountProfileInput
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal class BaOfficeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val defaultSnapshot = BaPageSnapshot()
    private val clock = BaSystemOfficeClock
    private val repository = BaOfficePageRepository(clock)
    private val _chromeUiState = MutableStateFlow(BaOfficeChromeUiState())
    val chromeUiState: StateFlow<BaOfficeChromeUiState> = _chromeUiState.asStateFlow()
    private val _accountUiState = MutableStateFlow(BaOfficeAccountUiState())
    val accountUiState: StateFlow<BaOfficeAccountUiState> = _accountUiState.asStateFlow()
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
            accountUiState,
            syncUiState,
            serverUiState,
            runtimeUiState,
            settingsDraftUiState,
            notificationDraftUiState,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val chrome = values[0] as BaOfficeChromeUiState
            val account = values[1] as BaOfficeAccountUiState
            val sync = values[2] as BaOfficeSyncUiState
            val server = values[3] as BaOfficeServerUiState
            val runtime = values[4] as BaOfficeRuntimeUiState
            val settingsDraft = values[5] as BaOfficeSettingsDraftUiState
            val notificationDraft = values[6] as BaOfficeNotificationDraftUiState
            BaOfficePageUiState(
                chromeUiState = chrome,
                accountUiState = account,
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
                    accountUiState = _accountUiState.value,
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
            val snapshot = repository.loadInitialSnapshot()
            val accountState = repository.loadAccountState()
            if (office.matchesSnapshot(defaultSnapshot)) {
                office.applySnapshot(snapshot)
            }
            _accountUiState.value = accountState.toOfficeAccountUiState()
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
            repository.clearListScrollState()
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
            )
        }
    }

    fun showAccountManagementSheet() {
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(showAccountManagementSheet = true)
        }
    }

    fun hideAccountManagementSheet() {
        _chromeUiState.update { state ->
            state.copy(showAccountManagementSheet = false)
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

    fun updateDebugUseRealCalendarPoolData(enabled: Boolean) {
        _chromeUiState.update { state ->
            if (state.debugUseRealCalendarPoolData == enabled) {
                state
            } else {
                state.copy(debugUseRealCalendarPoolData = enabled)
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

    fun restoreServerFromStore() {
        viewModelScope.launch {
            val restored =
                repository.restoreServerSelection(
                    currentServerIndex = _serverUiState.value.serverIndex,
                ) ?: return@launch
            _serverUiState.update { state ->
                state.copy(serverIndex = restored.serverIndex)
            }
            refreshCalendar(force = true)
            refreshPool(force = true)
        }
    }

    fun refreshRuntimeSettingsFromStore() {
        viewModelScope.launch {
            val snapshot = repository.loadInitialSnapshot()
            val accountState = repository.loadAccountState()
            _accountUiState.value = accountState.toOfficeAccountUiState()
            _runtimeUiState.update { state ->
                state.copy(
                    showEndedPools = snapshot.showEndedPools,
                    showEndedActivities = snapshot.showEndedActivities,
                    showCalendarPoolImages = snapshot.showCalendarPoolImages,
                    mediaAdaptiveRotationEnabled = snapshot.mediaAdaptiveRotationEnabled,
                    mediaSaveCustomEnabled = snapshot.mediaSaveCustomEnabled,
                    mediaSaveFixedTreeUri = snapshot.mediaSaveFixedTreeUri,
                    calendarRefreshIntervalHours = snapshot.calendarRefreshIntervalHours,
                )
            }
            if (!_chromeUiState.value.showSettingsSheet) {
                _settingsDraftUiState.value = BaOfficeSettingsDraftUiState(snapshot.toSettingsDraftState())
            }
        }
    }

    fun selectActiveAccount(
        accountId: BaAccountId,
        currentRuntimeUpdate: BaRuntimePersistenceUpdate?,
    ) {
        if (_accountUiState.value.activeAccountId == accountId) return
        viewModelScope.launch {
            try {
                currentRuntimeUpdate?.persistAsync()
                val snapshot = repository.selectActiveAccount(accountId) ?: return@launch
                val accountState = repository.loadAccountState()
                office.applySnapshot(snapshot)
                _accountUiState.value = accountState.toOfficeAccountUiState()
                _serverUiState.value = BaOfficeServerUiState(snapshot.serverIndex)
                _runtimeUiState.value = snapshot.toRuntimeUiState()
                _settingsDraftUiState.value = BaOfficeSettingsDraftUiState(snapshot.toSettingsDraftState())
                val notificationDraft = snapshot.toNotificationDraftState()
                _notificationDraftUiState.value =
                    BaOfficeNotificationDraftUiState(
                        draft = notificationDraft,
                        savedDraft = notificationDraft,
                    )
                refreshCalendar(force = true)
                refreshPool(force = true)
                AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun updateAllAccountsFollowGlobalNotificationSettings(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val accountState = repository.saveAllAccountsFollowGlobalNotificationSettings(enabled)
                _accountUiState.value = accountState.toOfficeAccountUiState()
                AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun updateAccountEnabled(
        accountId: BaAccountId,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            try {
                val accountState =
                    repository.saveAccountEnabled(
                        accountId = accountId,
                        enabled = enabled,
                    )
                _accountUiState.value = accountState.toOfficeAccountUiState()
                AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun addAccount(input: BaAccountProfileInput) {
        viewModelScope.launch {
            try {
                val accountState = repository.addAccount(input)
                applyAccountMutationState(accountState)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun updateAccountProfile(
        accountId: BaAccountId,
        input: BaAccountProfileInput,
    ) {
        viewModelScope.launch {
            try {
                val accountState =
                    repository.updateAccountProfile(
                        accountId = accountId,
                        input = input,
                    )
                applyAccountMutationState(accountState)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun deleteAccount(accountId: BaAccountId) {
        viewModelScope.launch {
            try {
                val accountState = repository.deleteAccount(accountId)
                applyAccountMutationState(accountState)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun moveAccount(
        accountId: BaAccountId,
        offset: Int,
    ) {
        viewModelScope.launch {
            try {
                val accountState = repository.moveAccount(accountId = accountId, offset = offset)
                applyAccountMutationState(accountState, refreshData = false)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun saveSettings(sheetState: BaSettingsSheetState) {
        viewModelScope.launch {
            try {
                val saveResult =
                    repository.persistSettings(sheetState = sheetState)
                val persisted = saveResult.persisted
                office.cafeLevel = persisted.savedCafeLevel
                val clampUpdate = office.clampCafeStoredToCapUpdate()
                _runtimeUiState.update { state ->
                    state.copy(
                        mediaAdaptiveRotationEnabled = persisted.mediaAdaptiveRotationEnabled,
                        mediaSaveCustomEnabled = persisted.mediaSaveCustomEnabled,
                        mediaSaveFixedTreeUri = persisted.mediaSaveFixedTreeUri,
                    )
                }
                _settingsDraftUiState.value =
                    BaOfficeSettingsDraftUiState(
                        BaPageSettingsDraftState(
                            cafeLevel = persisted.savedCafeLevel,
                            mediaAdaptiveRotationEnabled = persisted.mediaAdaptiveRotationEnabled,
                            mediaSaveCustomEnabled = persisted.mediaSaveCustomEnabled,
                            mediaSaveFixedTreeUri = persisted.mediaSaveFixedTreeUri,
                        ),
                    )

                AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
                _events.emit(
                    BaOfficeEvent.SettingsSaved(
                        persisted = persisted,
                        clampUpdate = clampUpdate,
                        runtimeUpdate = office.applyRuntimeTick(),
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
                val saveResult =
                    repository.persistNotificationSettings(
                        sheetState = sheetState,
                        previousCafeApNotifyEnabled = previousCafeApNotifyEnabled,
                        previousCafeApNotifyThreshold = previousCafeApNotifyThreshold,
                        previousArenaRefreshNotifyEnabled = previousArenaRefreshNotifyEnabled,
                        previousCafeVisitNotifyEnabled = previousCafeVisitNotifyEnabled,
                        serverIndex = serverIndex,
                    )
                val persisted = saveResult.persisted

                office.apNotifyEnabled = sheetState.apNotifyEnabled
                office.cafeApNotifyEnabled = persisted.cafeApNotifyEnabled
                office.arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled
                office.cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled
                office.apNotifyThreshold = persisted.savedThreshold
                office.cafeApNotifyThreshold = persisted.savedCafeApThreshold
                val savedDraft = saveResult.savedDraft
                if (saveResult.resetCafeApLastNotifiedLevel) {
                    office.cafeApLastNotifiedLevel = -1
                }
                saveResult.arenaRefreshLastNotifiedSlotMs?.let { slotMs ->
                    office.arenaRefreshLastNotifiedSlotMs = slotMs
                }
                saveResult.cafeVisitLastNotifiedSlotMs?.let { slotMs ->
                    office.cafeVisitLastNotifiedSlotMs = slotMs
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

    private fun notificationRuntimeDraft(base: BaPageNotificationDraftState): BaPageNotificationDraftState =
        base.copy(
            apNotifyEnabled = office.apNotifyEnabled,
            cafeApNotifyEnabled = office.cafeApNotifyEnabled,
            arenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled,
            cafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled,
            apNotifyThresholdText = office.apNotifyThreshold.toString(),
            cafeApNotifyThresholdText = office.cafeApNotifyThreshold.toString(),
        )

    private suspend fun applyAccountMutationState(
        accountState: BaAccountStoreSnapshot,
        refreshData: Boolean = true,
    ) {
        val snapshot = repository.loadInitialSnapshot()
        office.applySnapshot(snapshot)
        _accountUiState.value = accountState.toOfficeAccountUiState()
        _serverUiState.value = BaOfficeServerUiState(snapshot.serverIndex)
        _runtimeUiState.value = snapshot.toRuntimeUiState()
        if (!_chromeUiState.value.showSettingsSheet) {
            _settingsDraftUiState.value = BaOfficeSettingsDraftUiState(snapshot.toSettingsDraftState())
        }
        if (!_chromeUiState.value.showNotificationSettingsSheet) {
            val notificationDraft = snapshot.toNotificationDraftState()
            _notificationDraftUiState.value =
                BaOfficeNotificationDraftUiState(
                    draft = notificationDraft,
                    savedDraft = notificationDraft,
                )
        }
        if (refreshData) {
            refreshCalendar(force = true)
            refreshPool(force = true)
        }
        AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
    }
}
