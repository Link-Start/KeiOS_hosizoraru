package os.kei.ui.page.main.os

import org.junit.Test
import os.kei.ui.page.main.host.pager.MainPageRuntime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsPageTest {
    @Test
    fun `heavy content waits while os is pager target`() {
        val runtime =
            MainPageRuntime(
                contentReady = true,
                isDataActive = false,
                isPagerScrollInProgress = true,
            )

        assertFalse(shouldRenderOsHeavyContent(runtime))
    }

    @Test
    fun `heavy content renders while os is pager source`() {
        val runtime =
            MainPageRuntime(
                contentReady = true,
                isDataActive = true,
                isPagerScrollInProgress = true,
            )

        assertTrue(shouldRenderOsHeavyContent(runtime))
    }

    @Test
    fun `heavy content waits for ready page`() {
        val runtime =
            MainPageRuntime(
                contentReady = false,
                isDataActive = true,
                isPagerScrollInProgress = false,
            )

        assertFalse(shouldRenderOsHeavyContent(runtime))
    }
}
