package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubStarImportMergerTest {
    @Test
    fun `merge appends new imported tracks`() {
        val imported = track(repo = "fresh", packageName = "demo.fresh")

        val merge = GitHubStarImportMerger.merge(
            existingItems = listOf(track(repo = "existing", packageName = "demo.existing")),
            importedItems = listOf(imported)
        )

        assertEquals(2, merge.items.size)
        assertEquals(1, merge.result.addedCount)
        assertEquals(0, merge.result.updatedCount)
        assertEquals(setOf(imported.id), merge.result.affectedTrackIds)
        assertEquals(setOf("demo.fresh"), merge.result.affectedPackages)
    }

    @Test
    fun `merge updates exact id and preserves unchanged count`() {
        val unchanged = track(repo = "same", packageName = "demo.same")
        val before = track(repo = "changed", packageName = "demo.changed", appLabel = "Old")
        val after = before.copy(appLabel = "New")

        val merge = GitHubStarImportMerger.merge(
            existingItems = listOf(unchanged, before),
            importedItems = listOf(unchanged, after)
        )

        assertEquals(listOf(unchanged, after), merge.items)
        assertEquals(0, merge.result.addedCount)
        assertEquals(1, merge.result.updatedCount)
        assertEquals(1, merge.result.unchangedCount)
        assertEquals(setOf(after.id), merge.result.affectedTrackIds)
        assertTrue(merge.result.removedTrackIds.isEmpty())
    }

    @Test
    fun `verified package replaces repo only track by owner repo fallback`() {
        val repoOnly = track(repo = "linked", packageName = "")
        val verified = track(repo = "linked", packageName = "demo.linked")

        val merge = GitHubStarImportMerger.merge(
            existingItems = listOf(repoOnly),
            importedItems = listOf(verified)
        )

        assertEquals(listOf(verified), merge.items)
        assertEquals(0, merge.result.addedCount)
        assertEquals(1, merge.result.updatedCount)
        assertEquals(setOf(repoOnly.id), merge.result.removedTrackIds)
        assertEquals(setOf(verified.id), merge.result.affectedTrackIds)
        assertEquals(setOf("demo.linked"), merge.result.affectedPackages)
    }

    @Test
    fun `blank package import keeps repo only track unchanged`() {
        val repoOnly = track(repo = "repo-only", packageName = "")

        val merge = GitHubStarImportMerger.merge(
            existingItems = listOf(repoOnly),
            importedItems = listOf(repoOnly)
        )

        assertEquals(listOf(repoOnly), merge.items)
        assertEquals(1, merge.result.unchangedCount)
        assertTrue(merge.result.affectedTrackIds.isEmpty())
        assertTrue(merge.result.affectedPackages.isEmpty())
    }

    private fun track(
        repo: String,
        packageName: String,
        appLabel: String = repo
    ): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://github.com/demo/$repo",
            owner = "demo",
            repo = repo,
            packageName = packageName,
            appLabel = appLabel
        )
    }
}
