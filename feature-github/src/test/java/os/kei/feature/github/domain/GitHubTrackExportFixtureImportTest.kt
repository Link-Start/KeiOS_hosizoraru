package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubTrackedSourceMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A real v2 export from a device (2026-05-13): every item imports, none is dropped as invalid or duplicate. */
class GitHubTrackExportFixtureImportTest {
    @Test
    fun `exported tracks json imports all array items as valid tracked apps`() {
        val payload = GitHubTrackStore.parseTrackedItemsImport(GitHubTrackExportFixture.rawJson)
        val expectedCount = GitHubTrackExportFixture.expectedItemCount

        assertEquals(expectedCount, payload.sourceCount)
        assertEquals(expectedCount, payload.items.size)
        assertEquals(
            GitHubTrackExportFixture.expectedGitHubRepositoryCount,
            payload.items.count { it.sourceMode == GitHubTrackedSourceMode.GitHubRepository }
        )
        assertEquals(
            GitHubTrackExportFixture.expectedDirectApkCount,
            payload.items.count { it.sourceMode == GitHubTrackedSourceMode.DirectApk }
        )
        assertEquals(0, payload.invalidCount)
        assertEquals(0, payload.duplicateCount)
        assertNotNull(payload.items.firstOrNull { item ->
            item.owner == "LibChecker" &&
                    item.repo == "LibChecker" &&
                    item.packageName == "com.absinthe.libchecker"
        })
        assertNotNull(payload.items.firstOrNull { item ->
            item.owner == "vvb2060" &&
                    item.repo == "PackageInstaller" &&
                    item.packageName == "io.github.vvb2060.packageinstaller"
        })
        assertNotNull(payload.items.firstOrNull { item ->
            item.sourceMode == GitHubTrackedSourceMode.DirectApk &&
                    item.repoUrl == "https://telegram.org/dl/android/apk-public-beta" &&
                    item.packageName == "org.telegram.messenger.beta"
        })
    }
}
