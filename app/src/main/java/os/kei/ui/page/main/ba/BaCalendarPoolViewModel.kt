package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.BaPoolEntry

@Immutable
internal data class BaCalendarUiState(
    val entries: List<BaCalendarEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val lastSyncMs: Long = 0L,
)

@Immutable
internal data class BaPoolUiState(
    val entries: List<BaPoolEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val lastSyncMs: Long = 0L,
)

@Immutable
internal data class BaCalendarPoolRouteState(
    val calendarUiState: BaCalendarUiState = BaCalendarUiState(),
    val poolUiState: BaPoolUiState = BaPoolUiState(),
)

@Immutable
internal data class BaCalendarPoolSettingsUiState(
    val snapshot: BaPageSnapshot = BaPageSnapshot(),
    val loaded: Boolean = false,
)

@Immutable
internal data class BaCalendarPoolChromeUiState(
    val serverIndex: Int = BaPageSnapshot().serverIndex,
    val serverIndexTouched: Boolean = false,
    val calendarReloadSignal: Int = 0,
    val poolReloadSignal: Int = 0,
    val showServerPopup: Boolean = false,
    val serverPopupAnchorBounds: IntRect? = null,
)

private data class BaCalendarRequestKey(
    val isPageActive: Boolean,
    val serverIndex: Int,
    val reloadSignal: Int,
    val calendarRefreshIntervalHours: Int,
    val hydrationReady: Boolean,
)

private data class BaPoolRequestKey(
    val isPageActive: Boolean,
    val serverIndex: Int,
    val reloadSignal: Int,
    val calendarRefreshIntervalHours: Int,
    val hydrationReady: Boolean,
)

internal class BaCalendarPoolViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private val _calendarUiState = MutableStateFlow(BaCalendarUiState())
    val calendarUiState: StateFlow<BaCalendarUiState> = _calendarUiState.asStateFlow()

    private val _poolUiState = MutableStateFlow(BaPoolUiState())
    val poolUiState: StateFlow<BaPoolUiState> = _poolUiState.asStateFlow()

    private val _settingsUiState = MutableStateFlow(BaCalendarPoolSettingsUiState())
    val settingsUiState: StateFlow<BaCalendarPoolSettingsUiState> = _settingsUiState.asStateFlow()

    private val _chromeUiState = MutableStateFlow(BaCalendarPoolChromeUiState())
    val chromeUiState: StateFlow<BaCalendarPoolChromeUiState> = _chromeUiState.asStateFlow()

    val routeState: StateFlow<BaCalendarPoolRouteState> =
        combine(calendarUiState, poolUiState) { calendar, pool ->
            BaCalendarPoolRouteState(
                calendarUiState = calendar,
                poolUiState = pool,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = BaCalendarPoolRouteState(),
        )

    private var calendarJob: Job? = null
    private var poolJob: Job? = null
    private var lastCalendarRequestKey: BaCalendarRequestKey? = null
    private var lastPoolRequestKey: BaPoolRequestKey? = null
    private val imageWarmCoordinator =
        BaCalendarPoolImageWarmCoordinator(
            scope = viewModelScope,
            context = appContext,
        )

    init {
        viewModelScope.launch {
            val snapshot = BaCalendarPoolRepository.loadSettingsSnapshotAsync()
            _chromeUiState.update { state ->
                if (state.serverIndexTouched) {
                    state
                } else {
                    state.copy(serverIndex = snapshot.serverIndex)
                }
            }
            _settingsUiState.value =
                BaCalendarPoolSettingsUiState(
                    snapshot = snapshot,
                    loaded = true,
                )
        }
    }

    fun syncCalendar(
        isPageActive: Boolean,
        serverIndex: Int,
        reloadSignal: Int,
        calendarRefreshIntervalHours: Int,
        hydrationReady: Boolean,
    ) {
        val key =
            BaCalendarRequestKey(
                isPageActive = isPageActive,
                serverIndex = serverIndex,
                reloadSignal = reloadSignal,
                calendarRefreshIntervalHours = calendarRefreshIntervalHours,
                hydrationReady = hydrationReady,
            )
        val previousKey = lastCalendarRequestKey
        if (key == previousKey) return
        lastCalendarRequestKey = key
        calendarJob?.cancel()
        calendarJob =
            viewModelScope.launch {
                val current = _calendarUiState.value
                val showLoading =
                    current.entries.isEmpty() ||
                        reloadSignal > 0 ||
                        previousKey?.serverIndex != serverIndex
                _calendarUiState.value = current.copy(loading = showLoading, error = null)
                val snapshot =
                    BaCalendarPoolRepository.syncCalendar(
                        context = appContext,
                        isPageActive = isPageActive,
                        serverIndex = serverIndex,
                        reloadSignal = reloadSignal,
                        calendarRefreshIntervalHours = calendarRefreshIntervalHours,
                        hydrationReady = hydrationReady,
                    )
                _calendarUiState.value =
                    BaCalendarUiState(
                        entries = snapshot.entries,
                        loading = snapshot.loading,
                        error = snapshot.error,
                        lastSyncMs = snapshot.lastSyncMs,
                    )
                if (!snapshot.loading && snapshot.imageWarmEntries.isNotEmpty()) {
                    imageWarmCoordinator.scheduleCalendar(
                        serverIndex = serverIndex,
                        entries = snapshot.imageWarmEntries,
                    )
                }
            }
    }

    fun syncPool(
        isPageActive: Boolean,
        serverIndex: Int,
        reloadSignal: Int,
        calendarRefreshIntervalHours: Int,
        hydrationReady: Boolean,
    ) {
        val key =
            BaPoolRequestKey(
                isPageActive = isPageActive,
                serverIndex = serverIndex,
                reloadSignal = reloadSignal,
                calendarRefreshIntervalHours = calendarRefreshIntervalHours,
                hydrationReady = hydrationReady,
            )
        val previousKey = lastPoolRequestKey
        if (key == previousKey) return
        lastPoolRequestKey = key
        poolJob?.cancel()
        poolJob =
            viewModelScope.launch {
                val current = _poolUiState.value
                val showLoading =
                    current.entries.isEmpty() ||
                        reloadSignal > 0 ||
                        previousKey?.serverIndex != serverIndex
                _poolUiState.value = current.copy(loading = showLoading, error = null)
                val snapshot =
                    BaCalendarPoolRepository.syncPool(
                        context = appContext,
                        isPageActive = isPageActive,
                        serverIndex = serverIndex,
                        reloadSignal = reloadSignal,
                        calendarRefreshIntervalHours = calendarRefreshIntervalHours,
                        hydrationReady = hydrationReady,
                    )
                _poolUiState.value =
                    BaPoolUiState(
                        entries = snapshot.entries,
                        loading = snapshot.loading,
                        error = snapshot.error,
                        lastSyncMs = snapshot.lastSyncMs,
                    )
                if (!snapshot.loading && snapshot.imageWarmEntries.isNotEmpty()) {
                    imageWarmCoordinator.schedulePool(
                        serverIndex = serverIndex,
                        entries = snapshot.imageWarmEntries,
                    )
                }
            }
    }

    fun selectServer(index: Int) {
        val normalizedIndex = index.coerceIn(0, 2)
        _chromeUiState.update { state ->
            state.copy(
                serverIndex = normalizedIndex,
                serverIndexTouched = true,
                showServerPopup = false,
            )
        }
        _settingsUiState.update { state ->
            state.copy(
                snapshot = state.snapshot.copy(serverIndex = normalizedIndex),
            )
        }
        viewModelScope.launch {
            BaCalendarPoolRepository.saveServerIndexAsync(normalizedIndex)
        }
    }

    fun requestCalendarReload() {
        _chromeUiState.update { state ->
            state.copy(calendarReloadSignal = state.calendarReloadSignal + 1)
        }
    }

    fun requestPoolReload() {
        _chromeUiState.update { state ->
            state.copy(poolReloadSignal = state.poolReloadSignal + 1)
        }
    }

    fun updateServerPopupExpanded(expanded: Boolean) {
        _chromeUiState.update { state ->
            if (state.showServerPopup == expanded) {
                state
            } else {
                state.copy(showServerPopup = expanded)
            }
        }
    }

    fun updateServerPopupAnchorBounds(bounds: IntRect?) {
        _chromeUiState.update { state ->
            if (state.serverPopupAnchorBounds == bounds) {
                state
            } else {
                state.copy(serverPopupAnchorBounds = bounds)
            }
        }
    }

    suspend fun preparePoolGuideOpen(rawUrl: String): BaPoolGuideOpenPlan =
        BaCalendarPoolRepository.preparePoolGuideOpen(rawUrl)
}
