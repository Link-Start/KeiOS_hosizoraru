package os.kei.ui.page.main.github.page

import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GitHubTrackScrollFocusTest {
    @Test
    fun `leading item count includes overview and visible transient cards`() {
        assertEquals(
            1,
            githubTrackedListLeadingItemCount(
                pendingTrackVisible = false,
                attachCandidateVisible = false,
                previewVisible = false,
                resultVisible = false
            )
        )
        assertEquals(
            3,
            githubTrackedListLeadingItemCount(
                pendingTrackVisible = true,
                attachCandidateVisible = false,
                previewVisible = false,
                resultVisible = true
            )
        )
    }

    @Test
    fun `tracked lazy index adds leading cards to sorted position`() {
        val items = listOf(
            tracked("owner", "first"),
            tracked("owner", "second"),
            tracked("owner", "third")
        )

        assertEquals(
            4,
            githubTrackedLazyListIndex(
                targetTrackId = items[1].id,
                sortedTrackIds = items.map { it.id },
                leadingItemCount = 3
            )
        )
    }

    @Test
    fun `tracked lazy index returns null for hidden or blank target`() {
        val items = listOf(tracked("owner", "first"))

        val itemIds = items.map { it.id }

        assertNull(githubTrackedLazyListIndex("", itemIds, leadingItemCount = 1))
        assertNull(githubTrackedLazyListIndex("missing", itemIds, leadingItemCount = 1))
    }

    private fun tracked(owner: String, repo: String): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://github.com/$owner/$repo",
            owner = owner,
            repo = repo,
            packageName = "app.$repo",
            appLabel = repo
        )
    }
}
