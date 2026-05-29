package os.kei.ui.page.main.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.core.log.AppLogger
import os.kei.feature.webdav.client.WebDavClient
import os.kei.feature.webdav.model.WebDavConfig
import os.kei.feature.webdav.model.WebDavResult

/**
 * Coordinates upload/download for each sync item.
 * Export/import logic is injected via [DataPort] lambdas so this class
 * has no direct dependency on domain stores.
 */
internal class WebDavSyncRepository {
    private var client: WebDavClient? = null

    fun configure(config: WebDavConfig) {
        client = WebDavClient(config)
    }

    fun isConfigured(): Boolean = client != null

    // ── Single item sync ───────────────────────────────────────────

    suspend fun syncItem(
        item: WebDavSyncItem,
        exportJson: () -> String,
        importJson: (String) -> Unit,
    ): WebDavSyncItemResult {
        val c = client ?: return WebDavSyncItemResult.Error("Not configured")
        return withContext(Dispatchers.IO) {
            try {
                // Upload: export local → PUT
                val localJson = exportJson()
                when (val putResult = c.put(item.fileName, localJson)) {
                    is WebDavResult.Success -> {
                        WebDavSyncItemResult.Synced(
                            uploaded = true,
                            downloaded = false,
                            etag = putResult.etag,
                        )
                    }
                    is WebDavResult.Failure -> {
                        AppLogger.w(TAG, "Upload ${item.fileName} failed: ${putResult.code}")
                        WebDavSyncItemResult.Error("Upload failed: ${putResult.message}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Sync ${item.fileName} exception", e)
                WebDavSyncItemResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Upload ─────────────────────────────────────────────────────

    suspend fun upload(
        item: WebDavSyncItem,
        exportJson: () -> String,
    ): WebDavSyncItemResult {
        val c = client ?: return WebDavSyncItemResult.Error("Not configured")
        return withContext(Dispatchers.IO) {
            try {
                val json = exportJson()
                when (val result = c.put(item.fileName, json)) {
                    is WebDavResult.Success -> WebDavSyncItemResult.Synced(true, false, result.etag)
                    is WebDavResult.Failure -> WebDavSyncItemResult.Error("Upload failed: ${result.message}")
                }
            } catch (e: Exception) {
                WebDavSyncItemResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Download ───────────────────────────────────────────────────

    suspend fun download(
        item: WebDavSyncItem,
        importJson: (String) -> Unit,
    ): WebDavSyncItemResult {
        val c = client ?: return WebDavSyncItemResult.Error("Not configured")
        return withContext(Dispatchers.IO) {
            try {
                when (val result = c.get(item.fileName)) {
                    is WebDavResult.Success -> {
                        val body = result.body
                        if (body.isNullOrBlank()) {
                            WebDavSyncItemResult.RemoteEmpty
                        } else {
                            importJson(body)
                            WebDavSyncItemResult.Synced(false, true, result.etag)
                        }
                    }
                    is WebDavResult.Failure -> {
                        if (result.code == 404) {
                            WebDavSyncItemResult.RemoteEmpty
                        } else {
                            WebDavSyncItemResult.Error("Download failed: ${result.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                WebDavSyncItemResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Test connection ────────────────────────────────────────────

    suspend fun testConnection(): WebDavTestResult {
        val c = client ?: return WebDavTestResult(false, "Not configured")
        return when (val result = c.testConnection()) {
            is WebDavResult.Success -> WebDavTestResult(true, "OK")
            is WebDavResult.Failure -> WebDavTestResult(false, result.message)
        }
    }

    companion object {
        private const val TAG = "WebDavSyncRepo"
    }
}

internal sealed interface WebDavSyncItemResult {
    data class Synced(val uploaded: Boolean, val downloaded: Boolean, val etag: String?) : WebDavSyncItemResult
    data object RemoteEmpty : WebDavSyncItemResult
    data class Error(val message: String) : WebDavSyncItemResult
}

internal data class WebDavTestResult(val success: Boolean, val message: String)
