package os.kei.mcp.server

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.core.json.optArray
import os.kei.core.json.optObject
import os.kei.core.json.parseJsonObjectOrNull
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = McpServerTestApp::class, sdk = [35])
class McpKtorEndpointHostTest {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()

    @Test
    fun missingAndWrongTokenReturnUnauthorized() = withRunningEndpoint { port ->
        val missing = postMcp(port = port, token = null)
        val wrong = postMcp(port = port, token = "wrong")

        assertEquals(401, missing.code)
        assertEquals(401, wrong.code)
        missing.close()
        wrong.close()
    }

    @Test
    fun invalidHostReturnsForbiddenAfterAuth() = withRunningEndpoint { port ->
        val response = postMcp(port = port, token = "secret", host = "evil.example")

        assertEquals(403, response.code)
        response.close()
    }

    @Test
    fun authorizedInitializeAndToolsListUseSdkSession() = withRunningEndpoint { port ->
        val initialized = postMcp(port = port, token = "secret")
        val sessionId = initialized.header("mcp-session-id")

        assertEquals(200, initialized.code)
        assertNotNull(sessionId)
        initialized.close()

        val tools = postMcp(
            port = port,
            token = "secret",
            sessionId = sessionId,
            protocolVersion = "2025-11-25",
            body = """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}"""
        )
        val body = tools.body.string()
        val toolArray = body.parseJsonObjectOrNull()
            ?.optObject("result")
            ?.optArray("tools")
            ?: error("tools/list response should include tools")

        assertEquals(200, tools.code)
        assertEquals(1, toolArray.size)
        assertTrue(body.contains("keios.health.ping"))
        tools.close()

        val prompts = postMcp(
            port = port,
            token = "secret",
            sessionId = sessionId,
            protocolVersion = "2025-11-25",
            body = """{"jsonrpc":"2.0","id":3,"method":"prompts/list","params":{}}"""
        )
        val promptsBody = prompts.body.string()
        assertEquals(200, prompts.code)
        assertTrue(promptsBody.contains("keios.mcp.bootstrap"))
        prompts.close()

        val prompt = postMcp(
            port = port,
            token = "secret",
            sessionId = sessionId,
            protocolVersion = "2025-11-25",
            body = """{"jsonrpc":"2.0","id":4,"method":"prompts/get","params":{"name":"keios.mcp.bootstrap","arguments":{}}}"""
        )
        val promptBody = prompt.body.string()
        assertEquals(200, prompt.code)
        assertTrue(promptBody.contains("keios.health.ping"))
        prompt.close()

        val resources = postMcp(
            port = port,
            token = "secret",
            sessionId = sessionId,
            protocolVersion = "2025-11-25",
            body = """{"jsonrpc":"2.0","id":5,"method":"resources/list","params":{}}"""
        )
        val resourcesBody = resources.body.string()
        assertEquals(200, resources.code)
        assertTrue(resourcesBody.contains("keios://skill/overview.txt"))
        resources.close()

        val templates = postMcp(
            port = port,
            token = "secret",
            sessionId = sessionId,
            protocolVersion = "2025-11-25",
            body = """{"jsonrpc":"2.0","id":6,"method":"resources/templates/list","params":{}}"""
        )
        val templatesBody = templates.body.string()
        assertEquals(200, templates.code)
        assertTrue(templatesBody.contains("keios://skill/tool/{tool}"))
        templates.close()

        val resource = postMcp(
            port = port,
            token = "secret",
            sessionId = sessionId,
            protocolVersion = "2025-11-25",
            body = """{"jsonrpc":"2.0","id":7,"method":"resources/read","params":{"uri":"keios://skill/overview.txt"}}"""
        )
        val resourceBody = resource.body.string()
        assertEquals(200, resource.code)
        assertTrue(resourceBody.contains("ok=true"))
        resource.close()
    }

    private fun withRunningEndpoint(block: (port: Int) -> Unit) {
        val port = findFreePort()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            installMcpEndpoint(
                path = "/mcp",
                expectedTokenProvider = { "secret" },
                allowedHosts = listOf("127.0.0.1:$port", "localhost:$port"),
                serverFactory = ::createTestServer,
                logger = { _, _ -> }
            )
        }
        server.start(wait = false)
        try {
            block(port)
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        }
    }

    private fun postMcp(
        port: Int,
        token: String?,
        host: String = "127.0.0.1:$port",
        sessionId: String? = null,
        protocolVersion: String? = null,
        body: String = initializeRequest()
    ): okhttp3.Response {
        val requestBody = body.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/mcp")
            .header("Host", host)
            .header("Accept", "application/json, text/event-stream")
            .apply {
                token?.let { header("Authorization", "Bearer $it") }
                sessionId?.let { header("mcp-session-id", it) }
                protocolVersion?.let { header("mcp-protocol-version", it) }
            }
            .post(requestBody)
            .build()
        return client.newCall(request).execute()
    }

    private fun initializeRequest(): String {
        return """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "initialize",
              "params": {
                "protocolVersion": "2025-11-25",
                "capabilities": {},
                "clientInfo": {
                  "name": "test",
                  "version": "1"
                }
              }
            }
        """.trimIndent()
    }

    private fun createTestServer(): Server {
        return Server(
            serverInfo = Implementation(name = "test", version = "1"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                    prompts = ServerCapabilities.Prompts(listChanged = false),
                    resources = ServerCapabilities.Resources(listChanged = false, subscribe = false)
                )
            )
        ) {
            addMcpTextTool(
                environment = testEnvironment(),
                name = "keios.health.ping"
            ) {
                "pong"
            }
            addPrompt(
                name = "keios.mcp.bootstrap",
                description = "Bootstrap"
            ) { _ ->
                GetPromptResult(
                    description = "Bootstrap",
                    messages = listOf(
                        PromptMessage(
                            role = Role.User,
                            content = TextContent("keios.health.ping")
                        )
                    )
                )
            }
            addResource(
                uri = "keios://skill/overview.txt",
                name = "overview",
                description = "Overview",
                mimeType = MIME_TEXT
            ) { _ ->
                callResource("keios://skill/overview.txt", MIME_TEXT, "ok=true")
            }
            addResourceTemplate(
                uriTemplate = "keios://skill/tool/{tool}",
                name = "tool-help",
                description = "Tool help",
                mimeType = MIME_MARKDOWN
            ) { _, params ->
                callResource(
                    "keios://skill/tool/${params["tool"].orEmpty()}",
                    MIME_MARKDOWN,
                    "# tool"
                )
            }
        }
    }

    private fun testEnvironment(): McpToolEnvironment {
        return McpToolEnvironment(
            appContext = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            shizukuApiUtils = os.kei.core.shizuku.ShizukuApiUtils(),
            appVersionName = "test",
            appVersionCode = 1L,
            appPackageName = "os.kei.test",
            appLabel = "KeiOS",
            stateProvider = { McpServerUiState(authToken = "secret") },
            toolCallLogger = { _, _, _, _, _ -> }
        )
    }

    private fun findFreePort(): Int {
        return ServerSocket(0).use { socket -> socket.localPort }
    }
}
