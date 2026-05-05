package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Stable
internal class MainLoadedPagerState internal constructor(
    initialPage: Int,
    initialPageCount: Int
) {
    var pageCount by mutableIntStateOf(initialPageCount.coerceAtLeast(0))
        private set

    var currentPage by mutableIntStateOf(initialPage)
        private set

    var targetPage by mutableIntStateOf(initialPage)
        private set

    var settledPage by mutableIntStateOf(initialPage)
        private set

    var currentPageOffsetFraction by mutableFloatStateOf(0f)
        private set

    var pagePosition by mutableFloatStateOf(initialPage.toFloat())
        private set

    var isScrollInProgress by mutableStateOf(false)
        private set

    private var navigationEpoch by mutableIntStateOf(0)

    private var userScrollStartPage: Int = initialPage

    val accessibilityPage: Int
        get() = if (isScrollInProgress) targetPage else settledPage

    internal fun updatePageCount(count: Int) {
        pageCount = count.coerceAtLeast(0)
        val lastIndex = lastIndex()
        if (lastIndex < 0) {
            updatePosition(0f, target = 0, scrolling = false, settle = true)
            return
        }
        val coercedPosition = pagePosition.coerceIn(0f, lastIndex.toFloat())
        val coercedTarget = targetPage.coerceIn(0, lastIndex)
        updatePosition(coercedPosition, target = coercedTarget, scrolling = isScrollInProgress, settle = false)
        if (settledPage > lastIndex) {
            updatePosition(lastIndex.toFloat(), target = lastIndex, scrolling = false, settle = true)
        }
    }

    fun scrollToPage(page: Int) {
        val epoch = nextNavigationEpoch()
        val target = coercePage(page)
        snapToPage(target, epoch)
    }

    internal fun startUserScroll() {
        if (pageCount <= 1) return
        nextNavigationEpoch()
        isScrollInProgress = true
        userScrollStartPage = pagePosition.roundToInt().coerceIn(0, lastIndex())
        targetPage = userScrollStartPage
    }

    internal fun dragBy(deltaPages: Float) {
        val lastIndex = lastIndex()
        if (lastIndex <= 0) return
        val minPosition = if (isScrollInProgress) {
            (userScrollStartPage - 1).coerceAtLeast(0).toFloat()
        } else {
            0f
        }
        val maxPosition = if (isScrollInProgress) {
            (userScrollStartPage + 1).coerceAtMost(lastIndex).toFloat()
        } else {
            lastIndex.toFloat()
        }
        val nextPosition = (pagePosition + deltaPages).coerceIn(minPosition, maxPosition)
        val target = nextPosition.roundToInt().coerceIn(0, lastIndex)
        updatePosition(nextPosition, target = target, scrolling = true, settle = false)
    }

    internal suspend fun settleAfterDrag(
        velocityPagesPerSecond: Float,
        animationsEnabled: Boolean
    ) {
        val lastIndex = lastIndex()
        if (lastIndex <= 0) {
            scrollToPage(0)
            return
        }
        val minTarget = (userScrollStartPage - 1).coerceAtLeast(0)
        val maxTarget = (userScrollStartPage + 1).coerceAtMost(lastIndex)
        val velocityTarget = when {
            velocityPagesPerSecond > MainLoadedPagerVelocityThreshold -> ceil(pagePosition).toInt()
            velocityPagesPerSecond < -MainLoadedPagerVelocityThreshold -> floor(pagePosition).toInt()
            else -> pagePosition.roundToInt()
        }
        animateToPage(
            target = velocityTarget.coerceIn(minTarget, maxTarget),
            animationsEnabled = animationsEnabled,
            durationMillis = MainLoadedPagerSettleDurationMillis,
            epoch = navigationEpoch
        )
    }

    internal suspend fun animateToPage(
        target: Int,
        animationsEnabled: Boolean,
        durationMillis: Int,
        epoch: Int = nextNavigationEpoch()
    ) {
        val coercedTarget = coercePage(target)
        if (!animationsEnabled) {
            snapToPage(coercedTarget, epoch)
            return
        }
        val startPosition = pagePosition
        if (startPosition == coercedTarget.toFloat()) {
            snapToPage(coercedTarget, epoch)
            return
        }
        isScrollInProgress = true
        targetPage = coercedTarget
        try {
            animateLoadedPagerPosition(
                start = startPosition,
                target = coercedTarget.toFloat(),
                durationMillis = durationMillis,
                onFrame = { value ->
                    if (isNavigationCurrent(epoch)) {
                        updatePosition(value, target = coercedTarget, scrolling = true, settle = false)
                    }
                }
            )
            snapToPage(coercedTarget, epoch)
        } finally {
            if (isNavigationCurrent(epoch) && isScrollInProgress) {
                val fallbackPage = pagePosition.roundToInt().coerceIn(0, lastIndex().coerceAtLeast(0))
                snapToPage(fallbackPage, epoch)
            }
        }
    }

    private fun snapToPage(page: Int, epoch: Int) {
        if (!isNavigationCurrent(epoch)) return
        val target = coercePage(page)
        updatePosition(target.toFloat(), target = target, scrolling = false, settle = true)
    }

    private fun nextNavigationEpoch(): Int {
        navigationEpoch += 1
        return navigationEpoch
    }

    private fun isNavigationCurrent(epoch: Int): Boolean {
        return epoch == navigationEpoch
    }

    private fun updatePosition(
        position: Float,
        target: Int,
        scrolling: Boolean,
        settle: Boolean
    ) {
        val lastIndex = lastIndex()
        val safePosition = if (lastIndex >= 0) {
            position.coerceIn(0f, lastIndex.toFloat())
        } else {
            0f
        }
        val basePage = floor(safePosition).toInt().coerceIn(0, lastIndex.coerceAtLeast(0))
        pagePosition = safePosition
        currentPage = basePage
        currentPageOffsetFraction = (safePosition - basePage).coerceIn(0f, 1f)
        targetPage = target.coerceIn(0, lastIndex.coerceAtLeast(0))
        isScrollInProgress = scrolling
        if (settle) {
            settledPage = targetPage
            currentPage = targetPage
            currentPageOffsetFraction = 0f
            pagePosition = targetPage.toFloat()
            isScrollInProgress = false
        }
    }

    private fun coercePage(page: Int): Int {
        val lastIndex = lastIndex()
        return if (lastIndex >= 0) page.coerceIn(0, lastIndex) else 0
    }

    private fun lastIndex(): Int = pageCount - 1
}

@Composable
internal fun rememberMainLoadedPagerState(
    initialPage: Int,
    pageCount: Int
): MainLoadedPagerState {
    var savedPage by rememberSaveable { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    val state = remember {
        MainLoadedPagerState(
            initialPage = savedPage.coerceAtLeast(0),
            initialPageCount = pageCount
        )
    }
    SideEffect {
        state.updatePageCount(pageCount)
        savedPage = state.settledPage
    }
    return state
}

private const val MainLoadedPagerVelocityThreshold = 0.55f
private const val MainLoadedPagerSettleDurationMillis = 220
