package os.kei.ui.page.main.widget.chrome

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@Stable
internal class TabbedPageChromeScrollState internal constructor(
    val chromeNestedScrollConnection: NestedScrollConnection,
    private val showNowHandler: () -> Unit,
) {
    fun showNow() {
        showNowHandler()
    }
}

@Composable
internal fun rememberTabbedPageChromeScrollState(
    visible: Boolean,
    activeListStateProvider: () -> LazyListState,
    onVisibleChange: (Boolean) -> Unit,
): TabbedPageChromeScrollState {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 22.dp.toPx() } }
    val controller = remember(thresholdPx) { ScrollChromeVisibilityController(thresholdPx) }
    val currentVisible = rememberUpdatedState(visible)
    val currentActiveListStateProvider = rememberUpdatedState(activeListStateProvider)
    val currentOnVisibleChange = rememberUpdatedState(onVisibleChange)
    val chromeNestedScrollConnection =
        remember(controller) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val listState = currentActiveListStateProvider.value()
                    controller.updateWithinScrollBounds(
                        deltaY = consumed.y,
                        visible = currentVisible.value,
                        canScrollBackward = listState.canScrollBackward,
                        canScrollForward = listState.canScrollForward,
                        onVisibleChange = currentOnVisibleChange.value,
                    )
                    return Offset.Zero
                }
            }
        }
    val state =
        remember(chromeNestedScrollConnection, controller) {
            TabbedPageChromeScrollState(
                chromeNestedScrollConnection = chromeNestedScrollConnection,
                showNowHandler = {
                    controller.showNow(
                        visible = currentVisible.value,
                        onVisibleChange = currentOnVisibleChange.value,
                    )
                },
            )
        }

    LaunchedEffect(activeListStateProvider, controller) {
        snapshotFlow {
            val listState = activeListStateProvider()
            listState.canScrollBackward to listState.canScrollForward
        }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward) ->
                controller.showForStaticContent(
                    visible = currentVisible.value,
                    canScrollBackward = canScrollBackward,
                    canScrollForward = canScrollForward,
                    onVisibleChange = currentOnVisibleChange.value,
                )
            }
    }

    return state
}

internal fun LazyListState.canMoveForTabbedPageChrome(deltaY: Float): Boolean =
    when {
        deltaY < -1f -> canScrollForward
        deltaY > 1f -> canScrollBackward
        else -> true
    }

internal fun tabbedPageContentNestedScrollConnection(
    listState: LazyListState,
    chrome: NestedScrollConnection,
    delegate: NestedScrollConnection,
): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (!listState.canMoveForTabbedPageChrome(available.y)) return Offset.Zero
            return delegate.onPreScroll(available, source)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            chrome.onPostScroll(consumed, available, source)
            val canMove =
                listState.canMoveForTabbedPageChrome(consumed.y) ||
                    listState.canMoveForTabbedPageChrome(available.y)
            if (!canMove) return Offset.Zero
            return delegate.onPostScroll(consumed, available, source)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!listState.canMoveForTabbedPageChrome(available.y)) return Velocity.Zero
            return delegate.onPreFling(available)
        }

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity,
        ): Velocity {
            val canMove =
                listState.canMoveForTabbedPageChrome(consumed.y) ||
                    listState.canMoveForTabbedPageChrome(available.y)
            if (!canMove) return Velocity.Zero
            return delegate.onPostFling(consumed, available)
        }
    }
