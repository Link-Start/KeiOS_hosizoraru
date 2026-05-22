package os.kei.ui.page.main.settings.state

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.export.ExportJobResult
import os.kei.core.log.AppLogStore
import os.kei.ui.page.main.settings.cache.CacheEntrySummary
import os.kei.ui.page.main.settings.cache.CacheStores
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import os.kei.core.concurrency.AppDispatchers

internal class SettingsPageRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
) {
    suspend fun listCacheEntries(context: Context): List<CacheEntrySummary> {
        return withContext(ioDispatcher) {
            runCatching { CacheStores.list(context) }.getOrDefault(emptyList())
        }
    }

    suspend fun clearAllCaches(context: Context): Result<Unit> {
        return withContext(ioDispatcher) {
            runCatching { CacheStores.clearAll(context) }
        }
    }

    suspend fun clearCache(
        context: Context,
        cacheId: String
    ): Result<Unit> {
        return withContext(ioDispatcher) {
            runCatching { CacheStores.clear(context, cacheId) }
        }
    }

    suspend fun loadLogStats(context: Context): AppLogStore.Stats {
        return withContext(ioDispatcher) {
            runCatching { AppLogStore.stats(context) }.getOrDefault(AppLogStore.Stats.Empty)
        }
    }

    suspend fun clearLogs(context: Context): Result<Unit> {
        return withContext(ioDispatcher) {
            runCatching { AppLogStore.clear(context) }
        }
    }

    suspend fun exportLogZip(
        context: Context,
        uri: Uri
    ): ExportJobResult {
        return withContext(ioDispatcher) {
            val fileName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.trim()
                .orEmpty()
                .ifBlank { "keios-logs.zip" }
            AppLogStore.exportZipToUri(context, uri)
                .fold(
                    onSuccess = { ExportJobResult.success(fileName = fileName) },
                    onFailure = { error ->
                        ExportJobResult.failure(
                            fileName = fileName,
                            error = error
                        )
                    }
                )
        }
    }

    suspend fun buildLogExportFileName(): String {
        return withContext(defaultDispatcher) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            "keios-logs-$stamp.zip"
        }
    }
}
