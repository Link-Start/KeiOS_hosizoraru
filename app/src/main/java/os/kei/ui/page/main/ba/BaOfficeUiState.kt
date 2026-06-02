package os.kei.ui.page.main.ba

import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CancellationException
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal data class BaOfficeServerUiState(
    val serverIndex: Int = BaPageSnapshot().serverIndex,
)

internal data class BaOfficeChromeUiState(
    val showSettingsSheet: Boolean = false,
    val showNotificationSettingsSheet: Boolean = false,
    val showDebugSheet: Boolean = false,
    val showOverviewServerPopup: Boolean = false,
    val showCafeLevelPopup: Boolean = false,
    val overviewServerPopupAnchorBounds: IntRect? = null,
    val cafeLevelPopupAnchorBounds: IntRect? = null,
    val notificationLeadDropdownExpanded: Boolean = false,
    val notificationLeadDropdownAnchorBounds: IntRect? = null,
    val consumedScrollToTopSignal: Int = 0,
    val debugUseRealCalendarPoolData: Boolean = true,
)

internal data class BaOfficeSyncUiState(
    val calendarReloadSignal: Int = 0,
    val poolReloadSignal: Int = 0,
    val calendarHydrationReady: Boolean = false,
    val poolHydrationReady: Boolean = false,
)

internal data class BaOfficeRuntimeUiState(
    val showEndedPools: Boolean = BaPageSnapshot().showEndedPools,
    val showEndedActivities: Boolean = BaPageSnapshot().showEndedActivities,
    val showCalendarPoolImages: Boolean = BaPageSnapshot().showCalendarPoolImages,
    val mediaAdaptiveRotationEnabled: Boolean = BaPageSnapshot().mediaAdaptiveRotationEnabled,
    val mediaSaveCustomEnabled: Boolean = BaPageSnapshot().mediaSaveCustomEnabled,
    val mediaSaveFixedTreeUri: String = BaPageSnapshot().mediaSaveFixedTreeUri,
    val idIndependentByServer: Boolean = BaPageSnapshot().idIndependentByServer,
    val calendarRefreshIntervalHours: Int = BaPageSnapshot().calendarRefreshIntervalHours,
)

internal data class BaOfficeSettingsDraftUiState(
    val draft: BaPageSettingsDraftState = BaPageSnapshot().toSettingsDraftState(),
)

internal data class BaOfficeNotificationDraftUiState(
    val draft: BaPageNotificationDraftState = BaPageSnapshot().toNotificationDraftState(),
    val savedDraft: BaPageNotificationDraftState = BaPageSnapshot().toNotificationDraftState(),
)

internal data class BaOfficePageUiState(
    val chromeUiState: BaOfficeChromeUiState = BaOfficeChromeUiState(),
    val syncUiState: BaOfficeSyncUiState = BaOfficeSyncUiState(),
    val serverUiState: BaOfficeServerUiState = BaOfficeServerUiState(),
    val runtimeUiState: BaOfficeRuntimeUiState = BaOfficeRuntimeUiState(),
    val settingsDraftUiState: BaOfficeSettingsDraftUiState = BaOfficeSettingsDraftUiState(),
    val notificationDraftUiState: BaOfficeNotificationDraftUiState = BaOfficeNotificationDraftUiState(),
)

internal sealed interface BaOfficeEvent {
    data class SettingsSaved(
        val persisted: BaSettingsPersistenceResult,
        val clampUpdate: BaRuntimePersistenceUpdate?,
        val runtimeUpdate: BaRuntimePersistenceUpdate?,
    ) : BaOfficeEvent

    data class NotificationSettingsSaved(
        val savedDraft: BaPageNotificationDraftState,
        val runtimeUpdate: BaRuntimePersistenceUpdate?,
    ) : BaOfficeEvent

    data class OperationFailed(
        val error: Throwable,
    ) : BaOfficeEvent
}

internal fun BaPageSnapshot.toRuntimeUiState(): BaOfficeRuntimeUiState =
    BaOfficeRuntimeUiState(
        showEndedPools = showEndedPools,
        showEndedActivities = showEndedActivities,
        showCalendarPoolImages = showCalendarPoolImages,
        mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
        mediaSaveCustomEnabled = mediaSaveCustomEnabled,
        mediaSaveFixedTreeUri = mediaSaveFixedTreeUri,
        idIndependentByServer = idIndependentByServer,
        calendarRefreshIntervalHours = calendarRefreshIntervalHours,
    )

internal fun BaOfficeChromeUiState.withoutFloatingPopups(): BaOfficeChromeUiState =
    copy(
        showOverviewServerPopup = false,
        showCafeLevelPopup = false,
        notificationLeadDropdownExpanded = false,
        notificationLeadDropdownAnchorBounds = null,
    )

internal fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
