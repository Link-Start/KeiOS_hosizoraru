package os.kei.ui.page.main.github.release

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.data.remote.GitHubReleaseListEntry

class GitHubReleaseLanesTest {
    @Test
    fun `the reading lane starts empty, so the history keeps its full height`() {
        val rows = releaseRows("v3", "v2", "v1")

        val lanes = githubReleaseLanesFor(rows, readingIds = emptySet())

        assertEquals(listOf("v3", "v2", "v1"), lanes.first.tags())
        assertTrue(lanes.second.isEmpty())
    }

    @Test
    fun `an opened release leaves the history rather than expanding inside it`() {
        val rows = releaseRows("v3", "v2", "v1")

        val lanes = githubReleaseLanesFor(rows, readingIds = setOf(releaseId("v2")))

        // The list you were scanning loses one row instead of most of its height, which is the whole point:
        // picking a version off a list is a comparison, and a comparison needs both things visible.
        assertEquals(listOf("v3", "v1"), lanes.first.tags())
        assertEquals(listOf("v2"), lanes.second.tags())
    }

    @Test
    fun `both lanes stay in the page's order, because a history in touch order is not a history`() {
        val rows = releaseRows("v5", "v4", "v3", "v2", "v1")

        // Opened newest-last, deliberately out of the list's order.
        val lanes =
            githubReleaseLanesFor(
                rows,
                readingIds = linkedSetOf(releaseId("v2"), releaseId("v5"), releaseId("v3")),
            )

        assertEquals(listOf("v5", "v3", "v2"), lanes.second.tags())
        assertEquals(listOf("v4", "v1"), lanes.first.tags())
    }

    @Test
    fun `every release is in exactly one lane, and an id from another page invents none`() {
        val rows = releaseRows("v3", "v2", "v1")

        val lanes =
            githubReleaseLanesFor(
                rows,
                // Left over from a page the reader has since moved off.
                readingIds = setOf(releaseId("v1"), releaseId("v99")),
            )

        assertEquals(listOf("v1", "v2", "v3"), (lanes.first + lanes.second).tags().sorted())
        assertEquals(3, lanes.first.size + lanes.second.size)
    }

    @Test
    fun `the flat position survives the split, so the first release stays the first`() {
        val rows = releaseRows("v3", "v2", "v1")

        val lanes = githubReleaseLanesFor(rows, readingIds = setOf(releaseId("v3")))

        // A lane on its own no longer knows where its rows sat, and the instrumentation tag on the first
        // release of the page has to survive that release moving lanes.
        assertEquals(0, lanes.second.first().index)
        assertEquals(listOf(1, 2), lanes.first.map { row -> row.index })
    }

    @Test
    fun `an empty page of results lanes to nothing at all`() {
        val lanes = githubReleaseLanesFor(emptyList(), readingIds = setOf(releaseId("v1")))

        assertTrue(lanes.first.isEmpty())
        assertTrue(lanes.second.isEmpty())
    }
}

/** The id an entry derives from its coordinates, which is what the lane rule keys on. */
private fun releaseId(tag: String): String = "$tag|https://github.com/owner/repo/releases/tag/$tag"

private fun List<IndexedValue<GitHubReleaseRow>>.tags(): List<String> =
    map { row -> row.value.entry.tagName }

private fun releaseRows(vararg tags: String): List<GitHubReleaseRow> =
    tags.map { tag ->
        GitHubReleaseRow(
            entry =
                GitHubReleaseListEntry(
                    tagName = tag,
                    releaseName = tag,
                    htmlUrl = "https://github.com/owner/repo/releases/tag/$tag",
                    prerelease = false,
                ),
        )
    }
