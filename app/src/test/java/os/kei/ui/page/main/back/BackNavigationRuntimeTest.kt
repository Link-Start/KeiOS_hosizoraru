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
    fun `main back pops the route first, then returns to home, then leaves back to the system`() {
        listOf(
            Triple("root pager off home navigates home", 1 to 3, MainBackNavigationAction.NavigateHome),
            Triple("a pushed route pops before the pager moves", 2 to 3, MainBackNavigationAction.PopRoute),
            Triple("home at the root lets the system finish", 1 to 0, MainBackNavigationAction.None),
        ).forEach { (name, state, expected) ->
            val (backStackSize, targetPageIndex) = state
            assertEquals(
                expected,
                resolveMainBackNavigationAction(
                    backStackSize = backStackSize,
                    targetPageIndex = targetPageIndex,
                    homePageIndex = 0,
                ),
                name,
            )
        }
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
                localBackPipeline = PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
                activityBackPipeline = PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
                romFamily = PredictiveBackOemCompat.RomFamily.HyperOs,
            )

        controller.updatePolicy(policy)
        val policyState = controller.state
        controller.updatePolicy(policy)
        assertSame(policyState, controller.state)

        controller.beginGesture(BackNavigationSource.StandaloneRoute)
        val gestureState = controller.state
        controller.beginGesture(BackNavigationSource.StandaloneRoute)
        assertSame(gestureState, controller.state)

        controller.beginCommit(BackNavigationSource.StandaloneRoute)
        val commitState = controller.state
        controller.beginCommit(BackNavigationSource.StandaloneRoute)
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
    fun `local back uses compose predictive back only on a compose pipeline with animations on`() {
        listOf(
            Triple("aosp policy", policy(ComposePredictive, FrameworkFinish, animations = true), true)
                to BackNavigationHandlerMode.ComposePredictive,
            Triple("hyperos policy", policy(CommitOnly, FrameworkFinish, animations = true, rom = HyperOs), true)
                to BackNavigationHandlerMode.CommitOnly,
            Triple("disabled animation setting", policy(CommitOnly, CommitCallback, animations = false), false)
                to BackNavigationHandlerMode.CommitOnly,
        ).forEach { (case, expected) ->
            val (name, policy, transitionAnimationsEnabled) = case
            assertEquals(
                expected,
                resolveBackNavigationHandlerMode(
                    policy = policy,
                    transitionAnimationsEnabled = transitionAnimationsEnabled,
                    predictiveBackAnimationsEnabled = true,
                ),
                name,
            )
        }
    }

    @Test
    fun `the activity root installs a callback only to intercept or when predictive back is off`() {
        data class Case(
            val name: String,
            val policy: PredictiveBackOemCompat.Policy,
            val predictiveBackAnimationsEnabled: Boolean,
            val needsInterception: Boolean,
            val expected: ActivityBackHandlerMode,
        )
        listOf(
            Case(
                "framework finish when nothing intercepts",
                policy(CommitOnly, FrameworkFinish, animations = true, rom = HyperOs),
                predictiveBackAnimationsEnabled = false,
                needsInterception = false,
                expected = ActivityBackHandlerMode.FrameworkFinish,
            ),
            Case(
                "local interception needs the callback",
                policy(ComposePredictive, FrameworkFinish, animations = true),
                predictiveBackAnimationsEnabled = true,
                needsInterception = true,
                expected = ActivityBackHandlerMode.CommitCallback,
            ),
            Case(
                "disabled predictive back setting keeps the callback",
                policy(CommitOnly, CommitCallback, animations = false),
                predictiveBackAnimationsEnabled = false,
                needsInterception = false,
                expected = ActivityBackHandlerMode.CommitCallback,
            ),
        ).forEach { case ->
            val mode =
                resolveActivityBackHandlerMode(
                    policy = case.policy,
                    transitionAnimationsEnabled = true,
                    predictiveBackAnimationsEnabled = case.predictiveBackAnimationsEnabled,
                    needsInterception = case.needsInterception,
                )
            assertEquals(case.expected, mode, case.name)
            assertEquals(
                case.expected == ActivityBackHandlerMode.CommitCallback,
                shouldInstallActivityBackCallback(
                    policy = case.policy,
                    transitionAnimationsEnabled = true,
                    predictiveBackAnimationsEnabled = case.predictiveBackAnimationsEnabled,
                    needsInterception = case.needsInterception,
                ),
                case.name,
            )
        }
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
        val ComposePredictive = PredictiveBackOemCompat.LocalBackPipeline.ComposePredictive
        val CommitOnly = PredictiveBackOemCompat.LocalBackPipeline.CommitOnly
        val FrameworkFinish = PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish
        val CommitCallback = PredictiveBackOemCompat.ActivityBackPipeline.CommitCallback
        val HyperOs = PredictiveBackOemCompat.RomFamily.HyperOs

        fun policy(
            local: PredictiveBackOemCompat.LocalBackPipeline,
            activity: PredictiveBackOemCompat.ActivityBackPipeline,
            animations: Boolean,
            rom: PredictiveBackOemCompat.RomFamily = PredictiveBackOemCompat.RomFamily.Aosp,
        ) = PredictiveBackOemCompat.Policy(
            frameworkAnimationsEnabled = animations,
            localBackPipeline = local,
            activityBackPipeline = activity,
            romFamily = rom,
        )

        val testBackMotionConfig = BackGestureMotionConfig(
            translationFactor = 0.12f,
            contentFadeFactor = 0.2f,
            scrimFadeFactor = 0.3f,
            minScale = 0.9f,
        )
    }
}
