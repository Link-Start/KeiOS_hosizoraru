package os.kei.ui.page.main.mcp

import os.kei.mcp.server.BOOTSTRAP_PROMPT
import os.kei.mcp.server.DIAGNOSTICS_PLAN_PROMPT
import os.kei.mcp.server.SKILL_RESOURCE_URI
import os.kei.mcp.server.SUBAGENT_RESOURCE_URI
import os.kei.mcp.server.WORKFLOW_PLAN_PROMPT
import os.kei.mcp.server.WORKFLOW_RESOURCE_URI

internal data class McpClawSetupPromptPayload(
    val serverName: String,
    val endpoint: String,
    val configJson: String,
    val skillResourceUri: String = SKILL_RESOURCE_URI,
    val subAgentResourceUri: String = SUBAGENT_RESOURCE_URI,
    val workflowResourceUri: String = WORKFLOW_RESOURCE_URI,
    val bootstrapPrompt: String = BOOTSTRAP_PROMPT,
    val workflowPrompt: String = WORKFLOW_PLAN_PROMPT,
    val diagnosticsPrompt: String = DIAGNOSTICS_PLAN_PROMPT,
)

internal fun renderClawSetupPrompt(
    template: String,
    payload: McpClawSetupPromptPayload,
): String =
    template
        .trimIndent()
        .replace("{{SERVER_NAME}}", payload.serverName)
        .replace("{{ENDPOINT}}", payload.endpoint)
        .replace("{{MCP_CONFIG_JSON}}", payload.configJson.trim())
        .replace("{{SKILL_RESOURCE_URI}}", payload.skillResourceUri)
        .replace("{{SUBAGENT_RESOURCE_URI}}", payload.subAgentResourceUri)
        .replace("{{WORKFLOW_RESOURCE_URI}}", payload.workflowResourceUri)
        .replace("{{BOOTSTRAP_PROMPT}}", payload.bootstrapPrompt)
        .replace("{{WORKFLOW_PROMPT}}", payload.workflowPrompt)
        .replace("{{DIAGNOSTICS_PROMPT}}", payload.diagnosticsPrompt)
        .trim()
