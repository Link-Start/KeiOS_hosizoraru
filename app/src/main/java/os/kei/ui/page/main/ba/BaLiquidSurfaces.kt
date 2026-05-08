package os.kei.ui.page.main.ba

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.widget.glass.AppInteractiveTokens
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.glassStyle
import os.kei.ui.page.main.widget.glass.resolvedGlassBlurDp
import os.kei.ui.page.main.widget.glass.resolvedGlassLensDp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BaLiquidCardShape = RoundedCornerShape(24.dp)
private val BaLiquidPanelShape = RoundedCornerShape(18.dp)

@Composable
private fun BaLiquidSurfaceColumn(
    backdrop: Backdrop?,
    modifier: Modifier,
    shape: RoundedCornerShape,
    cornerRadius: Dp,
    accentColor: Color,
    accentAlpha: Float,
    variant: GlassVariant,
    effectsEnabled: Boolean,
    contentPadding: PaddingValues,
    verticalSpacing: Dp,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    pressFeedback: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val glass = glassStyle(
        isDark = isDark,
        variant = variant,
        blurRadius = null,
    )
    val fallbackSurface = accentColor
        .copy(alpha = (accentAlpha * 0.25f).coerceIn(0f, 0.04f))
        .compositeOver(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = glass.fallbackAlpha))
    val borderColor = accentColor.copy(
        alpha = if (isDark) accentAlpha * 1.1f else accentAlpha * 0.95f
    )
    val accentTint = accentColor.copy(alpha = (accentAlpha * 0.35f).coerceIn(0f, 0.05f))
    val interactionSource = remember { MutableInteractionSource() }
    val hasInteraction = onClick != null || onLongClick != null
    val hasLiquidPress = pressFeedback
    val useLiquidClick = onClick != null && onLongClick == null
    val clickModifier = if (hasInteraction && !useLiquidClick) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }
    val fallbackClickModifier = if (hasInteraction) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }
    val localBackdrop = rememberLayerBackdrop()
    val activeBackdrop = when {
        !effectsEnabled -> null
        backdrop != null -> backdrop
        else -> localBackdrop
    }
    val liquidShape = RoundedRectangle(cornerRadius)
    val pressSafePadding = if (hasLiquidPress) {
        AppInteractiveTokens.compactLiquidPressSafePadding
    } else {
        0.dp
    }

    if (activeBackdrop != null) {
        Box(modifier = modifier.padding(pressSafePadding)) {
            if (backdrop == null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .layerBackdrop(localBackdrop)
                )
            }
            LiquidSurface(
                backdrop = activeBackdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(clickModifier),
                shape = liquidShape,
                isInteractive = hasLiquidPress,
                surfaceColor = accentTint
                    .compositeOver(glass.overlayColor)
                    .compositeOver(glass.baseColor),
                blurRadius = resolvedGlassBlurDp(UiPerformanceBudget.backdropBlur, variant),
                lensRadius = resolvedGlassLensDp(UiPerformanceBudget.backdropLens, variant),
                shadow = glass.shadowAlpha > 0f,
                interactionSource = interactionSource,
                consumeDragChanges = false,
                onClick = if (useLiquidClick) onClick else null,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                    content = content,
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .padding(pressSafePadding)
                .clip(shape)
                .background(fallbackSurface, shape)
                .then(
                    if (borderColor.alpha > 0.01f && glass.borderWidth > 0.dp) {
                        Modifier.border(
                            width = glass.borderWidth,
                            color = borderColor,
                            shape = shape,
                        )
                    } else {
                        Modifier
                    }
                )
                .then(fallbackClickModifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

@Composable
internal fun BaLiquidCard(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    accentAlpha: Float = 0f,
    effectsEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    verticalSpacing: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pressFeedback: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BaLiquidSurfaceColumn(
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth(),
        shape = BaLiquidCardShape,
        cornerRadius = 24.dp,
        accentColor = accentColor,
        accentAlpha = accentAlpha,
        variant = GlassVariant.Bar,
        effectsEnabled = effectsEnabled,
        contentPadding = contentPadding,
        verticalSpacing = verticalSpacing,
        onClick = onClick,
        onLongClick = onLongClick,
        pressFeedback = pressFeedback,
        content = content,
    )
}

@Composable
internal fun BaLiquidPanel(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    accentAlpha: Float = 0.05f,
    effectsEnabled: Boolean = true,
    variant: GlassVariant = GlassVariant.Compact,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    verticalSpacing: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pressFeedback: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BaLiquidSurfaceColumn(
        backdrop = backdrop,
        modifier = modifier
            .fillMaxWidth(),
        shape = BaLiquidPanelShape,
        cornerRadius = 18.dp,
        accentColor = accentColor,
        accentAlpha = accentAlpha,
        variant = variant,
        effectsEnabled = effectsEnabled,
        contentPadding = contentPadding,
        verticalSpacing = verticalSpacing,
        onClick = onClick,
        onLongClick = onLongClick,
        pressFeedback = pressFeedback,
        content = content,
    )
}

@Composable
internal fun BaLiquidMetricPanel(
    backdrop: Backdrop?,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    valueColor: Color = accentColor,
    valueMaxLines: Int = 1,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    BaLiquidPanel(
        backdrop = backdrop,
        modifier = modifier,
        accentColor = accentColor,
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = label,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.92f),
                        maxLines = 1,
                    )
                    Text(
                        text = value,
                        color = valueColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = valueMaxLines.coerceAtLeast(1),
                    )
                    secondary?.takeIf { it.isNotBlank() }?.let { text ->
                        Text(
                            text = text,
                            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.88f),
                            maxLines = 1,
                        )
                    }
                }
            trailing?.invoke(this)
        }
    }
}
