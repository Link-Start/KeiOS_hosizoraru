package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.widget.chrome.ScrollChromeVisibilityController

internal data class MainPagerTabJumpControllerState(
    val pagerScrollEnabled: Boolean,
    val showBottomBar: Boolean,
    val selectedPageIndex: Int,
    val navigationActive: Boolean,
    val nestedScrollConnection: NestedScrollConnection,
    val farJumpAlpha: Float,
    val onActionBarInteractingChanged: (Boolean) -> Unit,
    val onPageSelected: (Int) -> Unit
)

@Composable
internal fun rememberMainPagerTabJumpController(
    tabs: List<BottomPage>,
    pagerState: MainPagerStateContract,
    pagerRuntime: MainPagerRuntimeSnapshot,
    transitionAnimationsEnabled: Boolean,
    requestedBottomPage: String?,
    requestedBottomPageToken: Int,
    onRequestedBottomPageConsumed: () -> Unit
): MainPagerTabJumpControllerState {
    val coroutineScope = rememberCoroutineScope()
    var tabJumpJob by remember { mutableStateOf<Job?>(null) }
    var pagerScrollEnabled by remember { mutableStateOf(true) }
    var showBottomBar by remember { mutableStateOf(true) }
    var navigationActive by remember { mutableStateOf(false) }
    var selectedPageIndex by rememberSaveable(tabs.map { it.name }) {
        mutableIntStateOf(pagerState.selectedPage.coerceIn(0, tabs.lastIndex.coerceAtLeast(0)))
    }
    val density = LocalDensity.current
    val bottomBarVisibilityThresholdPx = remember(density) { with(density) { 22.dp.toPx() } }
    val bottomBarVisibilityController = remember(bottomBarVisibilityThresholdPx) {
        ScrollChromeVisibilityController(bottomBarVisibilityThresholdPx)
    }
    val currentShowBottomBar by rememberUpdatedState(showBottomBar)

    val nestedScrollConnection = remember(pagerRuntime.homePageBottomBarPinned, bottomBarVisibilityController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pagerRuntime.homePageBottomBarPinned) {
                    bottomBarVisibilityController.showNow(currentShowBottomBar) { showBottomBar = it }
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!pagerRuntime.homePageBottomBarPinned) {
                    bottomBarVisibilityController.update(consumed.y, currentShowBottomBar) { showBottomBar = it }
                }
                return Offset.Zero
            }
        }
    }
    val onActionBarInteractingChanged: (Boolean) -> Unit = { interacting ->
        pagerScrollEnabled = !interacting
    }
    val onPageSelected: (Int) -> Unit = onPageSelected@ { index ->
        if (index !in tabs.indices) return@onPageSelected
        showBottomBar = true
        val targetPageIndex = index.coerceIn(0, tabs.lastIndex)
        if (targetPageIndex == selectedPageIndex) return@onPageSelected

        selectedPageIndex = targetPageIndex
        navigationActive = true
        tabJumpJob?.cancel()
        val nextJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val runningJob = coroutineContext.job
            try {
                val distance = kotlin.math.abs(targetPageIndex - pagerState.currentPage).coerceAtLeast(2)
                pagerState.animateToPage(
                    target = targetPageIndex,
                    animationsEnabled = transitionAnimationsEnabled,
                    durationMillis = 100 * distance + 100
                )
            } finally {
                if (tabJumpJob == runningJob) {
                    navigationActive = false
                    selectedPageIndex = pagerState.selectedPage.coerceIn(
                        0,
                        tabs.lastIndex.coerceAtLeast(0)
                    )
                    tabJumpJob = null
                }
            }
        }
        tabJumpJob = nextJob
        nextJob.start()
    }

    LaunchedEffect(pagerRuntime.homePageBottomBarPinned) {
        if (pagerRuntime.homePageBottomBarPinned && !showBottomBar) {
            bottomBarVisibilityController.reset()
            showBottomBar = true
        }
    }
    LaunchedEffect(pagerState.selectedPage) {
        bottomBarVisibilityController.showNow(showBottomBar) { showBottomBar = it }
    }
    LaunchedEffect(pagerState.selectedPage, pagerState.isScrollInProgress, tabs.size) {
        if (!pagerState.isScrollInProgress && !navigationActive) {
            selectedPageIndex = pagerState.selectedPage.coerceIn(
                0,
                tabs.lastIndex.coerceAtLeast(0)
            )
        }
    }
    LaunchedEffect(requestedBottomPageToken, requestedBottomPage, tabs) {
        val target = requestedBottomPage ?: return@LaunchedEffect
        val index = tabs.indexOfFirst { it.name == target }
        if (index >= 0) {
            onPageSelected(index)
        }
        onRequestedBottomPageConsumed()
    }

    return remember(
        pagerScrollEnabled,
        showBottomBar,
        selectedPageIndex,
        navigationActive,
        nestedScrollConnection,
        onActionBarInteractingChanged,
        onPageSelected
    ) {
        MainPagerTabJumpControllerState(
            pagerScrollEnabled = pagerScrollEnabled,
            showBottomBar = showBottomBar,
            selectedPageIndex = selectedPageIndex,
            navigationActive = navigationActive,
            nestedScrollConnection = nestedScrollConnection,
            farJumpAlpha = 1f,
            onActionBarInteractingChanged = onActionBarInteractingChanged,
            onPageSelected = onPageSelected
        )
    }
}
