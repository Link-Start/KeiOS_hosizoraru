package os.kei.ui.page.main.settings.state

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
import os.kei.ui.page.main.settings.page.SettingsSearchTarget
import os.kei.ui.page.main.settings.page.deriveSettingsSearchTargets
import os.kei.ui.page.main.settings.support.SettingsBatteryOptimizationController
import os.kei.ui.page.main.settings.support.SettingsBatteryOptimizationSnapshot
import os.kei.ui.page.main.settings.support.SettingsPermissionKeepAliveController
import os.kei.ui.page.main.settings.support.SettingsPermissionKeepAliveSnapshot

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
internal data class SettingsSupportUiState(
    val batteryOptimizationState: SettingsBatteryOptimizationSnapshot = SettingsBatteryOptimizationSnapshot(),
    val permissionKeepAliveState: SettingsPermissionKeepAliveSnapshot = SettingsPermissionKeepAliveSnapshot(),
)

@Immutable
internal data class SettingsSearchUiState(
    val matchingTargets: List<SettingsSearchTarget> = emptyList(),
)

@Immutable
internal data class SettingsPageSnapshotState(
    val diagnosticsUiState: SettingsDiagnosticsUiState = SettingsDiagnosticsUiState(),
    val supportUiState: SettingsSupportUiState = SettingsSupportUiState(),
    val chromeState: SettingsPageChromeState = SettingsPageChromeState(),
    val searchUiState: SettingsSearchUiState = SettingsSearchUiState(),
)

@Immutable
internal data class SettingsPageChromeState(
    val selectedCategoryIndex: Int = 0,
    val searchExpanded: Boolean = false,
    val searchQuery: String = "",
    val bottomBarVisible: Boolean = true,
    val sliderInteractionActive: Boolean = false,
    val showThemeModePopup: Boolean = false,
    val themePopupAnchorBounds: IntRect? = null,
    val showLauncherIconDesignPopup: Boolean = false,
    val launcherIconDesignPopupAnchorBounds: IntRect? = null,
    val showLogLevelPopup: Boolean = false,
    val logLevelPopupAnchorBounds: IntRect? = null,
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
    private var permissionKeepAliveRefreshJob: Job? = null

    private val _cacheState = MutableStateFlow(SettingsCacheUiState())
    val cacheState: StateFlow<SettingsCacheUiState> = _cacheState.asStateFlow()

    private val _logState = MutableStateFlow(SettingsLogUiState())
    val logState: StateFlow<SettingsLogUiState> = _logState.asStateFlow()
    private val _chromeState = MutableStateFlow(SettingsPageChromeState())
    val chromeState: StateFlow<SettingsPageChromeState> = _chromeState.asStateFlow()
    private val searchTargetsState = MutableStateFlow<List<SettingsSearchTarget>>(emptyList())
    private val _batteryOptimizationState = MutableStateFlow(SettingsBatteryOptimizationSnapshot())
    val batteryOptimizationState: StateFlow<SettingsBatteryOptimizationSnapshot> =
        _batteryOptimizationState.asStateFlow()
    private val _permissionKeepAliveState = MutableStateFlow(SettingsPermissionKeepAliveSnapshot())
    val permissionKeepAliveState: StateFlow<SettingsPermissionKeepAliveSnapshot> =
        _permissionKeepAliveState.asStateFlow()
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

    val supportUiState: StateFlow<SettingsSupportUiState> =
        combine(batteryOptimizationState, permissionKeepAliveState) { battery, permission ->
            SettingsSupportUiState(
                batteryOptimizationState = battery,
                permissionKeepAliveState = permission,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SettingsSupportUiState(),
        )

    val pageSnapshotState: StateFlow<SettingsPageSnapshotState> =
        combine(diagnosticsUiState, supportUiState, chromeState, searchTargetsState) { diagnostics, support, chrome, targets ->
            SettingsPageSnapshotState(
                diagnosticsUiState = diagnostics,
                supportUiState = support,
                chromeState = chrome,
                searchUiState =
                    SettingsSearchUiState(
                        matchingTargets =
                            deriveSettingsSearchTargets(
                                targets = targets,
                                query = chrome.trimmedSearchQuery,
                            ),
                    ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue =
                SettingsPageSnapshotState(
                    diagnosticsUiState = diagnosticsUiState.value,
                    supportUiState = supportUiState.value,
                    chromeState = chromeState.value,
                    searchUiState =
                        SettingsSearchUiState(
                            matchingTargets =
                                deriveSettingsSearchTargets(
                                    targets = searchTargetsState.value,
                                    query = chromeState.value.trimmedSearchQuery,
                                ),
                        ),
                ),
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

    fun updateSearchTargets(targets: List<SettingsSearchTarget>) {
        searchTargetsState.update { current ->
            if (current == targets) current else targets
        }
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

    fun updateShowThemeModePopup(show: Boolean) {
        _chromeState.update { state ->
            if (state.showThemeModePopup == show) state else state.copy(showThemeModePopup = show)
        }
    }

    fun updateThemePopupAnchorBounds(bounds: IntRect?) {
        _chromeState.update { state ->
            if (state.themePopupAnchorBounds == bounds) state else state.copy(themePopupAnchorBounds = bounds)
        }
    }

    fun updateShowLauncherIconDesignPopup(show: Boolean) {
        _chromeState.update { state ->
            if (state.showLauncherIconDesignPopup == show) state else state.copy(showLauncherIconDesignPopup = show)
        }
    }

    fun updateLauncherIconDesignPopupAnchorBounds(bounds: IntRect?) {
        _chromeState.update { state ->
            if (state.launcherIconDesignPopupAnchorBounds == bounds) {
                state
            } else {
                state.copy(launcherIconDesignPopupAnchorBounds = bounds)
            }
        }
    }

    fun updateShowLogLevelPopup(show: Boolean) {
        _chromeState.update { state ->
            if (state.showLogLevelPopup == show) state else state.copy(showLogLevelPopup = show)
        }
    }

    fun updateLogLevelPopupAnchorBounds(bounds: IntRect?) {
        _chromeState.update { state ->
            if (state.logLevelPopupAnchorBounds == bounds) state else state.copy(logLevelPopupAnchorBounds = bounds)
        }
    }

    fun requestShizukuRefresh() {
        _chromeState.update { state ->
            state.copy(shizukuRefreshToken = state.shizukuRefreshToken + 1)
        }
    }

    private var batteryOptimizationRefreshJob: Job? = null

    fun refreshBatteryOptimization(controller: SettingsBatteryOptimizationController) {
        batteryOptimizationRefreshJob?.cancel()
        batteryOptimizationRefreshJob =
            viewModelScope.launch {
                val snapshot = controller.loadSnapshot()
                _batteryOptimizationState.value = snapshot
            }
    }

    fun refreshPermissionKeepAlive(
        controller: SettingsPermissionKeepAliveController,
        notificationPermissionGranted: Boolean,
        shizukuStatus: String,
    ) {
        permissionKeepAliveRefreshJob?.cancel()
        permissionKeepAliveRefreshJob =
            viewModelScope.launch {
                refreshPermissionKeepAliveNow(
                    controller = controller,
                    notificationPermissionGranted = notificationPermissionGranted,
                    shizukuStatus = shizukuStatus,
                )
            }
    }

    suspend fun refreshPermissionKeepAliveNow(
        controller: SettingsPermissionKeepAliveController,
        notificationPermissionGranted: Boolean,
        shizukuStatus: String,
    ) {
        val snapshot =
            controller.loadSnapshot(
                notificationPermissionGranted = notificationPermissionGranted,
                shizukuStatus = shizukuStatus,
            )
        _permissionKeepAliveState.update { snapshot }
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
