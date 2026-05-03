package os.kei.ui.page.main.debug

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal class DebugBgmPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    val selectedPagePosition: Float
        get() {
            val lastPage = (pagerState.pageCount - 1).coerceAtLeast(0)
            return if (pagerState.isScrollInProgress || isNavigating) {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, lastPage.toFloat())
            } else {
                selectedPage.coerceIn(0, lastPage).toFloat()
            }
        }

    fun animateToPage(targetIndex: Int) {
        val safeTarget = targetIndex.coerceIn(0, pagerState.pageCount - 1)
        if (safeTarget == selectedPage) return

        navJob?.cancel()
        selectedPage = safeTarget
        isNavigating = true

        val layoutInfo = pagerState.layoutInfo
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val currentDistanceInPages =
            safeTarget - pagerState.currentPage - pagerState.currentPageOffsetFraction
        val scrollPixels = currentDistanceInPages * pageSize
        val distance = abs(safeTarget - pagerState.currentPage).coerceAtLeast(1)
        val durationMillis = 120 + distance * 90

        navJob = coroutineScope.launch {
            val runningJob = coroutineContext.job
            try {
                pagerState.animateScrollBy(
                    value = scrollPixels,
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        easing = EaseInOut
                    )
                )
            } finally {
                if (navJob == runningJob) {
                    isNavigating = false
                    if (pagerState.currentPage != safeTarget) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
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
