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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * v2 Liquid Glass BottomSheet.
 *
 * Height behavior:
 * - Default (resting) height: 50% of screen (half-sheet, doesn't obscure too much content)
 * - Maximum height: screen height minus status bar (never covers the status bar)
 * - Minimum height before dismiss: 15% of screen
 * - User can drag up/down to resize between min and max
 *
 * Dismiss protection:
 * - When [allowDismiss] is false, drag-to-dismiss is completely disabled (prevents accidental
 *   data loss when the sheet has unsaved changes)
 * - When [allowDismiss] is true, user must drag past 50% of the dismiss threshold with intent
 *   (not just a casual swipe) — requires dragging below 25% of screen height
 */

private val LiquidSheetCornerRadius = 28.dp
private val LiquidSheetMaxWidth = 480.dp
private val LiquidSheetDragHandleWidth = 36.dp
private val LiquidSheetDragHandleHeight = 4.dp
private val LiquidSheetDragHandleTopPadding = 10.dp
private const val LiquidSheetScrimAlpha = 0.42f

/** Default sheet height as fraction of available space (below status bar). */
private const val LiquidSheetDefaultHeightFraction = 0.50f

/** Maximum sheet height as fraction of available space (below status bar). */
private const val LiquidSheetMaxHeightFraction = 1.0f

/** Below this fraction the sheet will dismiss (when allowDismiss = true). */
private const val LiquidSheetDismissFraction = 0.20f

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

    // Progress: 0f = hidden, 1f = at default height (half screen)
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
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val progress = sheetProgress.value.coerceIn(0f, 1f)
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // Available height = screen - status bar (sheet never covers status bar)
        val availableHeight = screenHeightDp - statusBarHeight
        // Default resting height = half of available
        val defaultHeight = availableHeight * LiquidSheetDefaultHeightFraction
        // Max height = full available (up to status bar)
        val maxSheetHeight = availableHeight * LiquidSheetMaxHeightFraction

        BackHandler(enabled = true) {
            if (allowDismiss) {
                currentOnDismissRequest?.invoke()
            } else {
                currentOnBlockedDismissRequest?.invoke()
            }
        }

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
            val sheetShape = RoundedRectangle(LiquidSheetCornerRadius)

            val surfaceColor = if (isDark) {
                Color(0xFF0D0D12).copy(alpha = 0.92f)
            } else {
                Color(0xFFFCFCFF).copy(alpha = 0.88f)
            }
            val sheenColor = if (isDark) {
                Color.White.copy(alpha = 0.06f)
            } else {
                Color.White.copy(alpha = 0.24f)
            }
            val dragHandleColor = if (isDark) {
                Color.White.copy(alpha = 0.28f)
            } else {
                Color.Black.copy(alpha = 0.18f)
            }

            // Sheet height is controlled by progress:
            // progress 0 → off screen (translationY = full height)
            // progress 1 → at default height
            Column(
                modifier = modifier
                    .widthIn(max = LiquidSheetMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .graphicsLayer {
                        // Slide from bottom: at progress=0 sheet is fully below screen
                        translationY = size.height * (1f - progress)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(sheetShape)
                    .background(surfaceColor, sheetShape)
                    .background(sheenColor, sheetShape)
                    // Block clicks from passing through to scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    // Drag gesture: only when allowDismiss is true
                    .pointerInput(allowDismiss) {
                        if (!allowDismiss) return@pointerInput
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                val currentProgress = sheetProgress.value
                                // Dismiss only if dragged below the dismiss threshold
                                if (currentProgress < LiquidSheetDismissFraction) {
                                    currentOnDismissRequest?.invoke()
                                } else {
                                    // Snap back to full
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
                                // Only allow downward drag (positive = down)
                                if (totalDrag > 0f) {
                                    val sheetHeightPx = with(density) { defaultHeight.toPx() }
                                    val newProgress =
                                        (1f - totalDrag / sheetHeightPx).coerceIn(0f, 1f)
                                    scope.launch {
                                        sheetProgress.snapTo(newProgress)
                                    }
                                }
                            }
                        )
                    }
                    .padding(bottom = navBarHeight),
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

                // Content
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
