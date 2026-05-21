package os.kei.ui.page.main.host.pager

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeHdrEffectGateTest {
    @Test
    fun `gate activates only when Home is settled and idle`() {
        assertTrue(
            shouldActivateHomeHdrEffect(
                homeIconHdrEnabled = true,
                transitionAnimationsEnabled = true,
                mainRouteActive = true,
                homeIndex = 0,
                settledPage = 0,
                pagerScrollInProgress = false,
                navigationActive = false,
            ),
        )
    }

    @Test
    fun `gate stays inactive during unsafe states`() {
        val activeCase =
            HdrGateCase(
                homeIconHdrEnabled = true,
                transitionAnimationsEnabled = true,
                mainRouteActive = true,
                homeIndex = 0,
                settledPage = 0,
                pagerScrollInProgress = false,
                navigationActive = false,
            )
        listOf(
            activeCase.copy(homeIconHdrEnabled = false),
            activeCase.copy(transitionAnimationsEnabled = false),
            activeCase.copy(mainRouteActive = false),
            activeCase.copy(homeIndex = -1),
            activeCase.copy(settledPage = 1),
            activeCase.copy(pagerScrollInProgress = true),
            activeCase.copy(navigationActive = true),
        ).forEach { case ->
            assertFalse(
                shouldActivateHomeHdrEffect(
                    homeIconHdrEnabled = case.homeIconHdrEnabled,
                    transitionAnimationsEnabled = case.transitionAnimationsEnabled,
                    mainRouteActive = case.mainRouteActive,
                    homeIndex = case.homeIndex,
                    settledPage = case.settledPage,
                    pagerScrollInProgress = case.pagerScrollInProgress,
                    navigationActive = case.navigationActive,
                ),
            )
        }
    }
}

private data class HdrGateCase(
    val homeIconHdrEnabled: Boolean,
    val transitionAnimationsEnabled: Boolean,
    val mainRouteActive: Boolean,
    val homeIndex: Int,
    val settledPage: Int,
    val pagerScrollInProgress: Boolean,
    val navigationActive: Boolean,
)
