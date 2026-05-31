package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.flow.collectLatest
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.animation.DampedDragAnimation
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

@Immutable
data class LiquidSliderKeyPoint(
    val value: Float,
    val color: Color = Color.Unspecified,
    val size: Dp = 5.dp
)

@Composable
fun LiquidMusicProgressSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color.Unspecified,
    inactiveColor: Color = Color.Unspecified,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    onInteractionChanged: (Boolean) -> Unit = {}
) {
    val isLightTheme = !isSystemInDarkTheme()
    val defaultActiveColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF5DAEFF)
    val defaultInactiveColor = if (isLightTheme) {
        Color(0xFF1D1D1F).copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    LiquidTrackSlider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        onInteractionChanged = onInteractionChanged,
        style = LiquidTrackSliderStyle(
            activeColor = if (activeColor.isSpecified) activeColor else defaultActiveColor,
            inactiveColor = if (inactiveColor.isSpecified) inactiveColor else defaultInactiveColor,
            trackHeight = 4.dp,
            thumbWidth = 30.dp,
            thumbHeight = 18.dp,
            pressedScale = 1.28f,
            thumbGlassSurfaceAlpha = thumbGlassSurfaceAlphaFor(isLightTheme),
            thumbRestingHighlightAlpha = thumbRestingHighlightAlphaFor(isLightTheme)
        )
    )
}

@Composable
fun LiquidVolumeSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color.Unspecified,
    inactiveColor: Color = Color.Unspecified,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    onInteractionChanged: (Boolean) -> Unit = {}
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (activeColor.isSpecified) {
        activeColor
    } else if (isLightTheme) {
        Color(0xFF0088FF)
    } else {
        Color(0xFF5DAEFF)
    }
    val defaultInactiveColor = if (isLightTheme) {
        Color(0xFF1D1D1F).copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    LiquidTrackSlider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        onInteractionChanged = onInteractionChanged,
        style = LiquidTrackSliderStyle(
            activeColor = accentColor.copy(alpha = 0.92f),
            inactiveColor = if (inactiveColor.isSpecified) inactiveColor else defaultInactiveColor,
            trackHeight = 6.dp,
            thumbWidth = 40.dp,
            thumbHeight = 26.dp,
            pressedScale = 1.36f,
            thumbGlassSurfaceAlpha = thumbGlassSurfaceAlphaFor(isLightTheme),
            thumbRestingHighlightAlpha = thumbRestingHighlightAlphaFor(isLightTheme)
        )
    )
}

@Composable
fun LiquidKeyPointSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    keyPoints: List<LiquidSliderKeyPoint>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    snapToKeyPoints: Boolean = false,
    snapThreshold: Float? = null,
    activeColor: Color = Color.Unspecified,
    inactiveColor: Color = Color.Unspecified,
    onValueChangeFinished: ((Float) -> Unit)? = null,
    onInteractionChanged: (Boolean) -> Unit = {}
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (activeColor.isSpecified) {
        activeColor
    } else if (isLightTheme) {
        Color(0xFF0088FF)
    } else {
        Color(0xFF5DAEFF)
    }
    val defaultInactiveColor = if (isLightTheme) {
        Color(0xFF1D1D1F).copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    LiquidTrackSlider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        onInteractionChanged = onInteractionChanged,
        keyPoints = keyPoints,
        snapToKeyPoints = snapToKeyPoints,
        snapThreshold = snapThreshold,
        style = LiquidTrackSliderStyle(
            activeColor = accentColor,
            inactiveColor = if (inactiveColor.isSpecified) inactiveColor else defaultInactiveColor,
            keyPointColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            keyPointActiveColor = Color.White.copy(alpha = 0.90f),
            trackHeight = 7.dp,
            thumbWidth = 38.dp,
            thumbHeight = 22.dp,
            pressedScale = 1.42f,
            thumbGlassSurfaceAlpha = thumbGlassSurfaceAlphaFor(isLightTheme),
            thumbRestingHighlightAlpha = thumbRestingHighlightAlphaFor(isLightTheme)
        )
    )
}

@Composable
private fun LiquidTrackSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: ((Float) -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier,
    enabled: Boolean,
    onInteractionChanged: (Boolean) -> Unit,
    style: LiquidTrackSliderStyle,
    keyPoints: List<LiquidSliderKeyPoint> = emptyList(),
    snapToKeyPoints: Boolean = false,
    snapThreshold: Float? = null
) {
    val activeBackdrop = activeGlassBackdrop(backdrop)
    val trackBackdrop =
        if (activeBackdrop != null) {
            rememberLayerBackdrop()
        } else {
            null
        }
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val onValueChangeFinishedState = rememberUpdatedState(onValueChangeFinished)
    val onInteractionChangedState = rememberUpdatedState(onInteractionChanged)
    DisposableEffect(Unit) {
        onDispose {
            onInteractionChangedState.value(false)
        }
    }
    val safeValueRange = valueRange
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .liquidSliderInteractionLock(
                enabled = enabled,
                onInteractionChanged = onInteractionChangedState.value
            )
            .graphicsLayer {
                alpha = if (enabled) 1f else AppInteractiveTokens.disabledContentAlpha
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value(), safeValueRange, steps = 0)
                if (enabled) {
                    setProgress { target ->
                        val next = target.coerceIn(safeValueRange)
                        onValueChangeState.value(next)
                        onValueChangeFinishedState.value?.invoke(next)
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.coerceAtLeast(1)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val rangeSpan = safeValueRange.endInclusive - safeValueRange.start
        val dampedDragAnimation = remember(
            animationScope,
            safeValueRange,
            visibilityThreshold,
            enabled,
            trackWidth,
            isLtr,
            style.pressedScale,
            keyPoints,
            snapToKeyPoints,
            snapThreshold
        ) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value().coerceIn(safeValueRange),
                valueRange = safeValueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = style.pressedScale,
                consumeDragChanges = true,
                onDragStarted = { position ->
                    if (enabled) {
                        val target = resolveSliderTarget(
                            target = sliderValueAt(
                                offset = position,
                                widthPx = trackWidth.toFloat(),
                                valueRange = safeValueRange,
                                isLtr = isLtr
                            ),
                            valueRange = safeValueRange,
                            keyPoints = keyPoints,
                            snapToKeyPoints = snapToKeyPoints,
                            snapThreshold = snapThreshold
                        )
                        snapToValue(target)
                        onValueChangeState.value(target)
                        onInteractionChangedState.value(true)
                    }
                },
                onDragStopped = {
                    onInteractionChangedState.value(false)
                    if (enabled && didDrag) {
                        val next = resolveSliderTarget(
                            target = targetValue,
                            valueRange = safeValueRange,
                            keyPoints = keyPoints,
                            snapToKeyPoints = snapToKeyPoints,
                            snapThreshold = snapThreshold
                        )
                        onValueChangeState.value(next)
                        onValueChangeFinishedState.value?.invoke(next)
                        if (snapToKeyPoints) {
                            animateToValue(next)
                        }
                    }
                    didDrag = false
                },
                onDrag = { _, dragAmount ->
                    if (!enabled || rangeSpan == 0f) return@DampedDragAnimation
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = rangeSpan * (dragAmount.x / trackWidth)
                    val target = if (isLtr) targetValue + delta else targetValue - delta
                    val boundedTarget = target.coerceIn(safeValueRange)
                    snapToValue(boundedTarget)
                    onValueChangeState.value(boundedTarget)
                }
            )
        }
        // P1: read the deferred value() inside a snapshotFlow instead of at composition scope.
        // Reading value() here would recompose the whole slider on every external tick (e.g. the
        // music progress slider advancing during playback). All visual state is driven off the
        // animation in deferred blocks, so this side-effect is the only thing that forced recompose.
        // The > visibilityThreshold guard preserves the two-way-binding protection during drag.
        val valueState = rememberUpdatedState(value)
        val snapshotFlowManager = rememberAppSnapshotFlowManager()
        LaunchedEffect(
            dampedDragAnimation,
            snapshotFlowManager,
            safeValueRange,
            visibilityThreshold
        ) {
            snapshotFlowManager
                .snapshotFlow { valueState.value().coerceIn(safeValueRange) }
                .collectLatest { currentValue ->
                    if (abs(dampedDragAnimation.targetValue - currentValue) > visibilityThreshold) {
                        dampedDragAnimation.updateValue(currentValue)
                    }
                }
        }
        val thumbBackdrop =
            if (activeBackdrop != null && trackBackdrop != null) {
                // Per the Backdrop "Glass Slider" tutorial: the thumb must refract the background
                // AND the track simultaneously, so combine both layers directly. (The previous
                // build wrapped trackBackdrop in a scaled rememberBackdrop, which squashed the
                // refraction at rest and broke the clean magnifying-lens look from the screenshot.)
                rememberCombinedBackdrop(activeBackdrop, trackBackdrop)
            } else {
                null
            }

        Box(
            if (trackBackdrop != null) {
                Modifier.layerBackdrop(trackBackdrop)
            } else {
                Modifier
            }
        ) {
            Box(
                Modifier
                    .appSquircleBackground(style.inactiveColor, 999.dp)
                    .height(style.trackHeight)
                    .fillMaxWidth()
            )
            Box(
                Modifier
                    .appSquircleBackground(style.activeColor, 999.dp)
                    .height(style.trackHeight)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress)
                            .fastRoundToInt()
                            .coerceIn(0, constraints.maxWidth)
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }
        if (enabled) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(maxHeight)
                    .then(dampedDragAnimation.modifier)
                    .pointerInput(
                        safeValueRange,
                        isLtr,
                        trackWidth,
                        keyPoints,
                        snapToKeyPoints,
                        snapThreshold
                    ) {
                        detectTapGestures { position ->
                            val rawTarget = sliderValueAt(
                                offset = position,
                                widthPx = trackWidth.toFloat(),
                                valueRange = safeValueRange,
                                isLtr = isLtr
                            )
                            val target = resolveSliderTarget(
                                target = rawTarget,
                                valueRange = safeValueRange,
                                keyPoints = keyPoints,
                                snapToKeyPoints = snapToKeyPoints,
                                snapThreshold = snapThreshold
                            )
                            dampedDragAnimation.animateToValue(target)
                            onValueChangeState.value(target)
                            onValueChangeFinishedState.value?.invoke(target)
                        }
                    }
            )
        }

        keyPoints.forEach { keyPoint ->
            val progress = valueProgress(keyPoint.value, safeValueRange)
            // P1: derivedStateOf so a keypoint only recomposes when the thumb actually crosses it,
            // instead of every frame the drag animation reads `progress`.
            val isActive by remember(dampedDragAnimation, progress) {
                derivedStateOf { dampedDragAnimation.progress >= progress }
            }
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = (
                                -size.width / 2f + trackWidth * progress
                                ).fastCoerceIn(
                                -size.width / 4f,
                                trackWidth - size.width * 3f / 4f
                            ) *
                                if (isLtr) 1f else -1f
                    }
                    .appSquircleBackground(resolveKeyPointColor(keyPoint, style, isActive), 999.dp)
                    .size(keyPoint.size)
            )
        }

        Box(
            Modifier
                .graphicsLayer { clip = false }
                .graphicsLayer {
                    translationX = (
                            -size.width / 2f + trackWidth * dampedDragAnimation.progress
                            ).fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                            if (isLtr) 1f else -1f
                }
                .then(
                    if (thumbBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = thumbBackdrop,
                            shape = { ContinuousCapsule },
                            // Effect order per the Backdrop API: color filter -> blur -> lens.
                            // vibrancy() boosts saturation of what's refracted (the colored track),
                            // a light blur softens it, and an always-on lens gives the clear
                            // magnifying-glass refraction seen in the tutorial screenshot.
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(UiPerformanceBudget.backdropBlur.toPx() * (0.5f + 0.5f * progress))
                                lens(
                                    style.lensRefractionHeight.toPx(),
                                    style.lensRefractionAmount.toPx(),
                                    chromaticAberration = true,
                                    depthEffect = true
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Ambient.copy(
                                    alpha = lerp(style.thumbRestingHighlightAlpha, 1f, progress)
                                )
                            },
                            shadow = {
                                Shadow(
                                    radius = 4.dp,
                                    color = Color.Black.copy(alpha = 0.08f)
                                )
                            },
                            innerShadow = {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(radius = 4.dp * progress, alpha = progress)
                            },
                            // Press scale only (uniform). The previous velocity-driven squash-stretch
                            // could over-skew the thumb on fast flings and read as a glitch.
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                            },
                            // A barely-there white tint so the glass stays clear (the tutorial thumb
                            // has no opaque fill); just enough to lift contrast over busy backdrops.
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = style.thumbGlassSurfaceAlpha))
                            }
                        )
                    } else {
                        Modifier.appSquircleBackground(
                            Color.White.copy(alpha = SliderThumbFallbackSurfaceAlpha),
                            999.dp
                        )
                    }
                )
                .width(style.thumbWidth)
                .height(style.thumbHeight)
        )
    }
}

private fun sliderValueAt(
    offset: Offset,
    widthPx: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isLtr: Boolean
): Float {
    val fraction = (offset.x / widthPx.coerceAtLeast(1f)).fastCoerceIn(0f, 1f)
    val resolvedFraction = if (isLtr) fraction else 1f - fraction
    return valueRange.start + (valueRange.endInclusive - valueRange.start) * resolvedFraction
}

private fun resolveSliderTarget(
    target: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    keyPoints: List<LiquidSliderKeyPoint>,
    snapToKeyPoints: Boolean,
    snapThreshold: Float?
): Float {
    val bounded = target.coerceIn(valueRange)
    if (!snapToKeyPoints || keyPoints.isEmpty()) {
        return bounded
    }
    val closest = keyPoints
        .minByOrNull { keyPoint -> abs(keyPoint.value.coerceIn(valueRange) - bounded) }
        ?.value
        ?.coerceIn(valueRange)
        ?: return bounded
    if (snapThreshold != null && abs(closest - bounded) > snapThreshold) {
        return bounded
    }
    return closest
}

private fun valueProgress(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>
): Float {
    val span = valueRange.endInclusive - valueRange.start
    if (span == 0f) return 0f
    return ((value - valueRange.start) / span).fastCoerceIn(0f, 1f)
}

@Composable
private fun resolveKeyPointColor(
    keyPoint: LiquidSliderKeyPoint,
    style: LiquidTrackSliderStyle,
    isActive: Boolean
): Color {
    if (keyPoint.color.isSpecified) {
        return keyPoint.color
    }
    return if (isActive) style.keyPointActiveColor else style.keyPointColor
}

@Immutable
private data class LiquidTrackSliderStyle(
    val activeColor: Color,
    val inactiveColor: Color,
    val keyPointColor: Color = Color.White.copy(alpha = 0.74f),
    val keyPointActiveColor: Color = Color.White.copy(alpha = 0.92f),
    val trackHeight: Dp,
    val thumbWidth: Dp,
    val thumbHeight: Dp,
    val pressedScale: Float,
    // Lens refraction for the glass thumb. Mirrors the Backdrop "Glass Slider" tutorial's
    // proportions (height ~0.375x, amount ~0.5x of the thumb height), which keeps refractionHeight
    // safely under the capsule corner radius (thumbHeight / 2) and refractionAmount under the min
    // dimension (thumbHeight) — exceeding either produces corner discontinuities.
    val lensRefractionHeight: Dp = thumbHeight * 0.375f,
    val lensRefractionAmount: Dp = thumbHeight * 0.5f,
    // Theme-aware thumb tuning. In dark mode the black shadow adds almost no definition, so the
    // clear thumb leans on the white surface + highlight; we lift both a touch to keep it crisp.
    val thumbGlassSurfaceAlpha: Float = SliderThumbGlassSurfaceAlpha,
    val thumbRestingHighlightAlpha: Float = SliderThumbRestingHighlightAlpha
)

// Glass thumb (Backdrop available): clear refractive capsule matching the Glass Slider tutorial.
// Light mode keeps the thumb nearly clear; dark mode lifts the white surface + highlight a touch
// because the black drop shadow adds little contrast over dark backgrounds.
private const val SliderThumbGlassSurfaceAlpha = 0.10f
private const val SliderThumbGlassSurfaceAlphaDark = 0.18f
private const val SliderThumbRestingHighlightAlpha = 0.5f
private const val SliderThumbRestingHighlightAlphaDark = 0.7f

// Fallback thumb (glass effects disabled): needs an opaque-ish fill to stay visible without a lens.
private const val SliderThumbFallbackSurfaceAlpha = 0.42f

private fun thumbGlassSurfaceAlphaFor(isLightTheme: Boolean): Float =
    if (isLightTheme) SliderThumbGlassSurfaceAlpha else SliderThumbGlassSurfaceAlphaDark

private fun thumbRestingHighlightAlphaFor(isLightTheme: Boolean): Float =
    if (isLightTheme) SliderThumbRestingHighlightAlpha else SliderThumbRestingHighlightAlphaDark
