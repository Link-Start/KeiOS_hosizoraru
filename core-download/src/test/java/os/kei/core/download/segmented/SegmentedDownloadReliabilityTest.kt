package os.kei.core.download.segmented

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentedDownloadReliabilityTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `short caller call timeout does not cap single stream fallback`() = runBlocking {
        val bytes = ByteArray(8) { (it + 3).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Length", bytes.size)
                        .setBody(Buffer().write(bytes))
                        .throttleBody(1, 50, TimeUnit.MILLISECONDS)
            }
            val outputFile = temp.newFile("single-timeout.bin").apply { delete() }
            val client = OkHttpClient.Builder()
                .callTimeout(100, TimeUnit.MILLISECONDS)
                .build()

            SegmentedDownloadClient(client, Dispatchers.IO).downloadToFile(
                request = SegmentedDownloadRequest(
                    url = server.url("/asset.bin").toString(),
                    outputFile = outputFile,
                ),
                options = testOptions(minParallelSizeBytes = Long.MAX_VALUE),
            )

            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    @Test
    fun `range capable single connection resumes a partial response`() = runBlocking {
        val bytes = ByteArray(32) { (it + 17).toByte() }
        val disconnected = AtomicBoolean(false)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") return rangeResponse(bytes, 0, 0)
                    if (range.isBlank()) return MockResponse().setResponseCode(500)
                    val response = rangeResponse(bytes, range)
                    return if (disconnected.compareAndSet(false, true)) {
                        response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    } else {
                        response
                    }
                }
            }
            val outputFile = temp.newFile("single-range-resume.bin").apply { delete() }

            val result = SegmentedDownloadClient(OkHttpClient(), Dispatchers.IO).downloadToFile(
                request = SegmentedDownloadRequest(
                    url = server.url("/asset.bin").toString(),
                    outputFile = outputFile,
                ),
                options = testOptions(minParallelSizeBytes = Long.MAX_VALUE),
            )

            val ranges = server.takeAllRequests()
                .mapNotNull { it.getHeader("Range") }
                .filterNot { it == "bytes=0-0" }
            assertEquals(false, result.parallel)
            assertTrue(ranges.size >= 2, "ranges=$ranges")
            assertTrue(ranges.drop(1).any { it.substringAfter("bytes=").substringBefore('-').toLong() > 0L })
            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    @Test
    fun `retry after header controls rate limit delay`() = runBlocking {
        val bytes = ByteArray(8) { (it + 41).toByte() }
        val throttled = AtomicBoolean(false)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range").orEmpty()
                    if (range == "bytes=0-0") return rangeResponse(bytes, 0, 0)
                    if (throttled.compareAndSet(false, true)) {
                        return MockResponse()
                            .setResponseCode(429)
                            .addHeader("Retry-After", "1")
                    }
                    return rangeResponse(bytes, range)
                }
            }
            val outputFile = temp.newFile("retry-after.bin").apply { delete() }
            val startedNs = System.nanoTime()

            val result = SegmentedDownloadClient(OkHttpClient(), Dispatchers.IO).downloadToFile(
                request = SegmentedDownloadRequest(
                    url = server.url("/asset.bin").toString(),
                    outputFile = outputFile,
                ),
                options = testOptions(
                    minParallelSizeBytes = 1,
                    retryDelayMs = 1,
                ),
            )

            val elapsedMs = (System.nanoTime() - startedNs) / 1_000_000L
            assertEquals(1, result.retryCount)
            assertTrue(elapsedMs >= 900L, "elapsedMs=$elapsedMs")
            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    @Test
    fun `range unavailable single stream restarts after partial body`() = runBlocking {
        val bytes = ByteArray(32) { (it + 67).toByte() }
        val disconnected = AtomicBoolean(false)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val response = MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Length", bytes.size)
                        .setBody(Buffer().write(bytes))
                    val isProbe = request.getHeader("Range") == "bytes=0-0"
                    return if (!isProbe && disconnected.compareAndSet(false, true)) {
                        response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    } else {
                        response
                    }
                }
            }
            val outputFile = temp.newFile("single-restart.bin").apply { delete() }

            val result = SegmentedDownloadClient(OkHttpClient(), Dispatchers.IO).downloadToFile(
                request = SegmentedDownloadRequest(
                    url = server.url("/asset.bin").toString(),
                    outputFile = outputFile,
                ),
                options = testOptions(minParallelSizeBytes = Long.MAX_VALUE),
            )

            assertEquals(false, result.parallel)
            assertEquals(1, result.retryCount)
            assertEquals(3, server.requestCount)
            assertContentEquals(bytes, outputFile.readBytes())
        }
    }

    private fun testOptions(
        minParallelSizeBytes: Long,
        retryDelayMs: Long = 1,
    ): SegmentedDownloadOptions =
        SegmentedDownloadOptions(
            minParallelSizeBytes = minParallelSizeBytes,
            initialPartSizeBytes = 32,
            maxConnections = 1,
            maxRetriesPerPart = 2,
            retryDelayMs = retryDelayMs,
            progressIntervalMs = 0,
            requireHttpsForParallel = false,
            bufferSizeBytes = 4,
            writeQueueCapacity = 1,
        )
}
