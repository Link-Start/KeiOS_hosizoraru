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
    fun `the latest and the newest pre-release are anchored to the reading lane`() {
        val rows =
            releaseRows("v2-rc2" to PRE, "v2-rc1" to PRE, "v1" to LATEST, "v0-9" to PLAIN)

        val anchors = githubReleaseAnchorIds(rows, firstPage = true)

        // The newest pre-release, not every pre-release: v2-rc1 is history like anything else.
        assertEquals(setOf(releaseId("v2-rc2"), releaseId("v1")), anchors)
    }

    @Test
    fun `the anchors are in the lane whether or not they are open`() {
        val rows = releaseRows("v2-rc1" to PRE, "v1" to LATEST, "v0-9" to PLAIN)

        // Nothing open at all -- which is the case this rule exists for: collapsing an anchor must not make
        // it look like any other row of the history.
        val lanes = githubReleaseLanesFor(rows, readingIds = githubReleaseAnchorIds(rows, firstPage = true))

        assertEquals(listOf("v2-rc1", "v1"), lanes.second.tags())
        assertEquals(listOf("v0-9"), lanes.first.tags())
    }

    @Test
    fun `nothing is anchored past the first page`() {
        // `latest` is flagged on the first page only, but pre-releases are flagged everywhere, and a
        // repository whose CI publishes one per push would otherwise anchor an arbitrary old build on
        // every page of its history.
        val rows = releaseRows("v0-8-rc1" to PRE, "v0-8" to PLAIN)

        assertTrue(githubReleaseAnchorIds(rows, firstPage = false).isEmpty())
    }

    @Test
    fun `a feed that cannot tell releases apart anchors nothing`() {
        // Atom mode: no entry carries either flag, and guessing would be worse than leaving the lane to
        // the page's own expansion default.
        val rows = releaseRows("v3" to PLAIN, "v2" to PLAIN, "v1" to PLAIN)

        assertTrue(githubReleaseAnchorIds(rows, firstPage = true).isEmpty())
    }

    @Test
    fun `an anchor and an opened release share the lane in the page's order`() {
        val rows = releaseRows("v3" to LATEST, "v2" to PLAIN, "v1" to PLAIN)

        val lanes =
            githubReleaseLanesFor(
                rows,
                readingIds = githubReleaseAnchorIds(rows, firstPage = true) + releaseId("v1"),
            )

        assertEquals(listOf("v3", "v1"), lanes.second.tags())
        assertEquals(listOf("v2"), lanes.first.tags())
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

private enum class ReleaseKind { PLAIN, LATEST, PRE }

private val PLAIN = ReleaseKind.PLAIN
private val LATEST = ReleaseKind.LATEST
private val PRE = ReleaseKind.PRE

private fun releaseRows(vararg tags: String): List<GitHubReleaseRow> =
    releaseRows(*tags.map { tag -> tag to PLAIN }.toTypedArray())

private fun releaseRows(vararg tagged: Pair<String, ReleaseKind>): List<GitHubReleaseRow> =
    tagged.map { (tag, kind) ->
        GitHubReleaseRow(
            entry =
                GitHubReleaseListEntry(
                    tagName = tag,
                    releaseName = tag,
                    htmlUrl = "https://github.com/owner/repo/releases/tag/$tag",
                    prerelease = kind == PRE,
                    latest = kind == LATEST,
                ),
        )
    }
