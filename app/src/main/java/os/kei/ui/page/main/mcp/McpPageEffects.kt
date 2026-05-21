@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import os.kei.mcp.server.McpServerManager
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.mcp.state.McpToolBucketInput

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
    LaunchedEffect(
        mcpServerManager,
        uiState.port,
        uiState.allowExternal,
        uiState.serverName,
        pageUiState.showEditSheet,
    ) {
        mcpPageViewModel.syncServiceDraft(uiState)
    }
    LaunchedEffect(uiState.tools, pageUiState.toolsSearchQuery) {
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
    LaunchedEffect(runtime.scrollToTopSignal, runtime.isPageActive) {
        if (runtime.isPageActive && runtime.scrollToTopSignal > 0) {
            listState.animateScrollToItem(0)
        }
    }
}
