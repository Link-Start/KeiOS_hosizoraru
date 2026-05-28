// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName")

package os.kei.core.ui.effect.background

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ceil
import kotlin.math.floor

private const val DYNAMIC_BACKGROUND_RENDER_SCALE = 0.18f

@Composable
fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    effectBackground: Boolean = true,
    isFullSize: Boolean = false,
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        Box(modifier = modifier, content = content)
        return
    }

    val isDark = isSystemInDarkTheme()
    val surface = MiuixTheme.colorScheme.surface
    val painter = remember { BgEffectPainter() }

    val preset = remember(isDark) { BgEffectConfig.get(isDark) }

    val colorStage = remember { Animatable(0f) }
    LaunchedEffect(dynamicBackground, preset) {
        if (!dynamicBackground) return@LaunchedEffect
        val animatesColors = preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
        if (!animatesColors) return@LaunchedEffect

        var targetStage = floor(colorStage.value) + 1f
        while (isActive) {
            delay((preset.colorInterpPeriod * 500).toLong())
            colorStage.animateTo(
                targetValue = targetStage,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
            )
            targetStage += 1f
        }
    }

    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val renderScale =
        if (dynamicBackground && shaderSupported) {
            DYNAMIC_BACKGROUND_RENDER_SCALE
        } else {
            1f
        }
    val renderSize =
        IntSize(
            width = ceil(targetSize.width * renderScale).toInt().coerceAtLeast(0),
            height = ceil(targetSize.height * renderScale).toInt().coerceAtLeast(0),
        )

    Box(
        modifier = modifier.onSizeChanged { targetSize = it },
    ) {
        if (renderSize.width > 0 && renderSize.height > 0) {
            val renderWidthDp = with(density) { renderSize.width.toDp() }
            val renderHeightDp = with(density) { renderSize.height.toDp() }
            val shaderSizeModifier =
                if (dynamicBackground) {
                    Modifier
                        .requiredSize(renderWidthDp, renderHeightDp)
                        .graphicsLayer {
                            scaleX = 1f / renderScale
                            scaleY = 1f / renderScale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                } else {
                    Modifier.fillMaxSize()
                }
            Spacer(
                modifier = Modifier
                    .then(shaderSizeModifier)
                    .then(bgModifier)
                    .bgEffectDraw(
                        painter = painter,
                        preset = preset,
                        isDark = isDark,
                        surface = surface,
                        effectBackground = effectBackground,
                        isFullSize = isFullSize,
                        playing = dynamicBackground && effectBackground,
                        colorStage = { colorStage.value },
                        alpha = alpha,
                    ),
            )
        }
        content()
    }
}
