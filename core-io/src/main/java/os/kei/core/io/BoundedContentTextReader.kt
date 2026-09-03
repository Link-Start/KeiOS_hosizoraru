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
import java.io.InputStream
import java.io.Reader

const val DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES: Long = 5L * 1024L * 1024L

data class BoundedContentTextReadResult(
    val text: String,
    val byteCount: Long
)

enum class BoundedContentReadLimitStage {
    DeclaredLength,
    Streaming,
}

class BoundedContentTextReadTooLargeException(
    val maxBytes: Long,
    val observedBytes: Long = maxBytes + 1L,
    val declaredBytes: Long? = null,
    val stage: BoundedContentReadLimitStage = BoundedContentReadLimitStage.Streaming,
) : IllegalArgumentException(
    buildString {
        append("content text exceeds ")
        append(maxBytes)
        append(" bytes")
        append(" (stage=")
        append(stage.name)
        append(", observed=")
        append(observedBytes)
        declaredBytes?.let { declared ->
            append(", declared=")
            append(declared)
        }
        append(')')
    },
)

suspend fun ContentResolver.readTextFromUriLimited(
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
                    throw BoundedContentTextReadTooLargeException(
                        maxBytes = maxBytes,
                        observedBytes = total,
                    )
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

fun InputStream.readTextLimitedBlocking(
    maxBytes: Long = DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES
): BoundedContentTextReadResult {
    require(maxBytes > 0L) { "maxBytes must be positive" }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) {
            throw BoundedContentTextReadTooLargeException(
                maxBytes = maxBytes,
                observedBytes = total,
            )
        }
        output.write(buffer, 0, read)
    }
    return BoundedContentTextReadResult(
        text = output.toString(Charsets.UTF_8.name()),
        byteCount = total
    )
}

/**
 * Bounds a stream that is *parsed* rather than collected.
 *
 * [readTextLimitedBlocking] and [stringLimitedBlocking] both materialise the whole body, which is the
 * right shape for a response small enough to hold. A streamed document is the other case: an F-Droid
 * `index-v2.json` runs from a few megabytes to nearly sixty, and the point of streaming it is never to
 * hold it at all — so the budget has to be enforced as it flows, not after.
 *
 * Throws [BoundedContentTextReadTooLargeException] at the byte the budget is passed, so a caller that
 * cannot afford a repository's index spends the budget and no more before falling back. Counts characters
 * read, which for the UTF-8 documents this guards is at or under the byte count — a conservative bound in
 * the direction that matters.
 */
fun Reader.boundedBy(maxBytes: Long): Reader {
    require(maxBytes > 0L) { "maxBytes must be positive" }
    val delegate = this
    return object : Reader() {
        private var total = 0L

        /**
         * Overridden rather than inherited, and that is a throughput decision rather than tidiness.
         *
         * `Reader`'s own single-char `read()` allocates a one-element `CharArray` per call and routes it
         * through the bulk overload. A streaming JSON scanner reads one character at a time, so inheriting
         * it would mean fourteen million throwaway arrays to walk a fourteen-megabyte index — and it would
         * also step around the buffering of whatever this wraps.
         */
        override fun read(): Int {
            val char = delegate.read()
            if (char < 0) return char
            countAndCheck(1L)
            return char
        }

        override fun read(
            cbuf: CharArray,
            off: Int,
            len: Int,
        ): Int {
            val read = delegate.read(cbuf, off, len)
            if (read < 0) return read
            countAndCheck(read.toLong())
            return read
        }

        // Delegated so wrapping cannot quietly change what the underlying reader supports: `ready()`
        // defaults to false on a bare Reader, and mark/reset default to unsupported.
        override fun ready(): Boolean = delegate.ready()

        override fun markSupported(): Boolean = delegate.markSupported()

        override fun mark(readAheadLimit: Int) = delegate.mark(readAheadLimit)

        override fun reset() = delegate.reset()

        override fun skip(n: Long): Long {
            val skipped = delegate.skip(n)
            countAndCheck(skipped)
            return skipped
        }

        override fun close() = delegate.close()

        private fun countAndCheck(count: Long) {
            total += count
            if (total > maxBytes) {
                throw BoundedContentTextReadTooLargeException(
                    maxBytes = maxBytes,
                    observedBytes = total,
                )
            }
        }
    }
}

private const val YIELD_EVERY_BYTES = 64L * 1024L
