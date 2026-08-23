package os.kei.ui.page.main.host.pager

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class MainPagerFloatingToolbarLayoutTest {
    @Test
    fun `reported navigation inset keeps the established eight dp visual gap`() {
        val navigationInset = 24.dp

        assertEquals(
            navigationInset + 8.dp,
            navigationInset +
                MainPagerMiuixFloatingToolbarSpacing +
                mainPagerFloatingToolbarContentBottomPadding(navigationInset),
        )
    }

    @Test
    fun `zero navigation inset keeps the established fallback baseline`() {
        assertEquals(
            36.dp,
            MainPagerMiuixFloatingToolbarSpacing +
                mainPagerFloatingToolbarContentBottomPadding(navigationBarBottom = 0.dp),
        )
    }
}
