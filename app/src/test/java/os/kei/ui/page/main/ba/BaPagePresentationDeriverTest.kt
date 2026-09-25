package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaPagePresentationDeriverTest {
    @Test
    fun `saved settings draft is derived from immutable office snapshot`() {
        val officeState =
            BaOfficeController(
                BaPageSnapshot(
                    cafeLevel = 7,
                    showEndedActivities = true,
                    showEndedPools = true,
                    showCalendarPoolImages = false,
                ),
            ).state()
        val routeState =
            testBaPageRouteState(
                runtimeUiState =
                    BaOfficeRuntimeUiState(
                        mediaAdaptiveRotationEnabled = false,
                        mediaSaveCustomEnabled = true,
                        mediaSaveFixedTreeUri = "content://ba-media",
                        showEndedActivities = true,
                        showEndedPools = true,
                        showCalendarPoolImages = false,
                    ),
            )

        val draft =
            buildBaSavedSettingsDraftState(
                officeState = officeState,
                routeState = routeState,
            )

        assertEquals(7, draft.cafeLevel)
        assertTrue(draft.mediaSaveCustomEnabled)
        assertEquals("content://ba-media", draft.mediaSaveFixedTreeUri)
    }

    @Test
    fun `notification presentation carries AP read suppression mode into current and saved sheets`() {
        // true = persistent read until below threshold, false = hourly read.
        for (keepRead in listOf(true, false)) {
            val snapshot = BaPageSnapshot(keepApRemindersReadUntilBelowThreshold = keepRead)

            val presentation = buildNotificationPresentation(snapshot)

            assertEquals(
                keepRead,
                presentation.notificationSettingsSheetState.keepApRemindersReadUntilBelowThreshold,
                "current sheet, keepRead=$keepRead",
            )
            assertEquals(
                keepRead,
                presentation.savedNotificationSettingsSheetState.keepApRemindersReadUntilBelowThreshold,
                "saved sheet, keepRead=$keepRead",
            )
        }
    }

    private fun buildNotificationPresentation(snapshot: BaPageSnapshot): BaPagePresentationState =
        buildBaPagePresentationState(
            officeState = BaOfficeController(snapshot).state(),
            calendarUiState = BaCalendarUiState(),
            poolUiState = BaPoolUiState(),
            officePageUiState =
                BaOfficePageUiState(
                    notificationDraftUiState =
                        BaOfficeNotificationDraftUiState(
                            draft = snapshot.toNotificationDraftState(),
                            savedDraft = snapshot.toNotificationDraftState(),
                        ),
                ),
            clockState = testBaPageClockState(),
            serverOptions = emptyList(),
            cafeLevelOptions = emptyList(),
        )
}
