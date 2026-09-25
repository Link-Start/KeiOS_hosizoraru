package os.kei.core.download.segmented

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SegmentedDownloadSecurityAndIdentityTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `redirected requests strip origin sensitive headers`() = runBlocking {
        val bytes = ByteArray(32) { (it + 11).toByte() }
        MockWebServer().use { origin ->
            MockWebServer().use { cdn ->
                origin.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        MockResponse()
                            .setResponseCode(302)
                            .addHeader("Location", cdn.url("/asset.bin"))
                }
                cdn.dispatcher = byteRangeDispatcher(bytes)
                val outputFile = temp.newFile("redirected.bin").apply { delete() }

                downloader().downloadToFile(
                    request = SegmentedDownloadRequest(
                        url = origin.url("/download.bin").toString(),
                        outputFile = outputFile,
                        headers = mapOf(
                            "Authorization" to "Bearer secret-token",
                            "Cookie" to "session=secret-cookie",
                            "X-Download-Trace" to "trace-1",
                        ),
                    ),
                    options = testOptions(),
                )

                // Only the probe hits the origin; every data range reuses the final URL it resolved.
                assertEquals(1, origin.requestCount)
                val cdnRequests = cdn.takeAllRequests()
                assertTrue(cdnRequests.size > 1)
                cdnRequests.forEach { request ->
                    assertNull(request.getHeader("Authorization"))
                    assertNull(request.getHeader("Cookie"))
                    assertEquals("trace-1", request.getHeader("X-Download-Trace"))
                }
                assertContentEquals(bytes, outputFile.readBytes())
            }
        }
    }

    @Test
    fun `same origin requests preserve authorization headers`() = runBlocking {
        val bytes = ByteArray(32) { (it + 19).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = byteRangeDispatcher(bytes)
            val outputFile = temp.newFile("same-origin.bin").apply { delete() }

            downloader().downloadToFile(
                request = SegmentedDownloadRequest(
                    url = server.url("/asset.bin").toString(),
                    outputFile = outputFile,
                    headers = mapOf(
                        "Authorization" to "Bearer same-origin",
                        "Cookie" to "session=same-origin",
                    ),
                ),
                options = testOptions(),
            )

            server.takeAllRequests().forEach { request ->
                assertEquals("Bearer same-origin", request.getHeader("Authorization"))
                assertEquals("session=same-origin", request.getHeader("Cookie"))
            }
            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    @Test
    fun `etag change restarts download from a fresh resource snapshot`() = runBlocking {
        val firstBytes = ByteArray(32) { (it + 31).toByte() }
        val secondBytes = ByteArray(32) { (it + 79).toByte() }
        val changed = AtomicBoolean(false)
        val probeCount = AtomicInteger(0)
        val firstDataIfRange = AtomicReference<String?>()
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") {
                        probeCount.incrementAndGet()
                        val currentBytes = if (changed.get()) secondBytes else firstBytes
                        val etag = if (changed.get()) "\"v2\"" else "\"v1\""
                        return rangeResponse(currentBytes, start = 0, endInclusive = 0)
                            .addHeader("ETag", etag)
                    }
                    if (changed.compareAndSet(false, true)) {
                        firstDataIfRange.set(request.getHeader("If-Range"))
                        return MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Length", secondBytes.size)
                            .addHeader("ETag", "\"v2\"")
                            .setBody(Buffer().write(secondBytes))
                    }
                    return rangeResponse(secondBytes, range)
                        .addHeader("ETag", "\"v2\"")
                }
            }
            val outputFile = temp.newFile("resource-change.bin").apply { delete() }

            downloader().downloadToFile(
                request = SegmentedDownloadRequest(
                    url = server.url("/asset.bin").toString(),
                    outputFile = outputFile,
                ),
                options = testOptions(),
            )

            assertEquals("\"v1\"", firstDataIfRange.get())
            assertEquals(2, probeCount.get())
            assertContentEquals(secondBytes, outputFile.readBytes())
        }
    }

    @Test
    fun `expected size mismatch fails before data ranges and preserves output`() = runBlocking {
        val bytes = ByteArray(32) { (it + 101).toByte() }
        val previousBytes = byteArrayOf(7, 6, 5, 4)
        MockWebServer().use { server ->
            server.dispatcher = byteRangeDispatcher(bytes)
            val outputFile = temp.newFile("size-mismatch.bin").apply { writeBytes(previousBytes) }
            val partFile = File(outputFile.parentFile, "${outputFile.name}.part")

            val error = assertFailsWith<DownloadSizeMismatchException> {
                downloader().downloadToFile(
                    request = SegmentedDownloadRequest(
                        url = server.url("/asset.bin").toString(),
                        outputFile = outputFile,
                        expectedSizeBytes = bytes.size.toLong() - 1L,
                    ),
                    options = testOptions(),
                )
            }

            assertEquals(bytes.size.toLong() - 1L, error.expectedBytes)
            assertEquals(bytes.size.toLong(), error.actualBytes)
            assertEquals(1, server.requestCount)
            assertContentEquals(previousBytes, outputFile.readBytes())
            assertEquals(false, partFile.exists())
        }
    }

    private fun downloader(): SegmentedDownloadClient =
        SegmentedDownloadClient(
            client = OkHttpClient(),
            dispatcher = Dispatchers.IO,
        )

    private fun testOptions(): SegmentedDownloadOptions =
        SegmentedDownloadOptions(
            minParallelSizeBytes = 1,
            initialPartSizeBytes = 8,
            maxConnections = 4,
            maxRetriesPerPart = 0,
            retryDelayMs = 0,
            progressIntervalMs = 0,
            requireHttpsForParallel = false,
            bufferSizeBytes = 4,
        )
}
