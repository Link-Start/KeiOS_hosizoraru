package os.kei.ui.page.main.github.fdroid

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.model.GitHubReleaseChannel

class FdroidVersionLanesTest {
    @Test
    fun `the reading lane starts empty, so the history keeps its full height`() {
        val rows = versionRows(30L, 20L, 10L)

        val lanes = fdroidVersionLanesFor(rows, readingIds = emptySet())

        assertEquals(listOf(30L, 20L, 10L), lanes.first.codes())
        assertTrue(lanes.second.isEmpty())
    }

    @Test
    fun `an opened build leaves the history rather than expanding inside it`() {
        val rows = versionRows(30L, 20L, 10L)

        val lanes = fdroidVersionLanesFor(rows, readingIds = setOf(rows.idOf(20L)))

        // The history you were scanning loses one row instead of most of its height. Going back through
        // F-Droid builds is a comparison, and a comparison needs both things visible.
        assertEquals(listOf(30L, 10L), lanes.first.codes())
        assertEquals(listOf(20L), lanes.second.codes())
    }

    @Test
    fun `both lanes stay in version-code order, because a history in touch order is not a history`() {
        val rows = versionRows(50L, 40L, 30L, 20L, 10L)

        // Opened newest-last, deliberately out of the history's own order.
        val lanes =
            fdroidVersionLanesFor(
                rows,
                readingIds = linkedSetOf(rows.idOf(20L), rows.idOf(50L), rows.idOf(30L)),
            )

        assertEquals(listOf(50L, 30L, 20L), lanes.second.codes())
        assertEquals(listOf(40L, 10L), lanes.first.codes())
    }

    @Test
    fun `every build is in exactly one lane, and an id from a narrowed list invents none`() {
        val rows = versionRows(30L, 20L, 10L)

        val lanes =
            fdroidVersionLanesFor(
                rows,
                // Left over from before the reader typed into the filter field.
                readingIds = setOf(rows.idOf(10L), "999|gone.apk|"),
            )

        assertEquals(listOf(10L, 20L, 30L), (lanes.first + lanes.second).codes().sorted())
        assertEquals(3, lanes.first.size + lanes.second.size)
    }

    @Test
    fun `the recommended build and the newest pre-release are the two anchors`() {
        val rows =
            listOf(
                versionRow(50L, channel = GitHubReleaseChannel.BETA),
                versionRow(40L, channel = GitHubReleaseChannel.RC),
                versionRow(30L, recommended = true),
                versionRow(20L),
            )

        val anchors = fdroidVersionAnchorIds(rows)

        // 50 is the newest pre-release and 30 is what the track would install. 40 is a pre-release too
        // but not the newest one, so it stays in the history.
        assertEquals(setOf(rows.idOf(50L), rows.idOf(30L)), anchors)
    }

    @Test
    fun `a recommended build that is itself the newest pre-release anchors once`() {
        val rows =
            listOf(
                versionRow(50L, channel = GitHubReleaseChannel.BETA, recommended = true),
                versionRow(40L),
            )

        assertEquals(setOf(rows.idOf(50L)), fdroidVersionAnchorIds(rows))
    }

    @Test
    fun `two rebuilds of one version code under one APK name are distinct rows`() {
        // Which is why the recommended badge is matched on the row id rather than on version code plus
        // name: these two are separate rows, and comparing anything coarser would badge both.
        val first = versionRow(20L)
        val second =
            FdroidVersionRow(
                version = fdroidVersion(20L, apkSha256 = "rebuilt"),
                channel = GitHubReleaseChannel.STABLE,
                recommended = false,
                installed = false,
                compatible = true,
                downloadUrl = "",
            )

        assertTrue(first.id != second.id, "a rebuild is a different file: ${first.id}")
    }

    @Test
    fun `a history with no pre-release and no candidate anchors nothing`() {
        // Both are real states: a repository can publish only stable builds, and a track whose
        // anti-feature policy rejects every build has no candidate at all.
        val rows = listOf(versionRow(20L), versionRow(10L))

        assertTrue(fdroidVersionAnchorIds(rows).isEmpty())
    }

    @Test
    fun `narrowing the history past the recommended build anchors nothing rather than the top row`() {
        val rows = listOf(versionRow(50L), versionRow(40L))

        // What `publishRows` hands the page after a filter excludes the candidate. Anchoring the new top
        // row would tell the reader it is "the one you would install", which it is not.
        assertTrue(fdroidVersionAnchorIds(rows).isEmpty())
    }
}

private fun versionRows(vararg codes: Long): List<FdroidVersionRow> = codes.map { code -> versionRow(code) }

private fun versionRow(
    versionCode: Long,
    channel: GitHubReleaseChannel = GitHubReleaseChannel.STABLE,
    recommended: Boolean = false,
): FdroidVersionRow =
    FdroidVersionRow(
        version = fdroidVersion(versionCode = versionCode),
        channel = channel,
        recommended = recommended,
        installed = false,
        compatible = true,
        downloadUrl = "https://example.org/repo/app_$versionCode.apk",
    )

private fun List<FdroidVersionRow>.idOf(versionCode: Long): String =
    first { row -> row.version.versionCode == versionCode }.id

private fun List<IndexedValue<FdroidVersionRow>>.codes(): List<Long> =
    map { indexed -> indexed.value.version.versionCode }
