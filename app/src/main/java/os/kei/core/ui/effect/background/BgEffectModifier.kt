// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package os.kei.core.ui.effect.background

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val BG_EFFECT_TARGET_FPS = 30L
private const val BG_EFFECT_TIME_WRAP_SECONDS = 62.831852f

internal fun Modifier.bgEffectDraw(
    painter: BgEffectPainter,
    isDark: Boolean,
    surface: Color,
    effectBackground: Boolean,
    isFullSize: Boolean,
    playing: Boolean,
    alpha: () -> Float,
): Modifier =
    this then
        BgEffectElement(
            painter = painter,
            isDark = isDark,
            surface = surface,
            effectBackground = effectBackground,
            isFullSize = isFullSize,
            playing = playing,
            alpha = alpha,
        )

private data class BgEffectElement(
    val painter: BgEffectPainter,
    val isDark: Boolean,
    val surface: Color,
    val effectBackground: Boolean,
    val isFullSize: Boolean,
    val playing: Boolean,
    val alpha: () -> Float,
) : ModifierNodeElement<BgEffectNode>() {
    override fun create(): BgEffectNode =
        BgEffectNode(
            painter = painter,
            isDark = isDark,
            surface = surface,
            effectBackground = effectBackground,
            isFullSize = isFullSize,
            playing = playing,
            alpha = alpha,
        )

    override fun update(node: BgEffectNode) {
        node.update(
            painter = painter,
            isDark = isDark,
            surface = surface,
            effectBackground = effectBackground,
            isFullSize = isFullSize,
            playing = playing,
            alpha = alpha,
        )
    }
}

private class BgEffectNode(
    private var painter: BgEffectPainter,
    private var isDark: Boolean,
    private var surface: Color,
    private var effectBackground: Boolean,
    private var isFullSize: Boolean,
    private var playing: Boolean,
    private var alpha: () -> Float,
) : Modifier.Node(),
    DrawModifierNode {
    private var animationJob: Job? = null
    private var animTime: Float = 0f
    private var startOffset: Float = 0f

    override fun onAttach() {
        if (playing) startAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }

    fun update(
        painter: BgEffectPainter,
        isDark: Boolean,
        surface: Color,
        effectBackground: Boolean,
        isFullSize: Boolean,
        playing: Boolean,
        alpha: () -> Float,
    ) {
        this.painter = painter
        this.isDark = isDark
        this.surface = surface
        this.effectBackground = effectBackground
        this.isFullSize = isFullSize
        this.alpha = alpha
        if (this.playing != playing) {
            this.playing = playing
            if (playing) {
                startAnimation()
            } else {
                animationJob?.cancel()
                animationJob = null
            }
        }
        invalidateDraw()
    }

    private fun startAnimation() {
        animationJob?.cancel()
        startOffset = animTime
        animationJob =
            coroutineScope.launch {
                val minDeltaNanos = 1_000_000_000L / BG_EFFECT_TARGET_FPS
                val origin = withFrameNanos { it }
                var lastEmit = origin
                while (isActive) {
                    val now = withFrameNanos { it }
                    if (now - lastEmit < minDeltaNanos) continue
                    lastEmit = now
                    animTime = (startOffset + (now - origin) / 1_000_000_000f) % BG_EFFECT_TIME_WRAP_SECONDS
                    invalidateDraw()
                }
            }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        if (effectBackground) {
            val alphaValue = alpha()
            if (alphaValue > 0f) {
                val drawHeight = if (isFullSize) size.height else size.height * 0.5f
                painter.updateResolution(size.width, size.height)
                painter.updateBoundIfNeeded(drawHeight, size.height, size.width)
                painter.updateModeIfNeeded(isDark)
                painter.updateAnimTime(animTime)
                painter.updatePointsAnim(animTime)
                drawRect(painter.brush, alpha = alphaValue)
            }
        }
        drawContent()
    }
}
