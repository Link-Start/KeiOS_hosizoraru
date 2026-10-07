package os.kei.ui.page.main.student.model3d

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.test.*
import org.junit.Test

class BaModel3dBackgroundTest {
    @Test fun `manual opaque color remains the same in both themes`() {
        val custom = 0xFF387A9C.toInt()
        assertEquals(custom, model3dBackground(custom, Color.White).toArgb())
        assertEquals(custom, model3dBackground(custom, Color.Black).toArgb())
        assertEquals(Color.White, model3dBackground(null, Color.White))
        assertEquals(Color.Black, model3dBackground(null, Color.Black))
    }
    @Test fun `picker alpha blends into an opaque theme background`() {
        val transparent = model3dBackground(0x00387A9C, Color.White)
        assertEquals(Color.White, transparent)
        val half = model3dBackground(0x80000000.toInt(), Color.White)
        assertEquals(1f, half.alpha)
        assertTrue(half.red in 0.49f..0.51f)
        assertEquals("#7F7F7F", model3dBackgroundHex(half))
    }
    @Test fun `chrome picks the higher contrast text color on neutral and saturated backgrounds`() {
        assertTrue(model3dUsesDarkContent(Color.White))
        assertFalse(model3dUsesDarkContent(Color.Black))
        assertTrue(model3dUsesDarkContent(Color.Yellow))
        assertFalse(model3dUsesDarkContent(Color.Blue))
        for (dark in listOf(false, true)) {
            val background = model3dDefaultBackground(if (dark) Color.Black else Color.White, Color.Blue, dark)
            assertEquals(1f, background.alpha)
            assertEquals(!dark, model3dUsesDarkContent(background))
        }
    }
    @Test fun `RGB and ARGB input preserve color and reject partial or executable values`() {
        assertEquals(0xFF2255AA.toInt(), parseModel3dColor("#2255aa"))
        assertEquals(0x802255AA.toInt(), parseModel3dColor(" 802255AA "))
        for (raw in listOf("", "#12", "#2255A", "#FF2255AA99", "red", "#GG2255", "#2255AA;alert(1)")) {
            assertNull(parseModel3dColor(raw))
        }
    }
}
