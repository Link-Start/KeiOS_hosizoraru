package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.rangeDispatcher
import os.kei.feature.github.model.GitHubLookupConfig
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubApkManifestReaderTest {
    @Test
    fun `inspect reuses one zip directory for manifest and entry metadata`() {
        val apkBytes = apkWithManifestAndNativeLib(
            manifestBytes = BinaryManifestFixture.build(packageName = "os.kei.inspect")
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
            assertEquals(listOf("arm64-v8a"), info.nativeAbis)
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
}
