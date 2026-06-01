@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalLiquidParentBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun AppStandaloneLiquidTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
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
    pressSafePadding: Dp = Dp.Unspecified,
) {
    val resolvedPressSafePadding =
        if (pressSafePadding == Dp.Unspecified) {
            defaultLiquidPressSafePadding(variant)
        } else {
            pressSafePadding
        }
    AppStandaloneBackdropHost(
        modifier = modifier,
        pressSafePadding = resolvedPressSafePadding,
    ) { activeBackdrop ->
        AppLiquidTextButton(
            backdrop = activeBackdrop,
            text = text,
            onClick = onClick,
            modifier = buttonModifier,
            textColor = textColor,
            containerColor = containerColor,
            leadingIcon = leadingIcon,
            iconTint = iconTint,
            enabled = enabled,
            onLongClick = onLongClick,
            onPressedChange = onPressedChange,
            blurRadius = blurRadius,
            variant = variant,
            minHeight = minHeight,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            textMaxLines = textMaxLines,
            textOverflow = textOverflow,
            textSoftWrap = textSoftWrap,
            textSize = textSize,
            textLineHeight = textLineHeight,
            textFontWeight = textFontWeight,
            pressScaleEnabled = pressScaleEnabled,
            pressOverlayEnabled = pressOverlayEnabled,
        )
    }
}

@Composable
fun AppStandaloneLiquidIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    shape: Shape = ContinuousCapsule,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    iconTint: Color = MiuixTheme.colorScheme.primary,
    containerColor: Color? = null,
    enabled: Boolean = true,
    pressSafePadding: Dp = Dp.Unspecified,
) {
    val resolvedPressSafePadding =
        if (pressSafePadding == Dp.Unspecified) {
            defaultLiquidPressSafePadding(variant)
        } else {
            pressSafePadding
        }
    AppStandaloneBackdropHost(
        modifier = modifier,
        pressSafePadding = resolvedPressSafePadding,
    ) { activeBackdrop ->
        AppLiquidIconButton(
            backdrop = activeBackdrop,
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = buttonModifier,
            width = width,
            height = height,
            shape = shape,
            blurRadius = blurRadius,
            variant = variant,
            iconTint = iconTint,
            containerColor = containerColor,
            enabled = enabled,
        )
    }
}

@Composable
fun AppStandaloneLiquidIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
    shape: Shape = ContinuousCapsule,
    blurRadius: Dp? = null,
    variant: GlassVariant = GlassVariant.Content,
    iconTint: Color = Color.Unspecified,
    iconModifier: Modifier = Modifier,
    containerColor: Color? = null,
    enabled: Boolean = true,
    pressSafePadding: Dp = Dp.Unspecified,
) {
    val resolvedPressSafePadding =
        if (pressSafePadding == Dp.Unspecified) {
            defaultLiquidPressSafePadding(variant)
        } else {
            pressSafePadding
        }
    AppStandaloneBackdropHost(
        modifier = modifier,
        pressSafePadding = resolvedPressSafePadding,
    ) { activeBackdrop ->
        AppLiquidIconButton(
            backdrop = activeBackdrop,
            painter = painter,
            contentDescription = contentDescription,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = buttonModifier,
            width = width,
            height = height,
            shape = shape,
            blurRadius = blurRadius,
            variant = variant,
            iconTint = iconTint,
            iconModifier = iconModifier,
            containerColor = containerColor,
            enabled = enabled,
        )
    }
}

@Composable
internal fun AppStandaloneBackdropHost(
    modifier: Modifier,
    pressSafePadding: Dp = Dp.Unspecified,
    content: @Composable BoxScope.(Backdrop?) -> Unit,
) {
    val resolvedPressSafePadding =
        if (pressSafePadding == Dp.Unspecified) {
            0.dp
        } else {
            pressSafePadding
        }
    val parentBackdrop = LocalLiquidParentBackdrop.current
    Box(
        modifier = modifier.padding(resolvedPressSafePadding),
        contentAlignment = Alignment.Center,
    ) {
        if (parentBackdrop != null && appGlassRuntimeEffectsEnabled()) {
            content(parentBackdrop)
        } else if (appGlassRuntimeEffectsEnabled()) {
            val backdrop = rememberLayerBackdrop()
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(backdrop),
            )
            content(backdrop)
        } else {
            content(null)
        }
    }
}
