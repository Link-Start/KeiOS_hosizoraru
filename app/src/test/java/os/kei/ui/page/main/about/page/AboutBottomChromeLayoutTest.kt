package os.kei.ui.page.main.about.page

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class AboutBottomChromeLayoutTest {
    @Test
    fun expandedSearchUsesRemainingWidthAfterCompactDock() {
        assertEquals(
            300.dp,
            aboutExpandedSearchWidth(
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
            aboutExpandedSearchWidth(
                availableWidth = 220.dp,
                compactDockWidth = 62.dp,
                gap = 8.dp,
            )
        )
    }
}
