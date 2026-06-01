package os.kei.ui.page.main.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import os.kei.core.log.AppLogger
import os.kei.feature.webdav.client.WebDavDownloadResult
import os.kei.feature.webdav.client.WebDavError
import os.kei.feature.webdav.client.WebDavSyncClient
import os.kei.feature.webdav.client.WebDavTestConnectionResult
import os.kei.feature.webdav.client.WebDavUploadResult
import os.kei.feature.webdav.model.WebDavConfig
import java.security.MessageDigest

/**
 * Coordinates upload / download / two-way sync for each [WebDavSyncItem].
 *
 * Holds a single [WebDavSyncClient] that is rebuilt only when the connection config changes, so
 * repeated calls reuse the same OkHttp client and auth state. All domain export/import logic is
 * injected via [WebDavSyncDataPort] — the engine never touches a concrete domain store.
 *
 * Every method returns a string-free [WebDavItemOutcome] / [WebDavConnectionOutcome]; turning
 * those into user-facing text is the UI layer's job, which keeps this engine usable from
 * background (non-Compose) callers such as the auto-sync coordinator.
 */
internal class WebDavSyncEngine(
    private val clientFactory: (WebDavConfig) -> WebDavSyncClientBridge = {
        RealWebDavSyncClientBridge(WebDavSyncClient(it))
    },
    private val metadataStore: WebDavSyncMetadataStore = StoreBackedWebDavSyncMetadataStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var cachedConfig: WebDavConfig? = null
    private var cachedClient: WebDavSyncClientBridge? = null

    private suspend fun client(config: WebDavConfig): WebDavSyncClientBridge = mutex.withLock {
        val existing = cachedClient
        if (existing != null && cachedConfig == config) {
            existing
        } else {
            clientFactory(config).also {
                cachedClient = it
                cachedConfig = config
            }
        }
    }

    /** Drop the cached client (e.g. after the user clears or rewrites the config). */
    fun invalidate() {
        cachedClient = null
        cachedConfig = null
    }

    // ── Connection test ────────────────────────────────────────────────

    suspend fun testConnection(config: WebDavConfig): WebDavConnectionOutcome =
        when (val result = client(config).testConnection()) {
            is WebDavTestConnectionResult.Success ->
                WebDavConnectionOutcome(
                    if (result.dirCreated) {
                        WebDavConnectionStatus.SuccessDirCreated
                    } else {
                        WebDavConnectionStatus.Success
                    },
                )
            WebDavTestConnectionResult.AuthFailed ->
                WebDavConnectionOutcome(WebDavConnectionStatus.AuthFailed)
            WebDavTestConnectionResult.PermissionDenied ->
                WebDavConnectionOutcome(WebDavConnectionStatus.PermissionDenied)
            is WebDavTestConnectionResult.NetworkError ->
                WebDavConnectionOutcome(WebDavConnectionStatus.NetworkError, result.message)
            is WebDavTestConnectionResult.InvalidUrl ->
                WebDavConnectionOutcome(WebDavConnectionStatus.InvalidUrl, result.message)
            is WebDavTestConnectionResult.Error ->
                WebDavConnectionOutcome(WebDavConnectionStatus.Unknown, result.message)
        }

    // ── Two-way sync (pull → merge → push) ─────────────────────────────

    /**
     * Reconcile a single item with the remote: pull the remote copy, merge it into local, then
     * push the merged local copy back. Updates the persisted ETag + content hash + last-sync time
     * on success.
     */
    suspend fun sync(config: WebDavConfig, item: WebDavSyncItem, port: WebDavSyncDataPort): WebDavItemOutcome {
        val c = client(config)
        return try {
            when (val download = c.download(item.fileName)) {
                is WebDavDownloadResult.Success -> {
                    val localBefore = port.exportJson()
                    if (contentHash(localBefore) == contentHash(download.content)) {
                        recordSynced(item, download.etag, localBefore)
                        return WebDavItemOutcome(WebDavItemStatus.UpToDate)
                    }
                    // Remote exists → merge it locally, then push the merged result up.
                    port.merge(download.content)
                    val merged = port.exportJson()
                    pushMerged(c, item, port, merged, download.etag)
                }
                WebDavDownloadResult.Empty -> {
                    // No remote copy yet → first push of local data.
                    val local = port.exportJson()
                    pushMerged(
                        c = c,
                        item = item,
                        port = port,
                        content = local,
                        etag = null,
                        statusWhenWritten = WebDavItemStatus.Uploaded,
                    )
                }
                is WebDavDownloadResult.Error -> errorOutcome(download.error)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "sync ${item.name} failed", e)
            WebDavItemOutcome(WebDavItemStatus.Error, e.message)
        }
    }

    private suspend fun pushMerged(
        c: WebDavSyncClientBridge,
        item: WebDavSyncItem,
        port: WebDavSyncDataPort,
        content: String,
        etag: String?,
        statusWhenWritten: WebDavItemStatus = WebDavItemStatus.Merged,
    ): WebDavItemOutcome =
        when (val upload = c.upload(item.fileName, content, etag)) {
            is WebDavUploadResult.Success -> {
                recordSynced(item, upload.etag, content)
                WebDavItemOutcome(statusWhenWritten)
            }
            WebDavUploadResult.Conflict -> {
                // Remote moved under us — pull again, re-merge, and retry once unconditionally.
                when (val retry = c.download(item.fileName)) {
                    is WebDavDownloadResult.Success -> {
                        port.merge(retry.content)
                        val reMerged = port.exportJson()
                        when (val second = c.upload(item.fileName, reMerged, retry.etag)) {
                            is WebDavUploadResult.Success -> {
                                recordSynced(item, second.etag, reMerged)
                                WebDavItemOutcome(WebDavItemStatus.Merged)
                            }
                            else -> WebDavItemOutcome(WebDavItemStatus.ConflictUnresolved)
                        }
                    }
                    else -> WebDavItemOutcome(WebDavItemStatus.ConflictUnresolved)
                }
            }
            is WebDavUploadResult.Error -> errorOutcome(upload.error)
        }

    // ── Manual upload (push local → remote, overwrite) ─────────────────

    suspend fun upload(config: WebDavConfig, item: WebDavSyncItem, port: WebDavSyncDataPort): WebDavItemOutcome {
        val c = client(config)
        return try {
            val local = port.exportJson()
            // Manual upload is an explicit "local wins" action → unconditional write.
            when (val upload = c.upload(item.fileName, local, etag = null)) {
                is WebDavUploadResult.Success -> {
                    recordSynced(item, upload.etag, local)
                    WebDavItemOutcome(WebDavItemStatus.Uploaded)
                }
                WebDavUploadResult.Conflict -> WebDavItemOutcome(WebDavItemStatus.ConflictUnresolved)
                is WebDavUploadResult.Error -> errorOutcome(upload.error)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "upload ${item.name} failed", e)
            WebDavItemOutcome(WebDavItemStatus.Error, e.message)
        }
    }

    // ── Manual download (pull remote → merge into local) ───────────────

    suspend fun download(config: WebDavConfig, item: WebDavSyncItem, port: WebDavSyncDataPort): WebDavItemOutcome {
        val c = client(config)
        return try {
            when (val download = c.download(item.fileName)) {
                is WebDavDownloadResult.Success -> {
                    val localBefore = port.exportJson()
                    if (contentHash(localBefore) == contentHash(download.content)) {
                        recordSynced(item, download.etag, localBefore)
                        return WebDavItemOutcome(WebDavItemStatus.UpToDate)
                    }
                    port.merge(download.content)
                    // Re-export so the stored hash reflects the post-merge local state.
                    recordSynced(item, download.etag, port.exportJson())
                    WebDavItemOutcome(WebDavItemStatus.Downloaded)
                }
                WebDavDownloadResult.Empty -> WebDavItemOutcome(WebDavItemStatus.RemoteEmpty)
                is WebDavDownloadResult.Error -> errorOutcome(download.error)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "download ${item.name} failed", e)
            WebDavItemOutcome(WebDavItemStatus.Error, e.message)
        }
    }

    // ── Refresh remote summary (read-only) ─────────────────────────────

    /**
     * Fetch the remote payload for a single item and report what's on the server *without*
     * mutating local state. Used by the manual refresh action so other devices can see whether
     * the remote has data, how many items it holds, and how stale it is before choosing
     * Sync / Upload / Download.
     */
    suspend fun probeRemote(
        config: WebDavConfig,
        item: WebDavSyncItem,
        port: WebDavSyncDataPort,
    ): WebDavRemoteProbeOutcome {
        val c = client(config)
        return try {
            val nowMs = nowMillis()
            when (val download = c.download(item.fileName)) {
                is WebDavDownloadResult.Success -> {
                    val itemCount = runCatching { port.countRemoteItems(download.content) }
                        .getOrElse { -1 }
                    val byteSize = download.content.toByteArray(Charsets.UTF_8).size.toLong()
                    metadataStore.saveRemoteSummaryFound(
                        item = item,
                        itemCount = itemCount,
                        byteSize = byteSize,
                        etag = download.etag,
                        probedAtMs = nowMs,
                    )
                    WebDavRemoteProbeOutcome.Found(
                        itemCount = itemCount,
                        byteSize = byteSize,
                        etag = download.etag,
                    )
                }
                WebDavDownloadResult.Empty -> {
                    metadataStore.saveRemoteSummaryEmpty(item, nowMs)
                    WebDavRemoteProbeOutcome.Empty
                }
                is WebDavDownloadResult.Error -> WebDavRemoteProbeOutcome.Error(
                    when (download.error) {
                        WebDavError.AuthFailed -> WebDavItemStatus.AuthFailed
                        WebDavError.PermissionDenied -> WebDavItemStatus.PermissionDenied
                        WebDavError.NetworkUnreachable -> WebDavItemStatus.NetworkError
                        is WebDavError.Unknown -> WebDavItemStatus.Error
                    },
                    detail = (download.error as? WebDavError.Unknown)?.message,
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "probeRemote ${item.name} failed", e)
            WebDavRemoteProbeOutcome.Error(WebDavItemStatus.Error, e.message)
        }
    }

    private fun recordSynced(item: WebDavSyncItem, etag: String?, content: String) {
        metadataStore.setItemEtag(item, etag)
        metadataStore.setItemContentHash(item, contentHash(content))
        metadataStore.setLastSyncTime(item, nowMillis())
    }

    private fun errorOutcome(error: WebDavError): WebDavItemOutcome = when (error) {
        WebDavError.NetworkUnreachable -> WebDavItemOutcome(WebDavItemStatus.NetworkError)
        WebDavError.AuthFailed -> WebDavItemOutcome(WebDavItemStatus.AuthFailed)
        WebDavError.PermissionDenied -> WebDavItemOutcome(WebDavItemStatus.PermissionDenied)
        is WebDavError.Unknown -> WebDavItemOutcome(WebDavItemStatus.Error, error.message)
    }

    companion object {
        private const val TAG = "WebDavSyncEngine"

        /** Stable content fingerprint used to detect real local changes for auto-sync. */
        fun contentHash(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

internal interface WebDavSyncClientBridge {
    suspend fun testConnection(): WebDavTestConnectionResult
    suspend fun upload(fileName: String, content: String, etag: String? = null): WebDavUploadResult
    suspend fun download(fileName: String): WebDavDownloadResult
}

private class RealWebDavSyncClientBridge(
    private val delegate: WebDavSyncClient,
) : WebDavSyncClientBridge {
    override suspend fun testConnection(): WebDavTestConnectionResult = delegate.testConnection()

    override suspend fun upload(
        fileName: String,
        content: String,
        etag: String?,
    ): WebDavUploadResult = delegate.upload(fileName, content, etag)

    override suspend fun download(fileName: String): WebDavDownloadResult =
        delegate.download(fileName)
}

internal interface WebDavSyncMetadataStore {
    fun setItemEtag(item: WebDavSyncItem, etag: String?)
    fun setItemContentHash(item: WebDavSyncItem, hash: String)
    fun setLastSyncTime(item: WebDavSyncItem, timeMs: Long)
    fun saveRemoteSummaryFound(
        item: WebDavSyncItem,
        itemCount: Int,
        byteSize: Long,
        etag: String?,
        probedAtMs: Long,
    )

    fun saveRemoteSummaryEmpty(item: WebDavSyncItem, probedAtMs: Long)
}

private object StoreBackedWebDavSyncMetadataStore : WebDavSyncMetadataStore {
    override fun setItemEtag(item: WebDavSyncItem, etag: String?) {
        WebDavSyncStore.setItemEtag(item, etag)
    }

    override fun setItemContentHash(item: WebDavSyncItem, hash: String) {
        WebDavSyncStore.setItemContentHash(item, hash)
    }

    override fun setLastSyncTime(item: WebDavSyncItem, timeMs: Long) {
        WebDavSyncStore.setLastSyncTime(item, timeMs)
    }

    override fun saveRemoteSummaryFound(
        item: WebDavSyncItem,
        itemCount: Int,
        byteSize: Long,
        etag: String?,
        probedAtMs: Long,
    ) {
        WebDavSyncStore.saveRemoteSummaryFound(item, itemCount, byteSize, etag, probedAtMs)
    }

    override fun saveRemoteSummaryEmpty(item: WebDavSyncItem, probedAtMs: Long) {
        WebDavSyncStore.saveRemoteSummaryEmpty(item, probedAtMs)
    }
}

// ── Outcome types (UI-string-free) ─────────────────────────────────────

internal enum class WebDavConnectionStatus {
    Success,
    SuccessDirCreated,
    AuthFailed,
    PermissionDenied,
    NetworkError,
    InvalidUrl,
    Unknown,
}

internal data class WebDavConnectionOutcome(
    val status: WebDavConnectionStatus,
    val detail: String? = null,
) {
    val isSuccess: Boolean
        get() = status == WebDavConnectionStatus.Success || status == WebDavConnectionStatus.SuccessDirCreated
}

internal enum class WebDavItemStatus {
    Uploaded,
    Downloaded,
    Merged,
    UpToDate,
    RemoteEmpty,
    AuthFailed,
    PermissionDenied,
    NetworkError,
    ConflictUnresolved,
    Error,
}

internal data class WebDavItemOutcome(
    val status: WebDavItemStatus,
    val detail: String? = null,
) {
    val isSuccess: Boolean
        get() = when (status) {
            WebDavItemStatus.Uploaded,
            WebDavItemStatus.Downloaded,
            WebDavItemStatus.Merged,
            WebDavItemStatus.UpToDate,
            WebDavItemStatus.RemoteEmpty,
            -> true
            else -> false
        }
}

/**
 * Outcome of probing a single item's remote payload (read-only). [Found] carries the parsed
 * item count + byte size so the UI can render a remote summary; [Empty] means the file isn't
 * on the server yet; [Error] surfaces auth / permission / network failures so the UI can
 * localise them through the same enum vocabulary as sync outcomes.
 */
internal sealed interface WebDavRemoteProbeOutcome {
    data class Found(
        val itemCount: Int,
        val byteSize: Long,
        val etag: String?,
    ) : WebDavRemoteProbeOutcome
    data object Empty : WebDavRemoteProbeOutcome
    data class Error(val status: WebDavItemStatus, val detail: String?) : WebDavRemoteProbeOutcome
}

/**
 * Bridge between a sync item and its domain store.
 *
 * - [exportJson] serialises the current local state.
 * - [merge] folds a remote JSON payload into local state (union-merge, never a destructive
 *   replace) so two-way sync can never silently drop local-only data.
 * - [localCount] returns the current item count in local state. Used by the UI to render
 *   "<local> / <remote>" diffs and by the upload-safety guard.
 * - [countRemoteItems] parses a downloaded JSON payload and returns its item count *without*
 *   touching local state. Used by the refresh-remote-summary flow so other devices can see
 *   what's on the server before deciding to sync.
 */
internal data class WebDavSyncDataPort(
    val exportJson: () -> String,
    val merge: (remoteJson: String) -> Unit,
    val localCount: () -> Int,
    val countRemoteItems: (raw: String) -> Int,
)
