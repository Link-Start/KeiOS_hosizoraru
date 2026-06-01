package os.kei.ui.page.main.widget.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop

@Immutable
data class GlassEffectRuntime(
    val reducedProgress: Float = 0f
) {
    fun blurScaleFor(variant: GlassVariant): Float = lerp(
        start = 1f,
        stop = when (variant) {
            GlassVariant.Content -> 0.62f
            GlassVariant.Floating -> 0.66f
            else -> 0.70f
        },
        fraction = reducedProgress
    )

    fun lensScaleFor(variant: GlassVariant): Float = lerp(
        start = 1f,
        stop = when (variant) {
            GlassVariant.Content -> 0.58f
            GlassVariant.Floating -> 0.62f
            else -> 0.66f
        },
        fraction = reducedProgress
    )

    val interactionLensScale: Float
        get() = lerp(
            start = 1f,
            stop = 0.84f,
            fraction = reducedProgress
        )
}

val LocalGlassEffectRuntime = compositionLocalOf { GlassEffectRuntime() }

@Composable
@ReadOnlyComposable
fun glassEffectRuntime(): GlassEffectRuntime = LocalGlassEffectRuntime.current

@Composable
@ReadOnlyComposable
fun appGlassRuntimeEffectsEnabled(): Boolean =
    LocalLiquidControlsEnabled.current

@Composable
@ReadOnlyComposable
fun activeGlassBackdrop(backdrop: Backdrop?): Backdrop? =
    backdrop.takeIf { appGlassRuntimeEffectsEnabled() }

@Composable
@ReadOnlyComposable
fun resolvedGlassBlurDp(
    base: Dp,
    variant: GlassVariant
): Dp = (base * glassEffectRuntime().blurScaleFor(variant)).clampGlassBlur()

@Composable
@ReadOnlyComposable
fun resolvedGlassLensDp(
    base: Dp,
    variant: GlassVariant
): Dp = base * glassEffectRuntime().lensScaleFor(variant)
