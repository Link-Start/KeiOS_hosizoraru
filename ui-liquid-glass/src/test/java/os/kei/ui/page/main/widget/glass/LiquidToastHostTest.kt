package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.liquidglass.R
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for [LiquidToastHost] at the COMPOSABLE layer.
 *
 * The pure-state tests in LiquidToastStateTest cover the queue/token logic, but the "stuck toast
 * that never dismisses" regression lived in the composable's lifecycle: writing
 * `visibleState.targetState = true` in the composable body re-asserted it on every recomposition,
 * reversing the exit the dismiss timer had started. A second bug had the timer measure elapsed time
 * with System.currentTimeMillis() while delaying on the coroutine clock, so it never fired under a
 * test clock (and would desync under background suspension on-device).
 *
 * These drive the real timer with a manually-stepped clock (autoAdvance disabled) so the
 * delay -> targetState=false -> exit animation -> onDismiss chain is deterministic.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = LiquidToastHostTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidToastHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setHost(state: LiquidToastState) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .layerBackdrop(backdrop),
                ) {
                    LiquidToastHost(state = state, backdrop = backdrop)
                }
            }
        }
    }

    private fun nodeCount(text: String): Int = composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().size

    /** Step the clock in small slices so every delay tick + animation frame + recompose processes. */
    private fun stepClock(
        totalMs: Long,
        sliceMs: Long = 100,
    ) {
        var advanced = 0L
        while (advanced < totalMs) {
            composeRule.mainClock.advanceTimeBy(sliceMs)
            composeRule.waitForIdle()
            advanced += sliceMs
        }
    }

    @Test
    fun repeatedIdenticalToastAfterDismissStillDismisses() {
        // The exact regression: a second identical message shown after the first cleared must also
        // dismiss. The body-level `targetState = true` made the second one stick forever.
        val state = LiquidToastState()
        setHost(state)

        state.show("Saved")
        stepClock(4_000)
        assertTrue(nodeCount("Saved") == 0, "First toast should have dismissed")

        state.show("Saved")
        stepClock(600)
        assertTrue(nodeCount("Saved") > 0, "Second identical toast should be visible")

        stepClock(4_000)
        assertTrue(
            nodeCount("Saved") == 0,
            "Repeated identical toast must also auto-dismiss, not stick forever",
        )
    }

    @Test
    fun queuedToastsAllDrainAndClear() {
        val state = LiquidToastState()
        setHost(state)

        state.show("A")
        state.show("B")
        state.show("C") // queued behind the 2 visible

        // Drive past several display + animation cycles so chained promotions (dismiss -> recompose
        // -> promote queued -> new timer) each get picked up by the stepped clock.
        stepClock(30_000)

        val remaining = listOf("A", "B", "C").filter { nodeCount(it) > 0 }
        assertTrue(
            remaining.isEmpty(),
            "All queued toasts should drain and dismiss; still on screen: $remaining",
        )
    }

    @Test
    fun visibleToastExposesPoliteLiveRegionPaneTitleAndOneMessageLabel() {
        val state = LiquidToastState()
        setHost(state)
        state.show("Saved")
        stepClock(600)

        val context = RuntimeEnvironment.getApplication()
        val toastNode = composeRule.onNodeWithContentDescription("Saved")
        toastNode
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    context.getString(R.string.liquid_toast_pane_title),
                ),
            ).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeRule
            .onAllNodesWithContentDescription("Saved", useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule
            .onAllNodesWithText("Saved")
            .assertCountEquals(0)

        val dismissAction = toastNode.fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertEquals(context.getString(R.string.liquid_toast_dismiss), dismissAction.label)
    }

    @Test
    fun dismissActionPlaysExitAndReleasesTheToastSlot() {
        val state = LiquidToastState()
        setHost(state)
        state.show("Dismiss me")
        stepClock(600)

        composeRule.onNodeWithContentDescription("Dismiss me").performClick()
        stepClock(600)

        composeRule
            .onAllNodesWithContentDescription("Dismiss me")
            .assertCountEquals(0)
        assertTrue(state.visibleSlots.isEmpty())
    }
}

class LiquidToastHostTestApp : Application()
