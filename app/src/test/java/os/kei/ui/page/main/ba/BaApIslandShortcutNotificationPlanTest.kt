package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BA_CAFE_HOURLY_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaApIslandShortcutNotificationPlanTest {
    @Test
    fun `shortcut notification plan projects ap and cafe ap before sending`() {
        val nowMs = 10L * BA_CAFE_HOURLY_INTERVAL_MS
        val snapshot = BaPageSnapshot(
            apLimit = 240,
            apCurrent = 118.0,
            apRegenBaseMs = nowMs - 3L * BA_AP_REGEN_INTERVAL_MS,
            apNotifyThreshold = 120,
            cafeLevel = 10,
            cafeStoredAp = 300.0,
            cafeLastHourMs = nowMs - 2L * BA_CAFE_HOURLY_INTERVAL_MS,
            cafeApNotifyThreshold = 360
        )

        val plan = BaApIslandShortcutNotificationPlan.fromSnapshot(
            snapshot = snapshot,
            nowMs = nowMs
        )

        assertEquals(121, plan.ap.currentDisplay)
        assertEquals(240, plan.ap.limitDisplay)
        assertEquals(120, plan.ap.thresholdDisplay)
        assertEquals(361, plan.cafeAp.currentDisplay)
        assertEquals(740, plan.cafeAp.limitDisplay)
        assertEquals(360, plan.cafeAp.thresholdDisplay)
        assertTrue(plan.shouldSaveAp)
        assertTrue(plan.shouldSaveCafe)
    }

    @Test
    fun `shortcut notification plan clamps cafe threshold to cafe cap`() {
        val nowMs = BA_CAFE_HOURLY_INTERVAL_MS
        val snapshot = BaPageSnapshot(
            apLimit = 240,
            apCurrent = 42.0,
            apRegenBaseMs = nowMs,
            apNotifyThreshold = 999,
            cafeLevel = 1,
            cafeStoredAp = 50.0,
            cafeLastHourMs = nowMs,
            cafeApNotifyThreshold = 999
        )

        val plan = BaApIslandShortcutNotificationPlan.fromSnapshot(
            snapshot = snapshot,
            nowMs = nowMs
        )

        assertEquals(999, plan.ap.thresholdDisplay)
        assertEquals(92, plan.cafeAp.limitDisplay)
        assertEquals(92, plan.cafeAp.thresholdDisplay)
        assertFalse(plan.shouldSaveAp)
        assertFalse(plan.shouldSaveCafe)
    }
}
