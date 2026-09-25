package os.kei.ui.page.main.widget.glass

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Structural guards for the presentation family, pinned at the source level because none of them can be
 * observed from a unit test: they are about which modifier a transform goes through and which file owns
 * a constant.
 */
class LiquidPresentationMaterialContractTest {
    @Test
    fun `nobody inlines the presentation blur or lens expression any more`() {
        // The same two-line expressions were pasted into four files. Frame cost lives in them, so each
        // should be one line to find and one line to change: presentationGlassBlur() and
        // presentationGlassLens(...) in the one allowed file.
        val expressions =
            mapOf(
                "blur" to listOf("maxGlassBlur", "blurScaleFor"),
                "lens" to listOf("backdropLens", "lensScaleFor"),
            )
        val (owners, others) = kotlinMainSources().partition { it.name == PRESENTATION_MATERIAL_FILE }
        val owner = owners.single().readText()

        expressions.forEach { (name, markers) ->
            // The allow-list still matches: the owner derives it, so the scan below is not vacuous.
            assertTrue(markers.all { it in owner }, "$PRESENTATION_MATERIAL_FILE no longer derives the $name")
            val offenders = others.filter { file -> file.readText().let { text -> markers.all { it in text } } }
            assertEquals(emptyList(), offenders.map { it.name }, "these re-derive the presentation $name")
        }
    }

    @Test
    fun `the toast does not animate a glass surface with an ancestor transform`() {
        // AnimatedVisibility's scaleIn/scaleOut is a plain graphicsLayer wrapped *around* the element.
        // LayerBackdrop.drawBackdrop inverse-transforms its sample only by the layerBlock handed to
        // drawBackdrop, so an ancestor scale moves and magnifies the sampled backdrop with the pill —
        // the library's docs call this out explicitly, and the library source even carries a
        // "TODO: outer transformations lead to wrong position calculation".
        val host = toastSource("LiquidToastHost.kt")

        assertFalse(
            "AnimatedVisibility" in host,
            "the toast's enter/exit must go through drawBackdrop's layerBlock, not AnimatedVisibility",
        )
        assertFalse("scaleIn(" in host || "scaleOut(" in host, "same reason: those imply an ancestor layer")
        assertTrue(
            "layerBlock = transformProvider" in toastSource("LiquidToastSurface.kt"),
            "the pill's transform has to reach drawBackdrop's layerBlock",
        )
    }

    private companion object {
        const val PRESENTATION_MATERIAL_FILE = "LiquidPresentationMaterial.kt"
    }

    private fun toastSource(name: String): String = kotlinMainSources().single { it.name == name }.readText()

    private fun kotlinMainSources(): List<File> {
        val root = repositoryRoot()
        return listOf("app/src/main", "ui-liquid-glass/src/main")
            .map { File(root, it) }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" } }
    }

    private fun repositoryRoot(): File {
        val start = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        return generateSequence(start) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile || File(it, "settings.gradle").isFile }
    }
}
