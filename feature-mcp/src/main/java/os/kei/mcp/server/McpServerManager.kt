package os.kei.mcp.server

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import os.kei.feature.mcp.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.service.McpKeepAliveService
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

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
    monitorDispatcher: CoroutineDispatcher = AppDispatchers.mcpServer
) {
    companion object {
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

        private const val LOG_PUBLISH_INTERVAL_MS = 500L
    }

    private val scope = CoroutineScope(SupervisorJob() + monitorDispatcher)
    private val prefsLoadLock = Any()
    @Volatile
    private var prefsLoaded = false
    private val _uiState = MutableStateFlow(
        McpServerUiState(
            port = McpServerDefaults.PORT,
            allowExternal = false,
            tools = localMcpService.listLocalTools(),
            authToken = "",
            serverName = McpServerDefaults.SERVER_NAME
        )
    )
    val uiState: StateFlow<McpServerUiState> = _uiState.asStateFlow()
    private val endpointController =
        McpEndpointController(
            endpointHost = McpKtorEndpointHost(::appendLog),
            scope = scope,
            onSessionCountChanged = ::handleSessionCountChanged
        )
    private val logStore by lazy {
        McpRuntimeLogStore(
            minPublishIntervalMs = LOG_PUBLISH_INTERVAL_MS,
            publishScope = scope
        ) { logs ->
            _uiState.update { state -> state.copy(logs = logs) }
        }
    }

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
        loadPrefsAsync()
    }

    private fun loadPrefsAsync() {
        scope.launch {
            val snapshot = McpServerPrefs.loadSnapshot()
            synchronized(prefsLoadLock) {
                if (!prefsLoaded) {
                    applyPrefsSnapshot(snapshot)
                    prefsLoaded = true
                }
            }
        }
    }

    private fun ensurePrefsLoadedForCommand() {
        if (prefsLoaded) return
        synchronized(prefsLoadLock) {
            if (prefsLoaded) return
            applyPrefsSnapshot(McpServerPrefs.loadSnapshot())
            prefsLoaded = true
        }
    }

    private fun applyPrefsSnapshot(snapshot: McpPrefsSnapshot) {
        _uiState.update { state ->
            if (state.running) {
                state.copy(
                    authToken = snapshot.authToken,
                    serverName = snapshot.serverName,
                )
            } else {
                state.copy(
                    port = snapshot.port,
                    allowExternal = snapshot.allowExternal,
                    authToken = snapshot.authToken,
                    serverName = snapshot.serverName,
                )
            }
        }
    }

    @Synchronized
    fun start(port: Int, allowExternal: Boolean): Result<Unit> {
        ensurePrefsLoadedForCommand()
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
            endpointController.start(
                host = host,
                port = port,
                path = McpServerDefaults.ENDPOINT_PATH,
                expectedTokenProvider = { _uiState.value.authToken },
                allowedHosts = buildAllowedHosts(port = port, allowExternal = allowExternal, addresses = addresses),
                serverFactory = {
                    localMcpService.createRuntimeServer { server ->
                        endpointController.updateConnectedClientCountAsync(server)
                    }
                }
            )
            McpServerRuntimeRegistry.registerRunning(this)
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
        ensurePrefsLoadedForCommand()
        val token = McpServerPrefs.regenerateToken()
        _uiState.value = _uiState.value.copy(authToken = token)
        appendLog("INFO", "Authorization token regenerated")
    }

    @Synchronized
    fun updateServerName(name: String) {
        ensurePrefsLoadedForCommand()
        val fixed = name.trim().ifBlank { McpServerDefaults.SERVER_NAME }
        McpServerPrefs.saveServerName(fixed)
        _uiState.value = _uiState.value.copy(serverName = fixed)
    }

    @Synchronized
    fun resetServerConfigPreservingToken(): Boolean {
        ensurePrefsLoadedForCommand()
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
        ensurePrefsLoadedForCommand()
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
        ensurePrefsLoadedForCommand()
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
        includeJsonContentTypeHeader: Boolean? = null
    ): String {
        ensurePrefsLoadedForCommand()
        val authToken = ensureAuthToken()
        val state = _uiState.value
        val endpoint = url ?: state.localEndpoint
        val includeHeader = includeJsonContentTypeHeader ?: state.allowExternal
        return McpClientConfigBuilder.buildSingleServerConfig(
            serverName = state.serverName,
            endpoint = endpoint,
            authToken = authToken,
            includeJsonContentTypeHeader = includeHeader
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
            val sessions = endpointController.refreshClientCount()
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
        val stopped =
            endpointController.stop { server ->
                localMcpService.clearRuntimeServer(server)
            }
        if (stopped) {
            McpServerRuntimeRegistry.clearRunning(this)
        }
    }

    private fun handleSessionCountChanged(change: McpClientSessionCountChange) {
        _uiState.update { state -> state.copy(connectedClients = change.newCount) }
        syncKeepAliveNotification(forceStart = false)
        if (change.connected) {
            appendLog("INFO", "Client connected, online=${change.newCount}")
        } else {
            appendLog("INFO", "Client disconnected, online=${change.newCount}")
        }
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
        ensurePrefsLoadedForCommand()
        val current = _uiState.value.authToken
        val token = McpServerPrefs.ensureAuthToken(current)
        if (token != current) {
            _uiState.value = _uiState.value.copy(authToken = token)
        }
        return token
    }
}
