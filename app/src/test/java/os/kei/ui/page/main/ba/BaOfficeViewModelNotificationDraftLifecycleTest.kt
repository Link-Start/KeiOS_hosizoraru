package os.kei.ui.page.main.ba

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaGlobalReminderSettings
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class BaOfficeViewModelNotificationDraftLifecycleTest {
    @Test
    fun `view model show refresh hide reopen and save use synchronized AP read setting`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val repository =
                RecordingBaOfficePageRepository(
                    snapshot = BaPageSnapshot(keepApRemindersReadUntilBelowThreshold = true),
                )
            val viewModel =
                BaOfficeViewModel.createForTest(
                    application = application,
                    repository = repository,
                    persistRuntimeUpdate = {},
                    scheduleBaApThreshold = {},
                )
            advanceUntilIdle()

            viewModel.showNotificationSettingsSheet()
            assertTrue(
                viewModel.notificationDraftUiState.value.draft
                    .keepApRemindersReadUntilBelowThreshold,
            )

            repository.snapshot =
                repository.snapshot.copy(keepApRemindersReadUntilBelowThreshold = false)
            viewModel.refreshRuntimeSettingsFromStore()
            advanceUntilIdle()
            viewModel.hideNotificationSettingsSheet()
            viewModel.showNotificationSettingsSheet()

            val reopenedDraft = viewModel.notificationDraftUiState.value.draft
            assertFalse(reopenedDraft.keepApRemindersReadUntilBelowThreshold)
            viewModel.saveNotificationSettings(
                sheetState = buildBaNotificationSettingsSheetState(reopenedDraft),
                serverIndex = 2,
            )
            advanceUntilIdle()

            assertFalse(
                repository.savedSheetState
                    ?.keepApRemindersReadUntilBelowThreshold
                    ?: true,
            )
            assertFalse(
                viewModel.notificationDraftUiState.value.savedDraft
                    .keepApRemindersReadUntilBelowThreshold,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class RecordingBaOfficePageRepository(
    var snapshot: BaPageSnapshot,
) : BaOfficePageRepository() {
    var savedSheetState: BaNotificationSettingsSheetState? = null

    override suspend fun loadInitialSnapshot(): BaPageSnapshot = snapshot

    override suspend fun loadAccountState(): BaAccountStoreSnapshot =
        BaAccountStoreSnapshot(
            accounts = emptyList(),
            activeAccountId = null,
            allAccountsFollowGlobalNotificationSettings = true,
            globalReminderSettings = BaGlobalReminderSettings(),
        )

    override suspend fun persistNotificationSettings(
        sheetState: BaNotificationSettingsSheetState,
        previousCafeApNotifyEnabled: Boolean,
        previousCafeApNotifyThreshold: Int,
        previousArenaRefreshNotifyEnabled: Boolean,
        previousCafeVisitNotifyEnabled: Boolean,
        serverIndex: Int,
    ): BaOfficeNotificationSavePersistenceResult {
        savedSheetState = sheetState
        val persisted =
            BaNotificationSettingsPersistenceResult(
                savedThreshold = sheetState.apNotifyThresholdText.toInt(),
                savedCafeApThreshold = sheetState.cafeApNotifyThresholdText.toInt(),
                cafeApNotifyEnabled = sheetState.cafeApNotifyEnabled,
                keepApRemindersReadUntilBelowThreshold =
                    sheetState.keepApRemindersReadUntilBelowThreshold,
                arenaRefreshNotifyEnabled = sheetState.arenaRefreshNotifyEnabled,
                cafeVisitNotifyEnabled = sheetState.cafeVisitNotifyEnabled,
                calendarUpcomingNotifyEnabled = sheetState.calendarUpcomingNotifyEnabled,
                calendarEndingNotifyEnabled = sheetState.calendarEndingNotifyEnabled,
                poolUpcomingNotifyEnabled = sheetState.poolUpcomingNotifyEnabled,
                poolEndingNotifyEnabled = sheetState.poolEndingNotifyEnabled,
                calendarPoolChangeNotifyEnabled = sheetState.calendarPoolChangeNotifyEnabled,
                calendarPoolNotifyLeadHours = sheetState.calendarPoolNotifyLeadHours,
                craftNotifyEnabled = sheetState.craftNotifyEnabled,
            )
        return BaOfficeNotificationSavePersistenceResult(
            persisted = persisted,
            savedDraft =
                BaPageNotificationDraftState(
                    apNotifyEnabled = sheetState.apNotifyEnabled,
                    cafeApNotifyEnabled = persisted.cafeApNotifyEnabled,
                    keepApRemindersReadUntilBelowThreshold =
                        persisted.keepApRemindersReadUntilBelowThreshold,
                    arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled,
                    cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled,
                    craftNotifyEnabled = persisted.craftNotifyEnabled,
                    calendarUpcomingNotifyEnabled = persisted.calendarUpcomingNotifyEnabled,
                    calendarEndingNotifyEnabled = persisted.calendarEndingNotifyEnabled,
                    poolUpcomingNotifyEnabled = persisted.poolUpcomingNotifyEnabled,
                    poolEndingNotifyEnabled = persisted.poolEndingNotifyEnabled,
                    calendarPoolChangeNotifyEnabled = persisted.calendarPoolChangeNotifyEnabled,
                    calendarPoolNotifyLeadHours = persisted.calendarPoolNotifyLeadHours,
                    apNotifyThresholdText = persisted.savedThreshold.toString(),
                    cafeApNotifyThresholdText = persisted.savedCafeApThreshold.toString(),
                ),
            resetCafeApLastNotifiedLevel = false,
            arenaRefreshLastNotifiedSlotMs = null,
            cafeVisitLastNotifiedSlotMs = null,
        )
    }
}
