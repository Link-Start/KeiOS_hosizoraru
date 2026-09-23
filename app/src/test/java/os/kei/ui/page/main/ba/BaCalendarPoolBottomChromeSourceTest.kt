package os.kei.ui.page.main.ba

import java.io.File
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
