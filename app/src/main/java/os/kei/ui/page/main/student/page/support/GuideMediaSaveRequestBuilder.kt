package os.kei.ui.page.main.student.page.support

import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class GuideMediaSaveRequest(
    val sourceUrl: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
)

internal data class GuideMediaPackSaveRequest(
    val entries: List<GuideMediaSaveRequest>,
    val fileName: String,
)

internal data class GuideMediaPackSaveResult(
    val totalCount: Int,
    val savedCount: Int,
) {
    val success: Boolean
        get() = savedCount > 0
}

internal fun sanitizeGuideMediaTitle(raw: String): String {
    val cleaned =
        raw
            .replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    return cleaned.ifBlank { "BA_media" }.take(96)
}

internal fun sanitizeGuideMediaToken(raw: String): String =
    raw
        .replace(Regex("""[\\/:*?"<>|]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(48)

private val guideKnownMediaExts =
    setOf(
        "jpg",
        "jpeg",
        "png",
        "webp",
        "gif",
        "bmp",
        "mp4",
        "webm",
        "mkv",
        "mov",
        "m3u8",
        "ogg",
        "mp3",
        "wav",
        "flac",
        "aac",
        "m4a",
    )

private fun readGuideFileHeader(
    path: String,
    maxBytes: Int = 16,
): ByteArray {
    if (path.isBlank()) return ByteArray(0)
    val file = File(path)
    if (!file.exists() || file.length() <= 0L) return ByteArray(0)
    return runCatching {
        file.inputStream().use { input ->
            val buffer = ByteArray(maxBytes)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
    }.getOrDefault(ByteArray(0))
}

private fun ByteArray.startsWithAscii(prefix: String): Boolean {
    if (size < prefix.length) return false
    return prefix.indices.all { index ->
        this[index].toInt().toChar() == prefix[index]
    }
}

private fun ByteArray.asciiAt(
    offset: Int,
    length: Int,
): String {
    if (offset < 0 || length <= 0 || size < offset + length) return ""
    return buildString(length) {
        for (i in offset until offset + length) {
            append(this@asciiAt[i].toInt().toChar())
        }
    }
}

private fun inferGuideMediaExtFromLocalFile(
    rawSourceUrl: String,
    rawTitle: String,
): String? {
    val parsed = runCatching { rawSourceUrl.toUri() }.getOrNull()
    val path =
        when {
            parsed?.scheme.equals("file", ignoreCase = true) -> parsed?.path.orEmpty()
            rawSourceUrl.startsWith("/") -> rawSourceUrl
            else -> ""
        }
    val header = readGuideFileHeader(path = path, maxBytes = 16)
    if (header.isEmpty()) return null

    fun byteAt(index: Int): Int = header.getOrNull(index)?.toInt()?.and(0xFF) ?: -1

    return when {
        header.size >= 4 && header.startsWithAscii("OggS") -> {
            "ogg"
        }

        header.size >= 3 && header.startsWithAscii("ID3") -> {
            "mp3"
        }

        header.size >= 2 && byteAt(0) == 0xFF && (byteAt(1) and 0xE0) == 0xE0 -> {
            "mp3"
        }

        header.size >= 4 && header.startsWithAscii("fLaC") -> {
            "flac"
        }

        header.size >= 12 &&
            header.startsWithAscii("RIFF") &&
            header.asciiAt(8, 4) == "WAVE" -> {
            "wav"
        }

        header.size >= 2 && byteAt(0) == 0xFF && (byteAt(1) and 0xF6) == 0xF0 -> {
            "aac"
        }

        header.size >= 12 && header.asciiAt(4, 4) == "ftyp" -> {
            val lowerTitle = rawTitle.lowercase()
            if (lowerTitle.contains("bgm") || lowerTitle.contains("音频") || lowerTitle.contains("语音")) {
                "m4a"
            } else {
                "mp4"
            }
        }

        header.size >= 4 && byteAt(0) == 0x89 && header.asciiAt(1, 3) == "PNG" -> {
            "png"
        }

        header.size >= 3 && byteAt(0) == 0xFF && byteAt(1) == 0xD8 && byteAt(2) == 0xFF -> {
            "jpg"
        }

        header.size >= 6 && (header.startsWithAscii("GIF87a") || header.startsWithAscii("GIF89a")) -> {
            "gif"
        }

        header.size >= 12 &&
            header.startsWithAscii("RIFF") &&
            header.asciiAt(8, 4) == "WEBP" -> {
            "webp"
        }

        header.size >= 2 && header.startsWithAscii("BM") -> {
            "bmp"
        }

        else -> {
            null
        }
    }
}

private fun guessGuideMediaExt(
    rawSourceUrl: String,
    rawTitle: String = "",
): String {
    val normalized = normalizeGuidePlaybackSource(rawSourceUrl)
    val source = normalized.substringBefore('#').substringBefore('?')
    val ext = source.substringAfterLast('.', "").lowercase()
    if (ext in guideKnownMediaExts) return ext

    inferGuideMediaExtFromLocalFile(normalized, rawTitle)?.let { inferred ->
        return inferred
    }

    val lowerSource = normalized.lowercase()
    val lowerTitle = rawTitle.lowercase()
    return when {
        lowerSource.contains("audio") || lowerTitle.contains("bgm") || lowerTitle.contains("音频") -> "ogg"
        lowerSource.contains("video") || lowerTitle.contains("视频") -> "mp4"
        else -> "bin"
    }
}

private fun mimeTypeFromGuideExt(ext: String): String {
    val normalizedExt = ext.lowercase()
    val mapMime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(normalizedExt).orEmpty()
    if (mapMime.isNotBlank()) return mapMime
    return when (normalizedExt) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "ogg" -> "audio/ogg"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }
}

internal fun buildGuideMediaSaveRequest(
    rawUrl: String,
    rawTitle: String,
    rawPrefix: String = "",
): GuideMediaSaveRequest? {
    val source = normalizeGuidePlaybackSource(rawUrl)
    if (source.isBlank()) return null
    val ext = guessGuideMediaExt(source, rawTitle)
    val mime = mimeTypeFromGuideExt(ext)
    val baseTitle = sanitizeGuideMediaTitle(rawTitle)
    val prefix =
        sanitizeGuideMediaToken(rawPrefix)
            .takeIf { it.isNotBlank() && it != "学生图鉴" }
            .orEmpty()
    val title =
        when {
            prefix.isBlank() -> baseTitle
            baseTitle.startsWith(prefix) -> baseTitle
            else -> sanitizeGuideMediaTitle("${prefix}_$baseTitle")
        }
    val fileName =
        if (title.endsWith(".$ext", ignoreCase = true)) {
            title
        } else {
            "$title.$ext"
        }
    return GuideMediaSaveRequest(
        sourceUrl = source,
        title = title,
        fileName = fileName,
        mimeType = mime,
    )
}

private fun buildGuideMediaPackZipFileName(
    rawPackTitle: String,
    rawPrefix: String,
): String {
    val packTitle = sanitizeGuideMediaToken(rawPackTitle).ifBlank { "角色表情" }
    val prefix =
        sanitizeGuideMediaToken(rawPrefix)
            .takeIf { it.isNotBlank() && it != "学生图鉴" }
            .orEmpty()
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
    val base =
        if (prefix.isBlank()) {
            "${packTitle}_打包_$stamp"
        } else {
            "${prefix}_${packTitle}_打包_$stamp"
        }
    return "${sanitizeGuideMediaTitle(base)}.zip"
}

internal fun buildGuideMediaPackSaveRequest(
    rawItems: List<Pair<String, String>>,
    rawPackTitle: String,
    rawPrefix: String = "",
): GuideMediaPackSaveRequest? {
    if (rawItems.isEmpty()) return null
    val entries =
        rawItems
            .mapNotNull { (url, title) ->
                buildGuideMediaSaveRequest(
                    rawUrl = url,
                    rawTitle = title,
                    rawPrefix = rawPrefix,
                )
            }.distinctBy { it.sourceUrl }
    if (entries.isEmpty()) return null
    return GuideMediaPackSaveRequest(
        entries = entries,
        fileName =
            buildGuideMediaPackZipFileName(
                rawPackTitle = rawPackTitle,
                rawPrefix = rawPrefix,
            ),
    )
}
