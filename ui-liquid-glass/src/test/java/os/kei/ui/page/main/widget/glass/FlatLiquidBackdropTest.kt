package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The flat-field surface draws what the library's `drawBackdrop` draws over the same field: the fill, the
 * surface colour, the content clipped to the continuous-corner shape, the highlight and both shadows, in
 * the library's order. Rendered both ways and compared, rim included. What cannot be rendered here is
 * the reason for the node — the clip moving from compositing time into the layer — which is measured on
 * the phone in docs/planning/liquid-glass-clip-and-resolution.md.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class FlatLiquidBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val field = UniformColorBackdrop(Color(0xFF9DB8E8))
    private val shape: Shape = RoundedRectangle(24.dp)
    private val surface = Color(0xFF3D7BFF).copy(alpha = 0.24f)

    @Test
    fun theFlatSurfaceMatchesTheLibrarysDrawing() {
        // One composition, both surfaces side by side, so the two renders share a frame.
        composeRule.setContent {
            androidx.compose.foundation.layout.Row {
                Scene("library") { content ->
                    Modifier.drawBackdrop(
                        backdrop = field,
                        shape = { shape },
                        effects = {},
                        highlight = { Highlight.Default.copy(alpha = 0.8f) },
                        shadow = null,
                        innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.55f) },
                        onDrawSurface = { drawRect(surface) },
                    ).then(content)
                }
                Scene("flat") { content ->
                    Modifier.drawFlatLiquidBackdrop(
                        field = field,
                        shape = { shape },
                        highlight = { Highlight.Default.copy(alpha = 0.8f) },
                        innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.55f) },
                        onDrawSurface = { drawRect(surface) },
                    ).then(content)
                }
            }
        }
        composeRule.waitForIdle()
        val library = composeRule.onNodeWithTag("library").captureToImage().toPixelMap()
        val flat = composeRule.onNodeWithTag("flat").captureToImage().toPixelMap()
        // Robolectric rounds this rim one step further on Linux than on macOS: CI measured 2/255 where
        // macOS gives 1/255 (D#209, 2026-09-25). Two steps is the limit the shadow case below uses.
        assertClose(library, flat, tolerance = 2f / 255f)
    }

    @Test
    fun theOuterShadowAndTheAmbientHighlightMatchTheLibrarysDrawing() {
        // A small control's recipe: an ambient rim and a tight drop shadow that reaches past the bounds.
        val highlight = { Highlight(width = 0.8.dp, blurRadius = 1.4.dp, alpha = 0.72f, style = HighlightStyle.Ambient(0.6f)) }
        val shadow = { Shadow(radius = 4.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.16f)) }
        composeRule.setContent {
            androidx.compose.foundation.layout.Row {
                Scene("library") { content ->
                    Modifier.drawBackdrop(
                        backdrop = field,
                        shape = { shape },
                        effects = {},
                        highlight = highlight,
                        shadow = shadow,
                        innerShadow = null,
                        onDrawSurface = { drawRect(surface) },
                    ).then(content)
                }
                Scene("flat") { content ->
                    Modifier.drawFlatLiquidBackdrop(
                        field = field,
                        shape = { shape },
                        highlight = highlight,
                        shadow = shadow,
                        onDrawSurface = { drawRect(surface) },
                    ).then(content)
                }
            }
        }
        composeRule.waitForIdle()
        val library = composeRule.onNodeWithTag("library").captureToImage().toPixelMap()
        val flat = composeRule.onNodeWithTag("flat").captureToImage().toPixelMap()
        // Five rim pixels land one step further apart where the shadow's blurred edge meets the corner.
        assertClose(library, flat, tolerance = 2f / 255f)
    }

    @Test
    fun contentThatReachesTheCornersIsClippedToTheShape() {
        // A child painting its whole box: only the clip keeps it out of the corners.
        composeRule.setContent {
            Scene("flat") { content -> Modifier.drawFlatLiquidBackdrop(field = field, shape = { shape }).then(content) }
        }
        composeRule.waitForIdle()
        val flat = composeRule.onNodeWithTag("flat").captureToImage().toPixelMap()
        // 20dp of white margin, then the shape's top-left corner, which a 24dp radius leaves uncovered.
        val corner = flat[flat.width * 21 / 160, flat.height * 21 / 160]
        assertTrue(corner.red > 0.97f && corner.green > 0.97f && corner.blue > 0.97f, "the corner must stay outside the shape, was $corner")
    }

    @Composable
    private fun Scene(
        tag: String,
        surfaceModifier: (Modifier) -> Modifier,
    ) {
        // The content paints its whole box, so the clip is what keeps it out of the corners.
        Box(Modifier.testTag(tag).size(160.dp).background(Color.White).padding(20.dp)) {
            Box(surfaceModifier(Modifier.background(Color(0x55FF4D8D))).size(120.dp))
        }
    }

    private fun assertClose(
        expected: PixelMap,
        actual: PixelMap,
        // One step of 8-bit rounding: the rim's coverage lands in the layer before it is composited.
        tolerance: Float = 1f / 255f,
    ) {
        var worst = 0f
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val e = expected[x, y]
                val a = actual[x, y]
                worst = maxOf(worst, abs(e.red - a.red), abs(e.green - a.green), abs(e.blue - a.blue), abs(e.alpha - a.alpha))
            }
        }
        assertTrue(worst <= tolerance, "worst channel difference ${worst * 255f}/255 exceeds ${tolerance * 255f}")
    }
}
