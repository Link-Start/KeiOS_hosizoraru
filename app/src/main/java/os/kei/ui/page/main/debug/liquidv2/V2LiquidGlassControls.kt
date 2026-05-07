package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

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
            readabilityProfile = V2LiquidReadabilityProfile.BrightClear,
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
                readabilityProfile = V2LiquidReadabilityProfile.BrightClear,
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
    val isDark = isSystemInDarkTheme()
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
            chromaticAberration = false,
            readabilityProfile = V2LiquidReadabilityProfile.BrightClear,
            backgroundReadability = if (isDark) 0.18f else 0f
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
