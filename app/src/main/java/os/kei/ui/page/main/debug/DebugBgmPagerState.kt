package os.kei.ui.page.main.debug

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class DebugBgmPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope
) {
    private var navJob: Job? = null

    val settledIndex: Int
        get() {
            val lastPage = (pagerState.pageCount - 1).coerceAtLeast(0)
            return pagerState.settledPage.coerceIn(0, lastPage)
        }

    val selectedIndex: Int
        get() {
            val lastPage = (pagerState.pageCount - 1).coerceAtLeast(0)
            return pagerState.currentPage.coerceIn(0, lastPage)
        }

    val selectionPosition: Float
        get() {
            val lastPage = (pagerState.pageCount - 1).coerceAtLeast(0)
            return (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, lastPage.toFloat())
        }

    fun navigateToPage(targetIndex: Int) {
        val safeTarget = targetIndex.coerceIn(0, pagerState.pageCount - 1)
        if (safeTarget == pagerState.currentPage && pagerState.currentPageOffsetFraction == 0f) return

        navJob?.cancel()

        navJob = coroutineScope.launch {
            pagerState.animateScrollToPage(safeTarget)
        }
    }
}

@Composable
internal fun rememberDebugBgmPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): DebugBgmPagerState {
    return remember(pagerState, coroutineScope) {
        DebugBgmPagerState(
            pagerState = pagerState,
            coroutineScope = coroutineScope
        )
    }
}
