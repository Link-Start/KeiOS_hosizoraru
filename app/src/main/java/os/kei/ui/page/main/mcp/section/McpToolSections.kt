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

private val McpToolEntrypointGroups =
    setOf(
        McpToolDomains.RUNTIME,
        McpToolDomains.HOME,
    )

private val McpToolSystemGroups =
    setOf(
        McpToolDomains.SYSTEM,
        McpToolDomains.OS,
    )

private val McpToolCategorizedGroups =
    setOf(
        McpToolDomains.RUNTIME,
        McpToolDomains.HOME,
        McpToolDomains.SYSTEM,
        McpToolDomains.OS,
        McpToolDomains.GITHUB,
        McpToolDomains.BA,
        McpToolDomains.DEV,
    )

private val McpCodexToolNames = McpToolCatalog.devToolNames.toSet()

internal fun mcpToolBuckets(
    uiState: McpServerUiState,
    searchQuery: String,
): McpToolBuckets {
    val query = searchQuery.trim().lowercase(Locale.ROOT)
    val entrypointTools = mutableListOf<McpToolMeta>()
    val runtimeTools = mutableListOf<McpToolMeta>()
    val systemTools = mutableListOf<McpToolMeta>()
    val githubTools = mutableListOf<McpToolMeta>()
    val baTools = mutableListOf<McpToolMeta>()
    val codexTools = mutableListOf<McpToolMeta>()
    val workflowTools = mutableListOf<McpToolMeta>()
    val advancedCandidates = mutableListOf<McpToolMeta>()
    val categorizedToolNames = mutableSetOf<String>()

    uiState.tools.forEach { tool ->
        if (query.isNotBlank() && !tool.matchesMcpToolQuery(query)) {
            return@forEach
        }
        if (tool.group in McpToolCategorizedGroups || tool.visibility == McpToolVisibility.Workflow) {
            categorizedToolNames += tool.name
        }
        when {
            tool.visibility == McpToolVisibility.Entrypoint &&
                tool.group in McpToolEntrypointGroups -> entrypointTools += tool

            tool.group in McpToolEntrypointGroups &&
                tool.visibility != McpToolVisibility.Workflow -> runtimeTools += tool

            tool.group in McpToolSystemGroups -> systemTools += tool

            tool.group == McpToolDomains.GITHUB -> githubTools += tool

            tool.group == McpToolDomains.BA -> baTools += tool
        }
        if (tool.name in McpCodexToolNames) {
            codexTools += tool
        }
        if (tool.visibility == McpToolVisibility.Workflow) {
            workflowTools += tool
        }
        if (tool.visibility == McpToolVisibility.Advanced) {
            advancedCandidates += tool
        }
    }

    return McpToolBuckets(
        entrypointTools = entrypointTools,
        runtimeTools = runtimeTools,
        systemTools = systemTools,
        githubTools = githubTools,
        baTools = baTools,
        codexTools = codexTools,
        workflowTools = workflowTools,
        advancedTools = advancedCandidates.filter { tool -> tool.name !in categorizedToolNames },
    )
}

private fun McpToolMeta.matchesMcpToolQuery(query: String): Boolean =
    name.lowercase(Locale.ROOT).contains(query) ||
        description.lowercase(Locale.ROOT).contains(query) ||
        group.lowercase(Locale.ROOT).contains(query)

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
