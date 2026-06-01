@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppStatusPrimitives
import os.kei.ui.page.main.widget.core.rememberAppStatusPillMetrics
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.appGlassRuntimeEffectsEnabled
import os.kei.ui.page.main.widget.glass.resolvedGlassBlurDp
import os.kei.ui.page.main.widget.glass.resolvedGlassLensDp
import os.kei.ui.page.main.widget.shape.drawAppSquircleBackground
import os.kei.ui.page.main.widget.shape.drawAppSquircleBorder
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: AppStatusPillSize = AppStatusPillSize.Default,
    contentPadding: PaddingValues? = null,
    backgroundAlphaOverride: Float? = null,
    borderAlphaOverride: Float? = null,
    backdrop: Backdrop? = null,
) {
    StatusPill(
        label = label,
        color = { color },
        modifier = modifier,
        size = size,
        contentPadding = contentPadding,
        backgroundAlphaOverride = backgroundAlphaOverride,
        borderAlphaOverride = borderAlphaOverride,
        backdrop = backdrop,
    )
}

@Composable
fun StatusPill(
    label: String,
    color: () -> Color,
    modifier: Modifier = Modifier,
    size: AppStatusPillSize = AppStatusPillSize.Default,
    contentPadding: PaddingValues? = null,
    backgroundAlphaOverride: Float? = null,
    borderAlphaOverride: Float? = null,
    backdrop: Backdrop? = null,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val colorProvider = color
    val resolvedColor = colorProvider()
    val metrics = rememberAppStatusPillMetrics(size)
    val resolvedPadding = contentPadding ?: metrics.contentPadding
    val backgroundAlpha = backgroundAlphaOverride ?: if (isDark) 0.18f else 0.24f
    val borderAlpha = borderAlphaOverride ?: if (isDark) 0.35f else 0.42f
    val textColor = if (isDark) resolvedColor else resolvedColor.copy(alpha = 0.96f)
    val shape = AppStatusPrimitives.pillShape
    val cornerRadius = 999.dp
    val liquidControlsEnabled = appGlassRuntimeEffectsEnabled()
    val parentBackdrop = LocalLiquidParentBackdrop.current
    val inheritedBackdrop = backdrop ?: parentBackdrop
    val pillModifier =
        Modifier
            .then(modifier)
            .then(
                if (!liquidControlsEnabled) {
                    Modifier.drawAppSquircleBackground(cornerRadius) {
                        colorProvider().copy(alpha = backgroundAlpha)
                    }
                } else {
                    Modifier
                },
            ).drawAppSquircleBorder(
                width = 0.8.dp,
                cornerRadius = cornerRadius,
            ) {
                colorProvider().copy(alpha = borderAlpha)
            }
    val content: @Composable () -> Unit = {
        DisableSelection {
            Text(
                text = label,
                color = textColor,
                fontSize = metrics.typography.fontSize,
                lineHeight = metrics.typography.lineHeight,
                fontWeight = metrics.typography.fontWeight,
                textAlign = TextAlign.Center,
            )
        }
    }
    when {
        !liquidControlsEnabled -> {
            StatusPillStatic(
                modifier = pillModifier,
                resolvedPadding = resolvedPadding,
                content = content,
            )
        }

        inheritedBackdrop != null -> {
            StatusPillLiquid(
                modifier = pillModifier,
                backdrop = inheritedBackdrop,
                captureBackdrop = null,
                shape = shape,
                surfaceColor = resolvedColor.copy(alpha = backgroundAlpha),
                resolvedPadding = resolvedPadding,
                content = content,
            )
        }

        else -> {
            val localBackdrop = rememberLayerBackdrop()
            StatusPillLiquid(
                modifier = pillModifier,
                backdrop = localBackdrop,
                captureBackdrop = localBackdrop,
                shape = shape,
                surfaceColor = resolvedColor.copy(alpha = backgroundAlpha),
                resolvedPadding = resolvedPadding,
                content = content,
            )
        }
    }
}

@Composable
private fun StatusPillStatic(
    modifier: Modifier,
    resolvedPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.padding(resolvedPadding),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun StatusPillLiquid(
    modifier: Modifier,
    backdrop: Backdrop,
    captureBackdrop: LayerBackdrop?,
    shape: Shape,
    surfaceColor: Color,
    resolvedPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box {
        if (captureBackdrop != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(captureBackdrop),
            )
        }
        LiquidSurface(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            isInteractive = false,
            surfaceColor = surfaceColor,
            blurRadius = resolvedGlassBlurDp(UiPerformanceBudget.backdropBlur, GlassVariant.Compact),
            lensRadius = resolvedGlassLensDp(UiPerformanceBudget.backdropLens, GlassVariant.Compact),
            shadow = false,
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.padding(resolvedPadding),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
