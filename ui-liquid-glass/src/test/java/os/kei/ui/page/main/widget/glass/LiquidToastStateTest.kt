package os.kei.ui.page.main.widget.glass

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [LiquidToastState], the pure state machine behind the liquid glass toast host.
 *
 * These cover the original "stuck toast" regression (identical rapid toasts leaving a slot stuck
 * forever) and the stacking UX: up to 2 visible, FIFO queue with newer-favoring overflow, and
 * clean promotion/dismissal.
 */
class LiquidToastStateTest {

    private fun LiquidToastState.messages(): List<String> = visibleSlots.map { it.data.message }

    @Test
    fun showThenDismissClearsTheToast() {
        val state = LiquidToastState()
        state.show("A")
        assertEquals(listOf("A"), state.messages())

        state.dismiss(state.visibleSlots.first().token)
        assertTrue(state.visibleSlots.isEmpty())
    }

    @Test
    fun stacksUpToTwoVisibleAndQueuesTheRest() {
        val state = LiquidToastState()
        state.show("A")
        state.show("B")
        state.show("C")

        // Only two are visible; the third waits in the backlog.
        assertEquals(listOf("A", "B"), state.messages())
        assertTrue(state.hasBacklog)
    }

    @Test
    fun dismissingAVisibleToastPromotesTheQueuedOne() {
        val state = LiquidToastState()
        state.show("A")
        state.show("B")
        state.show("C")

        state.dismiss(state.visibleSlots.first { it.data.message == "A" }.token)

        assertEquals(listOf("B", "C"), state.messages())
        assertFalse(state.hasBacklog)
    }

    /**
     * Core of the stuck-toast fix: a repeated message shown again *after* the first cleared must
     * receive a brand-new unique token, otherwise the host's token-keyed timer never re-arms.
     */
    @Test
    fun repeatedMessageAfterDismissGetsAFreshUniqueToken() {
        val state = LiquidToastState()
        state.show("Saved")
        val firstToken = state.visibleSlots.single().token

        state.dismiss(firstToken)
        state.show("Saved")
        val secondToken = state.visibleSlots.single().token

        assertNotEquals(firstToken, secondToken)
    }

    @Test
    fun identicalMessageAlreadyVisibleIsCollapsed() {
        val state = LiquidToastState()
        state.show("Copied")
        state.show("Copied")

        assertEquals(listOf("Copied"), state.messages())
        assertFalse(state.hasBacklog)
    }

    @Test
    fun identicalMessageMatchingLastQueuedIsCollapsed() {
        val state = LiquidToastState()
        state.show("A")
        state.show("B")
        state.show("C") // queued
        state.show("C") // duplicate of last queued -> ignored

        // Promote the queue and confirm only one "C" ever surfaces.
        state.dismiss(state.visibleSlots.first { it.data.message == "A" }.token)
        state.dismiss(state.visibleSlots.first { it.data.message == "B" }.token)
        assertEquals(listOf("C"), state.messages())
        assertFalse(state.hasBacklog)
    }

    /**
     * Burst overflow: capacity is 2 visible + 4 queued = 6. A 7th distinct toast must evict the
     * oldest *waiting* one (favoring newer messages), never displace what's already on screen.
     */
    @Test
    fun overflowEvictsOldestQueuedAndKeepsNewest() {
        val state = LiquidToastState()
        // A,B visible; C,D,E,F queued (queue full at 4).
        listOf("A", "B", "C", "D", "E", "F").forEach { state.show(it) }
        // G overflows -> oldest queued (C) is evicted to make room for G.
        state.show("G")

        // Drain everything and record the order things actually surfaced.
        val surfaced = mutableListOf<String>()
        repeat(8) {
            val visible = state.visibleSlots
            if (visible.isEmpty()) return@repeat
            visible.forEach { slot ->
                if (slot.data.message !in surfaced) surfaced += slot.data.message
            }
            state.dismiss(visible.first().token)
        }

        assertTrue("A" in surfaced && "B" in surfaced)
        assertTrue("G" in surfaced, "newest toast should survive overflow")
        assertFalse("C" in surfaced, "oldest queued toast should be evicted")
    }

    @Test
    fun dismissUnknownTokenIsNoOp() {
        val state = LiquidToastState()
        state.show("A")
        val before = state.visibleSlots

        state.dismiss(999_999L)

        assertEquals(before.map { it.token }, state.visibleSlots.map { it.token })
    }

    @Test
    fun dismissAllClearsVisibleAndQueued() {
        val state = LiquidToastState()
        listOf("A", "B", "C", "D").forEach { state.show(it) }
        assertTrue(state.hasBacklog)

        state.dismissAll()

        assertTrue(state.visibleSlots.isEmpty())
        assertFalse(state.hasBacklog)
    }
}
