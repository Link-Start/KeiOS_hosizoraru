package os.kei.ui.page.main.student

import android.graphics.BitmapFactory
import androidx.core.net.toUri
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

internal object BaGuideTempMediaValidation {
    private const val VALIDATION_CACHE_MAX_SIZE = 2048
    private val mediaValidationCache = ConcurrentHashMap<String, Boolean>()

    fun looksLikeGifUrl(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isBlank()) return false
        if (Regex("""\.gif(\?.*)?(#.*)?$""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) return true
        val lower = normalized.lowercase()
        return lower.contains("format=gif") || lower.contains("image/gif")
    }

    fun looksLikeVideoUrl(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isBlank()) return false
        val lower = normalized.lowercase()
        return lower.endsWith(".mp4") ||
            lower.endsWith(".webm") ||
            lower.endsWith(".mov") ||
            lower.endsWith(".m3u8") ||
            lower.contains(".mp4?") ||
            lower.contains(".webm?") ||
            lower.contains(".mov?") ||
            lower.contains(".m3u8?")
    }

    fun looksLikeAudioUrl(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isBlank()) return false
        val lower = normalized.lowercase()
        return lower.endsWith(".mp3") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".aac") ||
            lower.contains(".mp3?") ||
            lower.contains(".ogg?") ||
            lower.contains(".wav?") ||
            lower.contains(".m4a?") ||
            lower.contains(".aac?")
    }

    fun looksLikeImageUrl(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isBlank()) return false
        if (looksLikeVideoUrl(normalized) || looksLikeAudioUrl(normalized)) return false
        if (looksLikeGifUrl(normalized)) return true
        val fromPath =
            runCatching { normalized.toUri().lastPathSegment.orEmpty() }
                .getOrDefault("")
                .substringAfterLast('.', "")
                .lowercase()
        return fromPath in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif")
    }

    fun isUsableCachedMedia(
        url: String,
        file: File,
    ): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val cacheKey = mediaValidationCacheKey(url, file)
        mediaValidationCache[cacheKey]?.let { cached -> return cached }
        val valid = validateCachedMediaFile(url, file)
        if (mediaValidationCache.size >= VALIDATION_CACHE_MAX_SIZE) {
            mediaValidationCache.clear()
        }
        mediaValidationCache[cacheKey] = valid
        return valid
    }

    private fun mediaValidationCacheKey(
        url: String,
        file: File,
    ): String =
        buildString {
            append(url)
            append('|')
            append(file.absolutePath)
            append('|')
            append(file.length())
            append('|')
            append(file.lastModified())
        }

    private fun validateCachedMediaFile(
        url: String,
        file: File,
    ): Boolean {
        val strictGif = looksLikeGifUrl(url) || file.extension.equals("gif", ignoreCase = true)
        val fileLooksLikeGif = hasGifHeader(file)
        if (strictGif || fileLooksLikeGif) {
            return fileLooksLikeGif && hasGifTrailer(file)
        }
        val mediaIsVideoOrAudio = looksLikeVideoUrl(url) || looksLikeAudioUrl(url)
        if (mediaIsVideoOrAudio) return true
        return hasDecodableImageBounds(file)
    }

    private fun hasGifHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 6L) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(6)
                if (input.read(header) != 6) return@runCatching false
                val magic = String(header)
                magic == "GIF87a" || magic == "GIF89a"
            }
        }.getOrDefault(false)
    }

    private fun hasGifTrailer(file: File): Boolean {
        if (!file.exists() || file.length() < 2L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(file.length() - 1L)
                raf.read() == 0x3B
            }
        }.getOrDefault(false)
    }

    private fun hasDecodableImageBounds(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        }.getOrDefault(false)
    }
}
