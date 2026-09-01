package os.kei.ui.page.main.github.history

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GitHubHistoryLaneSourceTest {
    @Test
    fun theSplitIsBehindTheSharedColumnGateAndTheSharedLaneRule() {
        val source = sourceFile(HISTORY_PAGE_SOURCE)

        assertTrue("val pageColumnCount = appPageColumnCount()" in source)
        assertTrue("val wideLayout = columnCount >= 2" in source)
        assertTrue(
            "contentMaxWidth = appPageContentMaxWidthFor(columnCount)," in source,
            "Two lanes need the doubled cap, or they divide a single column's width",
        )
        assertEquals(
            4,
            Regex("appPageAlternatingLanes\\(").findAll(source).count(),
            "One lane split per history mode, all through the shared rule",
        )
        assertTrue(
            "withIndex().toList(), columnCount)" in source,
            "A lane no longer knows a record's place in the whole list, and the entrance cascade does",
        )
    }

    @Test
    fun aStatusOnlyStateStaysOnOneLane() {
        val source = sourceFile(HISTORY_PAGE_SOURCE)

        // Loading, error and both empty states are a single card with no records behind them. Split, that
        // card sits in the left half with an empty right half beside it.
        assertTrue(
            "if (uiState.loading || uiState.errorMessage.isNotBlank() || currentDisplayRecordCount == 0)" in
                source,
        )
        // And the cards that do show are emitted once, in the leading lane, rather than mirrored.
        assertEquals(
            3,
            Regex("if \\(lane == 0\\) \\{").findAll(source).count(),
            "Loading, error and empty each gate on the leading lane",
        )
        assertTrue("if (currentDisplayRecordCount == 0 && lane == 0) {" in source)
    }

    @Test
    fun everythingThatWatchesScrollGoesThroughTheSharedTarget() {
        val source = sourceFile(HISTORY_PAGE_SOURCE)

        // The chrome reads the leading lane and scroll-to-top moves both — the page states that choice
        // once, in the target, instead of at each of the four call sites that used to take `listState`.
        assertTrue("rememberAppPageScrollTarget(listState, secondaryListState, wideLayout)" in source)
        assertTrue("activeListStateProvider = { scrollTarget.scrollableState }," in source)
        assertEquals(
            3,
            Regex("scrollTarget\\.scrollToTop\\(\\)").findAll(source).count(),
            "Category switch, search, and the title tap all scroll both lanes",
        )
        assertTrue(
            "listState.animateScrollToItem(0)" !in source && "listState.scrollToItem(0)" !in source,
            "No call site may still scroll the leading lane alone",
        )
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

private const val HISTORY_PAGE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/github/history/GitHubActionsNotificationHistoryPage.kt"
