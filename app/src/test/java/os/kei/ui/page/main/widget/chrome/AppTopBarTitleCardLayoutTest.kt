package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class AppTopBarTitleCardLayoutTest {
    @Test
    fun titleWidthEstimateTreatsCjkAsFullWidth() {
        assertEquals(
            76.dp,
            estimateTopBarTitleWidthAt18Sp("学生图鉴")
        )
    }

    @Test
    fun titleWidthEstimateAccountsForAsciiTitle() {
        assertEquals(
            62.dp,
            estimateTopBarTitleWidthAt18Sp("GitHub")
        )
    }

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
