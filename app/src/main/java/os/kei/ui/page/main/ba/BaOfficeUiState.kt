package os.kei.ui.page.main.ba

import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CancellationException
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountNotificationMode
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaDailyDoneConfig
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

/**
 * The daily-done template sheet, opened from the floating dock.
 *
 * Carries the template itself rather than letting the sheet read it: the template is global — the tiles,
 * the launcher shortcuts and MCP all apply the same record — so it has to be re-read on every opening,
 * and loading it before [show] flips is what keeps the sheet from drawing one frame of defaults and then
 * snapping to the stored values.
 *
 * [applying] is state and not a local flag because the run outlives the sheet: it is launched on the
 * view-model's scope so that leaving the page mid-run cannot stop it half way through the accounts.
 */
internal data class BaDailyDoneSheetUiState(
    val show: Boolean = false,
    val config: BaDailyDoneConfig = BaDailyDoneConfig(),
    val applying: Boolean = false,
)

internal data class BaOfficeChromeUiState(
    val showSettingsSheet: Boolean = false,
    val showAccountManagementSheet: Boolean = false,
    val accountManagementInitialEditAccountId: BaAccountId? = null,
    val showNotificationSettingsSheet: Boolean = false,
    val showApLimitToolsSheet: Boolean = false,
    val showCafeApToolsSheet: Boolean = false,
    val dailyDoneSheet: BaDailyDoneSheetUiState = BaDailyDoneSheetUiState(),
    val cafeCooldownEditTarget: BaCafeCooldownEditTarget? = null,
    val craftSlotEditTarget: BaCraftSlotEditTarget? = null,
    val showCafeLevelPopup: Boolean = false,
    val cafeLevelPopupAnchorBounds: IntRect? = null,
    val notificationLeadDropdownExpanded: Boolean = false,
    val notificationLeadDropdownAnchorBounds: IntRect? = null,
    val consumedScrollToTopSignal: Int = 0,
    val debugUseRealCalendarPoolData: Boolean = true,
)

internal val BaOfficeChromeUiState.hasVisiblePageSheet: Boolean
    get() =
        showSettingsSheet ||
            showAccountManagementSheet ||
            showNotificationSettingsSheet ||
            showApLimitToolsSheet ||
            showCafeApToolsSheet ||
            dailyDoneSheet.show ||
            cafeCooldownEditTarget != null ||
            craftSlotEditTarget != null

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
    val craftCardExpanded: Boolean = BaPageSnapshot().craftCardExpanded,
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
        craftCardExpanded = craftCardExpanded,
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
                                keepApRemindersReadUntilBelowThreshold =
                                    override.keepApRemindersReadUntilBelowThreshold,
                                arenaRefreshNotifyEnabled = override.arenaRefreshNotifyEnabled,
                                cafeVisitNotifyEnabled = override.cafeVisitNotifyEnabled,
                                craftNotifyEnabled = override.craftNotifyEnabled,
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
        showCafeLevelPopup = false,
        notificationLeadDropdownExpanded = false,
        notificationLeadDropdownAnchorBounds = null,
    )

internal fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
