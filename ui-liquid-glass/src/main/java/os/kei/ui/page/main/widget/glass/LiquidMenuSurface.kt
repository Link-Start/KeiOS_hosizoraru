@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.sheet.LocalSceneBackdrop

/**
 * A menu panel is mostly a large corner radius, so it can carry a generous rim without the
 * refraction eating into the rows.
 */
private const val LIQUID_MENU_LENS_SCALE = 0.46f
private const val LIQUID_MENU_REFRACTION_AMOUNT_SCALE = 1.55f

/** The panel scales up from here. Subtle, so it grows out of its button rather than popping. */
private const val LIQUID_MENU_MIN_SCALE = 0.88f

/** Reaches full opacity before the scale settles, so the reveal reads as one movement. */
private const val LIQUID_MENU_ALPHA_GAIN = 1.9f

/**
 * The reveal a menu panel applies to itself.
 *
 * Lives in a composition local rather than a parameter because the panel that owns the glass
 * ([LiquidGlassDropdownColumn]) sits three layers below the presentation that owns the animation, and
 * everything in between is caller content.
 */
@Stable
class LiquidMenuRevealState internal constructor(
    internal val progressProvider: () -> Float,
    internal val placementProvider: () -> LiquidMenuPivot,
)

/**
 * Where the panel grows from, in fractions of its own size, plus the directional nudge.
 *
 * [directionalOffsetPx] is signed: negative pulls the panel up toward an anchor above it, positive
 * pushes it down toward an anchor below.
 */
internal data class LiquidMenuPivot(
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0f,
    val directionalOffsetPx: Float = 0f,
)

val LocalLiquidMenuReveal = staticCompositionLocalOf<LiquidMenuRevealState?> { null }

internal class LiquidMenuSurface(
    val modifier: Modifier,
    val glassEnabled: Boolean,
)

/**
 * The menu panel's material.
 *
 * Two things here are the whole point of the rewrite.
 *
 * **It samples a backdrop that exists.** Menus used to render in a `Popup` window, where
 * [LiquidBackdropWindowBoundary] blanks `LocalSceneBackdrop` to `emptyBackdrop()` and nulls the parent
 * and dialog backdrops — so `preferredLiquidBackdrop` discarded the backdrop every call site threaded
 * in, `activeGlassBackdrop` returned null, and the panel silently took its opaque fallback. Every blur,
 * lens, vibrancy and shadow value configured for it was dead. The panel is now hosted in the activity
 * window by [LiquidMenuPresentation], so the scene backdrop is real.
 *
 * **The reveal goes through `layerBlock`, and the pivot is expressed as translation.** A transform
 * applied *around* `drawBackdrop` scales the sampled backdrop with the panel. But `layerBlock` alone is
 * not enough here: `InverseLayerScope.inverseTransformAtTopLeft` reads only `rotationZ`, `scaleX` and
 * `scaleY`, and inverts about the element's top-left — it never looks at `transformOrigin`. Setting an
 * arbitrary origin would therefore leave a residual offset that slides the refraction for the length of
 * the animation. Scaling about the top-left with a compensating translation is the same transform
 * (`s·x + p(1-s)` either way), and it is one the inverse handles exactly.
 */
@Composable
internal fun rememberLiquidMenuSurface(
    cornerRadius: Dp,
    isDark: Boolean,
    explicitBackdrop: Backdrop?,
    material: LiquidGlassDropdownMaterial,
): LiquidMenuSurface {
    val liquidControlsEnabled = LocalLiquidControlsEnabled.current
    val sceneBackdrop = LocalSceneBackdrop.current
    val inOverlay = LocalLiquidOverlayHost.current != null
    val reveal = LocalLiquidMenuReveal.current
    val resolvedBackdrop = explicitBackdrop ?: sceneBackdrop.takeIf { inOverlay }
    val glassEnabled = liquidControlsEnabled && resolvedBackdrop != null
    val shape = remember(cornerRadius) { RoundedRectangle(cornerRadius) }

    // Reading the reveal only inside the layer keeps the whole animation on the render thread: no
    // recomposition, no relayout of the rows, no re-measure of the panel.
    val transform: GraphicsLayerScope.() -> Unit = {
        if (reveal != null) {
            val pivot = reveal.placementProvider()
            val resolved =
                liquidMenuTransform(
                    progress = reveal.progressProvider(),
                    widthPx = size.width,
                    heightPx = size.height,
                    pivot = pivot,
                )
            scaleX = resolved.scale
            scaleY = resolved.scale
            translationX = resolved.translationX
            translationY = resolved.translationY
            presentationFade(resolved.alpha)
            // Explicit, not incidental: the inverse transform assumes it.
            transformOrigin = LiquidMenuTopLeftOrigin
        }
    }

    if (!glassEnabled) {
        return LiquidMenuSurface(
            modifier = Modifier
                .graphicsLayer(block = transform)
                .appSquircleBackground(liquidMenuFallbackFill(isDark), cornerRadius),
            glassEnabled = false,
        )
    }

    val blurRadius = presentationGlassBlur()
    val lens =
        presentationGlassLens(
            lensScale = LIQUID_MENU_LENS_SCALE,
            refractionScale = LIQUID_MENU_REFRACTION_AMOUNT_SCALE,
        )
    val fill = liquidMenuGlassFill(isDark = isDark, material = material)

    return LiquidMenuSurface(
        modifier = Modifier.drawBackdrop(
            backdrop = resolvedBackdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                safeLiquidLens(
                    lens.refractionHeight.toPx(),
                    lens.refractionAmount.toPx(),
                    chromaticAberration = false,
                    depthEffect = true,
                )
            },
            layerBlock = transform,
            // A brighter rim than its siblings, because it is doing the lifting a drop shadow would.
            highlight = { Highlight.Default.copy(alpha = if (isDark) 0.86f else 1f) },
            // No drop shadow, deliberately, and it cost a while to arrive at.
            //
            // `drawBackdrop`'s shadow is drawn by a `ShadowNode` that sits inside
            // `Modifier.graphicsLayer(layerBlock)` and paints past the element's bounds — a blurred
            // outline with the shape cleared out of it, spreading `radius * 2` every way. Inside a layer
            // bounded to the element, all that survives is the slice inside the bounding rectangle but
            // outside the rounded shape, which reads as dark right-angled corners hugging the panel.
            // Measuring across the panel's edge showed no darkening at all *outside* it, which is the
            // tell, and disabling the shadow made the corners clean immediately.
            //
            // A platform elevation shadow would not have that problem — the renderer derives it from the
            // node's outline rather than from draw instructions inside a layer. But `Modifier.shadow`
            // applied outside the transform measured as casting nothing here (identical pixels above and
            // below the panel with it on and off), so it went rather than stay as a knob that does
            // nothing — this file just deleted twenty of those.
            //
            // The menu is the only presentation this bites: the sheet and alert put their corners over a
            // dimmed scrim, and the toast is a capsule with almost no corner notch to expose. Depth here
            // comes from the rim and the inner shadow instead, which is what the glass already had.
            shadow = null,
            innerShadow = { InnerShadow(radius = 14.dp, alpha = if (isDark) 0.26f else 0.18f) },
            onDrawSurface = { drawRect(fill) },
        ),
        glassEnabled = true,
    )
}

private val LiquidMenuTopLeftOrigin = TransformOrigin(0f, 0f)

internal data class LiquidMenuTransformValues(
    val scale: Float,
    val alpha: Float,
    val translationX: Float,
    val translationY: Float,
)

/**
 * The reveal, kept pure so the pivot compensation can be unit-tested — it is the part that is easy to
 * get subtly wrong and impossible to see in a screenshot.
 */
internal fun liquidMenuTransform(
    progress: Float,
    widthPx: Float,
    heightPx: Float,
    pivot: LiquidMenuPivot,
): LiquidMenuTransformValues {
    val eased = progress.coerceIn(0f, 1f)
    val scale = LIQUID_MENU_MIN_SCALE + (1f - LIQUID_MENU_MIN_SCALE) * eased
    val safeWidth = widthPx.takeIf { it.isFinite() && it > 0f } ?: 0f
    val safeHeight = heightPx.takeIf { it.isFinite() && it > 0f } ?: 0f
    // t = p(1 - s): scaling about the top-left plus this translation is exactly scaling about the pivot.
    val pivotCompensationX = pivot.pivotX.coerceIn(0f, 1f) * safeWidth * (1f - scale)
    val pivotCompensationY = pivot.pivotY.coerceIn(0f, 1f) * safeHeight * (1f - scale)
    val directional =
        pivot.directionalOffsetPx.takeIf { it.isFinite() }?.times(1f - eased) ?: 0f
    return LiquidMenuTransformValues(
        scale = scale,
        alpha = (eased * LIQUID_MENU_ALPHA_GAIN).coerceIn(0f, 1f),
        translationX = pivotCompensationX,
        translationY = pivotCompensationY + directional,
    )
}

/**
 * The tint painted over the blurred sample.
 *
 * Heavier than [os.kei.ui.page.main.widget.dialog.liquidModalGlassFill] because a menu is dense text on
 * a small panel with no scrim between it and the page, and menu rows have to stay scannable at a glance.
 * The action-menu preset carries a touch more because it is the larger panel and the one that stacks
 * quick actions, submenus and destructive rows.
 */
internal fun liquidMenuGlassFill(
    isDark: Boolean,
    material: LiquidGlassDropdownMaterial,
): Color {
    // Light mode carries more than dark. Not symmetry for its own sake: the worst case in light mode is a
    // near-black card behind a pale panel, which shows through as a grey band across a row, and measured
    // on device at 0.83 it was thin enough to read but visible. Dark mode has no equivalent — mid-tone
    // content behind dark glass reads as depth rather than as a stain.
    val alpha =
        when (material) {
            LiquidGlassDropdownMaterial.Default -> if (isDark) 0.82f else 0.85f
            LiquidGlassDropdownMaterial.ActionMenu -> if (isDark) 0.85f else 0.89f
        }
    return if (isDark) {
        Color(0xFF171725).copy(alpha = alpha)
    } else {
        Color(0xFFF9FAFD).copy(alpha = alpha)
    }
}

/** Used when there is no usable backdrop, so it has to be opaque enough to stand on its own. */
internal fun liquidMenuFallbackFill(isDark: Boolean): Color =
    if (isDark) {
        Color(0xFF171725).copy(alpha = 0.98f)
    } else {
        Color(0xFFF9FAFD).copy(alpha = 0.98f)
    }
