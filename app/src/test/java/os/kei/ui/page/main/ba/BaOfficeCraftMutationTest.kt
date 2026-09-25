package os.kei.ui.page.main.ba

import org.junit.Test
import os.kei.ui.page.main.ba.support.BA_CRAFT_SLOT_COUNT
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.ba.support.BaCraftGrade
import os.kei.ui.page.main.ba.support.BaCraftSlot
import os.kei.ui.page.main.ba.support.BaCraftState
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.endAtMs
import os.kei.ui.page.main.ba.support.isActive
import os.kei.ui.page.main.ba.support.slotAt
import os.kei.ui.page.main.ba.support.withSlotAt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60L * 1000L
private const val HOUR = 60L * MINUTE
private const val NOW = 1_700_000_000_000L

class BaOfficeCraftMutationTest {
    private fun controller(craft: BaCraftState = BaCraftState()): BaOfficeController =
        BaOfficeController(
            snapshot = BaPageSnapshot(craft = craft),
            clock = FixedBaOfficeClock(NOW),
        )

    @Test
    fun `the holder starts normalized so the controller does not report dirty forever`() {
        val office = controller()
        // Padded to the real slot count even though the snapshot carried empty lists...
        assertEquals(BA_CRAFT_SLOT_COUNT, office.craft.generate.size)
        assertEquals(BA_CRAFT_SLOT_COUNT, office.craft.fusion.size)
        // ...and the equality chain still recognises that as matching the snapshot it came from.
        assertTrue(office.matchesSnapshot(BaPageSnapshot(craft = BaCraftState())))
    }

    @Test
    fun `starting a slot anchors it to the clock and returns state to persist`() {
        val office = controller()
        val next =
            office.startCraftSlot(
                function = BaCraftFunction.Generate,
                index = 1,
                slot = BaCraftSlot(grades = listOf(BaCraftGrade.High)),
            )
        val persisted = assertNotNull(next)
        val slot = persisted.slotAt(BaCraftFunction.Generate, 1)
        assertEquals(NOW, slot.startedAtMs)
        assertEquals(NOW + 3L * HOUR, slot.endAtMs())
        // The holder moved too, so the card re-renders without waiting for the store round trip.
        assertEquals(persisted, office.craft)
    }

    @Test
    fun `starting a slot with nothing selected is refused`() {
        val office = controller()
        assertNull(
            office.startCraftSlot(
                function = BaCraftFunction.Generate,
                index = 0,
                slot = BaCraftSlot(),
            ),
        )
        assertFalse(office.craft.slotAt(BaCraftFunction.Generate, 0).isActive())
    }

    @Test
    fun `a no-op write returns null so the caller skips persisting and re-arming the alarm`() {
        val office = controller()
        val slot = BaCraftSlot(startedAtMs = NOW, grades = listOf(BaCraftGrade.Low))
        assertNotNull(office.writeCraftSlot(BaCraftFunction.Fusion, 0, slot))
        // Same value again: nothing changed, so nothing should be written or rescheduled.
        assertNull(office.writeCraftSlot(BaCraftFunction.Fusion, 0, slot))
    }

    @Test
    fun `clearing an idle slot is also a no-op`() {
        val office = controller()
        assertNull(office.clearCraftSlot(BaCraftFunction.Generate, 2))
    }

    @Test
    fun `clearing a loaded slot empties just that slot`() {
        val loaded =
            BaCraftState()
                .withSlotAt(
                    BaCraftFunction.Generate,
                    0,
                    BaCraftSlot(startedAtMs = NOW, grades = listOf(BaCraftGrade.Low)),
                )
                .withSlotAt(
                    BaCraftFunction.Generate,
                    1,
                    BaCraftSlot(startedAtMs = NOW, grades = listOf(BaCraftGrade.High)),
                )
        val office = controller(loaded)
        val next = assertNotNull(office.clearCraftSlot(BaCraftFunction.Generate, 0))
        assertFalse(next.slotAt(BaCraftFunction.Generate, 0).isActive())
        assertTrue(next.slotAt(BaCraftFunction.Generate, 1).isActive())
    }

    @Test
    fun `craft reaches the exposed state so the card can read it`() {
        val office = controller()
        office.startCraftSlot(
            function = BaCraftFunction.Fusion,
            index = 2,
            slot = BaCraftSlot(grades = List(5) { BaCraftGrade.Highest }),
        )
        val state = office.state()
        assertEquals(NOW + 30L * HOUR, state.craft.slotAt(BaCraftFunction.Fusion, 2).endAtMs())
    }

    @Test
    fun `applying a snapshot replaces the slots`() {
        val office = controller()
        office.startCraftSlot(
            function = BaCraftFunction.Generate,
            index = 0,
            slot = BaCraftSlot(grades = listOf(BaCraftGrade.Low)),
        )
        assertTrue(office.craft.slotAt(BaCraftFunction.Generate, 0).isActive())
        office.applySnapshot(BaPageSnapshot(craft = BaCraftState()))
        assertFalse(office.craft.slotAt(BaCraftFunction.Generate, 0).isActive())
    }
}
