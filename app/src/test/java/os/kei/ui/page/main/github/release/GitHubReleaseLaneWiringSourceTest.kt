package os.kei.ui.page.main.github.release

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GitHubReleaseLaneWiringSourceTest {
    @Test
    fun theNoticesAreSpokenByTheBrowsingLaneOnly() {
        val source = sourceFile(PAGE_SOURCE)

        // "Loading", "empty", "past the end" describe the list, so exactly one lane may say them: the
        // reading lane renders cards and nothing else.
        val reading =
            source.substringAfter("val readingLane: LazyListScope.() -> Unit = {")
                .substringBefore("val listInnerPadding")
        assertTrue("releaseCards(" in reading)
        assertTrue("rows = lanes.second," in reading)
        assertTrue("releaseListBody(" !in reading, "The notices belong to the browsing lane")

        val browsing =
            source.substringAfter("val browsingLane: LazyListScope.() -> Unit = {")
                .substringBefore("val readingLane:")
        assertTrue("releaseListBody(" in browsing)
        assertTrue("rows = lanes.first," in browsing)
    }

    @Test
    fun oneColumnNeverConsultsTheLaneRule() {
        val source = sourceFile(PAGE_SOURCE)

        // With no second lane to move a release to, opening one has to keep working where it already is.
        assertTrue("if (columnCount >= 2) {" in source)
        assertTrue(
            "GitHubReleaseLanes(\n                                    first = uiState.rows.withIndex().toList()," in source,
        )
        assertTrue("content = browsingLane," in source, "One column renders the browsing lane alone")
    }

    @Test
    fun aListWithNothingInItDropsBackToOneColumn() {
        val source = sourceFile(PAGE_SOURCE)

        assertTrue(
            "val columnCount = if (uiState.rows.isEmpty()) 1 else appPageColumnCount()" in source,
            "A notice about the whole list must not sit in a half-width lane",
        )
        assertTrue("contentMaxWidth = appPageContentMaxWidthFor(columnCount)," in source)
    }

    @Test
    fun aClosedReleaseKeepsItsLaneAndPagingEmptiesIt() {
        val source = sourceFile(PAGE_SOURCE)

        // Recorded when the release opens, so the close that follows cannot fling it back across the page.
        val toggle =
            source.substringAfter("val onToggleRelease: (String, Boolean) -> Unit = { id, open ->")
                .substringBefore("val onShareAsset")
        assertTrue("keptInReadingLane[id] = Unit" in toggle)
        assertTrue(
            "keptInReadingLane.remove" !in toggle,
            "Closing a release must leave it in the lane it was read in",
        )
        // And the hold is scoped to the page of results, not to the visit: page forward and none of those
        // releases are on screen any more.
        assertTrue("keptInReadingLane.clear()" in source)
        assertEquals(1, Regex(Regex.escape("keptInReadingLane.clear()")).findAll(source).count())

        val lanes = source.substringAfter("val readingIds =").substringBefore("\n")
        assertTrue(
            "openReleases.keys + keptInReadingLane.keys" in lanes,
            "Anything open is being read, however it came to be open -- including the seeded expansions",
        )
    }

    @Test
    fun bothLanesScrollOnTheirOwn() {
        val source = sourceFile(PAGE_SOURCE)

        assertTrue("val secondaryListState = rememberLazyListState()" in source)
        assertTrue("primaryState = listState," in source)
        assertTrue("secondaryState = secondaryListState," in source)
    }
}

private fun sourceFile(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val sourceFile =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(sourceFile) {
        "Unable to locate $relativePath from $workingDirectory"
    }.readText()
}

private const val PAGE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/github/release/GitHubReleaseListPage.kt"
