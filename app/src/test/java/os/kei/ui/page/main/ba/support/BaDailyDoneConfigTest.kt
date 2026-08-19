package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals

private const val MINUTE = 60L * 1000L
private const val HOUR = 60L * MINUTE

class BaDailyDoneConfigTest {
    @Test
    fun `the defaults are the template that shipped before it was configurable`() {
        // The compatibility contract: an install that has never opened the editor must keep behaving the
        // way its teacher left it, so the defaults have to stay pinned to the old constants.
        val default = BaDailyDoneConfig()
        assertEquals(0, default.apRemaining)
        assertEquals(true, default.startHeadpat)
        assertEquals(true, default.startInvite1)
        assertEquals(true, default.startInvite2)
        assertEquals(BaCraftFunction.Generate, default.craftFunction)
        assertEquals(BA_DAILY_DONE_CRAFT_SLOTS, default.craftSlots)
        assertEquals(BA_DAILY_DONE_CRAFT_GRADE, default.craftGrade)
        assertEquals(1, default.craftEntriesPerSlot)
        assertEquals(3L * HOUR, default.craftSlotDurationMs())
    }

    @Test
    fun `the remainder is bounded by the storage ceiling, not by the ap limit`() {
        // Overcap is real - items and events push a pool past the natural limit - so the bound is the
        // value the store can hold rather than the teacher's configured limit.
        assertEquals(BA_AP_MAX, BaDailyDoneConfig(apRemaining = BA_AP_MAX + 500).normalized().apRemaining)
        assertEquals(0, BaDailyDoneConfig(apRemaining = -20).normalized().apRemaining)
        assertEquals(BA_AP_LIMIT_MAX, BaDailyDoneConfig(apRemaining = BA_AP_LIMIT_MAX).normalized().apRemaining)
    }

    @Test
    fun `the slot count cannot exceed the slots a function actually has`() {
        assertEquals(
            BA_CRAFT_SLOT_COUNT,
            BaDailyDoneConfig(craftSlots = BA_CRAFT_SLOT_COUNT + 4).normalized().craftSlots,
        )
        // Zero is a real choice - "leave my crafts alone" - so it survives normalization.
        assertEquals(0, BaDailyDoneConfig(craftSlots = 0).normalized().craftSlots)
        assertEquals(0, BaDailyDoneConfig(craftSlots = -1).normalized().craftSlots)
    }

    @Test
    fun `the entry count is clamped against the chosen function, not a shared maximum`() {
        // The two functions cap differently: three opened Generate nodes, five Fusion copies. Clamping to
        // one shared number would either forbid a real Fusion craft or allow an impossible Generate one.
        assertEquals(
            BA_CRAFT_GENERATE_MAX_ENTRIES,
            BaDailyDoneConfig(
                craftFunction = BaCraftFunction.Generate,
                craftEntriesPerSlot = 9,
            ).normalized().craftEntriesPerSlot,
        )
        assertEquals(
            BA_CRAFT_FUSION_MAX_ENTRIES,
            BaDailyDoneConfig(
                craftFunction = BaCraftFunction.Fusion,
                craftEntriesPerSlot = 9,
            ).normalized().craftEntriesPerSlot,
        )
    }

    @Test
    fun `a slot always produces at least one item`() {
        // Zero items would compute a zero duration, and a zero-duration slot cannot start - the whole
        // craft section would silently do nothing. "Load no slots" is craftSlots, not an empty slot.
        assertEquals(1, BaDailyDoneConfig(craftEntriesPerSlot = 0).normalized().craftEntriesPerSlot)
        assertEquals(1, BaDailyDoneConfig(craftEntriesPerSlot = -3).normalized().craftEntriesPerSlot)
    }

    @Test
    fun `switching a five copy fusion template to generate clamps the count down`() {
        val fusion =
            BaDailyDoneConfig(
                craftFunction = BaCraftFunction.Fusion,
                craftEntriesPerSlot = BA_CRAFT_FUSION_MAX_ENTRIES,
            ).normalized()
        assertEquals(BA_CRAFT_FUSION_MAX_ENTRIES, fusion.craftEntriesPerSlot)

        val moved = fusion.copy(craftFunction = BaCraftFunction.Generate).normalized()
        assertEquals(BA_CRAFT_GENERATE_MAX_ENTRIES, moved.craftEntriesPerSlot)
    }

    @Test
    fun `the slot duration is the grade summed once per produced item`() {
        assertEquals(
            30L * MINUTE,
            BaDailyDoneConfig(craftGrade = BaCraftGrade.Low, craftEntriesPerSlot = 1).craftSlotDurationMs(),
        )
        assertEquals(
            6L * HOUR,
            BaDailyDoneConfig(craftGrade = BaCraftGrade.Highest, craftEntriesPerSlot = 1).craftSlotDurationMs(),
        )
        assertEquals(
            18L * HOUR,
            BaDailyDoneConfig(craftGrade = BaCraftGrade.Highest, craftEntriesPerSlot = 3).craftSlotDurationMs(),
        )
        // The duration is computed from the *normalized* count, so an out-of-range value cannot inflate it.
        assertEquals(
            18L * HOUR,
            BaDailyDoneConfig(craftGrade = BaCraftGrade.Highest, craftEntriesPerSlot = 9).craftSlotDurationMs(),
        )
    }

    @Test
    fun `the grades a loaded slot receives repeat the one chosen grade`() {
        val grades =
            BaDailyDoneConfig(
                craftFunction = BaCraftFunction.Fusion,
                craftGrade = BaCraftGrade.Normal,
                craftEntriesPerSlot = 4,
            ).craftSlotGrades()
        assertEquals(List(4) { BaCraftGrade.Normal }, grades)
        // Which is exactly the invariant a Fusion slot enforces for itself.
        assertEquals(grades, BaCraftSlot(grades = grades).normalized(BaCraftFunction.Fusion).grades)
    }
}
