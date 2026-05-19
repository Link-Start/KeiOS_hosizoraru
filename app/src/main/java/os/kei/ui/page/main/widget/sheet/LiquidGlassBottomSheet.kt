package os.kei.ui.page.main.widget.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * v2 Liquid Glass BottomSheet — frosted-glass bottom sheet with spring animations.
 *
 * Uses [Dialog] (not Popup) to guarantee finite layout constraints from the window — this is the
 * same approach as Material 3 ModalBottomSheet and Miuix WindowBottomSheet. Popup gives unbounded
 * height which crashes any scrollable content inside.
 */

private val LiquidSheetCornerRadius = 28.dp
private val LiquidSheetMaxWidth = 480.dp
private val LiquidSheetDragHandleWidth = 36.dp
private val LiquidSheetDragHandleHeight = 4.dp
private val LiquidSheetDragHandleTopPadding = 10.dp
private val LiquidSheetBlurRadius = 16.dp
private val LiquidSheetLensStart = 24.dp
private val LiquidSheetLensEnd = 48.dp
private const val LiquidSheetDismissThreshold = 0.35f
private const val LiquidSheetScrimAlpha = 0.42f

@Composable
fun LiquidGlassBottomSheet(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    allowDismiss: Boolean = true,
    onBlockedDismissRequest: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val currentOnBlockedDismissRequest by rememberUpdatedState(onBlockedDismissRequest)

    // 0f = fully hidden (off-screen below), 1f = fully visible
    val sheetProgress = remember { Animatable(0f) }
    var isRendered by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            isRendered = true
            sheetProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f)
            )
        } else {
            if (isRendered) {
                sheetProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.92f, stiffness = 500f)
                )
                isRendered = false
                currentOnDismissFinished?.invoke()
            }
        }
    }

    if (!isRendered && !show) return

    // Dialog creates a full-screen window with finite constraints — unlike Popup which can give
    // unbounded height. This is critical: callers' content contains LazyColumn/verticalScroll
    // which crash under infinite height measurement.
    Dialog(
        onDismissRequest = {
            if (allowDismiss) {
                currentOnDismissRequest?.invoke()
            } else {
                currentOnBlockedDismissRequest?.invoke()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = allowDismiss,
            dismissOnClickOutside = false, // we handle scrim tap manually for animation
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val progress = sheetProgress.value.coerceIn(0f, 1f)
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

        BackHandler(enabled = true) {
            if (allowDismiss) {
                currentOnDismissRequest?.invoke()
            } else {
                currentOnBlockedDismissRequest?.invoke()
            }
        }

        // Full-screen container: scrim + sheet at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = LiquidSheetScrimAlpha * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = allowDismiss
                ) {
                    currentOnDismissRequest?.invoke()
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Sheet surface
            val sheetShape = RoundedRectangle(LiquidSheetCornerRadius)

            val surfaceColor = if (isDark) {
                Color(0xFF0D0D12).copy(alpha = 0.88f)
            } else {
                Color(0xFFFCFCFF).copy(alpha = 0.82f)
            }
            val sheenColor = if (isDark) {
                Color.White.copy(alpha = 0.08f)
            } else {
                Color.White.copy(alpha = 0.32f)
            }
            val dragHandleColor = if (isDark) {
                Color.White.copy(alpha = 0.28f)
            } else {
                Color.Black.copy(alpha = 0.18f)
            }

            Column(
                modifier = modifier
                    .widthIn(max = LiquidSheetMaxWidth)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val offsetY = size.height * (1f - progress)
                        translationY = offsetY
                        val scale = 0.96f + 0.04f * progress
                        scaleX = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(sheetShape)
                    // Dialog creates a separate window — Backdrop's layerBackdrop cannot capture
                    // content from the parent window, and on Xiaomi devices the system's
                    // MiBackgroundBlurBlend causes a native stack overflow (SIGSEGV) when
                    // processing blur nodes in a Dialog's render tree. Use a solid frosted-glass
                    // appearance via background colors instead of real-time backdrop effects.
                    .background(surfaceColor, sheetShape)
                    .background(sheenColor, sheetShape)
                    // Block clicks from passing through sheet to scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .pointerInput(allowDismiss) {
                        if (!allowDismiss) return@pointerInput
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                val currentProgress = sheetProgress.value
                                val shouldDismiss =
                                    currentProgress < (1f - LiquidSheetDismissThreshold)
                                if (shouldDismiss) {
                                    currentOnDismissRequest?.invoke()
                                } else {
                                    scope.launch {
                                        sheetProgress.animateTo(
                                            1f,
                                            spring(dampingRatio = 0.82f, stiffness = 500f)
                                        )
                                    }
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                                if (totalDrag > 0f) {
                                    val heightPx = with(density) { 400.dp.toPx() }
                                    val newProgress =
                                        (1f - totalDrag / heightPx).coerceIn(0f, 1f)
                                    scope.launch {
                                        sheetProgress.snapTo(newProgress)
                                    }
                                }
                            }
                        )
                    }
                    .padding(bottom = navBarPadding.calculateBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Spacer(modifier = Modifier.height(LiquidSheetDragHandleTopPadding))
                Box(
                    modifier = Modifier
                        .width(LiquidSheetDragHandleWidth)
                        .height(LiquidSheetDragHandleHeight)
                        .clip(RoundedRectangle(2.dp))
                        .background(dragHandleColor)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Title bar
                if (title != null || startAction != null || endAction != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        startAction?.invoke()
                        if (title != null) {
                            Text(
                                text = title,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                color = MiuixTheme.colorScheme.onBackground,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        endAction?.invoke()
                    }
                }

                // Content — Dialog guarantees finite height constraints from the window,
                // so callers can freely use LazyColumn, verticalScroll, etc.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}
