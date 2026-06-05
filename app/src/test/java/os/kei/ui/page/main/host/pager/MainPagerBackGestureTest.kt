package os.kei.ui.page.main.host.pager

import org.junit.Test
import kotlin.test.assertEquals

class MainPagerBackGestureTest {
    @Test
    fun `selected position moves from current page to home with gesture progress`() {
        assertEquals(
            expected = 2f,
            actual = resolveMainPagerBackSelectedPosition(
                selectedPageIndex = 4,
                homePageIndex = 0,
                progress = 0.5f,
            ),
        )
    }

    @Test
    fun `selected position clamps gesture progress`() {
        assertEquals(
            expected = 4f,
            actual = resolveMainPagerBackSelectedPosition(
                selectedPageIndex = 4,
                homePageIndex = 0,
                progress = -1f,
            ),
        )
        assertEquals(
            expected = 0f,
            actual = resolveMainPagerBackSelectedPosition(
                selectedPageIndex = 4,
                homePageIndex = 0,
                progress = 2f,
            ),
        )
    }
}
