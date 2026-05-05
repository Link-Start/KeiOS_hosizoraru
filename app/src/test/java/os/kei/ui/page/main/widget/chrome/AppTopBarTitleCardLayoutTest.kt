package os.kei.ui.page.main.widget.chrome

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
}
