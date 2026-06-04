@file:Suppress("FunctionName")

// Adapted from compose-miuix-ui WindowBottomSheet / BottomSheetContentLayout.
// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package os.kei.ui.page.main.widget.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.RemovePlatformDialogDefaultEffects
import top.yukonga.miuix.kmp.utils.platformDialogProperties
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LIQUID_SHEET_BACKGROUND_MIN_DEPTH = 0.10f
private const val LIQUID_SHEET_BACKGROUND_LOW_TINT_DEPTH = 0.22f
private const val LIQUID_SHEET_BACKGROUND_BLUR_START_DEPTH = 0.34f
private const val LIQUID_SHEET_BLOCKED_DISMISS_RESISTANCE = 0.35f
private const val LIQUID_SHEET_OPEN_CLOSE_DAMPING = 0.90f
private const val LIQUID_SHEET_OPEN_CLOSE_RESPONSE = 0.38f
private const val LIQUID_SHEET_SETTLE_DAMPING = 0.85f
private const val LIQUID_SHEET_SETTLE_RESPONSE = 0.40f
private const val LIQUID_SHEET_RESET_DURATION_MS = 150
private val LiquidSheetDismissVelocityThreshold = 800.dp
private const val LIQUID_SHEET_HANDLE_REST_WIDTH = 45f
private const val LIQUID_SHEET_HANDLE_PRESSED_WIDTH = 55f
private const val LIQUID_SHEET_HANDLE_PRESSED_SCALE = 1.15f
private const val LIQUID_SHEET_HANDLE_PRESS_DURATION_MS = 100
private const val LIQUID_SHEET_HANDLE_RELEASE_DURATION_MS = 150

@Composable
internal fun LiquidDetentWindowBottomSheet(
    show: Boolean,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    backgroundColor: Color,
    enableWindowDim: Boolean,
    cornerRadius: Dp,
    sheetMaxWidth: Dp,
    onDismissRequest: (() -> Unit)?,
    onDismissFinished: (() -> Unit)?,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    defaultWindowInsetsPadding: Boolean,
    dragHandleColor: Color,
    allowDismiss: Boolean,
    enableNestedScroll: Boolean,
    minimumFloatingHeight: Dp,
    dismissDragThreshold: Dp,
    onBlockedDismissRequest: (() -> Unit)?,
    contentCanScrollUp: () -> Boolean = { false },
    backgroundDepthBlurRadius: Dp,
    content: @Composable () -> Unit,
) {
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val captionBarPadding = WindowInsets.captionBar.asPaddingValues().calculateTopPadding()
    val displayCutoutPadding = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val safeTopInset = remember(statusBarsPadding, captionBarPadding, displayCutoutPadding) {
        maxOf(statusBarsPadding, captionBarPadding, displayCutoutPadding)
    }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val requestDismiss: () -> Unit = {
        currentOnDismissRequest?.invoke()
    }

    LiquidDetentBottomSheetContentLayout(
        show = show,
        backgroundColor = backgroundColor,
        cornerRadius = cornerRadius,
        sheetMaxWidth = sheetMaxWidth,
        outsideMargin = outsideMargin,
        insideMargin = insideMargin,
        dragHandleColor = dragHandleColor,
        popupHost = { visible, hostContent ->
            if (visible) {
                Dialog(
                    onDismissRequest = {
                        if (allowDismiss) {
                            requestDismiss()
                        } else {
                            onBlockedDismissRequest?.invoke()
                        }
                    },
                    properties = platformDialogProperties(),
                ) {
                    RemovePlatformDialogDefaultEffects()
                    hostContent()
                }
            }
        },
        modifier = modifier,
        title = title,
        startAction = startAction,
        endAction = endAction,
        enableWindowDim = enableWindowDim,
        onDismissRequest = requestDismiss,
        onDismissFinished = onDismissFinished,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        allowDismiss = allowDismiss,
        enableNestedScroll = enableNestedScroll,
        surfaceModifier = surfaceModifier,
        topInset = safeTopInset,
        minimumFloatingHeight = minimumFloatingHeight,
        dismissDragThreshold = dismissDragThreshold,
        onBlockedDismissRequest = onBlockedDismissRequest,
        contentCanScrollUp = contentCanScrollUp,
        backgroundDepthBlurRadius = backgroundDepthBlurRadius,
        content = {
            CompositionLocalProvider(
                LocalDismissState provides requestDismiss,
            ) {
                content()
            }
        },
    )
}

@Composable
private fun LiquidDetentBottomSheetContentLayout(
    show: Boolean,
    backgroundColor: Color,
    cornerRadius: Dp,
    sheetMaxWidth: Dp,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    dragHandleColor: Color,
    popupHost: @Composable (visible: Boolean, content: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    defaultWindowInsetsPadding: Boolean = true,
    allowDismiss: Boolean = true,
    enableNestedScroll: Boolean = true,
    surfaceModifier: Modifier,
    topInset: Dp,
    minimumFloatingHeight: Dp,
    dismissDragThreshold: Dp,
    onBlockedDismissRequest: (() -> Unit)?,
    contentCanScrollUp: () -> Boolean,
    backgroundDepthBlurRadius: Dp,
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val dismissOffsetY = remember { mutableFloatStateOf(0f) }
    val visibleSheetHeightPx = remember { mutableFloatStateOf(0f) }
    val userResizedSheet = remember { mutableStateOf(false) }
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }
    val sheetInteracting = remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            internalVisible.value = true
            dismissOffsetY.floatValue = 0f
            visibleSheetHeightPx.floatValue = 0f
            userResizedSheet.value = false
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = folmeSpring(
                    damping = LIQUID_SHEET_OPEN_CLOSE_DAMPING,
                    response = LIQUID_SHEET_OPEN_CLOSE_RESPONSE
                ),
            )
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            if (dismissOffsetY.floatValue > 0f) {
                animationProgress.snapTo(0f)
            } else {
                animationProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = folmeSpring(
                        damping = LIQUID_SHEET_OPEN_CLOSE_DAMPING,
                        response = LIQUID_SHEET_OPEN_CLOSE_RESPONSE
                    ),
                )
            }
            internalVisible.value = false
            currentOnDismissFinished?.invoke()
        }
    }

    if (!show && !internalVisible.value) return

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val coroutineScope = rememberCoroutineScope()
    val sheetHeightPx = remember { mutableIntStateOf(0) }
    val dimAlpha = remember { mutableFloatStateOf(1f) }
    val minimumFloatingHeightPx = with(density) { minimumFloatingHeight.toPx() }
    val dismissDragThresholdPx = with(density) { dismissDragThreshold.toPx() }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    fun windowHeightPx(): Float = with(density) { windowInfo.containerDpSize.height.toPx() }

    fun maxVisibleHeightPx(): Float =
        liquidSheetMaxVisibleHeightPx(
            windowHeightPx = windowHeightPx(),
            topInsetPx = with(density) { topInset.toPx() },
        )

    fun visibleHeightPx(): Float {
        val naturalHeight = sheetHeightPx.intValue.toFloat()
        val requestedHeight =
            when {
                visibleSheetHeightPx.floatValue > 0f -> visibleSheetHeightPx.floatValue
                naturalHeight > 0f -> naturalHeight
                else -> maxVisibleHeightPx()
            }
        return requestedHeight.coerceIn(0f, maxVisibleHeightPx())
    }

    fun sheetPlacementOffsetPx(): Float {
        val currentHeight = visibleHeightPx()
        val fallbackHeight = windowHeightPx()
        val baseOffset = if (currentHeight > 0f) currentHeight else fallbackHeight
        return baseOffset * (1f - animationProgress.value) + dismissOffsetY.floatValue
    }

    fun sheetTopOffsetPx(): Float {
        val currentHeight = visibleHeightPx()
        val windowHeightPx = windowHeightPx()
        return if (currentHeight > 0f) {
            (windowHeightPx - currentHeight + sheetPlacementOffsetPx()).coerceIn(0f, windowHeightPx)
        } else {
            windowHeightPx
        }
    }

    fun backgroundDepthProgress(): Float {
        val currentHeight = visibleHeightPx()
        val windowHeightPx = windowHeightPx()
        val visibleHeightFraction =
            if (currentHeight > 0f && windowHeightPx > 0f) {
                (currentHeight / windowHeightPx).coerceIn(0f, 1f)
            } else {
                1f
            }
        val heightDepth =
            (
                (visibleHeightFraction - LIQUID_SHEET_BACKGROUND_MIN_DEPTH) /
                    (1f - LIQUID_SHEET_BACKGROUND_MIN_DEPTH)
            ).coerceIn(0f, 1f)
        val dismissProgress =
            (dismissOffsetY.floatValue / dismissDragThresholdPx.coerceAtLeast(1f))
                .coerceIn(0f, 1f)
        return animationProgress.value * heightDepth * (1f - dismissProgress)
    }

    fun backgroundBlurLayerHeightPx(): Float {
        if (sheetInteracting.value) return 0f
        val depth = liquidSheetSmoothStep(backgroundDepthProgress())
        return if (depth > LIQUID_SHEET_BACKGROUND_BLUR_START_DEPTH) sheetTopOffsetPx() else 0f
    }

    fun backgroundBlurLayerAlpha(): Float {
        if (sheetInteracting.value) return 0f
        val depth = liquidSheetSmoothStep(backgroundDepthProgress())
        return (
            (depth - LIQUID_SHEET_BACKGROUND_BLUR_START_DEPTH) /
                (1f - LIQUID_SHEET_BACKGROUND_BLUR_START_DEPTH)
            ).coerceIn(0f, 1f)
    }

    val resetGesture: suspend () -> Unit = {
        animate(
            dismissOffsetY.floatValue,
            0f,
            animationSpec = tween(durationMillis = LIQUID_SHEET_RESET_DURATION_MS),
        ) { value, _ ->
            dismissOffsetY.floatValue = value
        }
        animate(
            dimAlpha.floatValue,
            1f,
            animationSpec = tween(durationMillis = LIQUID_SHEET_RESET_DURATION_MS)
        ) { value, _ ->
            dimAlpha.floatValue = value
        }
    }

    popupHost(internalVisible.value) {
        BackHandler(enabled = show) {
            if (allowDismiss) {
                currentOnDismissRequest?.invoke()
            } else {
                onBlockedDismissRequest?.invoke()
                coroutineScope.launch { resetGesture() }
            }
        }

        if (enableWindowDim) {
            LiquidDetentBackgroundDimLayer(
                dimAlpha = dimAlpha,
                depthProgress = ::backgroundDepthProgress,
                blurLayerHeightPx = ::backgroundBlurLayerHeightPx,
                blurLayerAlpha = ::backgroundBlurLayerAlpha,
                blurRadius = backgroundDepthBlurRadius,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInputDismissLayer(
                    allowDismiss = allowDismiss,
                    onDismissRequest = { currentOnDismissRequest?.invoke() },
                    onBlockedDismissRequest = onBlockedDismissRequest,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetModifier = modifier
                .liquidSheetFloatingPlacement {
                    sheetPlacementOffsetPx()
                }
            CompositionLocalProvider(
                LocalLiquidSheetVisibleHeightPx provides {
                    visibleHeightPx().roundToInt()
                },
            ) {
                LiquidDetentBottomSheetColumn(
                    title = title,
                    backgroundColor = backgroundColor,
                    cornerRadius = cornerRadius,
                    sheetMaxWidth = sheetMaxWidth,
                    outsideMargin = outsideMargin,
                    insideMargin = insideMargin,
                    defaultWindowInsetsPadding = defaultWindowInsetsPadding,
                    dragHandleColor = dragHandleColor,
                    allowDismiss = allowDismiss,
                    sheetHeightPx = sheetHeightPx,
                    visibleSheetHeightPx = visibleSheetHeightPx,
                    dismissOffsetY = dismissOffsetY,
                    userResizedSheet = userResizedSheet,
                    dimAlpha = dimAlpha,
                    onDismissRequest = { currentOnDismissRequest?.invoke() },
                    modifier = sheetModifier,
                    surfaceModifier = surfaceModifier,
                    topInset = topInset,
                    enableNestedScroll = enableNestedScroll,
                    minimumFloatingHeight = minimumFloatingHeight,
                    onBlockedDismissRequest = onBlockedDismissRequest,
                    contentCanScrollUp = contentCanScrollUp,
                    dismissDragThresholdPx = dismissDragThresholdPx,
                    onInteractionStarted = {
                        sheetInteracting.value = true
                    },
                    onInteractionFinished = {
                        sheetInteracting.value = false
                    },
                    startAction = startAction?.let { action ->
                        { CompositionLocalProvider(LocalDismissState provides { currentOnDismissRequest?.invoke() }) { action() } }
                    },
                    endAction = endAction?.let { action ->
                        { CompositionLocalProvider(LocalDismissState provides { currentOnDismissRequest?.invoke() }) { action() } }
                    },
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun LiquidDetentBackgroundDimLayer(
    dimAlpha: MutableFloatState,
    depthProgress: () -> Float,
    blurLayerHeightPx: () -> Float,
    blurLayerAlpha: () -> Float,
    blurRadius: Dp,
) {
    val baseColor = MiuixTheme.colorScheme.windowDimming
    val sceneBackdrop = LocalSceneBackdrop.current
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val depth = liquidSheetSmoothStep(depthProgress())
                val dimDepth =
                    lerp(
                        start = LIQUID_SHEET_BACKGROUND_LOW_TINT_DEPTH,
                        stop = 1f,
                        fraction = depth,
                    )
                drawRect(
                    baseColor.copy(
                        alpha = baseColor.alpha * dimAlpha.floatValue * dimDepth,
                    )
                )
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .liquidSheetHeightPx {
                    blurLayerHeightPx().roundToInt()
                }
                .graphicsLayer {
                    alpha = blurLayerAlpha()
                }
                .drawBackdrop(
                    backdrop = sceneBackdrop,
                    shape = { RectangleShape },
                    effects = {
                        blur(blurRadius.toPx())
                    },
                    highlight = null,
                    shadow = null,
                    innerShadow = null,
                    onDrawSurface = {
                        val depth = liquidSheetSmoothStep(depthProgress())
                        val depthTint =
                            if (isDark) {
                                Color.Black.copy(alpha = 0.04f * depth)
                            } else {
                                Color.White.copy(alpha = 0.025f * depth)
                            }
                        drawRect(depthTint)
                    },
                ),
        )
    }
}

private fun Modifier.liquidSheetHeightPx(
    heightPx: () -> Int,
): Modifier =
    layout { measurable, constraints ->
        val resolvedHeight = heightPx().coerceIn(0, constraints.maxHeight)
        val placeable =
            measurable.measure(
                constraints.copy(
                    minHeight = resolvedHeight,
                    maxHeight = resolvedHeight,
                )
            )
        layout(placeable.width, resolvedHeight) {
            placeable.place(0, 0)
        }
    }

private fun Modifier.liquidSheetOptionalHeightPx(
    heightPx: () -> Int,
): Modifier =
    layout { measurable, constraints ->
        val requestedHeight = heightPx()
        if (requestedHeight <= 0) {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        } else {
            val resolvedHeight = requestedHeight.coerceIn(0, constraints.maxHeight)
            val placeable =
                measurable.measure(
                    constraints.copy(
                        minHeight = resolvedHeight,
                        maxHeight = resolvedHeight,
                    )
                )
            layout(placeable.width, resolvedHeight) {
                placeable.place(0, 0)
            }
        }
    }

private fun liquidSheetSmoothStep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun Modifier.liquidSheetWidthPx(
    widthPx: () -> Int,
): Modifier =
    layout { measurable, constraints ->
        val resolvedWidth = widthPx().coerceIn(0, constraints.maxWidth)
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = resolvedWidth,
                    maxWidth = resolvedWidth,
                )
            )
        layout(resolvedWidth, placeable.height) {
            placeable.place(0, 0)
        }
    }

private fun Modifier.liquidSheetFloatingPlacement(
    offsetY: () -> Float,
): Modifier =
    this.then(
        Modifier.offset { IntOffset(x = 0, y = offsetY().roundToInt()) },
    )

internal fun liquidSheetMaxVisibleHeightPx(
    windowHeightPx: Float,
    topInsetPx: Float,
): Float =
    (windowHeightPx - topInsetPx).coerceAtLeast(0f)

private fun Modifier.pointerInputDismissLayer(
    allowDismiss: Boolean,
    onDismissRequest: () -> Unit,
    onBlockedDismissRequest: (() -> Unit)?,
): Modifier =
    pointerInput(allowDismiss, onDismissRequest, onBlockedDismissRequest) {
        detectTapGestures(
            onTap = {
                if (allowDismiss) {
                    onDismissRequest()
                } else {
                    onBlockedDismissRequest?.invoke()
                }
            },
        )
    }

@Composable
private fun LiquidDetentBottomSheetColumn(
    title: String?,
    backgroundColor: Color,
    cornerRadius: Dp,
    sheetMaxWidth: Dp,
    outsideMargin: DpSize,
    insideMargin: DpSize,
    defaultWindowInsetsPadding: Boolean,
    dragHandleColor: Color,
    allowDismiss: Boolean,
    sheetHeightPx: MutableIntState,
    visibleSheetHeightPx: MutableFloatState,
    dismissOffsetY: MutableFloatState,
    userResizedSheet: MutableState<Boolean>,
    dimAlpha: MutableFloatState,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier,
    topInset: Dp,
    enableNestedScroll: Boolean,
    minimumFloatingHeight: Dp,
    onBlockedDismissRequest: (() -> Unit)?,
    contentCanScrollUp: () -> Boolean,
    dismissDragThresholdPx: Float,
    onInteractionStarted: () -> Unit,
    onInteractionFinished: () -> Unit,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val windowHeight = windowInfo.containerDpSize.height
    val currentWindowHeight by rememberUpdatedState(windowHeight)
    val coroutineScope = rememberCoroutineScope()
    val settlingJob = remember { mutableStateOf<Job?>(null) }
    val isSettling = remember { mutableStateOf(false) }
    val currentContentCanScrollUp = rememberUpdatedState(contentCanScrollUp)
    val minimumFloatingHeightPx = with(density) { minimumFloatingHeight.toPx() }

    fun maxVisibleHeightPx(): Float {
        val windowHeightPx = with(density) { currentWindowHeight.toPx() }
        val topInsetPx = with(density) { topInset.toPx() }
        return liquidSheetMaxVisibleHeightPx(
            windowHeightPx = windowHeightPx,
            topInsetPx = topInsetPx,
        )
    }

    fun naturalHeightPx(): Float =
        sheetHeightPx.intValue.toFloat().coerceAtLeast(0f)

    fun currentVisibleHeightPx(): Float {
        val requestedHeight =
            when {
                visibleSheetHeightPx.floatValue > 0f -> visibleSheetHeightPx.floatValue
                naturalHeightPx() > 0f -> naturalHeightPx()
                else -> maxVisibleHeightPx()
            }
        return requestedHeight.coerceIn(0f, maxVisibleHeightPx())
    }

    fun minimumVisibleHeightPx(): Float {
        val maxVisibleHeight = maxVisibleHeightPx()
        val minimumResizableHeight = minimumFloatingHeightPx.coerceAtMost(maxVisibleHeight)
        val naturalHeight = naturalHeightPx()
        return if (naturalHeight in 1f..minimumResizableHeight) {
            naturalHeight
        } else {
            minimumResizableHeight
        }
    }

    fun updateDimAlpha(offset: Float) {
        dimAlpha.floatValue =
            if (allowDismiss && offset > 0f) {
                1f - (offset / dismissDragThresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            } else {
                1f
            }
    }

    fun applyResizeDrag(delta: Float): Float {
        if (delta == 0f) return 0f
        userResizedSheet.value = true
        val beforeHeight = currentVisibleHeightPx()
        val beforeDismissOffset = dismissOffsetY.floatValue
        var remainingDelta = delta

        if (remainingDelta < 0f && dismissOffsetY.floatValue > 0f) {
            val restoredOffset = minOf(dismissOffsetY.floatValue, -remainingDelta)
            dismissOffsetY.floatValue -= restoredOffset
            remainingDelta += restoredOffset
        }

        if (remainingDelta != 0f) {
            val desiredHeight = currentVisibleHeightPx() - remainingDelta
            val minimumVisibleHeight = minimumVisibleHeightPx()
            val maximumVisibleHeight = maxVisibleHeightPx()
            when {
                desiredHeight >= maximumVisibleHeight -> {
                    visibleSheetHeightPx.floatValue = maximumVisibleHeight
                    dismissOffsetY.floatValue = 0f
                }

                desiredHeight >= minimumVisibleHeight -> {
                    visibleSheetHeightPx.floatValue = desiredHeight
                    dismissOffsetY.floatValue = 0f
                }

                else -> {
                    visibleSheetHeightPx.floatValue = minimumVisibleHeight
                    val extraOffset = minimumVisibleHeight - desiredHeight
                    dismissOffsetY.floatValue =
                        if (allowDismiss) {
                            extraOffset
                        } else {
                            extraOffset * LIQUID_SHEET_BLOCKED_DISMISS_RESISTANCE
                        }
                }
            }
        }

        updateDimAlpha(dismissOffsetY.floatValue)
        return (beforeHeight - currentVisibleHeightPx()) +
            (dismissOffsetY.floatValue - beforeDismissOffset)
    }

    val settle: (Float) -> Unit = remember(
        allowDismiss,
        density,
        minimumFloatingHeight,
        onBlockedDismissRequest,
    ) {
        { velocity ->
            settlingJob.value?.cancel()
            isSettling.value = true
            onInteractionStarted()
            settlingJob.value = coroutineScope.launch {
                val currentDismissOffset = dismissOffsetY.floatValue
                val velocityThresholdPx =
                    with(density) { LiquidSheetDismissVelocityThreshold.toPx() }
                val windowHeightPx = with(density) { currentWindowHeight.toPx() }
                val thresholdDismissOffset =
                    if (allowDismiss) {
                        currentDismissOffset
                    } else {
                        currentDismissOffset / LIQUID_SHEET_BLOCKED_DISMISS_RESISTANCE
                    }
                val shouldRequestDismiss =
                    thresholdDismissOffset > 0f &&
                        (thresholdDismissOffset > dismissDragThresholdPx || velocity > velocityThresholdPx)
                val targetHeight =
                    currentVisibleHeightPx()
                        .coerceIn(minimumVisibleHeightPx(), maxVisibleHeightPx())

                try {
                    when {
                        shouldRequestDismiss && allowDismiss -> {
                            animateDismissOffScreen(
                                dismissOffsetY = dismissOffsetY,
                                visibleHeightPx = currentVisibleHeightPx(),
                                windowHeightPx = windowHeightPx,
                                dimAlpha = dimAlpha,
                                velocity = velocity,
                            ) {
                                onDismissRequest?.invoke()
                            }
                        }

                        shouldRequestDismiss -> {
                            onBlockedDismissRequest?.invoke()
                            animateDismissOffsetTo(
                                dismissOffsetY,
                                dimAlpha,
                                targetValue = 0f,
                                initialVelocity = 0f
                            )
                        }

                        else -> {
                            if (abs(visibleSheetHeightPx.floatValue - targetHeight) > 0.5f) {
                                animateSheetHeightTo(
                                    visibleSheetHeightPx = visibleSheetHeightPx,
                                    targetValue = targetHeight,
                                    initialVelocity = -velocity,
                                )
                            }
                            if (dismissOffsetY.floatValue != 0f) {
                                animateDismissOffsetTo(
                                    dismissOffsetY = dismissOffsetY,
                                    dimAlpha = dimAlpha,
                                    targetValue = 0f,
                                    initialVelocity = velocity,
                                )
                            } else {
                                dimAlpha.floatValue = 1f
                            }
                        }
                    }
                } catch (_: CancellationException) {
                } finally {
                    isSettling.value = false
                    onInteractionFinished()
                }
            }
        }
    }

    val nestedScrollConnection = remember(
        enableNestedScroll,
        allowDismiss,
        density,
        minimumFloatingHeight,
        dismissDragThresholdPx,
        settle
    ) {
        var downwardGestureStartedInScrollableContent = false
        var sheetConsumedScroll = false
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enableNestedScroll) return Offset.Zero
                if (isSettling.value) {
                    settlingJob.value?.cancel()
                    isSettling.value = false
                }

                val delta = available.y
                if (source == NestedScrollSource.UserInput) {
                    when {
                        delta > 0f && currentContentCanScrollUp.value() -> {
                            downwardGestureStartedInScrollableContent = true
                        }

                        delta < 0f -> {
                            downwardGestureStartedInScrollableContent = false
                        }
                    }
                }
                if (
                    delta < 0f &&
                    (
                        dismissOffsetY.floatValue > 0f ||
                            (!currentContentCanScrollUp.value() && currentVisibleHeightPx() < maxVisibleHeightPx())
                        )
                ) {
                    val consumedY = applyResizeDrag(delta)
                    if (consumedY != 0f) {
                        onInteractionStarted()
                        sheetConsumedScroll = true
                        return Offset(0f, consumedY)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!enableNestedScroll) return Offset.Zero
                val delta = available.y
                if (delta > 0f) {
                    if (
                        source == NestedScrollSource.UserInput &&
                        (downwardGestureStartedInScrollableContent || currentContentCanScrollUp.value())
                    ) {
                        return Offset.Zero
                    }
                    if (isSettling.value) {
                        settlingJob.value?.cancel()
                        isSettling.value = false
                    }
                    onInteractionStarted()
                    val consumedY = applyResizeDrag(delta)
                    if (consumedY != 0f) {
                        sheetConsumedScroll = true
                        return Offset(0f, consumedY)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enableNestedScroll || isSettling.value) return Velocity.Zero
                if (
                    available.y > 0f &&
                    (downwardGestureStartedInScrollableContent || currentContentCanScrollUp.value())
                ) {
                    downwardGestureStartedInScrollableContent = true
                    return Velocity.Zero
                }
                if (sheetConsumedScroll || dismissOffsetY.floatValue != 0f) {
                    settle(available.y)
                    sheetConsumedScroll = false
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!enableNestedScroll || isSettling.value) return Velocity.Zero
                if (downwardGestureStartedInScrollableContent) {
                    downwardGestureStartedInScrollableContent = false
                    return Velocity.Zero
                }
                if (sheetConsumedScroll || dismissOffsetY.floatValue != 0f) {
                    settle(available.y)
                    sheetConsumedScroll = false
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val imeInsets = WindowInsets.ime
    val sheetCornerShape = remember(cornerRadius) {
        RoundedRectangle(cornerRadius)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = modifier
                .pointerInput(Unit) { detectTapGestures { } }
                .then(if (enableNestedScroll) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
                .widthIn(max = sheetMaxWidth)
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = windowHeight - topInset)
                .then(
                    if (userResizedSheet.value) {
                        Modifier.liquidSheetOptionalHeightPx {
                            currentVisibleHeightPx().roundToInt()
                        }
                    } else {
                        Modifier
                    }
                )
                .onGloballyPositioned { coordinates ->
                    if (imeInsets.getBottom(density) == 0 && !userResizedSheet.value) {
                        sheetHeightPx.intValue = coordinates.size.height
                        visibleSheetHeightPx.floatValue = coordinates.size.height.toFloat()
                    }
                }
                .padding(horizontal = outsideMargin.width)
                .then(surfaceModifier)
                .clip(sheetCornerShape)
                .background(backgroundColor)
                .then(if (defaultWindowInsetsPadding) Modifier.imePadding() else Modifier)
                .padding(horizontal = insideMargin.width)
                .padding(bottom = insideMargin.height),
        ) {
            LiquidDetentTopChrome(
                title = title,
                startAction = startAction,
                endAction = endAction,
                dragHandleColor = dragHandleColor,
                coroutineScope = coroutineScope,
                onDragStarted = onInteractionStarted,
                onDrag = { dragAmount ->
                    if (isSettling.value) {
                        settlingJob.value?.cancel()
                        isSettling.value = false
                    }
                    applyResizeDrag(dragAmount)
                },
                onSettle = settle,
            )
            content()
        }
    }
}

@Composable
private fun LiquidDetentTopChrome(
    title: String?,
    startAction: (@Composable () -> Unit)?,
    endAction: (@Composable () -> Unit)?,
    dragHandleColor: Color,
    coroutineScope: CoroutineScope,
    onDragStarted: () -> Unit,
    onDrag: (Float) -> Unit,
    onSettle: (velocity: Float) -> Unit,
) {
    val isPressing = remember { mutableFloatStateOf(0f) }
    val pressScale = remember { Animatable(1f) }
    val pressWidth = remember { Animatable(LIQUID_SHEET_HANDLE_REST_WIDTH) }
    val handleShape = remember { RoundedCornerShape(2.dp) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressing.floatValue = 1f
                        coroutineScope.animateHandlePressDown(pressScale, pressWidth)
                        val released = tryAwaitRelease()
                        if (released) {
                            isPressing.floatValue = 0f
                            coroutineScope.animateHandlePressRelease(pressScale, pressWidth)
                        }
                    },
                )
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragAmount ->
                    onDrag(dragAmount)
                },
                onDragStarted = {
                    onDragStarted()
                    isPressing.floatValue = 1f
                    coroutineScope.animateHandlePressDown(pressScale, pressWidth)
                },
                onDragStopped = { velocity ->
                    isPressing.floatValue = 0f
                    coroutineScope.animateHandlePressRelease(pressScale, pressWidth)
                    onSettle(velocity)
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .liquidSheetWidthPx {
                        with(density) { pressWidth.value.dp.roundToPx() }
                    }
                    .height(4.dp)
                    .graphicsLayer {
                        scaleY = pressScale.value
                    }
                    .clip(handleShape)
                    .drawBehind {
                        val handleAlpha = lerp(0.2f, 0.35f, isPressing.floatValue)
                        drawRect(dragHandleColor.copy(alpha = handleAlpha))
                    },
            )
        }
        LiquidDetentTitleAndActionsRow(
            title = title,
            startAction = startAction,
            endAction = endAction,
        )
    }
}

@Composable
private fun LiquidDetentTitleAndActionsRow(
    title: String?,
    startAction: (@Composable () -> Unit)?,
    endAction: (@Composable () -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 12.dp),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            startAction?.invoke()
        }
        title?.let {
            Text(
                text = it,
                modifier = Modifier.align(Alignment.Center),
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            endAction?.invoke()
        }
    }
}

private fun CoroutineScope.animateHandlePressDown(
    pressScale: Animatable<Float, *>,
    pressWidth: Animatable<Float, *>,
) {
    launch {
        pressScale.animateTo(
            targetValue = LIQUID_SHEET_HANDLE_PRESSED_SCALE,
            animationSpec = tween(durationMillis = LIQUID_SHEET_HANDLE_PRESS_DURATION_MS),
        )
    }
    launch {
        pressWidth.animateTo(
            targetValue = LIQUID_SHEET_HANDLE_PRESSED_WIDTH,
            animationSpec = tween(durationMillis = LIQUID_SHEET_HANDLE_PRESS_DURATION_MS),
        )
    }
}

private fun CoroutineScope.animateHandlePressRelease(
    pressScale: Animatable<Float, *>,
    pressWidth: Animatable<Float, *>,
) {
    launch {
        pressScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = LIQUID_SHEET_HANDLE_RELEASE_DURATION_MS),
        )
    }
    launch {
        pressWidth.animateTo(
            targetValue = LIQUID_SHEET_HANDLE_REST_WIDTH,
            animationSpec = tween(durationMillis = LIQUID_SHEET_HANDLE_RELEASE_DURATION_MS),
        )
    }
}

private suspend fun animateSheetHeightTo(
    visibleSheetHeightPx: MutableFloatState,
    targetValue: Float,
    initialVelocity: Float,
) {
    animate(
        initialValue = visibleSheetHeightPx.floatValue,
        targetValue = targetValue,
        animationSpec = folmeSpring(
            damping = LIQUID_SHEET_SETTLE_DAMPING,
            response = LIQUID_SHEET_SETTLE_RESPONSE
        ),
        initialVelocity = initialVelocity,
    ) { value, _ ->
        visibleSheetHeightPx.floatValue = value
    }
    visibleSheetHeightPx.floatValue = targetValue
}

private suspend fun animateDismissOffsetTo(
    dismissOffsetY: MutableFloatState,
    dimAlpha: MutableFloatState,
    targetValue: Float,
    initialVelocity: Float,
) {
    animate(
        initialValue = dismissOffsetY.floatValue,
        targetValue = targetValue,
        animationSpec = folmeSpring(
            damping = LIQUID_SHEET_SETTLE_DAMPING,
            response = LIQUID_SHEET_SETTLE_RESPONSE
        ),
        initialVelocity = initialVelocity,
    ) { value, _ ->
        dismissOffsetY.floatValue = value
        dimAlpha.floatValue = 1f
    }
    dismissOffsetY.floatValue = targetValue
    dimAlpha.floatValue = 1f
}

private suspend fun animateDismissOffScreen(
    dismissOffsetY: MutableFloatState,
    visibleHeightPx: Float,
    windowHeightPx: Float,
    dimAlpha: MutableFloatState,
    velocity: Float = 0f,
    onDismiss: () -> Unit,
) {
    val sheetHeight = visibleHeightPx
    val thresholdPx = if (sheetHeight > 0f) sheetHeight else 500f
    val targetValue = maxOf(sheetHeight, windowHeightPx)
    animate(
        initialValue = dismissOffsetY.floatValue,
        targetValue = targetValue,
        animationSpec = folmeSpring(
            damping = LIQUID_SHEET_SETTLE_DAMPING,
            response = LIQUID_SHEET_SETTLE_RESPONSE
        ),
        initialVelocity = velocity,
    ) { value, _ ->
        dismissOffsetY.floatValue = value
        dimAlpha.floatValue = 1f - (value / thresholdPx).coerceIn(0f, 1f)
    }
    onDismiss()
}
