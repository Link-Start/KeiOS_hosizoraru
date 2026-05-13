package os.kei.ui.page.main.mcp.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.mcp.server.McpServerUiState
import os.kei.mcp.server.McpToolMeta
import os.kei.mcp.server.McpToolVisibility
import os.kei.ui.page.main.os.appLucideAppWindowIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidExpandableSection
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun McpServiceControlSection(
    backdrop: LayerBackdrop,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onShowResetConfigConfirm: () -> Unit,
    onCopySkillResource: () -> Unit,
    onCopyWorkflowResource: () -> Unit,
) {
    AppLiquidExpandableSection(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_section_service_control_title),
        subtitle = stringResource(R.string.mcp_section_service_control_subtitle),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerStartAction = {
            McpSectionHeaderIcon(
                icon = appLucideConfigIcon(),
                contentDescription = stringResource(R.string.mcp_section_service_control_title)
            )
        }
    ) {
        AppDualActionRow(
            spacing = CardLayoutRhythm.infoRowGap,
            first = { modifier ->
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetPrimaryAction,
                    text = stringResource(R.string.mcp_action_send_test_notification),
                    modifier = modifier,
                    textColor = MiuixTheme.colorScheme.primary,
                    onClick = onSendTestNotification
                )
            },
            second = { modifier ->
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetDangerAction,
                    text = stringResource(R.string.mcp_action_reset_service_config),
                    textColor = MiuixTheme.colorScheme.error,
                    modifier = modifier,
                    onClick = onShowResetConfigConfirm
                )
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppDualActionRow(
            spacing = CardLayoutRhythm.infoRowGap,
            first = { modifier ->
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.Compact,
                    text = stringResource(R.string.mcp_action_copy_skill_resource),
                    modifier = modifier,
                    textColor = MiuixTheme.colorScheme.primary,
                    onClick = onCopySkillResource
                )
            },
            second = { modifier ->
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.Compact,
                    text = stringResource(R.string.mcp_action_copy_workflow_resource),
                    modifier = modifier,
                    textColor = MiuixTheme.colorScheme.primary,
                    onClick = onCopyWorkflowResource
                )
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        McpServiceControlHint(
            title = stringResource(R.string.mcp_action_copy_skill_resource),
            summary = stringResource(R.string.mcp_action_copy_skill_resource_summary)
        )
        McpServiceControlHint(
            title = stringResource(R.string.mcp_action_copy_workflow_resource),
            summary = stringResource(R.string.mcp_action_copy_workflow_resource_summary)
        )
    }
}

@Composable
private fun McpServiceControlHint(
    title: String,
    summary: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 19.sp
        )
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.88f),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
internal fun McpToolsSection(
    backdrop: LayerBackdrop,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    uiState: McpServerUiState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    advancedExpanded: Boolean,
    onAdvancedExpandedChange: (Boolean) -> Unit,
) {
    val filteredTools = remember(uiState.tools, searchQuery) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) {
            uiState.tools
        } else {
            uiState.tools.filter { tool ->
                tool.name.lowercase(Locale.ROOT).contains(query) ||
                        tool.description.lowercase(Locale.ROOT).contains(query) ||
                        tool.group.lowercase(Locale.ROOT).contains(query)
            }
        }
    }
    val entrypointTools = filteredTools.filter { it.visibility == McpToolVisibility.Entrypoint }
    val workflowTools = filteredTools.filter { it.visibility == McpToolVisibility.Workflow }
    val advancedTools = filteredTools.filter { it.visibility == McpToolVisibility.Advanced }
    val shownAdvancedTools = if (advancedExpanded || searchQuery.isNotBlank()) {
        advancedTools
    } else {
        advancedTools.take(6)
    }
    AppLiquidExpandableSection(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_section_tool_surface_title),
        subtitle = stringResource(
            R.string.mcp_section_tool_surface_subtitle,
            entrypointTools.size,
            workflowTools.size,
            advancedTools.size
        ),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerStartAction = {
            McpSectionHeaderIcon(
                icon = appLucideAppWindowIcon(),
                contentDescription = stringResource(R.string.mcp_section_tools_title)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLiquidSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = stringResource(R.string.mcp_tools_search_hint),
                backdrop = backdrop
            )
            McpToolGroupRows(
                title = stringResource(R.string.mcp_tools_entrypoints_title),
                tools = entrypointTools
            )
            McpToolGroupRows(
                title = stringResource(R.string.mcp_tools_workflows_title),
                tools = workflowTools
            )
            McpToolGroupRows(
                title = stringResource(R.string.mcp_tools_advanced_title),
                tools = shownAdvancedTools
            )
            if (advancedTools.size > shownAdvancedTools.size && searchQuery.isBlank()) {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.Compact,
                    text = stringResource(
                        R.string.mcp_tools_expand_advanced,
                        advancedTools.size - shownAdvancedTools.size
                    ),
                    textColor = MiuixTheme.colorScheme.primary,
                    onClick = { onAdvancedExpandedChange(true) }
                )
            } else if (advancedExpanded && searchQuery.isBlank()) {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.Compact,
                    text = stringResource(R.string.mcp_tools_collapse_advanced),
                    onClick = { onAdvancedExpandedChange(false) }
                )
            }
        }
    }
}

@Composable
private fun McpToolGroupRows(
    title: String,
    tools: List<McpToolMeta>
) {
    Text(
        text = title,
        color = MiuixTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold
    )
    if (tools.isEmpty()) {
        MiuixInfoItem(
            key = title,
            value = stringResource(R.string.mcp_tools_empty_group)
        )
    } else {
        tools.forEach { tool ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = tool.name,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = tool.description,
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${tool.group} · ${tool.executionProfile.name} · ${tool.outputContract.wireName}",
                    color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
internal fun McpLogsSection(
    backdrop: LayerBackdrop,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    uiState: McpServerUiState,
    logsExporting: Boolean,
    onExportLogs: (generatedAt: String, fileName: String) -> Unit,
    onClearLogs: () -> Unit,
    subtitleColor: Color,
) {
    AppLiquidExpandableSection(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_section_logs_title),
        subtitle = stringResource(R.string.mcp_section_logs_subtitle, uiState.logs.size),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerStartAction = {
            McpSectionHeaderIcon(
                icon = appLucideNotesIcon(),
                contentDescription = stringResource(R.string.mcp_section_logs_title)
            )
        },
        headerActions = {
            AppCompactIconAction(
                icon = if (logsExporting) {
                    appLucideRefreshIcon()
                } else {
                    appLucideDownloadIcon()
                },
                contentDescription = stringResource(R.string.mcp_action_export_logs),
                tint = if (logsExporting) {
                    subtitleColor
                } else {
                    MiuixTheme.colorScheme.primary
                },
                enabled = !logsExporting,
                onClick = {
                    val generatedAt = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS",
                        Locale.getDefault()
                    ).format(Date())
                    val exportStamp = SimpleDateFormat(
                        "yyyyMMdd-HHmmss-SSS",
                        Locale.getDefault()
                    ).format(Date())
                    onExportLogs(generatedAt, "keios-mcp-logs-$exportStamp.json")
                }
            )
        }
    ) {
        if (uiState.logs.isEmpty()) {
            MiuixInfoItem(
                key = stringResource(R.string.mcp_log_label),
                value = stringResource(R.string.mcp_log_empty)
            )
        } else {
            uiState.logs.asReversed().forEach { log ->
                val logTitle = "${log.time} [${log.level}]"
                MiuixInfoItem(
                    key = logTitle,
                    value = log.message
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        AppLiquidTextButton(
            backdrop = backdrop,
            variant = GlassVariant.Content,
            text = stringResource(R.string.mcp_action_clear_logs),
            onClick = onClearLogs
        )
    }
}
