package os.kei.ui.page.main.widget.glass

import android.view.Surface
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppGripAwareDockStateTest {
    @Test
    fun screenHorizontalGravityFollowsDisplayRotation() {
        assertEquals(2f, resolveScreenHorizontalGravity(2f, 3f, Surface.ROTATION_0))
        assertEquals(-3f, resolveScreenHorizontalGravity(2f, 3f, Surface.ROTATION_90))
        assertEquals(-2f, resolveScreenHorizontalGravity(2f, 3f, Surface.ROTATION_180))
        assertEquals(3f, resolveScreenHorizontalGravity(2f, 3f, Surface.ROTATION_270))
        assertNull(resolveScreenHorizontalGravity(2f, 3f, Int.MAX_VALUE))
        assertEquals(2f, resolveScreenHorizontalGravity(2f, Float.NaN, Surface.ROTATION_0))
        assertNull(resolveScreenHorizontalGravity(Float.NaN, 3f, Surface.ROTATION_0))
        assertNull(resolveScreenHorizontalGravity(2f, Float.POSITIVE_INFINITY, Surface.ROTATION_90))
    }

    @Test
    fun touchResolverUsesLowerPhysicalEdgeZones() {
        assertEquals(
            AppPhysicalDockSide.Left,
            resolveGripTouchPhysicalSide(41f, 112f, 100, 200),
        )
        assertEquals(
            AppPhysicalDockSide.Right,
            resolveGripTouchPhysicalSide(59f, 112f, 100, 200),
        )
        assertNull(resolveGripTouchPhysicalSide(42f, 112f, 100, 200))
        assertNull(resolveGripTouchPhysicalSide(58f, 112f, 100, 200))
        assertNull(resolveGripTouchPhysicalSide(20f, 111f, 100, 200))
        assertNull(resolveGripTouchPhysicalSide(-1f, 150f, 100, 200))
        assertNull(resolveGripTouchPhysicalSide(20f, 201f, 100, 200))
        assertNull(resolveGripTouchPhysicalSide(Float.NaN, 150f, 100, 200))
        assertNull(resolveGripTouchPhysicalSide(20f, 150f, 0, 200))
    }

    @Test
    fun invalidSensorSamplesDoNotPoisonOrResetConfirmation() {
        val clock = TestClock()
        val state = AppGripAwareDockState(elapsedRealtimeMs = clock::now)

        state.recordSensorGravity(-2f, 0f, Surface.ROTATION_0)
        clock.advanceBy(APP_GRIP_AWARE_DOCK_SENSOR_CONFIRM_DELAY_MS / 2)
        state.recordSensorGravity(Float.NaN, 0f, Surface.ROTATION_0)
        state.recordSensorGravity(Float.POSITIVE_INFINITY, 0f, Surface.ROTATION_0)
        clock.advanceBy(APP_GRIP_AWARE_DOCK_SENSOR_CONFIRM_DELAY_MS / 2)
        state.recordSensorGravity(-2f, 0f, Surface.ROTATION_0)

        assertEquals(AppFloatingDockSide.Start, state.layoutSide(LayoutDirection.Ltr))
    }

    @Test
    fun rotatedSensorConfirmationMapsPhysicalRightIntoRtlStart() {
        val clock = TestClock()
        val state = AppGripAwareDockState(elapsedRealtimeMs = clock::now)

        confirmSensorSide(
            state = state,
            clock = clock,
            gravityX = 0f,
            gravityY = -2f,
            displayRotation = Surface.ROTATION_90,
        )

        assertEquals(AppFloatingDockSide.Start, state.layoutSide(LayoutDirection.Rtl))
    }

    @Test
    fun stoppingSensorSessionClearsCandidateAgeAndPreservesSelectedSide() {
        val clock = TestClock()
        val state = AppGripAwareDockState(elapsedRealtimeMs = clock::now)

        confirmSensorSide(state, clock, gravityX = -2f)
        assertEquals(AppFloatingDockSide.Start, state.layoutSide(LayoutDirection.Ltr))
        state.onSensorSessionStopped()
        assertEquals(AppFloatingDockSide.Start, state.layoutSide(LayoutDirection.Ltr))

        state.recordSensorGravity(2f, 0f, Surface.ROTATION_0)
        clock.advanceBy(APP_GRIP_AWARE_DOCK_SENSOR_CONFIRM_DELAY_MS - 1)
        state.recordSensorGravity(2f, 0f, Surface.ROTATION_0)
        assertEquals(AppFloatingDockSide.Start, state.layoutSide(LayoutDirection.Ltr))
        clock.advanceBy(1)
        state.recordSensorGravity(2f, 0f, Surface.ROTATION_0)
        assertEquals(AppFloatingDockSide.End, state.layoutSide(LayoutDirection.Ltr))
    }

    @Test
    fun confirmedSensorSideBlocksOpposingTouchUntilSessionStops() {
        val clock = TestClock()
        val state = AppGripAwareDockState(elapsedRealtimeMs = clock::now)

        confirmSensorSide(state, clock, gravityX = -2f)
        repeat(APP_GRIP_AWARE_DOCK_TOUCH_CONFIRM_COUNT) {
            state.recordPhysicalTouch(AppPhysicalDockSide.Right)
        }
        assertEquals(AppFloatingDockSide.Start, state.layoutSide(LayoutDirection.Ltr))

        state.onSensorSessionStopped()
        repeat(APP_GRIP_AWARE_DOCK_TOUCH_CONFIRM_COUNT) {
            state.recordPhysicalTouch(AppPhysicalDockSide.Right)
        }
        assertEquals(AppFloatingDockSide.End, state.layoutSide(LayoutDirection.Ltr))
    }

    @Test
    fun touchFallbackRequiresThreeRecentMatchingTouches() {
        val clock = TestClock()
        val state = AppGripAwareDockState(elapsedRealtimeMs = clock::now)

        repeat(APP_GRIP_AWARE_DOCK_TOUCH_CONFIRM_COUNT - 1) {
            state.recordPhysicalTouch(AppPhysicalDockSide.Left)
        }
        assertEquals(AppFloatingDockSide.End, state.layoutSide(LayoutDirection.Ltr))

        clock.advanceBy(APP_GRIP_AWARE_DOCK_TOUCH_MEMORY_MS + 1)
        state.recordPhysicalTouch(AppPhysicalDockSide.Left)
        assertEquals(AppFloatingDockSide.End, state.layoutSide(LayoutDirection.Ltr))
        repeat(APP_GRIP_AWARE_DOCK_TOUCH_CONFIRM_COUNT - 1) {
            state.recordPhysicalTouch(AppPhysicalDockSide.Left)
        }
        assertEquals(AppFloatingDockSide.Start, state.layoutSide(LayoutDirection.Ltr))
    }

    @Test
    fun selectedPhysicalSideRemapsAcrossLayoutDirectionChanges() {
        val clock = TestClock()
        val state = AppGripAwareDockState(elapsedRealtimeMs = clock::now)

        repeat(APP_GRIP_AWARE_DOCK_TOUCH_CONFIRM_COUNT) {
            state.recordPhysicalTouch(AppPhysicalDockSide.Left)
        }
        assertEquals(AppFloatingDockSide.Start, state.layoutSide(LayoutDirection.Ltr))

        assertEquals(AppFloatingDockSide.End, state.layoutSide(LayoutDirection.Rtl))

        // How production actually resets: `rememberAppGripAwareDockState` keys its `remember` on the
        // setting, so turning grip awareness off builds a fresh state rather than clearing this one.
        // There used to be a `resetToDefault()` here for that, with no caller outside this line.
        val afterSettingFlip = AppGripAwareDockState(elapsedRealtimeMs = clock::now)
        assertEquals(AppFloatingDockSide.Start, afterSettingFlip.layoutSide(LayoutDirection.Rtl))
    }

    @Test
    fun sensorCanConfirmTheAlreadySelectedDefaultSide() {
        val clock = TestClock()
        val state = AppGripAwareDockState(elapsedRealtimeMs = clock::now)

        confirmSensorSide(state, clock, gravityX = 2f)
        repeat(APP_GRIP_AWARE_DOCK_TOUCH_CONFIRM_COUNT) {
            state.recordPhysicalTouch(AppPhysicalDockSide.Left)
        }

        assertEquals(AppFloatingDockSide.End, state.layoutSide(LayoutDirection.Ltr))
    }

    private fun confirmSensorSide(
        state: AppGripAwareDockState,
        clock: TestClock,
        gravityX: Float,
        gravityY: Float = 0f,
        displayRotation: Int = Surface.ROTATION_0,
    ) {
        state.recordSensorGravity(gravityX, gravityY, displayRotation)
        clock.advanceBy(APP_GRIP_AWARE_DOCK_SENSOR_CONFIRM_DELAY_MS)
        state.recordSensorGravity(gravityX, gravityY, displayRotation)
    }
}

private class TestClock {
    private var elapsedRealtimeMs = 0L

    fun now(): Long = elapsedRealtimeMs

    fun advanceBy(durationMs: Long) {
        elapsedRealtimeMs += durationMs
    }
}
