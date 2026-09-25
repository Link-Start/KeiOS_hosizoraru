package os.kei.ui.page.main.widget.core

import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppSurfaceCardTransformContractTest {

    /**
     * The press scale has to go through `drawBackdrop`'s `layerBlock`. `LayerBackdrop.drawBackdrop`
     * inverse-transforms its sample only by that block, so an ancestor `graphicsLayer` on the card or its
     * box scales the sampled backdrop with the plate and the refraction slides while pressed. Robolectric
     * does not run `RenderEffect`, so this cannot be rendered in a unit test.
     */
    @Test
    fun interactiveTransformStaysInsideLiquidSurfaceBackdropLayer() {
        assertFalse(".graphicsLayer" in sourceFile(APP_FEATURE_CARDS_SOURCE))
        assertFalse(".graphicsLayer" in sourceFile(APP_SURFACE_BOX_SOURCE))

        val liquidSurfaceSource = sourceFile(LIQUID_SURFACES_SOURCE)
        assertTrue("{ applyLiquidSurfaceInteractiveTransform(interactiveHighlight) }" in liquidSurfaceSource)
        assertTrue("layerBlock = interactiveLayerBlock" in liquidSurfaceSource)
    }

    @Test
    fun cardPileTransformAlsoStaysInsideTheBackdropLayer() {
        // Same rule as the press transform, and for a sharper reason: `LayerBackdrop.drawBackdrop`
        // inverse-transforms the sampled backdrop by the layer block it is handed, so a transform
        // applied *outside* drawBackdrop drops into the library's "outer transformations lead to wrong
        // position calculation" path and the refraction visibly slides as the card recedes. The card
        // wrappers therefore hand a slot down to LiquidSurface rather than wrapping it in a transform.
        val cardSource = sourceFile(APP_FEATURE_CARDS_SOURCE)
        val surfaceBoxSource = sourceFile(APP_SURFACE_BOX_SOURCE)
        val liquidSurfaceSource = sourceFile(LIQUID_SURFACES_SOURCE)

        assertFalse("placeRelativeWithLayer" in cardSource)
        assertTrue("rememberAppEdgeStackSlot(enabled = edgeStackEnabled)" in cardSource)
        assertTrue("edgeStack = edgeStack," in cardSource)
        assertTrue("edgeStack = edgeStack," in surfaceBoxSource)
        assertTrue("applyAppEdgeStackTransform(stackCard)" in liquidSurfaceSource)
        // And the pivot has to be a translation, because the library's inverse reads only rotationZ
        // and the two scales and inverts about the top-left — never transformOrigin.
        assertTrue("transformOrigin = AppEdgeStackTopLeftOrigin" in sourceFile(APP_EDGE_STACK_SOURCE))
        assertTrue("APP_EDGE_STACK_PIVOT_X" in sourceFile(APP_EDGE_STACK_SOURCE))
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

private const val APP_FEATURE_CARDS_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/core/AppFeatureCards.kt"

private const val APP_SURFACE_BOX_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/core/AppSurfaceBox.kt"

private const val LIQUID_SURFACES_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/glass/LiquidSurfaces.kt"

private const val APP_EDGE_STACK_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/glass/AppEdgeStackedCards.kt"
