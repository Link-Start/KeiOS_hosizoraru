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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
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
import kotlin.math.roundToInt

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
private const val DETENT_SOLIDNESS_START = 0.58f
private const val LIQUID_SHEET_BLUR_SCALE = 0.58f
private const val LIQUID_SHEET_LENS_SCALE = 0.30f
private const val LIQUID_SHEET_REFRACTION_AMOUNT_SCALE = 1.28f
private const val LIQUID_SHEET_BACKGROUND_DEPTH_BLUR_SCALE = 1.48f
private const val LIQUID_SHEET_VISUAL_FRACTION_STEPS = 48f
private val LiquidSheetDetentDragThreshold = 72.dp

enum class LiquidSheetInitialDetent(
    internal val fraction: Float,
) {
    OneThird(DETENT_ONE_THIRD),
    Half(DETENT_HALF),
    ThreeQuarter(DETENT_THREE_QUARTER),
    Full(DETENT_FULL),
}

val LocalLiquidSheetContentOverflowReporter =
    compositionLocalOf<(Boolean) -> Unit> { {} }
val LocalLiquidSheetContentScrollStateReporter =
    compositionLocalOf<(Boolean) -> Unit> { {} }
val LocalLiquidSheetManagedScrollableContentReporter =
    compositionLocalOf<(Boolean) -> Unit> { {} }
val LocalLiquidSheetVisibleHeightPx =
    compositionLocalOf<(() -> Int)?> { null }
val LocalLiquidSheetEnabled = compositionLocalOf { true }

/**
 * Liquid glass bottom sheet.
 *
 * The sheet opens at a content-adaptive height. Dragging the top chrome resizes the sheet while the
 * bottom edge stays anchored, so users can temporarily reveal content behind the sheet. Long sheets
 * stop at one third of the available window before an additional downward drag requests dismissal.
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
    val backgroundDepthBlurRadius =
        UiPerformanceBudget.backdropBlur *
                LIQUID_SHEET_BACKGROUND_DEPTH_BLUR_SCALE *
                glassRuntime.blurScaleFor(GlassVariant.Floating)

    var managedScrollableContent by remember(show) { mutableStateOf(false) }
    var scrollableContentOverflowsOpeningDetent by remember(show, initialDetent) { mutableStateOf(false) }
    var plainContentExceedsOpeningDetent by remember(show, initialDetent) { mutableStateOf(false) }
    var contentCanScrollUp by remember(show) { mutableStateOf(false) }
    val adaptedInitialDetent =
        liquidSheetAdaptedInitialDetent(
            initialDetent = initialDetent,
            contentOverflowsOpeningDetent = plainContentExceedsOpeningDetent,
        )

    val targetFraction = adaptedInitialDetent.fraction
    val minHeight = liquidSheetMinHeight(targetFraction)
    val minimumFloatingHeight = liquidSheetMinHeight(DETENT_ONE_THIRD)
    val openingMinHeight = liquidSheetMinHeight(initialDetent.fraction)
    val contentDetentHeight = (minHeight - LiquidSheetEstimatedChromeHeight).coerceAtLeast(0.dp)
    val openingContentMinHeight = (openingMinHeight - LiquidSheetEstimatedChromeHeight).coerceAtLeast(0.dp)
    val resolvedSheetMaxWidth = liquidSheetMaxWidth(sheetMaxWidth)
    val density = LocalDensity.current
    val visualDetentFraction = remember(show) { mutableFloatStateOf(targetFraction) }

    LaunchedEffect(show, targetFraction) {
        if (show) {
            visualDetentFraction.floatValue = liquidSheetQuantizedVisualDetentFraction(targetFraction)
        }
    }

    val animatedContentDetentHeight = animateDpAsState(
        targetValue = contentDetentHeight,
        label = "liquid_sheet_detent_content_height",
    )
    val shouldBoundManagedScrollableContent = managedScrollableContent && scrollableContentOverflowsOpeningDetent
    val sheetShape = RoundedRectangle(cornerRadius)
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
                    Shadow.Default.copy(
                        color = Color.Black.copy(alpha = if (isDark) 0.20f else 0.13f)
                    )
                },
                innerShadow = {
                    InnerShadow(radius = 7.dp, alpha = if (isDark) 0.16f else 0.10f)
                },
                onDrawSurface = {
                    drawRect(
                        liquidSheetGlassSurfaceColor(
                            isDark = isDark,
                            detentFraction = visualDetentFraction.floatValue,
                        )
                    )
                },
                onDrawFront = {
                    val solidness = liquidSheetSolidness(visualDetentFraction.floatValue)
                    val topEdgeColor =
                        if (isDark) {
                            Color.White.copy(alpha = lerp(0.12f, 0.16f, solidness))
                        } else {
                            Color.White.copy(alpha = lerp(0.54f, 0.62f, solidness))
                        }
                    drawLine(
                        color = topEdgeColor,
                        start = Offset(x = cornerRadius.toPx(), y = 1.dp.toPx()),
                        end = Offset(x = size.width - cornerRadius.toPx(), y = 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                    drawRect(
                        Color.Black.copy(
                            alpha = if (isDark) {
                                lerp(0.018f, 0.028f, solidness)
                            } else {
                                lerp(0.012f, 0.018f, solidness)
                            },
                        )
                    )
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
        contentCanScrollUp = { contentCanScrollUp },
        backgroundDepthBlurRadius = backgroundDepthBlurRadius,
        onVisibleHeightFractionChanged = { fraction ->
            val nextFraction = liquidSheetQuantizedVisualDetentFraction(fraction)
            if (visualDetentFraction.floatValue != nextFraction) {
                visualDetentFraction.floatValue = nextFraction
            }
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Short sheets keep their natural height. Overflowing opening content gets a
                    // bounded viewport so the sheet can scroll internally without forcing blank
                    // space into compact sheets. After the user resizes the sheet, this viewport
                    // follows the sheet height from the layout phase.
                    .then(
                        if (shouldBoundManagedScrollableContent) {
                            val visibleHeightPxProvider = LocalLiquidSheetVisibleHeightPx.current
                            val estimatedChromeHeightPx =
                                with(density) { LiquidSheetEstimatedChromeHeight.toPx().roundToInt() }
                            Modifier.liquidSheetContentMaxHeightPx {
                                val openingContentHeightPx =
                                    with(density) {
                                        animatedContentDetentHeight.value.toPx().roundToInt()
                                    }
                                val resizedContentHeightPx =
                                    visibleHeightPxProvider
                                        ?.invoke()
                                        ?.minus(estimatedChromeHeightPx)
                                        ?.coerceAtLeast(0)
                                        ?: 0
                                maxOf(openingContentHeightPx, resizedContentHeightPx)
                            }
                        } else {
                            Modifier
                        }
                    )
                    .onSizeChanged { size ->
                        if (initialDetent != LiquidSheetInitialDetent.ThreeQuarter) return@onSizeChanged
                        val openingContentMinHeightPx =
                            with(density) {
                                openingContentMinHeight.toPx()
                            }
                        if (size.height > openingContentMinHeightPx + 1f) {
                            if (managedScrollableContent) {
                                scrollableContentOverflowsOpeningDetent = true
                            } else {
                                plainContentExceedsOpeningDetent = true
                            }
                        }
                    },
        ) {
            CompositionLocalProvider(
                LocalLiquidSheetContentOverflowReporter provides { overflows ->
                    if (overflows) scrollableContentOverflowsOpeningDetent = true
                },
                LocalLiquidSheetContentScrollStateReporter provides { canScrollUp ->
                    contentCanScrollUp = canScrollUp
                },
                LocalLiquidSheetManagedScrollableContentReporter provides { managed ->
                    managedScrollableContent = managed
                    if (managed) plainContentExceedsOpeningDetent = false
                },
                LocalLiquidParentBackdrop provides if (useLiquidBackdropSurface) sheetBackdrop else null,
            ) {
                content()
            }
        }
    }
}

private fun Modifier.liquidSheetContentMaxHeightPx(
    maxHeightPx: () -> Int,
): Modifier =
    layout { measurable, constraints ->
        val resolvedMaxHeight = maxHeightPx().coerceIn(0, constraints.maxHeight)
        val placeable =
            measurable.measure(
                constraints.copy(
                    minHeight = constraints.minHeight.coerceAtMost(resolvedMaxHeight),
                    maxHeight = resolvedMaxHeight,
                )
            )
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
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

internal fun liquidSheetSurfaceColor(
    isDark: Boolean,
    detentFraction: Float,
): Color {
    val solidness = liquidSheetSolidness(detentFraction)
    return if (isDark) {
        Color(0xFF141420).copy(alpha = lerp(0.90f, 0.99f, solidness))
    } else {
        Color(0xFFF8F9FC).copy(alpha = lerp(0.87f, 0.99f, solidness))
    }
}

internal fun liquidSheetGlassSurfaceColor(
    isDark: Boolean,
    detentFraction: Float,
): Color {
    val solidness = liquidSheetSolidness(detentFraction)
    return if (isDark) {
        Color(0xFF141420).copy(alpha = lerp(0.34f, 0.58f, solidness))
    } else {
        Color(0xFFF8F9FC).copy(alpha = lerp(0.28f, 0.50f, solidness))
    }
}

private fun liquidSheetDragHandleColor(
    isDark: Boolean,
    detentFraction: Float,
): Color {
    val solidness = liquidSheetSolidness(detentFraction)
    return if (isDark) {
        Color.White.copy(alpha = lerp(0.38f, 0.28f, solidness))
    } else {
        Color.Black.copy(alpha = lerp(0.28f, 0.20f, solidness))
    }
}

internal fun liquidSheetSolidness(detentFraction: Float): Float {
    val linear =
        (
            (detentFraction - DETENT_SOLIDNESS_START) /
                (DETENT_FULL - DETENT_SOLIDNESS_START)
        ).coerceIn(0f, 1f)
    return linear * linear * (3f - 2f * linear)
}

internal fun liquidSheetQuantizedVisualDetentFraction(detentFraction: Float): Float {
    val steps = LIQUID_SHEET_VISUAL_FRACTION_STEPS
    return ((detentFraction.coerceIn(0f, 1f) * steps).roundToInt() / steps)
        .coerceIn(0f, 1f)
}

fun liquidSheetAdaptedInitialDetent(
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
