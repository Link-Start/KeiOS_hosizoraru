package os.kei.ui.page.main.widget.glass

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Unit tests for [resolveToastDisplayLimit] — the burst-vs-readability tradeoff for the liquid
 * glass toast auto-dismiss timer.
 *
 * Background: AndroidX has no "backlog acceleration"; system Toast keeps a fixed duration and drops
 * overflow, and Compose Snackbar only ever EXTENDS time for accessibility. Our toast adds backlog
 * acceleration for snappier bursts, so these tests pin down the guard rails that keep it from
 * flashing unreadable blips.
 */
class LiquidToastDisplayLimitTest {
    @Test
    fun expeditingOnlyShortensTowardTheBacklogTargetAndNeverBelowTheReadableFloor() {
        data class Case(val label: String, val baseMs: Int, val expedited: Boolean, val expectedMs: Int)
        listOf(
            Case("not expedited keeps the base verbatim", baseMs = 2800, expedited = false, expectedMs = 2800),
            // A screen reader's extended base is honoured in full when acceleration is off for a11y users.
            Case("not expedited respects an accessibility-extended base", baseMs = 20_000, expedited = false, expectedMs = 20_000),
            Case("expedited shortens the default base to the backlog target", baseMs = 2800, expedited = true, expectedMs = 1400),
            // Acceleration only ever shortens: an already-short base is kept, not extended.
            Case("expedited never lengthens a short base", baseMs = 1200, expedited = true, expectedMs = 1200),
            // A pathologically tiny base is lifted rather than flashing away instantly.
            Case("expedited clamps a tiny base up to the floor", baseMs = 200, expedited = true, expectedMs = 1100),
        ).forEach { case ->
            assertEquals(
                case.expectedMs.milliseconds,
                resolveToastDisplayLimit(base = case.baseMs.milliseconds, expedited = case.expedited),
                case.label,
            )
        }

        // Even an absurdly long base lands between the readable floor and the backlog target.
        val longBase = resolveToastDisplayLimit(base = 9000.milliseconds, expedited = true)
        assertTrue(longBase >= 1100.milliseconds, "expedited limit must stay above the readable floor, got $longBase")
        assertTrue(longBase <= 1400.milliseconds, "expedited limit must not exceed the backlog target, got $longBase")
    }
}
