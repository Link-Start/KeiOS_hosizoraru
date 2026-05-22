package os.kei.ui.page.main.student

import android.content.Context
import androidx.core.net.toUri
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import java.io.File
import java.security.MessageDigest

internal fun baGuideTempMediaRootDir(context: Context): File = File(context.cacheDir, BA_GUIDE_TEMP_MEDIA_ROOT_DIR)

internal fun baGuideTempMediaSessionId(sourceUrl: String): String = baGuideTempMediaSha1(sourceUrl).take(16)

internal fun baGuideTempMediaSessionDir(
    context: Context,
    sourceUrl: String,
): File = File(context.cacheDir, "$BA_GUIDE_TEMP_MEDIA_ROOT_DIR/${baGuideTempMediaSessionId(sourceUrl)}")

internal fun baGuideTempMediaSessionDirById(
    context: Context,
    id: String,
): File = File(context.cacheDir, "$BA_GUIDE_TEMP_MEDIA_ROOT_DIR/$id")

internal fun baGuideNormalizeMediaTarget(raw: String): String = normalizeGuideUrl(raw.trim()).trim()

internal fun baGuideTempMediaTargetFile(
    context: Context,
    sourceUrl: String,
    normalizedUrl: String,
): File {
    val name = baGuideTempMediaSha1(normalizedUrl) + baGuideTempMediaFileExtFromUrl(normalizedUrl)
    return File(baGuideTempMediaSessionDir(context, sourceUrl), name)
}

internal fun baGuideTempMediaFileExtFromUrl(url: String): String {
    val normalized = url.substringBefore('?').substringBefore('#')
    val fromPath =
        runCatching { normalized.toUri().lastPathSegment.orEmpty() }
            .getOrDefault("")
            .substringAfterLast('.', "")
            .lowercase()
    val ext =
        when (fromPath) {
            "png", "jpg", "jpeg", "webp", "gif", "bmp",
            "mp4", "webm", "mov", "m3u8",
            "ogg", "mp3", "wav", "m4a", "aac", "flac",
            -> {
                fromPath
            }

            else -> {
                when {
                    BaGuideTempMediaValidation.looksLikeGifUrl(url) -> "gif"
                    BaGuideTempMediaValidation.looksLikeAudioUrl(url) -> "ogg"
                    BaGuideTempMediaValidation.looksLikeVideoUrl(url) -> "mp4"
                    else -> "bin"
                }
            }
        }
    return ".$ext"
}

internal fun baGuideTempMediaMimeTypeForExtension(extension: String): String =
    when (extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "avif" -> "image/avif"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "m3u8" -> "application/vnd.apple.mpegurl"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

internal fun baGuideTempMediaSha1(raw: String): String {
    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(raw.toByteArray())
    return buildString(bytes.size * 2) {
        bytes.forEach { b -> append("%02x".format(b)) }
    }
}
