package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidSurfaceMaterialTest {
    @Test
    fun enabledSurfaceOmitsIdentityContentLayer() {
        assertEquals(0, liquidSurfaceContentAlphaModifier(enabled = true).elementCount())
        assertEquals(1, liquidSurfaceContentAlphaModifier(enabled = false).elementCount())
    }

    @Test
    fun passiveSurfaceOmitsZeroContributionShadowLayers() {
        assertNull(
            liquidSurfaceOuterShadowOrNull(
                enabled = false,
                alpha = 0.10f,
            ),
        )
        assertNull(
            liquidSurfaceOuterShadowOrNull(
                enabled = true,
                alpha = 0f,
            ),
        )
        assertFalse(
            liquidSurfaceNeedsInteractiveInnerShadow(
                isInteractive = false,
                enabled = true,
            ),
        )
    }

    @Test
    fun interactiveSurfaceKeepsVisibleShadowLayers() {
        assertNotNull(
            liquidSurfaceOuterShadowOrNull(
                enabled = true,
                alpha = 0.10f,
            ),
        )
        assertTrue(
            liquidSurfaceNeedsInteractiveInnerShadow(
                isInteractive = true,
                enabled = true,
            ),
        )
    }

    @Test
    fun theGlassShadowIsTightEnoughToKeepItsCornersRound() {
        // The right-angled shadow was geometry. `ShadowNode` spreads `radius * 2`, so `Shadow.Default`'s
        // 24dp blur threw a ring 48dp past its surface: far enough to reach the enclosing scroll
        // container's clip on a card, and four times the size of a 30dp checkbox. Either way the blurred
        // silhouette keeps no visible corner rounding, and a straight boundary beside a rounded corner is
        // the artifact. A tight ring stays close to the shape it traces.
        assertTrue("a card's 24dp blur must not come back: $LiquidShadowRadius", LiquidShadowRadius <= 12.dp)

        val shadow = liquidGlassShadow(Color.Black.copy(alpha = 0.10f))
        assertEquals(LiquidShadowRadius, shadow.radius)
        // Dropped, not centred, so what little spread there is falls below the surface.
        assertTrue(shadow.offset.y > 0.dp)
        assertTrue(shadow.offset.y < shadow.radius)
        assertEquals(0.dp, shadow.offset.x)
    }

    @Test
    fun idleHighlightStaysQuietInBothThemes() {
        assertEquals(
            0.62f,
            liquidSurfaceHighlightAlpha(
                isDark = false,
                interactive = true,
                enabled = true,
                pressProgress = 0f,
            ),
        )
        assertEquals(
            0.42f,
            liquidSurfaceHighlightAlpha(
                isDark = true,
                interactive = false,
                enabled = true,
                pressProgress = 0f,
            ),
        )
    }

    @Test
    fun pressAddsOnlyAControlledHighlightBoost() {
        val lightPressed =
            liquidSurfaceHighlightAlpha(
                isDark = false,
                interactive = true,
                enabled = true,
                pressProgress = 1f,
            )
        val darkPressed =
            liquidSurfaceHighlightAlpha(
                isDark = true,
                interactive = true,
                enabled = true,
                pressProgress = 1f,
            )

        assertTrue(lightPressed <= 0.72f)
        assertTrue(darkPressed <= 0.52f)
    }

    @Test
    fun malformedPressProgressIsClamped() {
        assertEquals(
            0.62f,
            liquidSurfaceHighlightAlpha(
                isDark = false,
                interactive = true,
                enabled = true,
                pressProgress = Float.POSITIVE_INFINITY,
            ),
        )
        assertEquals(
            0.62f,
            liquidSurfaceHighlightAlpha(
                isDark = false,
                interactive = true,
                enabled = true,
                pressProgress = Float.NaN,
            ),
        )
    }

    @Test
    fun explicitHighlightOverrideIsThemeIndependentAndClamped() {
        assertEquals(
            0.82f,
            liquidSurfaceHighlightAlpha(
                isDark = true,
                interactive = false,
                enabled = true,
                pressProgress = 0f,
                overrideAlpha = 0.82f,
            ),
        )
        assertEquals(
            1f,
            liquidSurfaceHighlightAlpha(
                isDark = false,
                interactive = true,
                enabled = true,
                pressProgress = 1f,
                overrideAlpha = 1.2f,
            ),
        )
        assertEquals(
            0f,
            liquidSurfaceHighlightAlpha(
                isDark = false,
                interactive = true,
                enabled = true,
                pressProgress = 1f,
                overrideAlpha = -0.2f,
            ),
        )
    }

    @Test
    fun malformedHighlightOverrideFallsBackToDefaultCurve() {
        assertEquals(
            0.62f,
            liquidSurfaceHighlightAlpha(
                isDark = false,
                interactive = true,
                enabled = true,
                pressProgress = 0f,
                overrideAlpha = Float.NaN,
            ),
        )
        assertEquals(
            0.42f,
            liquidSurfaceHighlightAlpha(
                isDark = true,
                interactive = false,
                enabled = true,
                pressProgress = 0f,
                overrideAlpha = Float.POSITIVE_INFINITY,
            ),
        )
    }
}

private fun Modifier.elementCount(): Int = foldIn(0) { count, _ -> count + 1 }

private fun sourceFile(relativePath: String): String {
    val workingDirectory = java.io.File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val sourceFile =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> java.io.File(directory, relativePath) }
            .firstOrNull(java.io.File::isFile)
    return requireNotNull(sourceFile) { "Unable to locate $relativePath from $workingDirectory" }.readText()
}
