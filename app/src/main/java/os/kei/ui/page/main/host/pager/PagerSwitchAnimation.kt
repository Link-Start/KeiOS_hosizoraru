package os.kei.ui.page.main.host.pager

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.pager.PagerState
import kotlin.math.abs

internal suspend fun PagerState.animateTabSwitch(
    fromIndex: Int,
    targetIndex: Int,
    animationsEnabled: Boolean = true,
    onFarJumpBefore: suspend () -> Unit = {},
    onFarJumpAfter: suspend () -> Unit = {}
) {
    val total = pageCount
    if (total <= 0) return

    val from = fromIndex.coerceIn(0, total - 1)
    val target = targetIndex.coerceIn(0, total - 1)
    if (target == from && !isScrollInProgress) return

    if (!animationsEnabled) {
        onFarJumpBefore()
        if (currentPage != target || isScrollInProgress) {
            scrollToPage(target)
        }
        onFarJumpAfter()
        return
    }

    val farJumpDistance = abs(target - from)
    val pageSizePx = layoutInfo.pageSize.takeIf { it > 0 } ?: 0
    if (pageSizePx <= 0) {
        if (currentPage != target || isScrollInProgress) {
            scrollToPage(target)
        }
        return
    }
    val pageStridePx = (pageSizePx + layoutInfo.pageSpacing).toFloat()
    val distanceInPages = target - currentPage - currentPageOffsetFraction
    val scrollDistancePx = pageStridePx * distanceInPages
    val animationDistance = abs(target - currentPage).coerceAtLeast(2)
    if (farJumpDistance > 1) onFarJumpBefore()
    animateScrollBy(
        value = scrollDistancePx,
        animationSpec = tween(
            durationMillis = tabSwitchDurationMillis(animationDistance),
            easing = FastOutSlowInEasing
        )
    )
    scrollToPage(target)
    if (farJumpDistance > 1) onFarJumpAfter()
}

private fun tabSwitchDurationMillis(distance: Int): Int {
    return (100 * distance + 100).coerceIn(180, 420)
}
