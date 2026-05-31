@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults

private val LiquidSheetCornerRadius = 28.dp
private val LiquidSheetCompactMaxWidth = 480.dp
private val LiquidSheetMediumMaxWidth = 560.dp
private val LiquidSheetInsideMargin = DpSize(width = 20.dp, height = 0.dp)
private val LiquidSheetOutsideMargin = DpSize(width = 0.dp, height = 0.dp)
private val LiquidSheetEstimatedChromeHeight = 72.dp

private const val DETENT_ONE_THIRD = 1f / 3f
private const val DETENT_HALF = 0.50f
private const val DETENT_THREE_QUARTER = 0.75f
private const val DETENT_FULL = 1.0f
private const val DETENT_SOLIDNESS_START = 0.75f
private const val LIQUID_SHEET_BLUR_SCALE = 0.40f
private const val LIQUID_SHEET_LENS_SCALE = 0.30f
private const val LIQUID_SHEET_REFRACTION_AMOUNT_SCALE = 1.35f
private val LiquidSheetDetentDragThreshold = 72.dp

enum class LiquidSheetInitialDetent(
    internal val fraction: Float,
) {
    OneThird(DETENT_ONE_THIRD),
    Half(DETENT_HALF),
    ThreeQuarter(DETENT_THREE_QUARTER),
    Full(DETENT_FULL),
}

internal val LocalLiquidSheetContentOverflowReporter =
    compositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Floating liquid glass bottom sheet.
 *
 * The sheet opens at a content-adaptive height. After opening, users can freely drag the top chrome
 * to a temporary hover position for peeking behind the sheet. The lowest hover position keeps one
 * third of the available window visible before an additional downward drag requests dismissal.
 */
@Composable
fun LiquidGlassBottomSheet(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    backgroundColor: Color? = null,
    enableWindowDim: Boolean = true,
    cornerRadius: Dp = LiquidSheetCornerRadius,
    sheetMaxWidth: Dp = BottomSheetDefaults.maxWidth,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = LiquidSheetOutsideMargin,
    insideMargin: DpSize = LiquidSheetInsideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    dragHandleColor: Color? = null,
    allowDismiss: Boolean = true,
    onBlockedDismissRequest: (() -> Unit)? = null,
    enableNestedScroll: Boolean = true,
    initialDetent: LiquidSheetInitialDetent = LiquidSheetInitialDetent.ThreeQuarter,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val sceneBackdrop = LocalSceneBackdrop.current
    val sheetBackdrop = rememberLayerBackdrop()
    val liquidControlsEnabled = LocalLiquidControlsEnabled.current
    val useLiquidBackdropSurface = liquidControlsEnabled && backgroundColor == null
    val glassRuntime = LocalGlassEffectRuntime.current
    val sheetBlurRadius =
        UiPerformanceBudget.backdropBlur *
                LIQUID_SHEET_BLUR_SCALE *
                glassRuntime.blurScaleFor(GlassVariant.Floating)
    val sheetLensRadius =
        UiPerformanceBudget.backdropLens *
                LIQUID_SHEET_LENS_SCALE *
                glassRuntime.lensScaleFor(GlassVariant.Floating)

    var contentOverflowsOpeningDetent by remember(show, initialDetent) { mutableStateOf(false) }
    val adaptedInitialDetent =
        liquidSheetAdaptedInitialDetent(
            initialDetent = initialDetent,
            contentOverflowsOpeningDetent = contentOverflowsOpeningDetent,
        )

    val targetFraction = adaptedInitialDetent.fraction
    val minHeight = liquidSheetMinHeight(targetFraction)
    val minimumFloatingHeight = liquidSheetMinHeight(DETENT_ONE_THIRD)
    val openingMinHeight = liquidSheetMinHeight(initialDetent.fraction)
    val contentDetentHeight = (minHeight - LiquidSheetEstimatedChromeHeight).coerceAtLeast(0.dp)
    val openingContentMinHeight = (openingMinHeight - LiquidSheetEstimatedChromeHeight).coerceAtLeast(0.dp)
    val resolvedSheetMaxWidth = liquidSheetMaxWidth(sheetMaxWidth)
    val density = LocalDensity.current

    val animatedContentDetentHeight by animateDpAsState(
        targetValue = contentDetentHeight,
        label = "liquid_sheet_detent_content_height",
    )
    val sheetShape = RoundedRectangle(cornerRadius)
    val sheetGlassSurfaceColor =
        liquidSheetGlassSurfaceColor(
            isDark = isDark,
            detentFraction = targetFraction,
        )
    val sheetSurfaceModifier =
        if (useLiquidBackdropSurface) {
            Modifier.drawBackdrop(
                backdrop = sceneBackdrop,
                shape = { sheetShape },
                effects = {
                    vibrancy()
                    blur(sheetBlurRadius.toPx())
                    lens(
                        sheetLensRadius.toPx(),
                        (sheetLensRadius * LIQUID_SHEET_REFRACTION_AMOUNT_SCALE).toPx(),
                        chromaticAberration = false,
                        depthEffect = false,
                    )
                },
                exportedBackdrop = sheetBackdrop,
                highlight = {
                    Highlight.Default.copy(alpha = if (isDark) 0.72f else 0.86f)
                },
                shadow = {
                    Shadow.Default.copy(color = Color.Black.copy(alpha = if (isDark) 0.22f else 0.12f))
                },
                innerShadow = {
                    InnerShadow(radius = 8.dp, alpha = if (isDark) 0.18f else 0.12f)
                },
                onDrawSurface = {
                    drawRect(sheetGlassSurfaceColor)
                },
            )
        } else {
            Modifier
        }
    val resolvedBackgroundColor =
        backgroundColor
            ?: if (useLiquidBackdropSurface) {
                Color.Transparent
            } else {
                liquidSheetSurfaceColor(
                    isDark = isDark,
                    detentFraction = targetFraction,
                )
            }
    LiquidDetentWindowBottomSheet(
        show = show,
        modifier = modifier,
        surfaceModifier = sheetSurfaceModifier,
        dragSurfaceColor =
            if (useLiquidBackdropSurface) {
                sheetGlassSurfaceColor
            } else {
                resolvedBackgroundColor
            },
        title = title,
        startAction = startAction,
        endAction = endAction,
        backgroundColor = resolvedBackgroundColor,
        enableWindowDim = enableWindowDim,
        cornerRadius = cornerRadius,
        sheetMaxWidth = resolvedSheetMaxWidth,
        onDismissRequest = {
            if (allowDismiss) {
                onDismissRequest?.invoke()
            } else {
                onBlockedDismissRequest?.invoke()
            }
        },
        onDismissFinished = onDismissFinished,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        dragHandleColor =
            dragHandleColor
                ?: liquidSheetDragHandleColor(
                    isDark = isDark,
                    detentFraction = targetFraction,
                ),
        allowDismiss = allowDismiss,
        enableNestedScroll = enableNestedScroll,
        minimumFloatingHeight = minimumFloatingHeight,
        dismissDragThreshold = LiquidSheetDetentDragThreshold,
        onBlockedDismissRequest = onBlockedDismissRequest,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Short sheets keep their natural height. Overflowing opening content gets a
                    // bounded viewport so the sheet can scroll internally without forcing blank
                    // space into compact sheets.
                    .heightIn(
                        min = 0.dp,
                        max = if (contentOverflowsOpeningDetent) animatedContentDetentHeight else Dp.Unspecified,
                    )
                    .onSizeChanged { size ->
                        if (initialDetent != LiquidSheetInitialDetent.ThreeQuarter) return@onSizeChanged
                        val openingContentMinHeightPx =
                            with(density) {
                                openingContentMinHeight.toPx()
                            }
                        if (size.height > openingContentMinHeightPx + 1f) {
                            contentOverflowsOpeningDetent = true
                        }
                    },
        ) {
            CompositionLocalProvider(
                LocalLiquidSheetContentOverflowReporter provides { overflows ->
                    if (overflows) contentOverflowsOpeningDetent = true
                },
                LocalLiquidParentBackdrop provides if (useLiquidBackdropSurface) sheetBackdrop else null,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun liquidSheetMinHeight(fraction: Float): Dp {
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val safeTopInset =
        maxOf(
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            WindowInsets.captionBar.asPaddingValues().calculateTopPadding(),
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
        )
    val availableHeight = (windowHeight - safeTopInset).coerceAtLeast(0.dp)
    return availableHeight * fraction.coerceIn(DETENT_ONE_THIRD, DETENT_FULL)
}

@Composable
private fun liquidSheetMaxWidth(requestedMaxWidth: Dp): Dp {
    val windowWidth = LocalWindowInfo.current.containerDpSize.width
    val adaptiveMaxWidth =
        when {
            windowWidth >= 840.dp -> BottomSheetDefaults.maxWidth
            windowWidth >= 600.dp -> LiquidSheetMediumMaxWidth
            else -> LiquidSheetCompactMaxWidth
        }
    return minOf(requestedMaxWidth, adaptiveMaxWidth)
}

private fun liquidSheetSurfaceColor(
    isDark: Boolean,
    detentFraction: Float,
): Color {
    val solidness = liquidSheetSolidness(detentFraction)
    return if (isDark) {
        Color(0xFF141420).copy(alpha = lerp(0.88f, 0.985f, solidness))
    } else {
        Color(0xFFF8F9FC).copy(alpha = lerp(0.84f, 0.985f, solidness))
    }
}

private fun liquidSheetGlassSurfaceColor(
    isDark: Boolean,
    detentFraction: Float,
): Color {
    val solidness = liquidSheetSolidness(detentFraction)
    return if (isDark) {
        Color(0xFF141420).copy(alpha = lerp(0.30f, 0.52f, solidness))
    } else {
        Color(0xFFF8F9FC).copy(alpha = lerp(0.24f, 0.42f, solidness))
    }
}

private fun liquidSheetDragHandleColor(
    isDark: Boolean,
    detentFraction: Float,
): Color {
    val solidness = liquidSheetSolidness(detentFraction)
    return if (isDark) {
        Color.White.copy(alpha = lerp(0.34f, 0.26f, solidness))
    } else {
        Color.Black.copy(alpha = lerp(0.24f, 0.18f, solidness))
    }
}

private fun liquidSheetSolidness(detentFraction: Float): Float {
    val linear =
        (
            (detentFraction - DETENT_SOLIDNESS_START) /
                (DETENT_FULL - DETENT_SOLIDNESS_START)
        ).coerceIn(0f, 1f)
    return linear * linear * (3f - 2f * linear)
}

internal fun liquidSheetAdaptedInitialDetent(
    initialDetent: LiquidSheetInitialDetent,
    contentOverflowsOpeningDetent: Boolean,
): LiquidSheetInitialDetent =
    if (
        initialDetent == LiquidSheetInitialDetent.ThreeQuarter &&
        contentOverflowsOpeningDetent
    ) {
        LiquidSheetInitialDetent.Full
    } else {
        initialDetent
    }
