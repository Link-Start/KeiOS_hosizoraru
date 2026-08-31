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
import kotlin.time.Duration.Companion.seconds

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
        allowedHosts = allowedHosts.distinct(),
        // Without this an SSE stream carrying nothing is indistinguishable from a dead socket, and
        // [MCP_CIO_IDLE_TIMEOUT_SECONDS] reaps it after 12 seconds -- while a DeepScan tool is allowed
        // to run for 60. A caller waiting on a long tool lost the stream it was waiting on, four times
        // over, before the answer existed. kotlin-sdk 0.15.0 added the hook; Ktor sends the comment
        // frame and the connection stays live because it is genuinely carrying bytes.
        //
        // Deliberately a short period rather than a longer idle timeout: raising the timeout would keep
        // genuinely dead sockets on a phone alive for a minute, and the point of the 12 seconds is that
        // they do not. Heartbeating under it keeps live streams live and still reaps dead ones fast.
        sseHeartbeatConfig = { period = MCP_SSE_HEARTBEAT_PERIOD }
    ) {
        serverFactory()
    }
}

private fun String.isMcpEndpointPath(basePath: String): Boolean {
    val fixedBasePath = basePath.trimEnd('/')
    return this == fixedBasePath || startsWith("$fixedBasePath/")
}

internal const val MCP_CIO_IDLE_TIMEOUT_SECONDS = 12

/**
 * SSE keep-alive period, which must stay comfortably under [MCP_CIO_IDLE_TIMEOUT_SECONDS].
 *
 * Two heartbeats fit inside the idle window, so a single dropped frame does not reap a live stream.
 */
internal val MCP_SSE_HEARTBEAT_PERIOD = 5.seconds
internal const val MCP_CIO_CONNECTION_GROUP_SIZE = 1
internal const val MCP_CIO_WORKER_GROUP_SIZE = 1
internal const val MCP_CIO_CALL_GROUP_SIZE = 3
internal const val MCP_CIO_SHUTDOWN_GRACE_PERIOD_MS = 250L
internal const val MCP_CIO_SHUTDOWN_TIMEOUT_MS = 1_500L
