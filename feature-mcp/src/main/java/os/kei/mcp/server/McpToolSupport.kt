package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

const val DEFAULT_TOPINFO_LIMIT = 120
const val MAX_TOPINFO_LIMIT = 300
const val DEFAULT_LOG_LIMIT = 80
const val MAX_LOG_LIMIT = 200
const val DEFAULT_TRACK_LIMIT = 80
const val MAX_TRACK_LIMIT = 400
const val DEFAULT_ENTRY_LIMIT = 12
const val MAX_ENTRY_LIMIT = 200

const val MIME_MARKDOWN = "text/markdown"
const val MIME_TEXT = "text/plain"
const val MIME_JSON = "application/json"

const val SKILL_RESOURCE_URI = "keios://skill/keios-mcp.md"
const val SKILL_OVERVIEW_URI = "keios://skill/overview.txt"
const val SKILL_TOOL_TEMPLATE_URI = "keios://skill/tool/{tool}"
const val SKILL_DOMAIN_TEMPLATE_URI = "keios://skill/domain/{domain}"
const val SUBAGENT_RESOURCE_URI = "keios://skill/subagent.md"
const val WORKFLOW_RESOURCE_URI = "keios://skill/workflows.md"
const val WORKFLOW_TEMPLATE_URI = "keios://skill/workflow/{workflow}"
const val CONFIG_RESOURCE_URI = "keios://mcp/config/default.json"
const val CONFIG_TEMPLATE_URI = "keios://mcp/config/{mode}.json"
const val BOOTSTRAP_PROMPT = "keios.mcp.bootstrap"
const val WORKFLOW_PLAN_PROMPT = "keios.mcp.workflow.plan"
const val DIAGNOSTICS_PLAN_PROMPT = "keios.mcp.diagnostics.plan"
const val DEFAULT_ENDPOINT = "http://127.0.0.1:38888/mcp"

internal fun callText(text: String): CallToolResult {
    return CallToolResult(content = listOf(TextContent(text)))
}

internal fun callResource(uri: String, mimeType: String, text: String): ReadResourceResult {
    return ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                uri = uri,
                mimeType = mimeType,
                text = text
            )
        )
    )
}

fun argString(value: Any?): String {
    return (value as? JsonPrimitive)?.contentOrNull.orEmpty()
}

fun argInt(value: Any?, defaultValue: Int): Int {
    return argString(value).trim().toIntOrNull() ?: defaultValue
}

fun argIntOrNull(value: Any?): Int? {
    return argString(value).trim().toIntOrNull()
}

fun argBoolean(value: Any?, defaultValue: Boolean): Boolean {
    val raw = argString(value).trim().lowercase(Locale.ROOT)
    return when (raw) {
        "1", "true", "yes", "y", "on" -> true
        "0", "false", "no", "n", "off" -> false
        else -> defaultValue
    }
}

fun normalizeMcpConfigMode(raw: String): String {
    return when (raw.trim().lowercase(Locale.ROOT)) {
        "local" -> "local"
        "lan" -> "lan"
        else -> "auto"
    }
}
