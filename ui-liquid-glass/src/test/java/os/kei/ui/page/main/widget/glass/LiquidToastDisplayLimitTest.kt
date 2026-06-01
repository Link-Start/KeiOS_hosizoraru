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
    fun notExpeditedKeepsBaseDurationVerbatim() {
        val base = 2800.milliseconds
        assertEquals(base, resolveToastDisplayLimit(base = base, expedited = false))
    }

    @Test
    fun notExpeditedRespectsAccessibilityExtendedDuration() {
        // When a screen reader extends the base well beyond the default, a non-expedited toast must
        // honor it fully (this is the path taken when acceleration is disabled for a11y users).
        val extended = 20_000.milliseconds
        assertEquals(extended, resolveToastDisplayLimit(base = extended, expedited = false))
    }

    @Test
    fun expeditedShortensTowardBacklogTarget() {
        // Default 2800ms base, expedited -> shortened to the 1400ms backlog target (still >= floor).
        val base = 2800.milliseconds
        val limit = resolveToastDisplayLimit(base = base, expedited = true)
        assertEquals(1400.milliseconds, limit)
    }

    @Test
    fun expeditedNeverDropsBelowReadableFloor() {
        // Even with an absurdly long base, the expedited result is clamped to the readable floor,
        // never below it — a burst can't flash a toast away before it can be seen.
        val limit = resolveToastDisplayLimit(base = 9000.milliseconds, expedited = true)
        assertTrue(limit >= 1100.milliseconds, "expedited limit must stay above the readable floor")
        // And it must not exceed the backlog target either (acceleration only shortens).
        assertTrue(limit <= 1400.milliseconds)
    }

    @Test
    fun expeditedNeverLengthensAShortBase() {
        // If the base is already shorter than the backlog target, acceleration must not lengthen it;
        // it only ever shortens. Result is the base, clamped up to the floor.
        val base = 1200.milliseconds
        val limit = resolveToastDisplayLimit(base = base, expedited = true)
        assertEquals(1200.milliseconds, limit, "should keep the already-short base, not extend it")
    }

    @Test
    fun expeditedClampsTinyBaseUpToFloor() {
        // A pathologically tiny base is lifted to the readable floor rather than flashing instantly.
        val limit = resolveToastDisplayLimit(base = 200.milliseconds, expedited = true)
        assertEquals(1100.milliseconds, limit)
    }
}
