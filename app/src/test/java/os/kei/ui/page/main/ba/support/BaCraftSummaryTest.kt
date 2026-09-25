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

private fun slot(
    grade: BaCraftGrade,
    startedAtMs: Long = START,
): BaCraftSlot = BaCraftSlot(startedAtMs = startedAtMs, grades = listOf(grade))

/**
 * The one line the Craft Chamber card falls back to when it is collapsed.
 *
 * Collapsing is only acceptable if it is non-lossy, so these pin the two facts that carry the card's
 * meaning without its six rows: how many crafts are waiting to be collected, and when the next one
 * lands.
 */
class BaCraftSummaryTest {
    @Test
    fun `an empty chamber is idle`() {
        val summary = BaCraftState().summary(START)

        assertTrue(summary.isIdle)
        assertEquals(0, summary.runningCount)
        assertEquals(0, summary.readyCount)
        assertNull(summary.nextCompletionAtMs)
    }

    @Test
    fun `a slot with grades but no start is idle, not running`() {
        val state = BaCraftState(generate = listOf(BaCraftSlot(grades = listOf(BaCraftGrade.High))))

        assertTrue(state.summary(START).isIdle)
    }

    @Test
    fun `a started slot counts as running until its end`() {
        val state = BaCraftState(generate = listOf(slot(BaCraftGrade.High)))

        val running = state.summary(START + HOUR)
        assertEquals(1, running.runningCount)
        assertEquals(0, running.readyCount)
        assertFalse(running.isIdle)
    }

    @Test
    fun `an elapsed slot moves from running to ready`() {
        val state = BaCraftState(generate = listOf(slot(BaCraftGrade.High)))

        val ready = state.summary(START + 3L * HOUR)
        assertEquals(0, ready.runningCount)
        assertEquals(1, ready.readyCount)
    }

    @Test
    fun `the boundary instant counts as ready`() {
        val state = BaCraftState(generate = listOf(slot(BaCraftGrade.Low)))

        // isComplete is `now >= end`, so the exact instant is already collectable.
        assertEquals(1, state.summary(START + 30L * MINUTE).readyCount)
    }

    @Test
    fun `both functions are counted, not just generate`() {
        val state =
            BaCraftState(
                generate = listOf(slot(BaCraftGrade.Low), slot(BaCraftGrade.Highest)),
                fusion = listOf(slot(BaCraftGrade.High)),
            )

        val summary = state.summary(START + HOUR)
        // Low (30m) elapsed; High (3h) and Highest (6h) still running.
        assertEquals(1, summary.readyCount)
        assertEquals(2, summary.runningCount)
    }

    @Test
    fun `next completion is the earliest still in the future`() {
        val state =
            BaCraftState(
                generate = listOf(slot(BaCraftGrade.Highest), slot(BaCraftGrade.Normal)),
                fusion = listOf(slot(BaCraftGrade.High)),
            )

        // Normal is 1h30 — sooner than High's 3h and Highest's 6h.
        assertEquals(START + 90L * MINUTE, state.summary(START).nextCompletionAtMs)
    }

    @Test
    fun `an already-elapsed slot does not become the next completion`() {
        val state =
            BaCraftState(
                generate = listOf(slot(BaCraftGrade.Low), slot(BaCraftGrade.High)),
            )

        val summary = state.summary(START + HOUR)
        assertEquals(1, summary.readyCount)
        assertEquals(START + 3L * HOUR, summary.nextCompletionAtMs)
    }

    @Test
    fun `everything elapsed leaves no next completion`() {
        val state = BaCraftState(generate = listOf(slot(BaCraftGrade.Low), slot(BaCraftGrade.Normal)))

        val summary = state.summary(START + 12L * HOUR)
        assertEquals(2, summary.readyCount)
        assertEquals(0, summary.runningCount)
        assertNull(summary.nextCompletionAtMs)
    }

    @Test
    fun `a custom duration override drives the summary too`() {
        val state =
            BaCraftState(
                generate =
                    listOf(
                        BaCraftSlot(
                            startedAtMs = START,
                            grades = listOf(BaCraftGrade.Highest),
                            customDurationMs = 10L * MINUTE,
                        ),
                    ),
            )

        // The override wins over the summed 6h, exactly as the rows show it.
        assertEquals(START + 10L * MINUTE, state.summary(START).nextCompletionAtMs)
        assertEquals(1, state.summary(START + 11L * MINUTE).readyCount)
    }

    @Test
    fun `a full chamber counts all six slots`() {
        val full = List(BA_CRAFT_SLOT_COUNT) { slot(BaCraftGrade.High) }
        val state = BaCraftState(generate = full, fusion = full)

        assertEquals(6, state.summary(START).runningCount)
    }
}
