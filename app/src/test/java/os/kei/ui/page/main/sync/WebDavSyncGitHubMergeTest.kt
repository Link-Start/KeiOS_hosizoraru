package os.kei.ui.page.main.sync

import org.junit.Test
import os.kei.BuildConfig
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.defaultKeiOsTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebDavSyncGitHubMergeTest {
    @Test
    fun `merge updates existing item and preserves local app type`() {
        val existingSelfTrack =
            defaultKeiOsTrackedApp(packageName = BuildConfig.APPLICATION_ID).copy(
                localAppType = GitHubTrackedLocalAppType.System,
                preferPreRelease = false,
            )

        val merged =
            mergeGitHubTrackedItemsForSync(
                existingItems = listOf(existingSelfTrack),
                importedItems =
                    listOf(
                        existingSelfTrack.copy(
                            localAppType = GitHubTrackedLocalAppType.Unknown,
                            preferPreRelease = true,
                        ),
                    ),
            )
        val updated = merged.first { it.id == existingSelfTrack.id }

        assertTrue(updated.preferPreRelease)
        assertEquals(GitHubTrackedLocalAppType.System, updated.localAppType)
    }

    @Test
    fun `normalization keeps self track when list is empty`() {
        val normalized = normalizeGitHubTrackedItemsForSync(emptyList())

        assertEquals(1, normalized.size)
        assertEquals(
            defaultKeiOsTrackedApp(packageName = BuildConfig.APPLICATION_ID).id,
            normalized.single().id,
        )
    }
}
