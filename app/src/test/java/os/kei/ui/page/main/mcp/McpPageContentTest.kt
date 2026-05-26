package os.kei.ui.page.main.mcp

import org.junit.Test
import os.kei.ui.page.main.host.pager.MainPageRuntime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpPageContentTest {
    @Test
    fun `heavy content waits while mcp is pager target`() {
        val runtime =
            MainPageRuntime(
                contentReady = true,
                isDataActive = false,
                isPagerScrollInProgress = true,
            )

        assertFalse(shouldRenderMcpHeavyContent(runtime))
    }

    @Test
    fun `heavy content renders when mcp is settled`() {
        val runtime =
            MainPageRuntime(
                contentReady = true,
                isDataActive = true,
                isPagerScrollInProgress = true,
            )

        assertTrue(shouldRenderMcpHeavyContent(runtime))
    }

    @Test
    fun `heavy content waits for activation frame`() {
        val runtime =
            MainPageRuntime(
                contentReady = false,
                isDataActive = true,
                isPagerScrollInProgress = false,
            )

        assertFalse(shouldRenderMcpHeavyContent(runtime))
    }
}
