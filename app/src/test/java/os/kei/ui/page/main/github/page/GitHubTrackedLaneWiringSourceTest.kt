package os.kei.ui.page.main.github.page

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GitHubTrackedLaneWiringSourceTest {
    @Test
    fun theListStatusIsSpokenByTheBrowsingLaneOnly() {
        val source = sourceFile(MAIN_CONTENT_SOURCE)

        // "Nothing tracked" and "nothing matched" describe the list, so exactly one lane may say them.
        // Without the opt-out the reading lane repeats them beside the browsing lane.
        assertEquals(1, Regex("showListStatus = false,").findAll(source).count())
        val reading = source.substringAfter("val readingLane: LazyListScope.() -> Unit = {")
        assertTrue("showListStatus = false," in reading)
        assertTrue("laneTracked = lanes.second," in reading)

        val browsing =
            source.substringAfter("val browsingLane: LazyListScope.() -> Unit = {")
                .substringBefore("val readingLane:")
        assertTrue("laneTracked = lanes.first," in browsing)
        assertTrue("showListStatus" !in browsing, "The browsing lane keeps the default")
        // The share-import cards belong to the list too, so they sit with the browsing lane.
        assertTrue("GitHubPendingShareImportCard(" in browsing)
    }

    @Test
    fun oneColumnPutsEverythingInTheBrowsingLane() {
        val source = sourceFile(MAIN_CONTENT_SOURCE)

        // The single-column shape must not consult the lane rule at all: with no second lane to move a
        // card to, expanding has to keep working exactly where the card already is.
        assertTrue(
            "GitHubTrackedLanes(first = tracked.sortedTracked, second = emptyList())" in source,
        )
        assertTrue("if (layout.columnCount >= 2) {" in source)
        assertTrue("content = browsingLane," in source, "One column renders the browsing lane alone")
    }

    @Test
    fun openingACardOnlyMovesItWhereThereIsSomewhereToMoveIt() {
        val source = sourceFile(PAGE_SOURCE)

        assertTrue("actions.setTrackedCardExpanded(itemId, expanded)" in source)
        assertTrue(
            "if (expanded && githubColumnCount >= 2) {" in source,
            "A phone has one lane, so nothing is detained there",
        )
        assertTrue("actions.detainTrackedCardInReadingLane(itemId)" in source)
    }

    @Test
    fun theReadingLaneIsEmptiedOnLeavingThePageAndNotOnCollapse() {
        val source = sourceFile(PAGE_SOURCE)

        // The whole point of holding a collapsed card: closing one must not fling it back across the page
        // while the reader is still looking at it. Leaving the page is the moment it goes home.
        assertTrue("LaunchedEffect(runtime.isPageActive) {" in source)
        assertTrue("if (!runtime.isPageActive) actions.releaseTrackedReadingLane()" in source)

        val actionsSource = sourceFile(ACTIONS_SOURCE)
        val collapse = actionsSource.substringAfter("fun collapseTrackedCard(")
            .substringBefore("\n    fun ")
        assertTrue(
            "releaseTrackedReadingLane" !in collapse && "laneDetainedTrackIds" !in collapse,
            "Collapsing must leave the hold in place",
        )
    }

    @Test
    fun aPinIsPersistedAndAHoldIsNot() {
        val actionsSource = sourceFile(ACTIONS_SOURCE)
        val stateSource = sourceFile(STATE_SOURCE)

        // A pin is a decision and survives a restart; a hold is a record of this visit and must not.
        assertTrue("GitHubPageUiStateStore.setPinnedTrackIds(next)" in actionsSource)
        assertTrue("var pinnedTrackIds by mutableStateOf(pageUiState.pinnedTrackIds)" in stateSource)
        assertTrue("var laneDetainedTrackIds by mutableStateOf(emptyList<String>())" in stateSource)
        val release = actionsSource.substringAfter("fun releaseTrackedReadingLane()")
            .substringBefore("\n    fun ")
        assertTrue("GitHubPageUiStateStore" !in release, "Releasing a hold writes nothing to disk")
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

private const val MAIN_CONTENT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/github/section/GitHubMainContentSection.kt"
private const val PAGE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/github/page/GitHubPage.kt"
private const val ACTIONS_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/github/page/GitHubPageActions.kt"
private const val STATE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/github/page/GitHubPageState.kt"
