package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal data class McpClientSessionCountChange(
    val oldCount: Int,
    val newCount: Int,
) {
    val connected: Boolean
        get() = newCount > oldCount
}

internal class McpEndpointController(
    private val endpointHost: McpKtorEndpointHost,
    private val scope: CoroutineScope,
    private val onSessionCountChanged: (McpClientSessionCountChange) -> Unit,
) {
    companion object {
        private val SESSION_MONITOR_SETTLE_DELAYS = longArrayOf(1_000L, 3_000L, 8_000L)
        private val SESSION_MONITOR_CALIBRATION_DELAY = 60_000.milliseconds
    }

    private var endpointSession: McpEndpointSession? = null
    private var monitorJob: Job? = null
    private var lastConnectedCount: Int = 0

    @Synchronized
    fun start(
        host: String,
        port: Int,
        path: String,
        expectedTokenProvider: () -> String,
        allowedHosts: List<String>,
        serverFactory: () -> Server,
    ): McpEndpointSession {
        val session =
            endpointHost.start(
                host = host,
                port = port,
                path = path,
                expectedTokenProvider = expectedTokenProvider,
                allowedHosts = allowedHosts,
                serverFactory = serverFactory,
            )
        endpointSession = session
        lastConnectedCount = 0
        monitorJob?.cancel()
        monitorJob = null
        return session
    }

    @Synchronized
    fun stop(onServerClosed: (Server) -> Unit): Boolean {
        monitorJob?.cancel()
        monitorJob = null
        lastConnectedCount = 0
        val current = endpointSession ?: return false
        endpointHost.stopEngine(current)
        scope.launch {
            endpointHost.closeServer(current)
        }
        onServerClosed(current.server)
        endpointSession = null
        return true
    }

    @Synchronized
    fun refreshClientCount(): Int {
        val server = endpointSession?.server ?: return 0
        val count = updateConnectedClientCountLocked(server)
        if (count > 0) {
            ensureActiveSessionMonitorLocked(server)
        }
        return count
    }

    fun updateConnectedClientCountAsync(server: Server) {
        scope.launch {
            synchronized(this@McpEndpointController) {
                if (endpointSession?.server !== server) return@synchronized
                val count = updateConnectedClientCountLocked(server)
                if (count > 0) {
                    ensureActiveSessionMonitorLocked(server)
                }
            }
        }
    }

    private fun ensureActiveSessionMonitorLocked(server: Server) {
        if (monitorJob?.isActive == true) return
        val job =
            scope.launch {
                try {
                    SESSION_MONITOR_SETTLE_DELAYS.forEach { delayMs ->
                        delay(delayMs.milliseconds)
                        val count =
                            synchronized(this@McpEndpointController) {
                                if (endpointSession?.server !== server) return@synchronized 0
                                updateConnectedClientCountLocked(server)
                            }
                        if (count <= 0) return@launch
                    }
                    while (true) {
                        delay(SESSION_MONITOR_CALIBRATION_DELAY)
                        val count =
                            synchronized(this@McpEndpointController) {
                                if (endpointSession?.server !== server) return@synchronized 0
                                updateConnectedClientCountLocked(server)
                            }
                        if (count <= 0) break
                    }
                } finally {
                    synchronized(this@McpEndpointController) {
                        if (monitorJob === this.coroutineContext[Job]) {
                            monitorJob = null
                        }
                    }
                }
            }
        monitorJob = job
    }

    private fun updateConnectedClientCountLocked(server: Server): Int {
        val count = runCatching { server.sessions.size }.getOrDefault(0)
        if (count == lastConnectedCount) return count
        val old = lastConnectedCount
        lastConnectedCount = count
        onSessionCountChanged(McpClientSessionCountChange(oldCount = old, newCount = count))
        return count
    }
}
