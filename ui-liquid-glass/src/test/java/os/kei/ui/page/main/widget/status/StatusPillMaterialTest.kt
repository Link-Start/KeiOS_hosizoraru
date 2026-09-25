package os.kei.ui.page.main.widget.status

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPillMaterialTest {
    @Test
    fun lightStandaloneFallbackKeepsAnAiryTintedGlassStack() {
        val accent = Color(0xFF60A5FA)
        val optics =
            statusPillFallbackOptics(
                isDark = false,
                accent = accent,
                backgroundAlpha = 0.24f,
                borderAlpha = 0.42f,
            )

        assertEquals(0.24f, optics.baseColor.alpha, COLOR_CHANNEL_TOLERANCE)
        assertTrue(optics.veilTop.alpha > optics.veilMiddle.alpha)
        assertTrue(optics.veilMiddle.alpha > optics.innerShadeBottom.alpha * 0.75f)
        assertEquals(0.42f, optics.rimColor.alpha, COLOR_CHANNEL_TOLERANCE)
        assertTrue(optics.rimColor.red > accent.red)
        assertTrue(optics.rimColor.green > accent.green)
    }

    @Test
    fun darkStandaloneFallbackKeepsTheBaseLightAndTheShadeQuiet() {
        val accent = Color(0xFF22C55E)
        val optics =
            statusPillFallbackOptics(
                isDark = true,
                accent = accent,
                backgroundAlpha = 0.18f,
                borderAlpha = 0.35f,
            )

        assertEquals(0.18f, optics.baseColor.alpha, COLOR_CHANNEL_TOLERANCE)
        assertTrue(optics.veilTop.alpha < 0.08f)
        assertTrue(optics.innerShadeBottom.alpha < 0.04f)
        assertEquals(0.35f, optics.rimColor.alpha, COLOR_CHANNEL_TOLERANCE)
        assertTrue(optics.rimColor.red > accent.red)
        assertTrue(optics.rimColor.blue > accent.blue)
    }

    @Test
    fun fallbackOverridesStillControlTheWholeOpticalStack() {
        val optics =
            statusPillFallbackOptics(
                isDark = false,
                accent = Color(0xFF60A5FA),
                backgroundAlpha = 0f,
                borderAlpha = 0f,
            )

        assertEquals(0f, optics.baseColor.alpha, COLOR_CHANNEL_TOLERANCE)
        assertEquals(0f, optics.veilTop.alpha, COLOR_CHANNEL_TOLERANCE)
        assertEquals(0f, optics.veilMiddle.alpha, COLOR_CHANNEL_TOLERANCE)
        assertEquals(0f, optics.innerShadeBottom.alpha, COLOR_CHANNEL_TOLERANCE)
        assertEquals(0f, optics.rimColor.alpha, COLOR_CHANNEL_TOLERANCE)
    }

    @Test
    fun transparentRimVariantFallbackKeepsTheLegacyBlueSurfaceInLightAndDarkThemes() {
        val accent = Color(0xFF3B82F6)
        val surfaceAlpha = 0x22 / 255f
        val lightOptics =
            statusPillFallbackOptics(
                isDark = false,
                accent = accent,
                backgroundAlpha = surfaceAlpha,
                borderAlpha = 0f,
            )
        val darkOptics =
            statusPillFallbackOptics(
                isDark = true,
                accent = accent,
                backgroundAlpha = surfaceAlpha,
                borderAlpha = 0f,
            )

        listOf(lightOptics, darkOptics).forEach { optics ->
            assertEquals(surfaceAlpha, optics.baseColor.alpha, COLOR_CHANNEL_TOLERANCE)
            assertEquals(accent.red, optics.baseColor.red, COLOR_CHANNEL_TOLERANCE)
            assertEquals(accent.green, optics.baseColor.green, COLOR_CHANNEL_TOLERANCE)
            assertEquals(accent.blue, optics.baseColor.blue, COLOR_CHANNEL_TOLERANCE)
            assertEquals(0f, optics.rimColor.alpha, COLOR_CHANNEL_TOLERANCE)
        }
        assertTrue(lightOptics.veilTop.alpha > darkOptics.veilTop.alpha)
        assertTrue(lightOptics.innerShadeBottom.alpha < darkOptics.innerShadeBottom.alpha)
    }

    private companion object {
        const val COLOR_CHANNEL_TOLERANCE = 0.001f
    }
}
