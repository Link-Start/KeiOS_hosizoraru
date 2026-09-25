package os.kei.core.download.segmented

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SegmentedDownloadClientTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `preallocated file length does not satisfy written byte coverage`() {
        val file = temp.newFile("preallocated.part")
        RandomAccessFile(file, "rw").use { it.setLength(32) }

        assertEquals(32, file.length())
        assertFailsWith<IOException> {
            validateWrittenByteCount(writtenBytes = 31, expectedBytes = 32)
        }
    }

    @Test
    fun `segmented download preserves bytes across uneven sizes and part plans`() = runBlocking {
        val cases = listOf(
            IntegrityCase(size = 1_025, partSizeBytes = 127, maxConnections = 3),
            IntegrityCase(size = 65_539, partSizeBytes = 4_097, maxConnections = 5),
            IntegrityCase(size = 1_048_699, partSizeBytes = 65_537, maxConnections = 4),
        )
        cases.forEachIndexed { index, case ->
            val bytes = ByteArray(case.size) { byteIndex ->
                ((byteIndex * 31 + index * 17) and 0xff).toByte()
            }
            MockWebServer().use { server ->
                server.dispatcher = byteRangeDispatcher(bytes)
                val outputFile = temp.newFile("integrity-$index.bin").apply { delete() }

                client().downloadToFile(
                    request = request(server, outputFile),
                    options = testOptions(
                        partSizeBytes = case.partSizeBytes,
                        maxConnections = case.maxConnections,
                        bufferSizeBytes = 257,
                    ),
                )

                assertContentEquals(bytes, outputFile.readBytes(), "case=$case")
            }
        }
    }

    @Test
    fun `range unavailable uses single stream fallback`() = runBlocking {
        val bytes = ByteArray(16) { (it + 1).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Length", bytes.size)
                        .setBody(Buffer().write(bytes))
                }
            }
            val outputFile = temp.newFile("fallback.bin").apply { delete() }

            val result = client().downloadToFile(
                request = request(server, outputFile),
                options = testOptions(partSizeBytes = 8, maxConnections = 4),
            )

            assertEquals(false, result.parallel)
            assertEquals(false, result.rangeSupported)
            assertEquals("range-ignored", result.fallbackReason)
            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    @Test
    fun `unexpected content range falls back to full stream`() = runBlocking {
        val bytes = ByteArray(16) { (it + 29).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") return rangeResponse(bytes, 0, 0)
                    if (range == "bytes=0-3") {
                        return MockResponse()
                            .setResponseCode(206)
                            .addHeader("Content-Range", "bytes 1-4/${bytes.size}")
                            .setBody(Buffer().write(bytes.copyOfRange(0, 4)))
                    }
                    return rangeResponse(bytes, range)
                }
            }
            val outputFile = temp.newFile("bad-range.bin").apply { writeText("old") }
            val partFile = File(outputFile.parentFile, "${outputFile.name}.part")

            val result = client().downloadToFile(
                request = request(server, outputFile),
                options = testOptions(
                    partSizeBytes = 4,
                    maxConnections = 2,
                    maxRetriesPerPart = 0,
                ),
            )

            assertEquals(false, result.parallel)
            assertEquals("range-protocol-error", result.fallbackReason)
            assertContentEquals(bytes, outputFile.readBytes())
            assertFalse(partFile.exists())
        }
    }

    @Test
    fun `sha256 mismatch fails before replacing output file`() = runBlocking {
        val bytes = ByteArray(32) { (it + 37).toByte() }
        val existingBytes = byteArrayOf(5, 4, 3, 2)
        MockWebServer().use { server ->
            server.dispatcher = byteRangeDispatcher(bytes)
            val outputFile = temp.newFile("digest.bin").apply { writeBytes(existingBytes) }
            val partFile = File(outputFile.parentFile, "${outputFile.name}.part")

            assertFailsWith<SegmentedDownloadException> {
                client().downloadToFile(
                    request = request(server, outputFile).copy(
                        expectedSha256 = "sha256:${"0".repeat(64)}",
                    ),
                    options = testOptions(partSizeBytes = 8, maxConnections = 4),
                )
            }

            assertContentEquals(existingBytes, outputFile.readBytes())
            assertFalse(partFile.exists())
        }
    }

    @Test
    fun `sha256 match allows final output replacement`() = runBlocking {
        val bytes = ByteArray(32) { (it + 41).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = byteRangeDispatcher(bytes)
            val outputFile = temp.newFile("digest-match.bin").apply { delete() }

            client().downloadToFile(
                request = request(server, outputFile).copy(
                    expectedSha256 = bytes.sha256Hex(),
                ),
                options = testOptions(partSizeBytes = 8, maxConnections = 4),
            )

            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    @Test
    fun `worker retries after partial EOF and resumes from offset`() = runBlocking {
        val bytes = ByteArray(12) { (it + 7).toByte() }
        val truncatedOnce = AtomicBoolean(false)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") return rangeResponse(bytes, 0, 0)
                    if (range == "bytes=0-3" && truncatedOnce.compareAndSet(false, true)) {
                        return MockResponse()
                            .setResponseCode(206)
                            .addHeader("Content-Range", "bytes 0-3/${bytes.size}")
                            .setBody(Buffer().write(bytes.copyOfRange(0, 2)))
                    }
                    return rangeResponse(bytes, range)
                }
            }
            val outputFile = temp.newFile("partial.bin").apply { delete() }

            val result = client().downloadToFile(
                request = request(server, outputFile),
                options = testOptions(partSizeBytes = 4, maxConnections = 2),
            )

            assertEquals(true, result.parallel)
            assertEquals(1, result.retryCount)
            assertContentEquals(bytes, outputFile.readBytes())
            val ranges = server.takeAllRequests().map { it.getHeader("Range").orEmpty() }
            assertTrue("bytes=2-3" in ranges)
        }
    }

    @Test
    fun `zero max retries fails after partial EOF and removes part file`() = runBlocking {
        val bytes = ByteArray(12) { (it + 19).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") return rangeResponse(bytes, 0, 0)
                    if (range == "bytes=0-3") {
                        return MockResponse()
                            .setResponseCode(206)
                            .addHeader("Content-Range", "bytes 0-3/${bytes.size}")
                            .setBody(Buffer().write(bytes.copyOfRange(0, 2)))
                    }
                    return rangeResponse(bytes, range)
                }
            }
            val outputFile = temp.newFile("no-retry.bin").apply { delete() }
            val partFile = File(outputFile.parentFile, "${outputFile.name}.part")

            assertFailsWith<SegmentedDownloadException> {
                client().downloadToFile(
                    request = request(server, outputFile),
                    options = testOptions(
                        partSizeBytes = 4,
                        maxConnections = 2,
                        maxRetriesPerPart = 0,
                    ),
                )
            }

            assertFalse(outputFile.exists())
            assertFalse(partFile.exists())
            val ranges = server.takeAllRequests().map { it.getHeader("Range").orEmpty() }
            assertFalse("bytes=2-3" in ranges)
        }
    }

    @Test
    fun `active connections clamp to part count`() = runBlocking {
        val bytes = ByteArray(12) { (it + 5).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = byteRangeDispatcher(bytes)
            val outputFile = temp.newFile("connections.bin").apply { delete() }
            val progress = mutableListOf<SegmentedDownloadProgress>()

            client().downloadToFile(
                request = request(server, outputFile),
                options = testOptions(partSizeBytes = 8, maxConnections = 4),
                onProgress = { progress += it },
            )

            assertEquals(2, progress.first { it.parallel }.activeConnections)
            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    @Test
    fun `range response body is bounded to requested part`() = runBlocking {
        val bytes = ByteArray(12) { (it + 17).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") return rangeResponse(bytes, 0, 0)
                    if (range == "bytes=0-3") {
                        return MockResponse()
                            .setResponseCode(206)
                            .addHeader("Content-Range", "bytes 0-3/${bytes.size}")
                            .setBody(Buffer().write(bytes))
                    }
                    return rangeResponse(bytes, range)
                }
            }
            val outputFile = temp.newFile("bounded.bin").apply { delete() }

            client().downloadToFile(
                request = request(server, outputFile),
                options = testOptions(partSizeBytes = 4, maxConnections = 2),
            )

            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    @Test
    fun `slow active range keeps ownership until completion`() = runBlocking {
        val partSizeBytes = 512L * 1024L
        val bytes = ByteArray((2L * 1024L * 1024L).toInt()) { (it % 251).toByte() }
        MockWebServer().use { server ->
            val throttledOnce = AtomicBoolean(true)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") return rangeResponse(bytes, 0, 0)
                    val response = rangeResponse(bytes, range)
                    return if (
                        range == "bytes=0-${partSizeBytes - 1L}" &&
                        throttledOnce.compareAndSet(true, false)
                    ) {
                        response.throttleBody(32L * 1024L, 20, TimeUnit.MILLISECONDS)
                    } else {
                        response
                    }
                }
            }
            val outputFile = temp.newFile("steal.bin").apply { delete() }

            val result = client().downloadToFile(
                request = request(server, outputFile),
                options = testOptions(
                    partSizeBytes = partSizeBytes,
                    maxConnections = 4,
                    bufferSizeBytes = 64 * 1024,
                ),
            )

            assertContentEquals(bytes, outputFile.readBytes())
            assertEquals(0, result.stealCount)
            assertEquals(0, result.handoffCount)
        }
    }

    @Test
    fun `cancellation removes temp part file`() = runBlocking {
        val bytes = ByteArray(128) { it.toByte() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") return rangeResponse(bytes, 0, 0)
                    return rangeResponse(bytes, range)
                        .throttleBody(1, 250, TimeUnit.MILLISECONDS)
                }
            }
            val outputFile = temp.newFile("cancel.bin").apply { delete() }
            val partFile = File(outputFile.parentFile, "${outputFile.name}.part")
            val started = AtomicInteger(0)
            val job = launch {
                client().downloadToFile(
                    request = request(server, outputFile),
                    options = testOptions(partSizeBytes = 64, maxConnections = 2),
                    onProgress = {
                        if (it.downloadedBytes > 0L) started.incrementAndGet()
                    },
                )
            }

            repeat(20) {
                if (partFile.exists() || started.get() > 0) return@repeat
                delay(50)
            }
            job.cancelAndJoin()

            assertFalse(partFile.exists())
            assertFalse(outputFile.exists())
        }
    }

    private fun client(): SegmentedDownloadClient =
        SegmentedDownloadClient(
            client = OkHttpClient(),
            dispatcher = Dispatchers.IO,
        )

    private fun request(
        server: MockWebServer,
        outputFile: File,
    ): SegmentedDownloadRequest =
        SegmentedDownloadRequest(
            url = server.url("/download.bin").toString(),
            outputFile = outputFile,
            headers = mapOf("User-Agent" to "KeiOS-Test"),
        )

    private fun testOptions(
        partSizeBytes: Long,
        maxConnections: Int,
        bufferSizeBytes: Int = 4,
        maxRetriesPerPart: Int = 2,
    ): SegmentedDownloadOptions =
        SegmentedDownloadOptions(
            minParallelSizeBytes = 1,
            initialPartSizeBytes = partSizeBytes,
            maxConnections = maxConnections,
            maxRetriesPerPart = maxRetriesPerPart,
            retryDelayMs = 1,
            progressIntervalMs = 0,
            requireHttpsForParallel = false,
            bufferSizeBytes = bufferSizeBytes,
        )

    private data class IntegrityCase(
        val size: Int,
        val partSizeBytes: Long,
        val maxConnections: Int,
    )

    private fun ByteArray.sha256Hex(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
