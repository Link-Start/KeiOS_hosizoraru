package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Immutable
internal data class V2GlassDropdownItem(
    val label: String,
    val enabled: Boolean = true,
    val leadingIcon: ImageVector? = null,
    val trailingText: String? = null,
    val contentDescription: String? = null,
    val tint: Color = Color.Unspecified
)

@Composable
internal fun V2GlassButton(
    text: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingIcon: ImageVector? = icon,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    loading: Boolean = false,
    role: V2GlassRole = V2GlassRole.Neutral,
    size: V2GlassControlSize = V2GlassControlSize.Regular,
    density: V2GlassContentDensity = V2GlassContentDensity.Comfortable,
    fill: Boolean = false,
    tint: Color = Color.Unspecified,
    textStyle: TextStyle? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val palette = rememberV2LiquidGlassPalette()
    val height = size.controlHeight()
    val resolvedTint =
        if (tint.isSpecified) tint else palette.roleTint(role, if (selected) 0.24f else 0.16f)
    val contentTextStyle = textStyle ?: TextStyle(
        color = palette.content,
        fontSize = size.textSize(),
        fontWeight = FontWeight.SemiBold
    )
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .height(height)
            .then(if (fill) Modifier.fillMaxWidth() else Modifier),
        spec = V2GlassSurfaceSpec.capsule(
            tint = resolvedTint,
            surfaceColor = palette.clearTint,
            interactive = true,
            role = role,
            size = size,
            density = density
        ).copy(
            selected = selected,
            loading = loading,
            disabled = !enabled,
            semanticsRole = Role.Button
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = density.horizontalPadding(), vertical = 0.dp),
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        Row(
            modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                V2LoadingGlyph(color = palette.content, modifier = Modifier.size(size.iconSize()))
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = palette.content,
                    modifier = Modifier.size(size.iconSize())
                )
            }
            if (content != null) {
                content()
            } else {
                Text(
                    text = text,
                    color = contentTextStyle.color,
                    fontSize = contentTextStyle.fontSize,
                    fontWeight = contentTextStyle.fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (fill && trailingIcon != null) Modifier.weight(1f) else Modifier
                )
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = palette.content,
                    modifier = Modifier.size(size.iconSize())
                )
            }
        }
    }
}

@Composable
internal fun V2GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified,
    enabled: Boolean = true,
    selected: Boolean = false,
    loading: Boolean = false,
    badge: String? = null,
    role: V2GlassRole = V2GlassRole.Neutral,
    size: V2GlassControlSize = V2GlassControlSize.Regular,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    val buttonSize = size.controlHeight()
    val resolvedTint =
        if (tint.isSpecified) tint else palette.roleTint(role, if (selected) 0.22f else 0.14f)
    Box(modifier = modifier.size(buttonSize)) {
        V2GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.matchParentSize(),
            spec = V2GlassSurfaceSpec(
                shape = ContinuousCapsule,
                role = role,
                tint = resolvedTint,
                surfaceColor = palette.clearTint,
                blur = V2LiquidGlassTokens.blurBalanced,
                lensHeight = V2LiquidGlassTokens.lensSoft,
                lensAmount = V2LiquidGlassTokens.lensBalanced,
                selected = selected,
                loading = loading,
                interactive = true,
                disabled = !enabled,
                semanticsRole = Role.Button
            ),
            interactionSource = interactionSource,
            contentAlignment = Alignment.Center,
            onClick = onClick
        ) {
            if (loading) {
                V2LoadingGlyph(color = palette.content, modifier = Modifier.size(size.iconSize()))
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (iconTint.isSpecified) iconTint else palette.content,
                    modifier = Modifier.size(size.iconSize() + 2.dp)
                )
            }
        }
        if (!badge.isNullOrBlank()) {
            Text(
                text = badge,
                color = Color.White,
                fontSize = AppTypographyTokens.Eyebrow.fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(palette.danger, ContinuousCapsule)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
internal fun V2GlassStatusCapsule(
    label: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    role: V2GlassRole = V2GlassRole.Neutral,
    size: V2GlassControlSize = V2GlassControlSize.Compact
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(if (size == V2GlassControlSize.Compact) 30.dp else 34.dp),
        spec = V2GlassSurfaceSpec.capsule(
            tint = tint,
            surfaceColor = palette.clearTint,
            interactive = false,
            role = role,
            size = size
        ).copy(
            blur = V2LiquidGlassTokens.blurSoft,
            lensHeight = 12.dp,
            lensAmount = 16.dp,
            chromaticAberration = false
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = palette.content,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
    val progress by appMotionFloatState(
        targetValue = if (checked) 1f else 0f,
        durationMillis = V2LiquidGlassTokens.stateMotionMs,
        label = "v2_glass_switch"
    )
    val trackTint = if (checked) {
        palette.roleTint(role, 0.35f)
    } else {
        palette.clearTint
    }
    val canChange = enabled && !readOnly
    val resolvedTrackSpec = trackSpec ?: V2GlassSurfaceSpec.capsule(
        tint = trackTint,
        surfaceColor = palette.clearTint,
        interactive = true,
        role = role
    ).copy(disabled = !enabled, semanticsRole = Role.Switch)
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.size(
            width = V2LiquidGlassTokens.switchWidth,
            height = V2LiquidGlassTokens.switchHeight
        ),
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
                .size(28.dp),
            spec = (thumbSpec ?: V2GlassSurfaceSpec(
                shape = ContinuousCapsule,
                tint = if (checked) palette.roleTint(role, 0.38f) else Color.Unspecified,
                surfaceColor = Color.White.copy(alpha = 0.34f),
                blur = V2LiquidGlassTokens.blurSoft,
                lensHeight = 12.dp,
                lensAmount = 18.dp,
                interactive = false
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
                        blur(V2LiquidGlassTokens.blurBalanced.toPx() * (1f - 0.35f * pressProgress))
                        lens(
                            V2LiquidGlassTokens.lensSoft.toPx() * (0.45f + 0.55f * pressProgress),
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
        activeTint = palette.accent.copy(alpha = 0.30f)
    )
}

@Composable
internal fun V2GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    showClearAction: Boolean = true,
    clearContentDescription: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    V2GlassSearchField(
        value = TextFieldValue(value),
        onValueChange = { onValueChange(it.text) },
        placeholder = placeholder,
        backdrop = backdrop,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        onTrailingClick = onTrailingClick,
        showClearAction = showClearAction,
        clearContentDescription = clearContentDescription,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}

@Composable
internal fun V2GlassSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    showClearAction: Boolean = true,
    clearContentDescription: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(48.dp),
        spec = V2GlassSurfaceSpec.capsule(
            surfaceColor = palette.clearTint,
            interactive = enabled,
            role = V2GlassRole.Neutral
        ).copy(disabled = !enabled),
        contentPadding = PaddingValues(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = palette.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                textStyle = TextStyle(
                    color = palette.content,
                    fontSize = AppTypographyTokens.Body.fontSize
                ),
                decorationBox = { innerTextField ->
                    if (value.text.isBlank()) {
                        Text(
                            text = placeholder,
                            color = palette.secondary,
                            fontSize = AppTypographyTokens.Body.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            )
            val clearVisible = showClearAction && value.text.isNotBlank() && enabled && !readOnly
            val actionIcon = if (clearVisible) appLucideCloseIcon() else trailingIcon
            val action = if (clearVisible) {
                { onValueChange(TextFieldValue("")) }
            } else {
                onTrailingClick
            }
            if (actionIcon != null && action != null) {
                V2GlassIconButton(
                    icon = actionIcon,
                    contentDescription = clearContentDescription ?: "",
                    backdrop = backdrop,
                    size = V2GlassControlSize.Compact,
                    tint = palette.clearTint,
                    onClick = action
                )
            }
        }
    }
}

@Composable
internal fun V2GlassDropdown(
    label: String,
    items: List<V2GlassDropdownItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxMenuHeight: Dp = 260.dp,
    dismissOnSelect: Boolean = true,
    onDismiss: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = rememberV2LiquidGlassPalette()
    val menuBackdrop = rememberLayerBackdrop()
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        V2GlassButton(
            text = if (items.isEmpty()) label else "$label · ${items[safeIndex].label}",
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = null,
            tint = palette.accent.copy(alpha = 0.16f),
            enabled = enabled,
            onClick = { expanded = !expanded }
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            V2GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxMenuHeight),
                spec = V2GlassSurfaceSpec(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    surfaceColor = palette.panelTint,
                    blur = V2LiquidGlassTokens.blurBalanced,
                    lensHeight = 18.dp,
                    lensAmount = 28.dp
                ),
                exportedBackdrop = menuBackdrop,
                contentPadding = PaddingValues(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEachIndexed { index, item ->
                        val selected = index == safeIndex
                        V2GlassSurface(
                            backdrop = menuBackdrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            spec = V2GlassSurfaceSpec.capsule(
                                tint = when {
                                    item.tint.isSpecified -> item.tint
                                    selected -> palette.accent.copy(alpha = 0.22f)
                                    else -> Color.Unspecified
                                },
                                surfaceColor = if (selected) palette.clearTint else Color.Transparent,
                                interactive = item.enabled,
                                role = V2GlassRole.Accent
                            ).copy(
                                selected = selected,
                                disabled = !item.enabled
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                            onClick = {
                                if (item.enabled) {
                                    onSelectedIndexChange(index)
                                    if (dismissOnSelect) {
                                        expanded = false
                                        onDismiss()
                                    }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.leadingIcon != null) {
                                    Icon(
                                        imageVector = item.leadingIcon,
                                        contentDescription = item.contentDescription,
                                        tint = palette.content,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = item.label,
                                    color = palette.content,
                                    fontSize = AppTypographyTokens.Body.fontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.trailingText != null) {
                                    Text(
                                        text = item.trailingText,
                                        color = palette.secondary,
                                        fontSize = AppTypographyTokens.Supporting.fontSize,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Stable
internal fun Float.toV2PercentLabel(): String = "${(this * 100f).fastRoundToInt()}%"

@Composable
private fun V2LoadingGlyph(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.14f
        drawArc(
            color = color.copy(alpha = 0.78f),
            startAngle = -70f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(strokeWidth, strokeWidth),
            size = Size(size.width - strokeWidth * 2f, size.height - strokeWidth * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun V2SliderTicks(
    steps: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
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

private fun V2GlassControlSize.controlHeight(): Dp {
    return when (this) {
        V2GlassControlSize.Compact -> 38.dp
        V2GlassControlSize.Regular -> V2LiquidGlassTokens.controlHeight
        V2GlassControlSize.Large -> 54.dp
    }
}

private fun V2GlassControlSize.iconSize(): Dp {
    return when (this) {
        V2GlassControlSize.Compact -> 15.dp
        V2GlassControlSize.Regular -> 17.dp
        V2GlassControlSize.Large -> 20.dp
    }
}

private fun V2GlassControlSize.textSize() = when (this) {
    V2GlassControlSize.Compact -> AppTypographyTokens.Supporting.fontSize
    V2GlassControlSize.Regular -> AppTypographyTokens.Body.fontSize
    V2GlassControlSize.Large -> AppTypographyTokens.BodyEmphasis.fontSize
}

private fun V2GlassContentDensity.horizontalPadding(): Dp {
    return when (this) {
        V2GlassContentDensity.Compact -> 12.dp
        V2GlassContentDensity.Comfortable -> 16.dp
        V2GlassContentDensity.Spacious -> 20.dp
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
