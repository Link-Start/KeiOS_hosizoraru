@file:Suppress("FunctionName")

package os.kei.ui.page.main.host.pager

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
internal class MainMiuixPagerState internal constructor(
    internal val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) : MainPagerStateContract {
    private var snapJob: Job? = null
    private var navigationSelectedPage by mutableIntStateOf(pagerState.currentPage)
    private var navigating by mutableStateOf(false)

    override val pageCount: Int
        get() = pagerState.pageCount

    override val currentPage: Int
        get() = pagerState.currentPage.coerceInPageRange()

    override val targetPage: Int
        get() = if (navigating) navigationSelectedPage.coerceInPageRange() else pagerState.targetPage.coerceInPageRange()

    override val settledPage: Int
        get() = pagerState.settledPage.coerceInPageRange()

    override val currentPageOffsetFraction: Float
        get() = pagerState.currentPageOffsetFraction

    override val pagePosition: Float
        get() =
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, lastIndex().toFloat().coerceAtLeast(0f))

    override val isScrollInProgress: Boolean
        get() = pagerState.isScrollInProgress

    override val selectedPage: Int
        get() = navigationSelectedPage.coerceInPageRange()

    override val isProgrammaticNavigationInProgress: Boolean
        get() = navigating

    override fun scrollToPage(page: Int) {
        val target = page.coerceInPageRange()
        snapJob?.cancel()
        snapJob =
            coroutineScope.launch {
                pagerState.scrollToPage(target)
                navigationSelectedPage = target
                navigating = false
            }
    }

    override suspend fun animateToPage(
        target: Int,
        animationsEnabled: Boolean,
        durationMillis: Int,
    ) {
        val targetIndex = target.coerceInPageRange()
        if (targetIndex == navigationSelectedPage && navigating) return
        navigationSelectedPage = targetIndex
        snapJob?.cancel()
        if (!animationsEnabled) {
            pagerState.scrollToPage(targetIndex)
            navigationSelectedPage = targetIndex
            navigating = false
            return
        }
        navigating = true
        try {
            pagerState.scroll(MutatePriority.UserInput) {
                val layoutInfo = pagerState.layoutInfo
                val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                if (pageSize <= 0) return@scroll
                val distanceInPages =
                    targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
                val scrollPixels = distanceInPages * pageSize
                var previousValue = 0f
                animate(
                    initialValue = 0f,
                    targetValue = scrollPixels,
                    animationSpec =
                        tween(
                            durationMillis = durationMillis.coerceIn(180, 520),
                            easing = EaseInOut,
                        ),
                ) { currentValue, _ ->
                    previousValue += scrollBy(currentValue - previousValue)
                }
            }
            if (pagerState.currentPage != targetIndex) {
                pagerState.scrollToPage(targetIndex)
            }
        } finally {
            navigating = false
            navigationSelectedPage = pagerState.currentPage.coerceInPageRange()
        }
    }

    internal fun syncPage() {
        if (!navigating && navigationSelectedPage != pagerState.currentPage) {
            navigationSelectedPage = pagerState.currentPage.coerceInPageRange()
        }
    }

    internal fun syncPageCount() {
        val safeSelectedPage = navigationSelectedPage.coerceInPageRange()
        if (safeSelectedPage != navigationSelectedPage) {
            navigationSelectedPage = safeSelectedPage
        }
    }

    private fun Int.coerceInPageRange(): Int {
        val lastIndex = lastIndex()
        return if (lastIndex >= 0) coerceIn(0, lastIndex) else 0
    }

    private fun lastIndex(): Int = pageCount - 1
}

@Composable
internal fun rememberMainMiuixPagerState(
    initialPage: Int,
    pageCount: Int,
    pageKeys: List<String> = List(pageCount.coerceAtLeast(0)) { index -> index.toString() },
): MainMiuixPagerState {
    var savedPageKey by rememberSaveable { mutableStateOf("") }
    val safePageCount = pageCount.coerceAtLeast(0)
    val resolvedInitialPage =
        remember(pageKeys, initialPage, savedPageKey) {
            resolveMainLoadedPagerInitialPage(
                pageKeys = pageKeys,
                initialPage = initialPage,
                savedPageKey = savedPageKey,
            )
        }
    val pagerState =
        rememberPagerState(
            initialPage = resolvedInitialPage.coerceIn(0, (safePageCount - 1).coerceAtLeast(0)),
            pageCount = { safePageCount },
        )
    val coroutineScope = rememberCoroutineScope()
    val state =
        remember(pagerState, coroutineScope) {
            MainMiuixPagerState(
                pagerState = pagerState,
                coroutineScope = coroutineScope,
            )
        }
    SideEffect {
        state.syncPageCount()
        savedPageKey = pageKeys.getOrNull(state.settledPage).orEmpty()
    }
    LaunchedEffect(pagerState.currentPage) {
        state.syncPage()
    }
    return state
}

@Composable
internal fun MainMiuixPager(
    state: MainMiuixPagerState,
    userScrollEnabled: Boolean,
    beyondViewportPageCount: Int,
    modifier: Modifier = Modifier,
    pageContent: @Composable (pageIndex: Int) -> Unit,
) {
    HorizontalPager(
        state = state.pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount =
            if (state.pageCount > 1) {
                beyondViewportPageCount.coerceAtLeast(0)
            } else {
                0
            },
        userScrollEnabled = userScrollEnabled && state.pageCount > 1,
        verticalAlignment = Alignment.Top,
        pageContent = { pageIndex ->
            pageContent(pageIndex)
        },
    )
}
