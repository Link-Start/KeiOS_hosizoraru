package os.kei.ui.page.main.student.page.support

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.ba.data.remote.GameKeeNetworkClient
import os.kei.feature.ba.data.remote.GameKeeNetworkResult
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException

private const val GUIDE_MEDIA_COPY_YIELD_BYTES = 512 * 1024

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
    ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
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
    ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
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
    ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
): Uri? =
    withContext(ioDispatcher) {
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
        createUniqueDocumentInTree(
            tree = treeDoc,
            mimeType = mimeType,
            fileName = fileName,
        )?.uri
    }
