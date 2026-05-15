package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_INTERVAL_MS
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `runtime persistence update keeps newest fields and home overview wakeup`() {
        val first = BaRuntimePersistenceUpdate(
            apCurrent = 120.0,
            apRegenBaseMs = 1_000L,
            notifyHomeOverview = true,
        )
        val second = BaRuntimePersistenceUpdate(
            apCurrent = 121.0,
            cafeStoredAp = 30.0,
            notifyHomeOverview = false,
        )

        val merged = first.mergedWith(second)

        assertEquals(121.0, merged.apCurrent)
        assertEquals(1_000L, merged.apRegenBaseMs)
        assertEquals(30.0, merged.cafeStoredAp)
        assertTrue(merged.notifyHomeOverview)
    }

    @Test
    fun `ap notification plan sends threshold only for a new reached level`() {
        val plan = planBaApNotificationSync(
            BaApNotificationSyncRequest(
                currentDisplay = 120,
                limitDisplay = 240,
                thresholdDisplay = 120,
                notifyEnabled = true,
                lastNotifiedLevel = 119,
            )
        )

        assertTrue(plan.shouldSendThresholdNotification)
        assertFalse(plan.shouldRefreshActiveNotification)
        assertNull(plan.nextLastNotifiedLevel)
    }

    @Test
    fun `ap notification plan resets notified level below threshold`() {
        val plan = planBaApNotificationSync(
            BaApNotificationSyncRequest(
                currentDisplay = 80,
                limitDisplay = 240,
                thresholdDisplay = 120,
                notifyEnabled = true,
                lastNotifiedLevel = 120,
            )
        )

        assertFalse(plan.shouldSendThresholdNotification)
        assertTrue(plan.shouldRefreshActiveNotification)
        assertEquals(-1, plan.nextLastNotifiedLevel)
    }
}
