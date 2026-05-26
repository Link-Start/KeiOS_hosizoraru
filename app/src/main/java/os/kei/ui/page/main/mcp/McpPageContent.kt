@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.host.pager.MainPageBackdropSet
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.mcp.section.McpLogsSection
import os.kei.ui.page.main.mcp.section.McpOverviewCardSection
import os.kei.ui.page.main.mcp.section.McpServiceControlSection
import os.kei.ui.page.main.mcp.section.McpToolAdvancedSection
import os.kei.ui.page.main.mcp.section.McpToolBaSection
import os.kei.ui.page.main.mcp.section.McpToolCodexSection
import os.kei.ui.page.main.mcp.section.McpToolEntrypointsSection
import os.kei.ui.page.main.mcp.section.McpToolGithubSection
import os.kei.ui.page.main.mcp.section.McpToolRuntimeSection
import os.kei.ui.page.main.mcp.section.McpToolSystemSection
import os.kei.ui.page.main.mcp.section.McpToolWorkflowSection
import os.kei.ui.page.main.mcp.state.McpPageOverviewState
import os.kei.ui.page.main.mcp.state.McpToolBuckets
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay

@Composable
internal fun McpPageContent(
    uiState: McpServerUiState,
    pageUiState: McpPageUiState,
    toolBuckets: McpToolBuckets,
    overviewState: McpPageOverviewState,
    runtime: MainPageRuntime,
    innerPadding: PaddingValues,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    backdrops: MainPageBackdropSet,
    titleColor: Color,
    subtitleColor: Color,
    isDark: Boolean,
    refreshRunning: Boolean,
    actions: McpPageActions,
) {
    val renderHeavyContent = shouldRenderMcpHeavyContent(runtime)
    val revealPhase = rememberMcpHeavyContentRevealPhase(renderHeavyContent)
    Box(modifier = Modifier.fillMaxSize()) {
        if (renderHeavyContent) {
            AppPageLazyColumn(
                innerPadding = innerPadding,
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                bottomExtra = appPageBottomPaddingWithFloatingOverlay(runtime.contentBottomPadding),
                sectionSpacing = 12.dp,
            ) {
                item(key = "mcp-overview", contentType = "mcp_overview_section") {
                    McpOverviewCardSection(
                        backdrop = backdrops.content,
                        titleColor = titleColor,
                        subtitleColor = subtitleColor,
                        overviewCardColor = overviewState.overviewCardColor,
                        overviewBorderColor = overviewState.overviewBorderColor,
                        overviewAccentColor = overviewState.overviewAccentColor,
                        runtimeText = overviewState.runtimeText,
                        isDark = isDark,
                        running = uiState.running,
                        overviewMetrics = overviewState.overviewMetrics,
                        onToggleServer = actions.onToggleServer,
                        onOpenEditSheet = actions.onOpenEditSheet,
                    )
                }
                if (revealPhase >= MCP_HEAVY_CONTENT_REVEAL_CONTROLS) {
                    item(key = "mcp-service-control", contentType = "mcp_service_control_section") {
                        McpServiceControlSection(
                            backdrop = backdrops.content,
                            expanded = pageUiState.controlExpanded,
                            onExpandedChange = actions.onControlExpandedChange,
                            onSendTestNotification = actions.onSendTestNotification,
                            onShowResetConfigConfirm = actions.onShowResetConfigConfirm,
                            onCopySkillResource = actions.onCopySkillResource,
                            onCopyWorkflowResource = actions.onCopyWorkflowResource,
                        )
                    }
                }
                if (revealPhase >= MCP_HEAVY_CONTENT_REVEAL_ENTRYPOINTS) {
                    item(key = "mcp-tool-entrypoints", contentType = "mcp_tool_entrypoints_section") {
                        McpToolEntrypointsSection(
                            backdrop = backdrops.content,
                            buckets = toolBuckets,
                            searchQuery = pageUiState.toolsSearchQuery,
                            onSearchQueryChange = actions.onToolsSearchQueryChange,
                            expanded = pageUiState.toolEntrypointsExpanded,
                            onExpandedChange = actions.onToolEntrypointsExpandedChange,
                        )
                    }
                }
                if (revealPhase >= MCP_HEAVY_CONTENT_REVEAL_PRIMARY_TOOLS) {
                    item(key = "mcp-tool-runtime", contentType = "mcp_tool_runtime_section") {
                        McpToolRuntimeSection(
                            backdrop = backdrops.content,
                            tools = toolBuckets.runtimeTools,
                            searchQuery = pageUiState.toolsSearchQuery,
                            expanded = pageUiState.runtimeToolsExpanded,
                            onExpandedChange = actions.onRuntimeToolsExpandedChange,
                        )
                    }
                    item(key = "mcp-tool-system", contentType = "mcp_tool_system_section") {
                        McpToolSystemSection(
                            backdrop = backdrops.content,
                            tools = toolBuckets.systemTools,
                            searchQuery = pageUiState.toolsSearchQuery,
                            expanded = pageUiState.systemToolsExpanded,
                            onExpandedChange = actions.onSystemToolsExpandedChange,
                        )
                    }
                }
                if (revealPhase >= MCP_HEAVY_CONTENT_REVEAL_SECONDARY_TOOLS) {
                    item(key = "mcp-tool-github", contentType = "mcp_tool_github_section") {
                        McpToolGithubSection(
                            backdrop = backdrops.content,
                            tools = toolBuckets.githubTools,
                            searchQuery = pageUiState.toolsSearchQuery,
                            expanded = pageUiState.githubToolsExpanded,
                            onExpandedChange = actions.onGithubToolsExpandedChange,
                        )
                    }
                    item(key = "mcp-tool-ba", contentType = "mcp_tool_ba_section") {
                        McpToolBaSection(
                            backdrop = backdrops.content,
                            tools = toolBuckets.baTools,
                            searchQuery = pageUiState.toolsSearchQuery,
                            expanded = pageUiState.baToolsExpanded,
                            onExpandedChange = actions.onBaToolsExpandedChange,
                        )
                    }
                    item(key = "mcp-tool-codex", contentType = "mcp_tool_codex_section") {
                        McpToolCodexSection(
                            backdrop = backdrops.content,
                            tools = toolBuckets.codexTools,
                            searchQuery = pageUiState.toolsSearchQuery,
                            expanded = pageUiState.codexToolsExpanded,
                            onExpandedChange = actions.onCodexToolsExpandedChange,
                        )
                    }
                    item(key = "mcp-tool-workflows", contentType = "mcp_tool_workflows_section") {
                        McpToolWorkflowSection(
                            backdrop = backdrops.content,
                            tools = toolBuckets.workflowTools,
                            searchQuery = pageUiState.toolsSearchQuery,
                            expanded = pageUiState.workflowToolsExpanded,
                            onExpandedChange = actions.onWorkflowToolsExpandedChange,
                        )
                    }
                    if (toolBuckets.advancedTools.isNotEmpty()) {
                        item(key = "mcp-tool-advanced", contentType = "mcp_tool_advanced_section") {
                            McpToolAdvancedSection(
                                backdrop = backdrops.content,
                                tools = toolBuckets.advancedTools,
                                searchQuery = pageUiState.toolsSearchQuery,
                                expanded = pageUiState.advancedToolsExpanded,
                                onExpandedChange = actions.onAdvancedToolsExpandedChange,
                            )
                        }
                    }
                    item(key = "mcp-logs", contentType = "mcp_logs_section") {
                        McpLogsSection(
                            backdrop = backdrops.content,
                            expanded = pageUiState.logsExpanded,
                            onExpandedChange = actions.onLogsExpandedChange,
                            uiState = uiState,
                            logsExporting = pageUiState.logsExporting,
                            onExportLogs = actions.onExportLogs,
                            onClearLogs = actions.onClearLogs,
                            subtitleColor = subtitleColor,
                        )
                    }
                }
            }
        }

        if (renderHeavyContent && revealPhase >= MCP_HEAVY_CONTENT_REVEAL_DOCK) {
            McpPageFloatingActionDock(
                backdrop = backdrops.content,
                uiState = uiState,
                runtime = runtime,
                refreshRunning = refreshRunning,
                actions = actions,
            )
        }
    }
}

internal fun shouldRenderMcpHeavyContent(runtime: MainPageRuntime): Boolean =
    runtime.contentReady && (!runtime.isPagerScrollInProgress || runtime.isDataActive)

@Composable
private fun rememberMcpHeavyContentRevealPhase(renderHeavyContent: Boolean): Int {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(renderHeavyContent) {
        if (!renderHeavyContent) {
            phase = 0
            return@LaunchedEffect
        }
        phase = MCP_HEAVY_CONTENT_REVEAL_OVERVIEW
        withFrameNanos { }
        phase = MCP_HEAVY_CONTENT_REVEAL_CONTROLS
        withFrameNanos { }
        phase = MCP_HEAVY_CONTENT_REVEAL_ENTRYPOINTS
        withFrameNanos { }
        phase = MCP_HEAVY_CONTENT_REVEAL_PRIMARY_TOOLS
        withFrameNanos { }
        phase = MCP_HEAVY_CONTENT_REVEAL_SECONDARY_TOOLS
        withFrameNanos { }
        phase = MCP_HEAVY_CONTENT_REVEAL_DOCK
    }
    return phase
}

private const val MCP_HEAVY_CONTENT_REVEAL_OVERVIEW = 1
private const val MCP_HEAVY_CONTENT_REVEAL_CONTROLS = 2
private const val MCP_HEAVY_CONTENT_REVEAL_ENTRYPOINTS = 3
private const val MCP_HEAVY_CONTENT_REVEAL_PRIMARY_TOOLS = 4
private const val MCP_HEAVY_CONTENT_REVEAL_SECONDARY_TOOLS = 5
private const val MCP_HEAVY_CONTENT_REVEAL_DOCK = 6
