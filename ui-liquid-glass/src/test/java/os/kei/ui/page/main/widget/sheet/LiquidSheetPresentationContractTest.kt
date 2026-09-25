package os.kei.ui.page.main.widget.sheet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiquidSheetPresentationContractTest {
    @Test
    fun imeCountsAsVisibleWhenEitherSourceReportsIt() {
        assertTrue("Compose IME insets", liquidSheetImeVisible(composeImeBottomPx = 1, platformImeVisible = false))
        assertTrue("platform IME visibility", liquidSheetImeVisible(composeImeBottomPx = 0, platformImeVisible = true))
        assertFalse("both sources clear", liquidSheetImeVisible(composeImeBottomPx = 0, platformImeVisible = false))
    }

    /**
     * The overlay host has to be a sibling of the captured content. Inside the `layerBackdrop`
     * producer it would be recorded into the very layer it samples, which the library documents as a
     * draw loop and a RenderThread SIGSEGV rather than a soft failure.
     */
    @Test
    fun overlayHostSitsOutsideTheBackdropProducer() {
        val host = sheetSource(SCENE_BACKDROP_SOURCE)
        val producer = host.indexOf(".layerBackdrop(sceneBackdrop)")
        val overlay = host.indexOf("overlayHost.Content()")

        assertTrue("Scene backdrop must still capture the app content", producer >= 0)
        assertTrue("Overlay host must be rendered", overlay >= 0)
        assertTrue(
            "Overlay host must not be inside the layerBackdrop subtree",
            overlay > producer,
        )
        assertTrue(
            "Overlay host must be provided so sheets anywhere in the tree can reach it",
            "LocalLiquidOverlayHost provides overlayHost," in host,
        )
    }

}

private fun sheetSource(relativePath: String): String {
    val roots = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
    val source = roots.map { File(it, relativePath) }.firstOrNull(File::isFile)
    return requireNotNull(source) {
        "Unable to locate $relativePath from ${System.getProperty("user.dir")}"
    }.readText()
}

private const val SCENE_BACKDROP_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/SceneBackdropScope.kt"
