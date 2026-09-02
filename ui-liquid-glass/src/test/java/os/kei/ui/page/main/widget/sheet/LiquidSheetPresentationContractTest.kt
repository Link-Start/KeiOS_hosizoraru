package os.kei.ui.page.main.widget.sheet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiquidSheetPresentationContractTest {
    @Test
    fun detectsComposeImeInsets() {
        assertTrue(liquidSheetImeVisible(composeImeBottomPx = 1, platformImeVisible = false))
    }

    @Test
    fun detectsPlatformImeInsets() {
        assertTrue(liquidSheetImeVisible(composeImeBottomPx = 0, platformImeVisible = true))
    }

    @Test
    fun reportsHiddenImeWhenBothSourcesAreClear() {
        assertFalse(liquidSheetImeVisible(composeImeBottomPx = 0, platformImeVisible = false))
    }

    /**
     * The sheet must not go back to living in its own window.
     *
     * A `LayerBackdrop` resolves the offset between consumer and producer through shared
     * `LayoutCoordinates`, which two windows do not have, so `LiquidBackdropWindowBoundary` blanks
     * `LocalSceneBackdrop` to `emptyBackdrop()` for Dialog and Popup content. A sheet hosted there
     * gets no error — every `blur()` it asks for simply draws nothing, and the sheet silently
     * degrades to a flat translucent fill. That is what the previous sheet was, and it is why its
     * legibility depended entirely on fill opacity.
     */
    @Test
    fun sheetRendersInTheActivityWindowThroughTheOverlayPortal() {
        val sheet = sheetSource(LIQUID_SHEET_ENTRY_SOURCE)
        val presentation = sheetSource(LIQUID_SHEET_PRESENTATION_SOURCE)

        assertTrue(
            "The sheet must portal into the activity window, not open a Dialog",
            "LiquidOverlayPortal {" in sheet,
        )
        assertFalse("Dialog(" in sheet)
        assertFalse("LiquidBackdropWindowDialog(" in sheet)
        assertFalse("Dialog(" in presentation)
        assertFalse("LiquidBackdropWindowDialog(" in presentation)
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

    /**
     * Guards the single motion driver. Anything reintroducing a second animated value for the sheet's
     * vertical state reopens the divergence that made an outside tap flinch while a drag did not.
     */
    @Test
    fun presentationKeepsOneExitAnimation() {
        val presentation = sheetSource(LIQUID_SHEET_PRESENTATION_SOURCE)

        assertTrue(
            "Both the gesture path and the programmatic path must call runExit",
            presentation.split("runExit(").size - 1 >= 3,
        )
        // Matches declarations, not prose: the KDoc names the old sheet's four values on purpose.
        assertFalse(
            "A separately animated dim is what desynchronised the old exit paths",
            "val dimAlpha" in presentation,
        )
        assertTrue(
            "Both the scrim and the placement must be derived from the one driver",
            "liquidSheetPresentation(hidden.floatValue)" in presentation &&
                "liquidSheetOffsetPx(hidden.floatValue" in presentation,
        )
        assertTrue(
            "Dismissal must latch so repeated gestures dispatch one request",
            "if (dismissInProgress.value) return" in presentation,
        )
    }

    @Test
    fun dragHeightReadsStayOutOfComposition() {
        val presentation = sheetSource(LIQUID_SHEET_PRESENTATION_SOURCE)
        val chrome = sheetSource(LIQUID_SHEET_CHROME_SOURCE)

        assertTrue(
            "The resized height should be read by the layout modifier",
            ".liquidSheetOptionalHeightPx {\n                    if (userResized.value)" in presentation,
        )
        assertFalse(
            "Drag capability values read in Composition recompose the sheet on every delta",
            "canExpand = liquidSheetCanGrow(currentHeightPx()" in presentation,
        )
        assertTrue("canExpand = ::canExpand" in presentation)
        assertTrue("canCollapse = ::canCollapse" in presentation)
        assertTrue("canExpand: () -> Boolean" in chrome)
        assertTrue("canCollapse: () -> Boolean" in chrome)
        assertTrue(".testTag(LiquidSheetDragRegionTestTag)" in chrome)
    }
}

private fun sheetSource(relativePath: String): String {
    val roots = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
    val source = roots.map { File(it, relativePath) }.firstOrNull(File::isFile)
    return requireNotNull(source) {
        "Unable to locate $relativePath from ${System.getProperty("user.dir")}"
    }.readText()
}

private const val LIQUID_SHEET_ENTRY_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/LiquidGlassBottomSheet.kt"
private const val LIQUID_SHEET_PRESENTATION_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/LiquidSheet.kt"
private const val LIQUID_SHEET_CHROME_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/LiquidSheetChrome.kt"
private const val SCENE_BACKDROP_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/SceneBackdropScope.kt"
