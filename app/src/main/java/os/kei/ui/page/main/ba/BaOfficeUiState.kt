package os.kei.ui.page.main.ba

import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CancellationException
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountNotificationMode
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaGlobalReminderSettings
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal data class BaOfficeAccountCardUiState(
    val id: BaAccountId,
    val displayName: String,
    val nickname: String,
    val friendCode: String,
    val serverIndex: Int,
    val enabled: Boolean,
    val notificationMode: BaAccountNotificationMode,
    val remindersEnabled: Boolean,
    val customReminderSettings: BaGlobalReminderSettings,
)

internal data class BaOfficeAccountUiState(
    val accounts: List<BaOfficeAccountCardUiState> = emptyList(),
    val activeAccountId: BaAccountId? = null,
    val allAccountsFollowGlobalNotificationSettings: Boolean = true,
    val globalReminderSettings: BaGlobalReminderSettings = BaGlobalReminderSettings(),
) {
    val activeIndex: Int
        get() = accounts.indexOfFirst { it.id == activeAccountId }.coerceAtLeast(0)
}

internal data class BaOfficeServerUiState(
    val serverIndex: Int = BaPageSnapshot().serverIndex,
)

internal data class BaOfficeChromeUiState(
    val showSettingsSheet: Boolean = false,
    val showAccountManagementSheet: Boolean = false,
    val showNotificationSettingsSheet: Boolean = false,
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
    val accountUiState: BaOfficeAccountUiState = BaOfficeAccountUiState(),
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
        calendarRefreshIntervalHours = calendarRefreshIntervalHours,
    )

internal fun BaAccountStoreSnapshot.toOfficeAccountUiState(): BaOfficeAccountUiState =
    BaOfficeAccountUiState(
        accounts =
            accounts.map { account ->
                BaOfficeAccountCardUiState(
                    id = account.profile.id,
                    displayName = account.profile.displayName,
                    nickname = account.profile.nickname,
                    friendCode = account.profile.friendCode,
                    serverIndex = account.profile.serverIndex.coerceIn(0, 2),
                    enabled = account.profile.enabled,
                    notificationMode = account.profile.notificationMode,
                    remindersEnabled = account.profile.remindersEnabled,
                    customReminderSettings =
                        account.reminderOverride?.let { override ->
                            BaGlobalReminderSettings(
                                apNotifyEnabled = override.apNotifyEnabled,
                                apNotifyThreshold = override.apNotifyThreshold,
                                cafeApNotifyEnabled = override.cafeApNotifyEnabled,
                                cafeApNotifyThreshold = override.cafeApNotifyThreshold,
                                arenaRefreshNotifyEnabled = override.arenaRefreshNotifyEnabled,
                                cafeVisitNotifyEnabled = override.cafeVisitNotifyEnabled,
                            )
                        } ?: globalReminderSettings,
                )
            },
        activeAccountId = activeAccountId,
        allAccountsFollowGlobalNotificationSettings = allAccountsFollowGlobalNotificationSettings,
        globalReminderSettings = globalReminderSettings,
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
