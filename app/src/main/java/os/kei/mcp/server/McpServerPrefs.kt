package os.kei.mcp.server

import com.tencent.mmkv.MMKV
import java.security.SecureRandom

internal data class McpPrefsSnapshot(
    val authToken: String = "",
    val serverName: String = McpServerDefaults.SERVER_NAME,
    val port: Int = McpServerDefaults.PORT,
    val allowExternal: Boolean = false
)

internal object McpServerDefaults {
    const val SERVER_NAME = "KeiOS MCP"
    const val PORT = 38888
    const val ENDPOINT_PATH = "/mcp"
}

internal object McpServerPrefs {
    private const val KV_ID = "mcp_server_prefs"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_SERVER_NAME = "server_name"
    private const val KEY_PORT = "port"
    private const val KEY_ALLOW_EXTERNAL = "allow_external"
    private val random = SecureRandom()
    private val store: MMKV by lazy { MMKV.mmkvWithID(KV_ID) }

    fun loadSnapshot(): McpPrefsSnapshot {
        val port = store.decodeInt(KEY_PORT, McpServerDefaults.PORT).let { value ->
            if (value in 1..65535) value else McpServerDefaults.PORT
        }
        return McpPrefsSnapshot(
            authToken = store.decodeString(KEY_AUTH_TOKEN).orEmpty(),
            serverName = store.decodeString(
                KEY_SERVER_NAME,
                McpServerDefaults.SERVER_NAME
            ).orEmpty().ifBlank { McpServerDefaults.SERVER_NAME },
            port = port,
            allowExternal = store.decodeBool(KEY_ALLOW_EXTERNAL, false)
        )
    }

    fun ensureAuthToken(current: String): String {
        if (current.isNotBlank()) return current
        val generated = generateToken()
        saveAuthToken(generated)
        return generated
    }

    fun saveAuthToken(token: String) {
        store.encode(KEY_AUTH_TOKEN, token)
    }

    fun saveServerName(name: String) {
        store.encode(KEY_SERVER_NAME, name)
    }

    fun savePort(port: Int) {
        if (port in 1..65535) {
            store.encode(KEY_PORT, port)
        }
    }

    fun saveAllowExternal(allowExternal: Boolean) {
        store.encode(KEY_ALLOW_EXTERNAL, allowExternal)
    }

    fun regenerateToken(): String {
        val token = generateToken()
        saveAuthToken(token)
        return token
    }

    fun clear() {
        store.removeValueForKey(KEY_AUTH_TOKEN)
        store.removeValueForKey(KEY_SERVER_NAME)
        store.removeValueForKey(KEY_PORT)
        store.removeValueForKey(KEY_ALLOW_EXTERNAL)
        store.trim()
    }

    fun storageFootprintBytes(): Long = store.totalSize()

    fun actualDataBytes(): Long = store.actualSize()

    fun configBytesEstimated(): Long {
        val snapshot = loadSnapshot()
        return (snapshot.authToken.length + snapshot.serverName.length).toLong() * 2 + 32L
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
