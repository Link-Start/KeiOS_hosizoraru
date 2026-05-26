package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.UiPerformanceBudget
import kotlin.math.abs

@Immutable
data class MainPageRuntime(
    val scrollToTopSignal: Int = 0,
    val contentTopPadding: Dp = 0.dp,
    val contentBottomPadding: Dp = 72.dp,
    val hasActivated: Boolean = true,
    val contentReady: Boolean = true,
    val isWarmActive: Boolean = true,
    val isWarmDataActive: Boolean = true,
    val isDataActive: Boolean = true,
    val backGestureActive: Boolean = false,
    val contentWorkAllowed: Boolean = true,
    val isPagerScrollInProgress: Boolean = false,
    val bottomBarVisible: Boolean = true,
    val floatingDockSide: AppFloatingDockSide = AppFloatingDockSide.End,
) {
    val isPageActive: Boolean
        get() = isWarmActive

    val isSettledDataActive: Boolean
        get() = contentReady && isDataActive && !isPagerScrollInProgress && contentWorkAllowed
}

@Immutable
internal data class MainPagerRuntimeSnapshot(
    val currentPageIndex: Int,
    val targetPageIndex: Int,
    val settledPageIndex: Int,
    val isPagerScrollInProgress: Boolean,
    val includeTargetPageInHeavyRender: Boolean,
    val targetWarmDataActive: Boolean,
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
        hasActivated: Boolean = true,
        contentReady: Boolean = true,
        contentWorkAllowed: Boolean = true,
    ): MainPageRuntime =
        MainPageRuntime(
            scrollToTopSignal = scrollToTopSignal,
            contentTopPadding = contentTopPadding,
            contentBottomPadding = contentBottomPadding,
            hasActivated = hasActivated,
            contentReady = contentReady,
            isWarmActive = contentWorkAllowed && isWarmActive(pageIndex),
            isWarmDataActive = contentWorkAllowed && isWarmDataActive(pageIndex),
            isDataActive = contentWorkAllowed && isDataActive(pageIndex),
            backGestureActive = !contentWorkAllowed,
            contentWorkAllowed = contentWorkAllowed,
            isPagerScrollInProgress = isPagerScrollInProgress,
            bottomBarVisible = bottomBarVisible,
            floatingDockSide = floatingDockSide,
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

    fun isWarmDataActive(pageIndex: Int): Boolean {
        val isTarget = pageIndex == targetPageIndex
        val isSettled = pageIndex == settledPageIndex
        return if (isPagerScrollInProgress) {
            isSettled || (isTarget && targetWarmDataActive)
        } else {
            isWarmActive(pageIndex)
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
    targetWarmDataActive: Boolean,
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
        targetWarmDataActive = targetWarmDataActive,
        shouldRenderNonHomeBackground =
            hasNonHomeBackground && (
                targetPage != BottomPage.Home ||
                    settledPage != BottomPage.Home
            ),
        homePageBottomBarPinned =
            targetPage == BottomPage.Home ||
                settledPage == BottomPage.Home,
    )
}

@Composable
internal fun rememberPagerTargetWarmDataActive(
    pagerState: MainPagerStateContract,
    activationDistance: Float = MAIN_PAGER_TARGET_WARM_DATA_ACTIVATION_DISTANCE,
): State<Boolean> {
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    val warmState =
        remember(pagerState, activationDistance) {
            mutableStateOf(pagerState.isTargetWarmDataActive(activationDistance))
        }
    LaunchedEffect(pagerState, activationDistance, snapshotFlowManager) {
        snapshotFlowManager
            .snapshotFlow { pagerState.isTargetWarmDataActive(activationDistance) }
            .distinctUntilChanged()
            .collect { active -> warmState.value = active }
    }
    return warmState
}

private fun MainPagerStateContract.isTargetWarmDataActive(activationDistance: Float): Boolean =
    abs(pagePosition - targetPage) <= activationDistance

private const val MAIN_PAGER_TARGET_WARM_DATA_ACTIVATION_DISTANCE = 0.75f
