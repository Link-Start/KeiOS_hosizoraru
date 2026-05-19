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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
 * v2 Liquid Glass BottomSheet — iOS-style multi-detent bottom sheet.
 *
 * Behavior:
 * - Opens at half-screen height (50%) regardless of content amount — doesn't overwhelm the user
 * - User can drag up to expand (max = screen minus status bar)
 * - User can drag down to shrink or dismiss
 * - When expanded to max, content inside becomes scrollable
 * - When [allowDismiss] is false: dragging down is allowed for resizing, but releasing below the
 *   half detent triggers a bounce-back animation + calls [onBlockedDismissRequest] as a visual
 *   cue (like Miuix's "unsaved changes" prompt). The sheet never fully closes.
 * - When [allowDismiss] is true: dragging below 20% of available height dismisses the sheet.
 *
 * Detents:
 * - Half (0.5): default resting position
 * - Full (1.0): maximum expanded, content scrolls
 * - Dismiss (0.0): sheet is off-screen
 */

private val LiquidSheetCornerRadius = 28.dp
private val LiquidSheetMaxWidth = 480.dp
private val LiquidSheetDragHandleWidth = 36.dp
private val LiquidSheetDragHandleHeight = 4.dp
private val LiquidSheetDragHandleTopPadding = 10.dp
private const val LiquidSheetScrimAlpha = 0.38f

/** Half-screen detent: sheet rests here on open. */
private const val DETENT_HALF = 0.50f

/** Full-screen detent: max expansion (below status bar). */
private const val DETENT_FULL = 1.0f

/** Below this fraction, sheet dismisses (when allowDismiss = true). */
private const val DETENT_DISMISS = 0.18f

/** Below this fraction from half, blocked-dismiss bounce triggers (when allowDismiss = false). */
private const val DETENT_BOUNCE = 0.35f

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

    // heightFraction: 0 = off-screen, DETENT_HALF = half screen, DETENT_FULL = max height
    val heightFraction = remember { Animatable(0f) }
    var isRendered by remember { mutableStateOf(false) }

    LaunchedEffect(show) {
        if (show) {
            isRendered = true
            // Always open to half-screen detent
            heightFraction.animateTo(
                targetValue = DETENT_HALF,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 350f)
            )
        } else {
            if (isRendered) {
                heightFraction.animateTo(
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
        // Remove the system's default dim background (FLAG_DIM_BEHIND) — we draw our own scrim.
        // Without this, closing the sheet leaves a residual gray overlay from the Dialog window
        // that persists until the Dialog composable is fully removed from the tree.
        top.yukonga.miuix.kmp.utils.RemovePlatformDialogDefaultEffects()

        val fraction = heightFraction.value.coerceIn(0f, DETENT_FULL)
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // Available height = screen minus status bar
        val availableHeightDp = screenHeightDp - statusBarHeight
        val availableHeightPx = with(density) { availableHeightDp.toPx() }
        // Current sheet height based on fraction
        val sheetHeightDp = availableHeightDp * fraction
        // Scrim alpha scales with how much of the screen is covered
        val scrimAlpha = LiquidSheetScrimAlpha * (fraction / DETENT_FULL).coerceIn(0f, 1f)
        // Visibility progress for enter/exit animation (0 when hidden, 1 when at half or above)
        val visibilityProgress = (fraction / DETENT_HALF).coerceIn(0f, 1f)

        // Nested scroll connection: when content scrolls to top and user keeps pulling down,
        // the sheet shrinks. When content is at top and user pushes up, sheet expands.
        // This creates the iOS-style "scroll content OR resize sheet" behavior.
        val sheetNestedScrollConnection = remember(allowDismiss) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    val currentFraction = heightFraction.value
                    // User scrolling down (dy > 0) and sheet is above half → shrink sheet first
                    if (dy > 0f && currentFraction > DETENT_HALF) {
                        val consumed = dy / availableHeightPx
                        val newFraction = (currentFraction - consumed).coerceAtLeast(DETENT_HALF)
                        scope.launch { heightFraction.snapTo(newFraction) }
                        val actualConsumed = (currentFraction - newFraction) * availableHeightPx
                        return Offset(0f, actualConsumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val dy = available.y
                    val currentFraction = heightFraction.value
                    // Content couldn't consume the scroll (at boundary):
                    // - Pulling up (dy < 0) at top of content → expand sheet
                    // - Pulling down (dy > 0) at top of content → shrink sheet
                    if (dy < 0f && currentFraction < DETENT_FULL) {
                        // Expand
                        val consumed = -dy / availableHeightPx
                        val newFraction = (currentFraction + consumed).coerceAtMost(DETENT_FULL)
                        scope.launch { heightFraction.snapTo(newFraction) }
                        return Offset(0f, -(newFraction - currentFraction) * availableHeightPx)
                    }
                    if (dy > 0f && currentFraction > DETENT_HALF) {
                        // Shrink (content at top, pulling down)
                        val consumed = dy / availableHeightPx
                        val newFraction = (currentFraction - consumed).coerceAtLeast(DETENT_HALF)
                        scope.launch { heightFraction.snapTo(newFraction) }
                        return Offset(0f, (currentFraction - newFraction) * availableHeightPx)
                    }
                    if (dy > 0f && currentFraction <= DETENT_HALF && allowDismiss) {
                        // Below half, pulling down → dismiss gesture
                        val consumed = dy / availableHeightPx
                        val newFraction = (currentFraction - consumed).coerceAtLeast(0f)
                        scope.launch { heightFraction.snapTo(newFraction) }
                        return Offset(0f, (currentFraction - newFraction) * availableHeightPx)
                    }
                    return Offset.Zero
                }
            }
        }

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
                .background(Color.Black.copy(alpha = scrimAlpha))
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

            // Official Backdrop recommendation: simple semi-transparent white surface.
            // Liquid glass effect comes from lens refraction, not complex color layering.
            val surfaceColor = Color.White.copy(alpha = 0.5f)
            val dragHandleColor = if (isDark) {
                Color.White.copy(alpha = 0.28f)
            } else {
                Color.Black.copy(alpha = 0.18f)
            }

            Column(
                modifier = modifier
                    .widthIn(max = LiquidSheetMaxWidth)
                    .fillMaxWidth()
                    .height(sheetHeightDp.coerceAtLeast(0.dp))
                    .graphicsLayer {
                        // Subtle scale on enter for depth
                        val scale = 0.95f + 0.05f * visibilityProgress
                        scaleX = scale
                        scaleY = scale
                        alpha = visibilityProgress
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(sheetShape)
                    .background(surfaceColor, sheetShape)
                    // Block clicks from passing through to scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    // Drag gesture for resizing
                    .pointerInput(allowDismiss) {
                        val availableHeightPx = with(density) { availableHeightDp.toPx() }
                        var dragStartFraction = 0f

                        detectVerticalDragGestures(
                            onDragStart = {
                                dragStartFraction = heightFraction.value
                            },
                            onDragEnd = {
                                val current = heightFraction.value
                                scope.launch {
                                    when {
                                        // Dismiss zone
                                        allowDismiss && current < DETENT_DISMISS -> {
                                            currentOnDismissRequest?.invoke()
                                        }
                                        // Below half: snap to half (or bounce if not dismissable)
                                        current < DETENT_HALF -> {
                                            if (!allowDismiss && current < DETENT_BOUNCE) {
                                                // Bounce back + notify blocked dismiss
                                                currentOnBlockedDismissRequest?.invoke()
                                            }
                                            heightFraction.animateTo(
                                                DETENT_HALF,
                                                spring(dampingRatio = 0.82f, stiffness = 450f)
                                            )
                                        }
                                        // Between half and 75%: snap to half
                                        current < (DETENT_HALF + DETENT_FULL) / 2f -> {
                                            heightFraction.animateTo(
                                                DETENT_HALF,
                                                spring(dampingRatio = 0.85f, stiffness = 400f)
                                            )
                                        }
                                        // Above 75%: snap to full
                                        else -> {
                                            heightFraction.animateTo(
                                                DETENT_FULL,
                                                spring(dampingRatio = 0.85f, stiffness = 400f)
                                            )
                                        }
                                    }
                                }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                // Convert drag pixels to fraction change
                                // Negative dragAmount = drag up = expand
                                // Positive dragAmount = drag down = shrink
                                val fractionDelta = -dragAmount / availableHeightPx
                                val newFraction = (heightFraction.value + fractionDelta)
                                    .coerceIn(
                                        // Allow dragging below half for dismiss gesture feel,
                                        // but clamp at 0 (fully hidden)
                                        if (allowDismiss) 0f else DETENT_DISMISS * 0.5f,
                                        DETENT_FULL
                                    )
                                scope.launch {
                                    heightFraction.snapTo(newFraction)
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

                // Content — nested scroll connection allows content scroll to expand/shrink
                // the sheet when at scroll boundaries (iOS-style behavior).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .nestedScroll(sheetNestedScrollConnection)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}
