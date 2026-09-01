@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import os.kei.mcp.server.McpServerManager
import os.kei.ui.page.main.widget.chrome.BindScrollToTopEffect
import os.kei.ui.page.main.widget.chrome.rememberAppPageScrollTarget
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.mcp.state.McpToolBucketInput
import os.kei.ui.page.main.widget.chrome.BindLazyListScrollBoundsEffect

@Composable
internal fun BindMcpPageEffects(
    mcpServerManager: McpServerManager,
    mcpPageViewModel: McpPageViewModel,
    uiState: McpServerUiState,
    pageUiState: McpPageUiState,
    runtime: MainPageRuntime,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    wideLayout: Boolean,
) {
    // Two containers, one on screen. Everything that watches a scroll position follows the active one, or
    // the bottom bar stops hiding on scroll and a tab re-tap scrolls a list nobody is looking at.
    val scrollTarget = rememberAppPageScrollTarget(listState, gridState, wideLayout)
    BindLazyListScrollBoundsEffect(
        listState = scrollTarget.scrollableState,
        isActive = runtime.isPageActive,
        onScrollBoundsChange = runtime.onScrollBoundsChange,
    )
    LaunchedEffect(
        mcpServerManager,
        uiState.port,
        uiState.allowExternal,
        uiState.serverName,
        pageUiState.showEditSheet,
        runtime.isSettledDataActive,
    ) {
        if (!runtime.isSettledDataActive) return@LaunchedEffect
        mcpPageViewModel.syncServiceDraft(uiState)
    }
    val toolBucketSearchQuery = pageUiState.toolsSearchQuery.trim()
    LaunchedEffect(uiState.tools, toolBucketSearchQuery, runtime.isSettledDataActive) {
        if (!runtime.isSettledDataActive) return@LaunchedEffect
        mcpPageViewModel.requestToolBuckets(
            McpToolBucketInput(
                tools = uiState.tools,
                searchQuery = toolBucketSearchQuery,
            ),
        )
    }
    BindScrollToTopEffect(
        scrollToTopSignal = runtime.scrollToTopSignal,
        target = scrollTarget,
        isActive = runtime.isPageActive,
    )
}
