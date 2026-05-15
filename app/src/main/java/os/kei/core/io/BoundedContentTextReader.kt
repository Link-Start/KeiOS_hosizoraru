package os.kei.core.io

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream

internal const val DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES: Long = 5L * 1024L * 1024L

internal data class BoundedContentTextReadResult(
    val text: String,
    val byteCount: Long
)

internal class BoundedContentTextReadTooLargeException(
    val maxBytes: Long
) : IllegalArgumentException("content text exceeds $maxBytes bytes")

internal suspend fun ContentResolver.readTextFromUriLimited(
    uri: Uri,
    maxBytes: Long = DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): BoundedContentTextReadResult {
    require(maxBytes > 0L) { "maxBytes must be positive" }
    return withContext(ioDispatcher) {
        openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw BoundedContentTextReadTooLargeException(maxBytes)
                }
                output.write(buffer, 0, read)
                if (total % YIELD_EVERY_BYTES < read) {
                    yield()
                }
            }
            BoundedContentTextReadResult(
                text = output.toString(Charsets.UTF_8.name()),
                byteCount = total
            )
        } ?: error("openInputStream returned null")
    }
}

private const val YIELD_EVERY_BYTES = 64L * 1024L
