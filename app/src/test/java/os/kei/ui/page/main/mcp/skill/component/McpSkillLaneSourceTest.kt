package os.kei.ui.page.main.mcp.skill.component

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class McpSkillLaneSourceTest {
    @Test
    fun theStackedShapeKeepsTheOrderItAlwaysHad() {
        val stacked =
            sourceFile(MCP_SKILL_CONTENT_LIST_SOURCE)
                .substringAfter("        AppPageLazyColumn(")

        // The split is the reason this needs pinning: the lanes reorder these five, so the one shape that
        // must not move is the phone's. Cards in a lazy column, top to bottom, exactly as before.
        assertEquals(
            listOf(
                "onboardingItem()",
                "quickCopyItem()",
                "resourcesItem()",
                "flowsItem()",
                "referenceItems()",
            ),
            stacked
                .lineSequence()
                .map(String::trim)
                .filter { line -> line.endsWith("Item()") || line.endsWith("Items()") }
                .toList(),
        )
    }

    @Test
    fun theSplitPutsTheProcedureInOneLaneAndTheCatalogueInTheOther() {
        val wide =
            sourceFile(MCP_SKILL_CONTENT_LIST_SOURCE)
                .substringAfter("        AppPageTwoColumnLists(")
                .substringBefore("    } else {")

        assertEquals(
            // Connect, then the payloads to copy, then the sequences to run.
            listOf("onboardingItem()", "quickCopyItem()", "flowsItem()"),
            laneBuilders(wide, "primary = {"),
        )
        assertEquals(
            // The URIs to read, and the document they come out of.
            listOf("resourcesItem()", "referenceItems()"),
            laneBuilders(wide, "secondary = {"),
        )
    }

    @Test
    fun expandingTheReferenceGrowsOnlyTheLaneItIsAlreadyIn() {
        val source = sourceFile(MCP_SKILL_CONTENT_LIST_SOURCE)
        val referenceBuilder =
            source
                .substringAfter("val referenceItems: LazyListScope.() -> Unit = {")
                .substringBefore("\n    }\n")

        // The section cards are emitted by the same builder as the card that expands them, so an unbounded
        // number of them lands in the reading lane rather than shoving the buttons off screen. Nothing else
        // in the file may emit them, or a lane assignment could put the card and its sections apart.
        assertTrue("if (referenceExpanded) {" in referenceBuilder)
        assertTrue("SkillSectionCard(" in referenceBuilder)
        assertEquals(
            1,
            Regex("SkillSectionCard\\(").findAll(source).count(),
            "Section cards must have exactly one emitter",
        )
    }

    @Test
    fun thePageWidensItsContentCapWithItsColumnCount() {
        val source = sourceFile(MCP_SKILL_PAGE_SOURCE)

        assertTrue("val columnCount = appPageColumnCount()" in source)
        assertTrue("contentMaxWidth = appPageContentMaxWidthFor(columnCount)," in source)
        assertTrue("wideLayout = columnCount >= 2," in source)
        assertTrue(
            "val secondaryListState = rememberLazyListState()" in source,
            "The second lane needs its own state, or both lanes scroll as one",
        )
    }
}

private fun laneBuilders(
    source: String,
    slot: String,
): List<String> =
    source
        .substringAfter(slot)
        .substringBefore("\n            },")
        .lineSequence()
        .map(String::trim)
        .filter { line -> line.endsWith("Item()") || line.endsWith("Items()") }
        .toList()

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

private const val MCP_SKILL_CONTENT_LIST_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/mcp/skill/component/McpSkillContentList.kt"
private const val MCP_SKILL_PAGE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/mcp/skill/page/McpSkillPage.kt"
