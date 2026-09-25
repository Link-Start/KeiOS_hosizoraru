package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaCalendarPoolContentStateTest {
    @Test
    fun `content status follows cached entries, refresh and error`() {
        data class Case(
            val label: String,
            val visibleEntryCount: Int,
            val refreshing: Boolean,
            val error: String?,
            val expected: BaCalendarPoolContentStatus?,
        )
        val cases =
            listOf(
                Case("sync error over cached entries surfaces the error", 3, false, "cached", BaCalendarPoolContentStatus.Error),
                Case("refresh with cached entries shows a refresh notice", 2, true, null, BaCalendarPoolContentStatus.Refreshing),
                Case("refresh without entries keeps the loading skeleton", 0, true, null, BaCalendarPoolContentStatus.Loading),
                Case("idle cached entries render with no notice", 2, false, null, null),
            )
        for (case in cases) {
            val status =
                resolveBaCalendarPoolContentStatus(
                    visibleEntryCount = case.visibleEntryCount,
                    loading = false,
                    refreshing = case.refreshing,
                    error = case.error,
                )
            assertEquals(case.expected, status, case.label)
        }
    }

    @Test
    fun `ba page content state preserves calendar and pool refreshing flags`() {
        val routeState =
            testBaPageRouteState(
                calendarUiState = BaCalendarUiState(loading = false, refreshing = true),
                poolUiState = BaPoolUiState(loading = false, refreshing = true),
            )

        val contentState =
            buildBaPageContentState(
                officeState = BaOfficeController(BaPageSnapshot()).state(),
                routeState = routeState,
                clockState = testBaPageClockState(),
                serverOptions = listOf("CN", "Global", "JP"),
                cafeLevelOptions = listOf(1, 2, 3),
            )

        assertTrue(contentState.baCalendarRefreshing)
        assertTrue(contentState.baPoolRefreshing)
    }
}
