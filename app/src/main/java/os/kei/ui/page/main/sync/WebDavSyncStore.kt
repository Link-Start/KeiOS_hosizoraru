package os.kei.ui.page.main.sync

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import os.kei.feature.webdav.model.WebDavConfig

/**
 * Persists WebDAV connection config and per-item sync metadata in MMKV.
 *
 * The persisted [WebDavConfig] is the single source of truth — there is no separate
 * UI-layer config model. Per-item metadata (enabled flag, last-sync time, remote ETag and
 * the hash of the last-synced local content) lives alongside it so the sync engine can make
 * conditional writes and the auto-sync coordinator can detect real local changes.
 */
internal object WebDavSyncStore {
    private const val STORE_ID = "webdav_sync"

    private const val KEY_PROVIDER = "provider"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_APP_PASSWORD = "app_password"
    private const val KEY_REMOTE_DIR = "remote_dir"
    private const val KEY_AUTO_SYNC = "auto_sync"
    private const val KEY_LAST_FULL_SYNC = "last_full_sync"

    const val DEFAULT_REMOTE_DIR = "KeiOS/"

    private val mmkv: MMKV get() = KeiMmkv.byId(STORE_ID)

    // ── Connection config ──────────────────────────────────────────────

    fun saveConfig(config: WebDavConfig, provider: WebDavProvider) {
        mmkv.encode(KEY_PROVIDER, provider.name)
        mmkv.encode(KEY_SERVER_URL, config.serverUrl)
        mmkv.encode(KEY_USERNAME, config.username)
        mmkv.encode(KEY_APP_PASSWORD, config.appPassword)
        mmkv.encode(KEY_REMOTE_DIR, config.remoteDir)
    }

    fun loadConfig(): WebDavConfig? {
        val url = mmkv.decodeString(KEY_SERVER_URL, null).orEmpty()
        val user = mmkv.decodeString(KEY_USERNAME, null).orEmpty()
        val pass = mmkv.decodeString(KEY_APP_PASSWORD, null).orEmpty()
        if (url.isBlank() || user.isBlank() || pass.isBlank()) return null
        return WebDavConfig(
            serverUrl = url,
            username = user,
            appPassword = pass,
            remoteDir = mmkv.decodeString(KEY_REMOTE_DIR, DEFAULT_REMOTE_DIR).orEmpty()
                .ifBlank { DEFAULT_REMOTE_DIR },
        )
    }

    fun loadProvider(): WebDavProvider {
        val raw = mmkv.decodeString(KEY_PROVIDER, null)
        return WebDavProvider.entries.firstOrNull { it.name == raw } ?: WebDavProvider.Jianguoyun
    }

    fun hasConfig(): Boolean = loadConfig() != null

    fun clearConfig() {
        val itemKeys = WebDavSyncItem.entries.flatMap { item ->
            listOf(
                enabledKey(item),
                lastSyncKey(item),
                etagKey(item),
                hashKey(item),
            )
        }
        mmkv.removeValuesForKeys(
            (listOf(
                KEY_PROVIDER,
                KEY_SERVER_URL,
                KEY_USERNAME,
                KEY_APP_PASSWORD,
                KEY_REMOTE_DIR,
                KEY_AUTO_SYNC,
                KEY_LAST_FULL_SYNC,
            ) + itemKeys).toTypedArray(),
        )
    }

    // ── Auto-sync preference ───────────────────────────────────────────

    fun isAutoSyncEnabled(): Boolean = mmkv.decodeBool(KEY_AUTO_SYNC, false)

    fun setAutoSyncEnabled(enabled: Boolean) {
        mmkv.encode(KEY_AUTO_SYNC, enabled)
    }

    // ── Per-item enabled flag ──────────────────────────────────────────

    fun isItemEnabled(item: WebDavSyncItem): Boolean =
        mmkv.decodeBool(enabledKey(item), true)

    fun setItemEnabled(item: WebDavSyncItem, enabled: Boolean) {
        mmkv.encode(enabledKey(item), enabled)
    }

    // ── Per-item last sync time ────────────────────────────────────────

    fun getLastSyncTime(item: WebDavSyncItem): Long =
        mmkv.decodeLong(lastSyncKey(item), 0L)

    fun setLastSyncTime(item: WebDavSyncItem, timeMs: Long) {
        mmkv.encode(lastSyncKey(item), timeMs)
    }

    // ── Per-item remote ETag (conditional writes) ──────────────────────

    fun getItemEtag(item: WebDavSyncItem): String? =
        mmkv.decodeString(etagKey(item), null)?.takeIf { it.isNotBlank() }

    fun setItemEtag(item: WebDavSyncItem, etag: String?) {
        if (etag.isNullOrBlank()) {
            mmkv.removeValueForKey(etagKey(item))
        } else {
            mmkv.encode(etagKey(item), etag)
        }
    }

    // ── Per-item local content hash (auto-sync change detection) ───────

    fun getItemContentHash(item: WebDavSyncItem): String? =
        mmkv.decodeString(hashKey(item), null)?.takeIf { it.isNotBlank() }

    fun setItemContentHash(item: WebDavSyncItem, hash: String) {
        mmkv.encode(hashKey(item), hash)
    }

    // ── Last full sync time ────────────────────────────────────────────

    fun getLastFullSyncTime(): Long = mmkv.decodeLong(KEY_LAST_FULL_SYNC, 0L)

    fun setLastFullSyncTime(timeMs: Long) {
        mmkv.encode(KEY_LAST_FULL_SYNC, timeMs)
    }

    private fun enabledKey(item: WebDavSyncItem) = "enabled_${item.name}"
    private fun lastSyncKey(item: WebDavSyncItem) = "last_sync_${item.name}"
    private fun etagKey(item: WebDavSyncItem) = "etag_${item.name}"
    private fun hashKey(item: WebDavSyncItem) = "hash_${item.name}"
}
