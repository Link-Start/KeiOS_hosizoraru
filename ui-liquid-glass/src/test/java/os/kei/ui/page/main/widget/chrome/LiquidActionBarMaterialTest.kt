package os.kei.ui.page.main.widget.chrome

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One material for both action bars.
 *
 * Replaces `LiquidGlassBottomBarMaterialTest`, which pinned the bottom bar's private near-twin of this
 * material — the very numbers the consolidation removed. Pinning them again would have frozen the
 * duplication in place, so these assert the *shared* values and that the private copy stays gone.
 */
class LiquidActionBarMaterialTest {
    @Test
    fun lightMaterialKeepsTheRebuiltReferenceRefraction() {
        val material = liquidActionBarMaterial(isLight = true)

        assertEquals(4.dp, material.blur)
        assertEquals(16.dp, material.lensHeight)
        assertEquals(32.dp, material.lensAmount)
        assertEquals(0.30f, material.surfaceAlpha)
        assertEquals(0.66f, material.highlightAlpha)
    }

    @Test
    fun darkMaterialKeepsTheRebuiltReferenceRefraction() {
        val material = liquidActionBarMaterial(isLight = false)

        assertEquals(4.dp, material.blur)
        assertEquals(16.dp, material.lensHeight)
        assertEquals(28.dp, material.lensAmount)
        assertEquals(0.22f, material.surfaceAlpha)
        assertEquals(0.46f, material.highlightAlpha)
    }

    @Test
    fun theSelectionIndicatorFollowsTheAccent() {
        // Was a flat 10% black/white film that ignored the theme's primary. The dark side still lands on
        // a neutral white film by design; only light mode mixes the accent in.
        val accent = Color(0xFF3B82F6)

        val light = liquidChromeSelectionIndicatorColor(isLight = true, accentColor = accent)
        val dark = liquidChromeSelectionIndicatorColor(isLight = false, accentColor = accent)

        // Delta, not equality: Color quantizes alpha to 8 bits, so 0.26f stores as 66/255 = 0.2588.
        assertEquals(0.26f, light.alpha, 0.01f)
        assertTrue("Light indicator should carry accent, not pure white", light.blue > light.red)
        assertEquals(Color.White.copy(alpha = 0.10f), dark)
    }

}
