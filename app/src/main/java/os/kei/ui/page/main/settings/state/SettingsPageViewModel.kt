package os.kei.ui.page.main.settings.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.export.ExportJobResult
import os.kei.core.log.AppLogLevel
import os.kei.core.log.AppLogStore
import os.kei.ui.page.main.settings.cache.CacheEntrySummary

@Immutable
internal data class SettingsCacheUiState(
    val cacheEntries: List<CacheEntrySummary>? = emptyList(),
    val cacheEntriesLoading: Boolean = false,
    val clearingCacheId: String? = null,
    val clearingAllCaches: Boolean = false
)

internal data class SettingsLogUiState(
    val logStats: AppLogStore.Stats = AppLogStore.Stats.Empty,
    val exportingLogZip: Boolean = false,
    val clearingLogs: Boolean = false,
    val pendingExportFileName: String? = null
)

@Immutable
internal data class SettingsDiagnosticsUiState(
    val cacheState: SettingsCacheUiState = SettingsCacheUiState(),
    val logState: SettingsLogUiState = SettingsLogUiState(),
)

internal class SettingsPageViewModel : ViewModel() {
    private val repository = SettingsPageRepository()

    private val _cacheState = MutableStateFlow(SettingsCacheUiState())
    val cacheState: StateFlow<SettingsCacheUiState> = _cacheState.asStateFlow()

    private val _logState = MutableStateFlow(SettingsLogUiState())
    val logState: StateFlow<SettingsLogUiState> = _logState.asStateFlow()

    val diagnosticsUiState: StateFlow<SettingsDiagnosticsUiState> =
        combine(cacheState, logState) { cache, log ->
            SettingsDiagnosticsUiState(
                cacheState = cache,
                logState = log,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SettingsDiagnosticsUiState(),
        )

    private val diagnosticsCoordinator = SettingsDiagnosticsCoordinator(
        repository = repository,
        scope = viewModelScope,
        cacheState = _cacheState,
        logState = _logState,
    )

    fun bindDiagnostics(
        context: Context,
        active: Boolean,
        cacheDiagnosticsEnabled: Boolean,
        logLevel: AppLogLevel,
    ) {
        diagnosticsCoordinator.bind(
            context = context,
            active = active,
            cacheDiagnosticsEnabled = cacheDiagnosticsEnabled,
            logLevel = logLevel,
        )
    }

    fun reloadCacheEntries(context: Context) {
        diagnosticsCoordinator.reloadCacheEntries(context)
    }

    suspend fun clearAllCaches(context: Context): Result<Unit> {
        return diagnosticsCoordinator.clearAllCaches(context)
    }

    suspend fun clearCache(
        context: Context,
        cacheId: String
    ): Result<Unit> {
        return diagnosticsCoordinator.clearCache(context, cacheId)
    }

    fun reloadLogStats(context: Context) {
        diagnosticsCoordinator.reloadLogStats(context)
    }

    fun beginLogExport() {
        if (_logState.value.exportingLogZip || _logState.value.clearingLogs) return
        viewModelScope.launch {
            val fileName = repository.buildLogExportFileName()
            _logState.update { state ->
                state.copy(
                    exportingLogZip = true,
                    pendingExportFileName = fileName
                )
            }
        }
    }

    fun consumePendingExportFileName(): String? {
        val fileName = _logState.value.pendingExportFileName
        _logState.update { state -> state.copy(pendingExportFileName = null) }
        return fileName
    }

    fun finishLogExport() {
        _logState.update { state ->
            state.copy(
                exportingLogZip = false,
                pendingExportFileName = null
            )
        }
    }

    suspend fun exportLogZip(
        context: Context,
        uri: Uri
    ): ExportJobResult {
        return repository.exportLogZip(
            context = context.applicationContext,
            uri = uri
        )
    }

    suspend fun clearLogs(context: Context): Result<Unit> {
        return diagnosticsCoordinator.clearLogs(context)
    }
}
