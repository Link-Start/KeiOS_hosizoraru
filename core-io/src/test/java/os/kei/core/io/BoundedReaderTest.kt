package os.kei.core.io

import java.io.BufferedReader
import java.io.Reader
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The streaming bound, which guards a document that is parsed rather than collected.
 *
 * It exists for F-Droid's `index-v2.json`: a few megabytes on a third-party repository and nearly sixty
 * on f-droid.org, streamed so it is never held. A caller that cannot afford the large one has to spend
 * its budget and stop, which means the limit has to hold on every path a reader can be read through.
 */
class BoundedReaderTest {
    @Test
    fun `a document inside the budget reads through unchanged`() {
        val text = "x".repeat(500)

        assertEquals(text, StringReader(text).boundedBy(1_000L).readText())
    }

    @Test
    fun `the bulk read stops at the byte the budget is passed`() {
        val reader = StringReader("y".repeat(5_000)).boundedBy(1_000L)

        assertFailsWith<BoundedContentTextReadTooLargeException> { reader.readText() }
    }

    @Test
    fun `the single-character read is bounded too, which is how a JSON scanner reads`() {
        // The stream parser this guards consumes one character at a time. A bound that only counted bulk
        // reads would let an unbounded document through the very path that reads it.
        val reader = StringReader("z".repeat(50)).boundedBy(10L)

        assertFailsWith<BoundedContentTextReadTooLargeException> {
            repeat(50) { reader.read() }
        }
    }

    @Test
    fun `the single-character read does not fall back to the allocating default`() {
        // `Reader.read()` allocates a one-element array per call and routes it through the bulk overload.
        // Inheriting it would mean one throwaway array per character of a fourteen-megabyte index, and
        // would step around the buffering of whatever is wrapped. Asserted by counting which overload the
        // delegate actually sees.
        val counting = CountingReader(StringReader("abcdef"))
        val bounded = counting.boundedBy(100L)

        repeat(6) { bounded.read() }

        assertEquals(6, counting.singleCharReads)
        assertEquals(0, counting.bulkReads, "a single-char read must not become a bulk read")
    }

    @Test
    fun `skipping counts against the budget rather than slipping past it`() {
        val reader = StringReader("q".repeat(100)).boundedBy(10L)

        assertFailsWith<BoundedContentTextReadTooLargeException> { reader.skip(50L) }
    }

    @Test
    fun `wrapping does not change what the underlying reader supports`() {
        // `ready()` is false and mark/reset unsupported on a bare Reader, so a wrapper that inherits them
        // silently downgrades a BufferedReader.
        val buffered = BufferedReader(StringReader("hello"))
        val bounded = buffered.boundedBy(100L)

        assertTrue(bounded.markSupported())
        assertTrue(bounded.ready())
        bounded.mark(8)
        assertEquals('h'.code, bounded.read())
        bounded.reset()
        assertEquals('h'.code, bounded.read())

        // And delegated rather than hardcoded true: a plain Reader reports not-ready, and the wrapper has
        // to say the same. `StringReader` is no use for this half -- it reports ready while merely open.
        assertFalse(CountingReader(StringReader("hi")).boundedBy(10L).ready())
    }

    @Test
    fun `the exception says what the budget was and what was observed`() {
        val failure =
            assertFailsWith<BoundedContentTextReadTooLargeException> {
                StringReader("w".repeat(64)).boundedBy(8L).readText()
            }

        assertEquals(8L, failure.maxBytes)
        assertTrue(failure.observedBytes > 8L)
        assertEquals(BoundedContentReadLimitStage.Streaming, failure.stage)
    }

    @Test
    fun `a non-positive budget is rejected rather than silently unbounded`() {
        assertFailsWith<IllegalArgumentException> { StringReader("a").boundedBy(0L) }
        assertFailsWith<IllegalArgumentException> { StringReader("a").boundedBy(-1L) }
    }

    @Test
    fun `closing reaches the wrapped reader`() {
        val counting = CountingReader(StringReader("a"))

        counting.boundedBy(10L).close()

        assertTrue(counting.closed)
    }
}

private class CountingReader(
    private val delegate: Reader,
) : Reader() {
    var singleCharReads = 0
        private set
    var bulkReads = 0
        private set
    var closed = false
        private set

    override fun read(): Int {
        singleCharReads++
        return delegate.read()
    }

    override fun read(
        cbuf: CharArray,
        off: Int,
        len: Int,
    ): Int {
        bulkReads++
        return delegate.read(cbuf, off, len)
    }

    override fun close() {
        closed = true
        delegate.close()
    }
}
