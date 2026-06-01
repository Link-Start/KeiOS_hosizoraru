// Copyright 2026, KeiOS contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Scrolls [listState] to the top when [scrollToTopSignal] increases while
 * [isActive] is true. Use for single-list pages. For multi-list pages (e.g.
 * pager with per-tab lists), call once per visible tab or dispatch manually.
 *
 * @param scrollToTopSignal monotonically increasing signal (typically from
 *  bottom-bar re-tap or pager host). The effect fires each time the signal
 *  value changes while [isActive] is true.
 * @param listState the [LazyListState] to scroll.
 * @param isActive whether the page is the active/settled page. When false the
 *  signal is ignored, preventing background pages from scrolling.
 */
@Composable
internal fun BindScrollToTopEffect(
    scrollToTopSignal: Int,
    listState: LazyListState,
    isActive: Boolean,
) {
    LaunchedEffect(scrollToTopSignal, isActive) {
        if (isActive && scrollToTopSignal > 0) {
            listState.animateScrollToItem(0)
        }
    }
}

@Composable
internal fun BindLazyListScrollBoundsEffect(
    listState: LazyListState,
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
