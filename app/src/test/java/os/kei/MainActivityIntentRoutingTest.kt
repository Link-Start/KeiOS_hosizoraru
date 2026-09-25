package os.kei

import org.junit.Test
import kotlin.test.assertEquals

class MainActivityIntentRoutingTest {
    private data class Case(
        val name: String,
        val target: String?,
        val mcpAction: String? = null,
        val shortcutAction: String? = null,
        val targetRoute: String? = null,
        val githubActionsTrackId: String? = null,
        val baAccountId: String? = null,
        val expected: MainActivityIntentRoute?
    )

    private fun route(
        target: String,
        targetRoute: String? = null,
        mcpServerAction: String? = null,
        shortcutAction: String? = null,
        githubActionsTrackId: String? = null,
        baAccountId: String? = null
    ) = MainActivityIntentRoute(
        targetBottomPage = target,
        targetRoute = targetRoute,
        mcpServerAction = mcpServerAction,
        shortcutAction = shortcutAction,
        githubActionsTrackId = githubActionsTrackId,
        baAccountId = baAccountId
    )

    private val trackId = "open-ani/animeko|me.him188.ani"

    private val cases = listOf(
        Case(
            name = "valid github shortcut route is preserved",
            target = MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            shortcutAction = MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED,
            expected = route(
                MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
                shortcutAction = MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED
            )
        ),
        Case(
            name = "valid github actions track route is preserved and trimmed",
            target = MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
            githubActionsTrackId = "  $trackId  ",
            expected = route(MainActivity.TARGET_BOTTOM_PAGE_GITHUB, githubActionsTrackId = trackId)
        ),
        Case(
            name = "github actions track route is dropped outside github",
            target = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            githubActionsTrackId = trackId,
            expected = route(MainActivity.TARGET_BOTTOM_PAGE_MCP)
        ),
        Case(
            name = "mismatched shortcut action is dropped",
            target = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            shortcutAction = MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED,
            expected = route(MainActivity.TARGET_BOTTOM_PAGE_MCP)
        ),
        Case(
            name = "valid mcp action is preserved on mcp target",
            target = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            mcpAction = MainActivity.MCP_SERVER_ACTION_TOGGLE,
            expected = route(
                MainActivity.TARGET_BOTTOM_PAGE_MCP,
                mcpServerAction = MainActivity.MCP_SERVER_ACTION_TOGGLE
            )
        ),
        Case(
            name = "valid ba bgm playback shortcut route is preserved",
            target = MainActivity.TARGET_BOTTOM_PAGE_BA,
            shortcutAction = MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK,
            expected = route(
                MainActivity.TARGET_BOTTOM_PAGE_BA,
                shortcutAction = MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK
            )
        ),
        Case(
            name = "ba account id is preserved and trimmed on ba target",
            target = MainActivity.TARGET_BOTTOM_PAGE_BA,
            baAccountId = "  cn-alt  ",
            expected = route(MainActivity.TARGET_BOTTOM_PAGE_BA, baAccountId = "cn-alt")
        ),
        Case(
            name = "ba account id is dropped outside ba target",
            target = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            baAccountId = "cn-alt",
            expected = route(MainActivity.TARGET_BOTTOM_PAGE_MCP)
        ),
        Case(
            name = "valid os target route is preserved",
            target = MainActivity.TARGET_BOTTOM_PAGE_OS,
            expected = route(MainActivity.TARGET_BOTTOM_PAGE_OS)
        ),
        Case(
            name = "valid webdav target route is preserved and trimmed",
            target = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            targetRoute = "  ${MainActivity.TARGET_ROUTE_WEBDAV_SYNC}  ",
            expected = route(
                MainActivity.TARGET_BOTTOM_PAGE_MCP,
                targetRoute = MainActivity.TARGET_ROUTE_WEBDAV_SYNC
            )
        ),
        Case(
            name = "unknown target route is dropped",
            target = MainActivity.TARGET_BOTTOM_PAGE_MCP,
            targetRoute = "Settings",
            expected = route(MainActivity.TARGET_BOTTOM_PAGE_MCP)
        ),
        Case(
            name = "unknown target is rejected",
            target = "Settings",
            mcpAction = MainActivity.MCP_SERVER_ACTION_TOGGLE,
            shortcutAction = MainActivity.SHORTCUT_ACTION_BA_AP_ISLAND,
            expected = null
        )
    )

    @Test
    fun `raw intent extras are sanitised into a route`() {
        cases.forEach { case ->
            val actual = MainActivityIntentRouting.sanitize(
                rawTargetBottomPage = case.target,
                rawMcpServerAction = case.mcpAction,
                rawShortcutAction = case.shortcutAction,
                rawTargetRoute = case.targetRoute,
                rawGitHubActionsTrackId = case.githubActionsTrackId,
                rawBaAccountId = case.baAccountId
            )
            assertEquals(case.expected, actual, case.name)
        }
    }
}
