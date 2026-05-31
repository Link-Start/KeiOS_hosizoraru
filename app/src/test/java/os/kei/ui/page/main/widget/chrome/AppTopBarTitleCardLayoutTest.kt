package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class AppTopBarTitleCardLayoutTest {
    // NOTE: the previous title-width estimate tests were removed because
    // estimateTopBarTitleWidthAt18Sp() no longer exists — commit c3fadc070 replaced the manual
    // width heuristic with a TextMeasurer-based auto-fit, so those cases tested a deleted API.

    @Test
    fun titleCardMatchesTopBarChromeBand() {
        assertEquals(52.dp, AppChromeTokens.topBarTitleHeight)
        assertEquals(52.dp, AppChromeTokens.liquidActionBarOuterHeight)
        assertEquals(64.dp, AppChromeTokens.topBarCollapsedHeight)
        assertEquals(6.dp, AppChromeTokens.topBarChromeTopPadding)
    }

    @Test
    fun pageContentPaddingUsesSharedTopBarToHeaderGap() {
        val padding = appPageContentPadding(
            innerPadding = PaddingValues(top = AppChromeTokens.topBarCollapsedHeight),
            topExtra = AppChromeTokens.topBarToHeaderGap
        )

        assertEquals(
            AppChromeTokens.topBarCollapsedHeight + AppChromeTokens.topBarToHeaderGap,
            padding.calculateTopPadding()
        )
    }
}
