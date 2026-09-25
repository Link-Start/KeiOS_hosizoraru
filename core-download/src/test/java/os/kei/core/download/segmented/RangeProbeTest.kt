package os.kei.core.download.segmented

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class RangeProbeTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `probe succeeds with 206 and valid content range`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes 0-0/128")
                    .setBody(Buffer().writeByte(1)),
            )
            val probe = RangeProbe(OkHttpClient())

            val result = probe.probe(
                SegmentedDownloadRequest(
                    url = server.url("/file.bin").toString(),
                    outputFile = temp.newFile("probe.bin"),
                )
            )

            assertEquals(true, result.rangeSupported)
            assertEquals(128, result.totalBytes)
            assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))
        }
    }

    @Test
    fun `probe falls back on malformed content range`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes 0-0/*")
                    .setBody(Buffer().writeByte(1)),
            )
            val probe = RangeProbe(OkHttpClient())

            val result = probe.probe(
                SegmentedDownloadRequest(
                    url = server.url("/file.bin").toString(),
                    outputFile = temp.newFile("malformed.bin"),
                )
            )

            assertEquals(false, result.rangeSupported)
            assertEquals("invalid-content-range", result.fallbackReason)
        }
    }
}
