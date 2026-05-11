package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LiquidGlassDropdownContainerRadius = 26.dp
private val LiquidGlassDropdownItemRadius = 18.dp
private val LiquidGlassDropdownMinWidth = 168.dp
private val LiquidGlassDropdownMaxWidth = 280.dp
private val LiquidGlassDropdownMaxHeight = 336.dp
private val LiquidGlassDropdownContentPadding = 8.dp
private val LiquidGlassDropdownItemPressSafePadding =
    AppInteractiveTokens.compactLiquidPressSafePadding
private val LiquidGlassDropdownRowMinHeight = 44.dp
private val LiquidGlassDropdownIconSize = 18.dp
private val LiquidGlassDropdownCheckSize = 18.dp
private val LocalLiquidGlassDropdownSizingPass = staticCompositionLocalOf { false }
private val LocalLiquidGlassDropdownBackdrop = staticCompositionLocalOf<Backdrop?> { null }

enum class LiquidGlassDropdownMaterial {
    Default,
    ActionMenu
}

private data class LiquidGlassDropdownMetrics(
    val containerRadius: Dp,
    val contentPadding: Dp,
    val blurRadius: Dp,
    val lensStart: Dp,
    val lensEnd: Dp,
    val shadowElevation: Dp,
    val innerShadowRadius: Dp,
    val vibrancy: Boolean,
    val chromaticAberration: Boolean,
    val depthEffect: Boolean,
    val lightHighlightAlpha: Float,
    val darkHighlightAlpha: Float,
    val lightOuterShadowAlpha: Float,
    val darkOuterShadowAlpha: Float,
    val lightSpotShadowAlpha: Float,
    val darkSpotShadowAlpha: Float,
    val lightShadowAlpha: Float,
    val darkShadowAlpha: Float,
    val lightInnerShadowAlpha: Float,
    val darkInnerShadowAlpha: Float
)

private fun liquidGlassDropdownMetrics(material: LiquidGlassDropdownMaterial): LiquidGlassDropdownMetrics {
    return when (material) {
        LiquidGlassDropdownMaterial.Default -> LiquidGlassDropdownMetrics(
            containerRadius = LiquidGlassDropdownContainerRadius,
            contentPadding = LiquidGlassDropdownContentPadding,
            blurRadius = 8.dp,
            lensStart = 18.dp,
            lensEnd = 34.dp,
            shadowElevation = 18.dp,
            innerShadowRadius = 10.dp,
            vibrancy = true,
            chromaticAberration = true,
            depthEffect = true,
            lightHighlightAlpha = 0.86f,
            darkHighlightAlpha = 0.72f,
            lightOuterShadowAlpha = 0.12f,
            darkOuterShadowAlpha = 0.22f,
            lightSpotShadowAlpha = 0.10f,
            darkSpotShadowAlpha = 0.18f,
            lightShadowAlpha = 0.14f,
            darkShadowAlpha = 0.22f,
            lightInnerShadowAlpha = 0.12f,
            darkInnerShadowAlpha = 0.20f
        )

        LiquidGlassDropdownMaterial.ActionMenu -> LiquidGlassDropdownMetrics(
            containerRadius = 31.dp,
            contentPadding = 10.dp,
            blurRadius = 28.dp,
            lensStart = 34.dp,
            lensEnd = 64.dp,
            shadowElevation = 28.dp,
            innerShadowRadius = 14.dp,
            vibrancy = false,
            chromaticAberration = true,
            depthEffect = true,
            lightHighlightAlpha = 0.96f,
            darkHighlightAlpha = 0.78f,
            lightOuterShadowAlpha = 0.18f,
            darkOuterShadowAlpha = 0.28f,
            lightSpotShadowAlpha = 0.14f,
            darkSpotShadowAlpha = 0.22f,
            lightShadowAlpha = 0.18f,
            darkShadowAlpha = 0.26f,
            lightInnerShadowAlpha = 0.16f,
            darkInnerShadowAlpha = 0.22f
        )
    }
}

@Composable
fun LiquidGlassDropdownColumn(
    modifier: Modifier = Modifier,
    minWidth: Dp = LiquidGlassDropdownMinWidth,
    maxWidth: Dp = LiquidGlassDropdownMaxWidth,
    maxHeight: Dp = LiquidGlassDropdownMaxHeight,
    initialScrollItemIndex: Int? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    backdrop: Backdrop? = null,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val metrics = liquidGlassDropdownMetrics(material)
    val containerShape = RoundedRectangle(metrics.containerRadius)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val colors = liquidGlassDropdownContainerColors(
        isDark = isDark,
        accentColor = accentColor,
        material = material
    )
    val activeBackdrop = backdrop.takeIf { LocalLiquidControlsEnabled.current }
    LaunchedEffect(initialScrollItemIndex, scrollState.maxValue, density) {
        val itemIndex = initialScrollItemIndex?.coerceAtLeast(0) ?: return@LaunchedEffect
        if (scrollState.maxValue <= 0) return@LaunchedEffect
        val targetOffset = with(density) {
            val rowOffset = LiquidGlassDropdownRowMinHeight.toPx() * itemIndex
            val contextInset = maxHeight.toPx() * 0.34f
            (rowOffset - contextInset).toInt()
        }.coerceIn(0, scrollState.maxValue)
        scrollState.scrollTo(targetOffset)
    }

    Box(
        modifier = modifier
            .widthIn(min = minWidth, max = maxWidth)
            .shadow(
                elevation = metrics.shadowElevation,
                shape = containerShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isDark) metrics.darkOuterShadowAlpha else metrics.lightOuterShadowAlpha),
                spotColor = Color.Black.copy(alpha = if (isDark) metrics.darkSpotShadowAlpha else metrics.lightSpotShadowAlpha)
            )
            .clip(containerShape)
            .border(
                width = 1.dp,
                color = colors.borderColor,
                shape = containerShape
            )
            .then(
                if (activeBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = activeBackdrop,
                        shape = { containerShape },
                        effects = {
                            if (metrics.vibrancy) {
                                vibrancy()
                            }
                            blur(metrics.blurRadius.toPx())
                            lens(
                                metrics.lensStart.toPx(),
                                metrics.lensEnd.toPx(),
                                chromaticAberration = metrics.chromaticAberration,
                                depthEffect = metrics.depthEffect
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = if (isDark) metrics.darkHighlightAlpha else metrics.lightHighlightAlpha)
                        },
                        shadow = {
                            Shadow.Default.copy(color = Color.Black.copy(alpha = if (isDark) metrics.darkShadowAlpha else metrics.lightShadowAlpha))
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = metrics.innerShadowRadius,
                                alpha = if (isDark) metrics.darkInnerShadowAlpha else metrics.lightInnerShadowAlpha
                            )
                        },
                        onDrawSurface = {
                            drawRect(colors.surfaceColor)
                            drawRect(colors.topSheen)
                        }
                    )
                } else {
                    Modifier
                        .background(colors.fallbackBaseColor, containerShape)
                        .background(colors.fallbackMiddleBrush, containerShape)
                        .background(colors.fallbackSheenBrush, containerShape)
                }
            )
    ) {
        CompositionLocalProvider(LocalLiquidGlassDropdownBackdrop provides activeBackdrop) {
            SubcomposeLayout(
                modifier = Modifier
                    .padding(metrics.contentPadding)
                    .heightIn(max = maxHeight)
                    .verticalScroll(scrollState)
            ) { constraints ->
                val minWidthPx = minWidth.roundToPx()
                val maxWidthPx = maxWidth.roundToPx().coerceAtLeast(minWidthPx)
                val contentInsetPx = metrics.contentPadding.roundToPx() * 2
                val minContentWidth = (minWidthPx - contentInsetPx).coerceAtLeast(0)
                val maxContentWidth = (maxWidthPx - contentInsetPx).coerceAtLeast(minContentWidth)
                val probeConstraints = constraints.copy(
                    minWidth = 0,
                    maxWidth = maxContentWidth,
                    minHeight = 0
                )
                val probePlaceables = subcompose("probe") {
                    CompositionLocalProvider(
                        LocalLiquidGlassDropdownSizingPass provides true,
                        content = content
                    )
                }.map { measurable ->
                    measurable.measure(probeConstraints)
                }
                val resolvedWidth = probePlaceables.maxOfOrNull { it.width }
                    ?.coerceIn(minContentWidth, maxContentWidth)
                    ?: minContentWidth
                val contentConstraints = constraints.copy(
                    minWidth = resolvedWidth,
                    maxWidth = resolvedWidth,
                    minHeight = 0
                )
                val placeables = subcompose("content", content).map { measurable ->
                    measurable.measure(contentConstraints)
                }
                val contentHeight = placeables.sumOf { it.height }
                layout(resolvedWidth, contentHeight) {
                    var currentY = 0
                    placeables.forEach { placeable ->
                        placeable.placeRelative(0, currentY)
                        currentY += placeable.height
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassDropdownItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    optionSize: Int = 1,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    subtitle: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    variant: GlassVariant = GlassVariant.SheetAction,
    enabled: Boolean = true,
    highlighted: Boolean = selected,
    showCheck: Boolean = selected,
    highlightContent: Boolean = selected && showCheck,
    textMaxLines: Int = 1
) {
    if (LocalLiquidGlassDropdownSizingPass.current) {
        LiquidGlassDropdownMeasureItem(
            text = text,
            selected = selected,
            modifier = modifier,
            index = index,
            optionSize = optionSize,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            subtitle = subtitle,
            trailingContent = trailingContent,
            accentColor = accentColor,
            variant = variant,
            highlighted = highlighted,
            showCheck = showCheck,
            highlightContent = highlightContent,
            textMaxLines = textMaxLines,
            enabled = enabled
        )
        return
    }

    val isDark = isSystemInDarkTheme()
    val itemBackdrop = LocalLiquidGlassDropdownBackdrop.current
    val itemAccent = liquidGlassDropdownItemAccent(
        isDark = isDark,
        accentColor = accentColor,
        variant = variant
    )
    val contentHighlighted = highlighted && highlightContent
    val textColor = if (contentHighlighted) {
        itemAccent
    } else {
        MiuixTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.96f else 0.92f)
    }.let { color -> if (enabled) color else color.copy(alpha = 0.42f) }
    val iconColor = if (contentHighlighted) {
        itemAccent
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = if (isDark) 0.88f else 0.78f)
    }.let { color -> if (enabled) color else color.copy(alpha = 0.38f) }
    val checkColor = if (enabled) itemAccent else itemAccent.copy(alpha = 0.42f)
    val selectedSurface = liquidGlassDropdownSelectedSurfaceColor(isDark = isDark)
    val currentOnClick by rememberUpdatedState(onClick)
    val rowShape = RoundedRectangle(LiquidGlassDropdownItemRadius)
    val outerTopPadding = if (index == 0) {
        LiquidGlassDropdownItemPressSafePadding
    } else {
        2.dp
    }
    val outerBottomPadding = if (index == optionSize - 1) {
        LiquidGlassDropdownItemPressSafePadding
    } else {
        2.dp
    }

    if (itemBackdrop != null && highlighted) {
        LiquidSurface(
            backdrop = itemBackdrop,
            modifier = modifier
                .padding(top = outerTopPadding, bottom = outerBottomPadding)
                .defaultMinSize(minHeight = LiquidGlassDropdownRowMinHeight),
            enabled = enabled,
            shape = rowShape,
            tint = Color.Unspecified,
            surfaceColor = selectedSurface,
            blurRadius = 3.dp,
            lensRadius = 14.dp,
            chromaticAberration = highlighted,
            depthEffect = true,
            shadow = true,
            onClick = { currentOnClick() }
        ) {
            LiquidGlassDropdownRowContent(
                text = text,
                textColor = textColor,
                iconColor = iconColor,
                checkColor = checkColor,
                showCheck = showCheck,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                subtitle = subtitle,
                trailingContent = trailingContent,
                modifier = liquidGlassDropdownRowContentModifier(),
                textMaxLines = textMaxLines,
                enabled = enabled
            )
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by appMotionFloatState(
            targetValue = if (pressed && enabled) AppInteractiveTokens.pressedScale else 1f,
            durationMillis = 110,
            label = "liquid_glass_dropdown_item_scale"
        )
        val pressedAlpha by appMotionFloatState(
            targetValue = appControlPressedOverlayAlpha(pressed && enabled, isDark),
            durationMillis = 110,
            label = "liquid_glass_dropdown_item_pressed_alpha"
        )
        val showSelectionPill = highlighted || pressed
        val pillSurface = if (highlighted) {
            selectedSurface
        } else {
            liquidGlassDropdownPressedSurfaceColor(isDark = isDark)
        }
        Box(
            modifier = modifier
                .padding(top = outerTopPadding, bottom = outerBottomPadding)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (showSelectionPill) {
                        Modifier.shadow(
                            elevation = 10.dp,
                            shape = rowShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = if (isDark) 0.18f else 0.10f),
                            spotColor = Color.Black.copy(alpha = if (isDark) 0.16f else 0.08f)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(rowShape)
                .background(if (showSelectionPill) pillSurface else Color.Transparent, rowShape)
                .then(
                    if (showSelectionPill) {
                        Modifier.border(
                            width = 1.dp,
                            color = liquidGlassDropdownSelectedBorderColor(isDark = isDark),
                            shape = rowShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = { currentOnClick() }
                )
                .defaultMinSize(minHeight = LiquidGlassDropdownRowMinHeight)
        ) {
            LiquidGlassDropdownRowContent(
                text = text,
                textColor = textColor,
                iconColor = iconColor,
                checkColor = checkColor,
                showCheck = showCheck,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                subtitle = subtitle,
                trailingContent = trailingContent,
                modifier = liquidGlassDropdownRowContentModifier(),
                textMaxLines = textMaxLines,
                enabled = enabled
            )
            if (pressedAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = pressedAlpha),
                            shape = rowShape
                        )
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassDropdownMeasureItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    index: Int = 0,
    optionSize: Int = 1,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    subtitle: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    variant: GlassVariant = GlassVariant.SheetAction,
    highlighted: Boolean = selected,
    showCheck: Boolean = selected,
    highlightContent: Boolean = selected && showCheck,
    textMaxLines: Int = 1,
    enabled: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    val itemAccent = liquidGlassDropdownItemAccent(
        isDark = isDark,
        accentColor = accentColor,
        variant = variant
    )
    val contentHighlighted = highlighted && highlightContent
    val textColor = if (contentHighlighted) {
        itemAccent
    } else {
        MiuixTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.96f else 0.92f)
    }.let { color -> if (enabled) color else color.copy(alpha = 0.42f) }
    val iconColor = if (contentHighlighted) {
        itemAccent
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = if (isDark) 0.88f else 0.78f)
    }.let { color -> if (enabled) color else color.copy(alpha = 0.38f) }
    val checkColor = if (enabled) itemAccent else itemAccent.copy(alpha = 0.42f)
    val outerTopPadding = if (index == 0) 0.dp else 2.dp
    val outerBottomPadding = if (index == optionSize - 1) 0.dp else 2.dp

    Box(
        modifier = modifier
            .padding(top = outerTopPadding, bottom = outerBottomPadding)
            .defaultMinSize(minHeight = LiquidGlassDropdownRowMinHeight)
    ) {
        LiquidGlassDropdownRowContent(
            text = text,
            textColor = textColor,
            iconColor = iconColor,
            checkColor = checkColor,
            showCheck = showCheck,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            subtitle = subtitle,
            trailingContent = trailingContent,
            modifier = liquidGlassDropdownRowContentModifier(),
            textMaxLines = textMaxLines,
            enabled = enabled
        )
    }
}

private fun liquidGlassDropdownRowContentModifier(): Modifier {
    return Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = LiquidGlassDropdownRowMinHeight)
}

@Composable
private fun LiquidGlassDropdownRowContent(
    text: String,
    textColor: Color,
    iconColor: Color,
    checkColor: Color,
    showCheck: Boolean,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    subtitle: String?,
    trailingContent: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    textMaxLines: Int = 1,
    enabled: Boolean = true
) {
    val textTypography = if (textMaxLines == 1 && subtitle == null) {
        AppTypographyTokens.Body
    } else {
        AppTypographyTokens.Supporting
    }
    val subtitleTypography = AppTypographyTokens.Caption
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant
        .copy(alpha = if (enabled) 0.68f else 0.34f)
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(LiquidGlassDropdownIconSize)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = textTypography.fontSize,
                lineHeight = textTypography.lineHeight,
                fontWeight = FontWeight.Medium,
                maxLines = textMaxLines,
                overflow = if (textMaxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = subtitleTypography.fontSize,
                    lineHeight = subtitleTypography.lineHeight,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(LiquidGlassDropdownIconSize)
            )
        }
        trailingContent?.invoke(this)
        if (showCheck) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(LiquidGlassDropdownCheckSize)
            )
        }
    }
}

@Composable
fun LiquidGlassDropdownSingleChoiceItem(
    text: String,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    subtitle: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    variant: GlassVariant = GlassVariant.SheetAction,
    enabled: Boolean = true,
    textMaxLines: Int = 1
) {
    LiquidGlassDropdownItem(
        text = text,
        selected = isSelected,
        onClick = { onSelectedIndexChange(index) },
        modifier = modifier,
        index = index,
        optionSize = optionSize,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        subtitle = subtitle,
        trailingContent = trailingContent,
        accentColor = accentColor,
        variant = variant,
        enabled = enabled,
        highlighted = isSelected,
        showCheck = isSelected,
        highlightContent = isSelected,
        textMaxLines = textMaxLines
    )
}

@Composable
fun LiquidGlassDropdownSingleChoiceList(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    subtitle: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    variant: GlassVariant = GlassVariant.SheetAction,
    enabled: Boolean = true,
    textMaxLines: Int = 1
) {
    options.forEachIndexed { index, option ->
        LiquidGlassDropdownSingleChoiceItem(
            text = option,
            optionSize = options.size,
            isSelected = selectedIndex == index,
            index = index,
            onSelectedIndexChange = onSelectedIndexChange,
            modifier = modifier,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            subtitle = subtitle,
            trailingContent = trailingContent,
            accentColor = accentColor,
            variant = variant,
            enabled = enabled,
            textMaxLines = textMaxLines
        )
    }
}

@Composable
fun AppStandaloneLiquidGlassDropdownColumn(
    modifier: Modifier = Modifier,
    minWidth: Dp = LiquidGlassDropdownMinWidth,
    maxWidth: Dp = LiquidGlassDropdownMaxWidth,
    maxHeight: Dp = LiquidGlassDropdownMaxHeight,
    initialScrollItemIndex: Int? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default,
    content: @Composable () -> Unit
) {
    val localBackdrop = rememberLayerBackdrop()
    AppStandaloneBackdropHost(
        backdrop = localBackdrop,
        modifier = modifier
    ) {
        LiquidGlassDropdownColumn(
            minWidth = minWidth,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            initialScrollItemIndex = initialScrollItemIndex,
            accentColor = accentColor,
            backdrop = localBackdrop,
            material = material,
            content = content
        )
    }
}

@Composable
fun AppLiquidGlassDropdownColumn(
    modifier: Modifier = Modifier,
    minWidth: Dp = LiquidGlassDropdownMinWidth,
    maxWidth: Dp = LiquidGlassDropdownMaxWidth,
    maxHeight: Dp = LiquidGlassDropdownMaxHeight,
    initialScrollItemIndex: Int? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    backdrop: Backdrop? = null,
    material: LiquidGlassDropdownMaterial = LiquidGlassDropdownMaterial.Default,
    content: @Composable () -> Unit
) {
    if (backdrop != null) {
        LiquidGlassDropdownColumn(
            modifier = modifier,
            minWidth = minWidth,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            initialScrollItemIndex = initialScrollItemIndex,
            accentColor = accentColor,
            backdrop = backdrop,
            material = material,
            content = content
        )
    } else {
        AppStandaloneLiquidGlassDropdownColumn(
            modifier = modifier,
            minWidth = minWidth,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            initialScrollItemIndex = initialScrollItemIndex,
            accentColor = accentColor,
            material = material,
            content = content
        )
    }
}

@Composable
fun LiquidGlassDropdownActionItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    optionSize: Int = 1,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    subtitle: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    variant: GlassVariant = GlassVariant.SheetAction,
    enabled: Boolean = true,
    highlighted: Boolean = false
) {
    LiquidGlassDropdownItem(
        text = text,
        selected = false,
        onClick = onClick,
        modifier = modifier,
        index = index,
        optionSize = optionSize,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        subtitle = subtitle,
        trailingContent = trailingContent,
        accentColor = accentColor,
        variant = variant,
        enabled = enabled,
        highlighted = highlighted,
        showCheck = false,
        highlightContent = false
    )
}

private data class LiquidGlassDropdownContainerColors(
    val surfaceColor: Color,
    val topSheen: Color,
    val borderColor: Color,
    val fallbackBaseColor: Color,
    val fallbackMiddleBrush: Brush,
    val fallbackSheenBrush: Brush
)

@Composable
private fun liquidGlassDropdownContainerColors(
    isDark: Boolean,
    accentColor: Color,
    material: LiquidGlassDropdownMaterial
): LiquidGlassDropdownContainerColors {
    return when (material) {
        LiquidGlassDropdownMaterial.ActionMenu -> if (isDark) {
            LiquidGlassDropdownContainerColors(
                surfaceColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                topSheen = Color.White.copy(alpha = 0.14f),
                borderColor = Color.White.copy(alpha = 0.28f),
                fallbackBaseColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                fallbackMiddleBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.26f),
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.08f)
                    ),
                    start = Offset.Zero,
                    end = Offset(360f, 460f)
                ),
                fallbackSheenBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(112f, 20f),
                    radius = 280f
                )
            )
        } else {
            LiquidGlassDropdownContainerColors(
                surfaceColor = Color.White.copy(alpha = 0.94f),
                topSheen = Color.White.copy(alpha = 0.26f),
                borderColor = Color.White.copy(alpha = 0.98f),
                fallbackBaseColor = Color.White.copy(alpha = 0.98f),
                fallbackMiddleBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f),
                        Color(0xFFF5F9FF).copy(alpha = 0.82f),
                        Color.White.copy(alpha = 0.68f)
                    ),
                    start = Offset.Zero,
                    end = Offset(360f, 460f)
                ),
                fallbackSheenBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.88f),
                        Color(0xFFEAF4FF).copy(alpha = 0.36f),
                        Color.Transparent
                    ),
                    center = Offset(112f, 20f),
                    radius = 300f
                )
            )
        }

        LiquidGlassDropdownMaterial.Default -> if (isDark) {
            LiquidGlassDropdownContainerColors(
                surfaceColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f),
                topSheen = Color.White.copy(alpha = 0.16f),
                borderColor = Color.White.copy(alpha = 0.24f),
                fallbackBaseColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                fallbackMiddleBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.10f)
                    ),
                    start = Offset.Zero,
                    end = Offset(320f, 420f)
                ),
                fallbackSheenBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(96f, 24f),
                    radius = 260f
                )
            )
        } else {
            LiquidGlassDropdownContainerColors(
                surfaceColor = Color.White.copy(alpha = 0.68f),
                topSheen = Color.White.copy(alpha = 0.28f),
                borderColor = Color.White.copy(alpha = 0.90f),
                fallbackBaseColor = Color.White.copy(alpha = 0.94f),
                fallbackMiddleBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color(0xFFEAF3FF).copy(alpha = 0.58f),
                        Color.White.copy(alpha = 0.36f)
                    ),
                    start = Offset.Zero,
                    end = Offset(320f, 420f)
                ),
                fallbackSheenBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.78f),
                        Color(0xFFE1EFFF).copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = Offset(96f, 24f),
                    radius = 260f
                )
            )
        }
    }
}

private fun liquidGlassDropdownSelectedSurfaceColor(isDark: Boolean): Color {
    return if (isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color(0xFFEFF4FB).copy(alpha = 0.72f)
    }
}

private fun liquidGlassDropdownPressedSurfaceColor(isDark: Boolean): Color {
    return if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.52f)
    }
}

private fun liquidGlassDropdownSelectedBorderColor(isDark: Boolean): Color {
    return if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
}

private fun liquidGlassDropdownItemAccent(
    isDark: Boolean,
    accentColor: Color,
    variant: GlassVariant
): Color {
    return when (variant) {
        GlassVariant.SheetDangerAction -> Color(0xFFE25B6A)
        else -> if (accentColor == Color.Unspecified) {
            if (isDark) Color(0xFF71ADFF) else Color(0xFF3B82F6)
        } else {
            accentColor
        }
    }
}
