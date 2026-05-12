package os.kei.ui.page.main.settings.state

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.export.ExportJobResult
import os.kei.core.log.AppLogStore
import os.kei.core.prefs.CacheEntrySummary
import os.kei.core.telemetry.FirebaseTelemetry
import os.kei.core.telemetry.FirebaseTelemetryRecord
import kotlin.time.Duration.Companion.milliseconds

private const val SETTINGS_LOG_STATS_REFRESH_MS = 1_200L
private const val SETTINGS_TELEMETRY_REFRESH_MS = 1_600L

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

internal data class SettingsTelemetryUiState(
    val basicStatsEnabled: Boolean = false,
    val errorLogsEnabled: Boolean = false,
    val recentRecords: List<FirebaseTelemetryRecord> = emptyList(),
    val processingCrashReports: Boolean = false
)

internal class SettingsPageViewModel : ViewModel() {
    private val repository = SettingsPageRepository()
    private var cacheLoadJob: Job? = null
    private var logStatsJob: Job? = null
    private var telemetryJob: Job? = null
    private var boundLogDebugEnabled: Boolean? = null
    private var boundTelemetryKey: Pair<Boolean, Boolean>? = null

    private val _cacheState = MutableStateFlow(SettingsCacheUiState())
    val cacheState: StateFlow<SettingsCacheUiState> = _cacheState.asStateFlow()

    private val _logState = MutableStateFlow(SettingsLogUiState())
    val logState: StateFlow<SettingsLogUiState> = _logState.asStateFlow()

    private val _telemetryState = MutableStateFlow(SettingsTelemetryUiState())
    val telemetryState: StateFlow<SettingsTelemetryUiState> = _telemetryState.asStateFlow()

    fun bindCacheDiagnostics(
        context: Context,
        enabled: Boolean
    ) {
        val appContext = context.applicationContext
        if (!enabled) {
            cacheLoadJob?.cancel()
            _cacheState.value = SettingsCacheUiState()
            return
        }
        reloadCacheEntries(appContext)
    }

    fun reloadCacheEntries(context: Context) {
        val appContext = context.applicationContext
        cacheLoadJob?.cancel()
        cacheLoadJob = viewModelScope.launch {
            _cacheState.update { state ->
                state.copy(cacheEntriesLoading = state.cacheEntries == null)
            }
            val entries = repository.listCacheEntries(appContext)
            _cacheState.update { state ->
                state.copy(
                    cacheEntries = entries,
                    cacheEntriesLoading = false
                )
            }
        }
    }

    suspend fun clearAllCaches(context: Context): Result<Unit> {
        val appContext = context.applicationContext
        _cacheState.update { state -> state.copy(clearingAllCaches = true) }
        val result = repository.clearAllCaches(appContext)
        _cacheState.update { state -> state.copy(clearingAllCaches = false) }
        reloadCacheEntries(appContext)
        return result
    }

    suspend fun clearCache(
        context: Context,
        cacheId: String
    ): Result<Unit> {
        val appContext = context.applicationContext
        _cacheState.update { state -> state.copy(clearingCacheId = cacheId) }
        val result = repository.clearCache(appContext, cacheId)
        _cacheState.update { state -> state.copy(clearingCacheId = null) }
        reloadCacheEntries(appContext)
        return result
    }

    fun bindLogStats(
        context: Context,
        logDebugEnabled: Boolean
    ) {
        val appContext = context.applicationContext
        if (boundLogDebugEnabled == logDebugEnabled && logStatsJob?.isActive == true) return
        boundLogDebugEnabled = logDebugEnabled
        logStatsJob?.cancel()
        logStatsJob = viewModelScope.launch {
            do {
                _logState.update { state ->
                    state.copy(logStats = repository.loadLogStats(appContext))
                }
                if (!logDebugEnabled) break
                delay(SETTINGS_LOG_STATS_REFRESH_MS.milliseconds)
            } while (true)
        }
    }

    fun reloadLogStats(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _logState.update { state ->
                state.copy(logStats = repository.loadLogStats(appContext))
            }
        }
    }

    fun bindTelemetry(
        context: Context,
        basicStatsEnabled: Boolean,
        errorLogsEnabled: Boolean
    ) {
        val appContext = context.applicationContext
        val nextKey = basicStatsEnabled to errorLogsEnabled
        if (boundTelemetryKey == nextKey && telemetryJob?.isActive == true) return
        boundTelemetryKey = nextKey
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            do {
                val snapshot = FirebaseTelemetry.loadSnapshot()
                _telemetryState.update { state ->
                    state.copy(
                        basicStatsEnabled = snapshot.basicStatsEnabled,
                        errorLogsEnabled = snapshot.errorLogsEnabled,
                        recentRecords = snapshot.recentRecords
                    )
                }
                if (!basicStatsEnabled && !errorLogsEnabled) break
                delay(SETTINGS_TELEMETRY_REFRESH_MS.milliseconds)
            } while (true)
        }
        FirebaseTelemetry.applyCollectionPrefs(appContext)
    }

    fun reloadTelemetry() {
        val snapshot = FirebaseTelemetry.loadSnapshot()
        _telemetryState.update { state ->
            state.copy(
                basicStatsEnabled = snapshot.basicStatsEnabled,
                errorLogsEnabled = snapshot.errorLogsEnabled,
                recentRecords = snapshot.recentRecords
            )
        }
    }

    fun clearTelemetryRecords() {
        FirebaseTelemetry.clearRecentRecords()
        reloadTelemetry()
    }

    fun sendUnsentErrorReports() {
        if (_telemetryState.value.processingCrashReports) return
        viewModelScope.launch {
            _telemetryState.update { state -> state.copy(processingCrashReports = true) }
            FirebaseTelemetry.sendUnsentErrorReports()
            delay(300.milliseconds)
            _telemetryState.update { state -> state.copy(processingCrashReports = false) }
            reloadTelemetry()
        }
    }

    fun deleteUnsentErrorReports() {
        if (_telemetryState.value.processingCrashReports) return
        viewModelScope.launch {
            _telemetryState.update { state -> state.copy(processingCrashReports = true) }
            FirebaseTelemetry.deleteUnsentErrorReports()
            delay(300.milliseconds)
            _telemetryState.update { state -> state.copy(processingCrashReports = false) }
            reloadTelemetry()
        }
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
        val appContext = context.applicationContext
        if (_logState.value.exportingLogZip || _logState.value.clearingLogs) {
            return Result.success(Unit)
        }
        _logState.update { state -> state.copy(clearingLogs = true) }
        val result = repository.clearLogs(appContext)
        _logState.update { state -> state.copy(clearingLogs = false) }
        reloadLogStats(appContext)
        return result
    }
}
