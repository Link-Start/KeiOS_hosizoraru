@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.shape.drawAppSquircleBackground
import os.kei.ui.page.main.widget.shape.drawAppSquircleBorder
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    contentTint: Color? = null,
    variant: GlassVariant = GlassVariant.SheetAction,
    enabled: Boolean = true,
    highlighted: Boolean = selected,
    showCheck: Boolean = selected,
    highlightContent: Boolean = selected && showCheck,
    reserveCheckSlot: Boolean = false,
    textMaxLines: Int = 1,
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
            reserveCheckSlot = reserveCheckSlot,
            textMaxLines = textMaxLines,
            enabled = enabled,
        )
        return
    }

    val isDark = isSystemInDarkTheme()
    val itemBackdrop = LocalLiquidGlassDropdownBackdrop.current
    val material = LocalLiquidGlassDropdownMaterial.current
    val itemAccent =
        liquidGlassDropdownItemAccent(
            isDark = isDark,
            accentColor = accentColor,
            variant = variant,
        )
    val contentHighlighted = highlighted && highlightContent
    val targetTextColor =
        (
            contentTint ?: if (contentHighlighted) {
                itemAccent
            } else {
                MiuixTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.96f else 0.92f)
            }
        ).let { color -> if (enabled) color else color.copy(alpha = 0.42f) }
    val targetIconColor =
        (
            contentTint ?: if (contentHighlighted) {
                itemAccent
            } else {
                MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = if (isDark) 0.88f else 0.78f)
            }
        ).let { color -> if (enabled) color else color.copy(alpha = 0.38f) }
    // Smooth color transitions when selection changes — prevents the abrupt "blink" that a plain
    // ternary produces. Spring keeps it feeling natural and tied to the user's selection gesture.
    val textColorState =
        animateColorAsState(
            targetValue = targetTextColor,
            animationSpec = spring(dampingRatio = 0.92f, stiffness = 500f),
            label = "liquid_glass_dropdown_item_text_color",
        )
    val iconColorState =
        animateColorAsState(
            targetValue = targetIconColor,
            animationSpec = spring(dampingRatio = 0.92f, stiffness = 500f),
            label = "liquid_glass_dropdown_item_icon_color",
        )
    val textColorProvider = remember(textColorState) { ColorProducer { textColorState.value } }
    val iconColorProvider = remember(iconColorState) { ColorProducer { iconColorState.value } }
    val checkColor = if (enabled) itemAccent else itemAccent.copy(alpha = 0.42f)
    val selectedSurface =
        liquidGlassDropdownSelectedSurfaceColor(
            isDark = isDark,
            material = material,
        )
    val currentOnClick by rememberUpdatedState(onClick)
    val rowShape = RoundedRectangle(LiquidGlassDropdownItemRadius)
    val outerTopPadding =
        if (index == 0) {
            LiquidGlassDropdownItemPressSafePadding
        } else {
            2.dp
        }
    val outerBottomPadding =
        if (index == optionSize - 1) {
            LiquidGlassDropdownItemPressSafePadding
        } else {
            2.dp
        }

    if (itemBackdrop != null && highlighted && material != LiquidGlassDropdownMaterial.ActionMenu) {
        LiquidSurface(
            backdrop = itemBackdrop,
            modifier =
                modifier
                    .padding(top = outerTopPadding, bottom = outerBottomPadding)
                    .defaultMinSize(minHeight = LiquidGlassDropdownRowMinHeight),
            enabled = enabled,
            shape = rowShape,
            tint = Color.Unspecified,
            surfaceColor = selectedSurface,
            blurRadius = 3.dp,
            lensRadius = 14.dp,
            effectVariant = GlassVariant.Compact,
            chromaticAberration = highlighted,
            depthEffect = true,
            shadow = true,
            onClick = { currentOnClick() },
        ) {
            LiquidGlassDropdownRowContent(
                text = text,
                textColor = textColorProvider,
                iconColor = iconColorProvider,
                checkColor = checkColor,
                showCheck = showCheck,
                reserveCheckSlot = reserveCheckSlot,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                subtitle = subtitle,
                trailingContent = trailingContent,
                modifier = liquidGlassDropdownRowContentModifier(),
                textMaxLines = textMaxLines,
                enabled = enabled,
            )
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        // Spring-based press feedback feels alive — settles quickly without overshoot,
        // matches the elastic feel of iOS/Liquid Glass interactions.
        val scaleState =
            animateFloatAsState(
                targetValue = if (pressed && enabled) AppInteractiveTokens.pressedScale else 1f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
                label = "liquid_glass_dropdown_item_scale",
            )
        // Pressed overlay alpha: faster spring with mild damping keeps the highlight visually
        // tied to the press gesture (no perceptible lag) while still smoothing out micro-jitter.
        val pressedAlphaState =
            animateFloatAsState(
                targetValue = appControlPressedOverlayAlpha(pressed && enabled, isDark),
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 900f),
                label = "liquid_glass_dropdown_item_pressed_alpha",
            )
        val showHighlightedPill = highlighted && material != LiquidGlassDropdownMaterial.ActionMenu
        val showSelectionPill = showHighlightedPill || pressed
        // Smooth pill alpha transition when selection changes — avoids the sudden flash that
        // a plain boolean produces. Critical for selection feedback in dropdowns.
        val pillAlphaState =
            animateFloatAsState(
                targetValue = if (showSelectionPill) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 600f),
                label = "liquid_glass_dropdown_item_pill_alpha",
            )
        val pillSurface =
            if (showHighlightedPill) {
                selectedSurface
            } else {
                liquidGlassDropdownPressedSurfaceColor(
                    isDark = isDark,
                    material = material,
                )
            }
        val pressedOverlayColor = MiuixTheme.colorScheme.onBackground
        Box(
            modifier =
                modifier
                    .padding(top = outerTopPadding, bottom = outerBottomPadding)
                    .graphicsLayer {
                        val scale = scaleState.value
                        val pillAlpha = pillAlphaState.value
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = (if (material == LiquidGlassDropdownMaterial.ActionMenu) 6.dp else 10.dp).toPx() * pillAlpha
                        shape = rowShape
                        clip = false
                        ambientShadowColor =
                            Color.Black.copy(
                                alpha =
                                    (
                                        if (material == LiquidGlassDropdownMaterial.ActionMenu) {
                                            if (isDark) 0.10f else 0.05f
                                        } else {
                                            if (isDark) 0.18f else 0.10f
                                        }
                                    ) * pillAlpha,
                            )
                        spotShadowColor =
                            Color.Black.copy(
                                alpha =
                                    (
                                        if (material == LiquidGlassDropdownMaterial.ActionMenu) {
                                            if (isDark) 0.08f else 0.04f
                                        } else {
                                            if (isDark) 0.16f else 0.08f
                                        }
                                    ) * pillAlpha,
                            )
                    }.drawAppSquircleBackground(LiquidGlassDropdownItemRadius) {
                        pillSurface.copy(alpha = pillSurface.alpha * pillAlphaState.value)
                    }.drawAppSquircleBorder(
                        width = 1.dp,
                        cornerRadius = LiquidGlassDropdownItemRadius,
                    ) {
                        val borderColor =
                            liquidGlassDropdownSelectedBorderColor(
                                isDark = isDark,
                                material = material,
                            )
                        borderColor.copy(alpha = borderColor.alpha * pillAlphaState.value)
                    }.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = { currentOnClick() },
                    ).defaultMinSize(minHeight = LiquidGlassDropdownRowMinHeight),
        ) {
            LiquidGlassDropdownRowContent(
                text = text,
                textColor = textColorProvider,
                iconColor = iconColorProvider,
                checkColor = checkColor,
                showCheck = showCheck,
                reserveCheckSlot = reserveCheckSlot,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                subtitle = subtitle,
                trailingContent = trailingContent,
                modifier = liquidGlassDropdownRowContentModifier(),
                textMaxLines = textMaxLines,
                enabled = enabled,
            )
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = pressedAlphaState.value
                        }.drawAppSquircleBackground(LiquidGlassDropdownItemRadius) {
                            pressedOverlayColor
                        },
            )
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
    reserveCheckSlot: Boolean = false,
    textMaxLines: Int = 1,
    enabled: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val itemAccent =
        liquidGlassDropdownItemAccent(
            isDark = isDark,
            accentColor = accentColor,
            variant = variant,
        )
    val contentHighlighted = highlighted && highlightContent
    val textColor =
        if (contentHighlighted) {
            itemAccent
        } else {
            MiuixTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.96f else 0.92f)
        }.let { color -> if (enabled) color else color.copy(alpha = 0.42f) }
    val iconColor =
        if (contentHighlighted) {
            itemAccent
        } else {
            MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = if (isDark) 0.88f else 0.78f)
        }.let { color -> if (enabled) color else color.copy(alpha = 0.38f) }
    val checkColor = if (enabled) itemAccent else itemAccent.copy(alpha = 0.42f)
    val outerTopPadding = if (index == 0) 0.dp else 2.dp
    val outerBottomPadding = if (index == optionSize - 1) 0.dp else 2.dp

    Box(
        modifier =
            modifier
                .padding(top = outerTopPadding, bottom = outerBottomPadding)
                .defaultMinSize(minHeight = LiquidGlassDropdownRowMinHeight),
    ) {
        LiquidGlassDropdownRowContent(
            text = text,
            textColor = ColorProducer { textColor },
            iconColor = ColorProducer { iconColor },
            checkColor = checkColor,
            showCheck = showCheck,
            reserveCheckSlot = reserveCheckSlot,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            subtitle = subtitle,
            trailingContent = trailingContent,
            modifier = liquidGlassDropdownRowContentModifier(),
            textMaxLines = textMaxLines,
            enabled = enabled,
        )
    }
}

@Composable
private fun liquidGlassDropdownRowContentModifier(): Modifier {
    val minHeight =
        when (LocalLiquidGlassDropdownMaterial.current) {
            LiquidGlassDropdownMaterial.ActionMenu -> 42.dp
            LiquidGlassDropdownMaterial.Default -> LiquidGlassDropdownRowMinHeight
        }
    return Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = minHeight)
}

@Composable
private fun LiquidGlassDropdownRowContent(
    text: String,
    textColor: ColorProducer,
    iconColor: ColorProducer,
    checkColor: Color,
    showCheck: Boolean,
    reserveCheckSlot: Boolean,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    subtitle: String?,
    trailingContent: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    textMaxLines: Int = 1,
    enabled: Boolean = true,
) {
    val material = LocalLiquidGlassDropdownMaterial.current
    val textTypography =
        when {
            material == LiquidGlassDropdownMaterial.ActionMenu -> AppTypographyTokens.Supporting
            textMaxLines == 1 && subtitle == null -> AppTypographyTokens.Body
            else -> AppTypographyTokens.Supporting
        }
    val subtitleTypography =
        when (material) {
            LiquidGlassDropdownMaterial.ActionMenu -> AppTypographyTokens.Eyebrow
            LiquidGlassDropdownMaterial.Default -> AppTypographyTokens.Caption
        }
    val subtitleColor =
        MiuixTheme.colorScheme.onBackgroundVariant
            .copy(
                alpha =
                    if (enabled) {
                        if (material == LiquidGlassDropdownMaterial.ActionMenu) 0.62f else 0.68f
                    } else {
                        0.34f
                    },
            )
    val actionMenuLeadingCheck =
        material == LiquidGlassDropdownMaterial.ActionMenu &&
            reserveCheckSlot
    val rowHorizontalPadding =
        when {
            material != LiquidGlassDropdownMaterial.ActionMenu -> 12.dp
            actionMenuLeadingCheck -> 23.dp
            else -> 23.dp
        }
    val rowVerticalPadding = if (material == LiquidGlassDropdownMaterial.ActionMenu) 7.dp else 8.dp
    val rowSpacing = if (material == LiquidGlassDropdownMaterial.ActionMenu) 13.dp else 10.dp
    Row(
        modifier =
            modifier.padding(
                horizontal = rowHorizontalPadding,
                vertical = rowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
        if (actionMenuLeadingCheck) {
            // Leading check slot: always reserves space (Box matches the icon size).
            // Fade + scale the icon based on showCheck so selection changes feel smooth.
            val checkAlphaState =
                animateFloatAsState(
                    targetValue = if (showCheck) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f),
                    label = "liquid_glass_dropdown_leading_check_alpha",
                )
            val checkScaleState =
                animateFloatAsState(
                    targetValue = if (showCheck) 1f else 0.6f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
                    label = "liquid_glass_dropdown_leading_check_scale",
                )
            Box(
                modifier = Modifier.size(LiquidGlassDropdownCheckSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = null,
                    tint = checkColor,
                    modifier =
                        Modifier
                            .size(LiquidGlassDropdownCheckSize)
                            .graphicsLayer {
                                alpha = checkAlphaState.value
                                scaleX = checkScaleState.value
                                scaleY = checkScaleState.value
                            },
                )
            }
        } else if (leadingIcon != null) {
            LiquidGlassDropdownIcon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(LiquidGlassDropdownIconSize),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            BasicText(
                text = text,
                color = textColor,
                style =
                    TextStyle(
                        fontSize = textTypography.fontSize,
                        lineHeight = textTypography.lineHeight,
                        fontWeight = FontWeight.Medium,
                    ),
                maxLines = textMaxLines,
                overflow = if (textMaxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = subtitleTypography.fontSize,
                    lineHeight = subtitleTypography.lineHeight,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingIcon != null) {
            LiquidGlassDropdownIcon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(LiquidGlassDropdownIconSize),
            )
        }
        trailingContent?.invoke(this)
        if (!actionMenuLeadingCheck) {
            // Trailing check: AnimatedVisibility with fade + scale gives iOS-style feedback when
            // selection changes. Layout shifts naturally as the icon slot appears/disappears.
            AnimatedVisibility(
                visible = showCheck,
                enter =
                    fadeIn(animationSpec = spring(dampingRatio = 0.85f, stiffness = 700f)) +
                        scaleIn(
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
                            initialScale = 0.6f,
                        ),
                exit =
                    fadeOut(animationSpec = spring(dampingRatio = 0.95f, stiffness = 900f)) +
                        scaleOut(
                            animationSpec = spring(dampingRatio = 0.95f, stiffness = 900f),
                            targetScale = 0.6f,
                        ),
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = null,
                    tint = checkColor,
                    modifier = Modifier.size(LiquidGlassDropdownCheckSize),
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassDropdownIcon(
    imageVector: ImageVector,
    tint: ColorProducer,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = rememberVectorPainter(imageVector),
        tint = tint,
        contentDescription = contentDescription,
        modifier = modifier,
    )
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
    textMaxLines: Int = 1,
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
        reserveCheckSlot = true,
        textMaxLines = textMaxLines,
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
    textMaxLines: Int = 1,
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
            textMaxLines = textMaxLines,
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
    contentTint: Color? = null,
    variant: GlassVariant = GlassVariant.SheetAction,
    enabled: Boolean = true,
    highlighted: Boolean = false,
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
        contentTint = contentTint,
        variant = variant,
        enabled = enabled,
        highlighted = highlighted,
        showCheck = false,
        highlightContent = false,
    )
}
