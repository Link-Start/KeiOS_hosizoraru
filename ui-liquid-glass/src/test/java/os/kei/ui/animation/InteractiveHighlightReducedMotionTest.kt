package os.kei.ui.animation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class InteractiveHighlightReducedMotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reducedMotionTracksTouchWithStaticImmediateFeedback() {
        lateinit var highlight: InteractiveHighlight
        composeRule.setContent {
            val scope = rememberCoroutineScope()
            highlight =
                remember(scope) {
                    InteractiveHighlight(
                        animationScope = scope,
                        animationsEnabled = false,
                    )
                }
            Box(
                Modifier
                    .size(120.dp)
                    .testTag("highlight-target")
                    .then(highlight.gestureModifier),
            )
        }

        val node = composeRule.onNodeWithTag("highlight-target")
        node.performTouchInput { down(center) }
        composeRule.waitForIdle()

        assertEquals(1f, highlight.pressProgress)
        assertEquals(0f, highlight.deformationProgress)
        assertEquals(Offset.Zero, highlight.offset)

        node.performTouchInput { moveBy(Offset(24f, 12f)) }
        composeRule.waitForIdle()

        assertEquals(Offset.Zero, highlight.offset)
        assertEquals(Offset(204f, 192f), highlight.touchPosition)

        node.performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(0f, highlight.pressProgress)
        assertEquals(0f, highlight.deformationProgress)
    }
}
