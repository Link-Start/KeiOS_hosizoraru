@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.foundation.lazy.LazyListState
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
    secondaryListState: LazyListState,
    wideLayout: Boolean,
) {
    // Two columns that scroll apart, so the chrome follows the first one. Either can move it through the
    // shared nested scroll; reading one keeps the show-again check from flapping when they disagree, and
    // the scroll-to-top signal moves both.
    val scrollTarget =
        rememberAppPageScrollTarget(
            listState = listState,
            secondaryState = secondaryListState,
            wideLayout = wideLayout,
        )
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
