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
 * Radial bulge refraction centered on a point.
 *
 * Displaces backdrop content outward from [centerX]/[centerY], strongest at the
 * center and fading to zero at [radius]. This produces a localized "press bulge"
 * that the built-in [com.kyant.backdrop.effects.lens] cannot, since lens refracts
 * along the shape outline rather than around an arbitrary point.
 *
 * Coordinate spaces: [centerX]/[centerY] are in component (pre-padding) space.
 * Earlier effects in the chain (notably `blur`) inflate the backdrop layer by
 * [BackdropEffectScope.padding] on every side, so the shader receives `coord` in
 * the padded layer space. We mirror the built-in lens convention and pass
 * `offset = -padding` to translate `coord` back into component space before
 * measuring distance to the center.
 *
 * @param centerX X of the effect center, in component pixels
 * @param centerY Y of the effect center, in component pixels
 * @param radius Radius of the effect area, in component pixels
 * @param strength Peak displacement at the center, in pixels
 */
fun BackdropEffectScope.radialRefraction(
    centerX: Float,
    centerY: Float,
    radius: Float,
    strength: Float = 12f,
) {
    if (radius <= 0f || strength <= 0f) return

    // Capture the accumulated padding from the scope (set by prior blur/lens).
    // -padding shifts the padded layer coord back into component space.
    val offset = -padding

    runtimeShaderEffect(
        key = "RadialRefraction",
        shaderString = RadialRefractionShaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("offset", offset, offset)
        setFloatUniform("center", centerX, centerY)
        setFloatUniform("radius", radius)
        setFloatUniform("strength", strength)
    }
}


// ── AGSL Shader Strings ─────────────────────────────────────────────

@Language("AGSL")
private val RadialRefractionShaderString = """
uniform shader content;

uniform float2 offset;
uniform float2 center;
uniform float radius;
uniform float strength;

half4 main(float2 coord) {
    // Translate from padded layer space into component space.
    float2 c = coord + offset;
    float dist = distance(c, center);

    if (dist >= radius) {
        return content.eval(coord);
    }

    // Normalized distance from center (0 = center, 1 = edge)
    float nd = dist / radius;

    // Refraction amount: stronger at center, fading to edges
    float refraction = (1.0 - nd * nd) * strength;

    // Direction is identical in component and layer space (pure translation).
    float2 dir = dist > 0.0 ? normalize(c - center) : float2(0.0, 0.0);

    // Displace in layer space, since content.eval samples layer coordinates.
    float2 displaced = coord + dir * refraction;

    return content.eval(displaced);
}
""".trimIndent()
