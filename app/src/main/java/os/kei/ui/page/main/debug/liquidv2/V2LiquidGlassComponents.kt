package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
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
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun V2GlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    spec: V2GlassSurfaceSpec = V2GlassSurfaceSpec(),
    interactionSource: MutableInteractionSource? = null,
    exportedBackdrop: LayerBackdrop? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by resolvedInteractionSource.collectIsPressedAsState()
    val pressProgress by appMotionFloatState(
        targetValue = if (pressed && spec.interactive && !spec.disabled) 1f else 0f,
        durationMillis = V2LiquidGlassTokens.pressMotionMs,
        label = "v2_glass_surface_press"
    )
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = resolvedInteractionSource,
            indication = null,
            enabled = !spec.disabled,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }
    val borderColor = if (spec.surfaceColor.isSpecified) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.White.copy(alpha = 0.34f)
    }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { spec.shape },
                effects = {
                    vibrancy()
                    blur(spec.blur.toPx())
                    lens(
                        refractionHeight = spec.lensHeight.toPx(),
                        refractionAmount = spec.lensAmount.toPx(),
                        depthEffect = spec.depthEffect,
                        chromaticAberration = spec.chromaticAberration
                    )
                },
                highlight = {
                    Highlight.Default.copy(alpha = if (spec.disabled) 0.32f else 0.88f)
                },
                shadow = {
                    Shadow(
                        radius = 18.dp,
                        color = Color.Black.copy(alpha = if (spec.disabled) 0.02f else 0.10f)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 7.dp * pressProgress,
                        alpha = pressProgress
                    )
                },
                layerBlock = if (spec.interactive && !spec.disabled) {
                    {
                        val scale = lerp(1f, 1.018f, pressProgress)
                        scaleX = scale
                        scaleY = lerp(1f, 0.992f, pressProgress)
                        translationY = -1.5.dp.toPx() * pressProgress
                    }
                } else {
                    null
                },
                exportedBackdrop = exportedBackdrop,
                onDrawSurface = {
                    if (spec.tint.isSpecified) {
                        drawRect(spec.tint, blendMode = BlendMode.Hue)
                        drawRect(spec.tint.copy(alpha = spec.tint.alpha * 0.72f))
                    }
                    if (spec.surfaceColor.isSpecified && spec.surfaceColor.alpha > 0f) {
                        drawRect(spec.surfaceColor)
                    }
                }
            )
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = borderColor.alpha * (1f - pressProgress * 0.45f)),
                shape = spec.shape
            )
            .then(clickableModifier)
            .alpha(if (spec.disabled) 0.54f else 1f)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
internal fun V2GlassButton(
    text: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.defaultMinSize(minHeight = V2LiquidGlassTokens.controlHeight),
        spec = V2GlassSurfaceSpec.capsule(
            tint = tint,
            surfaceColor = palette.clearTint,
            interactive = true
        ).copy(disabled = !enabled),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.content,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(
                text = text,
                color = palette.content,
                fontSize = AppTypographyTokens.Body.fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.size(V2LiquidGlassTokens.controlHeight),
        spec = V2GlassSurfaceSpec(
            shape = CircleShape,
            tint = tint,
            surfaceColor = palette.clearTint,
            blur = V2LiquidGlassTokens.blurBalanced,
            lensHeight = V2LiquidGlassTokens.lensSoft,
            lensAmount = V2LiquidGlassTokens.lensBalanced,
            interactive = true,
            disabled = !enabled
        ),
        contentAlignment = Alignment.Center,
        onClick = onClick
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.content,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
internal fun V2GlassStatusCapsule(
    label: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(30.dp),
        spec = V2GlassSurfaceSpec.capsule(
            tint = tint,
            surfaceColor = palette.clearTint,
            interactive = false
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
    enabled: Boolean = true
) {
    val palette = rememberV2LiquidGlassPalette()
    val progress by appMotionFloatState(
        targetValue = if (checked) 1f else 0f,
        durationMillis = V2LiquidGlassTokens.stateMotionMs,
        label = "v2_glass_switch"
    )
    val trackTint = if (checked) palette.accent.copy(alpha = 0.35f) else palette.clearTint
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.size(width = 62.dp, height = 36.dp),
        spec = V2GlassSurfaceSpec.capsule(
            tint = trackTint,
            surfaceColor = palette.clearTint,
            interactive = true
        ).copy(disabled = !enabled),
        onClick = { onCheckedChange(!checked) }
    ) {
        V2GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .padding(4.dp)
                .offset {
                    IntOffset(
                        x = (progress * 26.dp.roundToPx()).fastRoundToInt(),
                        y = 0
                    )
                }
                .size(28.dp),
            spec = V2GlassSurfaceSpec(
                shape = CircleShape,
                tint = if (checked) palette.accent.copy(alpha = 0.38f) else Color.Unspecified,
                surfaceColor = Color.White.copy(alpha = 0.34f),
                blur = V2LiquidGlassTokens.blurSoft,
                lensHeight = 12.dp,
                lensAmount = 18.dp,
                interactive = false
            )
        )
    }
}

@Composable
internal fun V2GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true
) {
    val palette = rememberV2LiquidGlassPalette()
    val trackBackdrop = rememberLayerBackdrop()
    val progress = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .fastCoerceIn(0f, 1f)
    var dragging by remember { mutableStateOf(false) }
    val pressProgress by appMotionFloatState(
        targetValue = if (dragging) 1f else 0f,
        durationMillis = V2LiquidGlassTokens.pressMotionMs,
        label = "v2_glass_slider_press"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        fun updateFromX(x: Float) {
            val nextProgress = (x / widthPx).fastCoerceIn(0f, 1f)
            val nextValue =
                valueRange.start + (valueRange.endInclusive - valueRange.start) * nextProgress
            onValueChange(nextValue)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .layerBackdrop(trackBackdrop)
                .background(palette.secondary.copy(alpha = 0.18f), ContinuousCapsule)
                .pointerInput(widthPx, enabled) {
                    if (enabled) {
                        detectTapGestures { updateFromX(it.x) }
                    }
                }
                .pointerInput(widthPx, enabled) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                updateFromX(it.x)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onDrag = { change, _ -> updateFromX(change.position.x) }
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
        }

        val thumbBackdrop = rememberCombinedBackdrop(backdrop, trackBackdrop)
        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (widthPx * progress - 20.dp.toPx()).fastCoerceIn(0f, widthPx - 40.dp.toPx())
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
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.36f)) }
                )
                .size(width = 40.dp, height = 26.dp)
        )
    }
}

@Composable
internal fun V2GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassBottomTabs(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
        backdrop = backdrop,
        modifier = modifier.height(52.dp),
        showIcons = false,
        compact = true,
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
    leadingIcon: ImageVector? = null
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(48.dp),
        spec = V2GlassSurfaceSpec.capsule(
            surfaceColor = palette.clearTint,
            interactive = true
        ),
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
                singleLine = true,
                textStyle = TextStyle(
                    color = palette.content,
                    fontSize = AppTypographyTokens.Body.fontSize
                ),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
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
        }
    }
}

@Composable
internal fun V2GlassDropdown(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = rememberV2LiquidGlassPalette()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        V2GlassButton(
            text = label,
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            tint = palette.accent.copy(alpha = 0.16f),
            onClick = { expanded = !expanded }
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            V2GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth(),
                spec = V2GlassSurfaceSpec(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    surfaceColor = palette.panelTint,
                    blur = V2LiquidGlassTokens.blurBalanced,
                    lensHeight = 18.dp,
                    lensAmount = 28.dp
                ),
                contentPadding = PaddingValues(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEachIndexed { index, item ->
                        V2GlassSurface(
                            backdrop = backdrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            spec = V2GlassSurfaceSpec.capsule(
                                tint = if (index == selectedIndex) palette.accent.copy(alpha = 0.22f) else Color.Unspecified,
                                surfaceColor = if (index == selectedIndex) palette.clearTint else Color.Transparent,
                                interactive = true
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                            onClick = {
                                onSelectedIndexChange(index)
                                expanded = false
                            }
                        ) {
                            Text(
                                text = item,
                                color = palette.content,
                                fontSize = AppTypographyTokens.Body.fontSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun V2GlassBottomTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icons: List<ImageVector> = emptyList(),
    showIcons: Boolean = true,
    compact: Boolean = false,
    activeTint: Color = Color.Unspecified
) {
    val palette = rememberV2LiquidGlassPalette()
    val tabsBackdrop = rememberLayerBackdrop()
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val tabCount = items.size.coerceAtLeast(1)
        val tabWidthPx = widthPx / tabCount
        val selectedOffsetPx by appMotionFloatState(
            targetValue = safeSelected.toFloat(),
            durationMillis = V2LiquidGlassTokens.stateMotionMs,
            label = "v2_glass_tabs_offset"
        )

        V2GlassSurface(
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            spec = V2GlassSurfaceSpec.capsule(
                surfaceColor = palette.clearTint,
                interactive = false
            ).copy(
                blur = V2LiquidGlassTokens.blurBalanced,
                lensHeight = V2LiquidGlassTokens.lensBalanced,
                lensAmount = V2LiquidGlassTokens.lensStrong
            ),
            contentPadding = PaddingValues(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    V2BottomTabContent(
                        label = item,
                        icon = icons.getOrNull(index),
                        color = palette.accent,
                        showIcon = showIcons,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    V2GlassBottomTabHitBox(
                        label = item,
                        icon = icons.getOrNull(index),
                        selected = index == safeSelected,
                        showIcon = showIcons,
                        compact = compact,
                        onClick = { onSelectedIndexChange(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Box(
            Modifier
                .padding(4.dp)
                .graphicsLayer {
                    translationX = selectedOffsetPx * tabWidthPx
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ContinuousCapsule },
                    effects = {
                        blur(V2LiquidGlassTokens.blurSoft.toPx())
                        lens(
                            V2LiquidGlassTokens.lensSoft.toPx(),
                            V2LiquidGlassTokens.lensBalanced.toPx(),
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.92f) },
                    shadow = { Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.10f)) },
                    onDrawSurface = {
                        if (activeTint.isSpecified) {
                            drawRect(activeTint, blendMode = BlendMode.Hue)
                            drawRect(activeTint.copy(alpha = activeTint.alpha * 0.68f))
                        } else {
                            drawRect(palette.accent.copy(alpha = 0.20f))
                        }
                    }
                )
                .height((if (compact) 40 else 54).dp)
                .width(with(LocalDensity.current) { (tabWidthPx - 8.dp.toPx()).toDp() })
                .align(Alignment.CenterStart)
        )
    }
}

@Composable
private fun RowScope.V2GlassBottomTabHitBox(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    showIcon: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        V2BottomTabContent(
            label = label,
            icon = icon,
            color = if (selected) palette.content else palette.secondary,
            showIcon = showIcon,
            compact = compact
        )
    }
}

@Composable
private fun V2BottomTabContent(
    label: String,
    icon: ImageVector?,
    color: Color,
    showIcon: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (compact) 0.dp else 2.dp,
            Alignment.CenterVertically
        )
    ) {
        if (showIcon && icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(if (compact) 15.dp else 17.dp)
            )
        }
        Text(
            text = label,
            color = color,
            fontSize = if (compact) AppTypographyTokens.Supporting.fontSize else AppTypographyTokens.Caption.fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun V2GlassActionBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(58.dp),
        spec = V2GlassSurfaceSpec.capsule(
            surfaceColor = palette.clearTint,
            interactive = false
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
internal fun BoxScope.V2GlassSheet(
    visible: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable (LayerBackdrop) -> Unit
) {
    if (!visible) return
    val palette = rememberV2LiquidGlassPalette()
    val sheetBackdrop = rememberLayerBackdrop()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(16.dp),
        spec = V2GlassSurfaceSpec(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
            surfaceColor = palette.panelTint,
            blur = V2LiquidGlassTokens.blurBalanced,
            lensHeight = V2LiquidGlassTokens.lensBalanced,
            lensAmount = V2LiquidGlassTokens.lensStrong,
            chromaticAberration = true
        ),
        exportedBackdrop = sheetBackdrop,
        contentPadding = PaddingValues(18.dp)
    ) {
        content(sheetBackdrop)
    }
}

@Composable
internal fun BoxScope.V2GlassDialog(
    visible: Boolean,
    backdrop: Backdrop,
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val palette = rememberV2LiquidGlassPalette()
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    )
    V2GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(28.dp)
            .fillMaxWidth(),
        spec = V2GlassSurfaceSpec(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
            surfaceColor = palette.panelTint,
            blur = V2LiquidGlassTokens.blurStrong,
            lensHeight = V2LiquidGlassTokens.lensBalanced,
            lensAmount = V2LiquidGlassTokens.lensStrong
        ),
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                color = palette.content,
                fontSize = AppTypographyTokens.SectionTitle.fontSize,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = palette.secondary,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight
            )
            V2GlassButton(
                text = confirmLabel,
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.End),
                tint = palette.accent.copy(alpha = 0.18f),
                onClick = onDismiss
            )
        }
    }
}

@Stable
internal fun Float.toV2PercentLabel(): String = "${(this * 100f).fastRoundToInt()}%"
