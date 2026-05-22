package os.kei.ui.page.main.ba

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal data class BaOfficeServerRestoreEvent(
    val serverIndex: Int,
)

internal data class BaOfficeSnapshotUiState(
    val snapshot: BaPageSnapshot = BaPageSnapshot(),
    val loaded: Boolean = false,
)

internal class BaOfficeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val defaultSnapshot = BaPageSnapshot()
    private val _snapshotUiState = MutableStateFlow(BaOfficeSnapshotUiState(snapshot = defaultSnapshot))
    val snapshotUiState: StateFlow<BaOfficeSnapshotUiState> = _snapshotUiState.asStateFlow()
    private val _serverRestoreEvents = MutableSharedFlow<BaOfficeServerRestoreEvent>(replay = 0)
    val serverRestoreEvents: SharedFlow<BaOfficeServerRestoreEvent> = _serverRestoreEvents.asSharedFlow()
    val office: BaOfficeController = BaOfficeController(defaultSnapshot)

    init {
        viewModelScope.launch {
            val snapshot = BaOfficeRepository.loadSnapshotAsync()
            if (office.matchesSnapshot(defaultSnapshot)) {
                office.applySnapshot(snapshot)
            }
            _snapshotUiState.value =
                BaOfficeSnapshotUiState(
                    snapshot = snapshot,
                    loaded = true,
                )
        }
    }

    fun clearListScrollState() {
        viewModelScope.launch {
            BaOfficeRepository.clearListScrollStateAsync()
        }
    }

    fun restoreServerFromStore(currentServerIndex: Int) {
        viewModelScope.launch {
            val savedServerIndex = BaOfficeRepository.loadServerIndexAsync()
            if (savedServerIndex == currentServerIndex) return@launch
            if (office.idIndependentByServer) {
                office.applyIdentity(BaOfficeRepository.loadIdentityForServer(savedServerIndex))
            }
            _serverRestoreEvents.emit(
                BaOfficeServerRestoreEvent(serverIndex = savedServerIndex),
            )
        }
    }
}
