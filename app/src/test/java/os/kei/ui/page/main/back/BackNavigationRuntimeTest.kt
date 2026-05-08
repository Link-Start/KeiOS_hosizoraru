package os.kei.ui.page.main.back

import org.junit.Test
import os.kei.core.platform.PredictiveBackOemCompat
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

    @Test
    fun `aosp policy enables compose predictive local back`() {
        val mode = resolveBackNavigationHandlerMode(
            policy = PredictiveBackOemCompat.Policy(
                frameworkAnimationsEnabled = true,
                popDirectionFollowsSwipeEdge = false,
                routeBackPipeline = PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
                localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.ComposePredictive,
                activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
                romFamily = PredictiveBackOemCompat.RomFamily.Aosp
            ),
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true
        )

        assertEquals(BackNavigationHandlerMode.ComposePredictive, mode)
    }

    @Test
    fun `hyperos policy keeps local back commit only`() {
        val mode = resolveBackNavigationHandlerMode(
            policy = PredictiveBackOemCompat.Policy(
                frameworkAnimationsEnabled = true,
                popDirectionFollowsSwipeEdge = true,
                routeBackPipeline = PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
                localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
                activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
                romFamily = PredictiveBackOemCompat.RomFamily.HyperOs
            ),
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true
        )

        assertEquals(BackNavigationHandlerMode.CommitOnly, mode)
    }

    @Test
    fun `disabled animation setting keeps local back commit only`() {
        val mode = resolveBackNavigationHandlerMode(
            policy = PredictiveBackOemCompat.Policy(
                frameworkAnimationsEnabled = false,
                popDirectionFollowsSwipeEdge = false,
                routeBackPipeline = PredictiveBackOemCompat.RouteBackPipeline.CommitOnly,
                localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
                activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.CommitCallback,
                romFamily = PredictiveBackOemCompat.RomFamily.Aosp
            ),
            transitionAnimationsEnabled = false,
            predictiveBackAnimationsEnabled = true
        )

        assertEquals(BackNavigationHandlerMode.CommitOnly, mode)
    }

    @Test
    fun `activity root uses framework finish when predictive back is enabled`() {
        val mode = resolveActivityBackHandlerMode(
            policy = PredictiveBackOemCompat.Policy(
                frameworkAnimationsEnabled = true,
                popDirectionFollowsSwipeEdge = true,
                routeBackPipeline = PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
                localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
                activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
                romFamily = PredictiveBackOemCompat.RomFamily.HyperOs
            ),
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = false,
            needsInterception = false
        )

        assertEquals(ActivityBackHandlerMode.FrameworkFinish, mode)
        assertFalse(
            shouldInstallActivityBackCallback(
                policy = PredictiveBackOemCompat.Policy(
                    frameworkAnimationsEnabled = true,
                    popDirectionFollowsSwipeEdge = true,
                    routeBackPipeline = PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
                    localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
                    activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
                    romFamily = PredictiveBackOemCompat.RomFamily.HyperOs
                ),
                transitionAnimationsEnabled = true,
                predictiveBackAnimationsEnabled = false,
                needsInterception = false
            )
        )
    }

    @Test
    fun `activity root installs callback for local interception`() {
        val policy = PredictiveBackOemCompat.Policy(
            frameworkAnimationsEnabled = true,
            popDirectionFollowsSwipeEdge = false,
            routeBackPipeline = PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
            localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.ComposePredictive,
            activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
            romFamily = PredictiveBackOemCompat.RomFamily.Aosp
        )

        assertEquals(
            ActivityBackHandlerMode.CommitCallback,
            resolveActivityBackHandlerMode(
                policy = policy,
                transitionAnimationsEnabled = true,
                predictiveBackAnimationsEnabled = true,
                needsInterception = true
            )
        )
        assertTrue(
            shouldInstallActivityBackCallback(
                policy = policy,
                transitionAnimationsEnabled = true,
                predictiveBackAnimationsEnabled = true,
                needsInterception = true
            )
        )
    }

    @Test
    fun `disabled predictive back setting keeps activity root callback`() {
        val policy = PredictiveBackOemCompat.Policy(
            frameworkAnimationsEnabled = false,
            popDirectionFollowsSwipeEdge = false,
            routeBackPipeline = PredictiveBackOemCompat.RouteBackPipeline.CommitOnly,
            localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
            activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.CommitCallback,
            romFamily = PredictiveBackOemCompat.RomFamily.Aosp
        )

        assertEquals(
            ActivityBackHandlerMode.CommitCallback,
            resolveActivityBackHandlerMode(
                policy = policy,
                transitionAnimationsEnabled = true,
                predictiveBackAnimationsEnabled = false,
                needsInterception = false
            )
        )
        assertTrue(
            shouldInstallActivityBackCallback(
                policy = policy,
                transitionAnimationsEnabled = true,
                predictiveBackAnimationsEnabled = false,
                needsInterception = false
            )
        )
    }
}
