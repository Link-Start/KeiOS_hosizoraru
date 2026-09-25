package os.kei.ui.page.main.widget.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class AppFloatingDockBadgeTest {
    // ---- the number has to mean one thing ------------------------------------------------------

    @Test
    fun aCollapsedDockSumsEveryBadgeItHides() {
        // The reported bug: the GitHub dock showed its unread-history count expanded and its refresh
        // count collapsed. Collapsed, one button stands in for both actions, so it owes the total.
        assertEquals("7", appFloatingDockCollapsedBadgeLabel(listOf("3", "4")))
        assertEquals("3", appFloatingDockCollapsedBadgeLabel(listOf("3", null)))
    }

    @Test
    fun anAlreadyCappedInputKeepsTheSumApproximate() {
        // "99+" plus anything is still only "at least 99"; reporting "102" would invent precision the
        // input never had.
        assertEquals("99+", appFloatingDockCollapsedBadgeLabel(listOf("99+", "3")))
        assertEquals("99+", appFloatingDockCollapsedBadgeLabel(listOf("99+")))
    }

    @Test
    fun aSumPastTheCapIsCapped() {
        assertEquals("99+", appFloatingDockCollapsedBadgeLabel(listOf("60", "50")))
        assertEquals("99", appFloatingDockCollapsedBadgeLabel(listOf("60", "39")))
    }

    @Test
    fun nothingBadgedMeansNoBadge() {
        assertNull(appFloatingDockCollapsedBadgeLabel(emptyList()))
        assertNull(appFloatingDockCollapsedBadgeLabel(listOf(null, null)))
        assertNull(appFloatingDockCollapsedBadgeLabel(listOf("", "   ")))
        // Zero is not a notification.
        assertNull(appFloatingDockCollapsedBadgeLabel(listOf("0", "0")))
    }

    @Test
    fun anUncountableBadgeCollapsesToADotRatherThanALie() {
        // `AppFloatingDockAction.badgeLabel` is free-form text, so a caller can badge an action with a
        // word. There is no honest total then — but dropping the badge would hide the notification, and
        // borrowing one action's label is the exact inconsistency this function exists to prevent.
        assertEquals(APP_FLOATING_DOCK_BADGE_DOT, appFloatingDockCollapsedBadgeLabel(listOf("NEW")))
        // A number present alongside one still wins, because it can at least be summed.
        assertEquals("4", appFloatingDockCollapsedBadgeLabel(listOf("NEW", "4")))
    }

    // ---- geometry: the badge must stay inside the capsule --------------------------------------

    @Test
    fun theBadgeCornerLandsOnTheRimAndNotThroughIt() {
        // Measured failure this replaces: against a 62dp dock at 3x, the badge's right edge tracked the
        // capsule's curve (210px, then 218px, then 222px as the curve opened out) because it was aligned
        // to the bounding box and the capsule clip was shaving it.
        val host = 186f // 62dp at 3x
        val badgeW = 78f
        val badgeH = 54f
        val inlay = 4.5f
        val offset =
            appFloatingDockBadgeOffsetPx(
                hostWidthPx = host,
                hostHeightPx = host,
                badgeWidthPx = badgeW,
                badgeHeightPx = badgeH,
                inlayPx = inlay,
            )
        val centre = host / 2f
        val radius = host / 2f - inlay
        // Every corner of the badge must be inside the inscribed circle.
        listOf(
            offset.x to offset.y,
            offset.x + badgeW to offset.y,
            offset.x to offset.y + badgeH,
            offset.x + badgeW to offset.y + badgeH,
        ).forEach { (x, y) ->
            val distance = hypot(x - centre, y - centre)
            assertTrue(
                "corner ($x, $y) is ${distance - radius}px outside the r=$radius rim",
                distance <= radius + 0.5f,
            )
        }
    }

    @Test
    fun theBadgeSitsTopRightRatherThanCentred() {
        val host = 186f
        val offset =
            appFloatingDockBadgeOffsetPx(
                hostWidthPx = host,
                hostHeightPx = host,
                badgeWidthPx = 54f,
                badgeHeightPx = 54f,
                inlayPx = 4.5f,
            )
        assertTrue("expected the badge above centre, got y=${offset.y}", offset.y < host / 2f - 27f)
        assertTrue("expected the badge right of centre, got x=${offset.x}", offset.x + 54f > host / 2f)
    }

    @Test
    fun aBadgeTooLargeToInscribeStillLandsInsideTheBox() {
        // Degenerate rather than hypothetical: a long label plus a small host. Must not return a
        // negative or NaN position — an off-screen badge is worse than an imperfect one.
        val offset =
            appFloatingDockBadgeOffsetPx(
                hostWidthPx = 40f,
                hostHeightPx = 40f,
                badgeWidthPx = 90f,
                badgeHeightPx = 30f,
                inlayPx = 4.5f,
            )
        assertTrue(offset.x.isFinite() && offset.y.isFinite())
        assertTrue(offset.x >= 0f && offset.y >= 0f)
    }

    @Test
    fun aDegenerateHostDoesNotProduceANaNPosition() {
        val offset =
            appFloatingDockBadgeOffsetPx(
                hostWidthPx = Float.NaN,
                hostHeightPx = 186f,
                badgeWidthPx = 54f,
                badgeHeightPx = 54f,
                inlayPx = 4.5f,
            )
        assertEquals(0f, offset.x, 0f)
        assertEquals(0f, offset.y, 0f)
    }
}
