@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.ui.page.main.widget.isAppInDarkTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val LIQUID_SHEET_HANDLE_REST_WIDTH = 45f
private const val LIQUID_SHEET_HANDLE_PRESSED_WIDTH = 55f
private const val LIQUID_SHEET_HANDLE_PRESSED_SCALE = 1.15f
private const val LIQUID_SHEET_HANDLE_PRESS_DURATION_MS = 100
private const val LIQUID_SHEET_HANDLE_RELEASE_DURATION_MS = 150

private val LiquidSheetScrollEdgeHeight = 18.dp

/**
 * The sheet's top bar: grabber, then the title flanked by its actions.
 *
 * Follows Apple's sheet anatomy — a grabber that both drags to resize *and* taps to cycle detents,
 * the dismissing action on the leading edge and the confirming one on the trailing edge, and a scroll
 * edge effect so the title stays legible once content slides under it.
 */
@Composable
internal fun LiquidSheetTopChrome(
    title: String?,
    startAction: (@Composable () -> Unit)?,
    endAction: (@Composable () -> Unit)?,
    dragHandleColor: Color,
    canExpand: () -> Boolean,
    canCollapse: () -> Boolean,
    canDismiss: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onDismiss: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: (velocity: Float) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pressProgress = remember { mutableFloatStateOf(0f) }
    val pressScale = remember { Animatable(1f) }
    val pressWidth = remember { Animatable(LIQUID_SHEET_HANDLE_REST_WIDTH) }
    val handleShape = remember { RoundedCornerShape(2.dp) }
    val density = LocalDensity.current
    val isDark = isAppInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (canExpand()) expand { onExpand(); true }
                if (canCollapse()) collapse { onCollapse(); true }
                if (canDismiss) dismiss { onDismiss(); true }
            }.pointerHoverIcon(PointerIcon.Hand)
            .testTag(LiquidSheetDragRegionTestTag)
            .pointerInput(canExpand, canCollapse, onExpand, onCollapse) {
                detectTapGestures(
                    onPress = {
                        pressProgress.floatValue = 1f
                        scope.animateHandlePressDown(pressScale, pressWidth)
                        val released = tryAwaitRelease()
                        if (released) {
                            pressProgress.floatValue = 0f
                            scope.animateHandlePressRelease(pressScale, pressWidth)
                        }
                    },
                    // Apple: tapping the grabber cycles the detents. Expanding wins while there is
                    // room, otherwise the tap collapses.
                    onTap = {
                        if (canExpand()) onExpand() else if (canCollapse()) onCollapse()
                    },
                )
            }.draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragAmount -> onDrag(dragAmount) },
                onDragStarted = {
                    pressProgress.floatValue = 1f
                    scope.animateHandlePressDown(pressScale, pressWidth)
                },
                onDragStopped = { velocity ->
                    pressProgress.floatValue = 0f
                    scope.animateHandlePressRelease(pressScale, pressWidth)
                    onDragStopped(velocity)
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
                    }.height(4.dp)
                    .graphicsLayer { scaleY = pressScale.value }
                    .clip(handleShape)
                    .drawBehind {
                        drawRect(
                            dragHandleColor.copy(
                                alpha = lerp(0.2f, 0.35f, pressProgress.floatValue),
                            ),
                        )
                    },
            )
        }
        LiquidSheetTitleRow(
            title = title,
            startAction = startAction,
            endAction = endAction,
        )
    }
}

/**
 * Apple's scroll edge effect: separates the chrome from content that has slid underneath it.
 *
 * Drawn *over* the content rather than as a band inside the chrome. A band would claim its height
 * permanently, padding every sheet whether or not anything scrolls, and making it appear on demand
 * would reflow the content the moment the user starts scrolling.
 */
internal fun Modifier.liquidSheetScrollEdge(
    visible: () -> Boolean,
    isDark: Boolean,
): Modifier =
    drawWithContent {
        drawContent()
        if (!visible()) return@drawWithContent
        val edgeHeight = LiquidSheetScrollEdgeHeight.toPx().coerceAtMost(size.height)
        val shadeColor =
            if (isDark) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.07f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(shadeColor, Color.Transparent),
                startY = 0f,
                endY = edgeHeight,
            ),
            size = Size(size.width, edgeHeight),
        )
        drawLine(
            color = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
        )
    }

@Composable
private fun LiquidSheetTitleRow(
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
