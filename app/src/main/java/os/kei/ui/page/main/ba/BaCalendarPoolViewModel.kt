package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry

@Immutable
internal data class BaCalendarUiState(
    val entries: List<BaCalendarEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val lastSyncMs: Long = 0L
)

@Immutable
internal data class BaPoolUiState(
    val entries: List<BaPoolEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val lastSyncMs: Long = 0L
)

private data class BaCalendarRequestKey(
    val isPageActive: Boolean,
    val serverIndex: Int,
    val reloadSignal: Int,
    val calendarRefreshIntervalHours: Int,
    val hydrationReady: Boolean
)

private data class BaPoolRequestKey(
    val isPageActive: Boolean,
    val serverIndex: Int,
    val reloadSignal: Int,
    val calendarRefreshIntervalHours: Int,
    val hydrationReady: Boolean
)

internal class BaCalendarPoolViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private val _calendarUiState = MutableStateFlow(BaCalendarUiState())
    val calendarUiState: StateFlow<BaCalendarUiState> = _calendarUiState.asStateFlow()

    private val _poolUiState = MutableStateFlow(BaPoolUiState())
    val poolUiState: StateFlow<BaPoolUiState> = _poolUiState.asStateFlow()

    private var calendarJob: Job? = null
    private var poolJob: Job? = null
    private var lastCalendarRequestKey: BaCalendarRequestKey? = null
    private var lastPoolRequestKey: BaPoolRequestKey? = null

    fun syncCalendar(
        isPageActive: Boolean,
        serverIndex: Int,
        reloadSignal: Int,
        calendarRefreshIntervalHours: Int,
        hydrationReady: Boolean
    ) {
        val key = BaCalendarRequestKey(
            isPageActive = isPageActive,
            serverIndex = serverIndex,
            reloadSignal = reloadSignal,
            calendarRefreshIntervalHours = calendarRefreshIntervalHours,
            hydrationReady = hydrationReady
        )
        val previousKey = lastCalendarRequestKey
        if (key == previousKey) return
        lastCalendarRequestKey = key
        calendarJob?.cancel()
        calendarJob = viewModelScope.launch {
            val current = _calendarUiState.value
            val showLoading = current.entries.isEmpty() ||
                    reloadSignal > 0 ||
                    previousKey?.serverIndex != serverIndex
            _calendarUiState.value = current.copy(loading = showLoading, error = null)
            val snapshot = BaCalendarPoolRepository.syncCalendar(
                context = appContext,
                isPageActive = isPageActive,
                serverIndex = serverIndex,
                reloadSignal = reloadSignal,
                calendarRefreshIntervalHours = calendarRefreshIntervalHours,
                hydrationReady = hydrationReady
            )
            _calendarUiState.value = BaCalendarUiState(
                entries = snapshot.entries,
                loading = snapshot.loading,
                error = snapshot.error,
                lastSyncMs = snapshot.lastSyncMs
            )
        }
    }

    fun syncPool(
        isPageActive: Boolean,
        serverIndex: Int,
        reloadSignal: Int,
        calendarRefreshIntervalHours: Int,
        hydrationReady: Boolean
    ) {
        val key = BaPoolRequestKey(
            isPageActive = isPageActive,
            serverIndex = serverIndex,
            reloadSignal = reloadSignal,
            calendarRefreshIntervalHours = calendarRefreshIntervalHours,
            hydrationReady = hydrationReady
        )
        val previousKey = lastPoolRequestKey
        if (key == previousKey) return
        lastPoolRequestKey = key
        poolJob?.cancel()
        poolJob = viewModelScope.launch {
            val current = _poolUiState.value
            val showLoading = current.entries.isEmpty() ||
                    reloadSignal > 0 ||
                    previousKey?.serverIndex != serverIndex
            _poolUiState.value = current.copy(loading = showLoading, error = null)
            val snapshot = BaCalendarPoolRepository.syncPool(
                context = appContext,
                isPageActive = isPageActive,
                serverIndex = serverIndex,
                reloadSignal = reloadSignal,
                calendarRefreshIntervalHours = calendarRefreshIntervalHours,
                hydrationReady = hydrationReady
            )
            _poolUiState.value = BaPoolUiState(
                entries = snapshot.entries,
                loading = snapshot.loading,
                error = snapshot.error,
                lastSyncMs = snapshot.lastSyncMs
            )
        }
    }
}
