package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BA_CAFE_HOURLY_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BA_CAFE_STUDENT_REFRESH_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs
import os.kei.ui.page.main.ba.support.floorToHourMs
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
    fun `cafe ap threshold plan emits notification when stored AP crosses threshold`() {
        val plan = BaReminderCoordinator.evaluateCafeApThreshold(
            snapshot = BaPageSnapshot(
                cafeApNotifyEnabled = true,
                cafeStoredAp = 70.0,
                cafeLastHourMs = floorToHourMs(NOW_MS) - 2L * BA_CAFE_HOURLY_INTERVAL_MS,
                cafeApNotifyThreshold = 120,
                cafeApLastNotifiedLevel = -1,
                cafeLevel = 10
            ),
            nowMs = NOW_MS
        )

        val notification = assertNotNull(plan.notification)
        assertTrue(plan.shouldSaveCafe)
        assertFalse(plan.resetLastNotifiedLevel)
        assertEquals(131, notification.currentDisplay)
        assertEquals(740, notification.limitDisplay)
        assertEquals(120, notification.thresholdDisplay)
    }

    @Test
    fun `cafe ap threshold plan resets duplicate guard below threshold`() {
        val plan = BaReminderCoordinator.evaluateCafeApThreshold(
            snapshot = BaPageSnapshot(
                cafeApNotifyEnabled = true,
                cafeStoredAp = 80.0,
                cafeLastHourMs = NOW_MS,
                cafeApNotifyThreshold = 120,
                cafeApLastNotifiedLevel = 120,
                cafeLevel = 10
            ),
            nowMs = NOW_MS
        )

        assertNull(plan.notification)
        assertTrue(plan.resetLastNotifiedLevel)
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

    @Test
    fun `calendar reminder groups upcoming entries by time and skips notified keys`() {
        val first = calendarEntry(id = 1, beginAtMs = NOW_MS + 30_000L)
        val second = calendarEntry(id = 2, beginAtMs = NOW_MS + 30_000L)
        val later = calendarEntry(id = 3, beginAtMs = NOW_MS + 2 * HOUR_MS)
        val notifiedKey = BaReminderKey(
            serverIndex = 2,
            type = "calendar_start",
            id = second.id,
            atMs = second.beginAtMs,
            leadHours = 1
        ).encode()

        val groups = BaReminderCoordinator.calendarUpcomingGroups(
            entries = listOf(first, second, later),
            nowMs = NOW_MS,
            serverIndex = 2,
            leadHours = 1,
            notifiedKeys = setOf(notifiedKey)
        )

        assertEquals(1, groups.size)
        assertEquals(listOf(first), groups.single().entries)
        assertEquals(
            "2|calendar_start|1|${first.beginAtMs}|1",
            groups.single().keys.single()
        )
    }

    @Test
    fun `pool ending reminder requires running entry inside lead window`() {
        val running = poolEntry(id = 7, endAtMs = NOW_MS + 10_000L, isRunning = true)
        val future = poolEntry(id = 8, endAtMs = NOW_MS + 10_000L, isRunning = false)

        val groups = BaReminderCoordinator.poolEndingGroups(
            entries = listOf(running, future),
            nowMs = NOW_MS,
            serverIndex = 1,
            leadHours = 1,
            notifiedKeys = emptySet()
        )

        assertEquals(1, groups.size)
        assertEquals(listOf(running), groups.single().entries)
        assertEquals("1|pool_end|7|${running.endAtMs}|1", groups.single().keys.single())
    }

    @Test
    fun `change reminder key uses shared key normalization`() {
        val key = BaReminderCoordinator.changeKey(
            serverIndex = 6,
            type = "pool_change",
            changedCount = 3,
            fingerprint = -1L
        )

        assertEquals("2|pool_change|3|0|0", key)
    }

    @Test
    fun `notified key policy clears disabled server and stale lead keys`() {
        val keptCalendar = BaReminderKey(
            serverIndex = 1,
            type = "calendar_start",
            id = 10,
            atMs = NOW_MS,
            leadHours = 6
        ).encode()
        val staleLead = BaReminderKey(
            serverIndex = 1,
            type = "calendar_end",
            id = 11,
            atMs = NOW_MS,
            leadHours = 12
        ).encode()
        val otherServer = BaReminderKey(
            serverIndex = 2,
            type = "pool_start",
            id = 12,
            atMs = NOW_MS,
            leadHours = 6
        ).encode()
        val keptChange = BaReminderKey(
            serverIndex = 1,
            type = "pool_change",
            id = 2,
            atMs = NOW_MS,
            leadHours = 0
        ).encode()

        val retained = BaReminderCoordinator.retainNotifiedKeysForPolicy(
            keys = setOf(keptCalendar, staleLead, otherServer, keptChange, "broken"),
            serverIndex = 1,
            leadHours = 6,
            calendarUpcomingEnabled = true,
            calendarEndingEnabled = true,
            poolUpcomingEnabled = true,
            poolEndingEnabled = false,
            calendarPoolChangeEnabled = true
        )

        assertEquals(setOf(keptCalendar, keptChange), retained)
    }

    @Test
    fun `notified key policy clears all keys when calendar pool notifications are disabled`() {
        val retained = BaReminderCoordinator.retainNotifiedKeysForPolicy(
            keys = setOf(
                BaReminderKey(
                    serverIndex = 1,
                    type = "calendar_start",
                    id = 10,
                    atMs = NOW_MS,
                    leadHours = 6
                ).encode(),
                BaReminderKey(
                    serverIndex = 1,
                    type = "calendar_change",
                    id = 1,
                    atMs = NOW_MS,
                    leadHours = 0
                ).encode()
            ),
            serverIndex = 1,
            leadHours = 6,
            calendarUpcomingEnabled = false,
            calendarEndingEnabled = false,
            poolUpcomingEnabled = false,
            poolEndingEnabled = false,
            calendarPoolChangeEnabled = false
        )

        assertTrue(retained.isEmpty())
    }

    private fun calendarEntry(
        id: Int,
        beginAtMs: Long,
        endAtMs: Long = beginAtMs + HOUR_MS,
        isRunning: Boolean = false
    ): BaCalendarEntry {
        return BaCalendarEntry(
            id = id,
            title = "Event $id",
            kindId = 31,
            kindName = "Event",
            beginAtMs = beginAtMs,
            endAtMs = endAtMs,
            linkUrl = "https://www.gamekee.com/ba/huodong/$id",
            imageUrl = "",
            isRunning = isRunning
        )
    }

    private fun poolEntry(
        id: Int,
        startAtMs: Long = NOW_MS - HOUR_MS,
        endAtMs: Long,
        isRunning: Boolean
    ): BaPoolEntry {
        return BaPoolEntry(
            id = id,
            name = "Pool $id",
            tagId = 6,
            tagName = "Pickup",
            startAtMs = startAtMs,
            endAtMs = endAtMs,
            linkUrl = "https://www.gamekee.com/ba/kachi/$id",
            imageUrl = "",
            isRunning = isRunning
        )
    }

    private companion object {
        private const val NOW_MS = 1_777_392_000_000L
        private const val HOUR_MS = 60L * 60L * 1000L
    }
}
