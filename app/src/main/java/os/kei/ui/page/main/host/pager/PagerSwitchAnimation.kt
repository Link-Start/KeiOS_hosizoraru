package os.kei.ui.page.main.host.pager

import androidx.compose.foundation.pager.PagerState
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import kotlin.math.abs

/**
 * Moves the pager to [targetIndex] the way a tab selection should: Miuix's page-navigation spring,
 * the one its own tab rows and snap fling share, rather than a tween with a distance-scaled length.
 *
 * A jump of more than one page is bracketed by [onFarJumpBefore] and [onFarJumpAfter], so the caller
 * can dim the pages the spring sweeps past.
 */
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

    if (layoutInfo.pageSize <= 0) {
        if (currentPage != target || isScrollInProgress) {
            scrollToPage(target)
        }
        return
    }
    val farJump = abs(target - from) > 1
    if (farJump) onFarJumpBefore()
    springAnimateToPage(target)
    if (farJump) onFarJumpAfter()
}
