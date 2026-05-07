package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
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
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun V2GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    role: V2GlassRole = V2GlassRole.Accent,
    trackSpec: V2GlassSurfaceSpec? = null,
    thumbSpec: V2GlassSurfaceSpec? = null,
    checkedIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource? = null,
    onDragStateChange: (Boolean) -> Unit = {}
) {
    val palette = rememberV2LiquidGlassPalette()
    val trackBackdrop = rememberLayerBackdrop()
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(if (checked) 1f else 0f) }
    val animatedProgress by appMotionFloatState(
        targetValue = if (checked) 1f else 0f,
        durationMillis = V2LiquidGlassTokens.stateMotionMs,
        label = "v2_glass_switch"
    )
    val dragMorph by appMotionFloatState(
        targetValue = if (dragging) 1f else 0f,
        durationMillis = V2LiquidGlassTokens.pressMotionMs,
        label = "v2_glass_switch_drag"
    )
    val progress = if (dragging) dragProgress else animatedProgress
    val trackTint = if (checked) palette.success.copy(alpha = 0.58f) else palette.clearTint
    val canChange = enabled && !readOnly
    val resolvedTrackSpec = trackSpec ?: V2GlassSurfaceSpec.capsule(
        tint = trackTint,
        surfaceColor = palette.clearTint,
        interactive = true,
        role = role
    ).copy(
        materialStyle = V2LiquidMaterialStyle.Tinted,
        parameters = V2LiquidParameterSet.controlRegular,
        rimLightAlpha = 0.34f,
        edgeChromaticAlpha = 0.14f,
        causticAlpha = 0.10f,
        disabled = !enabled,
        readabilityProfile = V2LiquidReadabilityProfile.BrightClear,
        semanticsRole = Role.Switch
    )
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .size(
                width = V2LiquidGlassTokens.switchWidth,
                height = V2LiquidGlassTokens.switchHeight
            )
            .pointerInput(canChange) {
                if (canChange) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            dragProgress = progress
                            onDragStateChange(true)
                        },
                        onDragEnd = {
                            dragging = false
                            onDragStateChange(false)
                            onCheckedChange(dragProgress >= 0.5f)
                        },
                        onDragCancel = {
                            dragging = false
                            onDragStateChange(false)
                        },
                        onDrag = { change, _ ->
                            dragProgress =
                                (change.position.x / size.width.toFloat()).fastCoerceIn(0f, 1f)
                        }
                    )
                }
            },
        spec = resolvedTrackSpec,
        interactionSource = interactionSource,
        onClick = { if (canChange) onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .layerBackdrop(trackBackdrop)
                .background(trackTint, ContinuousCapsule)
        )
        val thumbBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
        V2GlassSurface(
            backdrop = thumbBackdrop,
            modifier = Modifier
                .padding(4.dp)
                .offset {
                    IntOffset(
                        x = (progress * 26.dp.roundToPx()).fastRoundToInt(),
                        y = 0
                    )
                }
                .size(width = 28.dp + 6.dp * dragMorph, height = 28.dp),
            spec = (thumbSpec ?: V2GlassSurfaceSpec(
                shape = ContinuousCapsule,
                materialStyle = V2LiquidMaterialStyle.ControlThumb,
                parameters = V2LiquidParameterSet.thumbLens,
                tint = if (checked) palette.success.copy(alpha = 0.18f) else Color.Unspecified,
                surfaceColor = Color.White.copy(alpha = 0.28f),
                blur = V2LiquidGlassTokens.blurSoft,
                lensHeight = 12.dp,
                lensAmount = 18.dp,
                interactive = false,
                rimLightAlpha = 0.36f,
                edgeChromaticAlpha = 0.16f,
                causticAlpha = 0.12f,
                readabilityProfile = V2LiquidReadabilityProfile.BrightClear,
                shapeMorph = 1f,
                gestureTransform = { press ->
                    scaleX = lerp(1f, 1.08f, dragMorph + press).coerceAtMost(1.12f)
                    scaleY = lerp(1f, 0.96f, dragMorph)
                }
            )).copy(disabled = !enabled),
            contentAlignment = Alignment.Center
        ) {
            if (checkedIcon != null && checked) {
                Icon(
                    imageVector = checkedIcon,
                    contentDescription = null,
                    tint = palette.content,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
internal fun V2GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    compact: Boolean = false,
    showTicks: Boolean = false,
    valueLabel: ((Float) -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    onDragStateChange: (Boolean) -> Unit = {},
    trackSpec: V2GlassSurfaceSpec? = null,
    thumbSpec: V2GlassSurfaceSpec? = null
) {
    val palette = rememberV2LiquidGlassPalette()
    val trackBackdrop = rememberLayerBackdrop()
    val coercedValue = value.snapToSliderRange(valueRange, steps)
    val progress = coercedValue.toSliderProgress(valueRange)
    var dragging by remember { mutableStateOf(false) }
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val currentOnDragStateChange by rememberUpdatedState(onDragStateChange)
    val pressProgress by appMotionFloatState(
        targetValue = if (dragging) 1f else 0f,
        durationMillis = V2LiquidGlassTokens.pressMotionMs,
        label = "v2_glass_slider_press"
    )
    val sliderHeight =
        if (compact) V2LiquidGlassTokens.sliderCompactHeight else V2LiquidGlassTokens.sliderHeight
    val thumbWidth = if (compact) 34.dp else 40.dp
    val thumbHeight = if (compact) 22.dp else 26.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(sliderHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        fun updateFromX(x: Float, finish: Boolean) {
            val nextProgress = (x / widthPx).fastCoerceIn(0f, 1f)
            val nextValue =
                (valueRange.start + (valueRange.endInclusive - valueRange.start) * nextProgress)
                    .snapToSliderRange(valueRange, steps)
            onValueChange(nextValue)
            if (finish) currentOnFinished?.invoke()
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 8.dp else 10.dp)
                .layerBackdrop(trackBackdrop)
                .background(palette.secondary.copy(alpha = 0.18f), ContinuousCapsule)
                .pointerInput(widthPx, enabled, steps) {
                    if (enabled) {
                        detectTapGestures { updateFromX(it.x, finish = true) }
                    }
                }
                .pointerInput(widthPx, enabled, steps) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                currentOnDragStateChange(true)
                                updateFromX(it.x, finish = false)
                            },
                            onDragEnd = {
                                dragging = false
                                currentOnDragStateChange(false)
                                currentOnFinished?.invoke()
                            },
                            onDragCancel = {
                                dragging = false
                                currentOnDragStateChange(false)
                            },
                            onDrag = { change, _ -> updateFromX(change.position.x, finish = false) }
                        )
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(palette.accent.copy(alpha = 0.76f), ContinuousCapsule)
            )
            if (showTicks && steps > 0) {
                V2SliderTicks(steps = steps, color = palette.content.copy(alpha = 0.45f))
            }
        }

        val thumbBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (widthPx * progress - thumbWidth.toPx() / 2f)
                            .fastCoerceIn(0f, widthPx - thumbWidth.toPx())
                }
                .drawBackdrop(
                    backdrop = thumbBackdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        blur(
                            V2LiquidGlassTokens.blurBalanced.toPx() *
                                    (1f - 0.35f * pressProgress)
                        )
                        lens(
                            V2LiquidGlassTokens.lensSoft.toPx() *
                                    (0.45f + 0.55f * pressProgress),
                            V2LiquidGlassTokens.lensBalanced.toPx(),
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Ambient.copy(alpha = 0.70f + 0.30f * pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 6.dp * pressProgress,
                            alpha = pressProgress
                        )
                    },
                    layerBlock = {
                        scaleX = lerp(1f, 1.18f, pressProgress)
                        scaleY = lerp(1f, 0.92f, pressProgress)
                    },
                    onDrawSurface = {
                        val customThumbSpec = thumbSpec
                        if (customThumbSpec?.tint?.isSpecified == true) {
                            drawRect(customThumbSpec.tint, blendMode = BlendMode.Hue)
                        }
                        drawRect(
                            if (customThumbSpec?.surfaceColor?.isSpecified == true) {
                                customThumbSpec.surfaceColor
                            } else {
                                Color.White.copy(alpha = 0.36f)
                            }
                        )
                    }
                )
                .size(width = thumbWidth, height = thumbHeight)
        )
        if (valueLabel != null && dragging) {
            Text(
                text = valueLabel(coercedValue),
                color = palette.content,
                fontSize = AppTypographyTokens.Eyebrow.fontSize,
                modifier = Modifier
                    .graphicsLayer {
                        translationX =
                            (widthPx * progress - 20.dp.toPx()).fastCoerceIn(
                                0f,
                                widthPx - 40.dp.toPx()
                            )
                        translationY = -24.dp.toPx()
                    }
            )
        }
    }
}

@Composable
internal fun V2GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: V2GlassControlSize = V2GlassControlSize.Regular,
    selectionStyle: V2GlassSelectionStyle = V2GlassSelectionStyle.Indicator
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassBottomTabs(
        items = items.mapIndexed { index, label ->
            V2GlassTabItem(
                label = label,
                enabled = enabled,
                selectedTint = palette.accent.copy(alpha = if (index == selectedIndex) 0.30f else 0.18f)
            )
        },
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
        backdrop = backdrop,
        modifier = modifier.height(if (size == V2GlassControlSize.Compact) 46.dp else 52.dp),
        labelPolicy = V2GlassTabLabelPolicy.Always,
        showIcons = false,
        compact = true,
        selectionStyle = selectionStyle,
        activeTint = palette.accent.copy(alpha = 0.30f),
        spec = V2LiquidDockSpec(
            height = if (size == V2GlassControlSize.Compact) 46.dp else 52.dp,
            itemMinWidth = 52.dp,
            outerPadding = 4.dp,
            indicatorInset = 4.dp,
            selectedBlobStyle = V2LiquidMaterialStyle.ControlThumb
        )
    )
}

@Composable
private fun V2SliderTicks(
    steps: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(steps + 2) {
            Box(
                Modifier
                    .size(3.dp)
                    .background(color, ContinuousCapsule)
            )
        }
    }
}

private fun Float.toSliderProgress(valueRange: ClosedFloatingPointRange<Float>): Float {
    val range = valueRange.endInclusive - valueRange.start
    if (range <= 0f) return 0f
    return ((this - valueRange.start) / range).fastCoerceIn(0f, 1f)
}

private fun Float.snapToSliderRange(
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val coerced = coerceIn(valueRange.start, valueRange.endInclusive)
    if (steps <= 0) return coerced
    val intervals = steps + 1
    val progress = coerced.toSliderProgress(valueRange)
    val snappedProgress = (progress * intervals).fastRoundToInt() / intervals.toFloat()
    return valueRange.start + (valueRange.endInclusive - valueRange.start) * snappedProgress
}
