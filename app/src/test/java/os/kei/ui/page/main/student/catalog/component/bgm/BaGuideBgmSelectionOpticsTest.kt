package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

class BaGuideBgmSelectionOpticsTest {
    @Test
    fun lightOpticsRestoreReferenceOverlayAndRim() {
        val optics = baGuideBgmDockSelectionOptics(isDark = false)

        assertEquals(Color.White.copy(alpha = 0.08f), optics.overlayColor)
        assertEquals(Color.White.copy(alpha = 0.38f), optics.rimColor)
        assertEquals(1.dp, optics.rimWidth)
    }

    @Test
    fun darkOpticsRestoreReferenceOverlayAndRim() {
        val optics = baGuideBgmDockSelectionOptics(isDark = true)

        assertEquals(Color.White.copy(alpha = 0.03f), optics.overlayColor)
        assertEquals(Color.White.copy(alpha = 0.16f), optics.rimColor)
        assertEquals(1.dp, optics.rimWidth)
    }

}

private fun sourceFile(relativePath: String): String {
    val candidates =
        generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
    val sourceFile =
        candidates
            .map { File(it, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(sourceFile) {
        "Unable to locate $relativePath from ${System.getProperty("user.dir")}"
    }.readText()
}
