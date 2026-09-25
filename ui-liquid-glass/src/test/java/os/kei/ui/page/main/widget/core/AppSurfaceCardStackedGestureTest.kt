package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAliveHeadroom
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * A pinned card answers where it is drawn, not where it is laid out.
 *
 * The pile holds a card at the stack line with a `translationY` inside the layer `drawBackdrop` builds
 * from its `layerBlock`, while the card's layout position keeps scrolling up. Compose maps a pointer
 * through a layer on the way in, so everything below that layer travels with the pixels and everything
 * above it stays behind. `AppSurfaceBox` used to wrap the surface in its own `combinedClickable` —
 * above the layer — so any card with a long press had its gesture left at the layout position while
 * its plate was drawn at the stack line, the two disagreeing by the whole pile overshoot. On a real
 * page that means a press on a receded plate reaching the card in front of it, and a plate more than
 * its own height past the line answering nothing at all.
 *
 * Written as behaviour rather than as a modifier order on purpose: the ordering is exactly what a
 * refactor moves without noticing, and `AppSurfaceCardTransformContractTest` pins the wiring string
 * while staying blind to which side of the layer the gesture ends up on.
 *
 * No coordinates are hard-coded. The fixture reads the plate's drawn rect and the frame's
 * untransformed rect out of the tree and probes the band where they disagree, so the pile's constants
 * can be retuned without rewriting the test — and [theFixtureActuallyPinsTheCard] fails loudly if a
 * change ever stops them disagreeing, which would leave the other two assertions vacuous.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppSurfaceCardStackedGestureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theFixtureActuallyPinsTheCard() {
        composeRule.setContent { PinnedCard(mutableListOf()) }

        val plate = composeRule.onNodeWithTag(PLATE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val frame = composeRule.onNodeWithTag(FRAME, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(
            plate.top - frame.top > 24.dp,
            "the pile must actually be holding this card away from its layout position, or the " +
                "gesture assertions prove nothing: plate=$plate frame=$frame",
        )
    }

    @Test
    fun aPinnedCardAnswersWhereItIsDrawn() {
        val taps = mutableListOf<String>()
        composeRule.setContent { PinnedCard(taps) }
        val plate = composeRule.onNodeWithTag(PLATE, useUnmergedTree = true).getUnclippedBoundsInRoot()

        composeRule.clickAt((plate.top + plate.bottom) / 2f)

        composeRule.runOnIdle {
            assertEquals(listOf("click"), taps, "a press on the visible plate has to reach the card")
        }
    }

    @Test
    fun aPinnedCardDoesNotAnswerWhereItIsMerelyLaidOut() {
        val taps = mutableListOf<String>()
        composeRule.setContent { PinnedCard(taps) }
        val plate = composeRule.onNodeWithTag(PLATE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val frame = composeRule.onNodeWithTag(FRAME, useUnmergedTree = true).getUnclippedBoundsInRoot()

        // Between the two tops: inside the card's layout rect, above anything it draws. Blank page as
        // far as the reader is concerned, and the band a gesture left outside the layer answered in.
        composeRule.clickAt((frame.top + plate.top) / 2f)

        composeRule.runOnIdle {
            assertEquals(emptyList(), taps, "the card must not answer above the plate it draws")
        }
    }

    @Composable
    private fun PinnedCard(taps: MutableList<String>) {
        MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
            val state = rememberAppEdgeStackState(stackLine = 260.dp)
            AppEdgeStackKeepAlive(state = state, modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalAppEdgeStackCards provides state) {
                    // The wrapper measures its child a headroom taller and places it that far up, so the
                    // padding is what puts the card at the container's own origin. Sitting at the origin
                    // with the stack line well below it is what pins the card.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = AppEdgeStackKeepAliveHeadroom),
                    ) {
                        AppSurfaceCard(
                            modifier = Modifier.fillMaxWidth().height(160.dp).testTag(FRAME),
                            onClick = { taps += "click" },
                            onLongClick = { taps += "long" },
                        ) {
                            // Inside the surface, so this rides the pile transform and its bounds are
                            // where the card is actually drawn.
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp).testTag(PLATE))
                        }
                    }
                }
            }
        }
    }
}

private const val FRAME = "stacked-card-frame"
private const val PLATE = "stacked-card-plate"

private fun ComposeContentTestRule.clickAt(y: Dp) {
    val yPx = with(density) { y.toPx() }
    onRoot().performTouchInput { click(Offset(x = width / 2f, y = yPx)) }
}
