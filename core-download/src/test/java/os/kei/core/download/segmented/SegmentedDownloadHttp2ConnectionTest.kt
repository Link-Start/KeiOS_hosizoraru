package os.kei.core.download.segmented

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentedDownloadHttp2ConnectionTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `connection strategy controls physical HTTP2 connections`() = runBlocking {
        val bytes = ByteArray(512 * 1024) { (it * 29).toByte() }

        val shared = runDownload(bytes, SegmentedDownloadConnectionStrategy.Shared, "shared.bin")
        val isolated = runDownload(bytes, SegmentedDownloadConnectionStrategy.IsolatedPerWorker, "isolated.bin")

        assertEquals(1, shared.physicalConnections)
        assertTrue(isolated.physicalConnections > shared.physicalConnections)
        assertEquals("h2_prior_knowledge", shared.protocol)
        assertEquals("h2_prior_knowledge", isolated.protocol)
    }

    private suspend fun runDownload(
        bytes: ByteArray,
        strategy: SegmentedDownloadConnectionStrategy,
        fileName: String,
    ): ConnectionObservation =
        MockWebServer().use { server ->
            server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    rangeResponse(bytes, request.getHeader("Range"))
                        .throttleBody(16L * 1024L, 5L, TimeUnit.MILLISECONDS)
            }
            val tracker = BenchmarkConnectionTracker()
            val client = OkHttpClient.Builder()
                .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                .eventListener(tracker)
                .build()
            val outputFile = temp.newFile(fileName).apply { delete() }
            try {
                SegmentedDownloadClient(client, Dispatchers.IO).downloadToFile(
                    request = SegmentedDownloadRequest(
                        url = server.url("/asset.bin").toString(),
                        outputFile = outputFile,
                        expectedSizeBytes = bytes.size.toLong(),
                    ),
                    options = SegmentedDownloadOptions(
                        minParallelSizeBytes = 1L,
                        initialPartSizeBytes = 128L * 1024L,
                        maxConnections = 4,
                        maxRetriesPerPart = 1,
                        retryDelayMs = 1L,
                        progressIntervalMs = 0L,
                        requireHttpsForParallel = false,
                        bufferSizeBytes = 16 * 1024,
                        connectionStrategy = strategy,
                    ),
                )
                assertContentEquals(bytes, outputFile.readBytes())
                ConnectionObservation(
                    physicalConnections = tracker.physicalConnectionCount,
                    protocol = tracker.protocolLabel("unknown"),
                )
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }

    private data class ConnectionObservation(
        val physicalConnections: Int,
        val protocol: String,
    )
}
