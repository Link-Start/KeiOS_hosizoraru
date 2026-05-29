package os.kei.ui.page.main.sync

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv

/**
 * Persists WebDAV config and per-item sync state in MMKV.
 */
internal object WebDavSyncStore {
    private const val STORE_ID = "webdav_sync"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_APP_PASSWORD = "app_password"
    private const val KEY_REMOTE_DIR = "remote_dir"
    private const val KEY_PROVIDER = "provider"

    private val mmkv: MMKV get() = KeiMmkv.byId(STORE_ID)

    // ── Config ─────────────────────────────────────────────────────

    fun saveConfig(serverUrl: String, username: String, appPassword: String, remoteDir: String) {
        mmkv.encode(KEY_SERVER_URL, serverUrl)
        mmkv.encode(KEY_USERNAME, username)
        mmkv.encode(KEY_APP_PASSWORD, appPassword)
        mmkv.encode(KEY_REMOTE_DIR, remoteDir)
    }

    fun loadConfig(): WebDavSyncConfig? {
        val url = mmkv.decodeString(KEY_SERVER_URL, null).orEmpty()
        val user = mmkv.decodeString(KEY_USERNAME, null).orEmpty()
        val pass = mmkv.decodeString(KEY_APP_PASSWORD, null).orEmpty()
        if (url.isBlank() || user.isBlank() || pass.isBlank()) return null
        return WebDavSyncConfig(
            serverUrl = url,
            username = user,
            appPassword = pass,
            remoteDir = mmkv.decodeString(KEY_REMOTE_DIR, "KeiOS/").orEmpty(),
        )
    }

    fun clearConfig() {
        mmkv.removeValuesForKeys(
            arrayOf(KEY_SERVER_URL, KEY_USERNAME, KEY_APP_PASSWORD, KEY_REMOTE_DIR, KEY_PROVIDER),
        )
    }

    fun hasConfig(): Boolean = loadConfig() != null

    // ── Per-item enabled state ─────────────────────────────────────

    fun isItemEnabled(item: WebDavSyncItem): Boolean =
        mmkv.decodeBool("enabled_${item.name}", true)

    fun setItemEnabled(item: WebDavSyncItem, enabled: Boolean) {
        mmkv.encode("enabled_${item.name}", enabled)
    }

    // ── Per-item last sync time ────────────────────────────────────

    fun getLastSyncTime(item: WebDavSyncItem): Long =
        mmkv.decodeLong("last_sync_${item.name}", 0L)

    fun setLastSyncTime(item: WebDavSyncItem, timeMs: Long) {
        mmkv.encode("last_sync_${item.name}", timeMs)
    }

    // ── Last full sync time ────────────────────────────────────────

    fun getLastFullSyncTime(): Long = mmkv.decodeLong("last_full_sync", 0L)

    fun setLastFullSyncTime(timeMs: Long) {
        mmkv.encode("last_full_sync", timeMs)
    }
}

internal data class WebDavSyncConfig(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    val remoteDir: String = "KeiOS/",
)
