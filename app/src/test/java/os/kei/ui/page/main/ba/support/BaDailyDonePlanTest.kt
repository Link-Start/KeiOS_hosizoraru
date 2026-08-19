package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MINUTE = 60L * 1000L
private const val HOUR = 60L * MINUTE
private const val NOW = 1_700_000_000_000L

class BaDailyDonePlanTest {
    private fun snapshot(
        apCurrent: Double = 120.0,
        cafeStoredAp: Double = 300.0,
        coffeeHeadpatMs: Long = 0L,
        coffeeInvite1UsedMs: Long = 0L,
        coffeeInvite2UsedMs: Long = 0L,
        craft: BaCraftState = BaCraftState(),
        serverIndex: Int = 2,
        apLastNotifiedLevel: Int = -1,
    ): BaPageSnapshot =
        BaPageSnapshot(
            apCurrent = apCurrent,
            cafeStoredAp = cafeStoredAp,
            coffeeHeadpatMs = coffeeHeadpatMs,
            coffeeInvite1UsedMs = coffeeInvite1UsedMs,
            coffeeInvite2UsedMs = coffeeInvite2UsedMs,
            craft = craft,
            serverIndex = serverIndex,
            apLastNotifiedLevel = apLastNotifiedLevel,
        )

    @Test
    fun `both ap pools go to zero and re-base their anchors`() {
        val plan = planBaDailyDone(snapshot(), nowMs = NOW)
        assertEquals(0.0, plan.apCurrent)
        assertEquals(0.0, plan.cafeStoredAp)
        assertEquals(NOW, plan.apRegenBaseMs)
        assertEquals(NOW, plan.apSyncMs)
        assertEquals(floorToHourMs(NOW), plan.cafeLastHourMs)
        assertTrue(plan.outcome.apAdjusted)
        assertTrue(plan.outcome.cafeApCleared)
    }

    @Test
    fun `clearing is not a cafe claim - the cafe pool does not land in the player pool`() {
        val plan = planBaDailyDone(snapshot(apCurrent = 0.0, cafeStoredAp = 740.0), nowMs = NOW)
        assertEquals(0.0, plan.apCurrent)
        assertEquals(0.0, plan.cafeStoredAp)
    }

    @Test
    fun `notified levels reset so the next reminder is not deduped away`() {
        val plan = planBaDailyDone(snapshot(), nowMs = NOW)
        assertEquals(-1, plan.apLastNotifiedLevel)
        assertEquals(-1, plan.cafeApLastNotifiedLevel)
    }

    @Test
    fun `never used cooldowns all start`() {
        val plan = planBaDailyDone(snapshot(), nowMs = NOW)
        assertEquals(NOW, plan.coffeeHeadpatMs)
        assertEquals(NOW, plan.coffeeInvite1UsedMs)
        assertEquals(NOW, plan.coffeeInvite2UsedMs)
        assertTrue(plan.outcome.headpatStarted)
        assertTrue(plan.outcome.invite1Started)
        assertTrue(plan.outcome.invite2Started)
    }

    @Test
    fun `a running cooldown is left alone and not pushed out`() {
        val startedRecently = NOW - 30L * MINUTE
        val plan =
            planBaDailyDone(
                snapshot(
                    coffeeHeadpatMs = startedRecently,
                    coffeeInvite1UsedMs = startedRecently,
                    coffeeInvite2UsedMs = startedRecently,
                ),
                nowMs = NOW,
            )
        assertEquals(startedRecently, plan.coffeeHeadpatMs)
        assertEquals(startedRecently, plan.coffeeInvite1UsedMs)
        assertEquals(startedRecently, plan.coffeeInvite2UsedMs)
        assertFalse(plan.outcome.headpatStarted)
        assertFalse(plan.outcome.invite1Started)
        assertFalse(plan.outcome.invite2Started)
    }

    @Test
    fun `an elapsed invite cooldown restarts`() {
        // The invite cooldown is 20h; 21h ago is comfortably done.
        val plan = planBaDailyDone(snapshot(coffeeInvite1UsedMs = NOW - 21L * HOUR), nowMs = NOW)
        assertEquals(NOW, plan.coffeeInvite1UsedMs)
        assertTrue(plan.outcome.invite1Started)
    }

    @Test
    fun `an elapsed headpat cooldown restarts`() {
        // The headpat cooldown is 3h, and it also frees at the cafe student refresh.
        val plan = planBaDailyDone(snapshot(coffeeHeadpatMs = NOW - 4L * HOUR), nowMs = NOW)
        assertEquals(NOW, plan.coffeeHeadpatMs)
        assertTrue(plan.outcome.headpatStarted)
    }

    @Test
    fun `two generate slots are loaded with one advanced node each`() {
        val plan = planBaDailyDone(snapshot(), nowMs = NOW)
        assertEquals(BA_DAILY_DONE_CRAFT_SLOTS, plan.outcome.craftSlotsStarted)
        repeat(BA_DAILY_DONE_CRAFT_SLOTS) { index ->
            val slot = plan.craft.slotAt(BaCraftFunction.Generate, index)
            assertEquals(listOf(BaCraftGrade.High), slot.grades)
            assertEquals(NOW, slot.startedAtMs)
            assertEquals(NOW + 3L * HOUR, slot.endAtMs())
        }
    }

    @Test
    fun `the third generate slot and every fusion slot are left untouched`() {
        val plan = planBaDailyDone(snapshot(), nowMs = NOW)
        assertFalse(plan.craft.slotAt(BaCraftFunction.Generate, 2).isActive())
        repeat(BA_CRAFT_SLOT_COUNT) { index ->
            assertFalse(plan.craft.slotAt(BaCraftFunction.Fusion, index).isActive())
        }
    }

    @Test
    fun `a craft still counting down is not overwritten`() {
        val running =
            BaCraftState().withSlotAt(
                BaCraftFunction.Generate,
                0,
                BaCraftSlot(startedAtMs = NOW - HOUR, grades = List(3) { BaCraftGrade.Highest }),
            )
        val plan = planBaDailyDone(snapshot(craft = running), nowMs = NOW)
        // Slot 0 keeps its 18h craft; only slot 1 was free.
        assertEquals(1, plan.outcome.craftSlotsStarted)
        val kept = plan.craft.slotAt(BaCraftFunction.Generate, 0)
        assertEquals(3, kept.grades.size)
        assertEquals(NOW - HOUR, kept.startedAtMs)
    }

    @Test
    fun `a finished craft counts as free and is reloaded`() {
        val finished =
            BaCraftState().withSlotAt(
                BaCraftFunction.Generate,
                0,
                // A 30m craft started 5h ago: collected long since.
                BaCraftSlot(startedAtMs = NOW - 5L * HOUR, grades = listOf(BaCraftGrade.Low)),
            )
        val plan = planBaDailyDone(snapshot(craft = finished), nowMs = NOW)
        assertEquals(2, plan.outcome.craftSlotsStarted)
        val reloaded = plan.craft.slotAt(BaCraftFunction.Generate, 0)
        assertEquals(NOW, reloaded.startedAtMs)
        assertEquals(listOf(BaCraftGrade.High), reloaded.grades)
    }

    @Test
    fun `applying twice in a row changes nothing the second time`() {
        val first = planBaDailyDone(snapshot(), nowMs = NOW)
        val after =
            snapshot(
                apCurrent = first.apCurrent,
                cafeStoredAp = first.cafeStoredAp,
                coffeeHeadpatMs = first.coffeeHeadpatMs,
                coffeeInvite1UsedMs = first.coffeeInvite1UsedMs,
                coffeeInvite2UsedMs = first.coffeeInvite2UsedMs,
                craft = first.craft,
            )
        // One minute later, nothing has had time to come off cooldown.
        val second = planBaDailyDone(after, nowMs = NOW + MINUTE)
        assertFalse(second.outcome.changedAnything)
        assertEquals(0, second.outcome.craftSlotsStarted)
        assertEquals(first.coffeeHeadpatMs, second.coffeeHeadpatMs)
        assertEquals(first.craft, second.craft)
    }

    @Test
    fun `a fully spent account reports nothing changed`() {
        val plan =
            planBaDailyDone(
                snapshot(
                    apCurrent = 0.0,
                    cafeStoredAp = 0.0,
                    coffeeHeadpatMs = NOW - MINUTE,
                    coffeeInvite1UsedMs = NOW - MINUTE,
                    coffeeInvite2UsedMs = NOW - MINUTE,
                    craft =
                        BaCraftState()
                            .withSlotAt(
                                BaCraftFunction.Generate,
                                0,
                                BaCraftSlot(startedAtMs = NOW, grades = listOf(BaCraftGrade.High)),
                            )
                            .withSlotAt(
                                BaCraftFunction.Generate,
                                1,
                                BaCraftSlot(startedAtMs = NOW, grades = listOf(BaCraftGrade.High)),
                            ),
                ),
                nowMs = NOW,
            )
        assertFalse(plan.outcome.changedAnything)
    }

    @Test
    fun `the headpat rule follows the account server`() {
        // Same timestamp, different server: the cafe refresh boundary differs, so readiness can differ.
        val headpatMs = NOW - 4L * HOUR
        val cn = planBaDailyDone(snapshot(coffeeHeadpatMs = headpatMs, serverIndex = 0), nowMs = NOW)
        val jp = planBaDailyDone(snapshot(coffeeHeadpatMs = headpatMs, serverIndex = 2), nowMs = NOW)
        // Both are past the 3h cooldown, so both restart — the point is that serverIndex reaches the rule
        // at all rather than being silently dropped.
        assertEquals(NOW, cn.coffeeHeadpatMs)
        assertEquals(NOW, jp.coffeeHeadpatMs)
    }

    @Test
    fun `a configured remainder is what the pool is left holding, anchors still re-based`() {
        // The teacher stopped at 37 AP rather than exactly zero, which is the normal case for the player
        // pool — only the cafe can be emptied precisely.
        val plan =
            planBaDailyDone(
                snapshot(apCurrent = 120.0),
                config = BaDailyDoneConfig(apRemaining = 37),
                nowMs = NOW,
            )
        assertEquals(37.0, plan.apCurrent)
        assertEquals(NOW, plan.apRegenBaseMs)
        assertEquals(NOW, plan.apSyncMs)
        assertTrue(plan.outcome.apAdjusted)
        // The cafe pool is not configurable and still lands on zero.
        assertEquals(0.0, plan.cafeStoredAp)
    }

    @Test
    fun `a remainder that already matches the pool is not an adjustment`() {
        val plan =
            planBaDailyDone(
                snapshot(apCurrent = 37.0),
                config = BaDailyDoneConfig(apRemaining = 37),
                nowMs = NOW,
            )
        assertFalse(plan.outcome.apAdjusted)
    }

    @Test
    fun `a remainder above the current pool is a correction, not a refusal`() {
        // Reading the number off the game after a sync gap: the app was behind, so the template moves the
        // pool up. Refusing would leave the teacher hand-editing the value they had just typed.
        val plan =
            planBaDailyDone(
                snapshot(apCurrent = 4.0),
                config = BaDailyDoneConfig(apRemaining = 40),
                nowMs = NOW,
            )
        assertEquals(40.0, plan.apCurrent)
        assertTrue(plan.outcome.apAdjusted)
    }

    @Test
    fun `the ap notified marker is only cleared when the pool really is empty`() {
        // A remainder can sit above the teacher's own reminder threshold. Clearing the marker there would
        // re-announce a level they have already been told about.
        val kept =
            planBaDailyDone(
                snapshot(apLastNotifiedLevel = 150),
                config = BaDailyDoneConfig(apRemaining = 200),
                nowMs = NOW,
            )
        assertEquals(150, kept.apLastNotifiedLevel)

        val cleared =
            planBaDailyDone(
                snapshot(apLastNotifiedLevel = 150),
                config = BaDailyDoneConfig(apRemaining = 0),
                nowMs = NOW,
            )
        assertEquals(-1, cleared.apLastNotifiedLevel)
        // The cafe pool is always emptied, so its marker is always cleared.
        assertEquals(-1, kept.cafeApLastNotifiedLevel)
    }

    @Test
    fun `cooldowns switched off are left alone even when they are ready`() {
        val plan =
            planBaDailyDone(
                snapshot(),
                config =
                    BaDailyDoneConfig(
                        startHeadpat = false,
                        startInvite1 = false,
                        startInvite2 = false,
                    ),
                nowMs = NOW,
            )
        // Never-used cooldowns, which the default template would have started.
        assertEquals(0L, plan.coffeeHeadpatMs)
        assertEquals(0L, plan.coffeeInvite1UsedMs)
        assertEquals(0L, plan.coffeeInvite2UsedMs)
        assertFalse(plan.outcome.headpatStarted)
        assertFalse(plan.outcome.invite1Started)
        assertFalse(plan.outcome.invite2Started)
    }

    @Test
    fun `zero craft slots leaves both functions untouched`() {
        val plan =
            planBaDailyDone(snapshot(), config = BaDailyDoneConfig(craftSlots = 0), nowMs = NOW)
        assertEquals(0, plan.outcome.craftSlotsStarted)
        repeat(BA_CRAFT_SLOT_COUNT) { index ->
            assertFalse(plan.craft.slotAt(BaCraftFunction.Generate, index).isActive())
            assertFalse(plan.craft.slotAt(BaCraftFunction.Fusion, index).isActive())
        }
    }

    @Test
    fun `a fusion template loads fusion slots and leaves generate alone`() {
        val plan =
            planBaDailyDone(
                snapshot(),
                config =
                    BaDailyDoneConfig(
                        craftFunction = BaCraftFunction.Fusion,
                        craftSlots = BA_CRAFT_SLOT_COUNT,
                        craftGrade = BaCraftGrade.Highest,
                        craftEntriesPerSlot = BA_CRAFT_FUSION_MAX_ENTRIES,
                    ),
                nowMs = NOW,
            )
        assertEquals(BA_CRAFT_SLOT_COUNT, plan.outcome.craftSlotsStarted)
        repeat(BA_CRAFT_SLOT_COUNT) { index ->
            val slot = plan.craft.slotAt(BaCraftFunction.Fusion, index)
            assertEquals(List(BA_CRAFT_FUSION_MAX_ENTRIES) { BaCraftGrade.Highest }, slot.grades)
            // 5 copies at 6h each: the longest craft the game can hold.
            assertEquals(NOW + 30L * HOUR, slot.endAtMs())
            assertFalse(plan.craft.slotAt(BaCraftFunction.Generate, index).isActive())
        }
    }

    @Test
    fun `the entry count multiplies the slot duration rather than adding a second unit`() {
        val plan =
            planBaDailyDone(
                snapshot(),
                config =
                    BaDailyDoneConfig(
                        craftSlots = 1,
                        craftGrade = BaCraftGrade.Highest,
                        craftEntriesPerSlot = 2,
                    ),
                nowMs = NOW,
            )
        val slot = plan.craft.slotAt(BaCraftFunction.Generate, 0)
        assertEquals(listOf(BaCraftGrade.Highest, BaCraftGrade.Highest), slot.grades)
        assertEquals(NOW + 12L * HOUR, slot.endAtMs())
    }

    @Test
    fun `a six hour craft is one highest grade item, the shape the tile editor offers`() {
        val plan =
            planBaDailyDone(
                snapshot(),
                config = BaDailyDoneConfig(craftSlots = 1, craftGrade = BaCraftGrade.Highest),
                nowMs = NOW,
            )
        assertEquals(NOW + 6L * HOUR, plan.craft.slotAt(BaCraftFunction.Generate, 0).endAtMs())
    }

    @Test
    fun `a template that changes nothing still reports nothing changed`() {
        // Everything the configured template would touch is already spent: AP already at the remainder,
        // cooldowns running, and the one configured craft slot counting down.
        val plan =
            planBaDailyDone(
                snapshot(
                    apCurrent = 20.0,
                    cafeStoredAp = 0.0,
                    coffeeHeadpatMs = NOW - MINUTE,
                    craft =
                        BaCraftState().withSlotAt(
                            BaCraftFunction.Generate,
                            0,
                            BaCraftSlot(startedAtMs = NOW, grades = listOf(BaCraftGrade.High)),
                        ),
                ),
                config =
                    BaDailyDoneConfig(
                        apRemaining = 20,
                        startInvite1 = false,
                        startInvite2 = false,
                        craftSlots = 1,
                    ),
                nowMs = NOW,
            )
        assertFalse(plan.outcome.changedAnything)
    }
}
