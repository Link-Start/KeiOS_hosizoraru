@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import os.kei.mcp.server.McpServerManager
import os.kei.ui.page.main.widget.chrome.BindScrollToTopEffect
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
    onActionBarInteractingChanged: (Boolean) -> Unit,
) {
    BindLazyListScrollBoundsEffect(
        listState = listState,
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
    LaunchedEffect(uiState.tools, pageUiState.toolsSearchQuery, runtime.isSettledDataActive) {
        if (!runtime.isSettledDataActive) return@LaunchedEffect
        mcpPageViewModel.requestToolBuckets(
            McpToolBucketInput(
                tools = uiState.tools,
                searchQuery = pageUiState.toolsSearchQuery,
            ),
        )
    }
    DisposableEffect(Unit) {
        onDispose { onActionBarInteractingChanged(false) }
    }
    BindScrollToTopEffect(
        scrollToTopSignal = runtime.scrollToTopSignal,
        listState = listState,
        isActive = runtime.isPageActive,
    )
}
