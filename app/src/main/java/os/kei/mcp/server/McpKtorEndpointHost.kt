package os.kei.mcp.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

internal data class McpEndpointSession(
    val engine: EmbeddedServer<*, *>,
    val server: Server
)

internal class McpKtorEndpointHost(
    private val logger: (level: String, message: String) -> Unit
) {
    fun start(
        host: String,
        port: Int,
        path: String,
        expectedTokenProvider: () -> String,
        allowedHosts: List<String>,
        serverFactory: () -> Server
    ): McpEndpointSession {
        val server = serverFactory()
        val engine = embeddedServer(
            factory = CIO,
            host = host,
            port = port
        ) {
            installMcpEndpoint(
                path = path,
                expectedTokenProvider = expectedTokenProvider,
                allowedHosts = allowedHosts,
                serverFactory = { server },
                logger = logger
            )
        }
        engine.start(wait = false)
        return McpEndpointSession(engine = engine, server = server)
    }

    fun stop(session: McpEndpointSession?) {
        val current = session ?: return
        runCatching { current.engine.stop(gracePeriodMillis = 500, timeoutMillis = 2_000) }
        runBlocking {
            withTimeoutOrNull(1_500) {
                current.server.close()
            }
        }
    }
}

internal fun Application.installMcpEndpoint(
    path: String,
    expectedTokenProvider: () -> String,
    allowedHosts: List<String>,
    serverFactory: () -> Server,
    logger: (level: String, message: String) -> Unit
) {
    intercept(ApplicationCallPipeline.Plugins) {
        val appCall = context
        val requestPath = appCall.request.path()
        if (!requestPath.startsWith(path)) return@intercept

        val authHeaderRaw = appCall.request.headers["Authorization"].orEmpty()
        val providedToken = McpEndpointAuth.extractBearerToken(authHeaderRaw)
        val expectedToken = expectedTokenProvider()
        if (!McpEndpointAuth.constantTimeEquals(expected = expectedToken, provided = providedToken)) {
            val mode = McpEndpointAuth.describeAuthHeader(authHeaderRaw)
            logger("WARN", "Rejected unauthorized request: path=$requestPath auth=$mode")
            appCall.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            finish()
            return@intercept
        }
    }
    mcpStreamableHttp(
        path = path,
        enableDnsRebindingProtection = true,
        allowedHosts = allowedHosts.distinct()
    ) {
        serverFactory()
    }
}
