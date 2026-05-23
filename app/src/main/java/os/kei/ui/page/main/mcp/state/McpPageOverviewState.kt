package os.kei.ui.page.main.mcp.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import os.kei.R
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.mcp.model.McpOverviewMetric
import os.kei.ui.page.main.mcp.model.toMcpTokenPreview
import os.kei.ui.page.main.mcp.util.formatMcpUptimeText

@Immutable
internal data class McpPageOverviewState(
    val overviewAccentColor: Color,
    val overviewCardColor: Color,
    val overviewBorderColor: Color,
    val runtimeText: String,
    val overviewMetrics: List<McpOverviewMetric>
)

@Composable
internal fun rememberMcpPageOverviewState(
    context: Context,
    uiState: McpServerUiState,
    runtimeNowMs: Long,
    isDark: Boolean,
    titleColor: Color,
    subtitleColor: Color,
    runningColor: Color,
    stoppedColor: Color,
    runtimePendingText: String
): McpPageOverviewState {
    val overviewAccentColor = if (uiState.running) runningColor else stoppedColor
    val overviewCardColor = if (isDark) {
        overviewAccentColor.copy(alpha = 0.16f)
    } else {
        overviewAccentColor.copy(alpha = 0.10f)
    }
    val overviewBorderColor = if (isDark) {
        overviewAccentColor.copy(alpha = 0.32f)
    } else {
        overviewAccentColor.copy(alpha = 0.26f)
    }
    val runtimeText = if (!uiState.running || uiState.runningSinceEpochMs <= 0L) {
        runtimePendingText
    } else {
        formatMcpUptimeText(runtimeNowMs - uiState.runningSinceEpochMs)
    }
    val bindAddress = remember(uiState.allowExternal, uiState.addresses) {
        when {
            !uiState.allowExternal -> "127.0.0.1"
            uiState.addresses.isNotEmpty() -> uiState.addresses.first()
            else -> "0.0.0.0"
        }
    }
    val tokenPreview = remember(uiState.authToken) {
        uiState.authToken.toMcpTokenPreview()
    }.ifBlank { context.getString(R.string.common_na) }
    val overviewMetrics = remember(
        context,
        uiState.serverName,
        uiState.endpointPath,
        uiState.port,
        uiState.allowExternal,
        uiState.connectedClients,
        bindAddress,
        tokenPreview,
        runningColor,
        subtitleColor,
        titleColor
    ) {
        listOf(
            McpOverviewMetric(
                label = context.getString(R.string.mcp_overview_label_service_short),
                value = uiState.serverName.ifBlank { context.getString(R.string.mcp_default_service_name) },
                spanFullWidth = true,
                valueMaxLines = 1,
                labelWeight = 0.24f,
                valueWeight = 0.76f
            ),
            McpOverviewMetric(
                label = context.getString(R.string.mcp_overview_label_endpoint_short),
                value = uiState.endpointPath,
                valueMaxLines = 1,
                labelWeight = 0.34f,
                valueWeight = 0.66f
            ),
            McpOverviewMetric(
                label = context.getString(R.string.mcp_overview_label_bind_short),
                value = bindAddress,
                valueMaxLines = 1,
                labelWeight = 0.24f,
                valueWeight = 0.76f
            ),
            McpOverviewMetric(
                label = context.getString(R.string.mcp_overview_label_port_short),
                value = uiState.port.toString()
            ),
            McpOverviewMetric(
                label = context.getString(R.string.mcp_overview_label_network_short),
                value = if (uiState.allowExternal) {
                    context.getString(R.string.mcp_network_mode_lan_short)
                } else {
                    context.getString(R.string.mcp_network_mode_local_only_short)
                }
            ),
            McpOverviewMetric(
                label = context.getString(R.string.mcp_overview_label_clients_short),
                value = context.getString(R.string.mcp_clients_count, uiState.connectedClients),
                valueColor = if (uiState.connectedClients > 0) runningColor else subtitleColor,
                valueMaxLines = 1
            ),
            McpOverviewMetric(
                label = context.getString(R.string.mcp_overview_label_token_short),
                value = tokenPreview,
                valueColor = titleColor,
                valueMaxLines = 1,
                labelWeight = 0.34f,
                valueWeight = 0.66f
            )
        )
    }
    return remember(
        overviewAccentColor,
        overviewCardColor,
        overviewBorderColor,
        runtimeText,
        overviewMetrics
    ) {
        McpPageOverviewState(
            overviewAccentColor = overviewAccentColor,
            overviewCardColor = overviewCardColor,
            overviewBorderColor = overviewBorderColor,
            runtimeText = runtimeText,
            overviewMetrics = overviewMetrics
        )
    }
}
