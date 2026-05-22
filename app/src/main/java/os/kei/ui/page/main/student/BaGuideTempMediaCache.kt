package os.kei.ui.page.main.student

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import java.io.File

internal data class BaGuideMediaCacheMetadata(
    val sourceUrl: String,
    val normalizedUrl: String,
    val fileName: String,
    val extension: String,
    val mimeType: String,
    val bytes: Long,
    val lastAccessMs: Long,
    val valid: Boolean,
)

internal data class BaGuideMediaCacheSessionSummary(
    val count: Int,
    val bytes: Long,
    val latest: Long,
)

internal fun scanBaGuideMediaCacheSession(dir: File): BaGuideMediaCacheSessionSummary {
    if (!dir.exists()) return BaGuideMediaCacheSessionSummary(count = 0, bytes = 0L, latest = 0L)
    var count = 0
    var bytes = 0L
    var latest = 0L
    dir
        .walkTopDown()
        .filter { it.isFile }
        .filterNot { it.name.endsWith(".part", ignoreCase = true) }
        .forEach { file ->
            count += 1
            bytes += file.length()
            latest = maxOf(latest, file.lastModified())
        }
    return BaGuideMediaCacheSessionSummary(count = count, bytes = bytes, latest = latest)
}

object BaGuideTempMediaCache {
    private val clock = BaGuideSystemMediaCacheClock
    private val cacheIndex = BaGuideTempMediaIndex(clock)
    private val prefetcher = BaGuideTempMediaPrefetcher(cacheIndex, clock)
    private val resolver = BaGuideTempMediaCacheResolver(clock)

    suspend fun prefetchForGuide(
        context: Context,
        sourceUrl: String,
        rawUrls: List<String>,
        forceReDownload: Boolean = false,
        ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    ) = withContext(ioDispatcher) {
        prefetcher.prefetchForGuide(
            context = context,
            sourceUrl = sourceUrl,
            rawUrls = rawUrls,
            forceReDownload = forceReDownload,
        )
    }

    fun resolveCachedUrl(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ): String =
        resolver.resolveCachedUrl(
            context = context,
            sourceUrl = sourceUrl,
            rawUrl = rawUrl,
        )

    fun cachedMediaBytes(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ): Long = cachedMediaMetadata(context, sourceUrl, rawUrl).bytes

    internal fun cachedMediaMetadata(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ): BaGuideMediaCacheMetadata =
        resolver.cachedMediaMetadata(
            context = context,
            sourceUrl = sourceUrl,
            rawUrl = rawUrl,
        )

    fun clearGuideCache(
        context: Context,
        sourceUrl: String,
    ) {
        runCatching { baGuideTempMediaSessionDir(context, sourceUrl).deleteRecursively() }
        cacheIndex.removeSessionIndex(sourceUrl)
    }

    fun clearMediaCache(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ) {
        val normalized = baGuideNormalizeMediaTarget(rawUrl)
        if (normalized.isBlank()) return
        runCatching {
            val file = baGuideTempMediaTargetFile(context, sourceUrl, normalized)
            if (file.exists()) file.delete()
        }
        cacheIndex.rebuildSessionIndex(context, sourceUrl)
    }

    fun clearAll(context: Context) {
        runCatching { baGuideTempMediaRootDir(context).deleteRecursively() }
        cacheIndex.clearIndex()
    }

    fun cacheFileCount(context: Context): Int = cacheIndex.loadIndexSummary(context).count

    fun cacheTotalBytes(context: Context): Long = cacheIndex.loadIndexSummary(context).bytes

    fun latestModifiedAtMs(context: Context): Long = cacheIndex.loadIndexSummary(context).latest

    internal fun activeDownloadLockCount(): Int = prefetcher.activeDownloadLockCount()
}
