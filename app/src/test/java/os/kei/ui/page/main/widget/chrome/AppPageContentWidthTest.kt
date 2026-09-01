package os.kei.ui.page.main.widget.chrome

import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AppPageContentWidthTest {
    /**
     * The half that must never regress: on a phone this is identically zero.
     *
     * Every widget that adds the gutter — list content padding, the top-end action overlay, the tabbed bottom
     * chrome, Home's own padding — adds it unconditionally, so if this returned anything non-zero below the
     * cap it would shift the entire phone layout at once. The widths below are the real ones: 411dp is a
     * Pixel-class portrait, 426dp is the phone AVD (1280x2856 at density 480), 440dp is the widest phone this
     * app can install on.
     */
    @Test
    fun `a phone gets no gutter at all`() {
        listOf(360.dp, 411.dp, 426.dp, 440.dp).forEach { width ->
            assertEquals(0.dp, appPageSideGutterFor(width), "width=$width")
        }
    }

    /**
     * Measured Pad AVD geometry, both orientations — 2560x1600 at density 320.
     *
     * The point of the numbers is that the *content* is 720dp in both: 800 - 2*40 and 1280 - 2*280. Rotating
     * the tablet moves the gutter, never the row.
     */
    @Test
    fun `the pad gets the same content column in both orientations`() {
        val portrait = appPageSideGutterFor(800.dp)
        val landscape = appPageSideGutterFor(1280.dp)

        assertEquals(40.dp, portrait)
        assertEquals(280.dp, landscape)
        assertEquals(AppPageContentMaxWidth, 800.dp - portrait * 2f)
        assertEquals(AppPageContentMaxWidth, 1280.dp - landscape * 2f)
    }

    /** Exactly at the cap is the boundary, and it belongs to the no-gutter side. */
    @Test
    fun `the cap itself produces no gutter`() {
        assertEquals(0.dp, appPageSideGutterFor(AppPageContentMaxWidth))
        assertEquals(0.dp, appPageSideGutterFor(AppPageContentMaxWidth - 1.dp))
        assertEquals(0.5.dp, appPageSideGutterFor(AppPageContentMaxWidth + 1.dp))
    }

    /**
     * The device gate, which is the half that keeps a phone out however it is held.
     *
     * A phone turned on its side is ~817dp wide and would sail past any width-only test; its *smallest* width
     * is still ~375dp. A fold separates the same way: outer screen out, inner screen in.
     */
    @Test
    fun `only a tablet or an unfolded fold is a large screen`() {
        listOf(360.dp, 375.dp, 411.dp, 440.dp).forEach { smallestWidth ->
            assertFalse(appLargeScreenDeviceFor(smallestWidth), "smallestWidth=$smallestWidth")
        }
        listOf(600.dp, 673.dp, 800.dp).forEach { smallestWidth ->
            assertTrue(appLargeScreenDeviceFor(smallestWidth), "smallestWidth=$smallestWidth")
        }
    }

    /** A phone never gets two columns, at any width it can ever be laid out in. */
    @Test
    fun `a phone stays on one column even in landscape`() {
        listOf(360.dp, 440.dp, 817.dp, 952.dp).forEach { width ->
            assertEquals(1, appPageColumnCountFor(width, largeScreenDevice = false), "width=$width")
        }
    }

    /**
     * On a large-screen device the question becomes "is there room now", and the answer is the pane floor.
     *
     * 760dp is two [AppPaneMinWidth] panes. Below it a second column would be narrower than the narrowest
     * phone this app is drawn for, so a Pad in a halved split-screen window goes back to one.
     */
    @Test
    fun `a large screen takes two columns only when there is room`() {
        assertEquals(1, appPageColumnCountFor(600.dp, largeScreenDevice = true))
        assertEquals(1, appPageColumnCountFor(759.dp, largeScreenDevice = true))
        assertEquals(2, appPageColumnCountFor(AppDualPaneMinWidth, largeScreenDevice = true))
        assertEquals(2, appPageColumnCountFor(800.dp, largeScreenDevice = true))
        assertEquals(2, appPageColumnCountFor(1280.dp, largeScreenDevice = true))
    }

    /** Two columns get twice the cap plus the gap between them, so each lands near a full column. */
    @Test
    fun `the two-column cap is two columns wide`() {
        assertEquals(AppPageContentMaxWidth, appPageContentMaxWidthFor(1))
        assertEquals(
            AppPageContentMaxWidth * 2f + AppPageColumnGap,
            appPageContentMaxWidthFor(2),
        )
    }

    /**
     * A two-column page fills the panel, so the gutter reaches zero and the floor is what keeps it off the
     * bezel. The single-column cap never gets there on a large window, so it never sees the floor.
     */
    @Test
    fun `a wide column keeps an edge inset once the gutter runs out`() {
        val wideCap = appPageContentMaxWidthFor(2)
        assertEquals(
            AppWideContentEdgeInset,
            appPageSideGutterFor(1280.dp, wideCap, minimumGutter = AppWideContentEdgeInset),
        )
        // Still centred once the panel is wider than the cap, rather than pinned at the floor.
        assertEquals(
            22.dp,
            appPageSideGutterFor(wideCap + 44.dp, wideCap, minimumGutter = AppWideContentEdgeInset),
        )
        // And a phone is untouched: no floor is ever asked for at one column.
        assertEquals(0.dp, appPageSideGutterFor(426.dp, appPageContentMaxWidthFor(1)))
    }

    /**
     * A narrowed window is the case that would break if this read the display instead of the window.
     *
     * Split-screen on a tablet hands the app something phone-shaped. The gutter has to vanish then, or the
     * content column would be centred against a screen the app does not have and the visible half would be
     * padded off-centre.
     */
    @Test
    fun `a split-screen window falls back to no gutter`() {
        assertEquals(0.dp, appPageSideGutterFor(640.dp))
        assertEquals(0.dp, appPageSideGutterFor(400.dp))
    }
}
