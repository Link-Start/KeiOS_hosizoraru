package os.kei.ui.page.main.student

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    private val cacheIndex = BaGuideTempMediaIndex()
    private val downloadLocks = ConcurrentHashMap<String, DownloadLock>()

    private data class DownloadLock(
        val mutex: Mutex = Mutex(),
        val users: AtomicInteger = AtomicInteger(0),
    )

    suspend fun prefetchForGuide(
        context: Context,
        sourceUrl: String,
        rawUrls: List<String>,
        forceReDownload: Boolean = false,
        ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    ) = withContext(ioDispatcher) {
        val dir = baGuideTempMediaSessionDir(context, sourceUrl)
        dir.mkdirs()
        val targets =
            rawUrls
                .map(::baGuideNormalizeMediaTarget)
                .filter { it.isNotBlank() }
                .distinct()
        if (targets.isEmpty()) return@withContext

        coroutineScope {
            val semaphore = Semaphore(BA_GUIDE_TEMP_MEDIA_MAX_PARALLEL_DOWNLOADS)
            targets.chunked(BA_GUIDE_TEMP_MEDIA_PREFETCH_TASK_BATCH_SIZE).forEach { batch ->
                ensureActive()
                batch
                    .map { url ->
                        async {
                            semaphore.withPermit {
                                withDownloadLock(sourceUrl = sourceUrl, normalizedUrl = url) {
                                    ensureActive()
                                    val file = baGuideTempMediaTargetFile(context, sourceUrl, url)
                                    if (!forceReDownload && BaGuideTempMediaValidation.isUsableCachedMedia(url, file)) {
                                        return@withDownloadLock
                                    }
                                    val downloaded =
                                        BaGuideTempMediaDownload.downloadWithValidation(
                                            normalizedUrl = url,
                                            targetFile = file,
                                            forceReDownload = forceReDownload,
                                        )
                                    if (!downloaded) {
                                        runCatching { file.delete() }
                                    }
                                }
                            }
                        }
                    }.awaitAll()
            }
        }
        cacheIndex.rebuildSessionIndex(context, sourceUrl)
        cacheIndex.pruneTempCache(context)
    }

    fun resolveCachedUrl(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ): String {
        val normalized = baGuideNormalizeMediaTarget(rawUrl)
        if (normalized.isBlank()) return normalized
        val file = baGuideTempMediaTargetFile(context, sourceUrl, normalized)
        if (BaGuideTempMediaValidation.isUsableCachedMedia(normalized, file)) {
            touchCachedFile(file)
            return Uri.fromFile(file).toString()
        }
        if (file.exists()) {
            runCatching { file.delete() }
        }
        return normalized
    }

    fun cachedMediaBytes(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ): Long = cachedMediaMetadata(context, sourceUrl, rawUrl).bytes

    internal fun cachedMediaMetadata(
        context: Context,
        sourceUrl: String,
        rawUrl: String,
    ): BaGuideMediaCacheMetadata {
        val normalized = baGuideNormalizeMediaTarget(rawUrl)
        if (normalized.isBlank()) {
            return BaGuideMediaCacheMetadata(
                sourceUrl = sourceUrl,
                normalizedUrl = "",
                fileName = "",
                extension = "",
                mimeType = "",
                bytes = 0L,
                lastAccessMs = 0L,
                valid = false,
            )
        }
        val file = baGuideTempMediaTargetFile(context, sourceUrl, normalized)
        val valid = BaGuideTempMediaValidation.isUsableCachedMedia(normalized, file)
        val extension = file.extension.lowercase()
        return BaGuideMediaCacheMetadata(
            sourceUrl = sourceUrl,
            normalizedUrl = normalized,
            fileName = file.name,
            extension = extension,
            mimeType = baGuideTempMediaMimeTypeForExtension(extension),
            bytes = if (valid) file.length().coerceAtLeast(0L) else 0L,
            lastAccessMs = file.lastModified().takeIf { file.exists() } ?: 0L,
            valid = valid,
        )
    }

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

    internal fun activeDownloadLockCount(): Int = downloadLocks.size

    private suspend fun <T> withDownloadLock(
        sourceUrl: String,
        normalizedUrl: String,
        block: suspend () -> T,
    ): T {
        val key = lockKey(sourceUrl, normalizedUrl)
        val lock =
            downloadLocks.compute(key) { _, current ->
                (current ?: DownloadLock()).also { it.users.incrementAndGet() }
            } ?: error("download lock unavailable")
        return try {
            lock.mutex.withLock { block() }
        } finally {
            downloadLocks.computeIfPresent(key) { _, current ->
                if (current !== lock) {
                    current
                } else if (current.users.decrementAndGet() <= 0) {
                    null
                } else {
                    current
                }
            }
        }
    }

    private fun lockKey(
        sourceUrl: String,
        normalizedUrl: String,
    ): String = "${baGuideTempMediaSessionId(sourceUrl)}|${baGuideTempMediaSha1(normalizedUrl)}"

    private fun touchCachedFile(file: File) {
        runCatching {
            if (file.exists()) file.setLastModified(BaGuideSystemMediaCacheClock.nowMs())
        }
    }
}
