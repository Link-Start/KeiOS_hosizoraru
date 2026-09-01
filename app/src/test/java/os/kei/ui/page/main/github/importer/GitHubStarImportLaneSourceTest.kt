package os.kei.ui.page.main.github.importer

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GitHubStarImportLaneSourceTest {
    @Test
    fun everythingYouSetGoesLeftAndTheListYouSetItOnGoesRight() {
        val source = sourceFile(STAR_IMPORT_PAGE_SOURCE)
        val controls = source.substringAfter("val controlItems: LazyListScope.() -> Unit = {")
            .substringBefore("val candidateItems:")
        val candidates = source.substringAfter("val candidateItems: LazyListScope.() -> Unit = {")
            .substringBefore("val listModifier =")

        // The source picker, its guide, the discovered-list picker, the status and the filter/import bar
        // are all things you operate; the candidate rows are what they operate on.
        listOf(
            "StarImportSourceCard(",
            "StarImportSourceGuideCard(",
            "StarImportStarListPickerCard(",
            "StarImportStatusCard(",
            "StarImportListControlCard(",
        ).forEach { card ->
            assertTrue(card in controls, "$card belongs to the controls lane")
            assertTrue(card !in candidates, "$card must not be in the list lane")
        }
        assertTrue("starImportCandidateRows(" in candidates)
        assertTrue("starImportCandidateRows(" !in controls)
    }

    @Test
    fun theStackedShapeKeepsTheOrderItAlwaysHad() {
        val source = sourceFile(STAR_IMPORT_PAGE_SOURCE)
        val stacked = source.substringAfter("        } else {\n            AppPageLazyColumn(")

        // Controls first, then the list -- which is the single column this page has always been.
        val controlsIndex = stacked.indexOf("controlItems()")
        val candidatesIndex = stacked.indexOf("candidateItems()")
        assertTrue(controlsIndex >= 0 && candidatesIndex > controlsIndex)
    }

    @Test
    fun thePageStaysOneColumnUntilThereIsAListToPutBesideTheControls() {
        val source = sourceFile(STAR_IMPORT_PAGE_SOURCE)

        // Before a preview loads the right lane would be empty, and the source card and its guide are
        // exactly what wants the full width in that state.
        assertTrue("val columnCount = if (uiState.preview == null) 1 else appPageColumnCount()" in source)
        assertTrue("val wideLayout = columnCount >= 2" in source)
        assertTrue("contentMaxWidth = appPageContentMaxWidthFor(columnCount)," in source)
    }

    @Test
    fun theListLaneScrollsOnItsOwnState() {
        val source = sourceFile(STAR_IMPORT_PAGE_SOURCE)

        assertTrue("val candidateListState = rememberLazyListState()" in source)
        assertTrue("secondaryState = candidateListState," in source)
        // One scroll container per lane, and the stacked shape still has exactly one.
        assertEquals(1, Regex("AppPageTwoColumnLists\\(").findAll(source).count())
        assertEquals(1, Regex("AppPageLazyColumn\\(").findAll(source).count())
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

private const val STAR_IMPORT_PAGE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/github/importer/GitHubStarImportPage.kt"
