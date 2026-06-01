@file:Suppress("FunctionName")

// Adapted from compose-miuix-ui WindowBottomSheet / BottomSheetContentLayout.
// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package os.kei.ui.page.main.widget.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
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
import kotlin.math.roundToInt

private const val LIQUID_SHEET_BACKGROUND_MIN_DEPTH = 0.10f
private const val LIQUID_SHEET_UPWARD_RESISTANCE = 0.18f
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

internal val LiquidSheetFloatingOffsetYKey =
    SemanticsPropertyKey<Float>("LiquidSheetFloatingOffsetY")
internal var SemanticsPropertyReceiver.liquidSheetFloatingOffsetY by LiquidSheetFloatingOffsetYKey

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
    content: @Composable () -> Unit,
) {
    val animationProgress = remember { Animatable(0f, visibilityThreshold = 0.0001f) }
    val dragOffsetY = remember { mutableFloatStateOf(0f) }
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val internalVisible = remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            internalVisible.value = true
            dragOffsetY.floatValue = 0f
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = folmeSpring(
                    damping = LIQUID_SHEET_OPEN_CLOSE_DAMPING,
                    response = LIQUID_SHEET_OPEN_CLOSE_RESPONSE
                ),
            )
        } else {
            if (!internalVisible.value) return@LaunchedEffect
            if (dragOffsetY.floatValue > 0f) {
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
    fun maxFloatingOffsetPx(): Float =
        (sheetHeightPx.intValue.toFloat() - minimumFloatingHeightPx).coerceAtLeast(0f)

    fun sheetPlacementOffsetPx(): Float {
        val currentHeight = sheetHeightPx.intValue.toFloat()
        val windowHeightPx = with(density) { windowInfo.containerDpSize.height.toPx() }
        val baseOffset = if (currentHeight > 0) currentHeight else windowHeightPx
        return baseOffset * (1f - animationProgress.value) + dragOffsetY.floatValue
    }

    fun backgroundDepthProgress(): Float {
        val maxFloatingOffset = maxFloatingOffsetPx()
        val floatingProgress =
            if (maxFloatingOffset > 0f) {
                (dragOffsetY.floatValue / maxFloatingOffset).coerceIn(0f, 1f)
            } else {
                0f
            }
        val dismissProgress =
            if (maxFloatingOffset > 0f) {
                ((dragOffsetY.floatValue - maxFloatingOffset) / dismissDragThresholdPx.coerceAtLeast(
                    1f
                )).coerceIn(0f, 1f)
            } else {
                0f
            }
        val hoverDepth =
            lerp(start = 1f, stop = LIQUID_SHEET_BACKGROUND_MIN_DEPTH, fraction = floatingProgress)
        return animationProgress.value * hoverDepth * (1f - dismissProgress)
    }

    val resetGesture: suspend () -> Unit = {
        animate(
            dragOffsetY.floatValue,
            0f,
            animationSpec = tween(durationMillis = LIQUID_SHEET_RESET_DURATION_MS)
        ) { value, _ ->
            dragOffsetY.floatValue = value
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
                .semantics {
                    liquidSheetFloatingOffsetY = dragOffsetY.floatValue
                }

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
                dragOffsetY = dragOffsetY,
                dimAlpha = dimAlpha,
                onDismissRequest = { currentOnDismissRequest?.invoke() },
                modifier = sheetModifier,
                surfaceModifier = surfaceModifier,
                topInset = topInset,
                enableNestedScroll = enableNestedScroll,
                minimumFloatingHeight = minimumFloatingHeight,
                dismissDragThreshold = dismissDragThreshold,
                onBlockedDismissRequest = onBlockedDismissRequest,
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

@Composable
private fun LiquidDetentBackgroundDimLayer(
    dimAlpha: MutableFloatState,
    depthProgress: () -> Float,
) {
    val baseColor = MiuixTheme.colorScheme.windowDimming
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    color = baseColor.copy(alpha = baseColor.alpha * dimAlpha.floatValue * depthProgress()),
                )
            },
    )
}

private fun Modifier.liquidSheetFloatingPlacement(
    offsetY: () -> Float,
): Modifier =
    this.then(
        Modifier.offset { IntOffset(x = 0, y = offsetY().roundToInt()) },
    )

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
    dragOffsetY: MutableFloatState,
    dimAlpha: MutableFloatState,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier,
    topInset: Dp,
    enableNestedScroll: Boolean,
    minimumFloatingHeight: Dp,
    dismissDragThreshold: Dp,
    onBlockedDismissRequest: (() -> Unit)?,
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
    val minimumFloatingHeightPx = with(density) { minimumFloatingHeight.toPx() }
    val dismissDragThresholdPx = with(density) { dismissDragThreshold.toPx() }

    fun maxFloatingOffsetPx(): Float =
        (sheetHeightPx.intValue.toFloat() - minimumFloatingHeightPx).coerceAtLeast(0f)

    fun calculateNewOffset(current: Float, delta: Float): Float {
        val maxFloatingOffset = maxFloatingOffsetPx()
        val newOffset = current + delta
        return when {
            newOffset < 0f -> (current + delta * LIQUID_SHEET_UPWARD_RESISTANCE).coerceAtMost(0f)
            newOffset <= maxFloatingOffset -> newOffset
            allowDismiss -> newOffset
            else -> {
                val extraOffset = newOffset - maxFloatingOffset
                (maxFloatingOffset + extraOffset * LIQUID_SHEET_BLOCKED_DISMISS_RESISTANCE).coerceAtLeast(
                    0f
                )
            }
        }
    }

    fun updateDimAlpha(offset: Float) {
        val extraOffset = (offset - maxFloatingOffsetPx()).coerceAtLeast(0f)
        dimAlpha.floatValue =
            if (allowDismiss && extraOffset > 0f) {
                1f - (extraOffset / dismissDragThresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            } else {
                1f
            }
    }

    val settle: (Float) -> Unit = remember(
        allowDismiss,
        density,
        dismissDragThreshold,
        minimumFloatingHeight,
        onBlockedDismissRequest,
    ) {
        { velocity ->
            settlingJob.value?.cancel()
            isSettling.value = true
            settlingJob.value = coroutineScope.launch {
                val currentOffset = dragOffsetY.floatValue
                val velocityThresholdPx =
                    with(density) { LiquidSheetDismissVelocityThreshold.toPx() }
                val windowHeightPx = with(density) { currentWindowHeight.toPx() }
                val maxFloatingOffset = maxFloatingOffsetPx()
                val extraOffset = currentOffset - maxFloatingOffset
                val shouldRequestDismiss =
                    extraOffset > 0f &&
                            (extraOffset > dismissDragThresholdPx || velocity > velocityThresholdPx)
                val floatingTarget =
                    when {
                        currentOffset < 0f || velocity < -velocityThresholdPx -> 0f
                        velocity > velocityThresholdPx -> maxFloatingOffset
                        else -> currentOffset.coerceIn(0f, maxFloatingOffset)
                    }

                try {
                    when {
                        shouldRequestDismiss && allowDismiss -> {
                            animateDismissOffScreen(
                                dragOffsetY = dragOffsetY,
                                sheetHeightPx = sheetHeightPx.intValue,
                                windowHeightPx = windowHeightPx,
                                dimAlpha = dimAlpha,
                                velocity = velocity,
                            ) {
                                onDismissRequest?.invoke()
                            }
                        }

                        shouldRequestDismiss -> {
                            onBlockedDismissRequest?.invoke()
                            animateDragOffsetTo(
                                dragOffsetY,
                                dimAlpha,
                                targetValue = maxFloatingOffset,
                                initialVelocity = 0f
                            )
                        }

                        else -> {
                            animateDragOffsetTo(
                                dragOffsetY = dragOffsetY,
                                dimAlpha = dimAlpha,
                                targetValue = floatingTarget,
                                initialVelocity = velocity,
                            )
                        }
                    }
                } catch (_: CancellationException) {
                } finally {
                    isSettling.value = false
                }
            }
        }
    }

    val nestedScrollConnection = remember(
        enableNestedScroll,
        allowDismiss,
        density,
        minimumFloatingHeight,
        dismissDragThreshold,
        settle
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enableNestedScroll) return Offset.Zero
                if (isSettling.value) {
                    settlingJob.value?.cancel()
                    isSettling.value = false
                }

                val delta = available.y
                if (delta < 0f && dragOffsetY.floatValue > 0f) {
                    val newOffset =
                        calculateNewOffset(dragOffsetY.floatValue, delta).coerceAtLeast(0f)
                    val consumedY = dragOffsetY.floatValue - newOffset
                    if (consumedY != 0f) {
                        dragOffsetY.floatValue = newOffset
                        updateDimAlpha(newOffset)
                        return Offset(0f, -consumedY)
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
                    if (isSettling.value) {
                        settlingJob.value?.cancel()
                        isSettling.value = false
                    }
                    val newOffset = calculateNewOffset(dragOffsetY.floatValue, delta)
                    dragOffsetY.floatValue = newOffset
                    updateDimAlpha(newOffset)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enableNestedScroll || isSettling.value) return Velocity.Zero
                if (dragOffsetY.floatValue != 0f || available.y > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!enableNestedScroll || isSettling.value) return Velocity.Zero
                if (dragOffsetY.floatValue != 0f || available.y > 0f) {
                    settle(available.y)
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
        LiquidDetentOverscrollBackground(
            dragOffsetY = dragOffsetY,
            sheetMaxWidth = sheetMaxWidth,
            outsideMargin = outsideMargin,
            backgroundColor = backgroundColor,
        )

        Column(
            modifier = modifier
                .pointerInput(Unit) { detectTapGestures { } }
                .then(if (enableNestedScroll) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
                .widthIn(max = sheetMaxWidth)
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = windowHeight - topInset)
                .onGloballyPositioned { coordinates ->
                    if (imeInsets.getBottom(density) == 0) {
                        sheetHeightPx.intValue = coordinates.size.height
                    }
                }
                .padding(horizontal = outsideMargin.width)
                .clip(sheetCornerShape)
                .then(surfaceModifier)
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
                onDrag = { dragAmount ->
                    val finalOffset = calculateNewOffset(dragOffsetY.floatValue, dragAmount)
                    dragOffsetY.floatValue = finalOffset
                    updateDimAlpha(finalOffset)
                },
                onSettle = settle,
            )
            content()
        }
    }
}

@Composable
private fun BoxScope.LiquidDetentOverscrollBackground(
    dragOffsetY: MutableFloatState,
    sheetMaxWidth: Dp,
    outsideMargin: DpSize,
    backgroundColor: Color,
) {
    val density = LocalDensity.current
    val overscrollOffsetPx by remember { derivedStateOf { (-dragOffsetY.floatValue).coerceAtLeast(0f) } }
    if (overscrollOffsetPx > 0f) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = sheetMaxWidth)
                .fillMaxWidth()
                .height(with(density) { overscrollOffsetPx.toDp() } + 1.dp)
                .padding(horizontal = outsideMargin.width)
                .background(backgroundColor),
        )
    }
}

@Composable
private fun LiquidDetentTopChrome(
    title: String?,
    startAction: (@Composable () -> Unit)?,
    endAction: (@Composable () -> Unit)?,
    dragHandleColor: Color,
    coroutineScope: CoroutineScope,
    onDrag: (Float) -> Unit,
    onSettle: (velocity: Float) -> Unit,
) {
    val isPressing = remember { mutableFloatStateOf(0f) }
    val pressScale = remember { Animatable(1f) }
    val pressWidth = remember { Animatable(LIQUID_SHEET_HANDLE_REST_WIDTH) }
    val handleShape = remember { RoundedCornerShape(2.dp) }

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
                    .width(pressWidth.value.dp)
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

private suspend fun animateDragOffsetTo(
    dragOffsetY: MutableFloatState,
    dimAlpha: MutableFloatState,
    targetValue: Float,
    initialVelocity: Float,
) {
    animate(
        initialValue = dragOffsetY.floatValue,
        targetValue = targetValue,
        animationSpec = folmeSpring(
            damping = LIQUID_SHEET_SETTLE_DAMPING,
            response = LIQUID_SHEET_SETTLE_RESPONSE
        ),
        initialVelocity = initialVelocity,
    ) { value, _ ->
        dragOffsetY.floatValue = value
        dimAlpha.floatValue = 1f
    }
    dragOffsetY.floatValue = targetValue
    dimAlpha.floatValue = 1f
}

private suspend fun animateDismissOffScreen(
    dragOffsetY: MutableFloatState,
    sheetHeightPx: Int,
    windowHeightPx: Float,
    dimAlpha: MutableFloatState,
    velocity: Float = 0f,
    onDismiss: () -> Unit,
) {
    val sheetHeight = sheetHeightPx.toFloat()
    val thresholdPx = if (sheetHeight > 0f) sheetHeight else 500f
    val targetValue = maxOf(sheetHeight, windowHeightPx)
    animate(
        initialValue = dragOffsetY.floatValue,
        targetValue = targetValue,
        animationSpec = folmeSpring(
            damping = LIQUID_SHEET_SETTLE_DAMPING,
            response = LIQUID_SHEET_SETTLE_RESPONSE
        ),
        initialVelocity = velocity,
    ) { value, _ ->
        dragOffsetY.floatValue = value
        dimAlpha.floatValue = 1f - (value / thresholdPx).coerceIn(0f, 1f)
    }
    onDismiss()
}
