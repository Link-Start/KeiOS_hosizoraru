package os.kei.ui.page.main.widget.chrome

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The two ends of the top row must take the same gutter.
 *
 * They did not, and nothing caught it: the first Pad round gave the trailing actions
 * [appTopBarChromeGutter] and left the navigation icon on the bare window margin, so a pushed route in
 * landscape put its back button 242dp outside the column its own page occupied. That is invisible on a phone
 * — the gutter is 0dp there — and invisible in portrait, where it is 40dp. Only the 280dp landscape gutter
 * showed it, which is why this is pinned in source rather than left to a screenshot.
 */
class AppTopBarChromeGutterSourceTest {
    @Test
    fun `both ends of the top row take the same gutter`() {
        val source = sourceFile(APP_TOP_BAR_SOURCE)

        assertTrue(
            "start = appTopBarEdgePaddingStart() + appTopBarChromeGutter()" in source,
            "The navigation icon must come in with the content column, like the actions opposite it",
        )
        assertTrue(
            "end = appTopBarEdgePadding() + appTopBarChromeGutter()" in source,
            "The trailing actions must keep the gutter they have had since the first Pad round",
        )
        // Exactly the two ends. A third would mean the leading title picked it up too, which is wrong: that
        // branch only runs at the top tab bar, where the gutter is zero by rule and the term would be dead.
        assertEquals(2, source.occurrences("appTopBarChromeGutter()"))
    }

    /** One rule, not two that can drift apart again. */
    @Test
    fun `there is no side-specific gutter helper left`() {
        val source = sourceFile(APP_NAVIGATION_PLACEMENT_SOURCE)

        assertTrue("fun appTopBarChromeGutterFor(" in source)
        assertEquals(0, source.occurrences("fun appTopBarActionGutter("))
    }
}

private fun sourceFile(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val sourceFile =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(sourceFile) { "Unable to locate $relativePath from $workingDirectory" }.readText()
}

private fun String.occurrences(needle: String): Int = windowed(needle.length).count { it == needle }

private const val APP_TOP_BAR_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/widget/chrome/AppTopBar.kt"
private const val APP_NAVIGATION_PLACEMENT_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/chrome/AppNavigationPlacement.kt"
