package os.kei.ui.animation

import android.app.Application
import android.view.View
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(
    application = DampedDragAnimationSystemGestureExclusionTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class DampedDragAnimationSystemGestureExclusionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dragModifierLeavesSystemGesturesAvailableByDefault() {
        val rootView = setDragTarget()

        assertFalse(
            rootView.systemGestureExclusionRects.any { rect -> rect.width() > 0 && rect.height() > 0 },
        )
    }

    @Test
    fun dragModifierCanExplicitlyOptIntoSystemGestureExclusion() {
        val rootView = setDragTarget(excludeFromSystemGestures = true)

        assertTrue(
            rootView.systemGestureExclusionRects.any { rect -> rect.width() > 0 && rect.height() > 0 },
        )
    }

    @Test
    fun horizontalAxisRejectsVerticalGestureAsCancellation() {
        var dragCount = 0
        var stopCount = 0
        var cancelCount = 0
        setHorizontalDragTarget(
            onDragStopped = { stopCount++ },
            onDragCancelled = { cancelCount++ },
            onDrag = { dragCount++ },
        )

        composeRule.onNodeWithTag(DRAG_TARGET_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 48f))
            up()
        }

        assertTrue(dragCount == 0)
        assertTrue(stopCount == 0)
        assertTrue(cancelCount == 1)
    }

    @Test
    fun horizontalAxisAcceptsHorizontalGesture() {
        var dragCount = 0
        var stopCount = 0
        var cancelCount = 0
        setHorizontalDragTarget(
            onDragStopped = { stopCount++ },
            onDragCancelled = { cancelCount++ },
            onDrag = { dragCount++ },
        )

        composeRule.onNodeWithTag(DRAG_TARGET_TAG).performTouchInput {
            down(center)
            moveBy(Offset(48f, 0f))
            up()
        }

        assertTrue(dragCount > 0)
        assertTrue(stopCount == 1)
        assertTrue(cancelCount == 0)
    }

    private fun setHorizontalDragTarget(
        onDragStopped: () -> Unit,
        onDragCancelled: () -> Unit,
        onDrag: () -> Unit,
    ) {
        setDragTarget(
            height = 96.dp,
            dragOrientation = Orientation.Horizontal,
            dragTouchSlop = 8f,
            consumeDragChanges = true,
            onDragStopped = onDragStopped,
            onDragCancelled = onDragCancelled,
            onDrag = onDrag,
        )
    }

    /** One [DampedDragAnimation] on a 96dp-wide box tagged [DRAG_TARGET_TAG]; returns the host view. */
    private fun setDragTarget(
        height: Dp = 48.dp,
        excludeFromSystemGestures: Boolean = false,
        dragOrientation: Orientation? = null,
        dragTouchSlop: Float = 0f,
        consumeDragChanges: Boolean = false,
        onDragStopped: () -> Unit = {},
        onDragCancelled: () -> Unit = {},
        onDrag: () -> Unit = {},
    ): View {
        lateinit var rootView: View
        composeRule.setContent {
            rootView = LocalView.current
            val scope = rememberCoroutineScope()
            val dragAnimation =
                remember(scope) {
                    DampedDragAnimation(
                        animationScope = scope,
                        initialValue = 0f,
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        initialScale = 1f,
                        pressedScale = 1.1f,
                        consumeDragChanges = consumeDragChanges,
                        excludeFromSystemGestures = excludeFromSystemGestures,
                        dragOrientation = dragOrientation,
                        dragTouchSlop = dragTouchSlop,
                        onDragStarted = {},
                        onDragStopped = { onDragStopped() },
                        onDragCancelled = { onDragCancelled() },
                        onDrag = { _, _ -> onDrag() },
                    )
                }

            Box(
                modifier =
                    Modifier
                        .size(width = 96.dp, height = height)
                        .testTag(DRAG_TARGET_TAG)
                        .then(dragAnimation.modifier),
            )
        }
        composeRule.waitForIdle()
        return rootView
    }
}

private const val DRAG_TARGET_TAG = "damped-drag-target"

class DampedDragAnimationSystemGestureExclusionTestApp : Application()
