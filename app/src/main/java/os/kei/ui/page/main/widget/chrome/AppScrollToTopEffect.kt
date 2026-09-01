// Copyright 2026, KeiOS contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A page's scroll container, whichever kind it is.
 *
 * A page that lays its cards out in two columns on a tablet has *two* containers — a column and a staggered
 * grid — and only one of them is on screen. Everything that watches a scroll position has to follow the
 * one that is, or the bottom bar stops hiding on scroll and re-tapping the tab scrolls a list nobody can
 * see. This is the handle those effects take so each page states the choice once instead of at every
 * call site.
 */
@Stable
internal class AppPageScrollTarget(
    val scrollableState: ScrollableState,
    val firstVisibleItemIndex: () -> Int,
    val firstVisibleItemScrollOffset: () -> Int,
    val scrollToTop: suspend () -> Unit,
)

@Composable
internal fun rememberAppPageScrollTarget(listState: LazyListState): AppPageScrollTarget =
    remember(listState) {
        AppPageScrollTarget(
            scrollableState = listState,
            firstVisibleItemIndex = { listState.firstVisibleItemIndex },
            firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
            scrollToTop = { listState.animateScrollToItem(0) },
        )
    }

/** The active container of the two a two-column page holds. */
@Composable
internal fun rememberAppPageScrollTarget(
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    wideLayout: Boolean,
): AppPageScrollTarget =
    remember(listState, gridState, wideLayout) {
        if (wideLayout) {
            AppPageScrollTarget(
                scrollableState = gridState,
                firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                scrollToTop = { gridState.animateScrollToItem(0) },
            )
        } else {
            AppPageScrollTarget(
                scrollableState = listState,
                firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                scrollToTop = { listState.animateScrollToItem(0) },
            )
        }
    }

/**
 * Scrolls [listState] to the top when [scrollToTopSignal] increases while
 * [isActive] is true. Use for single-list pages. For multi-list pages (e.g.
 * pager with per-tab lists), call once per visible tab or dispatch manually.
 *
 * @param scrollToTopSignal monotonically increasing signal (typically from
 *  bottom-bar re-tap or pager host). The effect fires each time the signal
 *  value changes while [isActive] is true.
 * @param target the page's active scroll container.
 * @param isActive whether the page is the active/settled page. When false the
 *  signal is ignored, preventing background pages from scrolling.
 */
@Composable
internal fun BindScrollToTopEffect(
    scrollToTopSignal: Int,
    target: AppPageScrollTarget,
    isActive: Boolean,
) {
    LaunchedEffect(scrollToTopSignal, isActive, target) {
        if (isActive && scrollToTopSignal > 0) {
            target.scrollToTop()
        }
    }
}

@Composable
internal fun BindLazyListScrollBoundsEffect(
    listState: ScrollableState,
    isActive: Boolean = true,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
) {
    LaunchedEffect(listState, isActive, onScrollBoundsChange) {
        if (!isActive) return@LaunchedEffect
        snapshotFlow {
            listState.canScrollBackward to listState.canScrollForward
        }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward) ->
                onScrollBoundsChange(canScrollBackward, canScrollForward)
            }
    }
}
