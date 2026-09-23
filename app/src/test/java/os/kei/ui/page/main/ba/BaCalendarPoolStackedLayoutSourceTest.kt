package os.kei.ui.page.main.ba

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

class BaCalendarPoolStackedLayoutSourceTest {

    @Test
    fun twoColumnLayoutFoldsTheTopBarInsetIntoItsStackLine() {
        val source =
            sourceFile(BA_CALENDAR_POOL_STACKED_LAYOUT_SOURCE)
                .substringBefore("internal fun BaCalendarPoolStackedLayout(")

        // The stacked shape hangs the panel above the keep-alive box, so the top bar's inset is applied
        // there and the stack line is the list's own constant. Here the panel is the header *inside* the
        // box, so the inset has to be part of the stack line instead -- without it the line sits at the
        // window's top edge and the panel is drawn behind the top bar. That is the bug this pins.
        assertTrue(
            "val stackLine = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap" in
                source,
            "The two-column stack line must clear the top bar",
        )
        assertTrue(
            "topExtra = appEdgeStackKeepAliveTopPadding(stackLine)" in source,
            "The columns' top inset must be measured from that same stack line",
        )
        assertTrue(
            "rememberAppEdgeStackState(stackLine = stackLine)" in source,
            "The stack state must use that same line, or pinned cards would settle somewhere else",
        )

        val headerIndex = source.indexOf("header = {")
        val panelIndex = source.indexOf("BaCalendarPoolServerPanel(", startIndex = headerIndex)
        assertTrue(headerIndex >= 0 && panelIndex > headerIndex, "One panel serves both columns")
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

private const val BA_CALENDAR_POOL_STACKED_LAYOUT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaCalendarPoolStackedLayout.kt"