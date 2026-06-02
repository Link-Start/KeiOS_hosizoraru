package os.kei.ui.page.main.mcp.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.R
import os.kei.core.json.KeiJson
import os.kei.core.json.encodeCompact
import os.kei.mcp.server.McpServerUiState

@Composable
internal fun formatMcpUptimeText(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L)
    val days = totalMinutes / 1_440L
    val hours = (totalMinutes % 1_440L) / 60L
    val minutes = totalMinutes % 60L
    return when {
        days > 0L && minutes == 0L -> stringResource(R.string.mcp_uptime_days_hours, days, hours)
        days > 0L -> stringResource(R.string.mcp_uptime_days_hours_minutes, days, hours, minutes)
        hours > 0L && minutes == 0L -> stringResource(R.string.mcp_uptime_hours, hours)
        hours > 0L -> stringResource(R.string.mcp_uptime_hours_minutes, hours, minutes)
        else -> stringResource(R.string.mcp_uptime_minutes, minutes)
    }
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun buildMcpLogsExportJson(
    generatedAt: String,
    state: McpServerUiState
): String {
    return buildJsonObject {
        put("schema", "keios.mcp.logs.v1")
        put("generatedAt", generatedAt)
        put("serverName", state.serverName)
        put("running", state.running)
        put("port", state.port)
        put("endpointPath", state.endpointPath)
        put("allowExternal", state.allowExternal)
        put("connectedClients", state.connectedClients)
        put("logCount", state.logs.size)
        put(
            "logs",
            buildJsonArray {
                state.logs.forEach { log ->
                    add(
                        buildJsonObject {
                            put("time", log.time)
                            put("level", log.level)
                            put("message", log.message)
                        }
                    )
                }
            }
        )
    }.encodeCompact(KeiJson.pretty)
}
