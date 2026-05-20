@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

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
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private val LiquidSheetCornerRadius = 28.dp
private val LiquidSheetCompactMaxWidth = 480.dp
private val LiquidSheetMediumMaxWidth = 560.dp
private val LiquidSheetInsideMargin = DpSize(width = 20.dp, height = 0.dp)
private val LiquidSheetOutsideMargin = DpSize(width = 0.dp, height = 0.dp)
private val LiquidSheetEstimatedChromeHeight = 72.dp

private const val DETENT_HALF = 0.50f
private const val DETENT_THREE_QUARTER = 0.75f
private const val DETENT_FULL = 1.0f
private const val DETENT_SOLIDNESS_START = 0.75f

enum class LiquidSheetInitialDetent(
    internal val fraction: Float,
) {
    Half(DETENT_HALF),
    ThreeQuarter(DETENT_THREE_QUARTER),
    Full(DETENT_FULL),
}

internal val LocalLiquidSheetContentOverflowReporter =
    compositionLocalOf<(Boolean) -> Unit> { {} }

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
    var contentOverflowsOpeningDetent by remember(show, initialDetent) { mutableStateOf(false) }
    val targetDetent =
        if (
            initialDetent == LiquidSheetInitialDetent.ThreeQuarter &&
            contentOverflowsOpeningDetent
        ) {
            LiquidSheetInitialDetent.Full
        } else {
            initialDetent
        }
    val targetFraction = targetDetent.fraction
    val minHeight = liquidSheetMinHeight(targetFraction)
    val openingMinHeight = liquidSheetMinHeight(initialDetent.fraction)
    val contentMinHeight = (minHeight - LiquidSheetEstimatedChromeHeight).coerceAtLeast(0.dp)
    val openingContentMinHeight = (openingMinHeight - LiquidSheetEstimatedChromeHeight).coerceAtLeast(0.dp)
    val resolvedSheetMaxWidth = liquidSheetMaxWidth(sheetMaxWidth)
    val density = LocalDensity.current

    WindowBottomSheet(
        show = show,
        modifier = modifier,
        title = title,
        startAction = startAction,
        endAction = endAction,
        backgroundColor =
            backgroundColor
                ?: liquidSheetSurfaceColor(
                    isDark = isDark,
                    detentFraction = targetFraction,
                ),
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
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = contentMinHeight)
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
    return availableHeight * fraction.coerceIn(DETENT_HALF, DETENT_FULL)
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
