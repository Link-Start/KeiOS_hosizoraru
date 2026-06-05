package os.kei.ui.page.main.mcp

import os.kei.mcp.server.BOOTSTRAP_PROMPT
import os.kei.mcp.server.DIAGNOSTICS_PLAN_PROMPT
import os.kei.mcp.server.SKILL_RESOURCE_URI
import os.kei.mcp.server.SUBAGENT_RESOURCE_URI
import os.kei.mcp.server.WORKFLOW_PLAN_PROMPT
import os.kei.mcp.server.WORKFLOW_RESOURCE_URI
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class McpClawSetupPromptTest {
    @Test
    fun renderClawSetupPromptInjectsConfigAndResourceUris() {
        val prompt =
            renderClawSetupPrompt(
                template =
                    """
                    server={{SERVER_NAME}}
                    endpoint={{ENDPOINT}}
                    config={{MCP_CONFIG_JSON}}
                    skill={{SKILL_RESOURCE_URI}}
                    subAgent={{SUBAGENT_RESOURCE_URI}}
                    workflow={{WORKFLOW_RESOURCE_URI}}
                    bootstrap={{BOOTSTRAP_PROMPT}}
                    workflowPrompt={{WORKFLOW_PROMPT}}
                    diagnostics={{DIAGNOSTICS_PROMPT}}
                    """,
                payload =
                    McpClawSetupPromptPayload(
                        serverName = "KeiOS MCP",
                        endpoint = "http://127.0.0.1:38888/mcp",
                        configJson = """{"mcpServers":{"KeiOS MCP":{"type":"streamablehttp"}}}""",
                    ),
            )

        assertContains(prompt, "server=KeiOS MCP")
        assertContains(prompt, "endpoint=http://127.0.0.1:38888/mcp")
        assertContains(prompt, "\"type\":\"streamablehttp\"")
        assertContains(prompt, "skill=$SKILL_RESOURCE_URI")
        assertContains(prompt, "subAgent=$SUBAGENT_RESOURCE_URI")
        assertContains(prompt, "workflow=$WORKFLOW_RESOURCE_URI")
        assertContains(prompt, "bootstrap=$BOOTSTRAP_PROMPT")
        assertContains(prompt, "workflowPrompt=$WORKFLOW_PLAN_PROMPT")
        assertContains(prompt, "diagnostics=$DIAGNOSTICS_PLAN_PROMPT")
        listOf(
            "{{SERVER_NAME}}",
            "{{ENDPOINT}}",
            "{{MCP_CONFIG_JSON}}",
            "{{SKILL_RESOURCE_URI}}",
            "{{SUBAGENT_RESOURCE_URI}}",
            "{{WORKFLOW_RESOURCE_URI}}",
            "{{BOOTSTRAP_PROMPT}}",
            "{{WORKFLOW_PROMPT}}",
            "{{DIAGNOSTICS_PROMPT}}",
        ).forEach { token ->
            assertFalse(prompt.contains(token), "$token should be rendered")
        }
    }
}
