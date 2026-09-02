package os.kei.ui.page.main.widget.chrome

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * A self-measured bar keeps the same share of the window on every device.
 *
 * The first version of this took a fixed 96dp per tab, read off one 426dp AVD. Fixed dp is consistent in
 * physical size, which was never the question; what differs is how much dp a device has, so the same
 * number is half the bar on one phone and nearly two thirds on another — exactly the "too small for some,
 * too big for others" split. Borrowing a filled bar's rhythm instead makes both shapes agree everywhere.
 */
class TabbedPageSizedTabWidthTest {
    @Test
    fun `the tab width follows the window, so its share of the bar holds`() {
        // Bar widths for a compact phone, the validation AVD, and a large phone: the window less the
        // page's 14dp margins.
        val shares =
            listOf(332.dp, 392.dp, 412.dp).map { bar ->
                val tab = tabbedPageSizedTabMinWidth(availableWidth = bar)
                (tab * 2).value / bar.value
            }

        // Two tabs land on just under half the bar on all three, rather than drifting with the window.
        shares.forEach { share ->
            assertTrue(share in 0.45f..0.52f, "expected about half the bar, got $share")
        }
        assertTrue(
            abs(shares.max() - shares.min()) <= 0.02f,
            "the share must not drift between devices: $shares",
        )
    }

    @Test
    fun `a narrow window shrinks the tab rather than overflowing the bar`() {
        val tab = tabbedPageSizedTabMinWidth(availableWidth = 332.dp)

        assertTrue(tab < 96.dp, "a 360dp phone gets a smaller tab than a 426dp one, got $tab")
        assertTrue(tab * 2 < 332.dp, "two tabs still have to fit, got ${tab * 2}")
    }

    @Test
    fun `a tab never falls below the square target the bar height sets`() {
        // Split-screen and other narrow frames, where proportion would hand back something untappable.
        val tab = tabbedPageSizedTabMinWidth(availableWidth = 120.dp)

        assertEquals(AppChromeTokens.floatingBottomBarOuterHeight, tab)
    }

    @Test
    fun `a wide window stops feeding the bar instead of rebuilding the slab`() {
        // A fold's inner screen, single column. Unclamped this is ~159dp a tab, and two of those are the
        // full-width bar again in a wider frame.
        val tab = tabbedPageSizedTabMinWidth(availableWidth = 645.dp)

        assertEquals(TabbedPageSizedTabMaxWidth, tab)
        assertTrue((tab * 2).value / 645f < 0.4f, "the page keeps the surplus")
    }

    @Test
    fun `the rhythm is the one a filled bar of four tabs has`() {
        val bar = 392.dp
        val filledPerTab = (bar - AppChromeTokens.floatingBottomBarHorizontalPadding * 2f) / 4f

        assertEquals(filledPerTab, tabbedPageSizedTabMinWidth(availableWidth = bar))
        assertEquals(4, TabbedPageFilledBarTabReference)
    }
}
