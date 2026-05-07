package os.kei.ui.page.main.host.pager

import org.junit.Test
import kotlin.test.assertEquals

class MainLoadedPagerStateTest {
    @Test
    fun `initial page resolver keeps saved page identity after page list shrinks`() {
        val resolved = resolveMainLoadedPagerInitialPage(
            pageKeys = listOf("Home", "GitHub", "Ba"),
            initialPage = 3,
            savedPageKey = "Ba"
        )

        assertEquals(2, resolved)
    }

    @Test
    fun `initial page resolver falls back to requested page when saved key is missing`() {
        val resolved = resolveMainLoadedPagerInitialPage(
            pageKeys = listOf("Home", "GitHub"),
            initialPage = 1,
            savedPageKey = "Ba"
        )

        assertEquals(1, resolved)
    }

    @Test
    fun `initial page resolver handles empty page list`() {
        val resolved = resolveMainLoadedPagerInitialPage(
            pageKeys = emptyList(),
            initialPage = 4,
            savedPageKey = "GitHub"
        )

        assertEquals(0, resolved)
    }
}
