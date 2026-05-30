package os.kei.ui.page.main.sync

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.feature.webdav.model.WebDavConfig

/**
 * Drives the WebDAV sync page. Holds editable connection fields, the configured/auto-sync flags,
 * and per-item sync state. All status is exposed as enums ([WebDavItemStatus] /
 * [WebDavConnectionStatus]) so the page owns the localized strings.
 */
@Stable
internal class WebDavSyncViewModel : ViewModel() {
    private val engine = WebDavSyncEngine()

    var uiState by mutableStateOf(buildInitialState())
        private set

    private var syncJob: Job? = null

    // ── Field edits ────────────────────────────────────────────────────

    fun updateServerUrl(value: String) {
        uiState = uiState.copy(serverUrl = value, urlError = null, connectionResult = null)
    }

    fun updateUsername(value: String) {
        uiState = uiState.copy(username = value, connectionResult = null)
    }

    fun updateAppPassword(value: String) {
        uiState = uiState.copy(appPassword = value, connectionResult = null)
    }

    fun updateRemoteDir(value: String) {
        uiState = uiState.copy(remoteDir = value, connectionResult = null)
    }

    fun togglePasswordVisible() {
        uiState = uiState.copy(passwordVisible = !uiState.passwordVisible)
    }

    fun selectProvider(provider: WebDavProvider) {
        uiState = uiState.copy(
            provider = provider,
            serverUrl = provider.presetServerUrl ?: uiState.serverUrl,
            remoteDir = uiState.remoteDir.ifBlank { provider.defaultRemoteDir },
            urlError = null,
            connectionResult = null,
        )
    }

    // ── Save / clear ───────────────────────────────────────────────────

    fun saveConfig() {
        val s = uiState
        if (!validate()) return
        val config = s.provider.buildConfig(s.serverUrl, s.username, s.appPassword, s.remoteDir)
        WebDavSyncStore.saveConfig(config, s.provider)
        engine.invalidate()
        uiState = uiState.copy(
            isConfigured = true,
            serverUrl = config.serverUrl,
            remoteDir = config.remoteDir,
            urlError = null,
        )
    }

    fun clearConfig() {
        WebDavSyncStore.clearConfig()
        engine.invalidate()
        uiState = buildInitialState()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        WebDavSyncStore.setAutoSyncEnabled(enabled)
        uiState = uiState.copy(autoSyncEnabled = enabled)
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
        if (uiState.busy || uiState.refreshingRemote) return
        val config = WebDavSyncStore.loadConfig() ?: run {
            uiState = uiState.copy(missingConfig = true)
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(refreshingRemote = true, missingConfig = false)
            val targets = WebDavSyncItem.entries.filter { WebDavSyncStore.isItemEnabled(it) }
            for (item in targets) {
                val port = dataPorts[item] ?: continue
                engine.probeRemote(config, item, port)
            }
            // Per-item probe timestamps are written by the engine; record the wall-clock for the
            // batch so the UI can render "remote refreshed: <relative time>" without scanning all
            // five entries.
            WebDavSyncStore.setLastRemoteProbeTime(System.currentTimeMillis())
            uiState = uiState.copy(
                refreshingRemote = false,
                lastRemoteProbeTimeMs = WebDavSyncStore.getLastRemoteProbeTime(),
                itemStates = buildItemStates(uiState.itemStates),
            )
        }
    }

    // ── Connection test ────────────────────────────────────────────────

    fun testConnection() {
        val s = uiState
        if (!validate()) return
        val config = s.provider.buildConfig(s.serverUrl, s.username, s.appPassword, s.remoteDir)
        viewModelScope.launch {
            uiState = uiState.copy(testing = true, connectionResult = null)
            val outcome = engine.testConnection(config)
            uiState = uiState.copy(testing = false, connectionResult = outcome)
        }
    }

    // ── Item enable toggle ─────────────────────────────────────────────

    fun toggleItem(item: WebDavSyncItem) {
        val enabled = !WebDavSyncStore.isItemEnabled(item)
        WebDavSyncStore.setItemEnabled(item, enabled)
        uiState = uiState.copy(itemStates = buildItemStates(uiState.itemStates))
    }

    // ── Sync actions ───────────────────────────────────────────────────

    fun syncAll(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) =
        runBatch(dataPorts, WebDavBatchKind.Sync)

    fun uploadAll(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) =
        runBatch(dataPorts, WebDavBatchKind.Upload)

    fun downloadAll(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) =
        runBatch(dataPorts, WebDavBatchKind.Download)

    // ── Confirmation flow for destructive batch actions ────────────────

    /**
     * Open the confirmation dialog for a batch action. Sync is safe (merge-only) and runs
     * immediately; Upload All overwrites the remote and Download All merges into local — both
     * are gated through [WebDavSyncUiState.pendingBatchConfirmation] so a fresh device can't
     * blindly wipe data on either side.
     */
    fun requestBatchConfirmation(kind: WebDavBatchKind) {
        if (uiState.busy || uiState.pendingBatchConfirmation != null) return
        if (kind == WebDavBatchKind.Sync) {
            // Sync is union-merge end-to-end, no confirmation needed.
            return
        }
        uiState = uiState.copy(pendingBatchConfirmation = kind)
    }

    fun dismissBatchConfirmation() {
        uiState = uiState.copy(pendingBatchConfirmation = null)
    }

    fun confirmBatchAction(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) {
        val kind = uiState.pendingBatchConfirmation ?: return
        uiState = uiState.copy(pendingBatchConfirmation = null)
        runBatch(dataPorts, kind)
    }

    /**
     * Returns true when an Upload All would visibly shrink the remote — every enabled item that
     * has a known remote count exceeds the local count. Used by the page to switch the dialog to
     * a stronger warning so users on a fresh device don't accidentally wipe remote data.
     */
    fun uploadShrinksRemote(): Boolean {
        val targets = WebDavSyncItem.entries.filter { WebDavSyncStore.isItemEnabled(it) }
        if (targets.isEmpty()) return false
        var anyKnownDelta = false
        for (item in targets) {
            val state = uiState.itemStates[item] ?: continue
            val local = state.localCount
            val remote = state.remoteSummary
            if (local < 0 || remote == null || remote.empty || remote.itemCount < 0) continue
            anyKnownDelta = true
            if (local >= remote.itemCount) return false
        }
        return anyKnownDelta
    }

    /** Sync / upload / download a single item on demand. */
    fun runItem(
        item: WebDavSyncItem,
        kind: WebDavBatchKind,
        dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    ) {
        if (uiState.busy) return
        val config = WebDavSyncStore.loadConfig() ?: return
        val port = dataPorts[item] ?: return
        syncJob = viewModelScope.launch {
            uiState = uiState.copy(runningKind = kind)
            setItemRunning(item)
            val outcome = invoke(kind, config, item, port)
            applyItemOutcome(item, outcome)
            uiState = uiState.copy(runningKind = null, itemStates = buildItemStates(uiState.itemStates))
            // Local count may have changed after merge / download; refresh it so the UI shows
            // the post-action delta against the remote summary.
            refreshLocalCounts(dataPorts)
        }
    }

    private fun runBatch(
        dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
        kind: WebDavBatchKind,
    ) {
        if (uiState.busy) return
        val config = WebDavSyncStore.loadConfig() ?: run {
            uiState = uiState.copy(missingConfig = true)
            return
        }
        syncJob = viewModelScope.launch {
            uiState = uiState.copy(runningKind = kind, missingConfig = false)
            val targets = WebDavSyncItem.entries.filter { WebDavSyncStore.isItemEnabled(it) }
            targets.forEach { setItemRunning(it) }
            for (item in targets) {
                val port = dataPorts[item] ?: continue
                val outcome = invoke(kind, config, item, port)
                applyItemOutcome(item, outcome)
            }
            WebDavSyncStore.setLastFullSyncTime(System.currentTimeMillis())
            uiState = uiState.copy(
                runningKind = null,
                lastFullSyncTimeMs = WebDavSyncStore.getLastFullSyncTime(),
                itemStates = buildItemStates(),
            )
            refreshLocalCounts(dataPorts)
        }
    }

    private suspend fun invoke(
        kind: WebDavBatchKind,
        config: WebDavConfig,
        item: WebDavSyncItem,
        port: WebDavSyncDataPort,
    ): WebDavItemOutcome = when (kind) {
        WebDavBatchKind.Sync -> engine.sync(config, item, port)
        WebDavBatchKind.Upload -> engine.upload(config, item, port)
        WebDavBatchKind.Download -> engine.download(config, item, port)
    }

    private fun setItemRunning(item: WebDavSyncItem) {
        uiState = uiState.copy(
            itemStates = uiState.itemStates + (item to runningItemState(item)),
        )
    }

    private fun runningItemState(item: WebDavSyncItem): WebDavSyncItemUiState =
        (uiState.itemStates[item] ?: defaultItemState(item)).copy(running = true, lastOutcome = null)

    private fun applyItemOutcome(item: WebDavSyncItem, outcome: WebDavItemOutcome) {
        uiState = uiState.copy(
            itemStates = uiState.itemStates + (
                item to (uiState.itemStates[item] ?: defaultItemState(item)).copy(
                    running = false,
                    lastOutcome = outcome,
                    lastSyncTimeMs = WebDavSyncStore.getLastSyncTime(item),
                )
                ),
        )
    }

    // ── Init / helpers ─────────────────────────────────────────────────

    private fun validate(): Boolean {
        val s = uiState
        if (!s.provider.serverUrlLocked) {
            val url = s.serverUrl.trim()
            if (url.isBlank()) {
                uiState = s.copy(urlError = WebDavUrlError.Empty)
                return false
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                uiState = s.copy(urlError = WebDavUrlError.Scheme)
                return false
            }
        }
        if (s.username.isBlank() || s.appPassword.isBlank()) return false
        return true
    }

    private fun buildInitialState(): WebDavSyncUiState {
        val config = WebDavSyncStore.loadConfig()
        val provider = WebDavSyncStore.loadProvider()
        return WebDavSyncUiState(
            provider = provider,
            serverUrl = config?.serverUrl ?: provider.presetServerUrl.orEmpty(),
            username = config?.username.orEmpty(),
            appPassword = config?.appPassword.orEmpty(),
            remoteDir = config?.remoteDir ?: provider.defaultRemoteDir,
            isConfigured = config != null,
            autoSyncEnabled = WebDavSyncStore.isAutoSyncEnabled(),
            lastFullSyncTimeMs = WebDavSyncStore.getLastFullSyncTime(),
            lastRemoteProbeTimeMs = WebDavSyncStore.getLastRemoteProbeTime(),
            itemStates = buildItemStates(),
        )
    }

    /**
     * Rebuild item states from the store, preserving the transient [WebDavSyncItemUiState.lastOutcome]
     * from the current UI state (the store doesn't persist the last action result). Safe to call from
     * [buildInitialState] before [uiState] is assigned because it reads [previous] from the parameter.
     *
     * Note: [WebDavSyncItemUiState.localCount] is intentionally left at -1 here — populating it
     * requires a domain-store read that may be expensive and isn't needed by the store layer.
     * The page populates it from the data ports passed at the call site.
     */
    private fun buildItemStates(
        previous: Map<WebDavSyncItem, WebDavSyncItemUiState> = emptyMap(),
    ): Map<WebDavSyncItem, WebDavSyncItemUiState> =
        WebDavSyncItem.entries.associateWith { item ->
            WebDavSyncItemUiState(
                enabled = WebDavSyncStore.isItemEnabled(item),
                running = false,
                lastSyncTimeMs = WebDavSyncStore.getLastSyncTime(item),
                lastOutcome = previous[item]?.lastOutcome,
                remoteSummary = WebDavSyncStore.loadRemoteSummary(item),
                localCount = previous[item]?.localCount ?: -1,
            )
        }

    /**
     * Cheap-to-call refresh of [WebDavSyncItemUiState.localCount] for every item. Domain-store
     * reads happen on-demand here rather than in [buildItemStates] so the store layer doesn't
     * need to know about data ports. The page invokes this on first composition + after every
     * mutation that could change a count (sync / upload / download / clearConfig).
     */
    fun refreshLocalCounts(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>) {
        val updated = uiState.itemStates.toMutableMap()
        for ((item, port) in dataPorts) {
            val current = updated[item] ?: defaultItemState(item)
            val count = runCatching { port.localCount() }.getOrDefault(-1)
            updated[item] = current.copy(localCount = count)
        }
        uiState = uiState.copy(itemStates = updated)
    }

    private fun defaultItemState(item: WebDavSyncItem) = WebDavSyncItemUiState(
        enabled = WebDavSyncStore.isItemEnabled(item),
        lastSyncTimeMs = WebDavSyncStore.getLastSyncTime(item),
        remoteSummary = WebDavSyncStore.loadRemoteSummary(item),
    )
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
    val runningKind: WebDavBatchKind? = null,
    val pendingBatchConfirmation: WebDavBatchKind? = null,
    val lastFullSyncTimeMs: Long = 0L,
    val lastRemoteProbeTimeMs: Long = 0L,
    val itemStates: Map<WebDavSyncItem, WebDavSyncItemUiState> = emptyMap(),
) {
    val busy: Boolean get() = runningKind != null
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
    /** Local item count; -1 means "not yet measured". Populated by [WebDavSyncViewModel.refreshLocalCounts]. */
    val localCount: Int = -1,
)
