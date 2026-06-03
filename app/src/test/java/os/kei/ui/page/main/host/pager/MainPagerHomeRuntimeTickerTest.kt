package os.kei.ui.page.main.host.pager

import org.junit.Test
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainPagerHomeRuntimeTickerTest {
    @Test
    fun `ticker request ignores visual runtime fields`() {
        val overview =
            HomeMcpOverview(
                running = true,
                runningSinceEpochMs = 1_000L,
            )
        val baseRuntime =
            MainPageRuntime(
                isWarmActive = true,
                isDataActive = true,
                bottomBarVisible = true,
                floatingDockSide = AppFloatingDockSide.Start,
            )
        val visualChangedRuntime =
            baseRuntime.copy(
                bottomBarVisible = false,
                floatingDockSide = AppFloatingDockSide.End,
            )

        assertEquals(
            buildMainPagerHomeRuntimeTickerRequest(
                mcpOverview = overview,
                githubOverview = HomeGitHubOverview(),
                runtime = baseRuntime,
            ),
            buildMainPagerHomeRuntimeTickerRequest(
                mcpOverview = overview,
                githubOverview = HomeGitHubOverview(),
                runtime = visualChangedRuntime,
            ),
        )
    }

    @Test
    fun `ticker request ticks for active runtime sources`() {
        val runningOverview =
            HomeMcpOverview(
                running = true,
                runningSinceEpochMs = 1_000L,
            )
        val inactiveRuntime =
            MainPageRuntime(
                isWarmActive = true,
                isDataActive = false,
            )
        val activeRuntime =
            inactiveRuntime.copy(isDataActive = true)

        assertFalse(
            buildMainPagerHomeRuntimeTickerRequest(
                mcpOverview = runningOverview,
                githubOverview = HomeGitHubOverview(cachedRefreshMs = 1_000L),
                runtime = inactiveRuntime,
            ).shouldTick,
        )
        assertTrue(
            buildMainPagerHomeRuntimeTickerRequest(
                mcpOverview = runningOverview,
                githubOverview = HomeGitHubOverview(),
                runtime = activeRuntime,
            ).shouldTick,
        )
        assertTrue(
            buildMainPagerHomeRuntimeTickerRequest(
                mcpOverview = HomeMcpOverview(),
                githubOverview = HomeGitHubOverview(cachedRefreshMs = 1_000L),
                runtime = activeRuntime,
            ).shouldTick,
        )
        assertTrue(
            buildMainPagerHomeRuntimeTickerRequest(
                mcpOverview = HomeMcpOverview(),
                githubOverview = HomeGitHubOverview(refreshing = true),
                runtime = activeRuntime,
            ).shouldTick,
        )
    }
}
