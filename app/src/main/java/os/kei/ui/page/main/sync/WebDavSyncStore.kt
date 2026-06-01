package os.kei.ui.page.main.sync

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
    private const val KEY_LAST_REMOTE_PROBE = "last_remote_probe"

    const val DEFAULT_REMOTE_DIR = "KeiOS/"

    private val mmkv: MMKV get() = KeiMmkv.byId(STORE_ID)

    // ── Connection config ──────────────────────────────────────────────

    fun saveConfig(config: WebDavConfig, provider: WebDavProvider) {
        mmkv.encode(KEY_PROVIDER, provider.name)
        mmkv.encode(KEY_SERVER_URL, config.serverUrl)
        mmkv.encode(KEY_USERNAME, config.username)
        mmkv.encode(KEY_APP_PASSWORD, config.appPassword)
        mmkv.encode(KEY_REMOTE_DIR, config.remoteDir)
        WebDavSyncStoreSignals.notifyChanged()
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
                remoteCountKey(item),
                remoteByteSizeKey(item),
                remoteEtagKey(item),
                remoteProbedAtKey(item),
                remoteEmptyKey(item),
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
                KEY_LAST_REMOTE_PROBE,
            ) + itemKeys).toTypedArray(),
        )
        WebDavSyncStoreSignals.notifyChanged()
    }

    // ── Auto-sync preference ───────────────────────────────────────────

    fun isAutoSyncEnabled(): Boolean = mmkv.decodeBool(KEY_AUTO_SYNC, false)

    fun setAutoSyncEnabled(enabled: Boolean) {
        mmkv.encode(KEY_AUTO_SYNC, enabled)
        WebDavSyncStoreSignals.notifyChanged()
    }

    // ── Per-item enabled flag ──────────────────────────────────────────

    fun isItemEnabled(item: WebDavSyncItem): Boolean =
        mmkv.decodeBool(enabledKey(item), true)

    fun setItemEnabled(item: WebDavSyncItem, enabled: Boolean) {
        mmkv.encode(enabledKey(item), enabled)
        WebDavSyncStoreSignals.notifyChanged()
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
        WebDavSyncStoreSignals.notifyChanged()
    }

    // ── Remote summary (read-only probe results) ───────────────────────
    //
    // Captured by [WebDavSyncEngine.probeRemote] without touching local state. Lets the page
    // show "remote: <count> items, <size>, modified <time>" so other devices can decide how to
    // reconcile before pressing Sync / Upload / Download.

    fun getLastRemoteProbeTime(): Long = mmkv.decodeLong(KEY_LAST_REMOTE_PROBE, 0L)

    fun setLastRemoteProbeTime(timeMs: Long) {
        mmkv.encode(KEY_LAST_REMOTE_PROBE, timeMs)
    }

    fun loadRemoteSummary(item: WebDavSyncItem): WebDavRemoteSummary? {
        val probedAtMs = mmkv.decodeLong(remoteProbedAtKey(item), 0L)
        if (probedAtMs <= 0L) return null
        return WebDavRemoteSummary(
            empty = mmkv.decodeBool(remoteEmptyKey(item), false),
            itemCount = mmkv.decodeInt(remoteCountKey(item), -1),
            byteSize = mmkv.decodeLong(remoteByteSizeKey(item), -1L),
            etag = mmkv.decodeString(remoteEtagKey(item), null)?.takeIf { it.isNotBlank() },
            probedAtMs = probedAtMs,
        )
    }

    fun saveRemoteSummaryFound(
        item: WebDavSyncItem,
        itemCount: Int,
        byteSize: Long,
        etag: String?,
        probedAtMs: Long,
    ) {
        mmkv.encode(remoteEmptyKey(item), false)
        mmkv.encode(remoteCountKey(item), itemCount)
        mmkv.encode(remoteByteSizeKey(item), byteSize)
        if (etag.isNullOrBlank()) {
            mmkv.removeValueForKey(remoteEtagKey(item))
        } else {
            mmkv.encode(remoteEtagKey(item), etag)
        }
        mmkv.encode(remoteProbedAtKey(item), probedAtMs)
    }

    fun saveRemoteSummaryEmpty(item: WebDavSyncItem, probedAtMs: Long) {
        mmkv.encode(remoteEmptyKey(item), true)
        mmkv.encode(remoteCountKey(item), 0)
        mmkv.encode(remoteByteSizeKey(item), 0L)
        mmkv.removeValueForKey(remoteEtagKey(item))
        mmkv.encode(remoteProbedAtKey(item), probedAtMs)
    }

    private fun enabledKey(item: WebDavSyncItem) = "enabled_${item.name}"
    private fun lastSyncKey(item: WebDavSyncItem) = "last_sync_${item.name}"
    private fun etagKey(item: WebDavSyncItem) = "etag_${item.name}"
    private fun hashKey(item: WebDavSyncItem) = "hash_${item.name}"
    private fun remoteCountKey(item: WebDavSyncItem) = "remote_count_${item.name}"
    private fun remoteByteSizeKey(item: WebDavSyncItem) = "remote_size_${item.name}"
    private fun remoteEtagKey(item: WebDavSyncItem) = "remote_etag_${item.name}"
    private fun remoteProbedAtKey(item: WebDavSyncItem) = "remote_probed_at_${item.name}"
    private fun remoteEmptyKey(item: WebDavSyncItem) = "remote_empty_${item.name}"
}

internal object WebDavSyncStoreSignals {
    val version = MutableStateFlow(0L)

    fun notifyChanged() {
        version.update { current -> current + 1L }
    }
}

/**
 * Read-only snapshot of what's on the remote for a single item, captured by
 * [WebDavSyncEngine.probeRemote] and persisted by the store. [empty] means the file isn't on
 * the server yet (so [itemCount] / [byteSize] are 0); otherwise the values reflect the most
 * recent successful probe.
 */
internal data class WebDavRemoteSummary(
    val empty: Boolean,
    val itemCount: Int,
    val byteSize: Long,
    val etag: String?,
    val probedAtMs: Long,
)
