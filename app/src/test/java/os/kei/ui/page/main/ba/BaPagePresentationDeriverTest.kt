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
            buildBaPageRouteState(
                calendarUiState = BaCalendarUiState(),
                poolUiState = BaPoolUiState(),
                chromeUiState = BaOfficeChromeUiState(),
                syncUiState = BaOfficeSyncUiState(),
                accountUiState = BaOfficeAccountUiState(),
                serverUiState = BaOfficeServerUiState(),
                runtimeUiState =
                    BaOfficeRuntimeUiState(
                        mediaAdaptiveRotationEnabled = false,
                        mediaSaveCustomEnabled = true,
                        mediaSaveFixedTreeUri = "content://ba-media",
                        idIndependentByServer = true,
                        showEndedActivities = true,
                        showEndedPools = true,
                        showCalendarPoolImages = false,
                    ),
                settingsDraftUiState = BaOfficeSettingsDraftUiState(),
                notificationDraftUiState = BaOfficeNotificationDraftUiState(),
            )

        val draft =
            buildBaSavedSettingsDraftState(
                officeState = officeState,
                routeState = routeState,
            )

        assertEquals(7, draft.cafeLevel)
        assertTrue(draft.mediaSaveCustomEnabled)
        assertEquals("content://ba-media", draft.mediaSaveFixedTreeUri)
        assertTrue(draft.idIndependentByServer)
    }
}
