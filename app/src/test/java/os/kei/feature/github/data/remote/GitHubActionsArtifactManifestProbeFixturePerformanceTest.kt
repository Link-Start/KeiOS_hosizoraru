package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.apk.RemoteZipEntryReader
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.rangeDispatcher
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithManifest
import os.kei.feature.github.data.apk.ZipRangeTestFixtures.zipWithStoredEntries
import os.kei.feature.github.domain.GitHubTrackExportFixture
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubActionsArtifactManifestProbeFixturePerformanceTest {
    @Test
    fun `probe keeps nested apk manifest scan bounded for exported 31-track fixture`() {
        val items = GitHubTrackExportFixture.trackedItems
        val selectedItem = items.first()
        val artifactBytes = zipWithStoredEntries(
            items.mapIndexed { index, item ->
                artifactEntryName(index, item, selectedItem) to zipWithManifest(
                    BinaryManifestFixture.build(item.packageName)
                )
            }
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
                    id = 31L,
                    name = "fixture-31-track-artifact",
                    sizeBytes = artifactBytes.size.toLong()
                ),
                resolvedDownloadUrl = server.url("/download/artifact.zip").toString(),
                lookupConfig = GitHubLookupConfig()
            ).getOrThrow()

            assertEquals(31, items.size)
            assertEquals(selectedItem.packageName, packageName)
            val ranges = buildList {
                repeat(server.requestCount) {
                    add(server.takeRequest().getHeader("Range").orEmpty())
                }
            }
            assertEquals("bytes=0-0", ranges.first())
            assertTrue(ranges.all { it.matches(Regex("""bytes=\d+-\d+""")) })
            assertTrue(
                actual = ranges.size <= 12,
                message = "Expected a bounded range-only scan, got ${ranges.size} requests"
            )
        }
    }

    private fun artifactEntryName(
        index: Int,
        item: GitHubTrackedApp,
        selectedItem: GitHubTrackedApp
    ): String {
        val prefix = index.toString().padStart(2, '0')
        val repoName = item.repo.replace(Regex("""[^A-Za-z0-9_.-]+"""), "-")
        val variant = if (item.id == selectedItem.id) {
            "universal-release"
        } else {
            "arm64-debug"
        }
        return "outputs/$prefix-$repoName-$variant.apk"
    }
}
