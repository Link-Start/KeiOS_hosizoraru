package os.kei.core.download.range

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RemoteByteRangeClientTest {
    @Test
    fun `probe and range read validate content range and preserve headers`() = runBlocking {
        val bytes = ByteArray(32) { it.toByte() }
        MockWebServer().use { server ->
            server.dispatcher = byteRangeDispatcher(bytes)
            val client = RemoteByteRangeClient(OkHttpClient(), Dispatchers.IO)
            val request =
                Request.Builder()
                    .url(server.url("/asset.apk"))
                    .header("Authorization", "Bearer test")
                    .build()

            val probe = client.probe(request)
            val result =
                client.read(
                    request = request,
                    start = 4L,
                    endInclusive = 11L,
                    maxBytes = 8L,
                    expectedTotalSize = probe.totalSize,
                    expectedIdentity = probe.identity,
                )

            assertEquals(bytes.size.toLong(), probe.totalSize)
            assertEquals(RemoteResourceIdentity(strongEtag = "\"asset-v1\""), probe.identity)
            assertContentEquals(bytes.copyOfRange(4, 12), result.bytes)
            val probeRequest = server.takeRequest()
            val rangeRequest = server.takeRequest()
            assertEquals("Bearer test", probeRequest.getHeader("Authorization"))
            assertEquals("Bearer test", rangeRequest.getHeader("Authorization"))
            assertEquals("bytes=0-0", probeRequest.getHeader("Range"))
            assertEquals("bytes=4-11", rangeRequest.getHeader("Range"))
        }
    }

    @Test
    fun `range read rejects malformed 206 bodies`() = runBlocking {
        data class Case(val label: String, val response: MockResponse, val endInclusive: Long, val maxBytes: Long)
        listOf(
            Case(
                "response length overflow",
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes 0-1/4")
                    .setBody(Buffer().write(byteArrayOf(0, 1, 2))),
                endInclusive = 1L,
                maxBytes = 2L,
            ),
            Case(
                "declared truncated response",
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes 0-2/4")
                    .setBody(Buffer().write(byteArrayOf(0, 1))),
                endInclusive = 2L,
                maxBytes = 3L,
            ),
            Case(
                "chunked truncated response",
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes 0-2/4")
                    .setChunkedBody(Buffer().write(byteArrayOf(0, 1)), 1),
                endInclusive = 2L,
                maxBytes = 3L,
            ),
        ).forEach { case ->
            MockWebServer().use { server ->
                server.enqueue(case.response)
                val client = RemoteByteRangeClient(OkHttpClient(), Dispatchers.IO)

                assertFailsWith<RemoteByteRangeProtocolException>(case.label) {
                    client.read(
                        request = Request.Builder().url(server.url("/asset")).build(),
                        start = 0L,
                        endInclusive = case.endInclusive,
                        maxBytes = case.maxBytes,
                    )
                }
            }
        }
    }

    @Test
    fun `range read rejects changed remote size`() = runBlocking {
        val calls = AtomicInteger()
        MockWebServer().use { server ->
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val total = if (calls.getAndIncrement() == 0) 16 else 20
                        return MockResponse()
                            .setResponseCode(206)
                            .addHeader("Content-Range", "bytes 0-0/$total")
                            .setBody(Buffer().writeByte(1))
                    }
                }
            val client = RemoteByteRangeClient(OkHttpClient(), Dispatchers.IO)
            val request = Request.Builder().url(server.url("/asset")).build()
            val probe = client.probe(request)

            assertFailsWith<RemoteByteRangeResourceChangedException> {
                client.read(request, 0L, 0L, maxBytes = 1L, expectedTotalSize = probe.totalSize)
            }
            Unit
        }
    }

    @Test
    fun `range limit is checked before network request`() = runBlocking {
        MockWebServer().use { server ->
            val client = RemoteByteRangeClient(OkHttpClient(), Dispatchers.IO)

            assertFailsWith<IllegalArgumentException> {
                client.read(
                    request = Request.Builder().url(server.url("/asset")).build(),
                    start = 0L,
                    endInclusive = 8L,
                    maxBytes = 8L,
                )
            }
            assertEquals(0, server.requestCount)
        }
    }
}

private fun byteRangeDispatcher(bytes: ByteArray): Dispatcher =
    object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val range = request.getHeader("Range").orEmpty().removePrefix("bytes=").split("-")
            val start = range[0].toInt()
            val end = range[1].toInt()
            return MockResponse()
                .setResponseCode(206)
                .addHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                .addHeader("ETag", "\"asset-v1\"")
                .setBody(Buffer().write(bytes.copyOfRange(start, end + 1)))
        }
    }
