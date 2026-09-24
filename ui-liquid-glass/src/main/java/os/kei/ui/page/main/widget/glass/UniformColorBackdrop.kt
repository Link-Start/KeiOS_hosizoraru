package os.kei.ui.page.main.widget.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * A backdrop that is one colour everywhere, and says so.
 *
 * Draws exactly what `rememberCanvasBackdrop { drawRect(color) }` draws — `CanvasBackdrop` does nothing
 * but invoke its lambda — so swapping one for the other changes no pixel. What it adds is that a
 * consumer can tell the field it samples is flat. Blurring, lensing or refracting a flat field returns
 * that field, so a surface that only does those can draw the result directly instead of recording an
 * offscreen layer to compute it. See [liquidFlatField] and [rememberLiquidContentExport].
 */
@Stable
class UniformColorBackdrop(
    color: Color,
) : Backdrop {
    /**
     * Snapshot state, and the instance is kept across changes, because a new backdrop object does not
     * make `DrawBackdropNode` redraw: its update only re-observes the effects. A consumer recording this
     * backdrop reads [color] inside its draw, so a change here invalidates exactly those draws — the way a
     * `LayerBackdrop` invalidates its samplers when its own content changes. Measured: with a fresh instance
     * per colour, the OS overview card turned blue after a refresh and its pills kept sampling the grey it
     * had before.
     */
    var color: Color by mutableStateOf(color)
        internal set

    override val isCoordinatesDependent: Boolean
        get() = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        drawRect(color)
    }
}

@Composable
fun rememberUniformColorBackdrop(color: Color): UniformColorBackdrop {
    val backdrop = remember { UniformColorBackdrop(color) }
    SideEffect { backdrop.color = color }
    return backdrop
}

/**
 * The library's `vibrancy()`: a saturation of 1.5 with the Rec. 709 weights (0.213, 0.715, 0.072), which
 * are the weights [ColorMatrix.setToSaturation] uses. The matrix has no offset column, so it gives the
 * same result on premultiplied and straight colour, and a flat fill drawn through it lands on the colour
 * the effect would have produced from a flat backdrop.
 */
internal val LiquidVibrancyColorFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/**
 * What a glass surface should sample instead of [backdrop] when [backdrop] is one flat colour, or null.
 *
 * Over a flat field `vibrancy()` then any blur, lens, refraction or chromatic aberration leave exactly one
 * colour, `vibrancy(field)`. So a surface whose effect chain is only those can sample this instead and run
 * **no effects**: `DrawBackdropNode` sets its layer's `RenderEffect` from the effect scope, an empty scope
 * leaves it null, and HWUI then draws the layer inline instead of rasterising it offscreen every frame.
 * The clip, highlight, shadows, `layerBlock` transform, export and surface draws are all still
 * `drawBackdrop`'s own. A caller whose chain changes colour in any other way must not use this.
 */
@Composable
fun liquidFlatField(backdrop: Backdrop?): Backdrop? {
    val field = backdrop as? UniformColorBackdrop ?: return null
    val vibrant = remember { VibrantUniformBackdrop(field.color) }
    SideEffect { vibrant.color = field.color }
    return vibrant
}

/**
 * [UniformColorBackdrop] after the library's `vibrancy()`, applied through the same colour filter on the
 * GPU so the result rounds exactly as the effect chain's did. Stable and state-backed for the reason given
 * on [UniformColorBackdrop.color].
 */
@Stable
private class VibrantUniformBackdrop(
    color: Color,
) : Backdrop {
    var color: Color by mutableStateOf(color)

    override val isCoordinatesDependent: Boolean
        get() = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        drawRect(color, colorFilter = LiquidVibrancyColorFilter)
    }
}

/**
 * What a surface hands its content to sample: its exported layer, or — when that layer could only ever
 * hold one colour — that colour.
 *
 * `DrawBackdropNode` records its exported layer from `onDrawBehind`, the effected backdrop,
 * `onDrawSurface` and `onDrawFront`, and [LiquidSurface] uses only the surface. So over a
 * [UniformColorBackdrop] with no tint the export is `surfaceColor` over `vibrancy(field)` and nothing
 * else. Children then sample a flat field too — which lets them take the flat path themselves — and the
 * surface skips recording a second copy of itself every frame.
 */
@Immutable
internal class LiquidContentExport(
    /** Passed to the surface as `exportedBackdrop`; null when nothing needs recording. */
    val layer: LayerBackdrop?,
    /** Provided to the content as `LocalLiquidParentBackdrop`; null when the surface exports nothing. */
    val backdrop: Backdrop?,
) {
    companion object {
        val None = LiquidContentExport(layer = null, backdrop = null)
    }
}

@Composable
internal fun rememberLiquidContentExport(
    exportToContent: Boolean,
    activeBackdrop: Backdrop?,
    tint: Color,
    surfaceColor: Color,
): LiquidContentExport {
    if (!exportToContent || activeBackdrop == null) return LiquidContentExport.None
    val field = activeBackdrop as? UniformColorBackdrop
    if (field != null && !tint.isSpecified) {
        val vibrant = field.color.liquidVibrant()
        val exported =
            rememberUniformColorBackdrop(
                if (surfaceColor.isSpecified && surfaceColor.alpha > 0f) {
                    surfaceColor.compositeOver(vibrant)
                } else {
                    vibrant
                },
            )
        return remember(exported) { LiquidContentExport(layer = null, backdrop = exported) }
    }
    val layer = rememberLayerBackdrop()
    return remember(layer) { LiquidContentExport(layer = layer, backdrop = layer) }
}

/** [LiquidVibrancyColorFilter] applied on the CPU, in sRGB as the platform colour filter applies it. */
internal fun Color.liquidVibrant(): Color {
    val c = convert(ColorSpaces.Srgb)
    val saturation = 1.5f
    val inverse = 1f - saturation
    val rw = 0.213f * inverse
    val gw = 0.715f * inverse
    val bw = 0.072f * inverse
    val r = c.red
    val g = c.green
    val b = c.blue
    return Color(
        red = ((rw + saturation) * r + gw * g + bw * b).coerceIn(0f, 1f),
        green = (rw * r + (gw + saturation) * g + bw * b).coerceIn(0f, 1f),
        blue = (rw * r + gw * g + (bw + saturation) * b).coerceIn(0f, 1f),
        alpha = c.alpha,
    )
}

