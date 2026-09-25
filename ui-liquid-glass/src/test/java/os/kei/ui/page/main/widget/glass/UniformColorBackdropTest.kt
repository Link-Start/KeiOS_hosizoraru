package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.LayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A surface over a flat field draws the field's result directly, and a card over one hands its content a
 * flat field instead of recording an exported layer.
 *
 * The pixel equivalence against the real glass path cannot be checked here — Robolectric does not run
 * `RenderEffect` — so it was checked on the A17 AVD and is recorded in
 * `docs/planning/liquid-flat-field.md`. These pin the decision and the colour arithmetic that equivalence
 * rests on.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class UniformColorBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val field = Color(0xFFE9ECF2)
    private val surface = Color(0xFF3D7BFF).copy(alpha = 0.24f)

    @Test
    fun theCpuVibrancyIsTheSaturationMatrixTheFlatPathDrawsThrough() {
        val matrix = ColorMatrix().apply { setToSaturation(1.5f) }.values
        for (color in listOf(Color(0xFFE9ECF2), Color(0xFF3D7BFF), Color(0x9A202630), Color(0xFFFF4D8D))) {
            val expected =
                FloatArray(3) { row ->
                    (matrix[row * 5] * color.red + matrix[row * 5 + 1] * color.green + matrix[row * 5 + 2] * color.blue)
                        .coerceIn(0f, 1f)
                }
            val actual = color.liquidVibrant()
            // An sRGB Color is stored as 8-bit ARGB, so the result lands on the nearest 1/255 step, as the
            // GPU's output does.
            val step = 0.5f / 255f
            assertEquals(expected[0], actual.red, step)
            assertEquals(expected[1], actual.green, step)
            assertEquals(expected[2], actual.blue, step)
            assertEquals(color.alpha, actual.alpha, step)
        }
    }

    @Test
    fun aStillCardOverAFlatFieldExportsItsColourAndRecordsNoLayer() {
        val export = captureExport(backdrop = UniformColorBackdrop(field))

        assertNull(export.layer, "nothing to record: the export could only ever hold one colour")
        val flat = assertIs<UniformColorBackdrop>(export.backdrop)
        val expected = surface.compositeOver(field.liquidVibrant())
        assertColorNear(expected, flat.color, tolerance = 0.5f / 255f)
    }

    @Test
    fun aColourChangeReachesWhatSamplesTheFlatField() {
        // A surface over a flat field follows the field's colour when it changes.
        val first = Color(0xFF3B3B40)
        val second = Color(0xFF3D7BFF)
        var fieldColor by mutableStateOf(first)
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val field = rememberUniformColorBackdrop(fieldColor)
                LiquidSurface(
                    backdrop = field,
                    modifier = Modifier.size(80.dp).testTag("surface"),
                    shape = RectangleShape,
                    isInteractive = false,
                    highlightAlpha = 0f,
                )
            }
        }
        composeRule.waitForIdle()
        fieldColor = second
        composeRule.waitForIdle()

        val pixels = composeRule.onNodeWithTag("surface").captureToImage().toPixelMap()
        assertColorNear(second.liquidVibrant(), pixels[pixels.width / 2, pixels.height / 2], tolerance = 2f / 255f)
    }

    @Test
    fun aFlatFieldKeepsOneInstanceAndChangesColourInPlace() {
        // The contract the redraw depends on. `DrawBackdropNode` does not redraw when handed a new backdrop
        // object — its update only re-observes the effects — so on the A17 AVD a fresh instance per colour
        // left the OS overview card's pills drawing the grey they started with after the card turned blue.
        // Robolectric redraws on recomposition regardless and cannot show that, so this pins the contract
        // instead: one instance, colour as state, which invalidates every draw that read it.
        val first = Color(0xFF3B3B40)
        val second = Color(0xFF3D7BFF)
        var fieldColor by mutableStateOf(first)
        val seen = mutableListOf<UniformColorBackdrop>()
        composeRule.setContent {
            val field = rememberUniformColorBackdrop(fieldColor)
            androidx.compose.runtime.SideEffect { seen += field }
        }
        composeRule.waitForIdle()
        fieldColor = second
        composeRule.waitForIdle()

        assertTrue(seen.size >= 2, "the change must recompose the caller")
        assertTrue(seen.all { it === seen.first() }, "a new instance per colour is what left samplers stale")
        assertEquals(second, seen.last().color)
    }

    @Test
    fun aTintedCardKeepsItsRecordedExport() {
        val export = captureExport(backdrop = UniformColorBackdrop(field), tint = Color.Red)

        assertIs<LayerBackdrop>(export.layer)
    }

    @Test
    fun aCardOverALiveLayerKeepsItsRecordedExport() {
        lateinit var export: LiquidContentExport
        composeRule.setContent {
            val live = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            export =
                rememberLiquidContentExport(
                    exportToContent = true,
                    activeBackdrop = live,
                    tint = Color.Unspecified,
                    surfaceColor = surface,
                )
        }
        composeRule.waitForIdle()

        assertNotNull(export.layer)
    }

    @Test
    fun aSurfaceOverAFlatFieldPaintsTheColourTheGlassWouldHaveComputed() {
        // Saturated enough that vibrancy moves it by more than the tolerance, so a path that skipped it fails.
        val field = Color(0xFF9DB8E8)
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(Modifier.size(80.dp)) {
                    LiquidSurface(
                        backdrop = UniformColorBackdrop(field),
                        modifier = Modifier.size(80.dp).testTag("surface"),
                        shape = RectangleShape,
                        isInteractive = false,
                        surfaceColor = surface,
                        highlightAlpha = 0f,
                    )
                }
            }
        }

        val pixels = composeRule.onNodeWithTag("surface").captureToImage().toPixelMap()
        val centre = pixels[pixels.width / 2, pixels.height / 2]
        assertColorNear(surface.compositeOver(field.liquidVibrant()), centre, tolerance = 2f / 255f)
    }

    private fun captureExport(
        backdrop: UniformColorBackdrop,
        tint: Color = Color.Unspecified,
    ): LiquidContentExport {
        lateinit var export: LiquidContentExport
        composeRule.setContent {
            export =
                rememberLiquidContentExport(
                    exportToContent = true,
                    activeBackdrop = backdrop,
                    tint = tint,
                    surfaceColor = surface,
                )
        }
        composeRule.waitForIdle()
        return export
    }

    private fun assertColorNear(
        expected: Color,
        actual: Color,
        tolerance: Float = 1e-4f,
    ) {
        val deltas =
            listOf(
                expected.red - actual.red,
                expected.green - actual.green,
                expected.blue - actual.blue,
                expected.alpha - actual.alpha,
            )
        assertTrue(deltas.all { abs(it) <= tolerance }, "expected $expected, was $actual")
    }
}
