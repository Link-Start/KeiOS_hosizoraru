package os.kei.ui.page.main.student.catalog

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import os.kei.feature.ba.data.remote.GameKeeNetworkClient
import os.kei.feature.ba.data.remote.GameKeeNetworkResult
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.BaGuideSystemDataClock
import java.io.File
import java.security.MessageDigest

internal object BaGuideCatalogIconCache {
    private const val MAX_CACHE_COUNT = 96
    private const val MAX_DECODE_EDGE = 256
    private const val ROOT_DIR = "ba_guide_catalog_icon_cache"

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_COUNT) {}

    fun get(url: String): Bitmap? {
        val key = url.trim()
        if (key.isBlank()) return null
        return synchronized(cache) { cache.get(key) }
    }

    private fun cacheRoot(context: Context): File = File(context.cacheDir, ROOT_DIR).apply { mkdirs() }

    private fun fileExtFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "avif" -> ".$ext"
            else -> ".img"
        }
    }

    private fun cacheFile(
        context: Context,
        url: String,
    ): File {
        val normalized = url.trim()
        val fileName = sha1(normalized) + fileExtFromUrl(normalized)
        return File(cacheRoot(context), fileName)
    }

    private fun isFileFreshByBaInterval(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val refreshIntervalHours = BASettingsStore.loadCalendarRefreshIntervalHours().coerceAtLeast(1)
        val ttlMs = refreshIntervalHours * 60L * 60L * 1000L
        val ageMs = (BaGuideSystemDataClock.nowMs() - file.lastModified()).coerceAtLeast(0L)
        return ageMs < ttlMs
    }

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest
            .digest(text.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun getOrLoad(
        context: Context,
        url: String,
    ): Bitmap? {
        val key = url.trim()
        if (key.isBlank()) return null
        get(key)?.let { return it }
        val diskFile = cacheFile(context, key)
        if (isFileFreshByBaInterval(diskFile)) {
            val diskBitmap =
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(diskFile.absolutePath)
                }.getOrNull()
            if (diskBitmap != null) {
                synchronized(cache) { cache.put(key, diskBitmap) }
                return diskBitmap
            }
            runCatching { diskFile.delete() }
        } else if (diskFile.exists()) {
            runCatching { diskFile.delete() }
        }
        val bitmap =
            when (
                val result =
                    GameKeeNetworkClient.fetchImage(
                        imageUrl = key,
                        maxDecodeDimension = MAX_DECODE_EDGE,
                    )
            ) {
                is GameKeeNetworkResult.Success -> result.value
                is GameKeeNetworkResult.Failure -> null
            } ?: return null
        runCatching {
            diskFile.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
        synchronized(cache) { cache.put(key, bitmap) }
        return bitmap
    }

    fun clear(context: Context? = null) {
        synchronized(cache) { cache.evictAll() }
        context?.let { ctx ->
            runCatching { cacheRoot(ctx).deleteRecursively() }
        }
    }
}
