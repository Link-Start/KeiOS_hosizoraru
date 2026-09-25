package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidBadgeColorsTest {
    @Test
    fun noCustomContainerKeepsTheThemePair() {
        listOf(
            "no container" to null,
            "the default container passed explicitly" to DefaultContainer,
            "an unspecified container" to Color.Unspecified,
        ).forEach { (label, container) ->
            assertEquals(LiquidBadgeColors(DefaultContainer, DefaultContent), resolveColors(containerColor = container), label)
        }
    }

    @Test
    fun commonCustomColorsMeetNormalTextContrast() {
        listOf(
            Color(0xFF3B82F6),
            Color(0xFF22C55E),
            Color(0xFFEF4444),
            Color(0xFFF59E0B),
        ).forEach { container ->
            val colors = resolveColors(containerColor = container)

            assertTrue(
                glassContrastRatio(colors.contentColor, colors.containerColor) >= MINIMUM_TEXT_CONTRAST,
                "Expected contrast >= $MINIMUM_TEXT_CONTRAST for $colors",
            )
        }
    }

    @Test
    fun darkCustomContainerChoosesWhite() {
        val colors = resolveColors(containerColor = Color(0xFF1E3A8A))

        assertEquals(Color.White, colors.contentColor)
    }

    @Test
    fun translucentContainerUsesCompositedBackgroundInLightAndDarkThemes() {
        val container = Color(0x803B82F6)
        listOf(Color.White, Color(0xFF15181E)).forEach { surface ->
            val colors = resolveColors(containerColor = container, surfaceColor = surface)
            val effectiveContainer = colors.containerColor.compositeOver(surface)

            assertTrue(
                glassContrastRatio(colors.contentColor, effectiveContainer) >= MINIMUM_TEXT_CONTRAST,
                "Expected contrast >= $MINIMUM_TEXT_CONTRAST for $colors over $surface",
            )
        }
    }

    @Test
    fun explicitContentColorIsPreserved() {
        val explicitContent = Color(0xFF7F1D1D)
        val colors =
            resolveColors(
                containerColor = Color(0xFFF59E0B),
                contentColor = explicitContent,
            )

        assertEquals(explicitContent, colors.contentColor)
    }

    @Test
    fun unspecifiedContentColorUsesASafeAutomaticFallback() {
        val automatic =
            resolveColors(
                containerColor = Color(0xFF3B82F6),
                contentColor = Color.Unspecified,
            )
        assertTrue(automatic.contentColor == Color.Black || automatic.contentColor == Color.White)
        assertTrue(glassContrastRatio(automatic.contentColor, automatic.containerColor) >= MINIMUM_TEXT_CONTRAST)
    }

    @Test
    fun nonFiniteColorsFallBackWithoutReachingLuminance() {
        val invalid =
            Color(
                red = Float.NaN,
                green = 0.5f,
                blue = 0.5f,
                alpha = 1f,
                colorSpace = ColorSpaces.DisplayP3,
            )

        assertEquals(
            LiquidBadgeColors(DefaultContainer, DefaultContent),
            resolveColors(containerColor = invalid),
        )

        val automatic =
            resolveColors(
                containerColor = Color(0xFF3B82F6),
                contentColor = invalid,
            )
        assertTrue(automatic.contentColor == Color.Black || automatic.contentColor == Color.White)
        assertTrue(glassContrastRatio(automatic.contentColor, automatic.containerColor) >= MINIMUM_TEXT_CONTRAST)
    }

    @Test
    fun invalidSurfaceUsesDeterministicOpaqueFallback() {
        listOf(
            Color.Unspecified,
            Color(
                red = Float.NaN,
                green = 0.5f,
                blue = 0.5f,
                alpha = 1f,
                colorSpace = ColorSpaces.DisplayP3,
            ),
        ).forEach { invalidSurface ->
            val colors =
                resolveColors(
                    containerColor = Color(0x803B82F6),
                    surfaceColor = invalidSurface,
                )
            val effectiveContainer = colors.containerColor.compositeOver(Color.White)

            assertTrue(colors.containerColor.componentsAreFinite())
            assertTrue(colors.contentColor.componentsAreFinite())
            assertTrue(glassContrastRatio(colors.contentColor, effectiveContainer) >= MINIMUM_TEXT_CONTRAST)
        }
    }

    private fun resolveColors(
        containerColor: Color? = null,
        contentColor: Color? = null,
        surfaceColor: Color = Color.White,
    ): LiquidBadgeColors =
        resolveLiquidBadgeColors(
            containerColor = containerColor,
            contentColor = contentColor,
            defaultContainerColor = DefaultContainer,
            defaultContentColor = DefaultContent,
            surfaceColor = surfaceColor,
        )

    private fun Color.componentsAreFinite(): Boolean = red.isFinite() && green.isFinite() && blue.isFinite() && alpha.isFinite()

    private companion object {
        val DefaultContainer = Color(0xFFB3261E)
        val DefaultContent = Color(0xFFFFFFFF)
        const val MINIMUM_TEXT_CONTRAST = 4.5f
    }
}
