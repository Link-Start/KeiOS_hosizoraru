package os.kei.ui.page.main.student.catalog.page

import androidx.compose.ui.graphics.Color
import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The catalog's top and bottom edges must treat what is behind them, not paint over it.
 *
 * They used to be gradients of the page's `panelBackground` at alpha 0.96/0.98 — fine while that colour
 * was an opaque panel, and black the moment it became `Color.Transparent` so a managed background could
 * show. Measured on the AVD before the fix: rgb(1,2,3) at the top of the screen and rgb(5,5,5) at the
 * bottom, the wallpaper surviving only in the strip between them. After: rgb(20,24,30) and rgb(80,81,88).
 */
class BaGuideCatalogScrollEdgeTest {

    @Test
    fun neitherEdgeTintsWithThePanelColour() {
        val source = sourceFile(BA_GUIDE_CATALOG_PAGE_CONTENT_SOURCE)

        assertFalse(
            "panelBackground.copy(alpha" in source,
            "The panel colour is transparent while a background paints, so re-alpha'ing it paints black",
        )
        assertTrue(
            "val scrollEdgeTint = appPageBackdropBaseColor()" in source,
            "The edges must tint with the page's real base colour",
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

private const val BA_GUIDE_CATALOG_PAGE_CONTENT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/catalog/page/BaGuideCatalogPageContent.kt"
