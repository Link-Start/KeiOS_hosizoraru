package os.kei.mcp.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
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
            environment = applicationEnvironment(),
            configure = {
                configureMcpCioEndpoint(host = host, port = port)
            }
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

    fun stopEngine(session: McpEndpointSession?) {
        val current = session ?: return
        runCatching { current.engine.stop(gracePeriodMillis = 500, timeoutMillis = 2_000) }
    }

    suspend fun closeServer(session: McpEndpointSession?) {
        val current = session ?: return
        withTimeoutOrNull(1_500) {
            current.server.close()
        }
    }
}

internal fun CIOApplicationEngine.Configuration.configureMcpCioEndpoint(host: String, port: Int) {
    connectionIdleTimeoutSeconds = MCP_CIO_IDLE_TIMEOUT_SECONDS
    reuseAddress = true
    connectionGroupSize = MCP_CIO_CONNECTION_GROUP_SIZE
    workerGroupSize = MCP_CIO_WORKER_GROUP_SIZE
    callGroupSize = MCP_CIO_CALL_GROUP_SIZE
    shutdownGracePeriod = MCP_CIO_SHUTDOWN_GRACE_PERIOD_MS
    shutdownTimeout = MCP_CIO_SHUTDOWN_TIMEOUT_MS
    connector {
        this.host = host
        this.port = port
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
        if (!requestPath.isMcpEndpointPath(path)) return@intercept

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

private fun String.isMcpEndpointPath(basePath: String): Boolean {
    val fixedBasePath = basePath.trimEnd('/')
    return this == fixedBasePath || startsWith("$fixedBasePath/")
}

internal const val MCP_CIO_IDLE_TIMEOUT_SECONDS = 12
internal const val MCP_CIO_CONNECTION_GROUP_SIZE = 1
internal const val MCP_CIO_WORKER_GROUP_SIZE = 1
internal const val MCP_CIO_CALL_GROUP_SIZE = 3
internal const val MCP_CIO_SHUTDOWN_GRACE_PERIOD_MS = 250L
internal const val MCP_CIO_SHUTDOWN_TIMEOUT_MS = 1_500L
