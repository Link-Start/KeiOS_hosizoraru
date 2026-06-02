package os.kei.ui.page.main.student.catalog.state

import androidx.compose.runtime.mutableStateOf
import org.junit.Test
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaGuideCatalogFilterSortStateTest {
    @Test
    fun `filter and sort selections stay scoped to each catalog tab`() {
        var snapshot = BaGuideCatalogFilterSortSnapshot()
        var activeTab = BaGuideCatalogTab.Student
        val state =
            BaGuideCatalogFilterSortState(
                snapshot = { snapshot },
                onSnapshotChange = { snapshot = it },
                activeCatalogTab = { activeTab },
                showFilterPopupState = mutableStateOf(false),
            )

        state.selectSortMode(BaGuideCatalogSortMode.GlobalScoreDesc)
        state.toggleFilterOption(filterId = 68, optionId = 176)
        activeTab = BaGuideCatalogTab.NpcSatellite

        assertEquals(BaGuideCatalogSortMode.Default, state.sortMode)
        assertTrue(state.selectedFilterOptions.isEmpty())

        state.selectSortMode(BaGuideCatalogSortMode.ReleaseDateDesc)
        state.toggleFilterOption(filterId = 12034, optionId = 12035)
        activeTab = BaGuideCatalogTab.Student

        assertEquals(BaGuideCatalogSortMode.GlobalScoreDesc, state.sortMode)
        assertEquals(mapOf(68 to setOf(176)), state.selectedFilterOptions)
        assertEquals(
            BaGuideCatalogSortMode.ReleaseDateDesc,
            state.sortModeFor(BaGuideCatalogTab.NpcSatellite),
        )
        assertEquals(
            mapOf(12034 to setOf(12035)),
            state.selectedFilterOptionsFor(BaGuideCatalogTab.NpcSatellite),
        )
    }
}
