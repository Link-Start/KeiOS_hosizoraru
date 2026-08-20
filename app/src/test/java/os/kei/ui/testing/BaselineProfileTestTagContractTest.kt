package os.kei.ui.testing

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The macrobenchmark module cannot depend on the app's source set, so [BaselineProfileGenerator]
 * re-spells every test tag as its own string constant. A drift between the two spellings fails
 * only on a device, minutes into a profile run, as `Timed out waiting for testTag=…`. This walks
 * the same ground in a second.
 */
class BaselineProfileTestTagContractTest {
    @Test
    fun everyTagTheGeneratorWaitsForIsDeclaredInTheApp() {
        val declared = keiOsTestTagValues() + componentOwnedTagValues()
        val generatorTags = generatorTagConstants()

        assertTrue(generatorTags.isNotEmpty(), "Unable to parse tag constants out of the generator")
        generatorTags.forEach { (constant, value) ->
            assertTrue(
                value in declared,
                "$constant = \"$value\" matches no declared tag; the journey would time out",
            )
        }
    }

    @Test
    fun theOverlayLayerStillPublishesTagsAsResourceIds() {
        // The overlay layer is a sibling of the page content, so it inherits nothing from a page root.
        // Without this line every tag inside a sheet, alert, action sheet or menu is invisible to
        // UiAutomator — which is how LiquidSheetPanelTestTag came to exist for the baseline profile and
        // never resolve. Removing it would put every presentation journey back to timing out.
        val source = sourceFile(SCENE_BACKDROP_HOST)

        assertTrue(
            "testTagsAsResourceId = true" in source,
            "$SCENE_BACKDROP_HOST must publish overlay tags or every sheet journey goes blind",
        )
    }

    @Test
    fun everyPageRootTagIsAppliedThroughTheSharedModifier() {
        // testTagsAsResourceId is what publishes a tag to UiAutomator, and pageRootTestTag is the
        // only place that pairs the two. A page root that reaches for a bare testTag is invisible.
        PAGE_ROOT_SOURCES.forEach { (relativePath, tag) ->
            val source = sourceFile(relativePath)

            assertTrue(
                "pageRootTestTag(KeiOsTestTags.$tag)" in source,
                "$relativePath must tag its root through pageRootTestTag",
            )
        }
    }

    @Test
    fun theSharedModifierStillPublishesTagsAsResourceIds() {
        val source = sourceFile("app/src/main/java/os/kei/ui/testing/PageRootTestTag.kt")

        assertTrue(
            "testTagsAsResourceId = true" in source,
            "pageRootTestTag must keep setting testTagsAsResourceId or every journey goes blind",
        )
    }

    @Test
    fun theClassNamesTheTileLongPressJourneyStartsStillExist() {
        // The journey opens the daily-template editor with `am start -n <pkg>/<class>`, and the
        // macrobenchmark module cannot see either class to reference it. A rename would fail as a start
        // that never brings the sheet up, minutes into a run, so the two literals are checked against the
        // manifest that declares them here instead.
        val manifest = sourceFile("app/src/main/AndroidManifest.xml")
        val classNames = generatorClassNameConstants()

        // Vacuously passing is the failure mode to guard here: the filter is by shape, so a rewritten
        // constant could drop out of it and take the check with it.
        assertTrue(classNames.isNotEmpty(), "Unable to parse class-name constants out of the generator")
        classNames.forEach { (constant, className) ->
            val relativeName = className.removePrefix(APP_NAMESPACE)
            assertTrue(
                relativeName in manifest,
                "$constant = \"$className\" is declared by no manifest component; the journey would " +
                    "start nothing",
            )
        }
    }

    /**
     * A journey helper that nothing calls collects nothing, and says nothing about it.
     *
     * `launchDailyTemplateFromTileLongPress` and `discardTheOpenSheetsEdit` were written, documented as
     * covering the tile template editor, and never wired into a `@Test`. They sat uncalled through two
     * profile captures. Kotlin warns about an unused private function; the build does not fail on
     * warnings, and a profile run cannot fail on a journey that does not exist.
     *
     * Comments are stripped before counting, because a KDoc reference to the helper is exactly what made
     * the gap look wired.
     */
    @Test
    fun theGeneratorCallsEveryHelperItDeclares() {
        val source = generatorSourceWithoutComments()
        val helpers = SCOPED_HELPER.findAll(source).map { match -> match.groupValues[1] }.toList()

        assertTrue(helpers.isNotEmpty(), "Unable to parse journey helpers out of the generator")
        helpers.forEach { helper ->
            val calls = Regex("""\b$helper\s*\(""").findAll(source).count()
            assertTrue(
                calls > 1,
                "$helper is declared and never called, so whatever it was meant to collect is missing",
            )
        }
    }

    /** The same silence, one step earlier: a tag constant no journey ever waits for. */
    @Test
    fun theGeneratorUsesEveryConstantItDeclares() {
        val source = generatorSourceWithoutComments()
        val constants = generatorConstants().map { (constant, _) -> constant }

        assertTrue(constants.isNotEmpty(), "Unable to parse constants out of the generator")
        constants.forEach { constant ->
            val references = Regex("""\b$constant\b""").findAll(source).count()
            assertTrue(
                references > 1,
                "$constant is declared and never used, so the path it names is uncovered",
            )
        }
    }

    @Test
    fun tagValuesAreUnique() {
        val values = keiOsTestTagValues()

        assertEquals(values.size, values.toSet().size, "Two KeiOsTestTags entries share a value")
    }
}

private fun keiOsTestTagValues(): List<String> =
    CONST_DECLARATION
        .findAll(sourceFile("app/src/main/java/os/kei/ui/testing/KeiOsTestTags.kt"))
        .map { match -> match.groupValues[2] }
        .toList()

/**
 * Tags a ui-liquid-glass component owns rather than a page.
 *
 * A journey waiting for "any sheet" or "any menu" cannot name a page's tag, and copying these into
 * [KeiOsTestTags] would leave two spellings to drift apart — the exact failure this file exists to
 * prevent.
 */
private fun componentOwnedTagValues(): List<String> =
    COMPONENT_TAG_SOURCES.flatMap { relativePath ->
        CONST_DECLARATION.findAll(sourceFile(relativePath)).map { match -> match.groupValues[2] }
    }

private val COMPONENT_TAG_SOURCES =
    listOf(
        "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/LiquidSheet.kt",
        "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/MiuixSnapshotAdapters.kt",
        "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/UnsavedSheetDismiss.kt",
    )

private const val SCENE_BACKDROP_HOST =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/SceneBackdropScope.kt"

/**
 * The generator's tag constants only.
 *
 * It also holds a couple of *platform identifiers* — the class names the tile long-press journey starts —
 * which are strings but not tags, and would fail the declared-tag check on sight. Tag values are
 * snake_case identifiers and a class name is not, so the shape is the filter;
 * `theClassNamesTheTileLongPressJourneyStartsStillExist` covers what this skips.
 */
private fun generatorTagConstants(): List<Pair<String, String>> =
    generatorConstants().filter { (_, value) -> TAG_SHAPED.matches(value) }

private fun generatorClassNameConstants(): List<Pair<String, String>> =
    generatorConstants().filter { (_, value) -> value.startsWith(APP_NAMESPACE) }

private fun generatorConstants(): List<Pair<String, String>> =
    CONST_DECLARATION
        .findAll(sourceFile(GENERATOR_SOURCE))
        .map { match -> match.groupValues[1] to match.groupValues[2] }
        .toList()

private val TAG_SHAPED = Regex("""[a-z0-9_]+""")

/** Class names stay on the manifest namespace even when the installed applicationId has a suffix. */
private const val APP_NAMESPACE = "os.kei."

private val SCOPED_HELPER = Regex("""private fun MacrobenchmarkScope\.(\w+)\s*\(""")

/**
 * The generator with comments removed.
 *
 * A KDoc mention reads as a use to any plain text search, which is how an uncalled helper stayed
 * plausible through two captures.
 */
private fun generatorSourceWithoutComments(): String =
    sourceFile(GENERATOR_SOURCE)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

private const val GENERATOR_SOURCE =
    "baselineprofile/src/main/java/os/kei/baselineprofile/BaselineProfileGenerator.kt"

private val CONST_DECLARATION = Regex("""const val (\w+)\s*(?:=\s*)?\n?\s*"([^"]+)"""")

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

private val PAGE_ROOT_SOURCES =
    listOf(
        "app/src/main/java/os/kei/ui/page/main/settings/page/SettingsPage.kt" to "SettingsPageRoot",
        "app/src/main/java/os/kei/ui/page/main/about/page/AboutPage.kt" to "AboutPageRoot",
        "app/src/main/java/os/kei/ui/page/main/sync/WebDavSyncPage.kt" to "WebDavSyncPageRoot",
        "app/src/main/java/os/kei/ui/page/main/mcp/skill/page/McpSkillPage.kt" to "McpSkillPageRoot",
        "app/src/main/java/os/kei/ui/page/main/os/shell/page/OsShellRunnerContent.kt" to "OsShellRunnerPageRoot",
        "app/src/main/java/os/kei/ui/page/main/github/history/GitHubActionsNotificationHistoryPage.kt"
            to "GitHubActionsHistoryPageRoot",
    )
