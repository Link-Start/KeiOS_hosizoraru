package os.kei.ui.page.main.sync

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import os.kei.core.log.AppLogger
import os.kei.feature.webdav.jianguoyun.JianguoyunPreset
import os.kei.feature.webdav.model.WebDavConfig

@Stable
internal class WebDavSyncViewModel : ViewModel() {
    private val repository = WebDavSyncRepository()

    var uiState by mutableStateOf(buildInitialUiState())
        private set

    // ── Config actions ─────────────────────────────────────────────

    fun updateServerUrl(value: String) {
        uiState = uiState.copy(serverUrl = value, urlError = null)
    }

    fun updateUsername(value: String) {
        uiState = uiState.copy(username = value)
    }

    fun updateAppPassword(value: String) {
        uiState = uiState.copy(appPassword = value)
    }

    fun updateRemoteDir(value: String) {
        uiState = uiState.copy(remoteDir = value)
    }

    fun selectProvider(provider: WebDavProvider) {
        uiState = uiState.copy(
            selectedProvider = provider,
            serverUrl = provider.presetServerUrl ?: uiState.serverUrl,
            remoteDir = provider.defaultRemoteDir,
            urlError = null,
        )
    }

    fun saveConfig() {
        val urlError = validateUrl(uiState.serverUrl)
        if (urlError != null) {
            uiState = uiState.copy(urlError = urlError)
            return
        }
        val s = uiState
        WebDavSyncStore.saveConfig(s.serverUrl, s.username, s.appPassword, s.remoteDir)
        repository.configure(
            WebDavConfig(
                serverUrl = s.serverUrl,
                username = s.username,
                appPassword = s.appPassword,
                remoteDir = s.remoteDir,
            ),
        )
        uiState = uiState.copy(isConfigured = true, urlError = null)
    }

    fun clearConfig() {
        WebDavSyncStore.clearConfig()
        uiState = buildInitialUiState()
    }

    // ── Connection test ────────────────────────────────────────────

    fun testConnection() {
        val urlError = validateUrl(uiState.serverUrl)
        if (urlError != null) {
            uiState = uiState.copy(urlError = urlError)
            return
        }
        val s = uiState
        repository.configure(
            WebDavConfig(
                serverUrl = s.serverUrl,
                username = s.username,
                appPassword = s.appPassword,
                remoteDir = s.remoteDir,
            ),
        )
        viewModelScope.launch {
            uiState = uiState.copy(testing = true, testResult = null)
            val result = repository.testConnection()
            uiState = uiState.copy(
                testing = false,
                testResult = when {
                    result.success && result.dirCreated -> WebDavTestUiResult.SuccessDirCreated
                    result.success -> WebDavTestUiResult.Success
                    else -> WebDavTestUiResult.Failure(result.message)
                },
            )
        }
    }

    // ── Sync actions ───────────────────────────────────────────────

    fun toggleItem(item: WebDavSyncItem) {
        val enabled = !WebDavSyncStore.isItemEnabled(item)
        WebDavSyncStore.setItemEnabled(item, enabled)
        uiState = uiState.copy(itemStates = buildItemStates())
    }

    /**
     * Upload all enabled items. [dataPorts] provides export/import lambdas per item.
     */
    fun uploadAll(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort> = emptyMap()) {
        viewModelScope.launch {
            uiState = uiState.copy(syncing = true, syncProgress = "Syncing…", lastSyncError = null)
            val config = WebDavSyncStore.loadConfig() ?: run {
                uiState = uiState.copy(syncing = false, lastSyncError = "Not configured")
                return@launch
            }
            repository.configure(config.toWebDavConfig())
            var errors = 0
            for (item in WebDavSyncItem.entries) {
                if (!WebDavSyncStore.isItemEnabled(item)) continue
                val port = dataPorts[item] ?: continue
                uiState = uiState.copy(syncProgress = "Uploading ${item.name}…")
                val storedEtag = WebDavSyncStore.getItemEtag(item)
                when (val result = repository.upload(item, port.exportJson, storedEtag)) {
                    is WebDavSyncItemResult.Synced -> {
                        WebDavSyncStore.setLastSyncTime(item, System.currentTimeMillis())
                        if (result.etag != null) {
                            WebDavSyncStore.setItemEtag(item, result.etag)
                        }
                    }
                    is WebDavSyncItemResult.Conflict -> {
                        // Remote was modified — download and merge first, then re-upload
                        AppLogger.i(TAG, "${item.name}: conflict detected, downloading to merge...")
                        when (val dlResult = repository.download(item, port.importJson)) {
                            is WebDavSyncItemResult.Synced -> {
                                // Merge done, re-upload
                                when (val reUpload = repository.upload(item, port.exportJson, dlResult.etag)) {
                                    is WebDavSyncItemResult.Synced -> {
                                        WebDavSyncStore.setLastSyncTime(item, System.currentTimeMillis())
                                        if (reUpload.etag != null) {
                                            WebDavSyncStore.setItemEtag(item, reUpload.etag)
                                        }
                                    }
                                    else -> {
                                        errors++
                                        AppLogger.w(TAG, "${item.name}: re-upload after merge failed")
                                    }
                                }
                            }
                            else -> {
                                errors++
                                AppLogger.w(TAG, "${item.name}: download for merge failed")
                            }
                        }
                    }
                    is WebDavSyncItemResult.Error -> {
                        errors++
                        AppLogger.w(TAG, "${item.name}: ${result.message}")
                    }
                    is WebDavSyncItemResult.RemoteEmpty -> { /* ok */ }
                }
            }
            WebDavSyncStore.setLastFullSyncTime(System.currentTimeMillis())
            uiState = uiState.copy(
                syncing = false,
                syncProgress = null,
                lastSyncError = if (errors > 0) "$errors item(s) failed" else null,
                itemStates = buildItemStates(),
            )
        }
    }

    /**
     * Download all enabled items. [dataPorts] provides export/import lambdas per item.
     */
    fun downloadAll(dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort> = emptyMap()) {
        viewModelScope.launch {
            uiState = uiState.copy(syncing = true, syncProgress = "Downloading…", lastSyncError = null)
            val config = WebDavSyncStore.loadConfig() ?: run {
                uiState = uiState.copy(syncing = false, lastSyncError = "Not configured")
                return@launch
            }
            repository.configure(config.toWebDavConfig())
            var errors = 0
            for (item in WebDavSyncItem.entries) {
                if (!WebDavSyncStore.isItemEnabled(item)) continue
                val port = dataPorts[item] ?: continue
                uiState = uiState.copy(syncProgress = "Downloading ${item.name}…")
                when (val result = repository.download(item, port.importJson)) {
                    is WebDavSyncItemResult.Synced -> {
                        WebDavSyncStore.setLastSyncTime(item, System.currentTimeMillis())
                        if (result.etag != null) {
                            WebDavSyncStore.setItemEtag(item, result.etag)
                        }
                    }
                    is WebDavSyncItemResult.RemoteEmpty -> { /* no remote file, skip */ }
                    is WebDavSyncItemResult.Conflict -> { /* not expected on download */ }
                    is WebDavSyncItemResult.Error -> {
                        errors++
                        AppLogger.w(TAG, "${item.name}: ${result.message}")
                    }
                }
            }
            WebDavSyncStore.setLastFullSyncTime(System.currentTimeMillis())
            uiState = uiState.copy(
                syncing = false,
                syncProgress = null,
                lastSyncError = if (errors > 0) "$errors item(s) failed" else null,
                itemStates = buildItemStates(),
            )
        }
    }

    // ── Init ───────────────────────────────────────────────────────

    init {
        val saved = WebDavSyncStore.loadConfig()
        if (saved != null) {
            repository.configure(saved.toWebDavConfig())
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun validateUrl(url: String): String? {
        if (url.isBlank()) return "Server URL is required"
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "URL must start with http:// or https://"
        }
        return null
    }

    private fun buildInitialUiState(): WebDavSyncUiState {
        val config = WebDavSyncStore.loadConfig()
        return WebDavSyncUiState(
            serverUrl = config?.serverUrl.orEmpty(),
            username = config?.username.orEmpty(),
            appPassword = config?.appPassword.orEmpty(),
            remoteDir = config?.remoteDir ?: "KeiOS/",
            isConfigured = config != null,
            itemStates = buildItemStates(),
        )
    }

    private fun buildItemStates(): Map<WebDavSyncItem, WebDavSyncItemUiState> {
        return WebDavSyncItem.entries.associateWith { item ->
            WebDavSyncItemUiState(
                enabled = WebDavSyncStore.isItemEnabled(item),
                lastSyncTimeMs = WebDavSyncStore.getLastSyncTime(item),
            )
        }
    }

    companion object {
        private const val TAG = "WebDavSyncVM"
    }
}

private fun WebDavSyncConfig.toWebDavConfig() = WebDavConfig(
    serverUrl = serverUrl,
    username = username,
    appPassword = appPassword,
    remoteDir = remoteDir,
)

internal data class WebDavSyncUiState(
    val serverUrl: String = "",
    val username: String = "",
    val appPassword: String = "",
    val remoteDir: String = "KeiOS/",
    val selectedProvider: WebDavProvider = WebDavProvider.Jianguoyun,
    val isConfigured: Boolean = false,
    val testing: Boolean = false,
    val testResult: WebDavTestUiResult? = null,
    val syncing: Boolean = false,
    val syncProgress: String? = null,
    val lastSyncError: String? = null,
    val urlError: String? = null,
    val itemStates: Map<WebDavSyncItem, WebDavSyncItemUiState> = emptyMap(),
)

internal data class WebDavSyncItemUiState(
    val enabled: Boolean,
    val lastSyncTimeMs: Long,
)

internal sealed interface WebDavTestUiResult {
    data object Success : WebDavTestUiResult
    data object SuccessDirCreated : WebDavTestUiResult
    data class Failure(val message: String) : WebDavTestUiResult
}

internal enum class WebDavProvider(
    val displayName: String,
    val presetServerUrl: String?,
    val defaultRemoteDir: String,
) {
    Jianguoyun(
        displayName = "坚果云",
        presetServerUrl = JianguoyunPreset.SERVER_URL,
        defaultRemoteDir = "KeiOS/",
    ),
    Custom(
        displayName = "自定义",
        presetServerUrl = null,
        defaultRemoteDir = "keios/",
    ),
}

/**
 * Export/import bridge for a single sync item.
 */
internal data class WebDavSyncDataPort(
    val exportJson: () -> String,
    val importJson: (String) -> Unit,
)
