package os.kei.ui.page.main.back

import androidx.activity.BackEventCompat
import org.junit.Test
import os.kei.core.platform.PredictiveBackOemCompat
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
    fun `runtime controller keeps repeated lifecycle writes idempotent`() {
        val controller = BackNavigationRuntimeController()
        val policy =
            PredictiveBackOemCompat.Policy(
                frameworkAnimationsEnabled = true,
                popDirectionFollowsSwipeEdge = true,
                routeBackPipeline = PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
                localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
                activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
                romFamily = PredictiveBackOemCompat.RomFamily.HyperOs,
            )

        controller.updatePolicy(policy)
        val policyState = controller.state
        controller.updatePolicy(policy)
        assertSame(policyState, controller.state)

        controller.beginGesture(BackNavigationSource.MainRoute)
        val gestureState = controller.state
        controller.beginGesture(BackNavigationSource.MainRoute)
        assertSame(gestureState, controller.state)

        controller.beginCommit(BackNavigationSource.MainRoute)
        val commitState = controller.state
        controller.beginCommit(BackNavigationSource.MainRoute)
        assertSame(commitState, controller.state)

        controller.reset()
        val idleState = controller.state
        controller.reset()
        assertSame(idleState, controller.state)
    }

    @Test
    fun `runtime accepts navigation event progress without edge data`() {
        val controller = BackNavigationRuntimeController()

        controller.updateGestureProgress(
            progress = 1.5f,
            source = BackNavigationSource.Modal
        )

        assertTrue(controller.state.isGestureInProgress)
        assertEquals(BackNavigationSource.Modal, controller.state.source)
        assertEquals(1f, controller.state.progress)
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

    @Test
    fun `back gesture motion clamps progress and follows left edge`() {
        val motion = resolveBackGestureMotion(
            progress = 1.5f,
            containerWidthPx = 1000,
            containerHeightPx = 2000,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            touchY = 120f,
            config = testBackMotionConfig,
        )

        assertEquals(1f, motion.progress)
        assertEquals(120f, motion.translationX, absoluteTolerance = 0.001f)
        assertEquals(0.9f, motion.scale, absoluteTolerance = 0.001f)
        assertEquals(0.8f, motion.contentAlpha, absoluteTolerance = 0.001f)
        assertEquals(0.7f, motion.scrimAlpha, absoluteTolerance = 0.001f)
        assertEquals(0.84f, motion.pivotX, absoluteTolerance = 0.001f)
        assertEquals(0.14f, motion.pivotY, absoluteTolerance = 0.001f)
    }

    @Test
    fun `back gesture motion reverses translation for right edge`() {
        val motion = resolveBackGestureMotion(
            progress = 0.5f,
            containerWidthPx = 800,
            containerHeightPx = 1600,
            swipeEdge = BackEventCompat.EDGE_RIGHT,
            touchY = 800f,
            config = testBackMotionConfig,
        )

        assertEquals(-48f, motion.translationX, absoluteTolerance = 0.001f)
        assertTrue(motion.scale < 0.95f)
        assertTrue(motion.contentAlpha < 0.9f)
        assertTrue(motion.scrimAlpha < 0.85f)
        assertEquals(0.16f, motion.pivotX, absoluteTolerance = 0.001f)
        assertEquals(0.5f, motion.pivotY, absoluteTolerance = 0.001f)
    }

    @Test
    fun `back gesture settle duration scales with remaining distance`() {
        assertEquals(
            250,
            resolveBackGestureSettleDurationMillis(
                currentProgress = 0.5f,
                targetProgress = 1f,
                maxDurationMillis = BACK_GESTURE_COMMIT_SETTLE_DURATION_MS,
            ),
        )
        assertEquals(
            128,
            resolveBackGestureSettleDurationMillis(
                currentProgress = 0.96f,
                targetProgress = 1f,
                maxDurationMillis = BACK_GESTURE_COMMIT_SETTLE_DURATION_MS,
            ),
        )
        assertEquals(
            0,
            resolveBackGestureSettleDurationMillis(
                currentProgress = 1f,
                targetProgress = 1f,
                maxDurationMillis = BACK_GESTURE_COMMIT_SETTLE_DURATION_MS,
            ),
        )
    }

    private companion object {
        val testBackMotionConfig = BackGestureMotionConfig(
            translationFactor = 0.12f,
            contentFadeFactor = 0.2f,
            scrimFadeFactor = 0.3f,
            minScale = 0.9f,
        )
    }
}
