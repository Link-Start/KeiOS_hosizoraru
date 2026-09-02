package os.kei.ui.page.main.ba

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The merged page's bottom bar behaves like every other tabbed page's.
 *
 * It was the one bar in the app pinned open: `visible = true`, no scroll connection of its own, so it sat
 * over the list while reading and took the width of the page with two tabs in it.
 */
class BaCalendarPoolBottomChromeSourceTest {
    @Test
    fun theBarFollowsTheScrollLikeEveryOtherTabbedPage() {
        val source = sourceFile(PAGE_SOURCE)

        assertTrue("visible = bottomBarVisible," in source, "The bar must not be pinned open")
        assertTrue("visible = true," !in source, "No caller may hard-code the bar visible")
        assertTrue("rememberTabbedPageChromeScrollState(" in source)
        assertTrue("tabbedPageContentNestedScrollConnection(" in source)
        // The chrome has to see the scroll before the top bar consumes it, which is what the composed
        // connection is for -- see GitHubActionsNotificationHistoryPage.
        assertTrue("chrome = bottomChromeScrollState.chromeNestedScrollConnection," in source)
        assertTrue("delegate = scrollBehavior.nestedScrollConnection," in source)
    }

    @Test
    fun theChromeFollowsWhicheverListIsOnScreen() {
        val source = sourceFile(PAGE_SOURCE)

        // One list at a time in the single-column shape, so a single provider resolves to the tab the
        // pager has settled on rather than watching both.
        assertTrue("activeListStateProvider = { activeListState }," in source)
        val active = source.substringAfter("val activeListState =").substringBefore("var bottomBarVisible")
        assertTrue("BaCalendarPoolTab.Calendar -> calendarListState" in active)
        assertTrue("BaCalendarPoolTab.Pool -> poolListState" in active)
    }

    @Test
    fun bothColumnsKeepTheTopBarsOwnConnectionAndNoBarAtAll() {
        val source = sourceFile(PAGE_SOURCE)

        // With both lists side by side there is nothing to switch, so there is no bottom bar to hide and
        // nothing for the chrome connection to drive.
        assertTrue("if (!bothColumns) {" in source)
        val bothColumns =
            source.substringAfter("BaCalendarPoolBothColumnsContent(").substringBefore("} else {")
        assertTrue("nestedScrollConnection = scrollBehavior.nestedScrollConnection," in bothColumns)
        assertEquals(
            2,
            Regex(Regex.escape("nestedScrollConnection = contentNestedScrollConnection,")).findAll(source).count(),
            "Exactly the two single-column tabs take the composed connection",
        )
    }

    @Test
    fun theTwoTabBarIsSizedToItsLabels() {
        val source = sourceFile(PAGE_SOURCE)

        assertTrue("barSizedToTabs = true," in source)
        // Reaching the bar again has to be possible once it has hidden itself.
        assertTrue("onExpandDock = { bottomChromeScrollState.showNow() }," in source)
        assertTrue(
            "bottomChromeScrollState.showNow()" in source.substringAfter("onSelectCategory = { index ->"),
            "Switching tabs brings the bar back rather than leaving it hidden",
        )
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

private const val PAGE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaCalendarPoolPage.kt"
