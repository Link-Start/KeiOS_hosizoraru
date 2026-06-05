package os.kei.ui.page.main.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the WebDAV sync page. Holds editable connection fields, the configured/auto-sync flags,
 * and per-item sync state. All status is exposed as enums ([WebDavItemStatus] /
 * [WebDavConnectionStatus]) so the page owns the localized strings.
 */
internal class WebDavSyncViewModel(
    private val repository: WebDavSyncRepository = WebDavSyncRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(repository.buildInitialState())
    val uiState: StateFlow<WebDavSyncUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null

    // ── Field edits ────────────────────────────────────────────────────

    fun updateServerUrl(value: String) {
        _uiState.update { it.copy(serverUrl = value, urlError = null, connectionResult = null) }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value, connectionResult = null) }
    }

    fun updateAppPassword(value: String) {
        _uiState.update { it.copy(appPassword = value, connectionResult = null) }
    }

    fun updateRemoteDir(value: String) {
        _uiState.update { it.copy(remoteDir = value, connectionResult = null) }
    }

    fun togglePasswordVisible() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun selectProvider(provider: WebDavProvider) {
        _uiState.update { state ->
            state.copy(
                provider = provider,
                serverUrl = provider.presetServerUrl ?: state.serverUrl,
                remoteDir = state.remoteDir.ifBlank { provider.defaultRemoteDir },
                urlError = null,
                connectionResult = null,
            )
        }
    }

    // ── Save / clear ───────────────────────────────────────────────────

    fun saveConfig() {
        val s = _uiState.value
        if (s.interactionLocked) return
        if (!validate()) return
        viewModelScope.launch {
            val config =
                repository.saveConfig(
                    provider = s.provider,
                    serverUrl = s.serverUrl,
                    username = s.username,
                    appPassword = s.appPassword,
                    remoteDir = s.remoteDir,
                )
            _uiState.update { state ->
                state.copy(
                    isConfigured = true,
                    serverUrl = config.serverUrl,
                    remoteDir = config.remoteDir,
                    urlError = null,
                )
            }
        }
    }

    fun clearConfig() {
        if (_uiState.value.interactionLocked) return
        viewModelScope.launch {
            _uiState.value = repository.clearConfig()
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        if (_uiState.value.interactionLocked) return
        _uiState.update { it.copy(autoSyncEnabled = enabled) }
        viewModelScope.launch {
            repository.setAutoSyncEnabled(enabled)
        }
    }

    // ── Refresh remote summary (read-only) ─────────────────────────────

    /**
     * Probe the server for every enabled item and update the per-item remote summary cards.
     * Read-only: never touches local state. Surfaces auth / network failures through the same
     * status vocabulary as sync so the UI can localise them. The user invokes this manually
     * from the page so other devices can see what's on the server before deciding whether to
     * Sync, Upload, or Download.
     */
    fun refreshRemoteSummary(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) {
        if (_uiState.value.interactionLocked) return
        viewModelScope.launch {
            val config = repository.loadConfig() ?: run {
                _uiState.update { it.copy(missingConfig = true) }
                return@launch
            }
            val targets = repository.loadEnabledItems()
            if (targets.isEmpty()) return@launch
            _uiState.update { it.copy(refreshingRemote = true, missingConfig = false) }
            val updatedStates = _uiState.value.itemStates.toMutableMap()
            for (item in targets) {
                val port = dataPorts[item] ?: continue
                val current = updatedStates[item] ?: WebDavSyncItemUiState()
                updatedStates[item] = current.copy(remoteProbeError = null)
                val result = repository.probeRemote(config, item, port)
                updatedStates[item] = when (val outcome = result.outcome) {
                    is WebDavRemoteProbeOutcome.Error -> {
                        current.copy(
                            remoteSummary = result.summary,
                            remoteProbeError = WebDavItemOutcome(outcome.status, outcome.detail),
                        )
                    }

                    else -> {
                        current.copy(
                            remoteSummary = result.summary,
                            remoteProbeError = null,
                        )
                    }
                }
            }
            // Per-item probe timestamps are written by the engine; record the wall-clock for the
            // batch so the UI can render "remote refreshed: <relative time>" without scanning all
            // five entries.
            val lastRemoteProbeTimeMs = repository.recordRemoteProbeBatch()
            val itemStates = repository.loadItemStates(updatedStates)
            _uiState.update { state ->
                state.copy(
                    refreshingRemote = false,
                    lastRemoteProbeTimeMs = lastRemoteProbeTimeMs,
                    itemStates = itemStates,
                )
            }
        }
    }

    // ── Connection test ────────────────────────────────────────────────

    fun testConnection() {
        val s = _uiState.value
        if (s.interactionLocked) return
        if (!validate()) return
        val config = s.provider.buildConfig(s.serverUrl, s.username, s.appPassword, s.remoteDir)
        viewModelScope.launch {
            _uiState.update { it.copy(testing = true, connectionResult = null) }
            val outcome = repository.testConnection(config)
            _uiState.update { it.copy(testing = false, connectionResult = outcome) }
        }
    }

    // ── Item enable toggle ─────────────────────────────────────────────

    fun toggleItem(item: WebDavSyncItem) {
        val currentState = _uiState.value
        if (currentState.interactionLocked) return
        val enabled = !(currentState.itemStates[item]?.enabled ?: true)
        _uiState.update { state ->
            state.copy(
                itemStates =
                    state.itemStates + (
                        item to (state.itemStates[item] ?: WebDavSyncItemUiState()).copy(enabled = enabled)
                        ),
            )
        }
        viewModelScope.launch {
            val itemStates =
                repository.setItemEnabled(
                    item = item,
                    enabled = enabled,
                    previous = _uiState.value.itemStates,
                )
            _uiState.update { it.copy(itemStates = itemStates) }
        }
    }

    // ── Sync actions ───────────────────────────────────────────────────

    fun requestBatchPlan(
        kind: WebDavBatchKind,
        dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    ) {
        val state = _uiState.value
        if (state.interactionLocked || state.pendingPlan != null) return
        if (!hasEnabledItems()) return
        syncJob = viewModelScope.launch {
            val config = repository.loadConfig() ?: run {
                _uiState.update { it.copy(missingConfig = true) }
                return@launch
            }
            val targets = repository.loadEnabledItems()
            if (targets.isEmpty()) return@launch
            _uiState.update {
                it.copy(
                    planningKind = kind,
                    missingConfig = false,
                    pendingPlan = null,
                )
            }
            val plan =
                repository.preparePlan(
                    kind = kind,
                    scope = WebDavSyncPlanScope.Batch,
                    config = config,
                    targets = targets,
                    dataPorts = dataPorts,
                )
            val itemStates = itemStatesWithPlan(
                base = repository.loadItemStates(_uiState.value.itemStates),
                plan = plan,
            )
            _uiState.update {
                it.copy(
                    planningKind = null,
                    pendingPlan = plan,
                    lastRemoteProbeTimeMs = plan.createdAtMs,
                    itemStates = itemStates,
                )
            }
            refreshLocalCountsInternal(dataPorts)
        }
    }

    fun requestItemPlan(
        item: WebDavSyncItem,
        kind: WebDavBatchKind,
        dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    ) {
        val state = _uiState.value
        if (state.interactionLocked || state.pendingPlan != null) return
        syncJob = viewModelScope.launch {
            if (!repository.isItemEnabled(item)) return@launch
            val config = repository.loadConfig() ?: run {
                _uiState.update { it.copy(missingConfig = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    planningKind = kind,
                    missingConfig = false,
                    pendingPlan = null,
                )
            }
            val plan =
                repository.preparePlan(
                    kind = kind,
                    scope = WebDavSyncPlanScope.Single(item),
                    config = config,
                    targets = listOf(item),
                    dataPorts = dataPorts,
                )
            val itemStates = itemStatesWithPlan(
                base = repository.loadItemStates(_uiState.value.itemStates),
                plan = plan,
            )
            _uiState.update {
                it.copy(
                    planningKind = null,
                    pendingPlan = plan,
                    lastRemoteProbeTimeMs = plan.createdAtMs,
                    itemStates = itemStates,
                )
            }
            refreshLocalCountsInternal(dataPorts)
        }
    }

    fun dismissPlan() {
        _uiState.update { it.copy(pendingPlan = null) }
    }

    fun confirmPlan(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) {
        val plan = _uiState.value.pendingPlan ?: return
        if (plan.hasBlockingError) return
        _uiState.update { it.copy(pendingPlan = null) }
        runPlannedBatch(plan, dataPorts)
    }

    private fun runPlannedBatch(
        plan: WebDavSyncPlan,
        dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    ) {
        if (_uiState.value.interactionLocked) return
        syncJob = viewModelScope.launch {
            val config = repository.loadConfig() ?: run {
                _uiState.update { it.copy(missingConfig = true) }
                return@launch
            }
            val targets = plan.items.map { it.item }
            if (targets.isEmpty()) return@launch
            _uiState.update { it.copy(runningKind = plan.kind, missingConfig = false) }
            targets.forEach { setItemRunning(it) }
            for (planItem in plan.items) {
                val port = dataPorts[planItem.item] ?: continue
                val outcome =
                    repository.invoke(
                        kind = plan.kind,
                        config = config,
                        item = planItem.item,
                        port = port,
                        planItem = planItem,
                    )
                applyItemOutcome(planItem.item, outcome)
            }
            val lastFullSyncTimeMs = repository.recordFullSyncBatch()
            val itemStates = repository.loadItemStates(_uiState.value.itemStates)
            _uiState.update { state ->
                state.copy(
                    runningKind = null,
                    lastFullSyncTimeMs = lastFullSyncTimeMs,
                    itemStates = itemStates,
                )
            }
            refreshLocalCountsInternal(dataPorts)
        }
    }

    private fun setItemRunning(item: WebDavSyncItem) {
        _uiState.update { state ->
            state.copy(
                itemStates = state.itemStates + (item to runningItemState(item)),
            )
        }
    }

    private fun runningItemState(item: WebDavSyncItem): WebDavSyncItemUiState =
        (_uiState.value.itemStates[item] ?: WebDavSyncItemUiState()).copy(running = true, lastOutcome = null)

    private suspend fun applyItemOutcome(item: WebDavSyncItem, outcome: WebDavItemOutcome) {
        val lastSyncTimeMs = repository.loadLastSyncTime(item)
        _uiState.update { state ->
            state.copy(
                itemStates =
                    state.itemStates + (
                        item to (state.itemStates[item] ?: WebDavSyncItemUiState()).copy(
                            running = false,
                            lastOutcome = outcome,
                            lastSyncTimeMs = lastSyncTimeMs,
                        )
                        ),
            )
        }
    }

    private fun itemStatesWithPlan(
        base: Map<WebDavSyncItem, WebDavSyncItemUiState>,
        plan: WebDavSyncPlan,
    ): Map<WebDavSyncItem, WebDavSyncItemUiState> {
        val updated = base.toMutableMap()
        for (planItem in plan.items) {
            val current = updated[planItem.item] ?: WebDavSyncItemUiState()
            val remoteError =
                (planItem.remoteState as? WebDavSyncPlanRemoteState.Error)
                    ?.let { WebDavItemOutcome(status = it.status, detail = it.detail) }
            updated[planItem.item] =
                current.copy(
                    localCount = planItem.localCount,
                    remoteProbeError = remoteError,
                )
        }
        return updated
    }

    // ── Init / helpers ─────────────────────────────────────────────────

    private fun validate(): Boolean {
        val s = _uiState.value
        if (!s.provider.serverUrlLocked) {
            val url = s.serverUrl.trim()
            if (url.isBlank()) {
                _uiState.value = s.copy(urlError = WebDavUrlError.Empty)
                return false
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                _uiState.value = s.copy(urlError = WebDavUrlError.Scheme)
                return false
            }
        }
        if (s.username.isBlank() || s.appPassword.isBlank()) return false
        return true
    }

    /**
     * Cheap-to-call refresh of [WebDavSyncItemUiState.localCount] for every item. Domain-store
     * reads happen on-demand in [WebDavSyncRepository] so the page never performs cross-domain
     * JSON/store reads on the main thread. The page invokes this on first composition + after every
     * mutation that could change a count (sync / upload / download / clearConfig).
     */
    fun refreshLocalCounts(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) {
        viewModelScope.launch {
            refreshLocalCountsInternal(dataPorts)
        }
    }

    private suspend fun refreshLocalCountsInternal(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) {
        val counts = repository.loadLocalCounts(dataPorts)
        _uiState.update { state ->
            val updated = state.itemStates.toMutableMap()
            for ((item, count) in counts) {
                val current = updated[item] ?: WebDavSyncItemUiState()
                updated[item] = current.copy(localCount = count)
            }
            state.copy(itemStates = updated.toMap())
        }
    }

    private fun hasEnabledItems(): Boolean = _uiState.value.itemStates.values.any { it.enabled }
}

internal enum class WebDavBatchKind { Sync, Upload, Download }

internal enum class WebDavUrlError { Empty, Scheme }

internal data class WebDavSyncUiState(
    val provider: WebDavProvider = WebDavProvider.Jianguoyun,
    val serverUrl: String = "",
    val username: String = "",
    val appPassword: String = "",
    val remoteDir: String = WebDavSyncStore.DEFAULT_REMOTE_DIR,
    val passwordVisible: Boolean = false,
    val isConfigured: Boolean = false,
    val autoSyncEnabled: Boolean = false,
    val testing: Boolean = false,
    val refreshingRemote: Boolean = false,
    val connectionResult: WebDavConnectionOutcome? = null,
    val urlError: WebDavUrlError? = null,
    val missingConfig: Boolean = false,
    val planningKind: WebDavBatchKind? = null,
    val runningKind: WebDavBatchKind? = null,
    val pendingPlan: WebDavSyncPlan? = null,
    val lastFullSyncTimeMs: Long = 0L,
    val lastRemoteProbeTimeMs: Long = 0L,
    val itemStates: Map<WebDavSyncItem, WebDavSyncItemUiState> = emptyMap(),
) {
    val busy: Boolean get() = runningKind != null || planningKind != null
    val interactionLocked: Boolean
        get() = busy || testing || refreshingRemote
    val canConnect: Boolean
        get() = username.isNotBlank() && appPassword.isNotBlank() &&
            (provider.serverUrlLocked || serverUrl.isNotBlank())
}

internal data class WebDavSyncItemUiState(
    val enabled: Boolean = true,
    val running: Boolean = false,
    val lastSyncTimeMs: Long = 0L,
    val lastOutcome: WebDavItemOutcome? = null,
    val remoteSummary: WebDavRemoteSummary? = null,
    val remoteProbeError: WebDavItemOutcome? = null,
    /** Local item count; -1 means "not yet measured". Populated by [WebDavSyncViewModel.refreshLocalCounts]. */
    val localCount: Int = -1,
)
