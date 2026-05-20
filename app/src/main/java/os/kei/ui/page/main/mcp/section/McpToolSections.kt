@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.mcp.server.McpServerUiState
import os.kei.mcp.server.McpToolCatalog
import os.kei.mcp.server.McpToolDomains
import os.kei.mcp.server.McpToolMeta
import os.kei.mcp.server.McpToolVisibility
import os.kei.ui.page.main.os.appLucideAppWindowIcon
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideGridIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidExpandableSection
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

internal data class McpToolBuckets(
    val entrypointTools: List<McpToolMeta>,
    val runtimeTools: List<McpToolMeta>,
    val systemTools: List<McpToolMeta>,
    val githubTools: List<McpToolMeta>,
    val baTools: List<McpToolMeta>,
    val codexTools: List<McpToolMeta>,
    val workflowTools: List<McpToolMeta>,
    val advancedTools: List<McpToolMeta>,
)

internal fun mcpToolBuckets(
    uiState: McpServerUiState,
    searchQuery: String,
): McpToolBuckets {
    val query = searchQuery.trim().lowercase(Locale.ROOT)
    val filteredTools =
        if (query.isBlank()) {
            uiState.tools
        } else {
            uiState.tools.filter { tool ->
                tool.name.lowercase(Locale.ROOT).contains(query) ||
                    tool.description.lowercase(Locale.ROOT).contains(query) ||
                    tool.group.lowercase(Locale.ROOT).contains(query)
            }
        }
    val codexToolNames = McpToolCatalog.devToolNames.toSet()
    val categorizedToolNames =
        filteredTools
            .filter {
                it.group in
                    setOf(
                        McpToolDomains.RUNTIME,
                        McpToolDomains.HOME,
                        McpToolDomains.SYSTEM,
                        McpToolDomains.OS,
                        McpToolDomains.GITHUB,
                        McpToolDomains.BA,
                        McpToolDomains.DEV,
                    ) || it.visibility == McpToolVisibility.Workflow
            }.mapTo(mutableSetOf()) { it.name }
    return McpToolBuckets(
        entrypointTools =
            filteredTools.filter {
                it.visibility == McpToolVisibility.Entrypoint &&
                    it.group in setOf(McpToolDomains.RUNTIME, McpToolDomains.HOME) &&
                    it.visibility != McpToolVisibility.Workflow
            },
        runtimeTools =
            filteredTools.filter {
                it.group in setOf(McpToolDomains.RUNTIME, McpToolDomains.HOME) &&
                    it.visibility != McpToolVisibility.Entrypoint &&
                    it.visibility != McpToolVisibility.Workflow
            },
        systemTools =
            filteredTools.filter {
                it.group in setOf(McpToolDomains.SYSTEM, McpToolDomains.OS)
            },
        githubTools = filteredTools.filter { it.group == McpToolDomains.GITHUB },
        baTools = filteredTools.filter { it.group == McpToolDomains.BA },
        codexTools = filteredTools.filter { it.name in codexToolNames },
        workflowTools = filteredTools.filter { it.visibility == McpToolVisibility.Workflow },
        advancedTools =
            filteredTools.filter {
                it.visibility == McpToolVisibility.Advanced && it.name !in categorizedToolNames
            },
    )
}

@Composable
internal fun McpToolEntrypointsSection(
    backdrop: LayerBackdrop,
    buckets: McpToolBuckets,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    McpToolCard(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_section_tool_surface_title),
        subtitle =
            stringResource(
                R.string.mcp_section_tool_surface_subtitle,
                buckets.entrypointTools.size,
                buckets.githubTools.size,
                buckets.baTools.size,
                buckets.codexTools.size,
            ),
        expanded = expanded || searchQuery.isNotBlank(),
        onExpandedChange = onExpandedChange,
        iconContentDescription = stringResource(R.string.mcp_section_tools_title),
        icon = McpToolCardIcon.Entry,
    ) {
        AppLiquidSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = stringResource(R.string.mcp_tools_search_hint),
            backdrop = backdrop,
        )
        McpToolGroupRows(
            title = stringResource(R.string.mcp_tools_entrypoints_title),
            tools = buckets.entrypointTools,
        )
    }
}

@Composable
internal fun McpToolRuntimeSection(
    backdrop: LayerBackdrop,
    tools: List<McpToolMeta>,
    searchQuery: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    McpToolCard(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_tools_runtime_title),
        subtitle = stringResource(R.string.mcp_section_tools_subtitle, tools.size),
        expanded = expanded || searchQuery.isNotBlank(),
        onExpandedChange = onExpandedChange,
        iconContentDescription = stringResource(R.string.mcp_tools_runtime_title),
        icon = McpToolCardIcon.Runtime,
    ) {
        McpToolGroupRows(
            title = stringResource(R.string.mcp_tools_runtime_title),
            tools = tools,
        )
    }
}

@Composable
internal fun McpToolSystemSection(
    backdrop: LayerBackdrop,
    tools: List<McpToolMeta>,
    searchQuery: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    McpToolCard(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_tools_system_title),
        subtitle = stringResource(R.string.mcp_section_tools_subtitle, tools.size),
        expanded = expanded || searchQuery.isNotBlank(),
        onExpandedChange = onExpandedChange,
        iconContentDescription = stringResource(R.string.mcp_tools_system_title),
        icon = McpToolCardIcon.System,
    ) {
        McpToolGroupRows(
            title = stringResource(R.string.mcp_tools_system_title),
            tools = tools,
        )
    }
}

@Composable
internal fun McpToolGithubSection(
    backdrop: LayerBackdrop,
    tools: List<McpToolMeta>,
    searchQuery: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    McpToolCard(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_tools_github_title),
        subtitle = stringResource(R.string.mcp_section_tools_subtitle, tools.size),
        expanded = expanded || searchQuery.isNotBlank(),
        onExpandedChange = onExpandedChange,
        iconContentDescription = stringResource(R.string.mcp_tools_github_title),
        icon = McpToolCardIcon.Github,
    ) {
        McpToolGroupRows(
            title = stringResource(R.string.mcp_tools_github_title),
            tools = tools,
        )
    }
}

@Composable
internal fun McpToolBaSection(
    backdrop: LayerBackdrop,
    tools: List<McpToolMeta>,
    searchQuery: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    McpToolCard(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_tools_ba_title),
        subtitle = stringResource(R.string.mcp_section_tools_subtitle, tools.size),
        expanded = expanded || searchQuery.isNotBlank(),
        onExpandedChange = onExpandedChange,
        iconContentDescription = stringResource(R.string.mcp_tools_ba_title),
        icon = McpToolCardIcon.Ba,
    ) {
        McpToolGroupRows(
            title = stringResource(R.string.mcp_tools_ba_title),
            tools = tools,
        )
    }
}

@Composable
internal fun McpToolCodexSection(
    backdrop: LayerBackdrop,
    tools: List<McpToolMeta>,
    searchQuery: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    McpToolCard(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_tools_codex_title),
        subtitle = stringResource(R.string.mcp_section_tools_subtitle, tools.size),
        expanded = expanded || searchQuery.isNotBlank(),
        onExpandedChange = onExpandedChange,
        iconContentDescription = stringResource(R.string.mcp_tools_codex_title),
        icon = McpToolCardIcon.Codex,
    ) {
        McpToolGroupRows(
            title = stringResource(R.string.mcp_tools_codex_title),
            tools = tools,
        )
    }
}

@Composable
internal fun McpToolWorkflowSection(
    backdrop: LayerBackdrop,
    tools: List<McpToolMeta>,
    searchQuery: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    McpToolCard(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_tools_workflows_title),
        subtitle = stringResource(R.string.mcp_section_tools_subtitle, tools.size),
        expanded = expanded || searchQuery.isNotBlank(),
        onExpandedChange = onExpandedChange,
        iconContentDescription = stringResource(R.string.mcp_tools_workflows_title),
        icon = McpToolCardIcon.Workflow,
    ) {
        McpToolGroupRows(
            title = stringResource(R.string.mcp_tools_workflows_title),
            tools = tools,
        )
    }
}

@Composable
internal fun McpToolAdvancedSection(
    backdrop: LayerBackdrop,
    tools: List<McpToolMeta>,
    searchQuery: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    McpToolCard(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_tools_advanced_title),
        subtitle = stringResource(R.string.mcp_section_tools_subtitle, tools.size),
        expanded = expanded || searchQuery.isNotBlank(),
        onExpandedChange = onExpandedChange,
        iconContentDescription = stringResource(R.string.mcp_tools_advanced_title),
        icon = McpToolCardIcon.Advanced,
    ) {
        McpToolGroupRows(
            title = stringResource(R.string.mcp_tools_advanced_title),
            tools = tools,
        )
    }
}

private enum class McpToolCardIcon {
    Entry,
    Runtime,
    System,
    Github,
    Ba,
    Codex,
    Workflow,
    Advanced,
}

@Composable
private fun McpToolCard(
    backdrop: LayerBackdrop,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    iconContentDescription: String,
    icon: McpToolCardIcon,
    content: @Composable () -> Unit,
) {
    AppLiquidExpandableSection(
        backdrop = backdrop,
        title = title,
        subtitle = subtitle,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerStartAction = {
            McpSectionHeaderIcon(
                icon =
                    when (icon) {
                        McpToolCardIcon.Entry -> appLucideAppWindowIcon()
                        McpToolCardIcon.Runtime -> appLucideListIcon()
                        McpToolCardIcon.System -> appLucideGridIcon()
                        McpToolCardIcon.Github -> appLucidePackageIcon()
                        McpToolCardIcon.Ba -> appLucideHeartIcon()
                        McpToolCardIcon.Codex -> appLucideConfigIcon()
                        McpToolCardIcon.Workflow -> appLucideBranchIcon()
                        McpToolCardIcon.Advanced -> appLucideLayersIcon()
                    },
                contentDescription = iconContentDescription,
            )
        },
        content = content,
    )
}

@Composable
private fun McpToolGroupRows(
    title: String,
    tools: List<McpToolMeta>,
) {
    Text(
        text = title,
        color = MiuixTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
    )
    if (tools.isEmpty()) {
        MiuixInfoItem(
            key = title,
            value = stringResource(R.string.mcp_tools_empty_group),
        )
    } else {
        tools.forEach { tool ->
            key(tool.name) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = tool.name,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        text = tool.description,
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${tool.group} · ${tool.executionProfile.name} · ${tool.outputContract.wireName}",
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}
