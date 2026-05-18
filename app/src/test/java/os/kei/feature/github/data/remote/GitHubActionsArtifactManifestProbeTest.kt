package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.rangeDispatcher
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithManifest
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithStoredEntry
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubLookupConfig
import kotlinx.coroutines.runBlocking
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

            val packageName = runBlocking {
                probe.readPackageName(
                    artifact = GitHubActionsArtifact(
                        id = 42L,
                        name = "KeiOS build",
                        sizeBytes = artifactBytes.size.toLong()
                    ),
                    resolvedDownloadUrl = server.url("/download/artifact.zip").toString(),
                    lookupConfig = GitHubLookupConfig()
                ).getOrThrow()
            }

            assertEquals("os.kei.actions", packageName)
        }
    }

}
