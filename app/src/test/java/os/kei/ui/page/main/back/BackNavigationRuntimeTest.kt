package os.kei.ui.page.main.back

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackNavigationRuntimeTest {
    @Test
    fun `root pager back navigates to home before route pop`() {
        val action = resolveMainBackNavigationAction(
            backStackSize = 1,
            targetPageIndex = 3,
            homePageIndex = 0
        )

        assertEquals(MainBackNavigationAction.NavigateHome, action)
    }

    @Test
    fun `route stack pop has priority over root pager home navigation`() {
        val action = resolveMainBackNavigationAction(
            backStackSize = 2,
            targetPageIndex = 3,
            homePageIndex = 0
        )

        assertEquals(MainBackNavigationAction.PopRoute, action)
    }

    @Test
    fun `home page root lets system handle final back`() {
        val action = resolveMainBackNavigationAction(
            backStackSize = 1,
            targetPageIndex = 0,
            homePageIndex = 0
        )

        assertEquals(MainBackNavigationAction.None, action)
    }

    @Test
    fun `commit gate runs callback once until reset`() {
        val gate = BackNavigationCommitGate()
        var count = 0

        assertTrue(gate.tryCommit { count += 1 })
        assertFalse(gate.tryCommit { count += 1 })
        assertEquals(1, count)

        gate.reset()

        assertTrue(gate.tryCommit { count += 1 })
        assertEquals(2, count)
    }

    @Test
    fun `runtime state blocks content work during gesture or commit`() {
        assertTrue(BackNavigationRuntimeState().contentWorkAllowed)
        assertFalse(
            BackNavigationRuntimeState(
                isGestureInProgress = true,
                source = BackNavigationSource.MainPager
            ).contentWorkAllowed
        )
        assertFalse(
            BackNavigationRuntimeState(
                isCommitRunning = true,
                source = BackNavigationSource.Activity
            ).contentWorkAllowed
        )
    }
}

