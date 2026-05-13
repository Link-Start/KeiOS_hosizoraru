package os.kei.mcp.server

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.core.system.ShizukuApiUtils
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
            service.bindToolCallLogger { _, _, _, _ -> }
        }
    }
}
