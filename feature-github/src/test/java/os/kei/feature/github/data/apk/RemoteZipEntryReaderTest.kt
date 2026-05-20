package os.kei.feature.github.data.apk

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.rangeDispatcher
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithManifest
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithStoredEntry
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteZipEntryReaderTest {
    @Test
    fun `selected entries compare byte arrays by content`() {
        val left = RemoteZipSelectedEntries(
            entryNames = listOf("AndroidManifest.xml"),
            entries = mapOf("AndroidManifest.xml" to byteArrayOf(1, 2, 3))
        )
        val right = RemoteZipSelectedEntries(
            entryNames = listOf("AndroidManifest.xml"),
            entries = mapOf("AndroidManifest.xml" to byteArrayOf(1, 2, 3))
        )

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

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
                    val request = server.takeRequest()
                    assertEquals("no-store", request.getHeader("Cache-Control"))
                    assertEquals("no-cache", request.getHeader("Pragma"))
                    add(request.getHeader("Range").orEmpty())
                }
            }
            assertEquals("bytes=0-0", ranges.first())
            assertTrue(ranges.none { it.startsWith("bytes=-") })
            assertTrue(ranges.size <= 4)
            assertTrue(ranges.any { it.startsWith("bytes=0-") || it.matches(Regex("""bytes=\d+-\d+""")) })
        }
    }

    @Test
    fun `reader lists entries and reads selected entries from one central directory`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.batch")
        val apkBytes = zipWithManifest(manifest)
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(apkBytes)
            val reader = RemoteZipEntryReader(client = OkHttpClient())

            val selected = reader.readSelectedEntries(
                url = server.url("/download/app.apk").toString(),
                selectEntryNames = { entryNames ->
                    entryNames.filter { it == "AndroidManifest.xml" }
                }
            ).getOrThrow()
            val packageName = AndroidBinaryXmlPackageNameParser
                .parsePackageName(selected.entries.getValue("AndroidManifest.xml"))
                .getOrThrow()

            assertEquals("os.kei.batch", packageName)
            assertEquals(listOf("AndroidManifest.xml"), selected.entryNames)
            assertTrue(server.requestCount <= 4)
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

    @Test
    fun `reader fetches manifest from nested stored apk entry through ranges`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.nested")
        val apkBytes = zipWithManifest(manifest)
        val artifactBytes = zipWithStoredEntry(
            entryName = "outputs/app-universal-release.apk",
            bytes = apkBytes
        )
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(artifactBytes)
            val reader = RemoteZipEntryReader(client = OkHttpClient())

            val bytes = reader.readNestedStoredZipEntry(
                url = server.url("/download/artifact.zip").toString(),
                outerEntryName = "outputs/app-universal-release.apk",
                innerEntryName = "AndroidManifest.xml"
            ).getOrThrow()
            val packageName = AndroidBinaryXmlPackageNameParser.parsePackageName(bytes).getOrThrow()

            assertEquals("os.kei.nested", packageName)
            val ranges = buildList {
                repeat(server.requestCount) {
                    add(server.takeRequest().getHeader("Range").orEmpty())
                }
            }
            assertEquals("bytes=0-0", ranges.first())
            assertTrue(ranges.none { it.startsWith("bytes=-") })
            assertTrue(ranges.all { it.matches(Regex("""bytes=\d+-\d+""")) })
        }
    }

}
