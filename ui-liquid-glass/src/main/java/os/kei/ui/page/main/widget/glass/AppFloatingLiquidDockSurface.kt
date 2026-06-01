package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.animation.InteractiveHighlight
import os.kei.ui.page.main.widget.shape.appSquircleBackground
import os.kei.ui.page.main.widget.shape.appSquircleBorder
import os.kei.ui.page.main.widget.shape.appSquircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.min

@Composable
fun AppFloatingLiquidVerticalDockSurface(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val activeBackdrop = activeGlassBackdrop(backdrop)
    val isDark = isSystemInDarkTheme()
    val glass = glassStyle(
        isDark = isDark,
        variant = GlassVariant.Bar,
        blurRadius = null
    )
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            highlightStrength = 1.18f,
            highlightRadiusScale = 1.34f,
            consumeDragChanges = false
        )
    }
    val fallbackSurface = MiuixTheme.colorScheme.surfaceContainer
    val surfaceAlpha = if (isDark) 0.28f else 0.30f
    val highlightAlpha = if (isDark) min(glass.highlightAlpha, 0.52f) else 0.98f
    val shadowAlpha = if (isDark) 0.18f else 0.14f
    val overlayTop = if (isDark) {
        Color.White.copy(alpha = 0.070f)
    } else {
        Color.White.copy(alpha = 0.115f)
    }
    val overlayBottom = if (isDark) {
        Color(0xFF82B8FF).copy(alpha = 0.055f)
    } else {
        Color(0xFFB9D8FF).copy(alpha = 0.095f)
    }
    val sideRim = if (isDark) {
        Color.White.copy(alpha = 0.075f)
    } else {
        Color.White.copy(alpha = 0.32f)
    }
    val innerRim = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    val edgeColor = if (isDark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color(0xFF9FCCFF).copy(alpha = 0.76f)
    }

    Box(
        modifier = modifier
            .then(
                if (activeBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = activeBackdrop,
                        shape = { ContinuousCapsule },
                        layerBlock = {
                            val progress = interactiveHighlight.pressProgress
                            if (progress > 0f) {
                                scaleX = 1f + 2.dp.toPx() / size.width.coerceAtLeast(1f) * progress
                                scaleY = 1f + 2.dp.toPx() / size.height.coerceAtLeast(1f) * progress
                            }
                        },
                        effects = {
                            vibrancy()
                            blur(4.dp.toPx())
                            lens(
                                28.dp.toPx(),
                                54.dp.toPx(),
                                chromaticAberration = true,
                                depthEffect = true
                            )
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = highlightAlpha)
                        },
                        shadow = {
                            Shadow.Default.copy(color = Color.Black.copy(alpha = shadowAlpha))
                        },
                        innerShadow = {
                            InnerShadow(radius = 7.dp, alpha = if (isDark) 0.22f else 0.34f)
                        },
                        onDrawSurface = {
                            drawRect(fallbackSurface.copy(alpha = surfaceAlpha))
                        }
                    )
                } else {
                    Modifier.appSquircleBackground(fallbackSurface.copy(alpha = surfaceAlpha), 999.dp)
                }
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .graphicsLayer { clip = false }
            .appSquircleClip(999.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(overlayTop, overlayBottom)
                ),
            )
            .appSquircleBorder(
                width = 1.dp,
                color = edgeColor,
                cornerRadius = 999.dp,
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appSquircleClip(999.dp)
                    .background(
                        Brush.horizontalGradient(
                        colors = listOf(
                            sideRim,
                            Color.Transparent,
                            Color.Transparent,
                            sideRim
                        )
                    )
                )
        )
        Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appSquircleBorder(
                        width = 1.dp,
                        color = innerRim,
                        cornerRadius = 999.dp,
                    )
            )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}
