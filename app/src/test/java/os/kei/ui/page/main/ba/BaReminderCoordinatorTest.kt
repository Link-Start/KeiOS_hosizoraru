package os.kei.ui.page.main.ba

import os.kei.ui.page.main.ba.support.BA_AP_REGEN_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BA_CAFE_STUDENT_REFRESH_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaReminderCoordinatorTest {
    @Test
    fun `ap threshold plan emits notification when regenerated AP crosses threshold`() {
        val plan = BaReminderCoordinator.evaluateApThreshold(
            snapshot = BaPageSnapshot(
                apNotifyEnabled = true,
                apCurrent = 119.0,
                apRegenBaseMs = NOW_MS - BA_AP_REGEN_INTERVAL_MS,
                apNotifyThreshold = 120,
                apLimit = 240,
                apLastNotifiedLevel = -1
            ),
            nowMs = NOW_MS
        )

        val notification = assertNotNull(plan.notification)
        assertTrue(plan.shouldSaveAp)
        assertFalse(plan.resetLastNotifiedLevel)
        assertEquals(120, notification.currentDisplay)
        assertEquals(240, notification.limitDisplay)
        assertEquals(120, notification.thresholdDisplay)
    }

    @Test
    fun `ap threshold plan skips duplicate level notification`() {
        val plan = BaReminderCoordinator.evaluateApThreshold(
            snapshot = BaPageSnapshot(
                apNotifyEnabled = true,
                apCurrent = 121.0,
                apRegenBaseMs = NOW_MS,
                apNotifyThreshold = 120,
                apLimit = 240,
                apLastNotifiedLevel = 121
            ),
            nowMs = NOW_MS
        )

        assertNull(plan.notification)
        assertFalse(plan.resetLastNotifiedLevel)
    }

    @Test
    fun `cafe visit reminder seeds baseline then notifies on later slot`() {
        val currentSlot = currentCafeStudentRefreshSlotMs(NOW_MS, serverIndex = 2)

        val seed = BaReminderCoordinator.evaluateCafeVisit(
            snapshot = BaPageSnapshot(
                cafeVisitNotifyEnabled = true,
                cafeVisitLastNotifiedSlotMs = 0L,
                serverIndex = 2
            ),
            nowMs = NOW_MS
        )
        assertIs<BaSlotReminderPlan.SeedBaseline>(seed)
        assertEquals(currentSlot, seed.slotMs)

        val notify = BaReminderCoordinator.evaluateCafeVisit(
            snapshot = BaPageSnapshot(
                cafeVisitNotifyEnabled = true,
                cafeVisitLastNotifiedSlotMs = currentSlot - BA_CAFE_STUDENT_REFRESH_INTERVAL_MS,
                serverIndex = 2
            ),
            nowMs = NOW_MS
        )
        assertIs<BaSlotReminderPlan.Notify>(notify)
        assertEquals(currentSlot, notify.slotMs)
    }

    @Test
    fun `disabled slot reminder requests last notified reset`() {
        val plan = BaReminderCoordinator.evaluateArenaRefresh(
            snapshot = BaPageSnapshot(
                arenaRefreshNotifyEnabled = false,
                arenaRefreshLastNotifiedSlotMs = NOW_MS,
                serverIndex = 2
            ),
            nowMs = NOW_MS
        )

        assertEquals(BaSlotReminderPlan.Reset, plan)
    }

    @Test
    fun `reminder key normalizes server time and lead fields`() {
        val key = BaReminderKey(
            serverIndex = 9,
            type = " calendar_start ",
            id = 7,
            atMs = -20L,
            leadHours = -1
        ).encode()

        assertEquals("2|calendar_start|7|0|0", key)
    }

    private companion object {
        private const val NOW_MS = 1_777_392_000_000L
    }
}
