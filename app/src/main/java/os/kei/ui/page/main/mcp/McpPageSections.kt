@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.mcp.section.McpLogsSection
import os.kei.ui.page.main.mcp.section.McpOnboardingGuideSection
import os.kei.ui.page.main.mcp.section.McpServiceControlSection
import os.kei.ui.page.main.mcp.section.McpToolAdvancedSection
import os.kei.ui.page.main.mcp.section.McpToolBaSection
import os.kei.ui.page.main.mcp.section.McpToolCodexSection
import os.kei.ui.page.main.mcp.section.McpToolEntrypointsSection
import os.kei.ui.page.main.mcp.section.McpToolGithubSection
import os.kei.ui.page.main.mcp.section.McpToolRuntimeSection
import os.kei.ui.page.main.mcp.section.McpToolSystemSection
import os.kei.ui.page.main.mcp.section.McpToolWorkflowSection
import os.kei.ui.page.main.mcp.state.McpToolBuckets

/**
 * The cards the MCP list is built from, in reading order.
 *
 * An enum rather than a list of composable lambdas so the two emitters below dispatch on a stable value.
 * A lambda list would be rebuilt on every composition, and a lazy item whose content lambda changes
 * identity every frame can never skip — which for eleven accordion cards of tool rows is the whole cost.
 */
internal enum class McpPageSection(
    val key: String,
    val contentType: String,
) {
    Onboarding("mcp-onboarding-guide", "mcp_onboarding_guide_section"),
    ServiceControl("mcp-service-control", "mcp_service_control_section"),
    ToolEntrypoints("mcp-tool-entrypoints", "mcp_tool_entrypoints_section"),
    ToolRuntime("mcp-tool-runtime", "mcp_tool_runtime_section"),
    ToolSystem("mcp-tool-system", "mcp_tool_system_section"),
    ToolGithub("mcp-tool-github", "mcp_tool_github_section"),
    ToolBa("mcp-tool-ba", "mcp_tool_ba_section"),
    ToolCodex("mcp-tool-codex", "mcp_tool_codex_section"),
    ToolWorkflows("mcp-tool-workflows", "mcp_tool_workflows_section"),
    ToolAdvanced("mcp-tool-advanced", "mcp_tool_advanced_section"),
    Logs("mcp-logs", "mcp_logs_section"),
}

/**
 * Which sections this state actually shows.
 *
 * Only the advanced bucket is conditional, and it was already conditional as an `if` around one list item;
 * hoisting it here is what lets the column and the grid agree on the set without restating the rule.
 */
internal fun mcpPageSections(toolBuckets: McpToolBuckets): List<McpPageSection> =
    McpPageSection.entries.filter { section ->
        section != McpPageSection.ToolAdvanced || toolBuckets.advancedTools.isNotEmpty()
    }

/** Everything a section needs to draw itself, bundled so the two emitters stay one line each. */
internal data class McpSectionRenderInput(
    val uiState: McpServerUiState,
    val pageUiState: McpPageUiState,
    val toolBuckets: McpToolBuckets,
    val backdrop: Backdrop,
    val subtitleColor: Color,
    val actions: McpPageActions,
)

/** One MCP card, without the list item around it. */
@Composable
internal fun McpPageSectionContent(
    section: McpPageSection,
    input: McpSectionRenderInput,
) {
    val backdrop = input.backdrop
    val pageUiState = input.pageUiState
    val actions = input.actions
    val toolBuckets = input.toolBuckets
    when (section) {
        McpPageSection.Onboarding ->
            McpOnboardingGuideSection(
                backdrop = backdrop,
                expanded = pageUiState.onboardingExpanded,
                onExpandedChange = actions.onOnboardingExpandedChange,
                onCopyCurrentConfig = actions.onCopyCurrentConfig,
                onCopySkillResource = actions.onCopySkillResource,
                onCopySubAgentResource = actions.onCopySubAgentResource,
                onCopyWorkflowResource = actions.onCopyWorkflowResource,
            )

        McpPageSection.ServiceControl ->
            McpServiceControlSection(
                backdrop = backdrop,
                expanded = pageUiState.controlExpanded,
                contentVisible = true,
                onExpandedChange = actions.onControlExpandedChange,
                onSendTestNotification = actions.onSendTestNotification,
                onShowResetConfigConfirm = actions.onShowResetConfigConfirm,
                onCopySkillResource = actions.onCopySkillResource,
                onCopyWorkflowResource = actions.onCopyWorkflowResource,
            )

        McpPageSection.ToolEntrypoints ->
            McpToolEntrypointsSection(
                backdrop = backdrop,
                buckets = toolBuckets,
                searchQuery = pageUiState.toolsSearchQuery,
                onSearchQueryChange = actions.onToolsSearchQueryChange,
                expanded = pageUiState.toolEntrypointsExpanded,
                onExpandedChange = actions.onToolEntrypointsExpandedChange,
            )

        McpPageSection.ToolRuntime ->
            McpToolRuntimeSection(
                backdrop = backdrop,
                tools = toolBuckets.runtimeTools,
                searchQuery = pageUiState.toolsSearchQuery,
                expanded = pageUiState.runtimeToolsExpanded,
                onExpandedChange = actions.onRuntimeToolsExpandedChange,
            )

        McpPageSection.ToolSystem ->
            McpToolSystemSection(
                backdrop = backdrop,
                tools = toolBuckets.systemTools,
                searchQuery = pageUiState.toolsSearchQuery,
                expanded = pageUiState.systemToolsExpanded,
                onExpandedChange = actions.onSystemToolsExpandedChange,
            )

        McpPageSection.ToolGithub ->
            McpToolGithubSection(
                backdrop = backdrop,
                tools = toolBuckets.githubTools,
                searchQuery = pageUiState.toolsSearchQuery,
                expanded = pageUiState.githubToolsExpanded,
                onExpandedChange = actions.onGithubToolsExpandedChange,
            )

        McpPageSection.ToolBa ->
            McpToolBaSection(
                backdrop = backdrop,
                tools = toolBuckets.baTools,
                searchQuery = pageUiState.toolsSearchQuery,
                expanded = pageUiState.baToolsExpanded,
                onExpandedChange = actions.onBaToolsExpandedChange,
            )

        McpPageSection.ToolCodex ->
            McpToolCodexSection(
                backdrop = backdrop,
                tools = toolBuckets.codexTools,
                searchQuery = pageUiState.toolsSearchQuery,
                expanded = pageUiState.codexToolsExpanded,
                onExpandedChange = actions.onCodexToolsExpandedChange,
            )

        McpPageSection.ToolWorkflows ->
            McpToolWorkflowSection(
                backdrop = backdrop,
                tools = toolBuckets.workflowTools,
                searchQuery = pageUiState.toolsSearchQuery,
                expanded = pageUiState.workflowToolsExpanded,
                onExpandedChange = actions.onWorkflowToolsExpandedChange,
            )

        McpPageSection.ToolAdvanced ->
            McpToolAdvancedSection(
                backdrop = backdrop,
                tools = toolBuckets.advancedTools,
                searchQuery = pageUiState.toolsSearchQuery,
                expanded = pageUiState.advancedToolsExpanded,
                onExpandedChange = actions.onAdvancedToolsExpandedChange,
            )

        McpPageSection.Logs ->
            McpLogsSection(
                backdrop = backdrop,
                expanded = pageUiState.logsExpanded,
                onExpandedChange = actions.onLogsExpandedChange,
                uiState = input.uiState,
                logsExporting = pageUiState.logsExporting,
                onExportLogs = actions.onExportLogs,
                onClearLogs = actions.onClearLogs,
                subtitleColor = input.subtitleColor,
            )
    }
}

/** The sections as list items — the phone layout, one card per row. */
internal fun LazyListScope.mcpSectionItems(
    sections: List<McpPageSection>,
    input: McpSectionRenderInput,
) {
    sections.forEach { section ->
        item(key = section.key, contentType = section.contentType) {
            McpPageSectionContent(section = section, input = input)
        }
    }
}

/** The same sections as staggered cells, so a tablet packs two columns instead of one long strip. */
internal fun LazyStaggeredGridScope.mcpSectionCells(
    sections: List<McpPageSection>,
    input: McpSectionRenderInput,
) {
    sections.forEach { section ->
        item(key = section.key, contentType = section.contentType) {
            McpPageSectionContent(section = section, input = input)
        }
    }
}
