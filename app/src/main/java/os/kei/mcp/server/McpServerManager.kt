package os.kei.mcp.server

import android.content.Context
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.service.McpKeepAliveService
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.time.Duration.Companion.milliseconds

data class McpServerUiState(
    val running: Boolean = false,
    val runningSinceEpochMs: Long = 0L,
    val host: String = "127.0.0.1",
    val port: Int = 38888,
    val endpointPath: String = "/mcp",
    val allowExternal: Boolean = false,
    val addresses: List<String> = emptyList(),
    val lastError: String? = null,
    val tools: List<McpToolMeta> = emptyList(),
    val authToken: String = "",
    val serverName: String = "KeiOS MCP",
    val connectedClients: Int = 0,
    val logs: List<McpLogEntry> = emptyList()
) {
    val localEndpoint: String
        get() = "http://127.0.0.1:$port$endpointPath"

    val lanEndpoints: List<String>
        get() = addresses.map { "http://$it:$port$endpointPath" }
}

class McpServerManager(
    private val appContext: Context,
    private val localMcpService: LocalMcpService,
    monitorDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) {
    companion object {
        private val SESSION_MONITOR_ACTIVE_DELAY = 5_000.milliseconds

        fun loadSavedCacheSummary(context: Context): String {
            val snapshot = McpServerPrefs.loadSnapshot()
            val tokenState = context.getString(
                if (snapshot.authToken.isBlank()) {
                    R.string.settings_cache_entry_mcp_token_empty
                } else {
                    R.string.settings_cache_entry_mcp_token_saved
                }
            )
            val network = context.getString(
                if (snapshot.allowExternal) {
                    R.string.settings_cache_entry_mcp_network_lan
                } else {
                    R.string.settings_cache_entry_mcp_network_local
                }
            )
            return context.getString(
                R.string.settings_cache_entry_mcp_detail,
                snapshot.port,
                network,
                tokenState
            )
        }

        fun clearSavedCacheOnly() {
            McpServerPrefs.clear()
        }

        fun storageFootprintBytes(): Long = McpServerPrefs.storageFootprintBytes()

        fun actualDataBytes(): Long = McpServerPrefs.actualDataBytes()

        fun configBytesEstimated(): Long = McpServerPrefs.configBytesEstimated()
    }

    private var endpointSession: McpEndpointSession? = null
    private var monitorJob: Job? = null
    private var lastConnectedCount: Int = 0
    private val scope = CoroutineScope(SupervisorJob() + monitorDispatcher)
    private val endpointHost = McpKtorEndpointHost(::appendLog)
    private val logStore by lazy {
        McpRuntimeLogStore { logs ->
        _uiState.value = _uiState.value.copy(logs = logs)
        }
    }
    private val initialPrefsSnapshot = McpServerPrefs.loadSnapshot()
    private val _uiState = MutableStateFlow(
        McpServerUiState(
            port = initialPrefsSnapshot.port,
            allowExternal = initialPrefsSnapshot.allowExternal,
            tools = localMcpService.listLocalTools(),
            authToken = initialPrefsSnapshot.authToken,
            serverName = initialPrefsSnapshot.serverName
        )
    )
    val uiState: StateFlow<McpServerUiState> = _uiState.asStateFlow()

    init {
        localMcpService.bindMcpStateProvider { _uiState.value }
        localMcpService.bindToolCallLogger { name, profile, elapsedMs, success, error ->
            val status = if (success) "ok" else "error"
            val suffix = error?.takeIf { it.isNotBlank() }?.let { " reason=$it" }.orEmpty()
            appendLog(
                "INFO",
                "Tool call $status: name=$name profile=${profile.name} elapsedMs=$elapsedMs$suffix"
            )
        }
    }

    @Synchronized
    fun start(port: Int, allowExternal: Boolean): Result<Unit> {
        if (port !in 1..65535) {
            val message = "${appContext.getString(R.string.common_port_invalid)}: $port"
            _uiState.value = _uiState.value.copy(lastError = message)
            return Result.failure(IllegalArgumentException(message))
        }
        return runCatching {
            ensureAuthToken()
            val host = if (allowExternal) "0.0.0.0" else "127.0.0.1"
            val current = _uiState.value
            if (current.running && current.port == port && current.allowExternal == allowExternal) {
                McpServerRuntimeRegistry.registerRunning(this)
                refreshNow()
                syncKeepAliveNotification(forceStart = false)
                appendLog("INFO", "MCP server already running on $host:$port")
                return@runCatching
            }
            stopInternal()
            ensurePortAvailable(host, port)

            val addresses = if (allowExternal) ipv4Addresses() else emptyList()
            val session = endpointHost.start(
                host = host,
                port = port,
                path = McpServerDefaults.ENDPOINT_PATH,
                expectedTokenProvider = { _uiState.value.authToken },
                allowedHosts = buildAllowedHosts(port = port, allowExternal = allowExternal, addresses = addresses),
                serverFactory = {
                    localMcpService.createRuntimeServer { server ->
                        updateConnectedClientCountAsync(server)
                    }
                }
            )
            endpointSession = session
            McpServerRuntimeRegistry.registerRunning(this)
            lastConnectedCount = 0
            _uiState.value = _uiState.value.copy(
                running = true,
                runningSinceEpochMs = System.currentTimeMillis(),
                host = host,
                port = port,
                allowExternal = allowExternal,
                addresses = addresses,
                connectedClients = 0,
                lastError = null
            )
            McpServerPrefs.savePort(port)
            McpServerPrefs.saveAllowExternal(allowExternal)
            syncKeepAliveNotification(forceStart = true)
            appendLog("INFO", "MCP server started on $host:$port/mcp")
        }.onFailure {
            _uiState.value = _uiState.value.copy(
                running = false,
                runningSinceEpochMs = 0L,
                lastError = it.message ?: it.javaClass.simpleName
            )
            appendLog("ERROR", "Failed to start server: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun regenerateAuthToken() {
        val token = McpServerPrefs.regenerateToken()
        _uiState.value = _uiState.value.copy(authToken = token)
        appendLog("INFO", "Authorization token regenerated")
    }

    @Synchronized
    fun updateServerName(name: String) {
        val fixed = name.trim().ifBlank { McpServerDefaults.SERVER_NAME }
        McpServerPrefs.saveServerName(fixed)
        _uiState.value = _uiState.value.copy(serverName = fixed)
    }

    @Synchronized
    fun resetServerConfigPreservingToken(): Boolean {
        McpServerPrefs.saveServerName(McpServerDefaults.SERVER_NAME)
        McpServerPrefs.savePort(McpServerDefaults.PORT)
        McpServerPrefs.saveAllowExternal(false)
        val running = _uiState.value.running
        _uiState.value = if (running) {
            _uiState.value.copy(
                serverName = McpServerDefaults.SERVER_NAME,
                lastError = null
            )
        } else {
            _uiState.value.copy(
                serverName = McpServerDefaults.SERVER_NAME,
                port = McpServerDefaults.PORT,
                allowExternal = false,
                addresses = emptyList(),
                lastError = null
            )
        }
        if (running) {
            syncKeepAliveNotification(forceStart = false)
        }
        appendLog("INFO", "Server config reset to defaults")
        return running
    }

    @Synchronized
    fun updatePort(port: Int): Result<Unit> {
        if (port !in 1..65535) {
            val message = "${appContext.getString(R.string.common_port_invalid)}: $port"
            _uiState.value = _uiState.value.copy(lastError = message)
            return Result.failure(IllegalArgumentException(message))
        }
        McpServerPrefs.savePort(port)
        val current = _uiState.value
        _uiState.value = if (current.running) {
            current.copy(lastError = null)
        } else {
            current.copy(port = port, lastError = null)
        }
        return Result.success(Unit)
    }

    @Synchronized
    fun updateAllowExternal(allowExternal: Boolean): Result<Unit> {
        McpServerPrefs.saveAllowExternal(allowExternal)
        val current = _uiState.value
        _uiState.value = if (current.running) {
            current.copy(lastError = null)
        } else {
            current.copy(allowExternal = allowExternal, lastError = null)
        }
        return Result.success(Unit)
    }

    fun getSkillMarkdown(): String {
        return localMcpService.getSkillMarkdownForUi()
    }

    fun buildConfigJson(
        url: String? = null,
        includeJsonContentTypeHeader: Boolean = _uiState.value.allowExternal
    ): String {
        val authToken = ensureAuthToken()
        val state = _uiState.value
        val endpoint = url ?: state.localEndpoint
        return McpClientConfigBuilder.buildSingleServerConfig(
            serverName = state.serverName,
            endpoint = endpoint,
            authToken = authToken,
            includeJsonContentTypeHeader = includeJsonContentTypeHeader
        )
    }

    @Synchronized
    fun stop() {
        stopInternal()
        _uiState.value = _uiState.value.copy(
            running = false,
            runningSinceEpochMs = 0L,
            connectedClients = 0,
            lastError = null
        )
        runCatching { McpKeepAliveService.stop(appContext) }
        appendLog("INFO", "MCP server stopped")
    }

    @Synchronized
    fun clearLogs() {
        logStore.clear()
    }

    @Synchronized
    fun refreshNow() {
        val running = _uiState.value.running
        if (_uiState.value.allowExternal) {
            _uiState.value = _uiState.value.copy(addresses = ipv4Addresses())
        }
        if (running) {
            val sessions = endpointSession?.server?.sessions?.size ?: 0
            _uiState.value = _uiState.value.copy(connectedClients = sessions)
            if (sessions > 0) {
                endpointSession?.let(::ensureActiveSessionMonitor)
            }
            syncKeepAliveNotification(forceStart = false)
            appendLog("INFO", "Snapshot refreshed: clients=$sessions")
        } else {
            appendLog("INFO", "Snapshot refreshed: server stopped")
        }
    }

    @Synchronized
    fun refreshAddresses() {
        if (!_uiState.value.allowExternal) return
        _uiState.value = _uiState.value.copy(addresses = ipv4Addresses())
    }

    @Synchronized
    fun refreshNotificationNow() {
        if (!_uiState.value.running) return
        syncKeepAliveNotification(forceStart = false)
    }

    @Synchronized
    fun sendTestNotification(): Result<Unit> {
        val state = _uiState.value
        return runCatching {
            McpNotificationHelper.notifyTest(
                context = appContext,
                serverName = state.serverName,
                running = state.running,
                port = state.port,
                path = state.endpointPath,
                clients = state.connectedClients
            )
            appendLog("INFO", "Test notification sent")
        }.onFailure {
            appendLog("ERROR", "Send test notification failed: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    @Synchronized
    private fun stopInternal() {
        monitorJob?.cancel()
        monitorJob = null
        lastConnectedCount = 0
        val current = endpointSession ?: return
        endpointHost.stopEngine(current)
        scope.launch {
            endpointHost.closeServer(current)
        }
        localMcpService.clearRuntimeServer(current.server)
        endpointSession = null
        McpServerRuntimeRegistry.clearRunning(this)
    }

    @Synchronized
    private fun ensureActiveSessionMonitor(session: McpEndpointSession) {
        if (monitorJob?.isActive == true) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (true) {
                    val count = updateConnectedClientCount(session.server)
                    if (count <= 0) break
                    delay(SESSION_MONITOR_ACTIVE_DELAY)
                }
            } finally {
                synchronized(this@McpServerManager) {
                    if (monitorJob === this.coroutineContext[Job]) {
                        monitorJob = null
                    }
                }
            }
        }
        monitorJob = job
        job.start()
    }

    private fun updateConnectedClientCountAsync(server: io.modelcontextprotocol.kotlin.sdk.server.Server) {
        scope.launch {
            val count = updateConnectedClientCount(server)
            if (count > 0) {
                endpointSession?.takeIf { it.server === server }?.let { ensureActiveSessionMonitor(it) }
            }
        }
    }

    @Synchronized
    private fun updateConnectedClientCount(server: io.modelcontextprotocol.kotlin.sdk.server.Server): Int {
        val count = runCatching { server.sessions.size }.getOrDefault(0)
        if (count == lastConnectedCount) return count
        val old = lastConnectedCount
        lastConnectedCount = count
        _uiState.value = _uiState.value.copy(connectedClients = count)
        syncKeepAliveNotification(forceStart = false)
        if (count > old) {
            appendLog("INFO", "Client connected, online=$count")
        } else {
            appendLog("INFO", "Client disconnected, online=$count")
        }
        return count
    }

    private fun appendLog(level: String, message: String) {
        logStore.append(level = level, message = message)
    }

    private fun ipv4Addresses(): List<String> {
        return runCatching {
            val result = mutableListOf<String>()
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching emptyList()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                if (!network.isUp || network.isLoopback) continue
                val addresses = network.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        result += addr.hostAddress.orEmpty()
                    }
                }
            }
            result.distinct()
        }.getOrDefault(emptyList())
    }

    private fun ensurePortAvailable(host: String, port: Int) {
        runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(host, port))
            }
        }.getOrElse {
            throw IllegalStateException(
                appContext.getString(R.string.mcp_error_port_in_use, port, it.message.orEmpty()),
                it
            )
        }
    }

    private fun buildAllowedHosts(
        port: Int,
        allowExternal: Boolean,
        addresses: List<String>
    ): List<String> {
        return buildList {
            add("127.0.0.1:$port")
            add("localhost:$port")
            if (allowExternal) {
                addresses.forEach { address -> add("$address:$port") }
            }
        }
    }

    private fun syncKeepAliveNotification(forceStart: Boolean) {
        val state = _uiState.value
        if (!state.running) return
        runCatching {
            McpKeepAliveService.startOrUpdate(
                context = appContext,
                serverName = state.serverName,
                running = state.running,
                port = state.port,
                path = state.endpointPath,
                clients = state.connectedClients,
                forceStart = forceStart
            )
        }.onFailure {
            appendLog("WARN", "KeepAlive notification update failed: ${it.message ?: it.javaClass.simpleName}")
        }
    }

    @Synchronized
    private fun ensureAuthToken(): String {
        val current = _uiState.value.authToken
        val token = McpServerPrefs.ensureAuthToken(current)
        if (token != current) {
            _uiState.value = _uiState.value.copy(authToken = token)
        }
        return token
    }
}
