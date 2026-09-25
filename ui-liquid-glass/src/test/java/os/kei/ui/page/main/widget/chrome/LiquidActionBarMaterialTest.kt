package os.kei.ui.page.main.widget.chrome

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared action-bar chrome: the selection indicator's colour mixing. */
class LiquidActionBarMaterialTest {
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
