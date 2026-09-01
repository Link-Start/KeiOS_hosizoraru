package os.kei.ui.page.main.ba

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

class BaCalendarPoolStackedLayoutSourceTest {
    @Test
    fun serverPanelStaysPinnedAboveTheStackedDataList() {
        // Sliced to the stacked function: the two-column layout in the same file deliberately does the
        // opposite -- its panel is the columns' header and therefore lives *inside* the keep-alive box --
        // so matching on the whole file would read that shape's indices instead of this one's.
        val source =
            sourceFile(BA_CALENDAR_POOL_STACKED_LAYOUT_SOURCE)
                .substringAfter("internal fun BaCalendarPoolStackedLayout(")
        val serverPanelIndex = source.indexOf("BaCalendarPoolServerPanel(")
        val providerIndex =
            source.indexOf(
                "CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState)",
            )
        val keepAliveIndex =
            source.indexOf("AppEdgeStackKeepAlive(", startIndex = providerIndex.coerceAtLeast(0))
        val listIndex = source.indexOf("AppPageLazyColumn(", startIndex = keepAliveIndex.coerceAtLeast(0))

        assertTrue(serverPanelIndex >= 0, "The shared layout must render the server panel")
        assertTrue(providerIndex > serverPanelIndex, "The server panel must stay outside the stack provider")
        assertTrue(keepAliveIndex > providerIndex, "The keep-alive box must consume the stack provider")
        assertTrue(listIndex > keepAliveIndex, "The data list must sit inside the keep-alive box")

        // The keep-alive box anchors the geometry, not the list — that is what keeps the stack line
        // measured from the *visible* top edge, since the list inside it is shifted up by the headroom.
        // No assertion needed for the other direction: `appEdgeStackContainer` is private, so a host
        // cannot anchor on its list at all. The inset half is checked for every host at once in
        // `AppEdgeStackHostSourceTest`.
        assertTrue(
            "topExtra = appEdgeStackKeepAliveTopPadding(AppEdgeStackListTopInset)" in source,
            "The list's top inset must include the keep-alive headroom",
        )
    }

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

    @Test
    fun mergedPageOnlyCarriesTheTabBarWhenThereIsATabToBeOn() {
        val source = sourceFile(BA_CALENDAR_POOL_PAGE_SOURCE)

        assertTrue("val bothColumns = columnCount >= 2" in source)
        assertTrue(
            "if (!bothColumns) {" in source,
            "Two lists side by side leave nothing for the category bar to switch",
        )
        val bottomBarIndex = source.indexOf("bottomBar = {")
        val gateIndex = source.indexOf("if (!bothColumns) {", startIndex = bottomBarIndex)
        val chromeIndex = source.indexOf("TabbedPageBottomChrome(", startIndex = gateIndex)
        assertTrue(bottomBarIndex >= 0 && gateIndex > bottomBarIndex && chromeIndex > gateIndex)

        assertTrue(
            "BaCalendarPoolBothColumnsContent(" in source,
            "The wide shape must render both lists rather than a pager over one",
        )
        assertTrue(
            "contentMaxWidth = appPageContentMaxWidthFor(columnCount)," in source,
            "Two columns need the doubled cap, or they share one column's width",
        )
    }

    @Test
    fun bothCalendarPoolRoutesUseTheSharedStackedLayout() {
        listOf(
            sourceFile(BA_ACTIVITY_CALENDAR_SOURCE),
            sourceFile(BA_POOL_SOURCE),
        ).forEach { source ->
            assertTrue("BaCalendarPoolStackedLayout(" in source)
            assertTrue("ba-calendar-server-panel" !in source)
            assertTrue("ba-pool-server-panel" !in source)
        }
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
private const val BA_CALENDAR_POOL_PAGE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaCalendarPoolPage.kt"
private const val BA_ACTIVITY_CALENDAR_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaActivityCalendarPage.kt"
private const val BA_POOL_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaPoolPage.kt"
