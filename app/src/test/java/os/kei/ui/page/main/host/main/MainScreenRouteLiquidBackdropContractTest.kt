package os.kei.ui.page.main.host.main

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainScreenRouteLiquidBackdropContractTest {

    /**
     * Strengthened from the visibility-gated producer this replaces.
     *
     * The toast used to wrap NavDisplay in its own full-screen `layerBackdrop`, gated on
     * `liquidToastState.isVisible` so the offscreen layer stayed off the idle path. The property that
     * gate protected — no resident second rasterization of the whole app — now holds unconditionally,
     * because the toast samples `LocalSceneBackdrop` and produces nothing of its own. Sampling the
     * scene backdrop is also strictly better: the private producer had no `onDraw`, so it handed the
     * pill transparent pixels to blur wherever page content did not paint, and being inside the scene
     * capture meant every sheet and alert blurred a ghost of the toast into its own surface.
     */
    @Test
    fun theNavHostProducesNoBackdropLayerOfItsOwn() {
        val source = sourceFile(MAIN_SCREEN_NAV_HOST_SOURCE)

        assertEquals(0, source.occurrencesOf("rememberLayerBackdrop("))
        assertEquals(0, source.occurrencesOf("Modifier.layerBackdrop("))
        assertTrue(
            "LiquidToastHost(state = liquidToastState)" in source,
            "the toast host should take no backdrop here — it samples the scene backdrop",
        )
    }

}

private fun String.occurrencesOf(needle: String): Int = windowed(needle.length).count { it == needle }

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

private const val MAIN_SCREEN_NAV_HOST_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/host/main/MainScreenNavHost.kt"