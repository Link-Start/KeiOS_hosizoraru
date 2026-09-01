package os.kei.ui.page.main.widget.chrome

import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AppNavigationPlacementTest {
    /** A phone keeps the bottom bar whatever the user once picked on a tablet. */
    @Test
    fun `a compact window is always a bottom bar`() {
        listOf(360.dp, 411.dp, 426.dp, 440.dp, 599.dp).forEach { width ->
            assertEquals(
                AppNavigationPlacement.Bottom,
                appNavigationPlacementFor(width, sidebarPreferred = false),
                "width=$width",
            )
            assertEquals(
                AppNavigationPlacement.Bottom,
                appNavigationPlacementFor(width, sidebarPreferred = true),
                "width=$width, preference must not reach a phone",
            )
        }
    }

    /**
     * "Consider using a tab bar first" — so a tablet with no stated preference gets the tab bar, at the top.
     */
    @Test
    fun `a regular window defaults to the top tab bar`() {
        listOf(600.dp, 775.dp, 800.dp, 930.dp, 1280.dp).forEach { width ->
            assertEquals(
                AppNavigationPlacement.Top,
                appNavigationPlacementFor(width, sidebarPreferred = false),
                "width=$width",
            )
        }
    }

    @Test
    fun `asking for the sidebar gets it wherever it fits`() {
        listOf(775.dp, 800.dp, 930.dp, 1280.dp).forEach { width ->
            assertEquals(
                AppNavigationPlacement.Sidebar,
                appNavigationPlacementFor(width, sidebarPreferred = true),
                "width=$width",
            )
        }
    }

    /**
     * The band where the window is a tablet but too narrow to spend 280dp on navigation.
     *
     * The preference is kept, not discarded — the same call with a wider window returns Sidebar again — which
     * is what makes rotating a small tablet a shape change rather than a lost setting.
     */
    @Test
    fun `a sidebar falls back to the top bar when it would starve the content`() {
        assertEquals(660.dp, AppSidebarMinWindowWidth)
        assertEquals(AppNavigationPlacement.Top, appNavigationPlacementFor(600.dp, sidebarPreferred = true))
        assertEquals(AppNavigationPlacement.Top, appNavigationPlacementFor(659.dp, sidebarPreferred = true))
        assertEquals(AppNavigationPlacement.Sidebar, appNavigationPlacementFor(660.dp, sidebarPreferred = true))

        assertFalse(appSidebarAvailableAt(640.dp))
        assertTrue(appSidebarAvailableAt(800.dp))
    }

    /** Whatever the sidebar leaves behind is never below the floor that justified allowing it. */
    @Test
    fun `the content column always survives the sidebar`() {
        listOf(660.dp, 775.dp, 800.dp, 930.dp, 1280.dp).forEach { width ->
            val content = appNavigationContentWidthFor(width, AppNavigationPlacement.Sidebar)
            assertTrue(content >= AppPaneMinWidth, "width=$width left $content")
        }
    }

    /** A floating bar takes no width: the HIG asks for content to run underneath it, not beside it. */
    @Test
    fun `a floating bar does not narrow the content`() {
        assertEquals(1280.dp, appNavigationContentWidthFor(1280.dp, AppNavigationPlacement.Top))
        assertEquals(426.dp, appNavigationContentWidthFor(426.dp, AppNavigationPlacement.Bottom))
    }

    /**
     * The collision found on the Pad at 650dp: a centred bar and a trailing toolbar in the same row.
     *
     * 178dp is the toolbar's reserve, doubled because a centred element is only clear of one side if it is
     * equally clear of the other. At 1280dp the clamp is inactive (924 > the 460dp cap); at 650dp it bites.
     */
    @Test
    fun `the centred tab bar stays clear of the trailing toolbar`() {
        assertEquals(924.dp, appTopBarNavigationMaxWidth(1280.dp))
        assertEquals(294.dp, appTopBarNavigationMaxWidth(650.dp))
        // At the narrowest regular window the reserve still leaves 244dp, so the 240dp floor is a backstop
        // rather than something the placement rule can actually reach -- Top placement never goes below 600dp.
        assertEquals(244.dp, appTopBarNavigationMaxWidth(600.dp))
        assertEquals(240.dp, appTopBarNavigationMaxWidth(400.dp))
    }

    @Test
    fun `the title yields when the row cannot hold all three`() {
        // Pad landscape: a 388dp bar leaves 892dp, far past the 528dp the title and toolbar need.
        assertTrue(appTopRowFitsTitle(availableWidth = 1280.dp, barWidth = 388.dp))
        // 650dp with a 294dp bar leaves 356dp -- not enough, so the title goes rather than overlapping.
        assertFalse(appTopRowFitsTitle(availableWidth = 650.dp, barWidth = 294.dp))
    }

    /**
     * Only the bottom bar earns the scroll-away, because only it sits on top of the content.
     *
     * The top bar shares the title row and costs no vertical space, and the sidebar is beside the content
     * rather than over it, so in both cases hiding buys nothing and loses what the HIG asks for.
     */
    @Test
    fun `only the bottom placement collapses on scroll`() {
        assertTrue(appNavigationCollapsesOnScroll(AppNavigationPlacement.Bottom))
        assertFalse(appNavigationCollapsesOnScroll(AppNavigationPlacement.Top))
        assertFalse(appNavigationCollapsesOnScroll(AppNavigationPlacement.Sidebar))
    }

    @Test
    fun `scrolling away hides only the bottom bar`() {
        assertFalse(appNavigationVisible(AppNavigationPlacement.Bottom, scrolledAway = true))
        assertTrue(appNavigationVisible(AppNavigationPlacement.Bottom, scrolledAway = false))
        assertTrue(appNavigationVisible(AppNavigationPlacement.Top, scrolledAway = true))
        assertTrue(appNavigationVisible(AppNavigationPlacement.Sidebar, scrolledAway = true))
    }

    /**
     * A pushed route owns its top row, so both ends of it come in with the content column.
     *
     * The numbers are the Pad AVD's real gutters. 280dp landscape is what the back button used to be adrift
     * by: measured on the device, the content column started at 294dp while the button started at 14dp.
     */
    @Test
    fun `a page's top row chrome follows the content column`() {
        listOf(AppNavigationPlacement.Bottom, AppNavigationPlacement.Sidebar).forEach { placement ->
            assertEquals(280.dp, appTopBarChromeGutterFor(placement, 280.dp), "placement=$placement")
            assertEquals(40.dp, appTopBarChromeGutterFor(placement, 40.dp), "placement=$placement")
        }
    }

    /**
     * At the top tab bar the row belongs to the app, not the page, so it spans the window at both ends.
     *
     * Keeping the gutter there pulled the trailing actions into the centred tab bar on the Pad at 1280dp,
     * and would now do the same to the leading chrome.
     */
    @Test
    fun `the app's own top row spans the window`() {
        assertEquals(0.dp, appTopBarChromeGutterFor(AppNavigationPlacement.Top, 280.dp))
        assertEquals(0.dp, appTopBarChromeGutterFor(AppNavigationPlacement.Top, 40.dp))
    }

    /** A phone has no gutter to take, so no placement moves by a pixel. */
    @Test
    fun `a phone top row is unmoved at every placement`() {
        AppNavigationPlacement.entries.forEach { placement ->
            assertEquals(0.dp, appTopBarChromeGutterFor(placement, 0.dp), "placement=$placement")
        }
    }
}
