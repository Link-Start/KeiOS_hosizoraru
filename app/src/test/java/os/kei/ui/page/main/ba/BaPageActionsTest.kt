package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_INTERVAL_MS
import kotlin.test.assertEquals

class BaPageActionsTest {
    @Test
    fun `ap regen keeps base stable when ap is already full`() {
        val baseMs = 12_000L
        val nowMs = baseMs + BA_AP_REGEN_INTERVAL_MS

        val (nextAp, nextBase) = applyBaApRegenTick(
            apLimit = 240,
            apCurrent = 240.0,
            apRegenBaseMs = baseMs,
            nowMs = nowMs,
        )

        assertEquals(240.0, nextAp)
        assertEquals(baseMs, nextBase)
    }

    @Test
    fun `ap regen advances base only when a point is gained`() {
        val baseMs = 12_000L
        val nowMs = baseMs + BA_AP_REGEN_INTERVAL_MS

        val (nextAp, nextBase) = applyBaApRegenTick(
            apLimit = 240,
            apCurrent = 120.0,
            apRegenBaseMs = baseMs,
            nowMs = nowMs,
        )

        assertEquals(121.0, nextAp)
        assertEquals(nowMs, nextBase)
    }
}
