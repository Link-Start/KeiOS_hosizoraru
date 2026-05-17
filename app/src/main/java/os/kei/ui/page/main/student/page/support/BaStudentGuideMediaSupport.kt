package os.kei.ui.page.main.student.page.support

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import os.kei.feature.ba.data.remote.GameKeeNetworkClient
import os.kei.feature.ba.data.remote.GameKeeNetworkResult
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.hasRenderableGalleryMedia
import os.kei.ui.page.main.student.isMemoryHallFileGalleryItem
import os.kei.ui.page.main.student.isRenderableGalleryStaticImageUrl
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

internal fun normalizeGuidePlaybackSource(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    val scheme = runCatching { value.toUri().scheme.orEmpty() }.getOrDefault("")
    return if (scheme.equals("file", ignoreCase = true)) {
        value
    } else {
        normalizeGuideUrl(value)
    }
}

internal fun isGuideAudioPlaybackUrl(raw: String): Boolean {
    val normalized = normalizeGuidePlaybackSource(raw)
    if (normalized.isBlank()) return false
    val scheme = runCatching { normalized.toUri().scheme.orEmpty() }.getOrDefault("")
    return scheme.equals("http", ignoreCase = true) ||
        scheme.equals("https", ignoreCase = true) ||
        scheme.equals("file", ignoreCase = true)
}

internal fun collectGuideStaticImagePrefetchUrls(
    info: BaStudentGuideInfo,
    maxCount: Int = Int.MAX_VALUE,
): List<String> {
    if (maxCount <= 0) return emptyList()
    val orderedUrls = LinkedHashSet<String>()

    fun addPrefetchUrl(raw: String): Boolean {
        if (orderedUrls.size >= maxCount) return true
        val normalized = normalizeGuideUrl(raw)
        if (isRenderableGalleryStaticImageUrl(normalized)) {
            orderedUrls += normalized
        }
        return orderedUrls.size >= maxCount
    }

    if (addPrefetchUrl(info.imageUrl)) return orderedUrls.toList()
    if (info.galleryItems.isEmpty()) {
        addPrefetchUrl(info.imageUrl)
        return orderedUrls.toList()
    }

    val seenGalleryKeys = HashSet<String>()
    for (item in info.galleryItems) {
        if (!hasRenderableGalleryMedia(item) || isMemoryHallFileGalleryItem(item)) continue
        val itemKey = "${item.mediaType}|${item.mediaUrl.ifBlank { item.imageUrl }}"
        if (!seenGalleryKeys.add(itemKey)) continue
        if (addPrefetchUrl(item.imageUrl)) break
        if (addPrefetchUrl(item.mediaUrl)) break
    }
    return orderedUrls.toList()
}

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

private const val GUIDE_MEDIA_COPY_YIELD_BYTES = 512 * 1024

private fun sanitizeGuideMediaTitle(raw: String): String {
    val cleaned =
        raw
            .replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    return cleaned.ifBlank { "BA_media" }.take(96)
}

private fun sanitizeGuideMediaToken(raw: String): String =
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

private suspend fun InputStream.copyToCancellable(output: OutputStream): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copiedBytes = 0L
    var bytesSinceYield = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        copiedBytes += read.toLong()
        bytesSinceYield += read
        if (bytesSinceYield >= GUIDE_MEDIA_COPY_YIELD_BYTES) {
            bytesSinceYield = 0
            yield()
        }
    }
    return copiedBytes
}

private suspend fun copyGuideMediaFromSourceToOutput(
    context: Context,
    sourceUrl: String,
    output: OutputStream,
): Boolean {
    return try {
        val normalized = normalizeGuidePlaybackSource(sourceUrl)
        if (normalized.isBlank()) return false
        val sourceUri = runCatching { normalized.toUri() }.getOrNull()
        val scheme = sourceUri?.scheme.orEmpty().lowercase()
        when {
            scheme == "file" -> {
                val path = sourceUri?.path.orEmpty()
                if (path.isBlank()) {
                    false
                } else {
                    File(path).inputStream().use { input ->
                        input.copyToCancellable(output)
                    }
                    true
                }
            }

            scheme == "content" -> {
                val input = sourceUri?.let { context.contentResolver.openInputStream(it) }
                if (input == null) {
                    false
                } else {
                    input.use { it.copyToCancellable(output) }
                    true
                }
            }

            scheme == "http" || scheme == "https" || normalized.startsWith("//") -> {
                val tempDir = File(context.cacheDir, "guide_media_save")
                if (!tempDir.exists()) tempDir.mkdirs()
                val tempFile = File(tempDir, "tmp_${System.nanoTime().toString(16)}")
                try {
                    val downloaded =
                        GameKeeNetworkClient.downloadToFile(
                            normalized,
                            tempFile,
                        ) is GameKeeNetworkResult.Success
                    if (!downloaded || !tempFile.exists() || tempFile.length() <= 0L) {
                        false
                    } else {
                        tempFile.inputStream().use { input ->
                            input.copyToCancellable(output)
                        }
                        true
                    }
                } finally {
                    runCatching { tempFile.delete() }
                }
            }

            else -> {
                false
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }
}

internal suspend fun copyGuideMediaToUriAsync(
    context: Context,
    sourceUrl: String,
    outputUri: Uri,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Boolean {
    return withContext(ioDispatcher) {
        try {
            val output = context.contentResolver.openOutputStream(outputUri) ?: return@withContext false
            output.use { out ->
                copyGuideMediaFromSourceToOutput(context, sourceUrl, out)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
        }
    }
}

private fun createUniqueZipEntryName(
    usedNames: MutableSet<String>,
    rawFileName: String,
): String {
    val sanitized = sanitizeGuideMediaTitle(rawFileName).ifBlank { "BA_media.bin" }
    val base = sanitized.substringBeforeLast('.', sanitized).ifBlank { "BA_media" }
    val ext = sanitized.substringAfterLast('.', "")
    val suffix = ext.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    val maxAttempts = 120
    for (index in 0 until maxAttempts) {
        val candidate =
            if (index == 0) {
                "$base$suffix"
            } else {
                "$base ($index)$suffix"
            }
        if (usedNames.add(candidate)) {
            return candidate
        }
    }
    val fallback = "BA_media_${System.nanoTime().toString(16)}$suffix"
    usedNames += fallback
    return fallback
}

private suspend fun copyGuideMediaFromSourceToZipEntry(
    context: Context,
    sourceUrl: String,
    zip: ZipOutputStream,
    entryName: String,
): Boolean {
    var entryOpened = false
    return try {
        suspend fun copyInput(input: InputStream): Boolean {
            zip.putNextEntry(ZipEntry(entryName))
            entryOpened = true
            input.use { it.copyToCancellable(zip) }
            return true
        }

        val normalized = normalizeGuidePlaybackSource(sourceUrl)
        if (normalized.isBlank()) return false
        val sourceUri = runCatching { normalized.toUri() }.getOrNull()
        val scheme = sourceUri?.scheme.orEmpty().lowercase()
        when {
            scheme == "file" -> {
                val path = sourceUri?.path.orEmpty()
                if (path.isBlank()) {
                    false
                } else {
                    copyInput(File(path).inputStream())
                }
            }

            scheme == "content" -> {
                val input = sourceUri?.let { context.contentResolver.openInputStream(it) }
                if (input == null) {
                    false
                } else {
                    copyInput(input)
                }
            }

            scheme == "http" || scheme == "https" || normalized.startsWith("//") -> {
                val tempDir = File(context.cacheDir, "guide_media_save")
                if (!tempDir.exists()) tempDir.mkdirs()
                val tempFile = File(tempDir, "tmp_${System.nanoTime().toString(16)}")
                try {
                    val downloaded =
                        GameKeeNetworkClient.downloadToFile(
                            normalized,
                            tempFile,
                        ) is GameKeeNetworkResult.Success
                    if (!downloaded || !tempFile.exists() || tempFile.length() <= 0L) {
                        false
                    } else {
                        copyInput(tempFile.inputStream())
                    }
                } finally {
                    runCatching { tempFile.delete() }
                }
            }

            else -> {
                false
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    } finally {
        if (entryOpened) {
            runCatching { zip.closeEntry() }
        }
    }
}

internal suspend fun copyGuideMediaPackToUriAsync(
    context: Context,
    request: GuideMediaPackSaveRequest,
    outputUri: Uri,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): GuideMediaPackSaveResult {
    return withContext(ioDispatcher) {
        val total = request.entries.size
        if (total <= 0) return@withContext GuideMediaPackSaveResult(totalCount = 0, savedCount = 0)
        var savedCount = 0
        try {
            val output =
                context.contentResolver.openOutputStream(outputUri)
                    ?: return@withContext GuideMediaPackSaveResult(totalCount = total, savedCount = 0)
            output.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                    val usedEntryNames = mutableSetOf<String>()
                    request.entries.forEach { item ->
                        currentCoroutineContext().ensureActive()
                        val entryName =
                            createUniqueZipEntryName(
                                usedNames = usedEntryNames,
                                rawFileName = item.fileName,
                            )
                        val copied =
                            copyGuideMediaFromSourceToZipEntry(
                                context = context,
                                sourceUrl = item.sourceUrl,
                                zip = zip,
                                entryName = entryName,
                            )
                        if (copied) {
                            savedCount += 1
                        }
                        yield()
                    }
                }
            }
            GuideMediaPackSaveResult(totalCount = total, savedCount = savedCount)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            GuideMediaPackSaveResult(totalCount = total, savedCount = 0)
        }
    }
}

internal fun createUniqueDocumentInTree(
    tree: DocumentFile,
    mimeType: String,
    fileName: String,
): DocumentFile? {
    val base = fileName.substringBeforeLast('.', fileName).ifBlank { "BA_media" }
    val ext = fileName.substringAfterLast('.', "")
    val normalizedExt = ext.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    val maxAttempts = 120
    for (index in 0 until maxAttempts) {
        val candidate =
            if (index == 0) {
                "$base$normalizedExt"
            } else {
                "$base ($index)$normalizedExt"
            }
        val exists = tree.findFile(candidate)
        if (exists == null) {
            return tree.createFile(mimeType, candidate)
        }
    }
    return null
}

internal suspend fun createUniqueDocumentUriInTreeAsync(
    context: Context,
    treeUri: Uri,
    mimeType: String,
    fileName: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Uri? =
    withContext(ioDispatcher) {
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        createUniqueDocumentInTree(
            tree = treeDoc,
            mimeType = mimeType,
            fileName = fileName,
        )?.uri
    }

@Composable
internal fun rememberGuideSyncProgress(
    loading: Boolean,
    animationsEnabled: Boolean,
): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(loading, animationsEnabled) {
        if (!animationsEnabled) {
            progress.snapTo(if (loading) 0.9f else 1f)
            return@LaunchedEffect
        }
        if (loading) {
            progress.snapTo(0.12f)
            progress.animateTo(
                targetValue = 0.68f,
                animationSpec =
                    tween(
                        durationMillis = resolvedMotionDuration(520, animationsEnabled),
                        easing = FastOutSlowInEasing,
                    ),
            )
            progress.animateTo(
                targetValue = 0.90f,
                animationSpec =
                    tween(
                        durationMillis = resolvedMotionDuration(1800, animationsEnabled),
                        easing = LinearEasing,
                    ),
            )
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = resolvedMotionDuration(260, animationsEnabled),
                        easing = FastOutSlowInEasing,
                    ),
            )
        }
    }
    return progress.value
}
