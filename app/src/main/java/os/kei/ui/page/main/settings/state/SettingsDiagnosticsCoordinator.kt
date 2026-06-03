package os.kei.ui.page.main.settings.state

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.log.AppLogLevel
import kotlin.time.Duration.Companion.milliseconds

private const val SETTINGS_LOG_STATS_REFRESH_MS = 1_200L

internal class SettingsDiagnosticsCoordinator(
    private val repository: SettingsPageRepository,
    private val scope: CoroutineScope,
    private val cacheState: MutableStateFlow<SettingsCacheUiState>,
    private val logState: MutableStateFlow<SettingsLogUiState>,
) {
    private var cacheLoadJob: Job? = null
    private var logStatsJob: Job? = null
    private var boundCacheKey: SettingsCacheDiagnosticsBindKey? = null
    private var boundLogKey: SettingsLogStatsBindKey? = null

    fun bind(
        context: Context,
        active: Boolean,
        cacheDiagnosticsEnabled: Boolean,
        logLevel: AppLogLevel,
    ) {
        bindCacheDiagnostics(
            context = context,
            active = active,
            enabled = cacheDiagnosticsEnabled,
        )
        bindLogStats(
            context = context,
            active = active,
            logLevel = logLevel,
        )
    }

    fun reloadCacheEntries(context: Context) {
        val appContext = context.applicationContext
        cacheLoadJob?.cancel()
        cacheLoadJob = scope.launch {
            cacheState.update { state ->
                state.copy(cacheEntriesLoading = state.cacheEntries == null)
            }
            val entries = repository.listCacheEntries(appContext)
            cacheState.update { state ->
                state.copy(
                    cacheEntries = entries,
                    cacheEntriesLoading = false
                )
            }
        }
    }

    suspend fun clearAllCaches(context: Context): Result<Unit> {
        val appContext = context.applicationContext
        cacheState.update { state -> state.copy(clearingAllCaches = true) }
        val result = repository.clearAllCaches(appContext)
        cacheState.update { state -> state.copy(clearingAllCaches = false) }
        reloadCacheEntries(appContext)
        return result
    }

    suspend fun clearCache(
        context: Context,
        cacheId: String,
    ): Result<Unit> {
        val appContext = context.applicationContext
        cacheState.update { state -> state.copy(clearingCacheId = cacheId) }
        val result = repository.clearCache(appContext, cacheId)
        cacheState.update { state -> state.copy(clearingCacheId = null) }
        reloadCacheEntries(appContext)
        return result
    }

    fun reloadLogStats(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            logState.update { state ->
                state.copy(logStats = repository.loadLogStats(appContext))
            }
        }
    }

    suspend fun clearLogs(context: Context): Result<Unit> {
        val appContext = context.applicationContext
        if (logState.value.exportingLogZip || logState.value.clearingLogs) {
            return Result.success(Unit)
        }
        logState.update { state -> state.copy(clearingLogs = true) }
        val result = repository.clearLogs(appContext)
        logState.update { state -> state.copy(clearingLogs = false) }
        reloadLogStats(appContext)
        return result
    }

    private fun bindCacheDiagnostics(
        context: Context,
        active: Boolean,
        enabled: Boolean,
    ) {
        val appContext = context.applicationContext
        val key = SettingsCacheDiagnosticsBindKey(
            active = active,
            enabled = enabled,
        )
        if (!enabled) {
            boundCacheKey = key
            cacheLoadJob?.cancel()
            cacheState.value = SettingsCacheUiState()
            return
        }
        if (!active) {
            boundCacheKey = key
            cacheLoadJob?.cancel()
            return
        }
        val state = cacheState.value
        if (boundCacheKey == key && (cacheLoadJob?.isActive == true || state.cacheEntries != null)) {
            return
        }
        boundCacheKey = key
        reloadCacheEntries(appContext)
    }

    private fun bindLogStats(
        context: Context,
        active: Boolean,
        logLevel: AppLogLevel,
    ) {
        val key = SettingsLogStatsBindKey(
            active = active,
            logLevel = logLevel,
        )
        if (boundLogKey == key && logStatsJob?.isActive == true) return
        boundLogKey = key
        logStatsJob?.cancel()
        if (!active) return
        val appContext = context.applicationContext
        logStatsJob = scope.launch {
            do {
                logState.update { state ->
                    state.copy(logStats = repository.loadLogStats(appContext))
                }
                if (logLevel == AppLogLevel.Off) break
                delay(SETTINGS_LOG_STATS_REFRESH_MS.milliseconds)
            } while (true)
        }
    }
}

private data class SettingsLogStatsBindKey(
    val active: Boolean,
    val logLevel: AppLogLevel,
)

private data class SettingsCacheDiagnosticsBindKey(
    val active: Boolean,
    val enabled: Boolean,
)
