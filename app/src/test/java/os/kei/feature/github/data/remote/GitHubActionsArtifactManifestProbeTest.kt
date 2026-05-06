package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubLookupConfig
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals

class GitHubActionsArtifactManifestProbeTest {
    @Test
    fun `probe reads package name from apk nested in actions artifact zip`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.actions")
        val apkBytes = zipWithManifest(manifest)
        val artifactBytes = zipWithStoredEntry(
            entryName = "outputs/app-universal-release.apk",
            bytes = apkBytes
        )
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(artifactBytes)
            val probe = GitHubActionsArtifactManifestProbe(
                manifestReader = GitHubApkManifestReader(
                    zipEntryReader = RemoteZipEntryReader(client = OkHttpClient())
                )
            )

            val packageName = probe.readPackageName(
                artifact = GitHubActionsArtifact(
                    id = 42L,
                    name = "KeiOS build",
                    sizeBytes = artifactBytes.size.toLong()
                ),
                resolvedDownloadUrl = server.url("/download/artifact.zip").toString(),
                lookupConfig = GitHubLookupConfig()
            ).getOrThrow()

            assertEquals("os.kei.actions", packageName)
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

    private fun zipWithStoredEntry(
        entryName: String,
        bytes: ByteArray
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val crc = CRC32().apply { update(bytes) }.value
            val entry = ZipEntry(entryName).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                this.crc = crc
            }
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun rangeDispatcher(bytes: ByteArray): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range").orEmpty()
                val responseBytes = when {
                    range.startsWith("bytes=") -> {
                        val parts = range.removePrefix("bytes=").split('-', limit = 2)
                        val start = parts[0].toInt()
                        val end = parts[1].toInt().coerceAtMost(bytes.lastIndex)
                        bytes.copyOfRange(start, end + 1)
                    }

                    else -> bytes
                }
                val start = if (range.startsWith("bytes=")) {
                    range.removePrefix("bytes=").substringBefore('-').toInt()
                } else {
                    0
                }
                val end = start + responseBytes.size - 1
                return MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                    .setBody(Buffer().write(responseBytes))
            }
        }
    }
}
