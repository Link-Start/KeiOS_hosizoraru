package os.kei.ui.page.main.settings.state

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
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
import os.kei.core.log.AppLogLevel
import os.kei.core.log.AppLogStore
import os.kei.ui.page.main.settings.cache.CacheEntrySummary

@Immutable
internal data class SettingsCacheUiState(
    val cacheEntries: List<CacheEntrySummary>? = emptyList(),
    val cacheEntriesLoading: Boolean = false,
    val clearingCacheId: String? = null,
    val clearingAllCaches: Boolean = false,
)

internal data class SettingsLogUiState(
    val logStats: AppLogStore.Stats = AppLogStore.Stats.Empty,
    val exportingLogZip: Boolean = false,
    val clearingLogs: Boolean = false,
)

@Immutable
internal data class SettingsDiagnosticsUiState(
    val cacheState: SettingsCacheUiState = SettingsCacheUiState(),
    val logState: SettingsLogUiState = SettingsLogUiState(),
)

@Immutable
internal data class SettingsPageChromeState(
    val selectedCategoryIndex: Int = 0,
    val searchExpanded: Boolean = false,
    val searchQuery: String = "",
    val bottomBarVisible: Boolean = true,
    val sliderInteractionActive: Boolean = false,
    val shizukuRefreshToken: Int = 0,
) {
    val trimmedSearchQuery: String
        get() = searchQuery.trim()
}

internal sealed interface SettingsPageEvent {
    data class Toast(
        @param:StringRes val messageRes: Int,
    ) : SettingsPageEvent

    data class LiquidToast(
        @param:StringRes val messageRes: Int,
    ) : SettingsPageEvent

    data class FailureToast(
        @param:StringRes val messageRes: Int,
        val reason: String,
    ) : SettingsPageEvent

    data class LaunchLogExport(
        val fileName: String,
    ) : SettingsPageEvent
}

internal class SettingsPageViewModel : ViewModel() {
    private val repository = SettingsPageRepository()

    private val _cacheState = MutableStateFlow(SettingsCacheUiState())
    val cacheState: StateFlow<SettingsCacheUiState> = _cacheState.asStateFlow()

    private val _logState = MutableStateFlow(SettingsLogUiState())
    val logState: StateFlow<SettingsLogUiState> = _logState.asStateFlow()
    private val _chromeState = MutableStateFlow(SettingsPageChromeState())
    val chromeState: StateFlow<SettingsPageChromeState> = _chromeState.asStateFlow()
    private val _events = MutableSharedFlow<SettingsPageEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<SettingsPageEvent> = _events.asSharedFlow()

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

    private val diagnosticsCoordinator =
        SettingsDiagnosticsCoordinator(
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

    fun updateSelectedCategoryIndex(index: Int) {
        _chromeState.update { state ->
            state.copy(selectedCategoryIndex = index.coerceAtLeast(0))
        }
    }

    fun updateSearchExpanded(expanded: Boolean) {
        _chromeState.update { state -> state.copy(searchExpanded = expanded) }
    }

    fun updateSearchQuery(query: String) {
        _chromeState.update { state -> state.copy(searchQuery = query.take(96)) }
    }

    fun updateBottomBarVisible(visible: Boolean) {
        _chromeState.update { state ->
            if (state.bottomBarVisible == visible) state else state.copy(bottomBarVisible = visible)
        }
    }

    fun updateSliderInteractionActive(active: Boolean) {
        _chromeState.update { state ->
            if (state.sliderInteractionActive == active) state else state.copy(sliderInteractionActive = active)
        }
    }

    fun requestShizukuRefresh() {
        _chromeState.update { state ->
            state.copy(shizukuRefreshToken = state.shizukuRefreshToken + 1)
        }
    }

    fun reloadCacheEntries(context: Context) {
        diagnosticsCoordinator.reloadCacheEntries(context)
    }

    fun requestClearAllCaches(context: Context) {
        viewModelScope.launch {
            val result = diagnosticsCoordinator.clearAllCaches(context)
            if (result.isSuccess) {
                _events.emit(SettingsPageEvent.LiquidToast(R.string.settings_cache_toast_cleared_all))
            } else {
                _events.emit(SettingsPageEvent.FailureToast(R.string.settings_cache_toast_clear_all_failed, result.reasonName()))
            }
        }
    }

    fun requestClearCache(
        context: Context,
        cacheId: String,
    ) {
        viewModelScope.launch {
            diagnosticsCoordinator.clearCache(context, cacheId)
        }
    }

    fun reloadLogStats(context: Context) {
        diagnosticsCoordinator.reloadLogStats(context)
    }

    fun beginLogExport() {
        if (_logState.value.exportingLogZip || _logState.value.clearingLogs) return
        viewModelScope.launch {
            val fileName = repository.buildLogExportFileName()
            _logState.update { state ->
                state.copy(exportingLogZip = true)
            }
            _events.emit(SettingsPageEvent.LaunchLogExport(fileName))
        }
    }

    fun finishLogExport() {
        _logState.update { state ->
            state.copy(exportingLogZip = false)
        }
    }

    fun completeLogExport(
        context: Context,
        uri: Uri?,
    ) {
        if (uri == null) {
            finishLogExport()
            return
        }
        viewModelScope.launch {
            val result =
                repository.exportLogZip(
                    context = context.applicationContext,
                    uri = uri,
                )
            finishLogExport()
            if (result.isSuccess) {
                _events.emit(SettingsPageEvent.Toast(R.string.settings_log_toast_exported))
            } else {
                _events.emit(SettingsPageEvent.FailureToast(R.string.settings_log_toast_export_failed, result.errorPreview))
            }
            reloadLogStats(context)
        }
    }

    fun requestClearLogs(context: Context) {
        viewModelScope.launch {
            val result = diagnosticsCoordinator.clearLogs(context)
            if (result.isSuccess) {
                _events.emit(SettingsPageEvent.LiquidToast(R.string.settings_log_toast_cleared))
            } else {
                _events.emit(SettingsPageEvent.FailureToast(R.string.settings_log_toast_clear_failed, result.reasonName()))
            }
        }
    }
}

private fun Result<*>.reasonName(): String = exceptionOrNull()?.javaClass?.simpleName ?: "Unknown"
