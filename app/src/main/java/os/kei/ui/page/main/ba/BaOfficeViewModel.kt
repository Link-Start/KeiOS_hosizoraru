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
import os.kei.R
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.ext.showToast
import os.kei.core.log.AppLogger
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountProfileInput
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaDailyDoneConfig
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal class BaOfficeViewModel private constructor(
    application: Application,
    private val repository: BaOfficePageRepository,
    private val persistRuntimeUpdate: suspend (BaRuntimePersistenceUpdate) -> Unit,
    private val scheduleBaApThreshold: () -> Unit,
    /**
     * Runs the saved daily-done template and reports the outcome. Injected for the same reason the two
     * above are: it writes straight to the settings store and toasts, neither of which a unit test can
     * stand up.
     */
    private val applyDailyDone: suspend (BaAccountId?) -> Unit,
) : AndroidViewModel(application) {
    private val defaultSnapshot = BaPageSnapshot()
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

    constructor(application: Application) : this(
        application = application,
        repository = BaOfficePageRepository(BaSystemOfficeClock),
        persistRuntimeUpdate = { update -> update.persistAsync() },
        scheduleBaApThreshold = {
            AppBackgroundScheduler.scheduleBaApThreshold(application)
        },
        applyDailyDone = { accountId ->
            BaDailyDoneRunner.applyAndToast(context = application, accountId = accountId)
        },
    )

    init {
        viewModelScope.launch {
            val snapshot = repository.loadInitialSnapshot()
            val accountState = repository.loadAccountState()
            if (office.matchesSnapshot(defaultSnapshot)) {
                applyOfficeSnapshot(
                    snapshot = snapshot,
                    accountState = accountState,
                    persistRuntimeTick = true,
                )
            } else {
                updateOfficeUiStateFromSnapshot(
                    snapshot = snapshot,
                    accountState = accountState,
                    updateNotificationDraft = true,
                )
            }
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

    fun showAccountManagementSheet(initialEditAccountId: BaAccountId? = null) {
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(
                    showAccountManagementSheet = true,
                    accountManagementInitialEditAccountId = initialEditAccountId,
                )
        }
    }

    fun hideAccountManagementSheet() {
        _chromeUiState.update { state ->
            state.copy(
                showAccountManagementSheet = false,
                accountManagementInitialEditAccountId = null,
            )
        }
    }

    fun showNotificationSettingsSheet() {
        val savedDraft =
            notificationRuntimeDraft(
                base = _notificationDraftUiState.value.savedDraft,
                office = office,
            )
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

    fun showApLimitToolsSheet() {
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(showApLimitToolsSheet = true)
        }
    }

    fun hideApLimitToolsSheet() {
        _chromeUiState.update { state ->
            state.copy(showApLimitToolsSheet = false)
        }
    }

    fun showCafeApToolsSheet() {
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(showCafeApToolsSheet = true)
        }
    }

    fun hideCafeApToolsSheet() {
        _chromeUiState.update { state ->
            state.copy(showCafeApToolsSheet = false)
        }
    }

    /**
     * Opens the daily-done template sheet with the template already read.
     *
     * Read here rather than by the sheet, and before [BaDailyDoneSheetUiState.show] flips. The record is
     * global — the tiles, the launcher shortcuts and MCP apply the same one — so it has to be re-read on
     * every opening, and reading it once the sheet is already up would show a frame of compiled-in
     * defaults before snapping to the teacher's values.
     */
    fun showDailyDoneSheet() {
        viewModelScope.launch {
            try {
                val config = repository.loadDailyDoneConfig()
                _chromeUiState.update { state ->
                    state
                        .withoutFloatingPopups()
                        .copy(
                            dailyDoneSheet =
                                BaDailyDoneSheetUiState(
                                    show = true,
                                    config = config,
                                ),
                        )
                }
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    /**
     * Closes the sheet. Deliberately does not stop a run in flight — see [applyDailyDoneTemplate], which
     * owns its own scope precisely so that dismissing the sheet cannot leave half the accounts applied.
     */
    fun hideDailyDoneSheet() {
        _chromeUiState.update { state ->
            state.copy(dailyDoneSheet = state.dailyDoneSheet.copy(show = false))
        }
    }

    /** Records the template a later trigger will apply, without running it now. */
    fun saveDailyDoneTemplate(config: BaDailyDoneConfig) {
        viewModelScope.launch {
            try {
                repository.saveDailyDoneConfig(config)
                closeDailyDoneSheet(config)
                getApplication<Application>().showToast(R.string.ba_daily_template_saved)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    /**
     * Saves the template and runs it across every enabled account.
     *
     * All accounts, like the tile the dock button stands in for and like the launcher shortcut. Not a
     * silent choice: the sheet names its scope above the controls, which is the whole reason this entry
     * point summons a sheet instead of applying on the tap.
     *
     * Three orderings here are load-bearing.
     *
     * [currentRuntimeUpdate] goes first. The page keeps AP and the cafe pool in memory between runtime
     * ticks, so without flushing them the template plans against a staler pool than the one on screen —
     * and can report "already done" for a page the reload then visibly changes underneath the toast.
     *
     * The template is saved before it is applied, so what just ran is also what a later tile tap will
     * run; the same ordering [BaDailyDoneTemplateActivity] keeps for the same reason.
     *
     * The reload last is not a nicety. The run writes straight to the store, so the office still holds
     * the pre-run values: without re-reading them the cards keep showing the old pool *and* the next
     * runtime tick persists it back over the template.
     *
     * On [viewModelScope] rather than the sheet's own scope, because leaving the page mid-run would
     * otherwise cancel the pass between two accounts.
     */
    fun applyDailyDoneTemplate(
        config: BaDailyDoneConfig,
        currentRuntimeUpdate: BaRuntimePersistenceUpdate?,
    ) {
        if (_chromeUiState.value.dailyDoneSheet.applying) return
        _chromeUiState.update { state ->
            state.copy(dailyDoneSheet = state.dailyDoneSheet.copy(applying = true))
        }
        viewModelScope.launch {
            try {
                currentRuntimeUpdate?.let { update -> persistRuntimeUpdate(update) }
                repository.saveDailyDoneConfig(config)
                // A null target is the store's "every enabled account" filter.
                applyDailyDone(null)
                reloadRuntimeSettings()
                closeDailyDoneSheet(config)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                // Left open on failure. The error arrives as a toast, and closing the sheet under it
                // would take the teacher's edits with it; the stored template is untouched here, so the
                // draft in the sheet survives and the same tap can be repeated.
                _events.emit(BaOfficeEvent.OperationFailed(error))
            } finally {
                _chromeUiState.update { state ->
                    state.copy(dailyDoneSheet = state.dailyDoneSheet.copy(applying = false))
                }
            }
        }
    }

    /** Closes the sheet on the template that was just written, so a reopen starts from it. */
    private fun closeDailyDoneSheet(config: BaDailyDoneConfig) {
        _chromeUiState.update { state ->
            state.copy(
                dailyDoneSheet =
                    state.dailyDoneSheet.copy(
                        show = false,
                        config = config,
                    ),
            )
        }
    }

    fun showCafeCooldownEditSheet(target: BaCafeCooldownEditTarget) {
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(cafeCooldownEditTarget = target)
        }
    }

    fun hideCafeCooldownEditSheet() {
        _chromeUiState.update { state ->
            state.copy(cafeCooldownEditTarget = null)
        }
    }

    fun showCraftSlotEditSheet(target: BaCraftSlotEditTarget) {
        _chromeUiState.update { state ->
            state
                .withoutFloatingPopups()
                .copy(craftSlotEditTarget = target)
        }
    }

    fun hideCraftSlotEditSheet() {
        _chromeUiState.update { state ->
            state.copy(craftSlotEditTarget = null)
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

    /**
     * Folds the Craft Chamber card open or shut, and remembers it.
     *
     * The flip lands in state first and persists after: the disclosure has to animate on the same
     * frame as the tap, and the write is a single MMKV boolean whose only job is surviving process
     * death. A failed write costs the teacher one re-tap next launch, so it is not worth an event.
     */
    fun updateCraftCardExpanded(expanded: Boolean) {
        if (_runtimeUiState.value.craftCardExpanded == expanded) return
        _runtimeUiState.update { state -> state.copy(craftCardExpanded = expanded) }
        viewModelScope.launch {
            try {
                repository.saveCraftCardExpanded(expanded)
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                AppLogger.e(TAG, "craft card expansion save failed", error)
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
            reloadRuntimeSettings()
        }
    }

    /** The body of [refreshRuntimeSettingsFromStore], for callers that already own a coroutine. */
    private suspend fun reloadRuntimeSettings() {
        val snapshot = repository.loadInitialSnapshot()
        val accountState = repository.loadAccountState()
        applyOfficeSnapshot(
            snapshot = snapshot,
            accountState = accountState,
            persistRuntimeTick = true,
        )
        scheduleBaApThreshold()
    }

    fun selectActiveAccount(
        accountId: BaAccountId,
        currentRuntimeUpdate: BaRuntimePersistenceUpdate?,
    ) {
        if (_accountUiState.value.activeAccountId == accountId) return
        viewModelScope.launch {
            try {
                currentRuntimeUpdate?.let { update -> persistRuntimeUpdate(update) }
                val snapshot = repository.selectActiveAccount(accountId) ?: return@launch
                val accountState = repository.loadAccountState()
                applyOfficeSnapshot(
                    snapshot = snapshot,
                    accountState = accountState,
                    persistRuntimeTick = true,
                )
                refreshCalendar(force = true)
                refreshPool(force = true)
                scheduleBaApThreshold()
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
                scheduleBaApThreshold()
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
                scheduleBaApThreshold()
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

                scheduleBaApThreshold()
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
                office.keepApRemindersReadUntilBelowThreshold =
                    persisted.keepApRemindersReadUntilBelowThreshold
                office.arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled
                office.cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled
                office.craftNotifyEnabled = persisted.craftNotifyEnabled
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

                scheduleBaApThreshold()
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

    private suspend fun applyAccountMutationState(
        accountState: BaAccountStoreSnapshot,
        refreshData: Boolean = true,
    ) {
        val snapshot = repository.loadInitialSnapshot()
        applyOfficeSnapshot(
            snapshot = snapshot,
            accountState = accountState,
            persistRuntimeTick = true,
        )
        if (refreshData) {
            refreshCalendar(force = true)
            refreshPool(force = true)
        }
        scheduleBaApThreshold()
    }

    private suspend fun applyOfficeSnapshot(
        snapshot: BaPageSnapshot,
        accountState: BaAccountStoreSnapshot,
        persistRuntimeTick: Boolean,
    ) {
        office.applySnapshot(snapshot)
        val runtimeUpdate =
            if (persistRuntimeTick) {
                office
                    .applyRuntimeTick()
                    ?.withAccountId(accountState.activeAccountId)
            } else {
                null
            }
        runtimeUpdate?.let { update -> persistRuntimeUpdate(update) }
        updateOfficeUiStateFromSnapshot(
            snapshot = snapshot,
            accountState = accountState,
            updateNotificationDraft = !_chromeUiState.value.showNotificationSettingsSheet,
        )
    }

    private fun updateOfficeUiStateFromSnapshot(
        snapshot: BaPageSnapshot,
        accountState: BaAccountStoreSnapshot,
        updateNotificationDraft: Boolean,
    ) {
        _accountUiState.value = accountState.toOfficeAccountUiState()
        _serverUiState.value = BaOfficeServerUiState(snapshot.serverIndex)
        _runtimeUiState.value = snapshot.toRuntimeUiState()
        if (!_chromeUiState.value.showSettingsSheet) {
            _settingsDraftUiState.value = BaOfficeSettingsDraftUiState(snapshot.toSettingsDraftState())
        }
        if (updateNotificationDraft) {
            val notificationDraft = snapshot.toNotificationDraftState()
            _notificationDraftUiState.value =
                BaOfficeNotificationDraftUiState(
                    draft = notificationDraft,
                    savedDraft = notificationDraft,
                )
        }
    }

    companion object {
        private const val TAG = "BaOfficeViewModel"

        internal fun createForTest(
            application: Application,
            repository: BaOfficePageRepository,
            persistRuntimeUpdate: suspend (BaRuntimePersistenceUpdate) -> Unit,
            scheduleBaApThreshold: () -> Unit,
            applyDailyDone: suspend (BaAccountId?) -> Unit = {},
        ): BaOfficeViewModel =
            BaOfficeViewModel(
                application = application,
                repository = repository,
                persistRuntimeUpdate = persistRuntimeUpdate,
                scheduleBaApThreshold = scheduleBaApThreshold,
                applyDailyDone = applyDailyDone,
            )
    }
}

internal fun notificationRuntimeDraft(
    base: BaPageNotificationDraftState,
    office: BaOfficeController,
): BaPageNotificationDraftState =
    base.copy(
        apNotifyEnabled = office.apNotifyEnabled,
        cafeApNotifyEnabled = office.cafeApNotifyEnabled,
        keepApRemindersReadUntilBelowThreshold =
            office.keepApRemindersReadUntilBelowThreshold,
        arenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled,
        cafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled,
        craftNotifyEnabled = office.craftNotifyEnabled,
        apNotifyThresholdText = office.apNotifyThreshold.toString(),
        cafeApNotifyThresholdText = office.cafeApNotifyThreshold.toString(),
    )
