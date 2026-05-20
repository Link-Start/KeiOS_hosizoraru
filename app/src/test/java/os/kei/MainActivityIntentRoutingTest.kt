package os.kei

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainActivityIntentRoutingTest {
    @Test
    fun `valid github shortcut route is preserved`() {
        val route = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            rawMcpServerAction = null,
            rawShortcutAction = MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_GITHUB, route?.targetBottomPage)
        assertEquals(MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED, route?.shortcutAction)
        assertNull(route?.mcpServerAction)
    }

    @Test
    fun `valid github actions track route is preserved`() {
        val trackId = "open-ani/animeko|me.him188.ani"
        val route = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            rawMcpServerAction = null,
            rawShortcutAction = null,
            rawGitHubActionsTrackId = "  $trackId  "
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_GITHUB, route?.targetBottomPage)
        assertEquals(trackId, route?.githubActionsTrackId)
        assertNull(route?.shortcutAction)
        assertNull(route?.mcpServerAction)
    }

    @Test
    fun `github actions track route is dropped outside github`() {
        val route = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            rawMcpServerAction = null,
            rawShortcutAction = null,
            rawGitHubActionsTrackId = "open-ani/animeko|me.him188.ani"
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_MCP, route?.targetBottomPage)
        assertNull(route?.githubActionsTrackId)
    }

    @Test
    fun `mismatched shortcut action is dropped`() {
        val route = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            rawMcpServerAction = null,
            rawShortcutAction = MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_MCP, route?.targetBottomPage)
        assertNull(route?.shortcutAction)
    }

    @Test
    fun `valid mcp action is preserved only on mcp target`() {
        val route = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            rawMcpServerAction = MainActivity.MCP_SERVER_ACTION_TOGGLE,
            rawShortcutAction = null
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_MCP, route?.targetBottomPage)
        assertEquals(MainActivity.MCP_SERVER_ACTION_TOGGLE, route?.mcpServerAction)
    }

    @Test
    fun `valid ba bgm playback shortcut route is preserved`() {
        val route = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_BA,
            rawMcpServerAction = null,
            rawShortcutAction = MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_BA, route?.targetBottomPage)
        assertEquals(MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK, route?.shortcutAction)
        assertNull(route?.mcpServerAction)
    }

    @Test
    fun `valid os target route is preserved`() {
        val route = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = MainActivity.TARGET_BOTTOM_PAGE_OS,
            rawMcpServerAction = null,
            rawShortcutAction = null
        )

        assertEquals(MainActivity.TARGET_BOTTOM_PAGE_OS, route?.targetBottomPage)
        assertNull(route?.shortcutAction)
        assertNull(route?.mcpServerAction)
    }

    @Test
    fun `unknown target is rejected`() {
        val route = MainActivityIntentRouting.sanitize(
            rawTargetBottomPage = "Settings",
            rawMcpServerAction = MainActivity.MCP_SERVER_ACTION_TOGGLE,
            rawShortcutAction = MainActivity.SHORTCUT_ACTION_BA_AP_ISLAND
        )

        assertNull(route)
    }
}
