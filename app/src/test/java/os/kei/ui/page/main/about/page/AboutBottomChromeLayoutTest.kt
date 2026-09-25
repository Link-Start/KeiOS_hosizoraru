package os.kei.ui.page.main.about.page

import androidx.compose.ui.unit.dp
import org.junit.Test
import os.kei.ui.page.main.widget.chrome.TabbedPageCompactDockAction
import os.kei.ui.page.main.widget.chrome.tabbedPageChromeVisible
import os.kei.ui.page.main.widget.chrome.tabbedPageCompactDockAction
import os.kei.ui.page.main.widget.chrome.tabbedPageExpandedSearchWidth
import os.kei.ui.page.main.widget.chrome.tabbedPageUsesCompactHeightDock
import kotlin.test.assertEquals

class AboutBottomChromeLayoutTest {
    @Test
    fun expandedSearchKeepsItsRightEdgeInsideNarrowSurfaces() {
        val availableWidth = 220.dp
        val compactDockWidth = 62.dp
        val gap = 8.dp
        val searchWidth =
            tabbedPageExpandedSearchWidth(
                availableWidth = availableWidth,
                compactDockWidth = compactDockWidth,
                gap = gap,
            )

        assertEquals(
            150.dp,
            searchWidth,
        )
        assertEquals(availableWidth, compactDockWidth + gap + searchWidth)
    }

    @Test
    fun onlyCompactLandscapeStartsWithTheSelectedCategoryDock() {
        listOf(
            Triple("compact landscape", 952.dp to 426.dp, true),
            Triple("portrait phone", 411.dp to 891.dp, false),
            Triple("tall landscape", 952.dp to 600.dp, false),
        ).forEach { (name, size, expected) ->
            assertEquals(
                expected,
                tabbedPageUsesCompactHeightDock(availableWidth = size.first, availableHeight = size.second),
                name,
            )
        }
        listOf(
            Triple("compact height, dock collapsed", true to false, false),
            Triple("compact height, dock expanded", true to true, true),
            Triple("regular height", false to false, true),
        ).forEach { (name, state, expected) ->
            assertEquals(
                expected,
                tabbedPageChromeVisible(
                    visible = true,
                    compactHeightPresentation = state.first,
                    compactHeightDockExpanded = state.second,
                ),
                name,
            )
        }
    }

    @Test
    fun compactDockClosesSearchBeforeExpandingOrShowingCategories() {
        assertEquals(
            TabbedPageCompactDockAction.CloseSearch,
            tabbedPageCompactDockAction(
                searchExpanded = true,
                compactHeightPresentation = true,
            ),
        )
        assertEquals(
            TabbedPageCompactDockAction.CloseSearch,
            tabbedPageCompactDockAction(
                searchExpanded = true,
                compactHeightPresentation = false,
            ),
        )
        assertEquals(
            TabbedPageCompactDockAction.ExpandCompactHeightDock,
            tabbedPageCompactDockAction(
                searchExpanded = false,
                compactHeightPresentation = true,
            ),
        )
        assertEquals(
            TabbedPageCompactDockAction.ShowDock,
            tabbedPageCompactDockAction(
                searchExpanded = false,
                compactHeightPresentation = false,
            ),
        )
    }
}
