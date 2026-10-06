package os.kei.ui.page.main.settings.state

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import os.kei.R
import os.kei.core.log.AppLogLevel
import os.kei.core.log.AppLogStore
import os.kei.ui.page.main.settings.cache.CacheEntrySummary
import os.kei.ui.page.main.settings.page.SettingsSearchTarget
import os.kei.ui.page.main.settings.section.SettingsAccessibilityGuardUiState
import os.kei.ui.page.main.settings.support.SettingsBatteryOptimizationController
import os.kei.ui.page.main.settings.support.SettingsBatteryOptimizationSnapshot
import os.kei.ui.page.main.settings.support.SettingsPermissionKeepAliveController
import os.kei.ui.page.main.settings.support.SettingsPermissionKeepAliveSnapshot
import os.kei.ui.page.main.sync.WebDavSyncStoreSignals
import os.kei.core.privilege.PrivilegeStatus

@Immutable
internal data class SettingsCacheUiState(
    val cacheEntries: List<CacheEntrySummary>? = null,
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
    val accessibilityGuardState: SettingsAccessibilityGuardUiState = SettingsAccessibilityGuardUiState(),
)

@Immutable
internal data class SettingsWebDavSyncUiState(
    val configured: Boolean = false,
    val username: String = "",
    val autoSyncEnabled: Boolean = false,
    val autoSyncIntervalHours: Int = 3,
    val lastFullSyncTimeMs: Long = 0L,
)

@Immutable
internal data class SettingsSearchUiState(
    val matchingTargets: List<SettingsSearchTarget> = emptyList(),
)

@Immutable
internal data class SettingsPageSnapshotState(
    val diagnosticsUiState: SettingsDiagnosticsUiState = SettingsDiagnosticsUiState(),
    val supportUiState: SettingsSupportUiState = SettingsSupportUiState(),
    val webDavSyncState: SettingsWebDavSyncUiState = SettingsWebDavSyncUiState(),
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
    val showLauncherIconDesignPopup: Boolean = false,
    val showLogLevelPopup: Boolean = false,
    val expandedCards: Map<SettingsCardExpansionId, Boolean> = emptyMap(),
    val privilegeRefreshToken: Int = 0,
) {
    val trimmedSearchQuery: String
        get() = searchQuery.trim()

    fun isCardExpanded(id: SettingsCardExpansionId): Boolean = expandedCards.isSettingsCardExpanded(id)
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

    data class LaunchAccessibilityGuardHistoryExport(
        val fileName: String,
    ) : SettingsPageEvent
}

internal sealed interface SettingsBackgroundEvent {
    data class LaunchNonHomeBackgroundCrop(
        val intent: Intent,
    ) : SettingsBackgroundEvent
}

internal class SettingsPageViewModel(
    private val repository: SettingsPageRepository = SettingsPageRepository(),
    initialExpandedCards: Map<SettingsCardExpansionId, Boolean> = SettingsCardExpansionStore.loadSnapshot(),
    initialWebDavSyncState: SettingsWebDavSyncUiState = repository.buildWebDavSyncState(),
) : ViewModel() {
    private var permissionKeepAliveRefreshJob: Job? = null
    private val permissionKeepAliveRefreshGeneration = AtomicLong(0L)
    private var accessibilityGuardRefreshJob: Job? = null
    private var batteryOptimizationRefreshJob: Job? = null
    private var searchTargetsJob: Job? = null

    private val _cacheState = MutableStateFlow(SettingsCacheUiState())
    val cacheState: StateFlow<SettingsCacheUiState> = _cacheState.asStateFlow()

    private val _logState = MutableStateFlow(SettingsLogUiState())
    val logState: StateFlow<SettingsLogUiState> = _logState.asStateFlow()
    private val _chromeState =
        MutableStateFlow(
            SettingsPageChromeState(
                expandedCards = initialExpandedCards,
            ),
        )
    val chromeState: StateFlow<SettingsPageChromeState> = _chromeState.asStateFlow()
    private val searchTargetsState = MutableStateFlow<List<SettingsSearchTarget>>(emptyList())
    private val _batteryOptimizationState = MutableStateFlow(SettingsBatteryOptimizationSnapshot())
    val batteryOptimizationState: StateFlow<SettingsBatteryOptimizationSnapshot> =
        _batteryOptimizationState.asStateFlow()
    private val _permissionKeepAliveState = MutableStateFlow(SettingsPermissionKeepAliveSnapshot())
    val permissionKeepAliveState: StateFlow<SettingsPermissionKeepAliveSnapshot> =
        _permissionKeepAliveState.asStateFlow()
    private val _accessibilityGuardState = MutableStateFlow(SettingsAccessibilityGuardUiState())
    val accessibilityGuardState: StateFlow<SettingsAccessibilityGuardUiState> =
        _accessibilityGuardState.asStateFlow()
    private val _webDavSyncState = MutableStateFlow(initialWebDavSyncState)
    val webDavSyncState: StateFlow<SettingsWebDavSyncUiState> = _webDavSyncState.asStateFlow()
    private val _events = MutableSharedFlow<SettingsPageEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<SettingsPageEvent> = _events.asSharedFlow()
    private val _backgroundEvents = MutableSharedFlow<SettingsBackgroundEvent>(replay = 0, extraBufferCapacity = 4)
    val backgroundEvents: SharedFlow<SettingsBackgroundEvent> = _backgroundEvents.asSharedFlow()

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
        combine(
            batteryOptimizationState,
            permissionKeepAliveState,
            accessibilityGuardState,
        ) { battery, permission, guard ->
            SettingsSupportUiState(
                batteryOptimizationState = battery,
                permissionKeepAliveState = permission,
                accessibilityGuardState = guard,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SettingsSupportUiState(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchUiState: StateFlow<SettingsSearchUiState> =
        combine(searchTargetsState, chromeState) { targets, chrome ->
            targets to chrome.trimmedSearchQuery
        }.distinctUntilChanged()
            .mapLatest { (targets, query) ->
                repository.deriveSearchState(
                    targets = targets,
                    query = query,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = SettingsSearchUiState(),
            )

    val pageSnapshotState: StateFlow<SettingsPageSnapshotState> =
        combine(
            diagnosticsUiState,
            supportUiState,
            webDavSyncState,
            chromeState,
            searchUiState,
        ) { diagnostics, support, webDavSync, chrome, search ->
            SettingsPageSnapshotState(
                diagnosticsUiState = diagnostics,
                supportUiState = support,
                webDavSyncState = webDavSync,
                chromeState = chrome,
                searchUiState = search,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue =
                SettingsPageSnapshotState(
                    diagnosticsUiState = diagnosticsUiState.value,
                    supportUiState = supportUiState.value,
                    webDavSyncState = webDavSyncState.value,
                    chromeState = chromeState.value,
                    searchUiState = searchUiState.value,
                ),
        )

    private val diagnosticsCoordinator =
        SettingsDiagnosticsCoordinator(
            repository = repository,
            scope = viewModelScope,
            cacheState = _cacheState,
            logState = _logState,
        )

    init {
        observeWebDavSyncState()
    }

    private fun observeWebDavSyncState() {
        viewModelScope.launch {
            WebDavSyncStoreSignals.version.collect {
                val snapshot = repository.loadWebDavSyncState()
                _webDavSyncState.update { current ->
                    if (current == snapshot) current else snapshot
                }
            }
        }
    }

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

    fun bindSearchTargets(context: Context) {
        searchTargetsJob?.cancel()
        searchTargetsJob =
            viewModelScope.launch {
                val targets = repository.buildSearchTargets(context.applicationContext)
                searchTargetsState.update { current ->
                    if (current == targets) current else targets
                }
            }
    }

    fun prepareNonHomeBackgroundCrop(
        context: Context,
        sourceUri: Uri,
    ) {
        viewModelScope.launch {
            val result =
                repository.buildNonHomeBackgroundCropIntent(
                    context = context.applicationContext,
                    sourceUri = sourceUri,
                )
            result
                .onSuccess { intent ->
                    _backgroundEvents.emit(SettingsBackgroundEvent.LaunchNonHomeBackgroundCrop(intent))
                }.onFailure { error ->
                    _events.emit(
                        SettingsPageEvent.FailureToast(
                            messageRes = R.string.settings_non_home_background_toast_crop_failed,
                            reason = error.javaClass.simpleName,
                        ),
                    )
                }
        }
    }

    fun trimManagedNonHomeBackgroundFiles(
        context: Context,
        keepUriText: String,
    ) {
        viewModelScope.launch {
            repository.trimManagedNonHomeBackgroundFiles(
                context = context.applicationContext,
                keepUriText = keepUriText,
            )
        }
    }

    fun notifyNonHomeBackgroundCropFailed(reason: String) {
        _events.tryEmit(
            SettingsPageEvent.FailureToast(
                messageRes = R.string.settings_non_home_background_toast_crop_failed,
                reason = reason,
            ),
        )
    }

    fun notifyNonHomeBackgroundSelected() {
        _events.tryEmit(SettingsPageEvent.Toast(R.string.settings_non_home_background_toast_selected))
    }

    fun notifyNonHomeBackgroundCleared() {
        _events.tryEmit(SettingsPageEvent.Toast(R.string.settings_non_home_background_toast_cleared))
    }

    override fun onCleared() {
        searchTargetsJob?.cancel()
        permissionKeepAliveRefreshJob?.cancel()
        accessibilityGuardRefreshJob?.cancel()
        batteryOptimizationRefreshJob?.cancel()
        super.onCleared()
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

    fun updateCardExpanded(
        id: SettingsCardExpansionId,
        expanded: Boolean,
    ) {
        SettingsCardExpansionStore.setExpanded(id, expanded)
        _chromeState.update { state ->
            val nextCards = state.expandedCards + (id to expanded)
            if (state.expandedCards == nextCards) state else state.copy(expandedCards = nextCards)
        }
    }

    fun updateShowThemeModePopup(show: Boolean) {
        _chromeState.update { state ->
            if (state.showThemeModePopup == show) state else state.copy(showThemeModePopup = show)
        }
    }

    fun updateShowLauncherIconDesignPopup(show: Boolean) {
        _chromeState.update { state ->
            if (state.showLauncherIconDesignPopup == show) state else state.copy(showLauncherIconDesignPopup = show)
        }
    }

    fun updateShowLogLevelPopup(show: Boolean) {
        _chromeState.update { state ->
            if (state.showLogLevelPopup == show) state else state.copy(showLogLevelPopup = show)
        }
    }

    fun requestPrivilegeRefresh() {
        _chromeState.update { state ->
            state.copy(privilegeRefreshToken = state.privilegeRefreshToken + 1)
        }
    }

    fun refreshBatteryOptimization(controller: SettingsBatteryOptimizationController) {
        _batteryOptimizationState.update { controller.loadSnapshot() }
    }

    fun refreshPermissionKeepAlive(
        controller: SettingsPermissionKeepAliveController,
        notificationPermissionGranted: Boolean,
        privilegeStatus: PrivilegeStatus,
    ) {
        permissionKeepAliveRefreshJob?.cancel()
        permissionKeepAliveRefreshJob =
            viewModelScope.launch {
                refreshPermissionKeepAliveNow(
                    controller = controller,
                    notificationPermissionGranted = notificationPermissionGranted,
                    privilegeStatus = privilegeStatus,
                )
            }
    }

    suspend fun refreshPermissionKeepAliveNow(
        controller: SettingsPermissionKeepAliveController,
        notificationPermissionGranted: Boolean,
        privilegeStatus: PrivilegeStatus,
    ) {
        val generation = permissionKeepAliveRefreshGeneration.incrementAndGet()
        val snapshot =
            controller.loadSnapshot(
                notificationPermissionGranted = notificationPermissionGranted,
                privilegeStatus = privilegeStatus,
            )
        if (permissionKeepAliveRefreshGeneration.get() != generation) return
        _permissionKeepAliveState.update { snapshot }
    }

    fun refreshAccessibilityGuard(context: Context) {
        accessibilityGuardRefreshJob?.cancel()
        accessibilityGuardRefreshJob =
            viewModelScope.launch {
                refreshAccessibilityGuardNow(context)
            }
    }

    suspend fun refreshAccessibilityGuardNow(context: Context) {
        _accessibilityGuardState.update { current -> current.copy(loading = true) }
        val result =
            runCatching {
                repository.loadAccessibilityGuardState(context.applicationContext)
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        result
            .onSuccess { snapshot ->
                _accessibilityGuardState.update { current ->
                    snapshot.copy(
                        loading = false,
                        manualCheckRunning = current.manualCheckRunning,
                        exportingHistory = current.exportingHistory,
                    )
                }
            }.onFailure { error ->
                _accessibilityGuardState.update { current -> current.copy(loading = false) }
                _events.emit(
                    SettingsPageEvent.FailureToast(
                        messageRes = R.string.settings_accessibility_guard_toast_update_failed,
                        reason = error.javaClass.simpleName,
                    ),
                )
            }
    }

    fun updateAccessibilityGuardDaemonEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val result = repository.setAccessibilityGuardDaemonEnabled(context.applicationContext, enabled)
            if (result.isFailure) {
                _events.emit(
                    SettingsPageEvent.FailureToast(
                        messageRes = R.string.settings_accessibility_guard_toast_update_failed,
                        reason = result.reasonName(),
                    ),
                )
            }
            refreshAccessibilityGuardNow(context.applicationContext)
        }
    }

    fun updateAccessibilityGuardBootCheckEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val result = repository.setAccessibilityGuardBootCheckEnabled(enabled)
            if (result.isFailure) {
                _events.emit(
                    SettingsPageEvent.FailureToast(
                        messageRes = R.string.settings_accessibility_guard_toast_update_failed,
                        reason = result.reasonName(),
                    ),
                )
            }
            refreshAccessibilityGuardNow(context.applicationContext)
        }
    }

    fun updateAccessibilityGuardScreenOnEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val result = repository.setAccessibilityGuardScreenOnEnabled(context.applicationContext, enabled)
            if (result.isFailure) {
                _events.emit(
                    SettingsPageEvent.FailureToast(
                        messageRes = R.string.settings_accessibility_guard_toast_update_failed,
                        reason = result.reasonName(),
                    ),
                )
            }
            refreshAccessibilityGuardNow(context.applicationContext)
        }
    }

    fun runAccessibilityGuardManualCheck(context: Context) {
        if (_accessibilityGuardState.value.manualCheckRunning) return
        viewModelScope.launch {
            _accessibilityGuardState.update { state -> state.copy(manualCheckRunning = true) }
            val result = repository.runAccessibilityGuardManualCheck(context.applicationContext)
            _accessibilityGuardState.update { state -> state.copy(manualCheckRunning = false) }
            if (result.isSuccess) {
                _events.emit(SettingsPageEvent.Toast(R.string.settings_accessibility_guard_toast_check_recorded))
            } else {
                _events.emit(
                    SettingsPageEvent.FailureToast(
                        messageRes = R.string.settings_accessibility_guard_toast_check_failed,
                        reason = result.reasonName(),
                    ),
                )
            }
            refreshAccessibilityGuardNow(context.applicationContext)
        }
    }

    fun beginAccessibilityGuardHistoryExport() {
        if (_accessibilityGuardState.value.exportingHistory) return
        viewModelScope.launch {
            val fileName = repository.buildAccessibilityGuardHistoryExportFileName()
            _accessibilityGuardState.update { state -> state.copy(exportingHistory = true) }
            _events.emit(SettingsPageEvent.LaunchAccessibilityGuardHistoryExport(fileName))
        }
    }

    fun finishAccessibilityGuardHistoryExport() {
        _accessibilityGuardState.update { state -> state.copy(exportingHistory = false) }
    }

    fun completeAccessibilityGuardHistoryExport(
        context: Context,
        uri: Uri?,
    ) {
        if (uri == null) {
            finishAccessibilityGuardHistoryExport()
            return
        }
        viewModelScope.launch {
            val result =
                repository.exportAccessibilityGuardHistory(
                    context = context.applicationContext,
                    uri = uri,
                )
            finishAccessibilityGuardHistoryExport()
            if (result.isSuccess) {
                _events.emit(SettingsPageEvent.Toast(R.string.settings_accessibility_guard_toast_history_exported))
            } else {
                _events.emit(
                    SettingsPageEvent.FailureToast(
                        messageRes = R.string.settings_accessibility_guard_toast_history_export_failed,
                        reason = result.errorPreview,
                    ),
                )
            }
            refreshAccessibilityGuardNow(context.applicationContext)
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
