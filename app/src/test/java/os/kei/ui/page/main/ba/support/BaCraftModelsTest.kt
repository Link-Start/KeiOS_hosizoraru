package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60L * 1000L
private const val HOUR = 60L * MINUTE

/** Any non-zero anchor: `startedAtMs == 0L` is the idle sentinel, so it cannot double as a start. */
private const val START = 1_700_000_000_000L

class BaCraftModelsTest {
    @Test
    fun `generate sums the grade of every opened node`() {
        val slot =
            BaCraftSlot(
                startedAtMs = 1_000L,
                grades = listOf(BaCraftGrade.High, BaCraftGrade.High, BaCraftGrade.Highest),
            )
        assertEquals(12L * HOUR, slot.computedDurationMs())
        assertEquals(1_000L + 12L * HOUR, slot.endAtMs())
    }

    @Test
    fun `generate caps at three entries and fusion at five`() {
        val overfilled = BaCraftSlot(grades = List(9) { BaCraftGrade.Low })
        assertEquals(3, overfilled.normalized(BaCraftFunction.Generate).grades.size)
        assertEquals(5, overfilled.normalized(BaCraftFunction.Fusion).grades.size)
    }

    @Test
    fun `fusion collapses a mixed list onto its first grade`() {
        val mixed =
            BaCraftSlot(
                grades = listOf(BaCraftGrade.High, BaCraftGrade.Low, BaCraftGrade.Highest),
            )
        val normalized = mixed.normalized(BaCraftFunction.Fusion)
        assertEquals(List(3) { BaCraftGrade.High }, normalized.grades)
        assertEquals(9L * HOUR, normalized.computedDurationMs())
    }

    @Test
    fun `generate keeps a mixed list intact`() {
        val mixed = listOf(BaCraftGrade.High, BaCraftGrade.Low, BaCraftGrade.Highest)
        assertEquals(mixed, BaCraftSlot(grades = mixed).normalized(BaCraftFunction.Generate).grades)
    }

    @Test
    fun `custom duration overrides the computed sum`() {
        val slot =
            BaCraftSlot(
                startedAtMs = 1_000L,
                grades = listOf(BaCraftGrade.Low),
                customDurationMs = 7L * HOUR,
            )
        assertEquals(30L * MINUTE, slot.computedDurationMs())
        assertEquals(7L * HOUR, slot.effectiveDurationMs())
        assertEquals(1_000L + 7L * HOUR, slot.endAtMs())
    }

    @Test
    fun `custom duration is bounded and a corrupt value cannot schedule months out`() {
        val slot = BaCraftSlot(customDurationMs = 400L * 24L * HOUR)
        assertEquals(BA_CRAFT_MAX_DURATION_MS, slot.normalized(BaCraftFunction.Fusion).customDurationMs)
    }

    @Test
    fun `an idle slot has no duration and no end`() {
        val noStart = BaCraftSlot(grades = listOf(BaCraftGrade.Low))
        assertFalse(noStart.isActive())
        assertEquals(0L, noStart.endAtMs())
        assertEquals(0L, noStart.remainingMs(nowMs = 5L))

        val noGrades = BaCraftSlot(startedAtMs = 1_000L)
        assertFalse(noGrades.isActive())
        assertEquals(0L, noGrades.endAtMs())
    }

    @Test
    fun `remaining clamps at zero once elapsed`() {
        val slot = BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.Low))
        assertEquals(10L * MINUTE, slot.remainingMs(nowMs = START + 20L * MINUTE))
        assertEquals(0L, slot.remainingMs(nowMs = START + 31L * MINUTE))
        assertTrue(slot.isComplete(nowMs = START + 30L * MINUTE))
        assertFalse(slot.isComplete(nowMs = START + 30L * MINUTE - 1L))
    }

    @Test
    fun `state normalizes to exactly three slots per function`() {
        val state = BaCraftState(generate = listOf(BaCraftSlot()), fusion = emptyList()).normalized()
        assertEquals(BA_CRAFT_SLOT_COUNT, state.generate.size)
        assertEquals(BA_CRAFT_SLOT_COUNT, state.fusion.size)
        assertFalse(state.hasActiveSlot())
    }

    @Test
    fun `next completion is the earliest future end across both functions`() {
        val state =
            BaCraftState()
                .withSlotAt(
                    BaCraftFunction.Generate,
                    0,
                    BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.Highest)),
                )
                .withSlotAt(
                    BaCraftFunction.Fusion,
                    2,
                    BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.Normal)),
                )
        // Fusion slot 2 ends at 1:30, generate slot 0 at 6:00.
        assertEquals(START + 90L * MINUTE, state.nextCompletionAtMs(nowMs = START + MINUTE))
        assertTrue(state.hasActiveSlot())
    }

    @Test
    fun `an elapsed slot is a completion rather than a future alarm`() {
        val state =
            BaCraftState().withSlotAt(
                BaCraftFunction.Fusion,
                1,
                BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.Low)),
            )
        val nowMs = START + 45L * MINUTE
        assertNull(state.nextCompletionAtMs(nowMs))
        val completions = state.completions(nowMs)
        assertEquals(1, completions.size)
        assertEquals(BaCraftFunction.Fusion, completions.single().function)
        assertEquals(1, completions.single().index)
        assertEquals(START + 30L * MINUTE, completions.single().endAtMs)
    }

    @Test
    fun `writing a slot out of range is ignored`() {
        val state = BaCraftState().normalized()
        val slot = BaCraftSlot(startedAtMs = 1L, grades = listOf(BaCraftGrade.Low))
        assertEquals(state, state.withSlotAt(BaCraftFunction.Generate, 3, slot))
        assertEquals(state, state.withSlotAt(BaCraftFunction.Generate, -1, slot))
    }

    @Test
    fun `writing a slot normalizes it and fills the rest`() {
        val state =
            BaCraftState().withSlotAt(
                BaCraftFunction.Fusion,
                0,
                BaCraftSlot(
                    startedAtMs = -5L,
                    grades = List(9) { BaCraftGrade.High },
                    label = "  a very long crafting note that overflows  ",
                ),
            )
        val slot = state.slotAt(BaCraftFunction.Fusion, 0)
        assertEquals(0L, slot.startedAtMs)
        assertEquals(5, slot.grades.size)
        assertEquals("a very long crafting not", slot.label)
        assertEquals(BA_CRAFT_SLOT_COUNT, state.fusion.size)
    }

    @Test
    fun `appending grades to a generate slot accumulates up to three mixed items`() {
        var slot = BaCraftSlot()
        slot = slot.withAppendedGrade(BaCraftFunction.Generate, BaCraftGrade.High)
        assertEquals(3L * HOUR, slot.computedDurationMs())
        slot = slot.withAppendedGrade(BaCraftFunction.Generate, BaCraftGrade.Highest)
        assertEquals(9L * HOUR, slot.computedDurationMs())
        slot = slot.withAppendedGrade(BaCraftFunction.Generate, BaCraftGrade.Low)
        assertEquals(9L * HOUR + 30L * MINUTE, slot.computedDurationMs())
        // Fourth tap is a no-op: the game opens at most three nodes.
        slot = slot.withAppendedGrade(BaCraftFunction.Generate, BaCraftGrade.Low)
        assertEquals(3, slot.grades.size)
        assertEquals(9L * HOUR + 30L * MINUTE, slot.computedDurationMs())
    }

    @Test
    fun `appending the same grade to a fusion slot raises the count to five`() {
        var slot = BaCraftSlot()
        repeat(7) { slot = slot.withAppendedGrade(BaCraftFunction.Fusion, BaCraftGrade.Highest) }
        assertEquals(5, slot.grades.size)
        assertEquals(30L * HOUR, slot.computedDurationMs())
    }

    @Test
    fun `appending a different grade to a fusion slot re-bases the recipe and keeps the count`() {
        var slot = BaCraftSlot()
        repeat(3) { slot = slot.withAppendedGrade(BaCraftFunction.Fusion, BaCraftGrade.High) }
        assertEquals(9L * HOUR, slot.computedDurationMs())
        slot = slot.withAppendedGrade(BaCraftFunction.Fusion, BaCraftGrade.Low)
        assertEquals(List(3) { BaCraftGrade.Low }, slot.grades)
        assertEquals(90L * MINUTE, slot.computedDurationMs())
    }

    @Test
    fun `removing the last item walks the total back down`() {
        var slot =
            BaCraftSlot(grades = listOf(BaCraftGrade.High, BaCraftGrade.Highest))
        slot = slot.withoutLastGrade(BaCraftFunction.Generate)
        assertEquals(3L * HOUR, slot.computedDurationMs())
        slot = slot.withoutLastGrade(BaCraftFunction.Generate)
        assertTrue(slot.grades.isEmpty())
        // Removing past empty stays empty rather than throwing.
        assertTrue(slot.withoutLastGrade(BaCraftFunction.Generate).grades.isEmpty())
    }

    @Test
    fun `starting anchors the slot and an empty slot cannot start`() {
        val loaded =
            BaCraftSlot(grades = listOf(BaCraftGrade.Low)).started(nowMs = START)
        assertEquals(START, loaded.startedAtMs)
        assertTrue(loaded.isActive())

        val empty = BaCraftSlot().started(nowMs = START)
        assertEquals(0L, empty.startedAtMs)
        assertFalse(empty.isActive())
    }

    @Test
    fun `starting with only a custom duration works`() {
        val slot = BaCraftSlot(customDurationMs = 2L * HOUR).started(nowMs = START)
        assertTrue(slot.isActive())
        assertEquals(START + 2L * HOUR, slot.endAtMs())
    }

    @Test
    fun `custom duration round trips through minutes text`() {
        assertEquals(90L * MINUTE, baCraftCustomDurationMsFromMinutes("90"))
        assertEquals("90", baCraftCustomDurationMinutesText(90L * MINUTE))
        // Blank clears the override instead of meaning zero minutes.
        assertEquals(0L, baCraftCustomDurationMsFromMinutes("   "))
        assertEquals("", baCraftCustomDurationMinutesText(0L))
        // Garbage and negatives cannot produce a bogus alarm.
        assertEquals(0L, baCraftCustomDurationMsFromMinutes("abc"))
        assertEquals(0L, baCraftCustomDurationMsFromMinutes("-30"))
        assertEquals(BA_CRAFT_MAX_DURATION_MS, baCraftCustomDurationMsFromMinutes("999999"))
    }
}
