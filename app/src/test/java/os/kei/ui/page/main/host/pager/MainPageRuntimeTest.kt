package os.kei.ui.page.main.host.pager

import org.junit.Test
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainPageRuntimeTest {
    @Test
    fun `back gesture freezes heavy page work while keeping activation state`() {
        val snapshot =
            buildMainPagerRuntimeSnapshot(
                tabs = listOf(BottomPage.Home, BottomPage.GitHub),
                currentPageIndex = 1,
                targetPageIndex = 1,
                settledPageIndex = 1,
                isPagerScrollInProgress = false,
                preloadPolicy = UiPerformanceBudget.resolvePreloadPolicy(preloadingEnabled = true),
                hasNonHomeBackground = false,
                targetWarmDataActive = true,
            )

        val runtime =
            snapshot.pageRuntime(
                pageIndex = 1,
                hasActivated = true,
                contentReady = true,
                contentWorkAllowed = false,
            )

        assertTrue(runtime.hasActivated)
        assertTrue(runtime.contentReady)
        assertTrue(runtime.backGestureActive)
        assertFalse(runtime.isWarmActive)
        assertFalse(runtime.isWarmDataActive)
        assertFalse(runtime.isDataActive)
    }
}
