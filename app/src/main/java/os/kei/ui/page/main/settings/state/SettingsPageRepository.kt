package os.kei.ui.page.main.settings.state

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.export.ExportJobResult
import os.kei.core.intent.UriGrantCompat
import os.kei.core.log.AppLogStore
import os.kei.ui.page.main.settings.cache.CacheEntrySummary
import os.kei.ui.page.main.settings.cache.CacheStores
import os.kei.ui.page.main.settings.page.SettingsSearchTarget
import os.kei.ui.page.main.settings.page.buildSettingsSearchTargets
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private const val NON_HOME_BACKGROUND_CROP_DIR = "non_home_background"
private const val NON_HOME_BACKGROUND_CROP_FILE_PREFIX = "cropped_non_home_"
private const val NON_HOME_BACKGROUND_CROP_TARGET_SHORT_EDGE = 1440
private const val NON_HOME_BACKGROUND_CROP_MAX_WIDTH = 2560
private const val NON_HOME_BACKGROUND_CROP_MAX_HEIGHT = 4096

internal class SettingsPageRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    suspend fun listCacheEntries(context: Context): List<CacheEntrySummary> =
        withContext(ioDispatcher) {
            runCatching { CacheStores.list(context) }.getOrDefault(emptyList())
        }

    suspend fun clearAllCaches(context: Context): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching { CacheStores.clearAll(context) }
        }

    suspend fun clearCache(
        context: Context,
        cacheId: String,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching { CacheStores.clear(context, cacheId) }
        }

    suspend fun loadLogStats(context: Context): AppLogStore.Stats =
        withContext(ioDispatcher) {
            runCatching { AppLogStore.stats(context) }.getOrDefault(AppLogStore.Stats.Empty)
        }

    suspend fun clearLogs(context: Context): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching { AppLogStore.clear(context) }
        }

    suspend fun exportLogZip(
        context: Context,
        uri: Uri,
    ): ExportJobResult =
        withContext(ioDispatcher) {
            val fileName =
                uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "keios-logs.zip" }
            AppLogStore
                .exportZipToUri(context, uri)
                .fold(
                    onSuccess = { ExportJobResult.success(fileName = fileName) },
                    onFailure = { error ->
                        ExportJobResult.failure(
                            fileName = fileName,
                            error = error,
                        )
                    },
                )
        }

    suspend fun buildLogExportFileName(): String =
        withContext(defaultDispatcher) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            "keios-logs-$stamp.zip"
        }

    suspend fun buildSearchTargets(context: Context): List<SettingsSearchTarget> {
        val appContext = context.applicationContext
        return withContext(defaultDispatcher) {
            buildSettingsSearchTargets(appContext::getString)
        }
    }

    suspend fun buildNonHomeBackgroundCropIntent(
        context: Context,
        sourceUri: Uri,
    ): Result<Intent> {
        val appContext = context.applicationContext
        return withContext(ioDispatcher) {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    sourceUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            runCatching {
                val outputUri = createNonHomeBackgroundCropOutputUri(appContext)
                val (aspectRatioX, aspectRatioY) = resolveNonHomeBackgroundAspectRatio(appContext)
                val (maxResultWidth, maxResultHeight) = resolveNonHomeBackgroundCropSize(appContext)
                val cropOptions =
                    UCrop.Options().apply {
                        setToolbarTitle(appContext.getString(R.string.settings_non_home_background_crop_title))
                        setCompressionFormat(Bitmap.CompressFormat.JPEG)
                        setCompressionQuality(92)
                        setFreeStyleCropEnabled(false)
                        setHideBottomControls(false)
                        setShowCropFrame(true)
                        setShowCropGrid(true)
                    }
                val cropIntent =
                    UCrop
                        .of(sourceUri, outputUri)
                        .withAspectRatio(aspectRatioX, aspectRatioY)
                        .withMaxResultSize(maxResultWidth, maxResultHeight)
                        .withOptions(cropOptions)
                        .getIntent(appContext)
                val cropGrantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                cropIntent.addFlags(cropGrantFlags)
                UriGrantCompat.grantToIntentTargets(
                    context = appContext,
                    intent = cropIntent,
                    uris = listOf(sourceUri, outputUri),
                    flags = cropGrantFlags,
                )
                cropIntent
            }
        }
    }

    suspend fun deleteManagedNonHomeBackgroundFile(
        context: Context,
        uriText: String,
    ) {
        val appContext = context.applicationContext
        withContext(ioDispatcher) {
            deleteManagedNonHomeBackgroundFileSync(appContext, uriText)
        }
    }
}

private fun createNonHomeBackgroundCropOutputUri(context: Context): Uri {
    val dir = File(context.filesDir, NON_HOME_BACKGROUND_CROP_DIR)
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val output =
        File(
            dir,
            "$NON_HOME_BACKGROUND_CROP_FILE_PREFIX${System.currentTimeMillis()}.jpg",
        )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        output,
    )
}

private fun resolveNonHomeBackgroundAspectRatio(context: Context): Pair<Float, Float> {
    val metrics = context.resources.displayMetrics
    val widthPx = metrics.widthPixels.coerceAtLeast(1)
    val heightPx = metrics.heightPixels.coerceAtLeast(1)
    return widthPx.toFloat() to heightPx.toFloat()
}

private fun resolveNonHomeBackgroundCropSize(context: Context): Pair<Int, Int> {
    val metrics = context.resources.displayMetrics
    val widthPx = metrics.widthPixels.coerceAtLeast(1)
    val heightPx = metrics.heightPixels.coerceAtLeast(1)
    val shortEdge = min(widthPx, heightPx).coerceAtLeast(1)
    val upscale =
        (NON_HOME_BACKGROUND_CROP_TARGET_SHORT_EDGE.toFloat() / shortEdge.toFloat())
            .coerceAtLeast(1f)
    val width = (widthPx * upscale).roundToInt().coerceIn(widthPx, NON_HOME_BACKGROUND_CROP_MAX_WIDTH)
    val height = (heightPx * upscale).roundToInt().coerceIn(heightPx, NON_HOME_BACKGROUND_CROP_MAX_HEIGHT)
    return width to height
}

private fun deleteManagedNonHomeBackgroundFileSync(
    context: Context,
    uriText: String,
) {
    if (uriText.isBlank()) return
    val uri = runCatching { uriText.toUri() }.getOrNull() ?: return
    val target =
        when (uri.scheme) {
            "file" -> File(uri.path ?: return)
            "content" -> resolveManagedNonHomeBackgroundFileByContentUri(context, uri) ?: return
            else -> return
        }
    if (target.name.startsWith(NON_HOME_BACKGROUND_CROP_FILE_PREFIX).not()) return
    if (target.parentFile?.name != NON_HOME_BACKGROUND_CROP_DIR) return
    runCatching { target.delete() }
}

private fun resolveManagedNonHomeBackgroundFileByContentUri(
    context: Context,
    uri: Uri,
): File? {
    val expectedAuthority = "${context.packageName}.fileprovider"
    if (uri.authority != expectedAuthority) return null
    val fileName =
        uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.startsWith(NON_HOME_BACKGROUND_CROP_FILE_PREFIX) }
            ?: return null
    val dir = File(context.filesDir, NON_HOME_BACKGROUND_CROP_DIR)
    return File(dir, fileName)
}
