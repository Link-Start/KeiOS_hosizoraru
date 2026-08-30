@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.sheet.LocalSceneBackdrop

/**
 * A capsule is mostly rim, so it takes a larger share of the lens budget than a sheet or an alert
 * does — the refraction *is* the shape here.
 */
private const val LIQUID_TOAST_LENS_SCALE = 0.62f
private const val LIQUID_TOAST_REFRACTION_AMOUNT_SCALE = 1.15f

internal class LiquidToastSurface(
    val modifier: Modifier,
    val glassEnabled: Boolean,
)

/**
 * The toast pill's material.
 *
 * Its own surface rather than a [LiquidSurface] call, for the same reason the sheet and the alert have
 * theirs: the enter/exit transform has to go through `drawBackdrop`'s `layerBlock`, and
 * [LiquidSurface] owns that parameter for its press interaction. See [LiquidToastHost] for why that
 * matters — in short, a scale applied *around* `drawBackdrop` scales the sampled backdrop with the
 * pill, which the library documents as the wrong result and which is what
 * `AnimatedVisibility(scaleIn/scaleOut)` was doing here.
 *
 * [transformProvider] receives the layer scope directly so the caller owns the whole transform
 * (scale, alpha) in one place, and the fallback path can reuse it verbatim.
 */
@Composable
internal fun rememberLiquidToastSurface(
    isDark: Boolean,
    explicitBackdrop: Backdrop?,
    transformProvider: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit,
): LiquidToastSurface {
    val liquidControlsEnabled = LocalLiquidControlsEnabled.current
    val sceneBackdrop = LocalSceneBackdrop.current
    // Outside a SceneBackdropHost the scene backdrop is `emptyBackdrop()`, whose drawBackdrop body is
    // empty — asking it for blur yields a *transparent* pill, not a blurred one. So previews and
    // Robolectric harnesses take the opaque path unless they hand in a real backdrop themselves.
    val inOverlay = LocalLiquidOverlayHost.current != null
    val resolvedBackdrop = explicitBackdrop ?: sceneBackdrop.takeIf { inOverlay }
    val glassEnabled = liquidControlsEnabled && resolvedBackdrop != null

    if (!glassEnabled) {
        return LiquidToastSurface(
            modifier = Modifier
                .graphicsLayer(block = transformProvider)
                .background(
                    color = liquidToastFallbackFill(isDark),
                    shape = ContinuousCapsule,
                ),
            glassEnabled = false,
        )
    }

    val blurRadius = presentationGlassBlur()
    val lens =
        presentationGlassLens(
            lensScale = LIQUID_TOAST_LENS_SCALE,
            refractionScale = LIQUID_TOAST_REFRACTION_AMOUNT_SCALE,
        )
    val fill = liquidToastGlassFill(isDark)

    return LiquidToastSurface(
        modifier = Modifier.drawBackdrop(
            backdrop = resolvedBackdrop,
            shape = { ContinuousCapsule },
            effects = {
                vibrancy()
                blur(blurRadius.toPx())
                safeLiquidLens(
                    lens.refractionHeight.toPx(),
                    lens.refractionAmount.toPx(),
                    // A toast can land over a photo or a busy list, and a capsule has a lot of rim
                    // relative to its area, so the extra fringe is visible where a sheet's is not.
                    chromaticAberration = true,
                    depthEffect = true,
                )
            },
            layerBlock = transformProvider,
            highlight = { Highlight.Default.copy(alpha = if (isDark) 0.70f else 0.88f) },
            shadow = {
                Shadow.Default.copy(
                    color = Color.Black.copy(alpha = if (isDark) 0.34f else 0.20f),
                )
            },
            innerShadow = { InnerShadow(radius = 8.dp, alpha = if (isDark) 0.22f else 0.14f) },
            onDrawSurface = { drawRect(fill) },
        ),
        glassEnabled = true,
    )
}

/**
 * The tint painted over the blurred sample.
 *
 * This is the fix for the toast's one outright bug: it used `Color.White.copy(alpha = 0.5f)` in *both*
 * themes, which is the identical mistake the pre-rewrite dialog made — a pale card on a dark app,
 * with `onBackground` text that is itself near-white in dark mode.
 *
 * Kept a little heavier than [liquidModalGlassFill] because a toast is smaller than an alert and gets
 * no scrim to separate it from the page, so the fill is the only contrast it has. The base tones match
 * the alert's so the family reads as one material.
 */
internal fun liquidToastGlassFill(isDark: Boolean): Color =
    if (isDark) {
        Color(0xFF171725).copy(alpha = 0.86f)
    } else {
        Color(0xFFF9FAFD).copy(alpha = 0.84f)
    }

/** Used when there is no usable backdrop, so it has to be opaque enough to stand on its own. */
internal fun liquidToastFallbackFill(isDark: Boolean): Color =
    if (isDark) {
        Color(0xFF171725).copy(alpha = 0.98f)
    } else {
        Color(0xFFF9FAFD).copy(alpha = 0.98f)
    }
