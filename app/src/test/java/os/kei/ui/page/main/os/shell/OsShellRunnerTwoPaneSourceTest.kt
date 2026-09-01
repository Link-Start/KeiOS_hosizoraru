package os.kei.ui.page.main.os.shell

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

class OsShellRunnerTwoPaneSourceTest {
    @Test
    fun theSplitIsCommandLeftAndOutputRightBehindTheSharedColumnGate() {
        val source = sourceFile(OS_SHELL_RUNNER_CONTENT_SOURCE)

        // The same gate the other adapted pages use, so Shell cannot disagree with them about what a tablet
        // is -- and so a phone, and a large-screen device in a narrowed window, keep the stacked shape.
        assertTrue("val columnCount = appPageColumnCount()" in source)
        assertTrue("val twoPanes = columnCount >= 2" in source)
        assertTrue(
            "contentMaxWidth = appPageContentMaxWidthFor(columnCount)," in source,
            "Two panes need the doubled cap, or they divide a single column's width",
        )

        val paneIndex = source.indexOf("AppPageTwoColumnPanes(")
        val primaryIndex = source.indexOf("primary = {", startIndex = paneIndex)
        val inputIndex = source.indexOf("OsShellRunnerInputCard(", startIndex = primaryIndex)
        val secondaryIndex = source.indexOf("secondary = {", startIndex = inputIndex)
        val outputIndex = source.indexOf("outputContent(", startIndex = secondaryIndex)

        assertTrue(paneIndex >= 0, "The wide shape must use the shared two-pane container")
        assertTrue(primaryIndex > paneIndex, "The leading pane comes first")
        assertTrue(inputIndex > primaryIndex, "The command goes in the leading pane")
        assertTrue(outputIndex > secondaryIndex, "What it printed goes in the trailing one")
    }

    @Test
    fun onlyTheWidePathFillsHeightAndOnlyTheStackedPathScrollsThePage() {
        val source = sourceFile(OS_SHELL_RUNNER_CONTENT_SOURCE)
        val wide = source.substringAfter("if (twoPanes) {").substringBefore("} else {")
        val stacked = source.substringAfter("        } else {")

        // Fill only where there is height to fill. A lazy list item is unbounded, so the same request there
        // would collapse both cards to nothing.
        assertTrue("fillAvailableHeight = true," in wide)
        assertTrue("outputContent(Modifier.fillMaxSize(), true)" in wide)
        assertTrue("fillAvailableHeight" !in stacked)
        assertTrue("outputContent(Modifier, false)" in stacked)

        // No nested-scroll connection on the panes: collapsing the top bar would change the inset the panes
        // are measured against, so the split would resize itself while the output scrolled.
        assertTrue("nestedScroll(" !in wide, "The panes must not drive the top bar")
        assertTrue("nestedScroll(scrollBehavior.nestedScrollConnection)" in stacked)

        // Both shapes still hand the page's material to the top bar's glass.
        assertTrue("layerBackdrop(topBarBackdrop.producer)" in wide)
        assertTrue("layerBackdrop(topBarBackdrop.producer)" in stacked)
    }

    @Test
    fun theOutputPanelDropsItsGrowthAnimationOnlyWhenThePaneOwnsTheHeight() {
        val source = sourceFile(OS_SHELL_RUNNER_CARDS_SOURCE)

        // Stacked, the card grows with its output and that growth is animated. Filling a pane, the height is
        // the window's -- there is nothing to animate, and animating it would fight every keyboard opening.
        assertTrue("Modifier.weight(1f)" in source)
        assertTrue("Modifier.animateContentSize().heightIn(min = 160.dp, max = 320.dp)" in source)
        assertTrue("fillAvailableHeight: Boolean = false," in source)
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

private const val OS_SHELL_RUNNER_CONTENT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/os/shell/page/OsShellRunnerContent.kt"
private const val OS_SHELL_RUNNER_CARDS_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/os/shell/component/OsShellRunnerCards.kt"
