package os.kei.ui.page.main.ba

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The three conditions that make a flattened panel indistinguishable from its glass twin.
 *
 * A nested panel's glass samples its parent's exported layer, and blurring a locally uniform field
 * returns that field — so the panel can composite its colours straight onto the background and land on
 * the same pixels, without a second offscreen layer over the same area. On the BA office page that is
 * worth p50 75ms -> 55ms of scroll frame time, because those two cards carry ~11 glass surfaces each
 * against one per accordion card on the GitHub page.
 *
 * All three conditions are invisible to the compiler and each one, dropped, is a silent visual or
 * interaction regression rather than a failure:
 *
 * 1. **Gated on having no gesture.** The press deformation lives inside the glass layer, so a pressable
 *    panel has to keep it. The AP and Cafe panels both carry `onLongClick`.
 * 2. **Composited over the page's card material**, not over the card's own fill. An exporting card's
 *    layer carries the page material rather than the card's surface on top of it. In light the two are
 *    within a level; in dark, compositing over the wrong one left every flattened panel visibly lighter
 *    (363k pixels over a 3% threshold, against 18k once corrected).
 * 3. **Opted in only where the parent is an office card.** The calendar and pool pages put panels over a
 *    real accent wash, where a blur is not a no-op, so they keep their glass.
 */
class BaLiquidPanelUniformFillSourceTest {
    @Test
    fun flatteningStaysGatedOnHavingNoGesture() {
        val source = sourceFile(SURFACES)

        assertTrue(
            "flattenOverUniformParent && !hasInteraction" in source,
            "A pressable panel must keep its glass layer, or the press deformation goes with it",
        )
    }

    @Test
    fun theFlatFillCompositesOverThePageCardMaterial() {
        val source = sourceFile(SURFACES)

        assertTrue(
            "appManagedPageCardMaterialColor(" in source,
            "$SURFACES must composite the flat fill over the page material the glass path refracts",
        )
        assertTrue(
            "uniformFill" in source && "appSquircleBackground(" in source,
            "The flat path must paint the corrected fill through the non-clipping squircle background",
        )
    }

    /**
     * Keyed on the fill helper, not on the shader mask.
     *
     * `LiquidSurface`'s own no-backdrop path draws through `squircleSurface`, which is a shader mask and
     * so allocates the very layer this is avoiding — routing the flat path through it measured as no
     * improvement at all (p50 73ms, unchanged).
     */
    @Test
    fun theFlatPathDoesNotReachForTheShaderMask() {
        val source = sourceFile(SURFACES)

        assertTrue(
            "appSquircleSurface(" !in source,
            "$SURFACES must not fill through the masking variant; it costs the layer back",
        )
    }

    @Test
    fun onlyOfficeCardsOptIn() {
        val root = repositoryRoot()
        val optedIn =
            File(root, "app/src/main")
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .filter { file -> "flattenOverUniformParent = true" in file.readText() }
                .map { file -> file.relativeTo(root).invariantSeparatorsPath }
                .toList()
                .sorted()

        assertTrue(optedIn.isNotEmpty(), "Unable to find the opt-in at all, so this guards nothing")
        assertTrue(
            optedIn == OFFICE_CARD_SOURCES,
            "Flattening spread beyond the office cards, where the parent layer is not known uniform.\n" +
                "Found:\n${optedIn.joinToString("\n")}\nExpected:\n${OFFICE_CARD_SOURCES.joinToString("\n")}",
        )
    }
}

private const val SURFACES = "app/src/main/java/os/kei/ui/page/main/ba/BaLiquidSurfaces.kt"

private val OFFICE_CARD_SOURCES =
    listOf(
        "app/src/main/java/os/kei/ui/page/main/ba/card/BaAccountPagerCard.kt",
        "app/src/main/java/os/kei/ui/page/main/ba/card/BaApCard.kt",
        "app/src/main/java/os/kei/ui/page/main/ba/card/BaCafeCard.kt",
        "app/src/main/java/os/kei/ui/page/main/ba/card/BaCardCommon.kt",
    ).sorted()

private fun repositoryRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val root =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile }
    return requireNotNull(root) { "Unable to locate the repository root from $workingDirectory" }
}

private fun sourceFile(relativePath: String): String =
    File(repositoryRoot(), relativePath).let { file ->
        require(file.isFile) { "Unable to locate $relativePath" }
        file.readText()
    }
