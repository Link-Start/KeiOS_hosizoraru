package os.kei.ui.page.main.jsonimport

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.io.BoundedContentTextReadResult
import os.kei.core.io.BoundedContentTextReadTooLargeException
import os.kei.core.io.readTextFromUriLimited

internal class KeiOSJsonImportSourceReader(
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun sourceFromIntent(context: Context, intent: Intent?): KeiOSJsonImportIntentSource {
        return withContext(ioDispatcher) {
            resolveSourceFromIntent(context, intent)
        }
    }

    private fun resolveSourceFromIntent(
        context: Context,
        intent: Intent?
    ): KeiOSJsonImportIntentSource {
        if (intent == null) {
            return KeiOSJsonImportIntentSource(
                displayName = context.getString(R.string.json_import_source_unknown)
            )
        }
        val streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: intent.data
        if (streamUri != null) {
            val metadata = queryUriMetadata(context, streamUri)
            return KeiOSJsonImportIntentSource(
                uri = streamUri,
                displayName = metadata.displayName.ifBlank {
                    streamUri.lastPathSegment?.substringAfterLast('/').orEmpty()
                },
                mimeType = intent.type.orEmpty().ifBlank { metadata.mimeType },
                declaredSizeBytes = metadata.sizeBytes
            )
        }
        val inlineText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (inlineText != null) {
            return KeiOSJsonImportIntentSource(
                inlineText = inlineText,
                displayName = context.getString(R.string.json_import_inline_text_name),
                mimeType = intent.type.orEmpty(),
                declaredSizeBytes = inlineText.toByteArray(Charsets.UTF_8).size.toLong()
            )
        }
        return KeiOSJsonImportIntentSource(
            displayName = context.getString(R.string.json_import_source_unknown)
        )
    }

    suspend fun readSource(
        context: Context,
        source: KeiOSJsonImportIntentSource
    ): KeiOSJsonImportFile {
        return withContext(ioDispatcher) {
            val inlineText = source.inlineText
            if (inlineText != null) {
                val bytes = inlineText.toByteArray(Charsets.UTF_8)
                if (bytes.isEmpty()) {
                    throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.EmptyFile)
                }
                if (bytes.size > KEIOS_JSON_IMPORT_MAX_BYTES) {
                    throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.FileTooLarge)
                }
                return@withContext KeiOSJsonImportFile(
                    raw = inlineText,
                    displayName = source.displayName,
                    mimeType = source.mimeType,
                    sizeBytes = bytes.size.toLong(),
                    sourceDescription = context.getString(R.string.json_import_source_inline_text)
                )
            }
            val uri = source.uri
                ?: throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.MissingSource)
            val metadata = queryUriMetadata(context, uri)
            val declaredSize = listOf(source.declaredSizeBytes, metadata.sizeBytes)
                .firstOrNull { it > 0L }
                ?: -1L
            if (declaredSize > KEIOS_JSON_IMPORT_MAX_BYTES) {
                throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.FileTooLarge)
            }
            val readResult = readUriTextLimited(context, uri)
            if (readResult.text.isEmpty()) {
                throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.EmptyFile)
            }
            KeiOSJsonImportFile(
                raw = readResult.text,
                displayName = source.displayName.ifBlank { metadata.displayName },
                mimeType = source.mimeType.ifBlank { metadata.mimeType },
                sizeBytes = readResult.byteCount,
                sourceDescription = uri.toString()
            )
        }
    }

    private suspend fun readUriTextLimited(
        context: Context,
        uri: Uri
    ): BoundedContentTextReadResult {
        return try {
            context.contentResolver.readTextFromUriLimited(
                uri = uri,
                maxBytes = KEIOS_JSON_IMPORT_MAX_BYTES,
                ioDispatcher = ioDispatcher
            )
        } catch (error: BoundedContentTextReadTooLargeException) {
            throw KeiOSJsonImportException(
                reason = KeiOSJsonImportFailureReason.FileTooLarge,
                cause = error
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw KeiOSJsonImportException(
                reason = KeiOSJsonImportFailureReason.ReadFailed,
                cause = error
            )
        }
    }

    private fun queryUriMetadata(context: Context, uri: Uri): UriMetadata {
        val resolver = context.contentResolver
        return runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                UriMetadata(
                    displayName = cursor.stringColumn(OpenableColumns.DISPLAY_NAME),
                    sizeBytes = cursor.longColumn(OpenableColumns.SIZE),
                    mimeType = resolver.getType(uri).orEmpty()
                )
            }
        }.getOrNull() ?: UriMetadata(mimeType = resolver.getType(uri).orEmpty())
    }

    private fun Cursor.stringColumn(name: String): String {
        val index = getColumnIndex(name)
        return if (index >= 0 && moveToFirst()) getString(index).orEmpty() else ""
    }

    private fun Cursor.longColumn(name: String): Long {
        val index = getColumnIndex(name)
        return if (index >= 0 && moveToFirst()) getLong(index) else -1L
    }

    private data class UriMetadata(
        val displayName: String = "",
        val sizeBytes: Long = -1L,
        val mimeType: String = ""
    )
}
