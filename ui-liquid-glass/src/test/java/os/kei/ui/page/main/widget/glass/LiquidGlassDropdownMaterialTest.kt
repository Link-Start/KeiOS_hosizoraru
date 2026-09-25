package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The menu panel's material.
 *
 * Most of what this file used to assert has been deleted rather than migrated, and the reason matters:
 * the old `LiquidGlassDropdownMetrics` carried twenty optical values — blur, lens start and end, four
 * shadow alphas per theme, highlight and inner-shadow alphas, vibrancy and depth flags — and *none of
 * them ever reached a shader*. The panel rendered in a `Popup`, where the scene backdrop is blanked, so
 * `activeGlassBackdrop` resolved to null and the opaque fallback drew every time. One of the deleted
 * tests was even named `actionMenuTracksBackdropDocumentationGeometry` and pinned `lensEnd` to 54dp.
 *
 * Tests over dead configuration are worse than no tests: they read as coverage. What is asserted here is
 * the legibility and lens floors of the material the panel actually draws; the reveal maths is in
 * LiquidMenuRevealTest.
 */
class LiquidGlassDropdownMaterialTest {
    @Test
    fun theFillDiffersByThemeAndStaysLegible() {
        LiquidGlassDropdownMaterial.entries.forEach { material ->
            val dark = liquidMenuGlassFill(isDark = true, material = material)
            val light = liquidMenuGlassFill(isDark = false, material = material)

            assertNotEquals(dark, light)
            assertTrue(dark.red < 0.5f && dark.green < 0.5f && dark.blue < 0.5f)
            assertTrue(light.red > 0.5f && light.green > 0.5f && light.blue > 0.5f)
            // Menu rows are dense text with no scrim between the panel and the page.
            assertTrue(
                "$material fill too sheer to read against",
                dark.alpha >= 0.80f && light.alpha >= 0.78f,
            )
        }
    }

    @Test
    fun theFallbackIsHeavierThanTheGlassFillAndSharesItsHue() {
        listOf(true, false).forEach { isDark ->
            val glass = liquidMenuGlassFill(isDark, LiquidGlassDropdownMaterial.ActionMenu)
            val fallback = liquidMenuFallbackFill(isDark)

            assertTrue(fallback.alpha > glass.alpha)
            assertEquals(glass.copy(alpha = 1f), fallback.copy(alpha = 1f))
        }
    }

    @Test
    fun theContainerRadiusStaysLargeEnoughForTheRefractionRim() {
        // safeLiquidLens caps the rim at the smallest corner radius, so a panel with a small radius
        // silently loses its refraction. Both presets have to stay generous.
        LiquidGlassDropdownMaterial.entries.forEach { material ->
            assertTrue(liquidGlassDropdownMetrics(material).containerRadius >= 24.dp)
        }
    }
}
