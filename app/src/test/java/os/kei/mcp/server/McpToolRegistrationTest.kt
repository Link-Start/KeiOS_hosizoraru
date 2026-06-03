package os.kei.mcp.server

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.core.shizuku.ShizukuApiUtils
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = McpServerTestApp::class, sdk = [35])
class McpToolRegistrationTest {
    @Test
    fun registeredToolsMatchCatalog() {
        val service = createService()
        val server = service.createRuntimeServer()

        assertEquals(
            McpToolCatalog.all.map { it.name }.toSet(),
            server.tools.keys
        )
    }

    @Test
    fun catalogSchemasAndAnnotationsReachSdkTools() {
        val service = createService()
        val server = service.createRuntimeServer()

        assertTrue("keios.mcp.workflow.blueprints" in server.tools.keys)
        assertTrue(WORKFLOW_PLAN_PROMPT in server.prompts.keys)
        assertTrue(DIAGNOSTICS_PLAN_PROMPT in server.prompts.keys)
        assertTrue(WORKFLOW_RESOURCE_URI in server.resources.keys)
        assertTrue(server.resourceTemplates.any { it.uriTemplate == WORKFLOW_TEMPLATE_URI })
        assertTrue(server.resourceTemplates.any { it.uriTemplate == SKILL_DOMAIN_TEMPLATE_URI })

        val listTool = server.tools.getValue("keios.github.tracks.list").tool
        val listProperties = listTool.inputSchema.properties.orEmpty()
        assertTrue("sourceMode" in listProperties.keys)
        assertTrue("filterMode" in listProperties.keys)
        assertTrue("sortDirection" in listProperties.keys)
        val sourceModeSchema = listProperties.getValue("sourceMode").toString()
        assertTrue(sourceModeSchema.contains("github_repository"))
        assertTrue(sourceModeSchema.contains("git_repository"))
        assertTrue(sourceModeSchema.contains("direct_apk"))
        val filterModeSchema = listProperties.getValue("filterMode").toString()
        assertTrue(filterModeSchema.contains("pre_release_tracked"))
        assertTrue(filterModeSchema.contains("git_repository"))
        assertTrue(filterModeSchema.contains("default"))
        assertTrue(listTool.meta.toString().contains("keios/group"))
        assertTrue(listTool.meta.toString().contains("keios/visibility"))
        assertTrue(listTool.meta.toString().contains("keios/maturity"))
        assertTrue(listTool.meta.toString().contains("keios/recommendedFor"))
        assertTrue(listTool.meta.toString().contains("keios/arguments"))

        val importTool = server.tools.getValue("keios.github.tracks.import").tool
        assertEquals(listOf("json"), importTool.inputSchema.required)
        assertFalse(importTool.annotations?.readOnlyHint ?: true)
        assertFalse(importTool.annotations?.idempotentHint ?: true)

        val searchTool = server.tools.getValue("keios.github.discovery.search").tool
        assertEquals(listOf("query"), searchTool.inputSchema.required)
        assertTrue(searchTool.annotations?.readOnlyHint ?: false)
        assertTrue(searchTool.annotations?.openWorldHint ?: false)

        val directApkTool = server.tools.getValue("keios.github.direct_apk.inspect").tool
        assertEquals(listOf("url"), directApkTool.inputSchema.required)
        assertTrue(directApkTool.annotations?.openWorldHint ?: false)

        val actionsTool = server.tools.getValue("keios.github.actions.recommended").tool
        assertFalse(actionsTool.annotations?.readOnlyHint ?: true)
        assertTrue(actionsTool.annotations?.openWorldHint ?: false)

        val workflowTool = server.tools.getValue("keios.mcp.workflow.blueprints").tool
        assertTrue(workflowTool.annotations?.readOnlyHint ?: false)
        assertFalse(workflowTool.annotations?.openWorldHint ?: true)
        assertTrue(workflowTool.meta.toString().contains("keios/entrypoint"))
        assertTrue(workflowTool.meta.toString().contains("keios/visibility"))
        assertTrue(workflowTool.meta.toString().contains("workflow"))
        assertTrue(workflowTool.meta.toString().contains("true"))

        val pingTool = server.tools.getValue("keios.health.ping").tool
        assertEquals(listOf("format", "text"), pingTool.outputSchema?.required?.sorted())

        val codexTool = server.tools.getValue("keios.dev.codex.config").tool
        assertTrue(codexTool.annotations?.readOnlyHint ?: false)
        assertFalse(codexTool.annotations?.openWorldHint ?: true)
        assertEquals(listOf("format", "text"), codexTool.outputSchema?.required?.sorted())
        assertTrue(codexTool.meta.toString().contains("codex-development"))
    }

    @Test
    fun skillMarkdownDocumentsCurrentGitHubTrackingOptions() {
        val service = createService()

        val markdown = service.getSkillMarkdownForUi()

        assertTrue(markdown.contains("filterMode"))
        assertTrue(markdown.contains("pre_release"))
        assertTrue(markdown.contains("git_repository"))
        assertTrue(markdown.contains("keios.github.tracked/v3"))
        assertTrue(markdown.contains("actionsUpdateIntervalMode"))
        assertTrue(markdown.contains("follow_global"))
        assertTrue(markdown.contains("3h"))
        assertTrue(markdown.contains(WORKFLOW_PLAN_PROMPT))
        assertTrue(markdown.contains(DIAGNOSTICS_PLAN_PROMPT))
        assertTrue(markdown.contains(WORKFLOW_RESOURCE_URI))
        assertTrue(markdown.contains(SKILL_DOMAIN_TEMPLATE_URI))
        assertTrue(markdown.contains("keios.mcp.workflow.blueprints"))
        assertTrue(markdown.contains("keios.dev.codex.config"))
        assertTrue(markdown.contains("keios://skill/domain/dev"))
        assertTrue(markdown.indexOf("Recommended Entry Points") < markdown.indexOf("Full Tool Index"))
    }

    private fun createService(): LocalMcpService {
        return LocalMcpService(
            appContext = ApplicationProvider.getApplicationContext(),
            shizukuApiUtils = ShizukuApiUtils(),
            appVersionName = "test",
            appVersionCode = 1L,
            appPackageName = "os.kei.test",
            appLabel = "KeiOS"
        ).also { service ->
            service.bindMcpStateProvider {
                McpServerUiState(
                    running = true,
                    authToken = "token",
                    serverName = "KeiOS MCP",
                    tools = service.listLocalTools()
                )
            }
            service.bindToolCallLogger { _, _, _, _, _ -> }
        }
    }
}
