package os.kei.ui.page.main.host.pager

import org.junit.Test
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
            buildMainPagerHomeRuntimeTickerRequest(overview, baseRuntime),
            buildMainPagerHomeRuntimeTickerRequest(overview, visualChangedRuntime),
        )
    }

    @Test
    fun `ticker request only ticks for active running server`() {
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

        assertFalse(buildMainPagerHomeRuntimeTickerRequest(runningOverview, inactiveRuntime).shouldTick)
        assertTrue(buildMainPagerHomeRuntimeTickerRequest(runningOverview, activeRuntime).shouldTick)
    }
}
