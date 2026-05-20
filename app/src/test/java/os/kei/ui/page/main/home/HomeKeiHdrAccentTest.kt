package os.kei.ui.page.main.home

import org.junit.Test
import kotlin.test.assertEquals

class HomeKeiHdrAccentTest {
    @Test
    fun `sweep is invisible at animation edges`() {
        assertEquals(0f, homeKeiHdrSweepVisibility(-0.35f))
        assertEquals(0f, homeKeiHdrSweepVisibility(0f))
        assertEquals(0f, homeKeiHdrSweepVisibility(1f))
        assertEquals(0f, homeKeiHdrSweepVisibility(1.35f))
    }

    @Test
    fun `sweep reaches full visibility near the center`() {
        assertEquals(1f, homeKeiHdrSweepVisibility(0.5f))
    }
}
