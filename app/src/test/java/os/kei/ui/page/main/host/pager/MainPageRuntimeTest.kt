package os.kei.ui.page.main.host.pager

import androidx.compose.ui.graphics.Color
import org.junit.Test
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainPageRuntimeTest {
    @Test
    fun `preload policy controls target page heavy render and the first fetch delay`() {
        val enabledPolicy = UiPerformanceBudget.resolvePreloadPolicy(preloadingEnabled = true)
        val disabledPolicy = UiPerformanceBudget.resolvePreloadPolicy(preloadingEnabled = false)

        assertTrue(enabledPolicy.includeTargetPageInHeavyRender)
        assertEquals(0, enabledPolicy.initialFetchDelayMs)
        assertFalse(disabledPolicy.includeTargetPageInHeavyRender)
        assertTrue(disabledPolicy.initialFetchDelayMs > 0)
    }

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

    @Test
    fun `main pager delays inactive non home page composition`() {
        assertTrue(
            shouldRenderMainPagerPageContent(
                pageType = BottomPage.Home,
                runtime = MainPageRuntime(hasActivated = false, isWarmActive = false),
            ),
        )
        assertFalse(
            shouldRenderMainPagerPageContent(
                pageType = BottomPage.GitHub,
                runtime = MainPageRuntime(hasActivated = false, isWarmActive = false),
            ),
        )
        assertTrue(
            shouldRenderMainPagerPageContent(
                pageType = BottomPage.GitHub,
                runtime = MainPageRuntime(hasActivated = false, isWarmActive = true),
            ),
        )
        assertTrue(
            shouldRenderMainPagerPageContent(
                pageType = BottomPage.GitHub,
                runtime = MainPageRuntime(hasActivated = true, isWarmActive = false),
            ),
        )
    }

    @Test
    fun `main pager keeps non home page container transparent for shared background`() {
        val nonHome = listOf(BottomPage.Os, BottomPage.Ba, BottomPage.Mcp, BottomPage.GitHub)

        assertEquals(null, mainPagerPageContainerColorOverride(BottomPage.Home, managedBackgroundActive = true))
        nonHome.forEach { page ->
            assertEquals(
                Color.Transparent,
                mainPagerPageContainerColorOverride(page, managedBackgroundActive = true),
            )
        }
    }

    @Test
    fun `main pager keeps its own surface when no background is painting`() {
        // Otherwise `appPageBackdropBaseColor()` resolves to the *elevated* token as the page base, and
        // `AppFeatureCard`'s `surfaceContainer` fill at 64% is that same colour in dark theme — measured a
        // 6-level step between card and page, against 36 on a route.
        (listOf(BottomPage.Home) + listOf(BottomPage.Os, BottomPage.Ba, BottomPage.Mcp, BottomPage.GitHub))
            .forEach { page ->
                assertEquals(
                    null,
                    mainPagerPageContainerColorOverride(page, managedBackgroundActive = false),
                )
            }
    }
}
