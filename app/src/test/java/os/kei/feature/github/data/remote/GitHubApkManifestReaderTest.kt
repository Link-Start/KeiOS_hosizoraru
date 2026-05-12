package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.apk.BinaryResourceTableFixture
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.rangeDispatcher
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubApkManifestReaderTest {
    @Test
    fun `inspect reuses one zip directory for manifest and entry metadata`() {
        val apkBytes = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(
                packageName = "os.kei.inspect",
                versionName = "2.4.0",
                versionCode = 20400L
            )
        )
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(apkBytes)
            val reader = GitHubApkManifestReader(
                zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
            )

            val info = reader.inspect(
                asset = GitHubReleaseAssetFile(
                    name = "inspect.apk",
                    downloadUrl = server.url("/download/inspect.apk").toString(),
                    sizeBytes = apkBytes.size.toLong(),
                    downloadCount = 1
                ),
                lookupConfig = GitHubLookupConfig()
            ).getOrThrow()

            assertEquals("os.kei.inspect", info.packageName)
            assertEquals("2.4.0", info.versionName)
            assertEquals("20400", info.versionCode)
            assertEquals(listOf("arm64-v8a"), info.nativeAbis)
            assertTrue(server.requestCount <= 4)
        }
    }

    @Test
    fun `inspect resolves application label from resources table`() {
        val labelResourceId = 0x7F010000
        val apkBytes = apkWithManifestNativeLibAndResources(
            manifestBytes = BinaryManifestFixture.buildWithApplicationLabelResource(
                packageName = "os.kei.inspect.label",
                labelResourceId = labelResourceId
            ),
            resourcesBytes = BinaryResourceTableFixture.stringResourceTable(
                resourceId = labelResourceId,
                key = "app_name",
                value = "KeiOS Label"
            )
        )
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(apkBytes)
            val reader = GitHubApkManifestReader(
                zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
            )

            val info = reader.inspect(
                asset = GitHubReleaseAssetFile(
                    name = "inspect-label.apk",
                    downloadUrl = server.url("/download/inspect-label.apk").toString(),
                    sizeBytes = apkBytes.size.toLong(),
                    downloadCount = 1
                ),
                lookupConfig = GitHubLookupConfig()
            ).getOrThrow()

            assertEquals("os.kei.inspect.label", info.packageName)
            assertEquals("KeiOS Label", info.appLabel)
        }
    }

    @Test
    fun `repository reuses in-flight manifest inspect for identical asset`() {
        val apkBytes = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(
                packageName = "os.kei.inspect.dedupe",
                versionName = "3.1.0",
                versionCode = 30100L
            )
        )
        MockWebServer().use { server ->
            val baseDispatcher = rangeDispatcher(apkBytes)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    Thread.sleep(50L)
                    return baseDispatcher.dispatch(request)
                }
            }
            val repository = GitHubApkInfoRepository(
                manifestReader = GitHubApkManifestReader(
                    zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
                )
            )
            val asset = GitHubReleaseAssetFile(
                name = "inspect-dedupe.apk",
                downloadUrl = server.url("/download/inspect-dedupe.apk").toString(),
                sizeBytes = apkBytes.size.toLong(),
                downloadCount = 1
            )
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            val futures = List(2) {
                executor.submit<GitHubApkManifestInfo> {
                    assertTrue(start.await(2, TimeUnit.SECONDS))
                    repository.inspect(asset = asset, lookupConfig = GitHubLookupConfig())
                        .getOrThrow()
                }
            }
            start.countDown()

            val results = futures.map { future -> future.get(5, TimeUnit.SECONDS) }
            executor.shutdownNow()

            assertEquals(
                listOf("os.kei.inspect.dedupe", "os.kei.inspect.dedupe"),
                results.map { it.packageName })
            assertEquals(listOf("3.1.0", "3.1.0"), results.map { it.versionName })
            assertTrue(server.requestCount <= 4)
        }
    }

    private fun apkWithManifestAndNativeLib(manifestBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libfixture.so"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun apkWithManifestNativeLibAndResources(
        manifestBytes: ByteArray,
        resourcesBytes: ByteArray
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifestBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("resources.arsc"))
            zip.write(resourcesBytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lib/arm64-v8a/libfixture.so"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
