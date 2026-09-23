package os.kei.ui.page.main.student

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class GuideFullscreenStatusBarScrimSourceTest {
    @Test
    fun imageViewerScrimStaysOnTheMiuixBlurChannelWithoutKyantBackdropMixing() {
        val source = sourceFile(GUIDE_GALLERY_FULLSCREEN_SOURCE)

        assertTrue("import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop" in source)
        assertTrue("import top.yukonga.miuix.kmp.blur.layerBackdrop" in source)
        assertTrue("import top.yukonga.miuix.kmp.blur.progressiveTextureBlur" in source)
        assertEquals(1, source.occurrencesOf("rememberLayerBackdrop()"))
        assertEquals(1, source.occurrencesOf(".layerBackdrop("))
        assertEquals(1, source.occurrencesOf(".progressiveTextureBlur("))
        assertTrue("gradient = ProgressiveBlur.Top" in source)

        // The fullscreen image dialog must never sample or export the kyant glass channel.
        assertFalse("import com.kyant.backdrop" in source)
        assertFalse("LocalLiquidParentBackdrop" in source)
        assertFalse("exportBackdropToContent" in source)
    }

    @Test
    fun videoViewerKeepsThePlatformSurfaceWithoutTextureBlur() {
        // Media3 PlayerView renders on a platform surface that layer backdrops cannot
        // sample, so the video dialog must stay off the miuix texture-blur channel.
        val source = sourceFile(GUIDE_GALLERY_FULLSCREEN_MEDIA_LAYER_SOURCE)

        assertFalse("progressiveTextureBlur" in source)
        assertFalse("layerBackdrop" in source)
    }
}

private fun String.occurrencesOf(needle: String): Int =
    windowed(needle.length).count { candidate -> candidate == needle }

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

private const val GUIDE_GALLERY_FULLSCREEN_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/section/gallery/GuideGalleryFullscreen.kt"

private const val GUIDE_GALLERY_FULLSCREEN_MEDIA_LAYER_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/section/gallery/GuideGalleryFullscreenMediaLayer.kt"
