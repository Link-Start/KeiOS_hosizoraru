package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.shape.drawAppSquircleBorder

data class AppLiquidSearchMaterialColors(
    val overlayTop: Color,
    val overlayBottom: Color,
    val centerGlow: Color,
    val bottomGlow: Color,
    val sideRim: Color,
    val innerRim: Color,
    val edge: Color,
)

fun appLiquidSearchMaterialColors(
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

fun appLiquidSearchHighlightAlpha(
    baseAlpha: Float,
    materialProgress: Float,
    isDark: Boolean,
    darkMaxAlpha: Float = 0.34f,
): Float {
    val targetAlpha = baseAlpha + 0.10f * materialProgress
    return targetAlpha.coerceAtMost(if (isDark) darkMaxAlpha else 1f)
}

fun appLiquidSearchPlaceholderColor(
    contentColor: Color,
    variantColor: Color,
    isDark: Boolean,
): Color =
    if (isDark) {
        contentColor.copy(alpha = 0.84f)
    } else {
        variantColor.copy(alpha = 0.78f)
    }

fun appLiquidSearchMaterialOverlayModifier(
    cornerRadius: Dp,
    colors: AppLiquidSearchMaterialColors,
    focusProgress: Float,
    pressProgress: Float,
): Modifier =
    appLiquidSearchMaterialOverlayModifier(
        cornerRadius = cornerRadius,
        colors = colors,
        focusProgress = { focusProgress },
        pressProgress = { pressProgress },
    )

fun appLiquidSearchMaterialOverlayModifier(
    cornerRadius: Dp,
    colors: AppLiquidSearchMaterialColors,
    focusProgress: () -> Float,
    pressProgress: () -> Float,
): Modifier =
    Modifier
        .drawWithCache {
            val overlayBrush = Brush.verticalGradient(colors = listOf(colors.overlayTop, colors.overlayBottom))
            val sideBrush =
                Brush.horizontalGradient(
                    colors =
                        listOf(
                            colors.sideRim,
                            Color.Transparent,
                            Color.Transparent,
                            colors.sideRim,
                        ),
                )
            onDrawBehind {
                val materialProgress = maxOf(focusProgress(), pressProgress())
                drawRect(overlayBrush)
                drawRect(sideBrush)
                drawRect(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                colors.centerGlow.copy(
                                    alpha = (colors.centerGlow.alpha + 0.055f * materialProgress).coerceAtMost(1f),
                                ),
                                Color.Transparent,
                            ),
                    ),
                )
                drawRect(
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0.00f to Color.Transparent,
                                0.62f to Color.Transparent,
                                1.00f to
                                    colors.bottomGlow.copy(
                                        alpha = (colors.bottomGlow.alpha + 0.035f * materialProgress).coerceAtMost(1f),
                                    ),
                            ),
                    ),
                )
            }
        }.drawAppSquircleBorder(
            width = 1.1.dp,
            cornerRadius = cornerRadius,
        ) {
            val materialProgress = maxOf(focusProgress(), pressProgress())
            colors.edge.copy(
                alpha = (colors.edge.alpha + 0.05f * materialProgress).coerceAtMost(1f),
            )
        }.drawAppSquircleBorder(
            width = 1.dp,
            cornerRadius = cornerRadius,
        ) {
            val materialProgress = maxOf(focusProgress(), pressProgress())
            colors.innerRim.copy(
                alpha = (colors.innerRim.alpha + 0.08f * materialProgress).coerceAtMost(1f),
            )
        }
