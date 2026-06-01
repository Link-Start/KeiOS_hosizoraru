package os.kei.ui.page.main.about.page

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import os.kei.ui.page.main.widget.chrome.TabbedPageBottomChromeSearchDockVisibleAlpha
import os.kei.ui.page.main.widget.chrome.tabbedPageCollapsedDockWidth
import os.kei.ui.page.main.widget.chrome.tabbedPageExpandedSearchWidth
import os.kei.ui.page.main.widget.chrome.tabbedPageSearchDockTargetWidth
import os.kei.ui.page.main.widget.chrome.tabbedPageSearchDockTargetX

class AboutBottomChromeLayoutTest {
    @Test
    fun expandedSearchUsesRemainingWidthAfterCompactDock() {
        assertEquals(
            300.dp,
            tabbedPageExpandedSearchWidth(
                availableWidth = 370.dp,
                compactDockWidth = 62.dp,
                gap = 8.dp,
            )
        )
    }

    @Test
    fun expandedSearchKeepsMinimumWidthOnNarrowSurfaces() {
        assertEquals(
            196.dp,
            tabbedPageExpandedSearchWidth(
                availableWidth = 220.dp,
                compactDockWidth = 62.dp,
                gap = 8.dp,
            )
        )
    }

    @Test
    fun collapsedDockUsesSpaceBeforeSearchButton() {
        assertEquals(
            300.dp,
            tabbedPageCollapsedDockWidth(
                availableWidth = 370.dp,
                searchDockWidth = 62.dp,
                gap = 8.dp,
            )
        )
    }

    @Test
    fun collapsedChromeKeepsSearchDockVisible() {
        assertEquals(1f, TabbedPageBottomChromeSearchDockVisibleAlpha)
    }

    @Test
    fun collapsedChromeKeepsSearchDockAsButton() {
        assertEquals(
            62.dp,
            tabbedPageSearchDockTargetWidth(
                searchExpanded = false,
                expandedSearchWidth = 300.dp,
                size = 62.dp,
            )
        )
        assertEquals(
            308.dp,
            tabbedPageSearchDockTargetX(
                searchExpanded = false,
                collapsedDockWidth = 300.dp,
                size = 62.dp,
                gap = 8.dp,
            )
        )
    }

    @Test
    fun expandedSearchDockStartsAfterCompactCategoryButton() {
        assertEquals(
            300.dp,
            tabbedPageSearchDockTargetWidth(
                searchExpanded = true,
                expandedSearchWidth = 300.dp,
                size = 62.dp,
            )
        )
        assertEquals(
            70.dp,
            tabbedPageSearchDockTargetX(
                searchExpanded = true,
                collapsedDockWidth = 300.dp,
                size = 62.dp,
                gap = 8.dp,
            )
        )
    }
}
