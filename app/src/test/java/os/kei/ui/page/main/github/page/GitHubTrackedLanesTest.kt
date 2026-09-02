package os.kei.ui.page.main.github.page

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp

class GitHubTrackedLanesTest {
    @Test
    fun `the reading lane starts empty, so the list keeps its full height`() {
        val tracked = trackedApps("a", "b", "c")

        val lanes = githubTrackedLanesFor(tracked, pinnedIds = emptyList(), detainedIds = emptySet())

        assertEquals(listOf("a", "b", "c"), lanes.first.repoNames())
        assertTrue(lanes.second.isEmpty())
    }

    @Test
    fun `an opened card leaves the browsing lane rather than expanding inside it`() {
        val tracked = trackedApps("a", "b", "c")

        val lanes =
            githubTrackedLanesFor(
                tracked,
                pinnedIds = emptyList(),
                detainedIds = setOf(trackId("b")),
            )

        // The whole point: the list you were scanning loses one row instead of most of its height.
        assertEquals(listOf("a", "c"), lanes.first.repoNames())
        assertEquals(listOf("b"), lanes.second.repoNames())
    }

    @Test
    fun `pins outrank cards merely opened, and keep the order they were pinned in`() {
        val tracked = trackedApps("a", "b", "c", "d")

        val lanes =
            githubTrackedLanesFor(
                tracked,
                // c pinned most recently, which is the order the action prepends in.
                pinnedIds = listOf(trackId("c"), trackId("a")),
                detainedIds = setOf(trackId("b")),
            )

        assertEquals(listOf("c", "a", "b"), lanes.second.repoNames())
        assertEquals(listOf("d"), lanes.first.repoNames())
    }

    @Test
    fun `everything opened this visit stays in the list's own order`() {
        val tracked = trackedApps("a", "b", "c", "d", "e")

        // Detained in a different order than the list: the lane must not become a pile in touch order.
        val lanes =
            githubTrackedLanesFor(
                tracked,
                pinnedIds = emptyList(),
                detainedIds = linkedSetOf(trackId("d"), trackId("b"), trackId("e")),
            )

        assertEquals(listOf("b", "d", "e"), lanes.second.repoNames())
        assertEquals(listOf("a", "c"), lanes.first.repoNames())
    }

    @Test
    fun `every card is in exactly one lane, and a stale id invents none`() {
        val tracked = trackedApps("a", "b", "c", "d")

        val lanes =
            githubTrackedLanesFor(
                tracked,
                pinnedIds = listOf(trackId("a")),
                // A pin or a hold left over from a track that has since been deleted.
                detainedIds = setOf(trackId("c"), trackId("gone")),
            )

        assertEquals(listOf("a", "b", "c", "d"), (lanes.first + lanes.second).repoNames().sorted())
        assertEquals(4, lanes.first.size + lanes.second.size)
    }

    @Test
    fun `a pin reaches the top of a single column, ahead of the sort`() {
        // `sortedTracked` arrives already sorted and already partitioned with archived tracks last, so
        // "ahead of the sort" means ahead of that whole arrangement.
        val tracked = trackedApps("newest", "older", "archived")

        val ordered = githubTrackedSortedWithPinsFirst(tracked, pinnedIds = listOf(trackId("archived")))

        assertEquals(listOf("archived", "newest", "older"), ordered.repoNames())
    }

    @Test
    fun `two pins keep their pin order at the top`() {
        val tracked = trackedApps("a", "b", "c")

        val ordered =
            githubTrackedSortedWithPinsFirst(
                tracked,
                pinnedIds = listOf(trackId("c"), trackId("a")),
            )

        assertEquals(listOf("c", "a", "b"), ordered.repoNames())
    }

    @Test
    fun `no pins changes nothing at all`() {
        val tracked = trackedApps("a", "b", "c")

        assertEquals(tracked, githubTrackedSortedWithPinsFirst(tracked, pinnedIds = emptyList()))
        // A pin for a track that is gone is also a no-op, not a reshuffle.
        assertEquals(tracked, githubTrackedSortedWithPinsFirst(tracked, pinnedIds = listOf(trackId("gone"))))
    }

    @Test
    fun `pinned ids survive a round trip through the store's encoding`() {
        val ids = listOf("owner/repo|pkg.one", "owner/other|pkg.two")

        assertEquals(ids, decodeGitHubPinnedTrackIds(encodeGitHubPinnedTrackIds(ids)))
        // Blank entries and duplicates are dropped rather than becoming phantom pins.
        assertEquals(listOf("a", "b"), decodeGitHubPinnedTrackIds("a\n\n b \na"))
        assertTrue(decodeGitHubPinnedTrackIds("").isEmpty())
        assertTrue(decodeGitHubPinnedTrackIds("   ").isEmpty())
    }
}

/** The id a tracked app derives from its coordinates, which is what the lane rules key on. */
private fun trackId(name: String): String = "owner/$name|pkg.$name"

/** Asserting on repo names rather than ids keeps the cases readable. */
private fun List<GitHubTrackedApp>.repoNames(): List<String> = map { item -> item.repo }

private fun trackedApps(vararg names: String): List<GitHubTrackedApp> =
    names.map { name ->
        GitHubTrackedApp(
            repoUrl = "https://github.com/owner/$name",
            owner = "owner",
            repo = name,
            packageName = "pkg.$name",
            appLabel = name,
        )
    }
