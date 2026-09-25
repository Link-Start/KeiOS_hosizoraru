package os.kei.ui.page.main.student.catalog.component.bgm

import kotlin.test.assertEquals
import org.junit.Test

class BaGuideBgmBottomChromeModeTest {
    @Test
    fun modeFollowsSearchInputThenSearchThenCompact() {
        data class Case(
            val label: String,
            val searchVisible: Boolean,
            val searchInputActive: Boolean,
            val compact: Boolean,
            val expected: BaGuideBgmBottomChromeMode,
        )
        listOf(
            Case("input active, search closed", false, true, false, BaGuideBgmBottomChromeMode.SearchInput),
            Case("expanded search during compact scroll", true, false, true, BaGuideBgmBottomChromeMode.SearchExpanded),
        ).forEach { case ->
            assertEquals(
                case.expected,
                BaGuideBgmChromePresentationDeriver
                    .derive(
                        searchVisible = case.searchVisible,
                        searchInputActive = case.searchInputActive,
                        compact = case.compact,
                    ).mode,
                case.label,
            )
        }
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
