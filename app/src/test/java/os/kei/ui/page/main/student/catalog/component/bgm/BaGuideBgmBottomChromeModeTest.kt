package os.kei.ui.page.main.student.catalog.component.bgm

import kotlin.test.assertEquals
import org.junit.Test

class BaGuideBgmBottomChromeModeTest {
    @Test
    fun searchInputKeepsFocusAboveEveryDockState() {
        assertEquals(
            BaGuideBgmBottomChromeMode.SearchInput,
            resolveBaGuideBgmBottomChromeMode(
                searchVisible = false,
                searchInputActive = true,
                compact = false
            )
        )
        assertEquals(
            BaGuideBgmBottomChromeMode.SearchInput,
            resolveBaGuideBgmBottomChromeMode(
                searchVisible = true,
                searchInputActive = true,
                compact = true
            )
        )
    }

    @Test
    fun expandedSearchKeepsBaselineDuringCompactScroll() {
        assertEquals(
            BaGuideBgmBottomChromeMode.SearchExpanded,
            resolveBaGuideBgmBottomChromeMode(
                searchVisible = true,
                searchInputActive = false,
                compact = true
            )
        )
    }

    @Test
    fun dockFallsBackToCompactOrExpandedWhenSearchIsClosed() {
        assertEquals(
            BaGuideBgmBottomChromeMode.Compact,
            resolveBaGuideBgmBottomChromeMode(
                searchVisible = false,
                searchInputActive = false,
                compact = true
            )
        )
        assertEquals(
            BaGuideBgmBottomChromeMode.Expanded,
            resolveBaGuideBgmBottomChromeMode(
                searchVisible = false,
                searchInputActive = false,
                compact = false
            )
        )
    }

    @Test
    fun compactDockExpandsForStaticContent() {
        val scrollState = BaGuideBgmBottomChromeScrollState(scrollThresholdPx = 20f)

        scrollState.compact()
        scrollState.expandForStaticContent(canScrollBackward = false, canScrollForward = false)

        assertEquals(false, scrollState.isCompact)
    }

    @Test
    fun compactDockStaysControlledWhenContentCanScroll() {
        val scrollState = BaGuideBgmBottomChromeScrollState(scrollThresholdPx = 20f)

        scrollState.compact()
        scrollState.expandForStaticContent(canScrollBackward = false, canScrollForward = true)

        assertEquals(true, scrollState.isCompact)
    }
}
