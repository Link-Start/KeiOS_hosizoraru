@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleBorder
import os.kei.ui.page.main.widget.shape.appSquircleClip
import os.kei.ui.page.main.widget.shape.drawAppSquircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousCapsule,
    enabled: Boolean = true,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    blurRadius: Dp = UiPerformanceBudget.backdropBlur,
    lensRadius: Dp = UiPerformanceBudget.backdropLens,
    effectVariant: GlassVariant? = null,
    chromaticAberration: Boolean = false,
    depthEffect: Boolean = true,
    shadow: Boolean = true,
    shadowAlpha: Float = 0.10f,
    exportedBackdrop: LayerBackdrop? = null,
    interactionSource: MutableInteractionSource? = null,
    consumeDragChanges: Boolean = false,
    clipContent: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight =
        remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                consumeDragChanges = consumeDragChanges,
            )
        }
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val clickableModifier =
        if (onClick != null) {
            Modifier.clickable(
                interactionSource = resolvedInteractionSource,
                indication = if (isInteractive) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    val interactiveLayerBlock: (GraphicsLayerScope.() -> Unit)? =
        if (isInteractive && enabled) {
            { applyLiquidSurfaceInteractiveTransform(interactiveHighlight) }
        } else {
            null
        }
    val activeBackdrop = activeGlassBackdrop(backdrop)
    val effectiveBlurRadius =
        effectVariant?.let { resolvedGlassBlurDp(blurRadius, it) } ?: blurRadius
    val effectiveLensRadius =
        effectVariant?.let { resolvedGlassLensDp(lensRadius, it) } ?: lensRadius
    val optimizedCornerRadius = appLiquidOptimizedCornerRadius(shape)
    val fallbackSurfaceColor =
        when {
            surfaceColor.isSpecified && surfaceColor.alpha > 0f -> surfaceColor
            else -> MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f)
        }
    val interactionModifier =
        if (isInteractive && enabled) {
            Modifier
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
        } else {
            Modifier
        }
    val surfaceModifier =
        if (activeBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = activeBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(effectiveBlurRadius.toPx())
                    lens(
                        effectiveLensRadius.toPx(),
                        effectiveLensRadius.toPx(),
                        chromaticAberration = chromaticAberration,
                        depthEffect = depthEffect,
                    )
                    // Radial refraction from touch point for interactive surfaces
                    if (isInteractive && enabled && interactiveHighlight.pressProgress > 0f) {
                        radialRefraction(
                            centerX = interactiveHighlight.touchPosition.x,
                            centerY = interactiveHighlight.touchPosition.y,
                            radius = effectiveLensRadius.toPx() * 2f,
                            strength = 8f * interactiveHighlight.pressProgress,
                        )
                    }
                },
                highlight = {
                    Highlight.Default.copy(alpha = if (isInteractive && enabled) 1f else 0.82f)
                },
                shadow = {
                    val resolvedShadowAlpha = if (shadow) shadowAlpha.coerceIn(0f, 1f) else 0f
                    if (resolvedShadowAlpha > 0f) {
                        Shadow.Default.copy(color = Color.Black.copy(alpha = resolvedShadowAlpha))
                    } else {
                        Shadow(alpha = 0f)
                    }
                },
                innerShadow = {
                    val progress = if (isInteractive && enabled) interactiveHighlight.pressProgress else 0f
                    InnerShadow(radius = 6.dp * progress, alpha = progress)
                },
                layerBlock = interactiveLayerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = tint.alpha * 0.70f))
                    }
                    if (surfaceColor.isSpecified && surfaceColor.alpha > 0f) {
                        drawRect(surfaceColor)
                    }
                },
            )
        } else {
            Modifier.appLiquidOptimizedSurface(
                shape = shape,
                optimizedCornerRadius = optimizedCornerRadius,
                color = fallbackSurfaceColor,
            )
        }

    if (clipContent) {
        Box(
            modifier =
                modifier
                    .then(surfaceModifier)
                    .then(clickableModifier)
                    .then(interactionModifier)
                    .graphicsLayer {
                        alpha = if (enabled) 1f else AppInteractiveTokens.disabledContentAlpha
                        clip = false
                    },
            contentAlignment = contentAlignment,
            content = content,
        )
    } else {
        Box(
            modifier =
                modifier
                    .then(clickableModifier)
                    .then(interactionModifier)
                    .graphicsLayer {
                        alpha = if (enabled) 1f else AppInteractiveTokens.disabledContentAlpha
                        clip = false
                    },
            contentAlignment = contentAlignment,
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .then(surfaceModifier)
                        .graphicsLayer { clip = false },
            )
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        interactiveLayerBlock?.invoke(this)
                        clip = false
                    },
                contentAlignment = contentAlignment,
                content = content,
            )
        }
    }
}

private fun GraphicsLayerScope.applyLiquidSurfaceInteractiveTransform(interactiveHighlight: InteractiveHighlight) {
    val progress = interactiveHighlight.pressProgress
    val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, progress)
    val maxOffset = size.minDimension
    val offset = interactiveHighlight.offset
    val initialDerivative = 0.05f
    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

    val maxDragScale = 4.dp.toPx() / size.height
    val offsetAngle = atan2(offset.y, offset.x)
    scaleX = scale +
        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
        (size.width / size.height).fastCoerceAtMost(1f)
    scaleY = scale +
        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
        (size.height / size.width).fastCoerceAtMost(1f)
}

@Composable
fun AppLiquidFloatingSurface(
    modifier: Modifier,
    shape: Shape = ContinuousCapsule,
    backdrop: Backdrop? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    clipContent: Boolean = true,
    consumeTouches: Boolean = false,
    pressDurationMillis: Int = 130,
    pressLabel: String = "app_liquid_floating_surface_press",
    pressSafePadding: Dp = Dp.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by resolvedInteractionSource.collectIsPressedAsState()
    val pressProgressState =
        appMotionFloatState(
            targetValue = if (pressed) 1f else 0f,
            durationMillis = pressDurationMillis,
            label = pressLabel,
        )
    val pressProgressProvider = remember(pressProgressState) { { pressProgressState.value } }
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (isDark) 0.20f else 0.40f)
    val overlayColor =
        if (isDark) {
            Color.White.copy(alpha = 0.04f)
        } else {
            Color.White.copy(alpha = 0.08f)
        }
    val borderColor =
        if (isDark) {
            Color.White.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.54f)
        }
    val resolvedPressSafePadding =
        if (pressSafePadding == Dp.Unspecified) {
            if (onClick != null || consumeTouches) {
                AppInteractiveTokens.denseLiquidPressSafePadding
            } else {
                0.dp
            }
        } else {
            pressSafePadding
        }
    val activeBackdrop = activeGlassBackdrop(backdrop)
    val effectBlurRadius = resolvedGlassBlurDp(UiPerformanceBudget.backdropBlur, GlassVariant.Floating)
    val effectLensRadius = resolvedGlassLensDp(UiPerformanceBudget.backdropLens, GlassVariant.Floating)
    val optimizedCornerRadius = appLiquidOptimizedCornerRadius(shape)

    Box(
        modifier =
            modifier
                .padding(resolvedPressSafePadding)
                .graphicsLayer {
                    val pressProgress = pressProgressProvider()
                    translationY = -with(density) { 1.25.dp.toPx() } * pressProgress
                    scaleX = lerp(1f, 1.010f, pressProgress)
                    scaleY = lerp(1f, 0.992f, pressProgress)
                    clip = false
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (activeBackdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = activeBackdrop,
                                shape = { shape },
                                effects = {
                                    vibrancy()
                                    blur(effectBlurRadius.toPx())
                                    val pressProgress = pressProgressProvider()
                                    lens(
                                        (
                                            effectLensRadius *
                                                (0.90f + 0.08f * pressProgress)
                                        ).toPx(),
                                        (
                                            effectLensRadius *
                                                (0.90f + 0.10f * pressProgress)
                                        ).toPx(),
                                    )
                                },
                                highlight = {
                                    val pressProgress = pressProgressProvider()
                                    Highlight.Default.copy(
                                        alpha = (if (isDark) 0.46f else 0.82f) + 0.06f * pressProgress,
                                    )
                                },
                                shadow = {
                                    val pressProgress = pressProgressProvider()
                                    val shadowAlpha = (if (isDark) 0.12f else 0.05f) * (1f - 0.35f * pressProgress)
                                    Shadow.Default.copy(
                                        color = Color.Black.copy(alpha = shadowAlpha),
                                    )
                                },
                                exportedBackdrop = exportedBackdrop,
                                onDrawSurface = { drawRect(surfaceColor) },
                            )
                        } else {
                            Modifier
                                .appLiquidOptimizedSurface(
                                    shape = shape,
                                    optimizedCornerRadius = optimizedCornerRadius,
                                    color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                                )
                        },
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .appLiquidOptimizedBorder(
                        shape = shape,
                        optimizedCornerRadius = optimizedCornerRadius,
                        color = {
                            val pressProgress = pressProgressProvider()
                            borderColor.copy(alpha = borderColor.alpha * (1f - 0.72f * pressProgress))
                        },
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (clipContent) {
                            Modifier.appLiquidOptimizedClip(
                                shape = shape,
                                optimizedCornerRadius = optimizedCornerRadius,
                            )
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (!clipContent) {
                                Modifier.appLiquidOptimizedClip(
                                    shape = shape,
                                    optimizedCornerRadius = optimizedCornerRadius,
                                )
                            } else {
                                Modifier
                            },
                        ).background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        overlayColor,
                                        overlayColor.copy(alpha = overlayColor.alpha * 0.52f),
                                    ),
                            ),
                        ),
            )
            if (consumeTouches && onClick == null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (!clipContent) {
                                    Modifier.appLiquidOptimizedClip(
                                        shape = shape,
                                        optimizedCornerRadius = optimizedCornerRadius,
                                    )
                                } else {
                                    Modifier
                                },
                            ).clickable(
                                interactionSource = resolvedInteractionSource,
                                indication = null,
                                onClick = {},
                            ),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            when {
                                onClick != null -> {
                                    Modifier.clickable(
                                        interactionSource = resolvedInteractionSource,
                                        indication = null,
                                        onClick = onClick,
                                    )
                                }

                                else -> {
                                    Modifier
                                }
                            },
                        ),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

private fun appLiquidOptimizedCornerRadius(shape: Shape): Dp? =
    when (shape) {
        CircleShape, ContinuousCapsule -> 999.dp
        else -> null
    }

@Composable
private fun Modifier.appLiquidOptimizedClip(
    shape: Shape,
    optimizedCornerRadius: Dp?,
): Modifier =
    if (optimizedCornerRadius != null) {
        appSquircleClip(optimizedCornerRadius)
    } else {
        clip(shape)
    }

@Composable
private fun Modifier.appLiquidOptimizedSurface(
    shape: Shape,
    optimizedCornerRadius: Dp?,
    color: Color,
): Modifier =
    if (optimizedCornerRadius != null) {
        appSquircleBackground(color = color, cornerRadius = optimizedCornerRadius)
    } else {
        clip(shape).background(color)
    }

@Composable
private fun Modifier.appLiquidOptimizedBorder(
    shape: Shape,
    optimizedCornerRadius: Dp?,
    color: Color,
): Modifier =
    if (optimizedCornerRadius != null) {
        appSquircleBorder(width = 1.dp, color = color, cornerRadius = optimizedCornerRadius)
    } else {
        border(1.dp, color, shape)
    }

@Composable
private fun Modifier.appLiquidOptimizedBorder(
    shape: Shape,
    optimizedCornerRadius: Dp?,
    color: () -> Color,
): Modifier =
    if (optimizedCornerRadius != null) {
        drawAppSquircleBorder(width = 1.dp, cornerRadius = optimizedCornerRadius, color = color)
    } else {
        appLiquidOptimizedBorder(shape = shape, optimizedCornerRadius = optimizedCornerRadius, color = color())
    }

@Composable
fun LiquidRoundedCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    blurRadius: Dp = UiPerformanceBudget.backdropBlur,
    lensRadius: Dp = UiPerformanceBudget.backdropLens,
    effectVariant: GlassVariant? = GlassVariant.Content,
    chromaticAberration: Boolean = false,
    depthEffect: Boolean = true,
    shadow: Boolean = true,
    shadowAlpha: Float = 0.10f,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidSurface(
        backdrop = backdrop,
        modifier = modifier,
        shape = RoundedRectangle(cornerRadius),
        tint = tint,
        surfaceColor = surfaceColor,
        blurRadius = blurRadius,
        lensRadius = lensRadius,
        effectVariant = effectVariant,
        chromaticAberration = chromaticAberration,
        depthEffect = depthEffect,
        shadow = shadow,
        shadowAlpha = shadowAlpha,
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
