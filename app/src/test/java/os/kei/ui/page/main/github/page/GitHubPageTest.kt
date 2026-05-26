package os.kei.ui.page.main.github.page

import org.junit.Test
import os.kei.ui.page.main.host.pager.MainPageRuntime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubPageTest {
    @Test
    fun `heavy content waits while github is pager target`() {
        val runtime =
            MainPageRuntime(
                contentReady = true,
                isDataActive = false,
                isPagerScrollInProgress = true,
            )

        assertFalse(shouldRenderGitHubHeavyContent(runtime))
    }

    @Test
    fun `heavy content renders while github is pager source`() {
        val runtime =
            MainPageRuntime(
                contentReady = true,
                isDataActive = true,
                isPagerScrollInProgress = true,
            )

        assertTrue(shouldRenderGitHubHeavyContent(runtime))
    }

    @Test
    fun `heavy content waits for ready page`() {
        val runtime =
            MainPageRuntime(
                contentReady = false,
                isDataActive = true,
                isPagerScrollInProgress = false,
            )

        assertFalse(shouldRenderGitHubHeavyContent(runtime))
    }
}
