package os.kei.ui.page.main.widget.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLiquidSearchMaterialTest {
    /** The ramp the cached glow brushes replace, held to the values it used to draw. */
    @Test
    fun glowFractionReproducesTheOldPerFrameAlphaRamp() {
        val base = 0.100f
        val gain = APP_LIQUID_SEARCH_CENTER_GLOW_GAIN
        val peak = base + gain

        listOf(0f, 0.25f, 0.5f, 1f).forEach { progress ->
            val fraction =
                appLiquidSearchGlowAlphaFraction(
                    baseAlpha = base,
                    gain = gain,
                    materialProgress = progress,
                )
            // `base + gain * p` is what the old code baked into a fresh Brush each draw.
            assertEquals(base + gain * progress, peak * fraction, 1e-5f)
        }
    }

    /** A glow whose resting alpha is zero — the dark compact centre specular — still ramps from zero. */
    @Test
    fun aGlowThatRestsAtZeroRampsLinearly() {
        assertEquals(
            0.5f,
            appLiquidSearchGlowAlphaFraction(baseAlpha = 0f, gain = APP_LIQUID_SEARCH_CENTER_GLOW_GAIN, materialProgress = 0.5f),
            1e-5f,
        )
        assertEquals(
            0f,
            appLiquidSearchGlowAlphaFraction(baseAlpha = 0f, gain = APP_LIQUID_SEARCH_CENTER_GLOW_GAIN, materialProgress = 0f),
            1e-5f,
        )
    }
}
