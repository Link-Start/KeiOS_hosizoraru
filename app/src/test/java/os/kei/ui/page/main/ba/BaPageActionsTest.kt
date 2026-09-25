package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.ui.page.main.ba.support.BA_HEADPAT_COOLDOWN_MS
import os.kei.ui.page.main.ba.support.BA_INVITE_COOLDOWN_MS
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.calculateInviteTicketAvailableMs
import os.kei.ui.page.main.ba.support.calculateNextHeadpatAvailableMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs
import os.kei.ui.page.main.ba.support.floorToHourMs
import os.kei.ui.page.main.ba.support.nextCafeStudentRefreshMs
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
    fun `runtime persistence update keeps submitted account id`() {
        val accountId = BaAccountId("cn-main")

        val update =
            BaRuntimePersistenceUpdate(apCurrent = 120.0)
                .withAccountId(accountId)
                .mergedWith(BaRuntimePersistenceUpdate(cafeStoredAp = 30.0).withAccountId(accountId))

        assertEquals(accountId, update.accountId)
        assertEquals(120.0, update.apCurrent)
        assertEquals(30.0, update.cafeStoredAp)
    }

    @Test
    fun `default runtime normalization keeps office eligible for initial snapshot hydration`() {
        val defaultSnapshot = BaPageSnapshot()
        val office =
            BaOfficeController(
                snapshot = defaultSnapshot,
                clock = FixedBaOfficeClock(1_800_000L),
            )

        office.normalizeRuntimeState()

        assertTrue(office.matchesSnapshot(defaultSnapshot))
    }

    @Test
    fun `office controller carries AP read setting and local anchors through state and snapshots`() {
        val initialSnapshot =
            BaPageSnapshot(
                keepApRemindersReadUntilBelowThreshold = false,
                apSuppressionAnchorAtMs = 1_000L,
                cafeApSuppressionAnchorAtMs = 2_000L,
            )
        val office = BaOfficeController(initialSnapshot)

        assertTrue(office.matchesSnapshot(initialSnapshot))
        assertFalse(office.state().keepApRemindersReadUntilBelowThreshold)
        assertEquals(1_000L, office.state().apSuppressionAnchorAtMs)
        assertEquals(2_000L, office.state().cafeApSuppressionAnchorAtMs)

        val nextSnapshot =
            initialSnapshot.copy(
                keepApRemindersReadUntilBelowThreshold = true,
                apSuppressionAnchorAtMs = 3_000L,
                cafeApSuppressionAnchorAtMs = 4_000L,
            )
        office.applySnapshot(nextSnapshot)

        assertTrue(office.matchesSnapshot(nextSnapshot))
        assertTrue(office.state().keepApRemindersReadUntilBelowThreshold)
        assertEquals(3_000L, office.state().apSuppressionAnchorAtMs)
        assertEquals(4_000L, office.state().cafeApSuppressionAnchorAtMs)
    }

    @Test
    fun `runtime effects wait for active account hydration`() {
        assertFalse(
            shouldRunBaRuntimeEffects(
                isPageActive = true,
                activeAccountId = null,
            ),
        )
        assertFalse(
            shouldRunBaRuntimeEffects(
                isPageActive = false,
                activeAccountId = BaAccountId("main"),
            ),
        )
        assertTrue(
            shouldRunBaRuntimeEffects(
                isPageActive = true,
                activeAccountId = BaAccountId("main"),
            ),
        )
    }

    @Test
    fun `runtime amount changes keep office ineligible for initial snapshot hydration`() {
        val defaultSnapshot = BaPageSnapshot()
        val office =
            BaOfficeController(
                snapshot = defaultSnapshot,
                clock = FixedBaOfficeClock(1_800_000L),
            )

        office.normalizeRuntimeState()
        office.addCurrentAp(delta = 1.0, markSync = false)

        assertFalse(office.matchesSnapshot(defaultSnapshot))
    }

    @Test
    fun `cafe stored ap update clamps to cafe storage cap`() {
        val (nextStoredAp, _) =
            applyBaCafeStoredApUpdate(
                newValue = 999.0,
                cafeLevel = 1,
                nowMs = 3_745_000L,
            )

        assertEquals(cafeStorageCap(1), nextStoredAp)
    }

    @Test
    fun `manual cafe stored ap update resets cafe notification guard`() {
        val nowMs = 7_345_000L
        val office =
            BaOfficeController(
                snapshot =
                    BaPageSnapshot(
                        cafeLevel = 10,
                        cafeStoredAp = 120.0,
                        cafeApLastNotifiedLevel = 120,
                    ),
                clock = FixedBaOfficeClock(nowMs),
            )

        val update = office.updateCafeStoredAp(12.75)

        assertEquals(12.75, office.cafeStoredAp)
        assertEquals(floorToHourMs(nowMs), office.cafeLastHourMs)
        assertEquals(-1, office.cafeApLastNotifiedLevel)
        assertEquals(12.75, update.cafeStoredAp)
        assertEquals(floorToHourMs(nowMs), update.cafeLastHourMs)
        assertEquals(-1, update.cafeApLastNotifiedLevel)
    }

    @Test
    fun `ap notification plan sends threshold only for a new reached level`() {
        val accountId = BaAccountId("cn-main")
        val plan = planBaApNotificationSync(
            BaApNotificationSyncRequest(
                currentDisplay = 120,
                limitDisplay = 240,
                thresholdDisplay = 120,
                notifyEnabled = true,
                lastNotifiedLevel = 119,
                notificationId = BaAccountNotificationKind.Ap.notificationId(accountId),
                accountDisplayName = "国服主号",
                accountId = accountId,
            )
        )

        assertTrue(plan.shouldSendThresholdNotification)
        assertFalse(plan.shouldRefreshActiveNotification)
        assertNull(plan.nextLastNotifiedLevel)
        assertEquals(BaAccountNotificationKind.Ap.notificationId(accountId), plan.request.notificationId)
        assertEquals("国服主号", plan.request.accountDisplayName)
        assertEquals(accountId, plan.request.accountId)
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

    @Test
    fun `invite cooldown edit stores timestamp for desired remaining time`() {
        val nowMs = BA_INVITE_COOLDOWN_MS + 1_000_000L
        val remainingMs = 10 * 60 * 1000L

        val usedMs = applyBaInviteTicketRemainingCooldown(remainingMs, nowMs)

        assertEquals(nowMs + remainingMs, calculateInviteTicketAvailableMs(usedMs))
    }

    @Test
    fun `invite cooldown edit clears cooldown for zero remaining time`() {
        val nowMs = BA_INVITE_COOLDOWN_MS + 1_000_000L

        val usedMs = applyBaInviteTicketRemainingCooldown(0L, nowMs)

        assertEquals(0L, usedMs)
    }

    @Test
    fun `headpat cooldown edit stores timestamp for representable remaining time`() {
        val serverIndex = 1
        val seedMs = 1_783_003_200_000L
        val currentSlotMs = currentCafeStudentRefreshSlotMs(seedMs, serverIndex)
        val nowMs = currentSlotMs + 2 * 60 * 60 * 1000L
        val remainingMs = 90 * 60 * 1000L

        val lastHeadpatMs = applyBaHeadpatRemainingCooldown(remainingMs, serverIndex, nowMs)

        assertEquals(nowMs + remainingMs, calculateNextHeadpatAvailableMs(lastHeadpatMs, serverIndex))
    }

    @Test
    fun `headpat cooldown edit clamps to next student refresh`() {
        val serverIndex = 1
        val seedMs = 1_783_003_200_000L
        val nextRefreshMs = nextCafeStudentRefreshMs(seedMs, serverIndex)
        val nowMs = nextRefreshMs - 10 * 60 * 1000L

        val lastHeadpatMs = applyBaHeadpatRemainingCooldown(BA_HEADPAT_COOLDOWN_MS, serverIndex, nowMs)

        assertEquals(nextRefreshMs, calculateNextHeadpatAvailableMs(lastHeadpatMs, serverIndex))
    }

    @Test
    fun `headpat cooldown edit clamps positive input to current slot minimum when needed`() {
        val serverIndex = 1
        val seedMs = 1_783_003_200_000L
        val currentSlotMs = currentCafeStudentRefreshSlotMs(seedMs, serverIndex)
        val nowMs = currentSlotMs + 2 * 60 * 60 * 1000L

        val lastHeadpatMs = applyBaHeadpatRemainingCooldown(10 * 60 * 1000L, serverIndex, nowMs)

        assertEquals(currentSlotMs + BA_HEADPAT_COOLDOWN_MS, calculateNextHeadpatAvailableMs(lastHeadpatMs, serverIndex))
    }
}

internal class FixedBaOfficeClock(private val nowMs: Long) : BaOfficeClock {
    override fun nowMs(): Long = nowMs
}
