@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.widget.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import os.kei.ui.page.main.back.BackNavigationCommitGate
import os.kei.ui.page.main.back.BackNavigationSource
import os.kei.ui.page.main.back.LocalBackNavigationRuntimeController
import os.kei.ui.page.main.back.LocalBackNavigationRuntimeState
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.basic.Text

/**
 * v2 Liquid Glass BottomSheet — iOS-style multi-detent bottom sheet.
 *
 * Behavior:
 * - Opens at three-quarter height (75%) for a useful default reading surface
 * - Auto-expands to full height when the content cannot fit at the opening detent
 * - User can drag up to expand (max = screen minus status bar)
 * - User can drag down to shrink or dismiss
 * - When expanded to max, content inside becomes scrollable
 * - When [allowDismiss] is false: dragging down is allowed for resizing, but releasing below the
 *   half detent triggers a bounce-back animation + calls [onBlockedDismissRequest] as a visual
 *   cue (like Miuix's "unsaved changes" prompt). The sheet never fully closes.
 * - When [allowDismiss] is true: dragging below 20% of available height dismisses the sheet.
 *
 * Detents:
 * - Half (0.5): compact resting position for peeking at the background page
 * - Three-quarter (0.75): default resting position on open
 * - Full (1.0): maximum expanded (below status bar), content scrolls
 * - Dismiss (0.0): sheet is off-screen
 *
 * Scroll priority:
 * - When sheet is NOT at full height: upward scroll EXPANDS the sheet (priority over content scroll)
 * - When sheet IS at full height: upward scroll scrolls content normally
 * - Downward scroll at content top: SHRINKS the sheet first
 */

private val LiquidSheetCornerRadius = 28.dp
private val LiquidSheetMaxWidth = 480.dp
private val LiquidSheetDragHandleWidth = 36.dp
private val LiquidSheetDragHandleHeight = 4.dp
private val LiquidSheetDragHandleTopPadding = 8.dp
private val LiquidSheetHeaderBottomPadding = 4.dp
private val LiquidSheetTitleTopPadding = 6.dp
private val LiquidSheetContentTopPadding = 8.dp
private const val LiquidSheetScrimAlpha = 0.38f

/** Half-screen detent: sheet rests here on open. */
private const val DETENT_HALF = 0.50f

/** Three-quarter detent: intermediate expansion. */
private const val DETENT_THREE_QUARTER = 0.75f

/** Full-screen detent: max expansion (below status bar). */
private const val DETENT_FULL = 1.0f

/** Below this fraction, sheet dismisses (when allowDismiss = true). */
private const val DETENT_DISMISS = 0.18f

/** Below this fraction from half, blocked-dismiss bounce triggers (when allowDismiss = false). */
private const val DETENT_BOUNCE = 0.35f

private const val LIQUID_SHEET_BLOCKED_BACK_DRAG_FACTOR = 0.1f

/**
 * Detent at which the iOS-style "fade to solid" begins. Below this, the sheet stays at full
 * frosted-glass transparency (semi-transparent surface + visible blurred backdrop). Above this,
 * the sheet progressively becomes more opaque, reaching near-solid by [DETENT_FULL]. This
 * matches iOS behavior: half-screen sheets feel like glass over your content, but full-screen
 * sheets become a solid reading surface for better contrast and readability.
 */
private const val DETENT_SOLIDNESS_START = 0.75f

/**
 * Initial detent the sheet animates to when [LiquidGlassBottomSheet.show] flips to `true`.
 * Most sheets default to [ThreeQuarter]. [Half] remains available as a compact user-controlled
 * resting point for peeking at the background page, and [Full] stays available for callers that
 * know their content needs the largest surface immediately.
 */
enum class LiquidSheetInitialDetent(
    internal val fraction: Float,
) {
    Half(DETENT_HALF),
    ThreeQuarter(DETENT_THREE_QUARTER),
    Full(DETENT_FULL),
}

internal fun liquidSheetPredictiveBackOffsetFraction(
    sheetFraction: Float,
    progress: Float,
    allowDismiss: Boolean,
): Float {
    val sheet = sheetFraction.coerceIn(0f, DETENT_FULL)
    val clampedProgress = progress.coerceIn(0f, 1f)
    val offset = sheet * clampedProgress
    return if (allowDismiss) {
        offset
    } else {
        offset * LIQUID_SHEET_BLOCKED_BACK_DRAG_FACTOR
    }
}

internal fun liquidSheetPredictiveBackScrimFactor(
    sheetFraction: Float,
    offsetFraction: Float,
    allowDismiss: Boolean,
): Float {
    if (!allowDismiss) return 1f
    val sheet = sheetFraction.coerceIn(0f, DETENT_FULL)
    if (sheet <= 0f) return 0f
    return (1f - (offsetFraction / sheet).coerceIn(0f, 1f)).coerceIn(0f, 1f)
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
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    allowDismiss: Boolean = true,
    onBlockedDismissRequest: (() -> Unit)? = null,
    initialDetent: LiquidSheetInitialDetent = LiquidSheetInitialDetent.ThreeQuarter,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val currentOnDismissFinished by rememberUpdatedState(onDismissFinished)
    val currentOnBlockedDismissRequest by rememberUpdatedState(onBlockedDismissRequest)
    val runtimeController = LocalBackNavigationRuntimeController.current
    val runtimeState = LocalBackNavigationRuntimeState.current
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val sheetNavigationBackEnabled =
        runtimeState.policy?.frameworkAnimationsEnabled
            ?: (transitionAnimationsEnabled && LocalPredictiveBackAnimationsEnabled.current)
    val backCommitGate = remember { BackNavigationCommitGate() }

    // heightFraction: 0 = off-screen, DETENT_HALF = half screen, DETENT_FULL = max height
    val heightFraction = remember { Animatable(0f) }
    val predictiveBackOffsetFraction = remember { Animatable(0f) }
    var isRendered by remember { mutableStateOf(false) }
    var contentOverflowsOpeningDetent by remember { mutableStateOf(false) }
    var userAdjustedDetent by remember { mutableStateOf(false) }
    var autoExpandedForContent by remember { mutableStateOf(false) }
    var openingDetentReady by remember { mutableStateOf(false) }
    var closingFromPredictiveBack by remember { mutableStateOf(false) }
    val canDismissSheet = allowDismiss && currentOnDismissRequest != null

    LaunchedEffect(show, initialDetent) {
        if (show) {
            isRendered = true
            closingFromPredictiveBack = false
            userAdjustedDetent = false
            autoExpandedForContent = false
            contentOverflowsOpeningDetent = false
            openingDetentReady = false
            predictiveBackOffsetFraction.snapTo(0f)
            // Animate to the caller-requested initial detent. The sheet remains
            // user-resizable across all three detents after this.
            heightFraction.animateTo(
                targetValue = initialDetent.fraction,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 350f),
            )
            openingDetentReady = true
        } else {
            if (isRendered) {
                if (closingFromPredictiveBack) {
                    val dismissOffsetTarget =
                        maxOf(
                            heightFraction.value,
                            predictiveBackOffsetFraction.value,
                        ).coerceIn(0f, DETENT_FULL)
                    predictiveBackOffsetFraction.animateTo(
                        targetValue = dismissOffsetTarget,
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 500f),
                    )
                    isRendered = false
                    heightFraction.snapTo(0f)
                    predictiveBackOffsetFraction.snapTo(0f)
                    closingFromPredictiveBack = false
                } else {
                    heightFraction.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 500f),
                    )
                    predictiveBackOffsetFraction.snapTo(0f)
                    isRendered = false
                }
                currentOnDismissFinished?.invoke()
            }
        }
    }

    LaunchedEffect(show, initialDetent, contentOverflowsOpeningDetent, userAdjustedDetent, openingDetentReady) {
        if (!show) return@LaunchedEffect
        if (initialDetent != LiquidSheetInitialDetent.ThreeQuarter) return@LaunchedEffect
        if (!openingDetentReady || !contentOverflowsOpeningDetent || userAdjustedDetent || autoExpandedForContent) {
            return@LaunchedEffect
        }
        autoExpandedForContent = true
        heightFraction.animateTo(
            targetValue = DETENT_FULL,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 360f),
        )
    }

    if (!isRendered && !show) return

    Dialog(
        onDismissRequest = {
            if (canDismissSheet) {
                currentOnDismissRequest?.invoke()
            } else {
                currentOnBlockedDismissRequest?.invoke()
            }
        },
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        // Remove the system's default dim background (FLAG_DIM_BEHIND) — we draw our own scrim.
        // Without this, closing the sheet leaves a residual gray overlay from the Dialog window
        // that persists until the Dialog composable is fully removed from the tree.
        top.yukonga.miuix.kmp.utils
            .RemovePlatformDialogDefaultEffects()

        val fraction = heightFraction.value.coerceIn(0f, DETENT_FULL)
        val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        // Available height = screen minus status bar
        val availableHeightDp = screenHeightDp - statusBarHeight
        val availableHeightPx = with(density) { availableHeightDp.toPx() }
        // Current sheet height based on fraction
        val sheetHeightDp = availableHeightDp * fraction
        // Scrim alpha scales with how much of the screen is covered
        val predictiveBackScrimFactor =
            liquidSheetPredictiveBackScrimFactor(
                sheetFraction = fraction,
                offsetFraction = predictiveBackOffsetFraction.value,
                allowDismiss = canDismissSheet,
            )
        val scrimAlpha =
            LiquidSheetScrimAlpha *
                (fraction / DETENT_FULL).coerceIn(0f, 1f) *
                predictiveBackScrimFactor
        // Visibility progress for enter/exit animation (0 when hidden, 1 when at half or above)
        val visibilityProgress = (fraction / DETENT_HALF).coerceIn(0f, 1f)

        // Nested scroll connection: implements iOS-style smart scroll priority.
        // When sheet is NOT at full height: upward scroll EXPANDS the sheet (not scroll content).
        // When sheet IS at full height: upward scroll scrolls content normally.
        // Downward scroll at content top: SHRINKS the sheet first.
        val sheetNestedScrollConnection =
            remember(canDismissSheet) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        val dy = available.y
                        val currentFraction = heightFraction.value

                        // User scrolling UP (dy < 0) and sheet is NOT at full → expand sheet first.
                        // Content doesn't scroll until sheet is maximized.
                        if (dy < 0f && currentFraction < DETENT_FULL) {
                            userAdjustedDetent = true
                            val consumed = -dy / availableHeightPx
                            val newFraction = (currentFraction + consumed).coerceAtMost(DETENT_FULL)
                            scope.launch { heightFraction.snapTo(newFraction) }
                            val actualConsumed = (newFraction - currentFraction) * availableHeightPx
                            return Offset(0f, -actualConsumed)
                        }

                        // User scrolling DOWN (dy > 0): do NOT intercept here.
                        // Let content scroll back to top first. Only after content can't scroll
                        // anymore (onPostScroll receives leftover), we shrink the sheet.
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        val dy = available.y
                        val currentFraction = heightFraction.value

                        // Content has consumed what it can. Leftover dy > 0 means content is at
                        // the top and can't scroll down anymore → now shrink the sheet.
                        if (dy > 0f) {
                            when {
                                // Sheet above half → shrink toward half
                                currentFraction > DETENT_HALF -> {
                                    userAdjustedDetent = true
                                    val delta = dy / availableHeightPx
                                    val newFraction = (currentFraction - delta).coerceAtLeast(DETENT_HALF)
                                    scope.launch { heightFraction.snapTo(newFraction) }
                                    return Offset(0f, (currentFraction - newFraction) * availableHeightPx)
                                }

                                // Sheet at or below half → dismiss zone
                                canDismissSheet -> {
                                    userAdjustedDetent = true
                                    val delta = dy / availableHeightPx
                                    val newFraction = (currentFraction - delta).coerceAtLeast(0f)
                                    scope.launch { heightFraction.snapTo(newFraction) }
                                    return Offset(0f, (currentFraction - newFraction) * availableHeightPx)
                                }

                                // Not dismissable: allow slight over-drag for bounce feedback
                                currentFraction > DETENT_BOUNCE * 0.8f -> {
                                    userAdjustedDetent = true
                                    val delta = dy / availableHeightPx
                                    val newFraction = (currentFraction - delta).coerceAtLeast(DETENT_BOUNCE * 0.8f)
                                    scope.launch { heightFraction.snapTo(newFraction) }
                                    return Offset(0f, (currentFraction - newFraction) * availableHeightPx)
                                }
                            }
                        }

                        return Offset.Zero
                    }
                }
            }

        BackHandler(enabled = !sheetNavigationBackEnabled) {
            if (canDismissSheet) {
                currentOnDismissRequest?.invoke()
            } else {
                currentOnBlockedDismissRequest?.invoke()
            }
        }

        if (sheetNavigationBackEnabled) {
            val navigationEventState =
                rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
            var predictiveBackStartFraction by remember { mutableStateOf<Float?>(null) }
            val resetPredictiveBackGesture: suspend () -> Unit = {
                predictiveBackOffsetFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
                )
                predictiveBackStartFraction = null
                runtimeController.reset()
            }

            NavigationBackHandler(
                state = navigationEventState,
                isBackEnabled = true,
                onBackCancelled = {
                    scope.launch {
                        resetPredictiveBackGesture()
                    }
                },
                onBackCompleted = {
                    scope.launch {
                        runtimeController.beginCommit(BackNavigationSource.Modal)
                        predictiveBackStartFraction = null
                        if (canDismissSheet) {
                            backCommitGate.reset()
                            backCommitGate.tryCommit {
                                closingFromPredictiveBack = true
                                currentOnDismissRequest?.invoke()
                            }
                        } else {
                            backCommitGate.reset()
                            backCommitGate.tryCommit {
                                currentOnBlockedDismissRequest?.invoke()
                            }
                            predictiveBackOffsetFraction.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 450f),
                            )
                        }
                        runtimeController.reset()
                    }
                },
            )

            LaunchedEffect(canDismissSheet, sheetNavigationBackEnabled) {
                snapshotFlow { navigationEventState.transitionState }
                    .collect { transitionState ->
                        if (
                            transitionState is NavigationEventTransitionState.InProgress &&
                            transitionState.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                        ) {
                            val startFraction =
                                predictiveBackStartFraction
                                    ?: heightFraction.value.coerceIn(0f, DETENT_FULL).also { fraction ->
                                        predictiveBackStartFraction = fraction
                                        backCommitGate.reset()
                                        runtimeController.beginGesture(BackNavigationSource.Modal)
                                    }
                            val event = transitionState.latestEvent
                            runtimeController.updateGestureProgress(
                                progress = event.progress,
                                source = BackNavigationSource.Modal,
                            )
                            predictiveBackOffsetFraction.snapTo(
                                liquidSheetPredictiveBackOffsetFraction(
                                    sheetFraction = startFraction,
                                    progress = event.progress,
                                    allowDismiss = canDismissSheet,
                                ),
                            )
                        }
                    }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = canDismissSheet,
                    ) {
                        currentOnDismissRequest?.invoke()
                    },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetShape = RoundedRectangle(LiquidSheetCornerRadius)

            // iOS-style "fade to solid" — derived from the current detent fraction.
            // - 0 below DETENT_SOLIDNESS_START → full frosted glass (semi-transparent surface,
            //   visible sheen/border for that liquid-glass look).
            // - 1 at DETENT_FULL → near-opaque solid (white in light / near-black in dark) for
            //   readability when the sheet covers the screen, with sheen/border faded out
            //   because there's no longer a backdrop to refract against.
            // Smoothstep curve gives a natural ease so the transition feels organic during drag
            // rather than a linear fade.
            val solidnessLinear =
                (
                    (fraction - DETENT_SOLIDNESS_START) /
                        (DETENT_FULL - DETENT_SOLIDNESS_START)
                ).coerceIn(0f, 1f)
            val solidnessProgress = solidnessLinear * solidnessLinear * (3f - 2f * solidnessLinear)

            // Multi-layer frosted glass surface — simulates liquid glass without Backdrop's
            // drawBackdrop (which crashes on Xiaomi due to MiBackgroundBlurBlend SIGSEGV in Dialog).
            // Uses layered semi-transparent fills + border + sheen to create depth and glass feel.
            // Surface alpha lerps toward near-solid as the sheet expands toward full.
            val surfaceColor =
                if (isDark) {
                    Color(0xFF141420).copy(alpha = lerp(0.88f, 0.985f, solidnessProgress))
                } else {
                    Color(0xFFF8F9FC).copy(alpha = lerp(0.84f, 0.985f, solidnessProgress))
                }
            // Sheen and border fade as the sheet becomes solid — at full opacity there's no
            // backdrop to refract against, so glass highlights would just look like noise.
            val sheenColor =
                if (isDark) {
                    Color.White.copy(alpha = lerp(0.06f, 0.02f, solidnessProgress))
                } else {
                    Color.White.copy(alpha = lerp(0.28f, 0.08f, solidnessProgress))
                }
            val borderColor =
                if (isDark) {
                    Color.White.copy(alpha = lerp(0.12f, 0.04f, solidnessProgress))
                } else {
                    Color.White.copy(alpha = lerp(0.65f, 0.18f, solidnessProgress))
                }
            val headerContentColor =
                if (isDark) {
                    Color.White.copy(alpha = 0.88f)
                } else {
                    Color.Black.copy(alpha = 0.78f)
                }
            val dragHandleColor =
                if (isDark) {
                    Color.White.copy(alpha = lerp(0.34f, 0.26f, solidnessProgress))
                } else {
                    Color.Black.copy(alpha = lerp(0.24f, 0.18f, solidnessProgress))
                }

            Box(
                modifier =
                    modifier
                        .widthIn(max = LiquidSheetMaxWidth)
                        .fillMaxWidth()
                        .height(sheetHeightDp.coerceAtLeast(0.dp))
                        .graphicsLayer {
                            // Subtle scale on enter for depth
                            val scale = 0.95f + 0.05f * visibilityProgress
                            scaleX = scale
                            scaleY = scale
                            alpha = visibilityProgress
                            translationY = availableHeightPx * predictiveBackOffsetFraction.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }.clip(sheetShape)
                        .background(surfaceColor, sheetShape)
                        .background(sheenColor, sheetShape)
                        .border(width = 0.5.dp, color = borderColor, shape = sheetShape)
                        // Block clicks from passing through to scrim
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {}
                        // Drag gesture for resizing
                        .pointerInput(canDismissSheet) {
                            val availableHeightPx = with(density) { availableHeightDp.toPx() }
                            var dragStartFraction = 0f

                            detectVerticalDragGestures(
                                onDragStart = {
                                    userAdjustedDetent = true
                                    dragStartFraction = heightFraction.value
                                },
                                onDragEnd = {
                                    val current = heightFraction.value
                                    val draggedUp = current > dragStartFraction
                                    scope.launch {
                                        when {
                                            // Dismiss zone
                                            canDismissSheet && current < DETENT_DISMISS -> {
                                                currentOnDismissRequest?.invoke()
                                            }

                                            // Below half: snap to half (or bounce if not dismissable)
                                            current < DETENT_HALF -> {
                                                if (!canDismissSheet && current < DETENT_BOUNCE) {
                                                    currentOnBlockedDismissRequest?.invoke()
                                                }
                                                heightFraction.animateTo(
                                                    DETENT_HALF,
                                                    spring(dampingRatio = 0.82f, stiffness = 450f),
                                                )
                                            }

                                            // Three-detent snapping based on position + direction:
                                            // Between half and three-quarter midpoint
                                            current < (DETENT_HALF + DETENT_THREE_QUARTER) / 2f -> {
                                                // If dragging up, go to three-quarter; otherwise snap back to half
                                                val target = if (draggedUp) DETENT_THREE_QUARTER else DETENT_HALF
                                                heightFraction.animateTo(
                                                    target,
                                                    spring(dampingRatio = 0.85f, stiffness = 400f),
                                                )
                                            }

                                            // Around three-quarter zone
                                            current < (DETENT_THREE_QUARTER + DETENT_FULL) / 2f -> {
                                                // Snap to three-quarter (closest detent)
                                                val target = if (draggedUp) DETENT_FULL else DETENT_THREE_QUARTER
                                                heightFraction.animateTo(
                                                    target,
                                                    spring(dampingRatio = 0.85f, stiffness = 400f),
                                                )
                                            }

                                            // Above three-quarter-full midpoint: snap to full
                                            else -> {
                                                heightFraction.animateTo(
                                                    DETENT_FULL,
                                                    spring(dampingRatio = 0.85f, stiffness = 400f),
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
                                    val newFraction =
                                        (heightFraction.value + fractionDelta)
                                            .coerceIn(
                                                // Allow dragging below half for dismiss gesture feel,
                                                // but clamp at 0 (fully hidden)
                                                if (canDismissSheet) 0f else DETENT_DISMISS * 0.5f,
                                                DETENT_FULL,
                                            )
                                    scope.launch {
                                        heightFraction.snapTo(newFraction)
                                    }
                                },
                            )
                        },
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(LiquidSheetDragHandleTopPadding))
                    Box(
                        modifier =
                            Modifier
                                .width(LiquidSheetDragHandleWidth)
                                .height(LiquidSheetDragHandleHeight)
                                .clip(RoundedRectangle(2.dp))
                                .background(dragHandleColor),
                    )
                    Spacer(modifier = Modifier.height(LiquidSheetTitleTopPadding))

                    // Title bar — title is centered as an overlay so asymmetric start/end action
                    // widths don't shift it off-center. Actions are positioned absolutely so the
                    // title's true horizontal center aligns with the sheet's center, matching the
                    // visual balance of iOS sheets.
                    if (title != null || startAction != null || endAction != null) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (title != null) {
                                Text(
                                    text = title,
                                    // Reserve horizontal space so a long title is ellipsised
                                    // before colliding with the actions instead of overlapping.
                                    modifier = Modifier.padding(horizontal = 56.dp),
                                    color = headerContentColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (startAction != null) {
                                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                                    startAction.invoke()
                                }
                            }
                            if (endAction != null) {
                                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                    endAction.invoke()
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(LiquidSheetHeaderBottomPadding))

                    // Content — nested scroll connection allows content scroll to expand/shrink
                    // the sheet when at scroll boundaries (iOS-style behavior).
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .nestedScroll(sheetNestedScrollConnection)
                                .padding(horizontal = 20.dp)
                                .padding(top = LiquidSheetContentTopPadding, bottom = 16.dp),
                    ) {
                        CompositionLocalProvider(
                            LocalLiquidSheetContentOverflowReporter provides { overflows ->
                                contentOverflowsOpeningDetent = overflows
                            },
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
