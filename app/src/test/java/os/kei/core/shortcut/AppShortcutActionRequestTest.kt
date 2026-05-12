package os.kei.core.shortcut

import org.junit.Test
import os.kei.MainActivity
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppShortcutActionRequestTest {
    @Test
    fun `github launcher shortcut request is preserved without main activity routing`() {
        val request = AppShortcutActionRequest.fromRaw(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            rawMcpServerAction = null,
            rawShortcutAction = MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_GITHUB, request?.targetBottomPage)
        assertEquals(MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED, request?.shortcutAction)
        assertNull(request?.mcpServerAction)
    }

    @Test
    fun `mismatched launcher shortcut action is dropped`() {
        val request = AppShortcutActionRequest.fromRaw(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            rawMcpServerAction = null,
            rawShortcutAction = MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_MCP, request?.targetBottomPage)
        assertNull(request?.shortcutAction)
        assertNull(request?.mcpServerAction)
    }
}
