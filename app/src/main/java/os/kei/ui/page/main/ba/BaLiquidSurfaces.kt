package os.kei.ui.page.main.ba

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.widget.core.AppSurfaceBox
import os.kei.ui.page.main.widget.glass.AppInteractiveTokens
import os.kei.ui.page.main.widget.chrome.LocalAppManagedSceneBackdrop
import os.kei.ui.page.main.widget.chrome.appManagedPageCardMaterialColor
import os.kei.ui.page.main.widget.chrome.appPageBackdropBaseColor
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdropOverridesFallback
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.activeGlassBackdrop
import os.kei.ui.page.main.widget.glass.appEdgeStackLayer
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackSlot
import os.kei.ui.page.main.widget.glass.glassStyle
import os.kei.ui.page.main.widget.glass.resolvedGlassBlurDp
import os.kei.ui.page.main.widget.glass.resolvedGlassLensDp
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleBorder
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun BaLiquidSurfaceColumn(
    backdrop: Backdrop?,
    modifier: Modifier,
    cornerRadius: Dp,
    accentColor: Color,
    accentAlpha: Float,
    variant: GlassVariant,
    effectsEnabled: Boolean,
    shadowEnabled: Boolean,
    contentPadding: PaddingValues,
    verticalSpacing: Dp,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    pressFeedback: Boolean,
    flattenOverUniformParent: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isAppInDarkTheme()
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
    val hasInteraction = onClick != null || onLongClick != null
    val hasLiquidPress = pressFeedback && hasInteraction
    val parentBackdrop = LocalLiquidParentBackdrop.current
    val inheritedBackdrop =
        if (LocalLiquidParentBackdropOverridesFallback.current) {
            parentBackdrop ?: backdrop
        } else {
            backdrop ?: parentBackdrop
        }
    // Blurring a locally uniform field returns that field, so a panel nested over a parent that
    // exported its layer can composite its colours straight onto the real background and land on the
    // same pixels — measured at max 6/255, with nothing over a 3% threshold. What it saves is a second
    // offscreen layer over the same area, which is the BA office page's whole frame budget.
    //
    // Gated on having no gesture. The press deformation lives inside the glass layer, so a panel that
    // can be pressed keeps its layer rather than trading feedback for frames.
    val flattenToUniformFill = flattenOverUniformParent && !hasInteraction
    // Composited over the *page's* card material, not over the card's own fill.
    //
    // An exporting card's layer carries the page material, not the card's surface on top of it, so the
    // glass panel refracts the page colour while the card paints its own over the same pixels. In light
    // the two are within a level of each other and it does not show; in dark, `background` and
    // `surfaceContainer` are far enough apart that compositing over the wrong one left every flattened
    // panel reading lighter than its glass twin. Kept alpha-correct rather than opaque so a managed
    // background still shows through, exactly as the material it stands in for does.
    val uniformFill =
        if (flattenToUniformFill) {
            accentTint
                .compositeOver(glass.overlayColor)
                .compositeOver(glass.baseColor)
                .compositeOver(
                    appManagedPageCardMaterialColor(
                        baseColor = appPageBackdropBaseColor(),
                        elevatedColor = MiuixTheme.colorScheme.surfaceContainer,
                        backed = LocalAppManagedSceneBackdrop.current != null,
                    ),
                )
        } else {
            Color.Unspecified
        }
    val edgeStack = rememberAppEdgeStackSlot()
    // Restoring glass here is the largest visible change in this rewrite. 482f0cfb3 switched the
    // backdrop off whenever a card stack was provided, and BaCalendarPoolStackedLayout always
    // provides one — so every card on the activity calendar and the pool page fell to
    // `appSquircleBackground(fallbackSurface)`: a flat surfaceContainer at 40% alpha in dark and 56%
    // in light. Those two pages were not Liquid Glass at all, they were semi-transparent rectangles,
    // and the same gate silently took their press feedback with it (the fallback branch swaps the
    // liquid press for `combinedClickable(indication = null)`). Recession is the pile's own job now —
    // a scrim drawn inside the glass layer — so the material no longer has to be sacrificed to get it.
    val activeBackdrop =
        if (effectsEnabled) {
            activeGlassBackdrop(inheritedBackdrop)
        } else {
            null
        }
    val liquidShape = RoundedRectangle(cornerRadius)
    val pressSafePadding = if (hasLiquidPress) {
        AppInteractiveTokens.compactLiquidPressSafePadding
    } else {
        0.dp
    }
    val stackedModifier = edgeStack.modifier.then(modifier)

    if (activeBackdrop != null && !flattenToUniformFill) {
        AppSurfaceBox(
            modifier = stackedModifier,
            edgeStack = edgeStack,
            backdrop = activeBackdrop,
            surfaceColor =
                accentTint
                    .compositeOver(glass.overlayColor)
                    .compositeOver(glass.baseColor),
            shape = liquidShape,
            contentColor = LocalContentColor.current,
            isInteractive = hasLiquidPress,
            // No outer drop shadow: these cards live in a scrolling list, which bounds the ring to
            // the card's vertical extent and leaves a right angle beside each rounded corner. See
            // LiquidSurface's `shadow`.
            shadow = false,
            shadowAlpha = glass.shadowAlpha,
            exportBackdropToContent = true,
            pressSafePadding = pressSafePadding,
            blurRadius = resolvedGlassBlurDp(UiPerformanceBudget.backdropBlur, variant),
            lensRadius = resolvedGlassLensDp(UiPerformanceBudget.backdropLens, variant),
            onClick = onClick,
            onLongClick = onLongClick,
        ) {
            CompositionLocalProvider(LocalAppEdgeStackCards provides null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                    content = content,
                )
            }
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val fallbackClickModifier =
            if (hasInteraction) {
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
        Column(
            modifier = stackedModifier
                // No glass layer to fold the pile into on this path, so it gets its own.
                .appEdgeStackLayer(edgeStack)
                .padding(pressSafePadding)
                .appSquircleBackground(
                    if (flattenToUniformFill) uniformFill else fallbackSurface,
                    cornerRadius,
                )
                .then(
                    if (borderColor.alpha > 0.01f && glass.borderWidth > 0.dp) {
                        Modifier.appSquircleBorder(
                            width = glass.borderWidth,
                            color = borderColor,
                            cornerRadius = cornerRadius,
                        )
                    } else {
                        Modifier
                    }
                )
                .then(fallbackClickModifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            CompositionLocalProvider(LocalAppEdgeStackCards provides null) {
                content()
            }
        }
    }
}

@Composable
internal fun BaLiquidCard(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    accentAlpha: Float = 0f,
    effectsEnabled: Boolean = true,
    shadowEnabled: Boolean = true,
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
        cornerRadius = 24.dp,
        accentColor = accentColor,
        accentAlpha = accentAlpha,
        variant = GlassVariant.Bar,
        // Never flattened: a card samples the page's *synthetic* flat material, not the pixels behind
        // it, so drawing its colours onto the real background is a different composite. Measured: doing
        // it anyway moved 847k pixels.
        flattenOverUniformParent = false,
        effectsEnabled = effectsEnabled,
        shadowEnabled = shadowEnabled,
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
    shadowEnabled: Boolean = true,
    variant: GlassVariant = GlassVariant.Compact,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    verticalSpacing: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pressFeedback: Boolean = true,
    /**
     * Draws this panel's colours straight onto the background instead of through a glass layer.
     *
     * Only correct where the parent exported a layer that is uniform under this panel — see
     * `BaLiquidPanelUniformFillSourceTest` for the three conditions and what each one costs.
     */
    flattenOverUniformParent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BaLiquidSurfaceColumn(
        backdrop = backdrop,
        flattenOverUniformParent = flattenOverUniformParent,
        modifier = modifier
            .fillMaxWidth(),
        cornerRadius = 18.dp,
        accentColor = accentColor,
        accentAlpha = accentAlpha,
        variant = variant,
        effectsEnabled = effectsEnabled,
        shadowEnabled = shadowEnabled,
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
    valueFontFamily: FontFamily? = null,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    labelLineHeight: TextUnit = TextUnit.Unspecified,
    labelFontWeight: FontWeight = FontWeight.Normal,
    valueFontSize: TextUnit = TextUnit.Unspecified,
    valueLineHeight: TextUnit = TextUnit.Unspecified,
    valueFontWeight: FontWeight = FontWeight.Bold,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pressFeedback: Boolean = true,
    effectsEnabled: Boolean = true,
    shadowEnabled: Boolean = true,
    /** See [BaLiquidPanel]. */
    flattenOverUniformParent: Boolean = false,
) {
    BaLiquidPanel(
        backdrop = backdrop,
        flattenOverUniformParent = flattenOverUniformParent,
        modifier = modifier,
        accentColor = accentColor,
        effectsEnabled = effectsEnabled,
        shadowEnabled = shadowEnabled,
        contentPadding = contentPadding,
        onClick = onClick,
        onLongClick = onLongClick,
        pressFeedback = pressFeedback,
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
                    fontSize = labelFontSize,
                    lineHeight = labelLineHeight,
                    fontWeight = labelFontWeight,
                    maxLines = 1,
                )
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = valueFontSize,
                    lineHeight = valueLineHeight,
                    fontWeight = valueFontWeight,
                    fontFamily = valueFontFamily,
                    maxLines = valueMaxLines.coerceAtLeast(1),
                    overflow = TextOverflow.Ellipsis,
                )
                secondary?.takeIf { it.isNotBlank() }?.let { text ->
                    Text(
                        text = text,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.88f),
                        fontSize = labelFontSize,
                        lineHeight = labelLineHeight,
                        maxLines = 1,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}
