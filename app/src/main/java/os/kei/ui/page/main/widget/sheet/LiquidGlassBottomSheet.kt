@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
private val LiquidSheetDetentDragThreshold = 72.dp

enum class LiquidSheetInitialDetent(
    internal val fraction: Float,
) {
    Half(DETENT_HALF),
    ThreeQuarter(DETENT_THREE_QUARTER),
    Full(DETENT_FULL),
}

internal val LocalLiquidSheetContentOverflowReporter =
    compositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Multi-detent liquid glass bottom sheet.
 *
 * Supports three height detents (Full / ThreeQuarter / Half). The sheet opens at a
 * content-adaptive initial detent, then users can drag the top chrome down to peek behind the
 * sheet and drag it up again to restore more room for sheet content.
 *
 * Implementation note: miuix's `BottomSheetContentLayout` has a binary settle (snap back or
 * dismiss) with no intermediate anchors. We work around this by intercepting `onDismissRequest`:
 * when not at the lowest detent, we drop the active detent and increment a generation key that
 * remounts the sheet at the new height, with the enter animation providing the smooth transition.
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

    var contentOverflowsOpeningDetent by remember(show, initialDetent) { mutableStateOf(false) }
    val adaptedInitialDetent =
        liquidSheetAdaptedInitialDetent(
            initialDetent = initialDetent,
            contentOverflowsOpeningDetent = contentOverflowsOpeningDetent,
        )

    // activeDetent starts at the content-adaptive initial and can be lowered by dismiss gestures.
    // `userHasChangedDetent` prevents the overflow-adaptation from overriding a user-driven drop.
    var activeDetent by remember(show) { mutableStateOf(adaptedInitialDetent) }
    var userHasChangedDetent by remember(show) { mutableStateOf(false) }
    // One-time upward adaptation: if content overflow is detected BEFORE the user has interacted,
    // promote to Full. Once the user has manually dropped a detent, never override their choice.
    // Use LaunchedEffect so this only fires as a side-effect of overflow detection, not on every
    // recomposition (which would fight the user's detent drop on remount).
    LaunchedEffect(contentOverflowsOpeningDetent, userHasChangedDetent) {
        if (contentOverflowsOpeningDetent && !userHasChangedDetent) {
            activeDetent = adaptedInitialDetent
        }
    }
    // Generation key: incrementing forces a clean remount of the underlying miuix sheet so its
    // internal dragOffsetY resets and the enter animation plays at the new detent height.
    var sheetGeneration by remember(show) { mutableIntStateOf(0) }

    val targetFraction = activeDetent.fraction
    val minHeight = liquidSheetMinHeight(targetFraction)
    val openingMinHeight = liquidSheetMinHeight(initialDetent.fraction)
    val contentMinHeight = (minHeight - LiquidSheetEstimatedChromeHeight).coerceAtLeast(0.dp)
    val openingContentMinHeight = (openingMinHeight - LiquidSheetEstimatedChromeHeight).coerceAtLeast(0.dp)
    val resolvedSheetMaxWidth = liquidSheetMaxWidth(sheetMaxWidth)
    val density = LocalDensity.current

    val lowestDetent = LiquidSheetInitialDetent.Half
    fun moveToDetent(detent: LiquidSheetInitialDetent) {
        if (activeDetent == detent) return
        activeDetent = detent
        userHasChangedDetent = true
        sheetGeneration++
    }

    // key() forces a clean remount when the detent drops, so miuix's internal dragOffsetY resets
    // and the sheet re-enters at the new height with its spring animation.
    key(sheetGeneration) {
        WindowBottomSheet(
            show = show,
            modifier =
                modifier.liquidSheetTopChromeExpandGesture(
                    enabled = show && activeDetent != LiquidSheetInitialDetent.Full,
                    chromeHeight = LiquidSheetEstimatedChromeHeight,
                    dragThreshold = LiquidSheetDetentDragThreshold,
                    onExpandDetent = {
                        activeDetent.expandedDetentOrNull()?.let(::moveToDetent)
                    },
                ),
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
                if (!allowDismiss) {
                    onBlockedDismissRequest?.invoke()
                    return@WindowBottomSheet
                }
                if (activeDetent != lowestDetent) {
                    moveToDetent(activeDetent.collapsedDetentOrNull() ?: lowestDetent)
                } else {
                    onDismissRequest?.invoke()
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
                        // Lock the content area to the active detent height. min ensures the sheet
                        // fills the detent even with short content; max caps it so the sheet doesn't
                        // exceed the detent when content is tall (content scrolls internally).
                        // Before the user has interacted, we use a generous max so the overflow
                        // detection in onSizeChanged can still measure the natural content height.
                        .heightIn(
                            min = contentMinHeight,
                            max = if (userHasChangedDetent) contentMinHeight else Dp.Unspecified,
                        )
                        .onSizeChanged { size ->
                            if (userHasChangedDetent) return@onSizeChanged
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

internal fun LiquidSheetInitialDetent.collapsedDetentOrNull(): LiquidSheetInitialDetent? =
    when (this) {
        LiquidSheetInitialDetent.Full -> LiquidSheetInitialDetent.ThreeQuarter
        LiquidSheetInitialDetent.ThreeQuarter -> LiquidSheetInitialDetent.Half
        LiquidSheetInitialDetent.Half -> null
    }

internal fun LiquidSheetInitialDetent.expandedDetentOrNull(): LiquidSheetInitialDetent? =
    when (this) {
        LiquidSheetInitialDetent.Full -> null
        LiquidSheetInitialDetent.ThreeQuarter -> LiquidSheetInitialDetent.Full
        LiquidSheetInitialDetent.Half -> LiquidSheetInitialDetent.ThreeQuarter
    }

private fun Modifier.liquidSheetTopChromeExpandGesture(
    enabled: Boolean,
    chromeHeight: Dp,
    dragThreshold: Dp,
    onExpandDetent: () -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(chromeHeight, dragThreshold, onExpandDetent) {
            val chromeHeightPx = chromeHeight.toPx()
            val dragThresholdPx = dragThreshold.toPx()
            awaitEachGesture {
                val down =
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                if (down.position.y > chromeHeightPx) return@awaitEachGesture

                val pointerId = down.id
                var totalDragY = 0f
                var shouldExpand = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change != null) {
                        totalDragY += change.positionChange().y
                        if (totalDragY < -dragThresholdPx) {
                            shouldExpand = true
                        }
                    }
                } while (event.changes.any { it.pressed })

                if (shouldExpand) {
                    onExpandDetent()
                }
            }
        }
    }
