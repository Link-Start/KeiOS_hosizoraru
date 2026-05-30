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
            itemStates = buildItemStates(),
        )
    }

    /**
     * Rebuild item states from the store, preserving the transient [WebDavSyncItemUiState.lastOutcome]
     * from the current UI state (the store doesn't persist the last action result). Safe to call from
     * [buildInitialState] before [uiState] is assigned because it reads [previous] from the parameter.
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
            )
        }

    private fun defaultItemState(item: WebDavSyncItem) = WebDavSyncItemUiState(
        enabled = WebDavSyncStore.isItemEnabled(item),
        lastSyncTimeMs = WebDavSyncStore.getLastSyncTime(item),
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
    val connectionResult: WebDavConnectionOutcome? = null,
    val urlError: WebDavUrlError? = null,
    val missingConfig: Boolean = false,
    val runningKind: WebDavBatchKind? = null,
    val lastFullSyncTimeMs: Long = 0L,
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
)
