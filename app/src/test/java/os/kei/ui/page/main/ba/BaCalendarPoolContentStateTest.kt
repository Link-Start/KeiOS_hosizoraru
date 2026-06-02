package os.kei.ui.page.main.ba

import androidx.compose.runtime.mutableLongStateOf
import org.junit.Test
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaCalendarPoolContentStateTest {
    @Test
    fun `cached entries keep rendering when sync error is present`() {
        val status =
            resolveBaCalendarPoolContentStatus(
                visibleEntryCount = 3,
                loading = false,
                refreshing = false,
                error = "cached",
            )

        assertEquals(BaCalendarPoolContentStatus.Error, status)
    }

    @Test
    fun `refreshing with cached entries renders a refresh notice before entries`() {
        val status =
            resolveBaCalendarPoolContentStatus(
                visibleEntryCount = 2,
                loading = false,
                refreshing = true,
                error = null,
            )

        assertEquals(BaCalendarPoolContentStatus.Refreshing, status)
    }

    @Test
    fun `refreshing without entries keeps the initial loading skeleton`() {
        val status =
            resolveBaCalendarPoolContentStatus(
                visibleEntryCount = 0,
                loading = false,
                refreshing = true,
                error = null,
            )

        assertEquals(BaCalendarPoolContentStatus.Loading, status)
    }

    @Test
    fun `ba page content state preserves calendar and pool refreshing flags`() {
        val routeState =
            buildBaPageRouteState(
                calendarUiState =
                    BaCalendarUiState(
                        loading = false,
                        refreshing = true,
                    ),
                poolUiState =
                    BaPoolUiState(
                        loading = false,
                        refreshing = true,
                    ),
                chromeUiState = BaOfficeChromeUiState(),
                syncUiState = BaOfficeSyncUiState(),
                accountUiState = BaOfficeAccountUiState(),
                serverUiState = BaOfficeServerUiState(),
                runtimeUiState = BaOfficeRuntimeUiState(),
                settingsDraftUiState = BaOfficeSettingsDraftUiState(),
                notificationDraftUiState = BaOfficeNotificationDraftUiState(),
            )

        val contentState =
            buildBaPageContentState(
                isPageActive = true,
                officeState = BaOfficeController(BaPageSnapshot()).state(),
                routeState = routeState,
                clockState =
                    BaPageClockState(
                        uiNowMs = mutableLongStateOf(0L),
                        uiMinuteMs = mutableLongStateOf(0L),
                    ),
                serverOptions = listOf("CN", "Global", "JP"),
                cafeLevelOptions = listOf(1, 2, 3),
            )

        assertTrue(contentState.baCalendarRefreshing)
        assertTrue(contentState.baPoolRefreshing)
    }
}
