// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package os.kei.core.ui.effect.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.asComposeShader
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

private const val DYNAMIC_BACKGROUND_FRAME_INTERVAL_MS = 33L

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
    val painter = remember { if (shaderSupported) BgEffectPainter() else null }

    val shaderBrush = remember(painter) {
        painter?.runtimeShader?.let { runtimeShader ->
            ShaderBrush(runtimeShader.asComposeShader())
        }
    }
    val isDark = isSystemInDarkTheme()

    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    var shaderReady by remember { mutableStateOf(false) }
    var frameVersion by remember { mutableIntStateOf(0) }
    val fallbackEffectHeight = with(LocalDensity.current) { 600.dp.toPx() }

    LaunchedEffect(targetSize, isDark, effectBackground, dynamicBackground) {
        if (painter == null || shaderBrush == null) {
            shaderReady = false
            return@LaunchedEffect
        }
        if (!effectBackground || targetSize.width <= 0 || targetSize.height <= 0) {
            shaderReady = false
            return@LaunchedEffect
        }
        val effectHeight = if (isFullSize) {
            targetSize.height.toFloat()
        } else {
            fallbackEffectHeight
        }
        painter.showRuntimeShader(
            effectHeight,
            targetSize.height.toFloat(),
            targetSize.width.toFloat(),
            isDark,
        )
        painter.setAnimTime(0f)
        painter.setResolution(
            floatArrayOf(
                targetSize.width.toFloat(),
                targetSize.height.toFloat(),
            ),
        )
        painter.updateMaterials()
        shaderReady = true
        frameVersion++

        if (!dynamicBackground) return@LaunchedEffect

        val startTimeNs = System.nanoTime()
        while (dynamicBackground) {
            delay(DYNAMIC_BACKGROUND_FRAME_INTERVAL_MS.milliseconds)
            val animTime = ((System.nanoTime() - startTimeNs) / 1_000_000_000f) % 62.831852f
            painter.setAnimTime(animTime)
            painter.updateMaterials()
            frameVersion++
        }
    }

    Box(
        modifier = modifier.onSizeChanged {
            targetSize = it
        },
    ) {
        val surface = MiuixTheme.colorScheme.surface
        if (shaderReady && shaderBrush != null) {
            val currentFrameVersion = frameVersion
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(bgModifier),
            ) {
                currentFrameVersion
                drawRect(surface)
                drawRect(shaderBrush, alpha = alpha())
            }
        }
        content()
    }
}
