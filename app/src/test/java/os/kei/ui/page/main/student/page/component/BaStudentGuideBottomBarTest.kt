package os.kei.ui.page.main.student.page.component

import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The guide bar decides its width and its labels from the same number: the space one tab gets.
 *
 * It used to decide labels by counting tabs — `tabCount <= 5` — which cannot tell a 360dp phone from
 * a 1280dp panel, so the six-tab guide was iconic everywhere and its bar was stretched across the
 * whole panel to hold those six icons.
 */
class BaStudentGuideBottomBarTest {
    @Test
    fun `a phone fills the bar, because six tabs fit no other way`() {
        val metrics = guideBottomBarMetrics(availableWidth = PHONE_BAR, tabCount = 6)

        assertFalse(metrics.sizedToTabs, "there is no room to be narrower than the page")
        // 350dp of bar, less its 8dp of padding, over six tabs.
        assertEquals(57.dp, metrics.perTabWidth)
    }

    @Test
    fun `a panel sizes the bar to its tabs instead of stretching six icons across it`() {
        val metrics = guideBottomBarMetrics(availableWidth = PAD_BAR, tabCount = 6)

        assertTrue(metrics.sizedToTabs)
        // The per-tab rhythm a filled bar settles on, capped -- see tabbedPageSizedTabMinWidth.
        assertEquals(120.dp, metrics.perTabWidth)
        val barWidth = metrics.perTabWidth * 6f + 8.dp
        assertTrue(barWidth < PAD_BAR * 0.7f, "expected a pill, not a strip: $barWidth of $PAD_BAR")
    }

    @Test
    fun `six tabs on a phone stay iconic and six on a panel gain labels`() {
        val phone = guideBottomBarMetrics(availableWidth = PHONE_BAR, tabCount = 6)
        val pad = guideBottomBarMetrics(availableWidth = PAD_BAR, tabCount = 6)

        assertFalse(guideBottomBarShowsLabels(phone.perTabWidth, fontScale = 1f))
        assertTrue(guideBottomBarShowsLabels(pad.perTabWidth, fontScale = 1f))
    }

    @Test
    fun `five tabs on a phone keep the labels they already had`() {
        // The behaviour the old count-based rule gave, and the reason the threshold is 68dp: a filled
        // phone bar hands five tabs 70dp each, and those were labelled before this changed.
        val metrics = guideBottomBarMetrics(availableWidth = PHONE_BAR, tabCount = 5)

        assertFalse(metrics.sizedToTabs)
        assertTrue(metrics.perTabWidth >= GuideBottomBarLabelMinTabWidth)
        assertTrue(guideBottomBarShowsLabels(metrics.perTabWidth, fontScale = 1f))
    }

    @Test
    fun `a large font scale drops the labels however much room there is`() {
        val pad = guideBottomBarMetrics(availableWidth = PAD_BAR, tabCount = 6)

        assertTrue(guideBottomBarShowsLabels(pad.perTabWidth, fontScale = 1.2f))
        assertFalse(guideBottomBarShowsLabels(pad.perTabWidth, fontScale = 1.21f))
    }

    @Test
    fun `a narrow split-screen frame still resolves to something tappable`() {
        val metrics = guideBottomBarMetrics(availableWidth = 260.dp, tabCount = 6)

        assertFalse(metrics.sizedToTabs)
        assertTrue(metrics.perTabWidth > 0.dp)
        assertFalse(guideBottomBarShowsLabels(metrics.perTabWidth, fontScale = 1f))
    }
}

/** A 426dp phone and a 1280dp panel, each less the page gutter and the bar's own 24dp margins. */
private val PHONE_BAR = 350.dp
private val PAD_BAR = 1204.dp
