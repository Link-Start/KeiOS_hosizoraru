package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal data class AppLiquidSearchMaterialColors(
    val overlayTop: Color,
    val overlayBottom: Color,
    val centerGlow: Color,
    val bottomGlow: Color,
    val sideRim: Color,
    val innerRim: Color,
    val edge: Color,
)

internal fun appLiquidSearchMaterialColors(
    isDark: Boolean,
    compactMaterial: Boolean = false,
): AppLiquidSearchMaterialColors =
    AppLiquidSearchMaterialColors(
        overlayTop =
            if (isDark) {
                Color.White.copy(alpha = if (compactMaterial) 0.026f else 0.070f)
            } else {
                Color.White.copy(alpha = 0.235f)
            },
        overlayBottom =
            if (isDark) {
                Color(0xFF82B8FF).copy(alpha = if (compactMaterial) 0.022f else 0.060f)
            } else {
                Color(0xFFB9D8FF).copy(alpha = 0.245f)
            },
        centerGlow =
            if (isDark) {
                Color(0xFFBBD9FF).copy(alpha = if (compactMaterial) 0.000f else 0.055f)
            } else {
                Color.White.copy(alpha = 0.340f)
            },
        bottomGlow =
            if (isDark) {
                Color(0xFF73AFFF).copy(alpha = if (compactMaterial) 0.018f else 0.050f)
            } else {
                Color(0xFFC6E0FF).copy(alpha = 0.275f)
            },
        sideRim = if (isDark) Color.White.copy(alpha = if (compactMaterial) 0.034f else 0.075f) else Color.White.copy(alpha = 0.56f),
        innerRim = if (isDark) Color.White.copy(alpha = if (compactMaterial) 0.055f else 0.120f) else Color.White.copy(alpha = 0.84f),
        edge = if (isDark) Color.White.copy(alpha = if (compactMaterial) 0.14f else 0.20f) else Color(0xFF86C3FF).copy(alpha = 0.96f),
    )

internal fun appLiquidSearchHighlightAlpha(
    baseAlpha: Float,
    materialProgress: Float,
    isDark: Boolean,
    darkMaxAlpha: Float = 0.34f,
): Float {
    val targetAlpha = baseAlpha + 0.10f * materialProgress
    return targetAlpha.coerceAtMost(if (isDark) darkMaxAlpha else 1f)
}

internal fun appLiquidSearchPlaceholderColor(
    contentColor: Color,
    variantColor: Color,
    isDark: Boolean,
): Color =
    if (isDark) {
        contentColor.copy(alpha = 0.84f)
    } else {
        variantColor.copy(alpha = 0.78f)
    }

internal fun appLiquidSearchMaterialOverlayModifier(
    shape: Shape,
    colors: AppLiquidSearchMaterialColors,
    focusProgress: Float,
    pressProgress: Float,
): Modifier {
    val materialProgress = maxOf(focusProgress, pressProgress)
    return Modifier
        .background(
            Brush.verticalGradient(colors = listOf(colors.overlayTop, colors.overlayBottom)),
            shape,
        ).background(
            Brush.horizontalGradient(
                colors =
                    listOf(
                        colors.sideRim,
                        Color.Transparent,
                        Color.Transparent,
                        colors.sideRim,
                    ),
            ),
            shape,
        ).background(
            Brush.radialGradient(
                colors =
                    listOf(
                        colors.centerGlow.copy(
                            alpha =
                                (colors.centerGlow.alpha + 0.055f * materialProgress).coerceAtMost(
                                    1f,
                                ),
                        ),
                        Color.Transparent,
                    ),
            ),
            shape,
        ).background(
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0.00f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1.00f to
                            colors.bottomGlow.copy(
                                alpha =
                                    (colors.bottomGlow.alpha + 0.035f * materialProgress).coerceAtMost(
                                        1f,
                                    ),
                            ),
                    ),
            ),
            shape,
        ).border(
            width = 1.1.dp,
            color =
                colors.edge.copy(
                    alpha = (colors.edge.alpha + 0.05f * materialProgress).coerceAtMost(1f),
                ),
            shape = shape,
        ).border(
            width = 1.dp,
            color =
                colors.innerRim.copy(
                    alpha = (colors.innerRim.alpha + 0.08f * materialProgress).coerceAtMost(1f),
                ),
            shape = shape,
        )
}
