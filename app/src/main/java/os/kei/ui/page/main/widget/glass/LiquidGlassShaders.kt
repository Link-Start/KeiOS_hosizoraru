@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import org.intellij.lang.annotations.Language

/**
 * Custom AGSL shader effects for liquid glass components.
 *
 * These shaders extend the built-in backdrop effects (blur, lens, vibrancy)
 * with project-specific visual behaviors.
 */

/**
 * Pulse ripple effect that creates an expanding circular distortion.
 *
 * Useful for button press feedback — creates a visual "pulse" that emanates
 * from the touch point, distorting the backdrop content as it passes.
 *
 * @param centerX X coordinate of the ripple center (in pixels)
 * @param centerY Y coordinate of the ripple center (in pixels)
 * @param radius Current radius of the ripple (0 = no effect, grows with animation)
 * @param strength Distortion strength (higher = more distortion)
 * @param width Width of the ripple ring (in pixels)
 */
fun BackdropEffectScope.pulseRipple(
    centerX: Float,
    centerY: Float,
    radius: Float,
    strength: Float = 8f,
    width: Float = 40f,
) {
    if (radius <= 0f) return

    runtimeShaderEffect(
        key = "PulseRipple",
        shaderString = PulseRippleShaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("center", centerX, centerY)
        setFloatUniform("radius", radius)
        setFloatUniform("strength", strength)
        setFloatUniform("width", width)
    }
}

/**
 * Radial gradient refraction effect.
 *
 * Creates a lens-like distortion that's stronger at the center and
 * fades towards the edges. Useful for creating a "magnifying glass"
 * effect on interactive elements.
 *
 * @param centerX X coordinate of the center (in pixels)
 * @param centerY Y coordinate of the center (in pixels)
 * @param radius Radius of the effect area (in pixels)
 * @param strength Refraction strength at the center
 */
fun BackdropEffectScope.radialRefraction(
    centerX: Float,
    centerY: Float,
    radius: Float,
    strength: Float = 12f,
) {
    if (radius <= 0f || strength <= 0f) return

    runtimeShaderEffect(
        key = "RadialRefraction",
        shaderString = RadialRefractionShaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("center", centerX, centerY)
        setFloatUniform("radius", radius)
        setFloatUniform("strength", strength)
    }
}

/**
 * Directional motion blur effect.
 *
 * Creates a blur that's stronger in one direction, simulating motion.
 * The built-in `blur()` effect is isotropic (same in all directions).
 *
 * @param angle Direction of the blur in radians (0 = right, PI/2 = down)
 * @param length Length of the blur (in pixels)
 */
fun BackdropEffectScope.directionalBlur(
    angle: Float,
    length: Float,
) {
    if (length <= 0f) return

    runtimeShaderEffect(
        key = "DirectionalBlur",
        shaderString = DirectionalBlurShaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("direction", kotlin.math.cos(angle), kotlin.math.sin(angle))
        setFloatUniform("length", length)
    }
}


// ── AGSL Shader Strings ─────────────────────────────────────────────

@Language("AGSL")
private val PulseRippleShaderString = """
uniform shader content;

uniform float2 center;
uniform float radius;
uniform float strength;
uniform float width;

half4 main(float2 coord) {
    float dist = distance(coord, center);

    // Create a ring at the current radius
    float ring = 1.0 - abs(dist - radius) / width;
    ring = clamp(ring, 0.0, 1.0);

    // Smooth falloff
    ring = ring * ring * (3.0 - 2.0 * ring);

    // Direction from center
    float2 dir = normalize(coord - center);

    // Distortion amount based on ring intensity
    float distortion = ring * strength;

    // Displace the coordinate
    float2 displaced = coord + dir * distortion;

    return content.eval(displaced);
}
""".trimIndent()

@Language("AGSL")
private val RadialRefractionShaderString = """
uniform shader content;

uniform float2 center;
uniform float radius;
uniform float strength;

half4 main(float2 coord) {
    float dist = distance(coord, center);

    if (dist >= radius) {
        return content.eval(coord);
    }

    // Normalized distance from center (0 = center, 1 = edge)
    float nd = dist / radius;

    // Refraction amount: stronger at center, fading to edges
    float refraction = (1.0 - nd * nd) * strength;

    // Direction from center
    float2 dir = dist > 0.0 ? normalize(coord - center) : float2(0.0, 0.0);

    // Displace outward
    float2 displaced = coord + dir * refraction;

    return content.eval(displaced);
}
""".trimIndent()

@Language("AGSL")
private val DirectionalBlurShaderString = """
uniform shader content;

uniform float2 direction;
uniform float length;

half4 main(float2 coord) {
    half4 color = half4(0.0);
    const int samples = 16;

    for (int i = 0; i < samples; i++) {
        float t = float(i) / float(samples - 1) - 0.5;
        float2 offset = direction * length * t;
        color += content.eval(coord + offset);
    }

    return color / float(samples);
}
""".trimIndent()
