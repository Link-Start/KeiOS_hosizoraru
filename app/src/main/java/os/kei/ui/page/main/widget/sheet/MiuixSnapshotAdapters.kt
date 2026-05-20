package os.kei.ui.page.main.widget.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.chrome.appWindowHeightPx
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.window.PopupPositionProvider as ComposePopupPositionProvider

enum class SnapshotPopupPlacement {
    Dropdown,
    ButtonEnd,
    ActionBarCenter
}

/**
 * Liquid glass popup expansion animation.
 *
 * Spring-based for a natural, elastic feel — slightly underdamped so the popup settles smoothly
 * without an obvious overshoot. Replaces the linear-feeling Miuix default fraction spec.
 */
private val SnapshotPopupFractionAnimationSpec = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 420f,
    visibilityThreshold = 0.001f
)

/**
 * Liquid glass popup collapse animation — quicker and more linear, so dismissal feels decisive.
 */
private val SnapshotPopupFractionExitAnimationSpec = tween<Float>(
    durationMillis = 180,
    easing = FastOutLinearInEasing
)

/** Alpha fades in slightly slower than the geometric reveal so content remains crisp. */
private val SnapshotPopupAlphaEnterAnimationSpec = tween<Float>(
    durationMillis = 220,
    easing = LinearOutSlowInEasing
)

/** Alpha fades out faster than the geometric collapse to avoid lingering ghost frames. */
private val SnapshotPopupAlphaExitAnimationSpec = tween<Float>(
    durationMillis = 140,
    easing = FastOutLinearInEasing
)

/** Initial scale of the popup before expansion — subtle so it grows into place rather than popping. */
private const val SnapshotPopupInitialScale = 0.88f

/** Vertical offset (in dp) the popup translates from before settling — adds directional cue. */
private const val SnapshotPopupTranslationDp = 8f

@Composable
fun SnapshotWindowListPopup(
    show: Boolean,
    popupModifier: Modifier = Modifier,
    popupPositionProvider: PopupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.Start,
    anchorBounds: IntRect? = null,
    placement: SnapshotPopupPlacement = SnapshotPopupPlacement.Dropdown,
    enableWindowDim: Boolean = false,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    maxHeight: Dp? = null,
    minWidth: Dp = 0.dp,
    maxWidth: Dp? = 280.dp,
    matchAnchorWidth: Boolean = false,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val explicitAnchorBounds = anchorBounds
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val screenHeightPx = appWindowHeightPx()
    val anchorWidthDp = remember(explicitAnchorBounds, density) {
        explicitAnchorBounds?.let { with(density) { it.width.toDp() } } ?: 0.dp
    }
    val resolvedMinWidth = if (matchAnchorWidth) {
        maxOf(minWidth, anchorWidthDp)
    } else {
        minWidth
    }
    val popupMinWidth = maxWidth?.let { resolvedMinWidth.coerceAtMost(it) } ?: resolvedMinWidth
    val opensDownward = remember(explicitAnchorBounds, screenHeightPx) {
        explicitAnchorBounds?.let {
            val availableBelow = screenHeightPx - it.bottom
            val availableAbove = it.top
            availableBelow >= availableAbove
        } ?: true
    }
    val normalizedAlignment = remember(alignment, layoutDirection) {
        alignment.normalizeForDropdown(layoutDirection)
    }
    val popupTransformOrigin = remember(normalizedAlignment, placement, opensDownward) {
        val pivotX = when (placement) {
            SnapshotPopupPlacement.Dropdown -> {
                if (normalizedAlignment == PopupPositionProvider.Align.End) 1f else 0f
            }
            SnapshotPopupPlacement.ButtonEnd -> 1f
            SnapshotPopupPlacement.ActionBarCenter -> 0.5f
        }
        val pivotY = if (opensDownward) 0f else 1f
        TransformOrigin(pivotFractionX = pivotX, pivotFractionY = pivotY)
    }
    val popupShowBelow = opensDownward
    val popupShowAbove = !opensDownward
    val fractionProgress = remember { Animatable(0f) }
    val alphaProgress = remember { Animatable(0f) }
    var wasVisible by remember { mutableStateOf(false) }
    var popupRender by remember { mutableStateOf(false) }
    val composePopupPositionProvider = remember(
        density,
        popupPositionProvider,
        alignment,
        placement,
        explicitAnchorBounds
    ) {
        object : ComposePopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val effectiveAnchorBounds = explicitAnchorBounds ?: anchorBounds
                val normalizedAlignment = alignment.normalizeForDropdown(layoutDirection)
                val popupMargin = popupPositionProvider.getMargins().toIntRect(density, layoutDirection)
                val windowBounds = IntRect(0, 0, windowSize.width, windowSize.height)
                val offsetY = calculateDropdownVerticalOffset(
                    anchorBounds = effectiveAnchorBounds,
                    windowBounds = windowBounds,
                    popupContentSize = popupContentSize,
                    popupMargin = popupMargin
                )
                val minX = windowBounds.left + popupMargin.left
                val maxX = (windowBounds.right - popupContentSize.width - popupMargin.right)
                    .coerceAtLeast(minX)
                val rawX = when (placement) {
                    SnapshotPopupPlacement.Dropdown -> {
                        if (normalizedAlignment == PopupPositionProvider.Align.End) {
                            effectiveAnchorBounds.right - popupContentSize.width - popupMargin.right
                        } else {
                            effectiveAnchorBounds.left + popupMargin.left
                        }
                    }

                    SnapshotPopupPlacement.ButtonEnd -> {
                        effectiveAnchorBounds.right - popupContentSize.width - popupMargin.right
                    }

                    SnapshotPopupPlacement.ActionBarCenter -> {
                        effectiveAnchorBounds.left + (effectiveAnchorBounds.width - popupContentSize.width) / 2
                    }
                }
                return IntOffset(rawX.coerceIn(minX, maxX), offsetY)
            }
        }
    }

    LaunchedEffect(show, transitionAnimationsEnabled, onDismissFinished) {
        if (show) {
            wasVisible = true
            popupRender = true
            if (transitionAnimationsEnabled) {
                launch {
                    fractionProgress.animateTo(1f, SnapshotPopupFractionAnimationSpec)
                }
                alphaProgress.animateTo(1f, SnapshotPopupAlphaEnterAnimationSpec)
            } else {
                fractionProgress.snapTo(1f)
                alphaProgress.snapTo(1f)
            }
        } else {
            if (!popupRender && !wasVisible) return@LaunchedEffect
            if (transitionAnimationsEnabled) {
                // Run both animations in parallel and wait for BOTH to complete before
                // removing the Popup. Previously fractionProgress.stop() was called after
                // alpha finished, which killed the fraction animation mid-flight and caused
                // the dropdown to "flash disappear" without a visible collapse.
                val fractionJob = launch {
                    fractionProgress.animateTo(0f, SnapshotPopupFractionExitAnimationSpec)
                }
                val alphaJob = launch {
                    alphaProgress.animateTo(0f, SnapshotPopupAlphaExitAnimationSpec)
                }
                fractionJob.join()
                alphaJob.join()
            } else {
                fractionProgress.snapTo(0f)
                alphaProgress.snapTo(0f)
            }
            popupRender = false
            if (wasVisible) {
                wasVisible = false
                onDismissFinished?.invoke()
            }
        }
    }

    if (popupRender) {
        Popup(
            popupPositionProvider = composePopupPositionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                clippingEnabled = false
            )
        ) {
            val fraction = fractionProgress.value.coerceIn(0f, 1f)
            // Smooth, non-linear easing on scale + translation: starts at 0.88 (subtle) and grows
            // to 1.0 with the spring. Linear interpolation here would feel mechanical against the
            // spring-based fraction curve.
            val scale = SnapshotPopupInitialScale + (1f - SnapshotPopupInitialScale) * fraction
            // Directional translation: slides from above when opening downward, from below when
            // opening upward. The pivot Y already provides the visual anchor; this adds spatial
            // continuity from the trigger button.
            val translationOffsetPx = with(density) { SnapshotPopupTranslationDp.dp.toPx() }
            val translationY = if (popupShowBelow) {
                -translationOffsetPx * (1f - fraction)
            } else {
                translationOffsetPx * (1f - fraction)
            }
            Box(
                modifier = popupModifier
                    .defaultMinSize(minWidth = popupMinWidth)
                    .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                    .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.translationY = translationY
                        alpha = alphaProgress.value.coerceIn(0f, 1f)
                        transformOrigin = popupTransformOrigin
                    }
                    .drawWithContent {
                        val progress = fractionProgress.value.coerceIn(0f, 1f)
                        val showMiddle = !popupShowBelow && !popupShowAbove
                        val clipStart = when {
                            popupShowAbove -> size.height * (1f - progress)
                            showMiddle -> size.height * (0.5f - 0.5f * progress)
                            else -> 0f
                        }
                        val clipBottom = when {
                            popupShowBelow -> size.height * progress
                            popupShowAbove -> size.height
                            showMiddle -> size.height * (0.5f + 0.5f * progress)
                            else -> size.height
                        }
                        if (clipBottom > clipStart) {
                            clipRect(
                                left = 0f,
                                top = clipStart,
                                right = size.width,
                                bottom = clipBottom
                            ) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
            ) {
                content()
            }
        }
    }
}

@Composable
fun SnapshotWindowBottomSheet(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    backgroundColor: Color = BottomSheetDefaults.backgroundColor(),
    enableWindowDim: Boolean = true,
    cornerRadius: Dp = BottomSheetDefaults.cornerRadius,
    sheetMaxWidth: Dp = BottomSheetDefaults.maxWidth,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = BottomSheetDefaults.outsideMargin,
    insideMargin: DpSize = DpSize(BottomSheetDefaults.insideMargin.width, 14.dp),
    defaultWindowInsetsPadding: Boolean = true,
    dragHandleColor: Color = BottomSheetDefaults.dragHandleColor(),
    allowDismiss: Boolean = true,
    onBlockedDismissRequest: (() -> Unit)? = null,
    enableNestedScroll: Boolean = true,
    initialDetent: LiquidSheetInitialDetent = LiquidSheetInitialDetent.ThreeQuarter,
    useLiquidGlassSheet: Boolean = os.kei.core.prefs.UiPrefs.isLiquidSheetEnabled(),
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var wasShown by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            wasShown = true
        } else if (wasShown) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    if (useLiquidGlassSheet) {
        LiquidGlassBottomSheet(
            show = show,
            modifier = modifier,
            title = title,
            startAction = startAction,
            endAction = endAction,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            allowDismiss = allowDismiss,
            onBlockedDismissRequest = onBlockedDismissRequest,
            initialDetent = initialDetent,
            content = content,
        )
        return
    }

    val currentOnBlockedDismissRequest by rememberUpdatedState(onBlockedDismissRequest)
    var blockedDismissPromptToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(blockedDismissPromptToken) {
        if (blockedDismissPromptToken <= 0) return@LaunchedEffect
        delay(BlockedSheetDismissPromptDelayMillis)
        currentOnBlockedDismissRequest?.invoke()
    }

    WindowBottomSheet(
        show = show,
        modifier = modifier.blockedSheetDismissGesturePrompt(
            enabled = show && !allowDismiss && onBlockedDismissRequest != null,
            onBlockedDismissRequest = { blockedDismissPromptToken++ }
        ),
        title = title,
        startAction = startAction,
        endAction = endAction,
        backgroundColor = backgroundColor,
        enableWindowDim = enableWindowDim,
        cornerRadius = cornerRadius,
        sheetMaxWidth = sheetMaxWidth,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        dragHandleColor = dragHandleColor,
        allowDismiss = allowDismiss,
        enableNestedScroll = enableNestedScroll,
        content = content,
    )

    BackHandler(
        enabled = show && !allowDismiss && onBlockedDismissRequest != null,
        onBack = { currentOnBlockedDismissRequest?.invoke() }
    )
}

private const val BlockedSheetDismissPromptDelayMillis = 140L

private fun Modifier.blockedSheetDismissGesturePrompt(
    enabled: Boolean,
    onBlockedDismissRequest: () -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(onBlockedDismissRequest) {
        val topGestureRegionPx = 72.dp.toPx()
        val dismissIntentThresholdPx = 42.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )
            if (down.position.y > topGestureRegionPx) {
                return@awaitEachGesture
            }
            var totalX = 0f
            var totalY = 0f
            var hasDismissIntent = false
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: event.changes.firstOrNull()
                    ?: break
                if (!change.pressed) break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                if (
                    !hasDismissIntent &&
                    totalY > dismissIntentThresholdPx &&
                    totalY > abs(totalX) * 1.35f
                ) {
                    hasDismissIntent = true
                }
            }
            if (hasDismissIntent) {
                onBlockedDismissRequest()
            }
        }
    }
}

private fun PaddingValues.toIntRect(
    density: androidx.compose.ui.unit.Density,
    layoutDirection: LayoutDirection
): IntRect = with(density) {
    IntRect(
        left = calculateLeftPadding(layoutDirection).roundToPx(),
        top = calculateTopPadding().roundToPx(),
        right = calculateRightPadding(layoutDirection).roundToPx(),
        bottom = calculateBottomPadding().roundToPx()
    )
}

private fun PopupPositionProvider.Align.normalizeForDropdown(layoutDirection: LayoutDirection): PopupPositionProvider.Align {
    return when (this) {
        PopupPositionProvider.Align.End,
        PopupPositionProvider.Align.TopEnd,
        PopupPositionProvider.Align.BottomEnd -> {
            if (layoutDirection == LayoutDirection.Ltr) PopupPositionProvider.Align.End
            else PopupPositionProvider.Align.Start
        }

        PopupPositionProvider.Align.Start,
        PopupPositionProvider.Align.TopStart,
        PopupPositionProvider.Align.BottomStart -> {
            if (layoutDirection == LayoutDirection.Ltr) PopupPositionProvider.Align.Start
            else PopupPositionProvider.Align.End
        }
    }
}

private fun calculateDropdownVerticalOffset(
    anchorBounds: IntRect,
    windowBounds: IntRect,
    popupContentSize: IntSize,
    popupMargin: IntRect
): Int {
    val availableBelow = windowBounds.bottom - anchorBounds.bottom - popupMargin.bottom
    val availableAbove = anchorBounds.top - windowBounds.top - popupMargin.top
    val preferBelow = availableBelow >= popupContentSize.height || availableBelow >= availableAbove
    val rawY = if (preferBelow) {
        anchorBounds.bottom + popupMargin.bottom
    } else {
        anchorBounds.top - popupContentSize.height - popupMargin.top
    }
    val minY = (windowBounds.top + popupMargin.top)
        .coerceAtMost(windowBounds.bottom - popupContentSize.height - popupMargin.bottom)
    val maxY = windowBounds.bottom - popupContentSize.height - popupMargin.bottom
    return rawY.coerceIn(minY, maxY)
}

fun Modifier.capturePopupAnchor(onBoundsChange: (IntRect) -> Unit): Modifier {
    return this.onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) return@onGloballyPositioned
        val position = coordinates.positionInWindow()
        if (!position.x.isFinite() || !position.y.isFinite()) return@onGloballyPositioned
        val right = position.x + coordinates.size.width
        val bottom = position.y + coordinates.size.height
        if (!right.isFinite() || !bottom.isFinite()) return@onGloballyPositioned
        onBoundsChange(
            IntRect(
                left = position.x.roundToInt(),
                top = position.y.roundToInt(),
                right = right.roundToInt(),
                bottom = bottom.roundToInt()
            )
        )
    }
}
