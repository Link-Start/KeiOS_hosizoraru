package os.kei.feature.github.data.apk

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteZipEntryReaderTest {
    @Test
    fun `reader fetches AndroidManifest entry through explicit byte ranges`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.remote")
        val apkBytes = zipWithManifest(manifest)
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(apkBytes)
            val reader = RemoteZipEntryReader(client = OkHttpClient())

            val bytes = reader.readEntry(
                url = server.url("/download/app.apk").toString(),
                entryName = "AndroidManifest.xml"
            ).getOrThrow()
            val packageName = AndroidBinaryXmlPackageNameParser.parsePackageName(bytes).getOrThrow()

            assertEquals("os.kei.remote", packageName)
            val ranges = buildList {
                repeat(server.requestCount) {
                    add(server.takeRequest().getHeader("Range").orEmpty())
                }
            }
            assertEquals("bytes=0-0", ranges.first())
            assertTrue(ranges.none { it.startsWith("bytes=-") })
            assertTrue(ranges.size <= 4)
            assertTrue(ranges.any { it.startsWith("bytes=0-") || it.matches(Regex("""bytes=\d+-\d+""")) })
        }
    }

    @Test
    fun `reader fails without downloading full body when range is ignored`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.remote")
        val apkBytes = zipWithManifest(manifest)
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(apkBytes))
            )
            val reader = RemoteZipEntryReader(client = OkHttpClient())

            val result = reader.readEntry(
                url = server.url("/download/app.apk").toString(),
                entryName = "AndroidManifest.xml"
            )

            assertTrue(result.isFailure)
            assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))
        }
    }

    @Test
    fun `reader reuses redirected range url after probe`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.redirect")
        val apkBytes = zipWithManifest(manifest)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                private val assetDispatcher = rangeDispatcher(apkBytes)

                override fun dispatch(request: RecordedRequest): MockResponse {
                    return if (request.path?.startsWith("/download/") == true) {
                        MockResponse()
                            .setResponseCode(302)
                            .addHeader("Location", server.url("/asset/app.apk"))
                    } else {
                        assetDispatcher.dispatch(request)
                    }
                }
            }
            val reader = RemoteZipEntryReader(client = OkHttpClient())

            val bytes = reader.readEntry(
                url = server.url("/download/app.apk").toString(),
                entryName = "AndroidManifest.xml"
            ).getOrThrow()
            val packageName = AndroidBinaryXmlPackageNameParser.parsePackageName(bytes).getOrThrow()

            assertEquals("os.kei.redirect", packageName)
            val paths = buildList {
                repeat(server.requestCount) {
                    add(server.takeRequest().path.orEmpty())
                }
            }
            assertEquals(1, paths.count { it.startsWith("/download/") })
            assertTrue(paths.drop(1).all { it.startsWith("/asset/") })
        }
    }

    private fun zipWithManifest(manifestBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifestBytes)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun rangeDispatcher(bytes: ByteArray): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range").orEmpty()
                val responseBytes = when {
                    range.startsWith("bytes=-") -> {
                        val count = range.removePrefix("bytes=-").toInt()
                        bytes.copyOfRange((bytes.size - count).coerceAtLeast(0), bytes.size)
                    }

                    range.startsWith("bytes=") -> {
                        val parts = range.removePrefix("bytes=").split('-', limit = 2)
                        val start = parts[0].toInt()
                        val end = parts[1].toInt().coerceAtMost(bytes.lastIndex)
                        bytes.copyOfRange(start, end + 1)
                    }

                    else -> bytes
                }
                val start = when {
                    range.startsWith("bytes=-") -> bytes.size - responseBytes.size
                    range.startsWith("bytes=") -> range.removePrefix("bytes=").substringBefore('-')
                        .toInt()

                    else -> 0
                }
                val end = start + responseBytes.size - 1
                return MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                    .setBody(okio.Buffer().write(responseBytes))
            }
        }
    }
}
