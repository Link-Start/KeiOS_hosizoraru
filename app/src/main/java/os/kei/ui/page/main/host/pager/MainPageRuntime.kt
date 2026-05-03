package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget

@Immutable
data class MainPageRuntime(
    val scrollToTopSignal: Int = 0,
    val contentTopPadding: Dp = 0.dp,
    val contentBottomPadding: Dp = 72.dp,
    val isWarmActive: Boolean = true,
    val isDataActive: Boolean = true,
    val isPagerScrollInProgress: Boolean = false,
    val bottomBarVisible: Boolean = true,
    val floatingDockSide: AppFloatingDockSide = AppFloatingDockSide.End,
) {
    val isPageActive: Boolean
        get() = isWarmActive
}

@Immutable
internal data class MainPagerRuntimeSnapshot(
    val currentPageIndex: Int,
    val targetPageIndex: Int,
    val settledPageIndex: Int,
    val isPagerScrollInProgress: Boolean,
    val includeTargetPageInHeavyRender: Boolean,
    val shouldRenderNonHomeBackground: Boolean,
    val homePageBottomBarPinned: Boolean,
) {
    val stablePageIndex: Int
        get() = if (isPagerScrollInProgress) targetPageIndex else settledPageIndex

    fun pageRuntime(
        pageIndex: Int,
        scrollToTopSignal: Int = 0,
        contentTopPadding: Dp = 0.dp,
        contentBottomPadding: Dp = 72.dp,
        bottomBarVisible: Boolean = true,
        floatingDockSide: AppFloatingDockSide = AppFloatingDockSide.End,
    ): MainPageRuntime = MainPageRuntime(
        scrollToTopSignal = scrollToTopSignal,
        contentTopPadding = contentTopPadding,
        contentBottomPadding = contentBottomPadding,
        isWarmActive = isWarmActive(pageIndex),
        isDataActive = isDataActive(pageIndex),
        isPagerScrollInProgress = isPagerScrollInProgress,
        bottomBarVisible = bottomBarVisible,
        floatingDockSide = floatingDockSide
    )

    fun isWarmActive(pageIndex: Int): Boolean {
        val isCurrent = pageIndex == currentPageIndex
        val isTarget = pageIndex == targetPageIndex
        val isSettled = pageIndex == settledPageIndex
        return if (isPagerScrollInProgress) {
            isSettled || isTarget
        } else {
            isCurrent || isSettled || (includeTargetPageInHeavyRender && isTarget)
        }
    }

    fun isDataActive(pageIndex: Int): Boolean = pageIndex == settledPageIndex
}

internal fun buildMainPagerRuntimeSnapshot(
    tabs: List<BottomPage>,
    currentPageIndex: Int,
    targetPageIndex: Int,
    settledPageIndex: Int,
    isPagerScrollInProgress: Boolean,
    preloadPolicy: UiPerformanceBudget.PreloadPolicy,
    hasNonHomeBackground: Boolean,
): MainPagerRuntimeSnapshot {
    fun pageAt(index: Int): BottomPage = tabs.getOrElse(index) { BottomPage.Home }

    val targetPage = pageAt(targetPageIndex)
    val settledPage = pageAt(settledPageIndex)
    return MainPagerRuntimeSnapshot(
        currentPageIndex = currentPageIndex,
        targetPageIndex = targetPageIndex,
        settledPageIndex = settledPageIndex,
        isPagerScrollInProgress = isPagerScrollInProgress,
        includeTargetPageInHeavyRender = preloadPolicy.includeTargetPageInHeavyRender,
        shouldRenderNonHomeBackground = hasNonHomeBackground && (
            targetPage != BottomPage.Home ||
                settledPage != BottomPage.Home
            ),
        homePageBottomBarPinned = targetPage == BottomPage.Home ||
            settledPage == BottomPage.Home
    )
}
