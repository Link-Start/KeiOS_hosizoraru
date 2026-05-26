package os.kei.ui.page.main.github.section

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.LiquidSurface
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import os.kei.ui.page.main.widget.glass.resolvedGlassBlurDp
import os.kei.ui.page.main.widget.glass.resolvedGlassLensDp
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleBorder
import top.yukonga.miuix.kmp.basic.Text

@Suppress("FunctionName")
@Composable
internal fun GitHubAssetCountBubble(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val isDark = isSystemInDarkTheme()
    val localBackdrop = rememberLayerBackdrop()
    val activeBackdrop = localBackdrop.takeIf { LocalLiquidControlsEnabled.current }
    val shape = CircleShape
    val cornerRadius = 999.dp
    val bubbleModifier =
        Modifier
            .then(
                if (activeBackdrop == null) {
                    Modifier.appSquircleBackground(
                        color = color.copy(alpha = if (isDark) 0.18f else 0.12f),
                        cornerRadius = cornerRadius,
                    )
                } else {
                    Modifier
                },
            ).appSquircleBorder(
                width = 0.8.dp,
                color = color.copy(alpha = if (isDark) 0.34f else 0.24f),
                cornerRadius = cornerRadius,
            )
    val content: @Composable () -> Unit = {
        if (loading) {
            LiquidCircularProgressBar(
                size = 14.dp,
                strokeWidth = 2.dp,
                activeColor = color,
                inactiveColor = color.copy(alpha = 0.18f),
            )
        } else {
            Text(
                text = label,
                color = if (isDark) color else color.copy(alpha = 0.96f),
                fontSize = AppTypographyTokens.Caption.fontSize,
                lineHeight = AppTypographyTokens.Caption.lineHeight,
                fontWeight = AppTypographyTokens.Caption.fontWeight,
                maxLines = 1,
            )
        }
    }
    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (activeBackdrop != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(localBackdrop),
            )
            LiquidSurface(
                backdrop = activeBackdrop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .then(bubbleModifier),
                shape = shape,
                isInteractive = false,
                surfaceColor = color.copy(alpha = if (isDark) 0.18f else 0.12f),
                blurRadius =
                    resolvedGlassBlurDp(
                        UiPerformanceBudget.backdropBlur,
                        GlassVariant.Compact,
                    ),
                lensRadius =
                    resolvedGlassLensDp(
                        UiPerformanceBudget.backdropLens,
                        GlassVariant.Compact,
                    ),
                shadow = false,
            ) {
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .then(bubbleModifier),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
