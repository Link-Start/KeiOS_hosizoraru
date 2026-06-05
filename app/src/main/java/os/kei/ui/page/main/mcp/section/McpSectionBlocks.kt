@file:Suppress("FunctionName")

package os.kei.ui.page.main.mcp.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.mcp.server.McpLogEntry
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.core.MiuixInfoItem
import os.kei.ui.page.main.widget.glass.AppLiquidExpandableSection
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MCP_LOG_INLINE_LIMIT = 12
@Composable
internal fun McpOnboardingGuideSection(
    backdrop: LayerBackdrop,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCopyCurrentConfig: () -> Unit,
    onCopySkillResource: () -> Unit,
    onCopySubAgentResource: () -> Unit,
    onCopyWorkflowResource: () -> Unit,
) {
    AppLiquidExpandableSection(
        backdrop = backdrop,
        title = stringResource(R.string.mcp_onboarding_card_title),
        subtitle = stringResource(R.string.mcp_onboarding_card_subtitle),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        headerStartAction = {
            McpSectionHeaderIcon(
                icon = appLucideInfoIcon(),
                contentDescription = stringResource(R.string.mcp_onboarding_card_title),
            )
        },
    ) {
        McpOnboardingActionRow(
            title = stringResource(R.string.mcp_onboarding_step_server_title),
            summary = stringResource(R.string.mcp_onboarding_step_server_summary),
            copyContentDescription = stringResource(R.string.mcp_action_copy_current_config),
            onCopy = onCopyCurrentConfig,
        )
        McpOnboardingActionRow(
            title = stringResource(R.string.mcp_onboarding_step_skill_title),
            summary = stringResource(R.string.mcp_onboarding_step_skill_summary),
            copyContentDescription = stringResource(R.string.mcp_action_copy_skill_resource),
            onCopy = onCopySkillResource,
        )
        McpOnboardingActionRow(
            title = stringResource(R.string.mcp_onboarding_step_subagent_title),
            summary = stringResource(R.string.mcp_onboarding_step_subagent_summary),
            copyContentDescription = stringResource(R.string.mcp_action_copy_subagent_resource),
            onCopy = onCopySubAgentResource,
        )
        McpOnboardingActionRow(
            title = stringResource(R.string.mcp_onboarding_step_workflow_title),
            summary = stringResource(R.string.mcp_onboarding_step_workflow_summary),
            copyContentDescription = stringResource(R.string.mcp_action_copy_workflow_resource),
            onCopy = onCopyWorkflowResource,
        )
        McpServiceControlHint(
            title = stringResource(R.string.mcp_onboarding_step_upgrade_title),
            summary = stringResource(R.string.mcp_onboarding_step_upgrade_summary),
        )
    }
}

@Composable
private fun McpOnboardingActionRow(
    title: String,
    summary: String,
    copyContentDescription: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.88f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        AppCompactIconAction(
            icon = osLucideCopyIcon(),
            contentDescription = copyContentDescription,
            tint = MiuixTheme.colorScheme.primary,
            minSize = 40.dp,
            onClick = onCopy,
        )
    }
}

@Composable
internal fun McpServiceControlSection(
    backdrop: LayerBackdrop,
    expanded: Boolean,
    contentVisible: Boolean = true,
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
        expanded = expanded && contentVisible,
        onExpandedChange = onExpandedChange,
        headerStartAction = {
            McpSectionHeaderIcon(
                icon = appLucideConfigIcon(),
                contentDescription = stringResource(R.string.mcp_section_service_control_title),
            )
        },
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
                    onClick = onSendTestNotification,
                )
            },
            second = { modifier ->
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.SheetDangerAction,
                    text = stringResource(R.string.mcp_action_reset_service_config),
                    textColor = MiuixTheme.colorScheme.error,
                    modifier = modifier,
                    onClick = onShowResetConfigConfirm,
                )
            },
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
                    onClick = onCopySkillResource,
                )
            },
            second = { modifier ->
                AppLiquidTextButton(
                    backdrop = backdrop,
                    variant = GlassVariant.Compact,
                    text = stringResource(R.string.mcp_action_copy_workflow_resource),
                    modifier = modifier,
                    textColor = MiuixTheme.colorScheme.primary,
                    onClick = onCopyWorkflowResource,
                )
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        McpServiceControlHint(
            title = stringResource(R.string.mcp_action_copy_skill_resource),
            summary = stringResource(R.string.mcp_action_copy_skill_resource_summary),
        )
        McpServiceControlHint(
            title = stringResource(R.string.mcp_action_copy_workflow_resource),
            summary = stringResource(R.string.mcp_action_copy_workflow_resource_summary),
        )
    }
}

@Composable
private fun McpServiceControlHint(
    title: String,
    summary: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.88f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
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
                contentDescription = stringResource(R.string.mcp_section_logs_title),
            )
        },
        headerActions = {
            AppCompactIconAction(
                icon =
                    if (logsExporting) {
                        appLucideRefreshIcon()
                    } else {
                        appLucideDownloadIcon()
                    },
                contentDescription = stringResource(R.string.mcp_action_export_logs),
                tint =
                    if (logsExporting) {
                        subtitleColor
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                enabled = !logsExporting,
                onClick = {
                    val generatedAt =
                        SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss.SSS",
                            Locale.getDefault(),
                        ).format(Date())
                    val exportStamp =
                        SimpleDateFormat(
                            "yyyyMMdd-HHmmss-SSS",
                            Locale.getDefault(),
                        ).format(Date())
                    onExportLogs(generatedAt, "keios-mcp-logs-$exportStamp.json")
                },
            )
        },
    ) {
        val logs = uiState.logs
        if (logs.isEmpty()) {
            MiuixInfoItem(
                key = stringResource(R.string.mcp_log_label),
                value = stringResource(R.string.mcp_log_empty),
            )
        } else {
            McpLogRows(logs = logs)
        }
        Spacer(modifier = Modifier.height(8.dp))
        AppLiquidTextButton(
            backdrop = backdrop,
            variant = GlassVariant.Content,
            text = stringResource(R.string.mcp_action_clear_logs),
            onClick = onClearLogs,
        )
    }
}

@Composable
private fun McpLogRows(logs: List<McpLogEntry>) {
    val displayLogs =
        remember(logs) {
            logs.mapIndexed { index, log -> IndexedMcpLog(index, log) }.asReversed()
        }
    if (displayLogs.size <= MCP_LOG_INLINE_LIMIT) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            displayLogs.forEach { item ->
                McpLogRow(log = item.log)
            }
        }
        return
    }
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = displayLogs,
            key = { item -> item.stableKey },
            contentType = { "mcp_log_entry" },
        ) { item ->
            McpLogRow(log = item.log)
        }
    }
}

@Composable
private fun McpLogRow(log: McpLogEntry) {
    MiuixInfoItem(
        key = "${log.time} [${log.level}]",
        value = log.message,
    )
}

private data class IndexedMcpLog(
    val sourceIndex: Int,
    val log: McpLogEntry,
) {
    val stableKey: String = "$sourceIndex:${log.time}:${log.level}:${log.message.hashCode()}:${log.message.length}"
}
