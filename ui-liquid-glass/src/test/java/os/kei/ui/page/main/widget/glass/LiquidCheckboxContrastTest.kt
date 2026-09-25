package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Test
import kotlin.test.assertTrue

class LiquidCheckboxContrastTest {
    @Test
    fun lightCheckedSurfaceKeepsWhiteCheckmarkAboveThreeToOne() {
        val checkedSurface =
            liquidCheckboxCheckedSurfaceColor(isDark = false)
                .compositeOver(Color.White)
        val contrast = glassContrastRatio(liquidCheckboxCheckmarkColor(isDark = false), checkedSurface)

        assertTrue(contrast >= 3f, "Expected light checkbox contrast >= 3:1, got $contrast")
    }

    @Test
    fun darkCheckedSurfaceKeepsDarkCheckmarkAboveThreeToOne() {
        val checkedSurface =
            liquidCheckboxCheckedSurfaceColor(isDark = true)
                .compositeOver(Color(0xFF15181E))
        val contrast = glassContrastRatio(liquidCheckboxCheckmarkColor(isDark = true), checkedSurface)

        assertTrue(contrast >= 3f, "Expected dark checkbox contrast >= 3:1, got $contrast")
    }
}
