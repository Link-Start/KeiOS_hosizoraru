package os.kei.ui.page.main.about.page

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import os.kei.ui.page.main.widget.chrome.tabbedPageCollapsedDockWidth
import os.kei.ui.page.main.widget.chrome.tabbedPageExpandedSearchWidth

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
}
