@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * Demo composable showing how to use custom shader effects.
 *
 * This serves as a reference for integrating [pulseRipple], [radialRefraction],
 * and [directionalBlur] into liquid glass components.
 *
 * Usage:
 * ```kotlin
 * LiquidGlassShaderDemo(backdrop = pageBackdrop)
 * ```
 */
@Composable
fun LiquidGlassShaderDemo(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animate ripple radius when pressed
    val rippleRadius by animateFloatAsState(
        targetValue = if (isPressed) 200f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "ripple_radius",
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ContinuousCapsule },
                    effects = {
                        // Base liquid glass effects
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(
                            16.dp.toPx(),
                            28.dp.toPx(),
                            chromaticAberration = true,
                            depthEffect = true,
                        )

                        // Custom shader: pulse ripple from center
                        pulseRipple(
                            centerX = size.width / 2f,
                            centerY = size.height / 2f,
                            radius = rippleRadius,
                            strength = 8f,
                            width = 40f,
                        )
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    innerShadow = {
                        val progress = if (isPressed) 1f else 0f
                        InnerShadow(radius = 6.dp * progress, alpha = progress)
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.15f))
                    },
                ),
        )
    }
}

/**
 * Example: Using radialRefraction for a magnifying glass effect.
 */
@Composable
fun LiquidGlassMagnifierExample(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    centerX: Float = 0f,
    centerY: Float = 0f,
) {
    Box(
        modifier = modifier
            .size(100.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousCapsule },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())

                    // Radial refraction: stronger at center, fading to edges
                    radialRefraction(
                        centerX = centerX,
                        centerY = centerY,
                        radius = 50.dp.toPx(),
                        strength = 12f,
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
            ),
    )
}
