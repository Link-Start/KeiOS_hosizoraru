package os.kei.ui.page.main.widget.glass

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The cull has to stay attached to the sheet card, and stay conservative about what it skips.
 *
 * Measured on the API 37 AVD, release build, scrolling the GitHub track editor: RenderThread
 * 24.05ms -> 17.92ms and frame production 95 -> 119 in the same window, with no change to the
 * source of any sheet. The whole win comes from one modifier on one composable, so losing it is
 * silent — nothing fails, the sheets simply get slow again.
 */
class CullWhenFullyClippedSourceTest {
    @Test
    fun theSheetCardStillCulls() {
        val source = sourceFile(SHEET_STYLES)

        assertTrue(
            "cullWhenFullyClipped()" in source,
            "$SHEET_STYLES must keep culling fully clipped cards, or every eager sheet pays for " +
                "layers no pixel of which reaches the screen",
        )
    }

    /**
     * Zero area, not "off screen".
     *
     * `boundsInWindow` returns the clipped rectangle, so a partly visible card reports a positive
     * area and must keep drawing. A predicate that tested the element's *position* instead would
     * blank the card at the edge of the viewport, which is the obvious wrong version of this.
     */
    @Test
    fun theCullTestsClippedAreaRatherThanPosition() {
        val source = sourceFile(CULL)

        assertTrue("boundsInWindow()" in source, "$CULL must read the clipped bounds")
        assertTrue(
            "bounds.width > 0f && bounds.height > 0f" in source,
            "$CULL must cull on zero clipped area only, so a partly visible card still draws",
        )
        assertTrue(
            "if (visible) drawContent()" in source,
            "$CULL must skip the whole subtree draw, which is where the layer cost lives",
        )
    }
}

private const val CULL =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/glass/CullWhenFullyClipped.kt"

private const val SHEET_STYLES =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/SheetStyles.kt"

private fun sourceFile(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val file =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(file) { "Unable to locate $relativePath from $workingDirectory" }.readText()
}
