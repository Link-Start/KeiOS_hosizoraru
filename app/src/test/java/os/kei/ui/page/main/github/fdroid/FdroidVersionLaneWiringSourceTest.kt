package os.kei.ui.page.main.github.fdroid

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The lane rule is a pure function and tested as one; this pins the wiring around it.
 *
 * Which lane gets the history's notices, and whether one column consults the rule at all, are decisions
 * made in the page rather than in [fdroidVersionLanesFor] — and getting either wrong fails silently: the
 * page still renders, just with "no versions" in a half-width column beside an empty one, or with the
 * loading notice repeated in both lanes. The release list carries the same tests for the same reason.
 */
class FdroidVersionLaneWiringSourceTest {
    @Test
    fun theNoticesAreSpokenByTheBrowsingLaneOnly() {
        val source = sourceFile(PAGE_SOURCE)

        // "Loading", "empty", "no match", "the refresh failed" all describe the history rather than one
        // of its halves, so exactly one lane may say them.
        val reading =
            source.substringAfter("val readingLane: LazyListScope.() -> Unit = {")
                .substringBefore("val listInnerPadding")
        assertTrue("fdroidVersionCards(rows = lanes.second" in reading)
        assertTrue("fdroidVersionListBody(" !in reading, "The notices belong to the browsing lane")

        val browsing =
            source.substringAfter("val browsingLane: LazyListScope.() -> Unit = {")
                .substringBefore("val readingLane:")
        assertTrue("fdroidVersionListBody(" in browsing)
        assertTrue("rows = lanes.first," in browsing)
    }

    @Test
    fun oneColumnNeverConsultsTheLaneRule() {
        val source = sourceFile(PAGE_SOURCE)

        // With no second lane to move a build to, opening one has to keep working where it already is.
        assertTrue("if (columnCount >= 2) {" in source)
        assertTrue(
            "FdroidVersionLanes(\n                                    first = uiState.rows.withIndex().toList()," in source,
        )
        assertTrue("content = browsingLane," in source, "One column renders the browsing lane alone")
    }

    @Test
    fun anEmptyHistoryDropsBackToOneColumnButAFilteredOneDoesNot() {
        val source = sourceFile(PAGE_SOURCE)

        // `totalCount`, not `rows`. A notice about the whole history must not sit in a half-width lane --
        // but `columnCount` also feeds `contentMaxWidth`, which the top row centres against, so keying it
        // on the *filtered* rows would snap the title and the field inward on the keystroke that stopped
        // matching and back out on the next one.
        assertTrue(
            "val columnCount = if (uiState.totalCount == 0) 1 else appPageColumnCount()" in source,
            "The column count must not depend on what the filters left visible",
        )
        assertTrue("contentMaxWidth = appPageContentMaxWidthFor(columnCount)," in source)
    }

    @Test
    fun aClosedBuildKeepsItsLaneAndNarrowingTheHistoryEmptiesTheHold() {
        val source = sourceFile(PAGE_SOURCE)

        // Recorded when the build opens, so the close that follows cannot fling it back across the page.
        val toggle =
            source.substringAfter("onToggleVersion = { id, open ->")
                .substringBefore("onOpenLink =")
        assertTrue("keptInReadingLane[id] = Unit" in toggle)
        assertTrue(
            "keptInReadingLane.remove" !in toggle,
            "Closing a build must leave it in the lane it was read in",
        )
        // And the hold is scoped to the list as filtered, not to the visit: narrow the history and those
        // builds are not necessarily on screen any more.
        assertTrue("keptInReadingLane.clear()" in source)
        assertEquals(1, Regex(Regex.escape("keptInReadingLane.clear()")).findAll(source).count())

        val readingIds = source.substringAfter("val readingIds =").substringBefore("\n")
        assertTrue(
            "viewModel.defaultExpandedIds + openVersions.keys + keptInReadingLane.keys" in readingIds,
            "Anything open is being read, however it came to be open -- including the anchors",
        )
    }

    @Test
    fun aFailedRefreshIsSaidAboveTheCachedHistoryRatherThanInsteadOfIt() {
        val source = sourceFile(PAGE_SOURCE)

        // The whole reason the page opens at all on an unreachable repository. Replacing the list with
        // the error would throw away the only builds there are.
        val whenBlock =
            source.substringAfter("private fun LazyListScope.fdroidVersionListBody(")
                .substringBefore("private fun LazyListScope.fdroidVersionCards(")
        val elseBranch = whenBlock.substringAfter("        else -> {")
        assertTrue("github_fdroid_version_refresh_failed_format" in elseBranch)
        assertTrue(
            elseBranch.indexOf("fdroidVersionCards(rows = rows") >
                elseBranch.indexOf("github_fdroid_version_refresh_failed_format"),
            "The cards still render after the failed-refresh notice",
        )
        // And the error only replaces the history when there is no history to keep.
        assertTrue("uiState.errorMessage.isNotBlank() && uiState.rows.isEmpty() ->" in whenBlock)
    }

    @Test
    fun narrowingTheHistoryDropsTheHoldWithoutReopeningAnything() {
        val source = sourceFile(PAGE_SOURCE)

        // Two separate guards on purpose. Clearing the hold has to happen on every narrowing, but seeding
        // must not: one guard for both would re-open the recommended build on every keystroke -- including
        // one the reader had just collapsed.
        val effect =
            source.substringAfter("LaunchedEffect(viewModel.defaultExpandedIds, uiState.query")
                .substringBefore("AppPageScaffold(")
        assertTrue("if (lastNarrowing != narrowing) {" in effect)
        assertTrue("if (seededAnchors == anchors" in effect)
        val narrowingBranch = effect.substringAfter("if (lastNarrowing != narrowing) {").substringBefore("}")
        assertTrue("keptInReadingLane.clear()" in narrowingBranch)
        assertTrue(
            "openVersions[id] = Unit" !in narrowingBranch,
            "A keystroke must not re-open cards the reader collapsed",
        )
    }

    @Test
    fun bothLanesScrollOnTheirOwn() {
        val source = sourceFile(PAGE_SOURCE)

        assertTrue("val secondaryListState = rememberLazyListState()" in source)
        assertTrue("primaryState = listState," in source)
        assertTrue("secondaryState = secondaryListState," in source)
    }

    @Test
    fun theCardsKeyOffTheRowIdRatherThanTheVersionCode() {
        val source = sourceFile(PAGE_SOURCE)

        // A version code is not unique: a split build publishes several APKs under one, and a lazy list
        // whose keys collide throws. See fdroidVersionRowId.
        assertTrue("key = { index -> rows[index].value.id }," in source)
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
    "app/src/main/java/os/kei/ui/page/main/github/fdroid/FdroidVersionListPage.kt"
