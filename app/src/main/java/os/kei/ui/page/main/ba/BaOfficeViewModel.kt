package os.kei.ui.page.main.ba

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs

internal data class BaOfficeServerRestoreEvent(
    val serverIndex: Int,
)

internal data class BaOfficeSnapshotUiState(
    val snapshot: BaPageSnapshot = BaPageSnapshot(),
    val loaded: Boolean = false,
)

internal sealed interface BaOfficeEvent {
    data class SettingsSaved(
        val persisted: BaSettingsPersistenceResult,
        val clampUpdate: BaRuntimePersistenceUpdate?,
        val runtimeUpdate: BaRuntimePersistenceUpdate?,
        val refreshCalendar: Boolean,
        val refreshPool: Boolean,
    ) : BaOfficeEvent

    data class NotificationSettingsSaved(
        val savedDraft: BaPageNotificationDraftState,
        val runtimeUpdate: BaRuntimePersistenceUpdate?,
    ) : BaOfficeEvent

    data class RefreshIntervalSaved(
        val hours: Int,
        val shouldRefresh: Boolean,
    ) : BaOfficeEvent

    data class OperationFailed(
        val error: Throwable,
    ) : BaOfficeEvent
}

internal class BaOfficeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val defaultSnapshot = BaPageSnapshot()
    private val _snapshotUiState = MutableStateFlow(BaOfficeSnapshotUiState(snapshot = defaultSnapshot))
    val snapshotUiState: StateFlow<BaOfficeSnapshotUiState> = _snapshotUiState.asStateFlow()
    private val _serverRestoreEvents = MutableSharedFlow<BaOfficeServerRestoreEvent>(replay = 0)
    val serverRestoreEvents: SharedFlow<BaOfficeServerRestoreEvent> = _serverRestoreEvents.asSharedFlow()
    private val _events = MutableSharedFlow<BaOfficeEvent>(replay = 0, extraBufferCapacity = 8)
    val events: SharedFlow<BaOfficeEvent> = _events.asSharedFlow()
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

    fun saveSettings(
        sheetState: BaSettingsSheetState,
        currentShowEndedActivities: Boolean,
        currentShowCalendarPoolImages: Boolean,
        serverIndex: Int,
    ) {
        viewModelScope.launch {
            try {
                val persisted =
                    BaSettingsPersistenceRepository.persistSettingsDraftAsync(
                        sheetState = sheetState,
                        currentShowEndedActivities = currentShowEndedActivities,
                        currentShowCalendarPoolImages = currentShowCalendarPoolImages,
                    )
                office.cafeLevel = persisted.savedCafeLevel
                val clampUpdate = office.clampCafeStoredToCapUpdate()
                office
                    .applyIdIndependentByServer(
                        serverIndex = serverIndex,
                        enabled = persisted.idIndependentByServer,
                    ).persistAsync()

                val refreshCalendarForEnded =
                    persisted.turningEndedActivitiesOn &&
                        BaSettingsPersistenceRepository.calendarCacheIsBlankAsync(serverIndex)
                val refreshCalendarForImages =
                    persisted.turningImagesOn &&
                        !BaSettingsPersistenceRepository.hasAnyImageInCalendarCacheAsync(serverIndex)
                val refreshPoolForImages =
                    persisted.turningImagesOn &&
                        !BaSettingsPersistenceRepository.hasAnyImageInPoolCacheAsync(serverIndex)
                AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
                _events.emit(
                    BaOfficeEvent.SettingsSaved(
                        persisted = persisted,
                        clampUpdate = clampUpdate,
                        runtimeUpdate = office.applyRuntimeTick(),
                        refreshCalendar = refreshCalendarForEnded || refreshCalendarForImages,
                        refreshPool = refreshPoolForImages,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun saveNotificationSettings(
        sheetState: BaNotificationSettingsSheetState,
        serverIndex: Int,
    ) {
        viewModelScope.launch {
            try {
                val previousCafeApNotifyEnabled = office.cafeApNotifyEnabled
                val previousCafeApNotifyThreshold = office.cafeApNotifyThreshold
                val previousArenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled
                val previousCafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled
                val persisted = BaSettingsPersistenceRepository.persistNotificationSettingsDraftAsync(sheetState)

                office.apNotifyEnabled = sheetState.apNotifyEnabled
                office.cafeApNotifyEnabled = persisted.cafeApNotifyEnabled
                office.arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled
                office.cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled
                office.apNotifyThreshold = persisted.savedThreshold
                office.cafeApNotifyThreshold = persisted.savedCafeApThreshold
                val savedDraft =
                    BaPageNotificationDraftState(
                        apNotifyEnabled = office.apNotifyEnabled,
                        cafeApNotifyEnabled = office.cafeApNotifyEnabled,
                        arenaRefreshNotifyEnabled = office.arenaRefreshNotifyEnabled,
                        cafeVisitNotifyEnabled = office.cafeVisitNotifyEnabled,
                        calendarUpcomingNotifyEnabled = persisted.calendarUpcomingNotifyEnabled,
                        calendarEndingNotifyEnabled = persisted.calendarEndingNotifyEnabled,
                        poolUpcomingNotifyEnabled = persisted.poolUpcomingNotifyEnabled,
                        poolEndingNotifyEnabled = persisted.poolEndingNotifyEnabled,
                        calendarPoolChangeNotifyEnabled = persisted.calendarPoolChangeNotifyEnabled,
                        calendarPoolNotifyLeadHours = persisted.calendarPoolNotifyLeadHours,
                        apNotifyThresholdText = office.apNotifyThreshold.toString(),
                        cafeApNotifyThresholdText = office.cafeApNotifyThreshold.toString(),
                    )
                if (!office.cafeApNotifyEnabled ||
                    previousCafeApNotifyThreshold != office.cafeApNotifyThreshold ||
                    !previousCafeApNotifyEnabled
                ) {
                    office.cafeApLastNotifiedLevel = -1
                    BaSettingsPersistenceRepository.resetCafeApLastNotifiedLevelAsync()
                }
                if (!office.arenaRefreshNotifyEnabled) {
                    office.arenaRefreshLastNotifiedSlotMs = 0L
                    BaSettingsPersistenceRepository.resetArenaRefreshLastNotifiedSlotAsync()
                } else if (!previousArenaRefreshNotifyEnabled) {
                    val baselineSlotMs =
                        currentArenaRefreshSlotMs(
                            nowMs = System.currentTimeMillis(),
                            serverIndex = serverIndex,
                        )
                    office.arenaRefreshLastNotifiedSlotMs = baselineSlotMs
                    BaSettingsPersistenceRepository.saveArenaRefreshLastNotifiedSlotAsync(baselineSlotMs)
                }
                if (!office.cafeVisitNotifyEnabled) {
                    office.cafeVisitLastNotifiedSlotMs = 0L
                    BaSettingsPersistenceRepository.resetCafeVisitLastNotifiedSlotAsync()
                } else if (!previousCafeVisitNotifyEnabled) {
                    val baselineSlotMs =
                        currentCafeStudentRefreshSlotMs(
                            nowMs = System.currentTimeMillis(),
                            serverIndex = serverIndex,
                        )
                    office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
                    BaSettingsPersistenceRepository.saveCafeVisitLastNotifiedSlotAsync(baselineSlotMs)
                }

                AppBackgroundScheduler.scheduleBaApThreshold(getApplication())
                _events.emit(
                    BaOfficeEvent.NotificationSettingsSaved(
                        savedDraft = savedDraft,
                        runtimeUpdate = office.applyRuntimeTick(),
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }

    fun saveRefreshInterval(
        hours: Int,
        calendarLastSyncMs: Long,
    ) {
        viewModelScope.launch {
            try {
                val persisted =
                    BaSettingsPersistenceRepository.persistRefreshIntervalAsync(
                        hours = hours,
                        calendarLastSyncMs = calendarLastSyncMs,
                    )
                _events.emit(
                    BaOfficeEvent.RefreshIntervalSaved(
                        hours = persisted.hours,
                        shouldRefresh = persisted.shouldRefresh,
                    ),
                )
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                _events.emit(BaOfficeEvent.OperationFailed(error))
            }
        }
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
