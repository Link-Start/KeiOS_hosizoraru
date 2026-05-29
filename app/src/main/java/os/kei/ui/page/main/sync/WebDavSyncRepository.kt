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

    // ── Ensure remote directory exists ──────────────────────────────

    /**
     * Ensure the remote directory exists. Creates it if missing.
     * Returns true if the directory is ready, false on failure.
     */
    private suspend fun ensureRemoteDir(): Boolean {
        val c = client ?: return false
        // First try PROPFIND to check if directory exists
        when (val result = c.testConnection()) {
            is WebDavResult.Success -> return true
            is WebDavResult.Failure -> {
                // 404 or 409 means directory doesn't exist, try to create it
                if (result.code == 404 || result.code == 409) {
                    AppLogger.i(TAG, "Remote dir not found, creating...")
                    return when (val mkResult = c.mkdir()) {
                        is WebDavResult.Success -> {
                            AppLogger.i(TAG, "Remote dir created successfully")
                            true
                        }
                        is WebDavResult.Failure -> {
                            // 405 = already exists (Method Not Allowed on MKCOL)
                            if (mkResult.code == 405) {
                                AppLogger.i(TAG, "Remote dir already exists (405)")
                                true
                            } else {
                                AppLogger.w(TAG, "Failed to create remote dir: ${mkResult.code} ${mkResult.message}")
                                false
                            }
                        }
                    }
                }
                // Other errors (auth, network, etc.)
                AppLogger.w(TAG, "PROPFIND failed: ${result.code} ${result.message}")
                return false
            }
        }
    }

    // ── Single item sync ───────────────────────────────────────────

    suspend fun syncItem(
        item: WebDavSyncItem,
        exportJson: () -> String,
        importJson: (String) -> Unit,
    ): WebDavSyncItemResult {
        val c = client ?: return WebDavSyncItemResult.Error("Not configured")
        return withContext(Dispatchers.IO) {
            try {
                // Ensure directory exists before syncing
                if (!ensureRemoteDir()) {
                    return@withContext WebDavSyncItemResult.Error("Cannot access remote directory")
                }
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
                // Ensure directory exists before uploading
                if (!ensureRemoteDir()) {
                    return@withContext WebDavSyncItemResult.Error("Cannot access remote directory")
                }
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
                // Ensure directory exists before downloading
                if (!ensureRemoteDir()) {
                    return@withContext WebDavSyncItemResult.Error("Cannot access remote directory")
                }
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
        return withContext(Dispatchers.IO) {
            try {
                // Try to access the remote directory
                when (val result = c.testConnection()) {
                    is WebDavResult.Success -> WebDavTestResult(true, "OK")
                    is WebDavResult.Failure -> {
                        // 404 or 409 = directory doesn't exist, try to create it
                        if (result.code == 404 || result.code == 409) {
                            AppLogger.i(TAG, "Remote dir not found during test, creating...")
                            when (val mkResult = c.mkdir()) {
                                is WebDavResult.Success -> WebDavTestResult(true, "OK", dirCreated = true)
                                is WebDavResult.Failure -> {
                                    // 405 = already exists
                                    if (mkResult.code == 405) {
                                        WebDavTestResult(true, "OK")
                                    } else {
                                        WebDavTestResult(false, "Cannot create directory: ${mkResult.message}")
                                    }
                                }
                            }
                        } else {
                            WebDavTestResult(false, result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Test connection exception", e)
                WebDavTestResult(false, e.message ?: "Unknown error")
            }
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

internal data class WebDavTestResult(
    val success: Boolean,
    val message: String,
    val dirCreated: Boolean = false,
)
