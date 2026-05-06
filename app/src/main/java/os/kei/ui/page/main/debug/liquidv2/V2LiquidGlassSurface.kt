package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
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
import os.kei.ui.page.main.widget.motion.appMotionFloatState

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
    val palette = rememberV2LiquidGlassPalette()
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val collectedPressed by resolvedInteractionSource.collectIsPressedAsState()
    val activePressed = (collectedPressed || spec.pressed) && spec.interactive && !spec.disabled
    val pressProgress by appMotionFloatState(
        targetValue = if (activePressed) 1f else 0f,
        durationMillis = spec.motion.pressDurationMs,
        label = "v2_glass_surface_press"
    )
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = resolvedInteractionSource,
            indication = null,
            enabled = !spec.disabled,
            role = spec.semanticsRole ?: Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }
    val layerBlock = remember(spec, pressProgress) {
        v2SurfaceLayerBlock(spec, pressProgress)
    }
    val tint = v2ResolvedTint(
        palette = palette,
        role = spec.role,
        tint = spec.tint,
        selected = spec.selected,
        loading = spec.loading
    )
    val borderColor = v2ResolvedBorderColor(
        spec = spec,
        fallback = if (spec.surfaceColor.isSpecified) {
            Color.White.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.34f)
        },
        pressProgress = pressProgress
    )

    Box(
        modifier = modifier
            .v2SurfaceBounds(spec)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { spec.shape },
                effects = {
                    vibrancy()
                    blur(spec.blur.toPx().coerceIn(0f, 28.dp.toPx()))
                    lens(
                        refractionHeight = spec.lensHeight.toPx().coerceIn(0f, 48.dp.toPx()),
                        refractionAmount = spec.lensAmount.toPx().coerceIn(0f, 56.dp.toPx()),
                        depthEffect = spec.depthEffect,
                        chromaticAberration = spec.chromaticAberration
                    )
                },
                highlight = {
                    Highlight.Default.copy(
                        alpha = if (spec.disabled) {
                            spec.disabledHighlightAlpha
                        } else {
                            spec.highlightAlpha
                        }
                    )
                },
                shadow = {
                    Shadow(
                        radius = spec.shadow.radius,
                        color = Color.Black.copy(
                            alpha = if (spec.disabled) {
                                spec.shadow.disabledAlpha
                            } else {
                                spec.shadow.alpha
                            }
                        )
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = spec.shadow.innerRadius * pressProgress,
                        alpha = pressProgress
                    )
                },
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = tint.alpha * 0.72f))
                    }
                    if (spec.surfaceColor.isSpecified && spec.surfaceColor.alpha > 0f) {
                        drawRect(spec.surfaceColor)
                    }
                    spec.onDrawSurface?.invoke(this)
                }
            )
            .then(
                if (spec.border.width > 0.dp) {
                    Modifier.border(
                        width = spec.border.width,
                        color = borderColor,
                        shape = spec.shape
                    )
                } else {
                    Modifier
                }
            )
            .then(clickableModifier)
            .alpha(if (spec.disabled) 0.54f else spec.contentAlpha)
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content
    )
}

private fun Modifier.v2SurfaceBounds(spec: V2GlassSurfaceSpec): Modifier {
    var next = this
    if (spec.minWidth > 0.dp || spec.minHeight > 0.dp) {
        next = next.defaultMinSize(minWidth = spec.minWidth, minHeight = spec.minHeight)
    }
    if (spec.maxWidth.isSpecified) {
        next = next.widthIn(max = spec.maxWidth)
    }
    if (spec.maxHeight.isSpecified) {
        next = next.heightIn(max = spec.maxHeight)
    }
    return next
}

private fun v2ResolvedTint(
    palette: V2LiquidGlassPalette,
    role: V2GlassRole,
    tint: Color,
    selected: Boolean,
    loading: Boolean
): Color {
    if (tint.isSpecified) return tint
    val alpha = when {
        loading -> 0.20f
        selected -> 0.24f
        else -> 0.16f
    }
    return palette.roleTint(role, alpha)
}

private fun v2ResolvedBorderColor(
    spec: V2GlassSurfaceSpec,
    fallback: Color,
    pressProgress: Float
): Color {
    val base = if (spec.border.color.isSpecified) spec.border.color else fallback
    return base.copy(
        alpha = base.alpha *
                spec.border.alpha *
                lerp(1f, spec.border.pressedAlphaMultiplier, pressProgress)
    )
}

private fun v2SurfaceLayerBlock(
    spec: V2GlassSurfaceSpec,
    pressProgress: Float
): (GraphicsLayerScope.() -> Unit)? {
    val hasDefaultPress = spec.interactive && !spec.disabled
    val customBlock = spec.layerBlock
    if (!hasDefaultPress && customBlock == null) return null
    return {
        if (hasDefaultPress) {
            scaleX = lerp(1f, spec.motion.pressScaleX, pressProgress)
            scaleY = lerp(1f, spec.motion.pressScaleY, pressProgress)
            translationY = -spec.motion.pressLift.toPx() * pressProgress
        }
        customBlock?.invoke(this, pressProgress)
    }
}
