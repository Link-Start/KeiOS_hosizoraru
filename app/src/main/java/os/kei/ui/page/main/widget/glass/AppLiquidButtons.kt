@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleBorder
import os.kei.ui.page.main.widget.shape.drawAppSquircleBackground
import os.kei.ui.page.main.widget.shape.drawAppSquircleBorder
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Shared effects configuration for liquid buttons.
 * Extracted to avoid duplication and ensure consistent visual behavior.
 */
private fun BackdropEffectScope.applyLiquidButtonEffects(
    glass: GlassStyle,
    variant: GlassVariant,
    pressProgress: Float,
) {
    vibrancy()
    blur(
        lerp(
            glass.blur.toPx(),
            (glass.blur * 0.68f).toPx(),
            pressProgress,
        ),
    )
    lens(
        glass.lensStart.toPx() + 3.dp.toPx() * pressProgress,
        glass.lensEnd.toPx() + 5.dp.toPx() * pressProgress,
        chromaticAberration = variant != GlassVariant.Compact,
        depthEffect = true,
    )
}

@Composable
fun AppLiquidIconButton(
    backdrop: Backdrop?,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    shape: Shape = ContinuousCapsule,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    iconTint: Color = MiuixTheme.colorScheme.primary,
    iconModifier: Modifier = Modifier,
    containerColor: Color? = null,
    enabled: Boolean = true,
    onPressedChange: ((Boolean) -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedWidth = if (width == Dp.Unspecified) defaultAppLiquidIconButtonSize(variant) else width
    val resolvedHeight = if (height == Dp.Unspecified) defaultAppLiquidIconButtonSize(variant) else height
    AppLiquidIconButtonContainer(
        backdrop = backdrop,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        width = resolvedWidth,
        height = resolvedHeight,
        shape = shape,
        blurRadius = blurRadius,
        variant = variant,
        isDark = isDark,
        containerColor = containerColor,
        contentTint = iconTint,
        enabled = enabled,
        onPressedChange = onPressedChange,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = iconModifier,
            tint = iconTint,
        )
    }
}

@Composable
fun AppLiquidIconButton(
    backdrop: Backdrop?,
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    shape: Shape = ContinuousCapsule,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    iconTint: Color = Color.Unspecified,
    iconModifier: Modifier = Modifier,
    containerColor: Color? = null,
    enabled: Boolean = true,
    onPressedChange: ((Boolean) -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val resolvedWidth = if (width == Dp.Unspecified) defaultAppLiquidIconButtonSize(variant) else width
    val resolvedHeight = if (height == Dp.Unspecified) defaultAppLiquidIconButtonSize(variant) else height
    AppLiquidIconButtonContainer(
        backdrop = backdrop,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        width = resolvedWidth,
        height = resolvedHeight,
        shape = shape,
        blurRadius = blurRadius,
        variant = variant,
        isDark = isDark,
        containerColor = containerColor,
        contentTint = iconTint,
        enabled = enabled,
        onPressedChange = onPressedChange,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = iconModifier,
            tint = iconTint,
        )
    }
}

@Composable
private fun AppLiquidIconButtonContainer(
    backdrop: Backdrop?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    width: Dp,
    height: Dp,
    shape: Shape,
    blurRadius: Dp?,
    variant: GlassVariant,
    isDark: Boolean,
    containerColor: Color?,
    contentTint: Color,
    enabled: Boolean,
    onPressedChange: ((Boolean) -> Unit)?,
    content: @Composable () -> Unit,
) {
    val activeBackdrop = activeGlassBackdrop(backdrop)
    val fallbackSurface = MiuixTheme.colorScheme.surfaceContainer
    val glass =
        glassStyle(
            isDark = isDark,
            variant = variant,
            blurRadius = blurRadius,
        ).let { baseStyle ->
            val accentSource = containerColor ?: contentTint
            baseStyle.tintWithAccent(
                accentColor = resolveGlassAccentColor(accentSource, isDark),
                isDark = isDark,
            )
        }
    val surfaceOverlayColor =
        resolveDarkCapsuleOverlayColor(
            defaultOverlayColor = glass.overlayColor,
            isDark = isDark,
        )
    val surfaceHighlightAlpha =
        resolveDarkCapsuleHighlightAlpha(
            defaultAlpha = glass.highlightAlpha,
            isDark = isDark,
            variant = variant,
        )
    val resolvedContainerColor =
        sanitizeCapsuleContainerColor(
            containerColor = containerColor,
            isDark = isDark,
        )
    val transparentContainer = containerColor?.alpha == 0f
    val showBorder = glass.showBorder && !transparentContainer && containerColor == null
    val containerOverlay =
        resolvedContainerColor
            ?.takeUnless { transparentContainer }
            ?.copy(alpha = glassContainerOverlayAlpha(variant, isDark))
    val pressedOverlayColor =
        appControlPressedOverlayColor(
            isDark = isDark,
            variant = variant,
            accentColor = resolvedContainerColor ?: Color.Unspecified,
        )
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight =
        remember(animationScope) {
            InteractiveHighlight(animationScope = animationScope)
        }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScaleState =
        appMotionFloatState(
            targetValue = if (isPressed) AppInteractiveTokens.pressedScale else 1f,
            durationMillis = 110,
            label = "glass_icon_button_scale",
        )
    val pressedOverlayAlphaState =
        appMotionFloatState(
            targetValue = appControlPressedOverlayAlpha(isPressed = isPressed, isDark = isDark),
            durationMillis = 110,
            label = "glass_icon_button_overlay",
        )
    val borderAlphaState =
        appMotionFloatState(
            targetValue = if (isPressed) 0f else 1f,
            durationMillis = 110,
            label = "glass_icon_button_border_alpha",
        )
    val animatedScaleProvider = remember(animatedScaleState) { { animatedScaleState.value } }
    val pressedOverlayAlphaProvider = remember(pressedOverlayAlphaState) { { pressedOverlayAlphaState.value } }
    val borderAlphaProvider = remember(borderAlphaState) { { borderAlphaState.value } }
    LaunchedEffect(isPressed, onPressedChange) {
        onPressedChange?.invoke(isPressed)
    }
    DisposableEffect(onPressedChange) {
        onDispose { onPressedChange?.invoke(false) }
    }
    Box(
        modifier =
            modifier
                .width(width)
                .height(height)
                .graphicsLayer {
                    val scale = animatedScaleProvider()
                    scaleX = scale
                    scaleY = scale
                    clip = false
                }.then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                    },
                ).then(
                    if (activeBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = activeBackdrop,
                            shape = { shape },
                            layerBlock =
                                if (enabled) {
                                    { applyLiquidButtonLayer(interactiveHighlight) }
                                } else {
                                    null
                                },
                            effects = {
                                applyLiquidButtonEffects(
                                    glass = glass,
                                    variant = variant,
                                    pressProgress = if (enabled) interactiveHighlight.pressProgress else 0f,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = surfaceHighlightAlpha)
                            },
                            shadow = {
                                Shadow.Default.copy(
                                    color =
                                        Color.Black.copy(
                                            alpha =
                                                appLiquidButtonShadowAlpha(
                                                    baseAlpha = glass.shadowAlpha,
                                                    variant = variant,
                                                    isPressed = isPressed,
                                                ),
                                        ),
                                )
                            },
                            innerShadow = {
                                val progress = if (enabled) interactiveHighlight.pressProgress else 0f
                                InnerShadow(radius = 6.dp * progress, alpha = progress)
                            },
                            onDrawSurface = {
                                if (variant == GlassVariant.Bar) {
                                    drawRect(fallbackSurface.copy(alpha = glass.fallbackAlpha))
                                } else {
                                    drawRect(glass.baseColor)
                                    if (surfaceOverlayColor != Color.Transparent) {
                                        drawRect(surfaceOverlayColor)
                                    }
                                }
                                containerOverlay?.let { drawRect(it) }
                            },
                        )
                    } else {
                        val fallbackColor =
                            when {
                                transparentContainer -> Color.Transparent
                                containerOverlay != null -> containerOverlay
                                else -> fallbackSurface.copy(alpha = glass.fallbackAlpha)
                            }
                        Modifier
                            .appSquircleBackground(fallbackColor, 999.dp)
                    },
                ).then(
                    if (enabled) {
                        Modifier
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    },
                ).graphicsLayer {
                    alpha = if (enabled) 1f else AppInteractiveTokens.disabledContentAlpha
                },
        contentAlignment = Alignment.Center,
    ) {
        if (showBorder) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .drawAppSquircleBorder(
                            width = glass.borderWidth,
                            cornerRadius = 999.dp,
                        ) {
                            glass.borderColor.copy(alpha = glass.borderColor.alpha * borderAlphaProvider())
                        },
            )
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .drawAppSquircleBackground(999.dp) {
                        pressedOverlayColor.copy(alpha = pressedOverlayAlphaProvider())
                    },
        )
        content()
    }
}

@Composable
fun AppLiquidTextButton(
    backdrop: Backdrop?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.primary,
    containerColor: Color? = null,
    leadingIcon: ImageVector? = null,
    iconTint: Color = textColor,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onPressedChange: ((Boolean) -> Unit)? = null,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    minHeight: Dp = defaultAppLiquidTextButtonMinHeight(variant),
    horizontalPadding: Dp = defaultAppLiquidTextButtonHorizontalPadding(variant),
    verticalPadding: Dp = defaultAppLiquidTextButtonVerticalPadding(variant),
    textMaxLines: Int = Int.MAX_VALUE,
    textOverflow: TextOverflow = TextOverflow.Clip,
    textSoftWrap: Boolean = true,
    textSize: TextUnit = AppTypographyTokens.Body.fontSize,
    textLineHeight: TextUnit = AppTypographyTokens.Body.lineHeight,
    textFontWeight: FontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
    pressScaleEnabled: Boolean = true,
    pressOverlayEnabled: Boolean = true,
    consumeDragChangesForInteraction: Boolean = false,
) {
    val liquidControlsEnabled = LocalLiquidControlsEnabled.current
    val activeBackdrop = activeGlassBackdrop(backdrop)
    val isDark = isSystemInDarkTheme()
    val fallbackSurface = MiuixTheme.colorScheme.surfaceContainer
    val longClick = onLongClick
    val glass =
        glassStyle(
            isDark = isDark,
            variant = variant,
            blurRadius = blurRadius,
        ).let { baseStyle ->
            val accentSource = containerColor ?: textColor
            baseStyle.tintWithAccent(
                accentColor = resolveGlassAccentColor(accentSource, isDark),
                isDark = isDark,
            )
        }
    val surfaceOverlayColor =
        resolveDarkCapsuleOverlayColor(
            defaultOverlayColor = glass.overlayColor,
            isDark = isDark,
        )
    val surfaceHighlightAlpha =
        resolveDarkCapsuleHighlightAlpha(
            defaultAlpha = glass.highlightAlpha,
            isDark = isDark,
            variant = variant,
        )
    val resolvedContainerColor =
        sanitizeCapsuleContainerColor(
            containerColor = containerColor,
            isDark = isDark,
        )
    val transparentContainer = containerColor?.alpha == 0f
    val containerOverlay =
        resolvedContainerColor
            ?.takeUnless { transparentContainer }
            ?.copy(alpha = glassContainerOverlayAlpha(variant, isDark))
    val pressedOverlayColor =
        appControlPressedOverlayColor(
            isDark = isDark,
            variant = variant,
            accentColor = resolvedContainerColor ?: textColor,
        )
    val liquidInteractionEnabled = enabled && liquidControlsEnabled && (pressScaleEnabled || pressOverlayEnabled)
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight =
        remember(animationScope, consumeDragChangesForInteraction) {
            InteractiveHighlight(
                animationScope = animationScope,
                consumeDragChanges = consumeDragChangesForInteraction,
            )
        }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScaleState =
        appMotionFloatState(
            targetValue =
                if (enabled && isPressed && pressScaleEnabled) {
                    AppInteractiveTokens.pressedScale
                } else {
                    1f
                },
            durationMillis = 110,
            label = "app_liquid_text_button_scale",
        )
    val pressedOverlayAlphaState =
        appMotionFloatState(
            targetValue =
                appControlPressedOverlayAlpha(
                    isPressed = enabled && isPressed && pressOverlayEnabled,
                    isDark = isDark,
                ),
            durationMillis = 110,
            label = "app_liquid_text_button_overlay",
        )
    val borderAlphaState =
        appMotionFloatState(
            targetValue = if (enabled && isPressed) 0f else 1f,
            durationMillis = 110,
            label = "app_liquid_text_button_border_alpha",
        )
    val animatedScaleProvider = remember(animatedScaleState) { { animatedScaleState.value } }
    val pressedOverlayAlphaProvider = remember(pressedOverlayAlphaState) { { pressedOverlayAlphaState.value } }
    val borderAlphaProvider = remember(borderAlphaState) { { borderAlphaState.value } }
    val borderModifier =
        if (glass.showBorder && containerColor == null) {
            Modifier.drawAppSquircleBorder(
                width = glass.borderWidth,
                cornerRadius = 999.dp,
            ) {
                glass.borderColor.copy(alpha = glass.borderColor.alpha * borderAlphaProvider())
            }
        } else {
            Modifier
        }

    LaunchedEffect(isPressed, onPressedChange) {
        onPressedChange?.invoke(isPressed)
    }
    DisposableEffect(onPressedChange) {
        onDispose { onPressedChange?.invoke(false) }
    }

    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = minHeight)
                .graphicsLayer {
                    val scale = animatedScaleProvider()
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1f else AppInteractiveTokens.disabledContentAlpha
                    clip = false
                }.then(
                    if (longClick != null) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = { if (enabled) onClick() },
                            onLongClick = longClick,
                        )
                    } else {
                        Modifier.clickable(
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    },
                ).then(
                    if (activeBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = activeBackdrop,
                            shape = { ContinuousCapsule },
                            layerBlock =
                                if (liquidInteractionEnabled) {
                                    { applyLiquidButtonLayer(interactiveHighlight) }
                                } else {
                                    null
                                },
                            effects = {
                                applyLiquidButtonEffects(
                                    glass = glass,
                                    variant = variant,
                                    pressProgress =
                                        if (liquidInteractionEnabled) {
                                            interactiveHighlight.pressProgress
                                        } else {
                                            0f
                                        },
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = surfaceHighlightAlpha)
                            },
                            shadow = {
                                Shadow.Default.copy(
                                    color =
                                        Color.Black.copy(
                                            alpha =
                                                appLiquidButtonShadowAlpha(
                                                    baseAlpha = glass.shadowAlpha,
                                                    variant = variant,
                                                    isPressed = isPressed,
                                                ),
                                        ),
                                )
                            },
                            innerShadow = {
                                val progress =
                                    if (liquidInteractionEnabled) interactiveHighlight.pressProgress else 0f
                                InnerShadow(radius = 6.dp * progress, alpha = progress)
                            },
                            onDrawSurface = {
                                if (variant == GlassVariant.Bar) {
                                    drawRect(fallbackSurface.copy(alpha = glass.fallbackAlpha))
                                } else {
                                    drawRect(glass.baseColor)
                                    if (surfaceOverlayColor != Color.Transparent) {
                                        drawRect(surfaceOverlayColor)
                                    }
                                }
                                containerOverlay?.let { drawRect(it) }
                            },
                        )
                    } else {
                        val fallbackColor =
                            when {
                                transparentContainer -> Color.Transparent
                                containerOverlay != null -> containerOverlay
                                else -> fallbackSurface.copy(alpha = glass.fallbackAlpha)
                            }
                        Modifier.appSquircleBackground(fallbackColor, 999.dp)
                    },
                ).then(
                    if (liquidInteractionEnabled) {
                        Modifier
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    },
                ).then(borderModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .drawAppSquircleBackground(999.dp) {
                        pressedOverlayColor.copy(alpha = pressedOverlayAlphaProvider())
                    },
        )
        DisableSelection {
            Row(
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(AppInteractiveTokens.controlContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                    )
                }
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = textColor,
                        fontSize = textSize,
                        lineHeight = textLineHeight,
                        fontWeight = textFontWeight,
                        maxLines = textMaxLines,
                        overflow = textOverflow,
                        softWrap = textSoftWrap,
                    )
                }
            }
        }
    }
}

private fun glassContainerOverlayAlpha(
    variant: GlassVariant,
    isDark: Boolean,
): Float =
    when (variant) {
        GlassVariant.Bar -> 0.34f
        GlassVariant.SheetInput -> 0.20f
        GlassVariant.SheetAction -> if (isDark) 0.24f else 0.34f
        GlassVariant.SheetPrimaryAction -> 0.18f
        GlassVariant.Compact -> if (isDark) 0.22f else 0.28f
        GlassVariant.SheetDangerAction -> 0.18f
        GlassVariant.Floating -> if (isDark) 0.20f else 0.18f
        GlassVariant.SearchField -> if (isDark) 0.18f else 0.22f
        GlassVariant.Content -> if (isDark) 0.26f else 0.32f
    }

private fun appLiquidButtonShadowAlpha(
    baseAlpha: Float,
    variant: GlassVariant,
    isPressed: Boolean,
): Float {
    val variantScale =
        when (variant) {
            GlassVariant.Floating -> 0.42f

            GlassVariant.Compact -> 0.58f

            GlassVariant.SearchField,
            GlassVariant.SheetAction,
            GlassVariant.SheetPrimaryAction,
            GlassVariant.SheetDangerAction,
            GlassVariant.SheetInput,
            -> 0.64f

            GlassVariant.Content -> 0.72f

            GlassVariant.Bar -> 0.84f
        }
    val pressScale = if (isPressed) 0.45f else 1f
    return baseAlpha * variantScale * pressScale
}

private fun resolveDarkCapsuleOverlayColor(
    defaultOverlayColor: Color,
    isDark: Boolean,
): Color {
    if (!isDark) return defaultOverlayColor
    if (defaultOverlayColor == Color.Transparent) return Color.Transparent
    return if (defaultOverlayColor.isNearNeutralWhite()) Color.Transparent else defaultOverlayColor
}

private fun resolveDarkCapsuleHighlightAlpha(
    defaultAlpha: Float,
    isDark: Boolean,
    variant: GlassVariant,
): Float {
    if (!isDark) return defaultAlpha
    val maxAlpha =
        when (variant) {
            GlassVariant.Bar -> 0.40f
            GlassVariant.SheetInput -> 0.42f
            GlassVariant.SheetAction -> 0.44f
            GlassVariant.SheetPrimaryAction -> 0.44f
            GlassVariant.SheetDangerAction -> 0.44f
            GlassVariant.Floating -> 0.46f
            GlassVariant.SearchField -> 0.48f
            GlassVariant.Compact -> 0.36f
            GlassVariant.Content -> 0.44f
        }
    return min(defaultAlpha, maxAlpha)
}

private fun GraphicsLayerScope.applyLiquidButtonLayer(interactiveHighlight: InteractiveHighlight) {
    val progress = interactiveHighlight.pressProgress
    if (progress <= 0f) {
        translationX = 0f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        return
    }

    val liquidScale = lerp(1f, 1f + 3.dp.toPx() / size.height.coerceAtLeast(1f), progress)
    val maxOffset = size.minDimension.coerceAtLeast(1f)
    val offset = interactiveHighlight.offset
    val offsetAngle = atan2(offset.y, offset.x)
    val maxDragScale = 3.dp.toPx() / size.height.coerceAtLeast(1f)

    translationX = maxOffset * tanh(0.045f * offset.x / maxOffset)
    translationY = maxOffset * tanh(0.045f * offset.y / maxOffset)
    scaleX = liquidScale +
        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension.coerceAtLeast(1f)) *
        (size.width / size.height.coerceAtLeast(1f)).fastCoerceAtMost(1f)
    scaleY = liquidScale +
        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension.coerceAtLeast(1f)) *
        (size.height / size.width.coerceAtLeast(1f)).fastCoerceAtMost(1f)
}

private fun sanitizeCapsuleContainerColor(
    containerColor: Color?,
    isDark: Boolean,
): Color? {
    if (containerColor == null) return null
    if (!isDark) return containerColor
    return if (containerColor.isNearNeutralWhite()) null else containerColor
}

private fun Color.isNearNeutralWhite(): Boolean {
    val maxChannel = maxOf(red, green, blue)
    val minChannel = minOf(red, green, blue)
    val chroma = maxChannel - minChannel
    return luminance() >= 0.88f && chroma <= 0.08f
}
