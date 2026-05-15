package os.kei.ui.page.main.student.catalog.page

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import os.kei.core.io.DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES
import os.kei.core.io.readTextFromUriLimited
import os.kei.ui.page.main.student.GuideBgmFavoriteImportResult
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.page.support.createUniqueDocumentInTree
import java.io.Writer

private const val EXPORT_WRITE_CHUNK_CHARS = 16 * 1024
private const val EXPORT_WRITE_YIELD_CHARS = 256 * 1024

internal data class BaGuideCatalogImportApplyResult(
    val studentFavorites: Map<Long, Long>,
    val bgmResult: GuideBgmFavoriteImportResult?
)

internal data class BaGuideCatalogJsonExportRequest(
    val payload: String,
    val fileName: String,
    val successToast: String
)

internal suspend fun buildBaGuideCatalogImportPreviewAsync(
    context: Context,
    uri: Uri,
    kind: BaGuideCatalogImportKind,
    currentFavorites: Map<Long, Long>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default
): BaGuideCatalogImportPreviewState {
    val raw = readBaGuideCatalogImportTextAsync(
        context = context,
        uri = uri,
        ioDispatcher = ioDispatcher
    )
    return when (kind) {
        BaGuideCatalogImportKind.Student -> withContext(parseDispatcher) {
            val studentFavorites = parseCatalogFavoritesExport(raw)
            BaGuideCatalogImportPreviewState(
                kind = kind,
                raw = raw,
                studentPreview = previewCatalogFavoritesImport(
                    imported = studentFavorites,
                    currentFavorites = currentFavorites
                ),
                bgmPreview = GuideBgmFavoriteStore.previewFavoritesJsonImport("")
            )
        }

        BaGuideCatalogImportKind.Bgm -> withContext(parseDispatcher) {
            BaGuideCatalogImportPreviewState(
                kind = kind,
                raw = raw,
                studentPreview = CatalogFavoritesImportPreview(0, 0, 0),
                bgmPreview = GuideBgmFavoriteStore.previewFavoritesJsonImport(raw)
            )
        }

        BaGuideCatalogImportKind.All -> coroutineScope {
            val studentPreview = async(parseDispatcher) {
                val studentFavorites = parseCatalogFavoritesExport(raw)
                previewCatalogFavoritesImport(
                    imported = studentFavorites,
                    currentFavorites = currentFavorites
                )
            }
            val bgmPreview = async(parseDispatcher) {
                GuideBgmFavoriteStore.previewFavoritesJsonImport(raw)
            }
            BaGuideCatalogImportPreviewState(
                kind = kind,
                raw = raw,
                studentPreview = studentPreview.await(),
                bgmPreview = bgmPreview.await()
            )
        }
    }
}

internal suspend fun applyBaGuideCatalogFavoritesImportAsync(
    preview: BaGuideCatalogImportPreviewState,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default
): BaGuideCatalogImportApplyResult = coroutineScope {
    val studentFavorites = when (preview.kind) {
        BaGuideCatalogImportKind.All,
        BaGuideCatalogImportKind.Student -> async(parseDispatcher) {
            parseCatalogFavoritesExport(preview.raw)
        }

        BaGuideCatalogImportKind.Bgm -> null
    }
    val bgmResult = when (preview.kind) {
        BaGuideCatalogImportKind.All,
        BaGuideCatalogImportKind.Bgm -> async(ioDispatcher) {
            GuideBgmFavoriteStore.importFavoritesJsonMerged(preview.raw)
        }

        BaGuideCatalogImportKind.Student -> null
    }
    BaGuideCatalogImportApplyResult(
        studentFavorites = studentFavorites?.await().orEmpty(),
        bgmResult = bgmResult?.await()
    )
}

internal suspend fun readBaGuideCatalogImportTextAsync(
    context: Context,
    uri: Uri,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): String {
    return context.contentResolver.readTextFromUriLimited(
        uri = uri,
        maxBytes = DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES,
        ioDispatcher = ioDispatcher
    ).text
}

internal suspend fun buildCatalogFavoritesExportJsonAsync(
    favorites: Map<Long, Long>,
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default
): String = withContext(parseDispatcher) {
    buildCatalogFavoritesExportJson(favorites)
}

internal suspend fun buildCatalogAllFavoritesExportJsonAsync(
    favorites: Map<Long, Long>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    parseDispatcher: CoroutineDispatcher = Dispatchers.Default
): String {
    val bgmFavoritesJson = withContext(ioDispatcher) {
        GuideBgmFavoriteStore.buildFavoritesExportJson()
    }
    return withContext(parseDispatcher) {
        buildCatalogAllFavoritesExportJson(
            favorites = favorites,
            bgmFavoritesJson = bgmFavoritesJson
        )
    }
}

internal suspend fun buildBgmFavoritesExportJsonAsync(
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): String = withContext(ioDispatcher) {
    GuideBgmFavoriteStore.buildFavoritesExportJson()
}

internal suspend fun writeBaGuideCatalogJsonExportAsync(
    context: Context,
    uri: Uri,
    request: BaGuideCatalogJsonExportRequest,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Boolean {
    if (request.payload.isBlank()) return false
    return try {
        withContext(ioDispatcher) {
            val output = context.contentResolver.openOutputStream(uri) ?: return@withContext false
            output.bufferedWriter().use { writer ->
                writer.writeTextCancellable(request.payload)
            }
            true
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }
}

internal suspend fun writeBaGuideCatalogJsonExportToTreeAsync(
    context: Context,
    treeUri: Uri,
    request: BaGuideCatalogJsonExportRequest,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Boolean {
    if (request.payload.isBlank() || request.fileName.isBlank()) return false
    return try {
        withContext(ioDispatcher) {
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            val targetDoc = createUniqueDocumentInTree(
                tree = treeDoc,
                mimeType = "application/json",
                fileName = request.fileName
            ) ?: return@withContext false
            val output = context.contentResolver.openOutputStream(targetDoc.uri)
                ?: return@withContext false
            output.bufferedWriter().use { writer ->
                writer.writeTextCancellable(request.payload)
            }
            true
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }
}

private suspend fun Writer.writeTextCancellable(text: String) {
    val context = currentCoroutineContext()
    var offset = 0
    var charsUntilYield = EXPORT_WRITE_YIELD_CHARS
    while (offset < text.length) {
        context.ensureActive()
        val count = minOf(EXPORT_WRITE_CHUNK_CHARS, text.length - offset)
        write(text, offset, count)
        offset += count
        charsUntilYield -= count
        if (charsUntilYield <= 0) {
            yield()
            charsUntilYield = EXPORT_WRITE_YIELD_CHARS
        }
    }
}
