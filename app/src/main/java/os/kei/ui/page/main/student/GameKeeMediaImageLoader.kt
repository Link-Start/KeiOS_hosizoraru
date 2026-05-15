package os.kei.ui.page.main.student

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.student.catalog.BaGuideCatalogIconCache

internal object GameKeeMediaImageLoader {
    private val keyLock = Any()
    private val locks = LinkedHashMap<String, Mutex>()

    suspend fun loadGuideBitmap(
        context: Context,
        source: String,
        maxDecodeDimension: Int = 2048,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Bitmap? {
        val target = normalizeGuideMediaSource(source)
        if (target.isBlank()) return null
        if (onProgress != null) {
            return withContext(ioDispatcher) {
                loadGuideBitmapSource(
                    context = context.applicationContext,
                    source = target,
                    maxDecodeDimension = maxDecodeDimension,
                    onProgress = onProgress
                )
            }
        }
        return withLoadLock("guide:$maxDecodeDimension:$target") {
            BaGuideImageCache.peekBitmap(target, maxDecodeDimension)?.let { return@withLoadLock it }
            withContext(ioDispatcher) {
                loadGuideBitmapSource(
                    context = context.applicationContext,
                    source = target,
                    maxDecodeDimension = maxDecodeDimension
                )
            }
        }
    }

    suspend fun loadCatalogIcon(
        context: Context,
        imageUrl: String,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Bitmap? {
        val target = imageUrl.trim()
        if (target.isBlank()) return null
        return withLoadLock("catalog-icon:$target") {
            BaGuideCatalogIconCache.get(target)?.let { return@withLoadLock it }
            withContext(ioDispatcher) {
                BaGuideCatalogIconCache.getOrLoad(context.applicationContext, target)
            }
        }
    }

    suspend fun resolveInlineGifTarget(
        context: Context,
        target: String,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String {
        val normalized = normalizeGuideMediaSource(target)
        if (normalized.isBlank() || !isHttpMediaSource(normalized)) return normalized
        return withLoadLock("inline-gif:$normalized") {
            withContext(ioDispatcher) {
                runCatching {
                    BaGuideTempMediaCache.prefetchForGuide(
                        context = context.applicationContext,
                        sourceUrl = GUIDE_INLINE_GIF_CACHE_SCOPE,
                        rawUrls = listOf(normalized)
                    )
                }
                var resolved = BaGuideTempMediaCache.resolveCachedUrl(
                    context = context.applicationContext,
                    sourceUrl = GUIDE_INLINE_GIF_CACHE_SCOPE,
                    rawUrl = normalized
                )
                if (!isFileMediaSource(resolved)) {
                    runCatching {
                        BaGuideTempMediaCache.prefetchForGuide(
                            context = context.applicationContext,
                            sourceUrl = GUIDE_INLINE_GIF_CACHE_SCOPE,
                            rawUrls = listOf(normalized),
                            forceReDownload = true
                        )
                    }
                    resolved = BaGuideTempMediaCache.resolveCachedUrl(
                        context = context.applicationContext,
                        sourceUrl = GUIDE_INLINE_GIF_CACHE_SCOPE,
                        rawUrl = normalized
                    )
                }
                resolved.ifBlank { normalized }
            }
        }
    }

    private suspend fun <T> withLoadLock(
        key: String,
        block: suspend () -> T
    ): T {
        val mutex = synchronized(keyLock) {
            trimLocksLocked()
            locks.getOrPut(key) { Mutex() }
        }
        return try {
            mutex.withLock { block() }
        } finally {
            synchronized(keyLock) {
                if (!mutex.isLocked && locks[key] === mutex) {
                    locks.remove(key)
                }
            }
        }
    }

    private fun trimLocksLocked() {
        while (locks.size > MAX_KEY_LOCKS) {
            val firstKey = locks.entries.firstOrNull()?.key ?: return
            locks.remove(firstKey)
        }
    }
}

private const val MAX_KEY_LOCKS = 96
