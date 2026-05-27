// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName")

package os.kei.core.ui.effect.background

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ceil

private const val DYNAMIC_BACKGROUND_RENDER_SCALE = 0.18f

@Suppress("SuspiciousIndentation")
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
    val isDark = isSystemInDarkTheme()

    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val painter = remember { if (shaderSupported) BgEffectPainter() else null }
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
        modifier =
            modifier.onSizeChanged {
                targetSize = it
            },
    ) {
        val surface = MiuixTheme.colorScheme.surface
        if (painter != null && renderSize.width > 0 && renderSize.height > 0) {
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
                modifier =
                    Modifier
                        .then(shaderSizeModifier)
                        .then(bgModifier)
                        .bgEffectDraw(
                            painter = painter,
                            isDark = isDark,
                            surface = surface,
                            effectBackground = effectBackground,
                            isFullSize = isFullSize,
                            playing = dynamicBackground && effectBackground,
                            alpha = alpha,
                        ),
            )
        }
        content()
    }
}
