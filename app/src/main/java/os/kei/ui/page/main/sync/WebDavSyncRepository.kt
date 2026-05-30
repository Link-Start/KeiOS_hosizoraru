package os.kei.ui.page.main.sync

import os.kei.core.log.AppLogger
import os.kei.feature.webdav.client.WebDavDownloadResult
import os.kei.feature.webdav.client.WebDavError
import os.kei.feature.webdav.client.WebDavSyncClient
import os.kei.feature.webdav.client.WebDavTestConnectionResult
import os.kei.feature.webdav.client.WebDavUploadResult
import os.kei.feature.webdav.model.WebDavConfig

/**
 * Coordinates upload/download for each sync item using [WebDavSyncClient].
 *
 * Export/import logic is injected via [WebDavSyncDataPort] lambdas so this class
 * has no direct dependency on domain stores. The existing importJson lambdas
 * already handle smart merging (e.g. [GuideBgmFavoriteStore.importFavoritesJsonMerged]).
 */
internal class WebDavSyncRepository {
    private var client: WebDavSyncClient? = null

    fun configure(config: WebDavConfig) {
        client = WebDavSyncClient(config)
    }

    fun isConfigured(): Boolean = client != null

    // ── Test connection ────────────────────────────────────────────

    suspend fun testConnection(): WebDavTestResult {
        val c = client ?: return WebDavTestResult(false, "Not configured", dirCreated = false)
        return when (val result = c.testConnection()) {
            is WebDavTestConnectionResult.Success -> {
                WebDavTestResult(
                    success = true,
                    message = if (result.dirCreated) "OK (directory created)" else "OK",
                    dirCreated = result.dirCreated,
                )
            }
            is WebDavTestConnectionResult.AuthFailed -> {
                WebDavTestResult(false, "Authentication failed — check username and password", dirCreated = false)
            }
            is WebDavTestConnectionResult.PermissionDenied -> {
                WebDavTestResult(false, "Permission denied — check account permissions", dirCreated = false)
            }
            is WebDavTestConnectionResult.NetworkError -> {
                WebDavTestResult(false, "Network error: ${result.message}", dirCreated = false)
            }
            is WebDavTestConnectionResult.InvalidUrl -> {
                WebDavTestResult(false, "Invalid URL: ${result.message}", dirCreated = false)
            }
            is WebDavTestConnectionResult.Error -> {
                WebDavTestResult(false, result.message, dirCreated = false)
            }
        }
    }

    // ── Upload ─────────────────────────────────────────────────────

    suspend fun upload(
        item: WebDavSyncItem,
        exportJson: () -> String,
        storedEtag: String? = null,
    ): WebDavSyncItemResult {
        val c = client ?: return WebDavSyncItemResult.Error("Not configured")
        val json = exportJson()
        return when (val result = c.upload(item.fileName, json, storedEtag)) {
            is WebDavUploadResult.Success -> {
                WebDavSyncItemResult.Synced(
                    uploaded = true,
                    downloaded = false,
                    etag = result.etag,
                )
            }
            is WebDavUploadResult.Conflict -> {
                AppLogger.w(TAG, "Upload ${item.fileName} conflict — file was modified remotely")
                WebDavSyncItemResult.Conflict
            }
            is WebDavUploadResult.Error -> {
                WebDavSyncItemResult.Error(formatError(result.error))
            }
        }
    }

    // ── Download ───────────────────────────────────────────────────

    suspend fun download(
        item: WebDavSyncItem,
        importJson: (String) -> Unit,
    ): WebDavSyncItemResult {
        val c = client ?: return WebDavSyncItemResult.Error("Not configured")
        return when (val result = c.download(item.fileName)) {
            is WebDavDownloadResult.Success -> {
                importJson(result.content)
                WebDavSyncItemResult.Synced(
                    uploaded = false,
                    downloaded = true,
                    etag = result.etag,
                )
            }
            is WebDavDownloadResult.Empty -> {
                WebDavSyncItemResult.RemoteEmpty
            }
            is WebDavDownloadResult.Error -> {
                WebDavSyncItemResult.Error(formatError(result.error))
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun formatError(error: WebDavError): String = when (error) {
        is WebDavError.NetworkUnreachable -> "Network unreachable"
        is WebDavError.AuthFailed -> "Authentication failed"
        is WebDavError.PermissionDenied -> "Permission denied"
        is WebDavError.Unknown -> "Error ${error.code}: ${error.message}"
    }

    companion object {
        private const val TAG = "WebDavSyncRepo"
    }
}

internal sealed interface WebDavSyncItemResult {
    data class Synced(val uploaded: Boolean, val downloaded: Boolean, val etag: String?) : WebDavSyncItemResult
    data object RemoteEmpty : WebDavSyncItemResult
    data object Conflict : WebDavSyncItemResult
    data class Error(val message: String) : WebDavSyncItemResult
}

internal data class WebDavTestResult(
    val success: Boolean,
    val message: String,
    val dirCreated: Boolean = false,
)
