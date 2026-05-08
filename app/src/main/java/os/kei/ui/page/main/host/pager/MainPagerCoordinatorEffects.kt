package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun BindMainPagerCoordinatorEffects(
    tabsSize: Int,
    pagerState: MainPagerStateContract
) {
    LaunchedEffect(tabsSize) {
        val lastIndex = tabsSize - 1
        if (lastIndex >= 0 && pagerState.currentPage > lastIndex) {
            pagerState.scrollToPage(lastIndex)
        }
    }
}
