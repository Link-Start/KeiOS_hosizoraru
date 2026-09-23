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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Stable
internal class MainLoadedPagerState internal constructor(
    initialPage: Int,
    initialPageCount: Int
) : MainPagerStateContract {
    override var pageCount by mutableIntStateOf(initialPageCount.coerceAtLeast(0))
        private set

    override var currentPage by mutableIntStateOf(initialPage)
        private set

    override var targetPage by mutableIntStateOf(initialPage)
        private set

    override var settledPage by mutableIntStateOf(initialPage)
        private set

    override var currentPageOffsetFraction by mutableFloatStateOf(0f)
        private set

    override var pagePosition by mutableFloatStateOf(initialPage.toFloat())
        private set

    override var isScrollInProgress by mutableStateOf(false)
        private set

    private var navigationEpoch by mutableIntStateOf(0)

    private var visualJumpEpoch by mutableIntStateOf(0)

    private var visualJumpFromPage by mutableIntStateOf(NoLoadedPagerVisualJumpPage)

    private var visualJumpTargetPage by mutableIntStateOf(NoLoadedPagerVisualJumpPage)

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

    override fun scrollToPage(page: Int) {
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
        animateToPageInternal(
            target = velocityTarget.coerceIn(minTarget, maxTarget),
            animationsEnabled = animationsEnabled,
            motion = MainLoadedPagerMotion.GestureSettle(velocityPagesPerSecond),
            epoch = navigationEpoch
        )
    }

    override suspend fun animateToPage(
        target: Int,
        animationsEnabled: Boolean,
    ) {
        val epoch = nextNavigationEpoch()
        val coercedTarget = coercePage(target)
        val visualFromPage = pagePosition.roundToInt().coerceIn(0, lastIndex().coerceAtLeast(0))
        if (
            animationsEnabled &&
                shouldUseLoadedPagerVisualPair(
                    startPage = visualFromPage,
                    targetPage = coercedTarget,
                )
        ) {
            animateToPageAsVisualPair(
                visualFromPage = visualFromPage,
                target = coercedTarget,
                epoch = epoch,
            )
        } else {
            animateToPageInternal(
                target = coercedTarget,
                animationsEnabled = animationsEnabled,
                motion = MainLoadedPagerMotion.Navigation,
                epoch = epoch,
            )
        }
    }

    private suspend fun animateToPageAsVisualPair(
        visualFromPage: Int,
        target: Int,
        epoch: Int,
    ) {
        visualJumpEpoch = epoch
        visualJumpFromPage = visualFromPage
        visualJumpTargetPage = target
        try {
            animateToPageInternal(
                target = target,
                animationsEnabled = true,
                motion = MainLoadedPagerMotion.Navigation,
                epoch = epoch,
            )
        } finally {
            if (visualJumpEpoch == epoch) {
                visualJumpEpoch = 0
                visualJumpFromPage = NoLoadedPagerVisualJumpPage
                visualJumpTargetPage = NoLoadedPagerVisualJumpPage
            }
        }
    }

    internal fun relativePositionFor(pageIndex: Int): Float =
        resolveLoadedPagerVisualRelativePosition(
            pageIndex = pageIndex,
            pagePosition = pagePosition,
            visualFromPage = visualJumpFromPage,
            visualTargetPage = visualJumpTargetPage,
        )

    internal fun isVisualPairPage(pageIndex: Int): Boolean =
        isLoadedPagerVisualPairPage(
            pageIndex = pageIndex,
            visualFromPage = visualJumpFromPage,
            visualTargetPage = visualJumpTargetPage,
        )

    internal fun shouldDrawVisualPairPage(pageIndex: Int): Boolean =
        shouldDrawLoadedPagerVisualPairPage(
            pageIndex = pageIndex,
            pagePosition = pagePosition,
            visualFromPage = visualJumpFromPage,
            visualTargetPage = visualJumpTargetPage,
        )

    internal fun visualPairVeilAlphaFor(pageIndex: Int): Float =
        resolveLoadedPagerVisualPairVeilAlpha(
            pageIndex = pageIndex,
            pagePosition = pagePosition,
            visualFromPage = visualJumpFromPage,
            visualTargetPage = visualJumpTargetPage,
        )

    internal suspend fun animateToPageViaAdjacent(
        target: Int,
        animationsEnabled: Boolean,
    ) {
        val epoch = nextNavigationEpoch()
        val coercedTarget = coercePage(target)
        if (!animationsEnabled) {
            snapToPage(coercedTarget, epoch)
            return
        }
        val startPosition = pagePosition
        if (abs(startPosition - coercedTarget.toFloat()) <= 1f) {
            animateToPageInternal(
                target = coercedTarget,
                animationsEnabled = true,
                motion = MainLoadedPagerMotion.Navigation,
                epoch = epoch
            )
            return
        }
        val anchorPage =
            if (coercedTarget > startPosition) {
                coercedTarget - 1
            } else {
                coercedTarget + 1
            }.coerceIn(0, lastIndex().coerceAtLeast(0))
        updatePosition(
            position = anchorPage.toFloat(),
            target = coercedTarget,
            scrolling = true,
            settle = false
        )
        animateToPageInternal(
            target = coercedTarget,
            animationsEnabled = true,
            motion = MainLoadedPagerMotion.Navigation,
            epoch = epoch
        )
    }

    private suspend fun animateToPageInternal(
        target: Int,
        animationsEnabled: Boolean,
        motion: MainLoadedPagerMotion,
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
            val onFrame: (Float) -> Unit = { value ->
                if (isNavigationCurrent(epoch)) {
                    updatePosition(value, target = coercedTarget, scrolling = true, settle = false)
                }
            }
            when (motion) {
                is MainLoadedPagerMotion.GestureSettle ->
                    animateLoadedPagerSettlePosition(
                        start = startPosition,
                        target = coercedTarget.toFloat(),
                        gestureVelocityPagesPerSecond = motion.initialVelocityPagesPerSecond,
                        onFrame = onFrame,
                    )

                MainLoadedPagerMotion.Navigation ->
                    animateLoadedPagerPosition(
                        start = startPosition,
                        target = coercedTarget.toFloat(),
                        onFrame = onFrame,
                    )
            }
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

private sealed interface MainLoadedPagerMotion {
    /** A tab selection: Miuix's page-navigation spring, see [animateLoadedPagerPosition]. */
    data object Navigation : MainLoadedPagerMotion

    data class GestureSettle(val initialVelocityPagesPerSecond: Float) : MainLoadedPagerMotion
}

@Composable
internal fun rememberMainLoadedPagerState(
    initialPage: Int,
    pageCount: Int,
    pageKeys: List<String> = List(pageCount.coerceAtLeast(0)) { index -> index.toString() }
): MainLoadedPagerState {
    var savedPageKey by rememberSaveable { mutableStateOf("") }
    val safePageCount = pageCount.coerceAtLeast(0)
    val resolvedInitialPage = remember(pageKeys, initialPage, savedPageKey) {
        resolveMainLoadedPagerInitialPage(
            pageKeys = pageKeys,
            initialPage = initialPage,
            savedPageKey = savedPageKey
        )
    }
    val state = remember(pageKeys) {
        MainLoadedPagerState(
            initialPage = resolvedInitialPage,
            initialPageCount = safePageCount
        )
    }
    SideEffect {
        state.updatePageCount(safePageCount)
        savedPageKey = pageKeys.getOrNull(state.settledPage).orEmpty()
    }
    return state
}

private const val MainLoadedPagerVelocityThreshold = 0.55f

private const val NoLoadedPagerVisualJumpPage = -1

private const val LoadedPagerHiddenPageRelativePosition = 2f

private const val LoadedPagerVisualPairHandoffProgress = 0.5f

private const val LoadedPagerVisualPairParallaxDistance = 0.08f

private const val LoadedPagerVisualPairMaximumVeilAlpha = 0.86f

internal fun shouldUseLoadedPagerVisualPair(
    startPage: Int,
    targetPage: Int,
): Boolean = abs(targetPage - startPage) > 1

internal fun isLoadedPagerVisualPairPage(
    pageIndex: Int,
    visualFromPage: Int,
    visualTargetPage: Int,
): Boolean =
    visualFromPage != NoLoadedPagerVisualJumpPage &&
        visualTargetPage != NoLoadedPagerVisualJumpPage &&
        visualFromPage != visualTargetPage &&
        (pageIndex == visualFromPage || pageIndex == visualTargetPage)

internal fun resolveLoadedPagerVisualRelativePosition(
    pageIndex: Int,
    pagePosition: Float,
    visualFromPage: Int,
    visualTargetPage: Int,
): Float {
    if (
        visualFromPage == NoLoadedPagerVisualJumpPage ||
            visualTargetPage == NoLoadedPagerVisualJumpPage ||
            visualFromPage == visualTargetPage
    ) {
        return pageIndex - pagePosition
    }
    val direction = loadedPagerVisualPairDirection(visualFromPage, visualTargetPage)
    val progress = resolveLoadedPagerVisualPairProgress(
        pagePosition = pagePosition,
        visualFromPage = visualFromPage,
        visualTargetPage = visualTargetPage,
    )
    return when (pageIndex) {
        visualFromPage -> {
            val outgoingProgress =
                (progress / LoadedPagerVisualPairHandoffProgress).coerceIn(0f, 1f)
            if (outgoingProgress == 0f) {
                0f
            } else {
                -direction * outgoingProgress * LoadedPagerVisualPairParallaxDistance
            }
        }
        visualTargetPage -> {
            val incomingProgress =
                (
                    (progress - LoadedPagerVisualPairHandoffProgress) /
                        (1f - LoadedPagerVisualPairHandoffProgress)
                ).coerceIn(0f, 1f)
            val remaining = 1f - incomingProgress
            if (remaining == 0f) {
                0f
            } else {
                direction * remaining * LoadedPagerVisualPairParallaxDistance
            }
        }
        else -> LoadedPagerHiddenPageRelativePosition
    }
}

internal fun shouldDrawLoadedPagerVisualPairPage(
    pageIndex: Int,
    pagePosition: Float,
    visualFromPage: Int,
    visualTargetPage: Int,
): Boolean {
    if (
        !isLoadedPagerVisualPairPage(
            pageIndex = pageIndex,
            visualFromPage = visualFromPage,
            visualTargetPage = visualTargetPage,
        )
    ) {
        return false
    }
    val progress = resolveLoadedPagerVisualPairProgress(
        pagePosition = pagePosition,
        visualFromPage = visualFromPage,
        visualTargetPage = visualTargetPage,
    )
    return if (pageIndex == visualFromPage) {
        progress < LoadedPagerVisualPairHandoffProgress
    } else {
        progress >= LoadedPagerVisualPairHandoffProgress
    }
}

internal fun resolveLoadedPagerVisualPairVeilAlpha(
    pageIndex: Int,
    pagePosition: Float,
    visualFromPage: Int,
    visualTargetPage: Int,
): Float {
    if (
        !isLoadedPagerVisualPairPage(
            pageIndex = pageIndex,
            visualFromPage = visualFromPage,
            visualTargetPage = visualTargetPage,
        )
    ) {
        return 1f
    }
    val progress = resolveLoadedPagerVisualPairProgress(
        pagePosition = pagePosition,
        visualFromPage = visualFromPage,
        visualTargetPage = visualTargetPage,
    )
    val phaseProgress = if (pageIndex == visualFromPage) {
        (1f - progress / LoadedPagerVisualPairHandoffProgress).coerceIn(0f, 1f)
    } else {
        (
            (progress - LoadedPagerVisualPairHandoffProgress) /
                (1f - LoadedPagerVisualPairHandoffProgress)
        ).coerceIn(0f, 1f)
    }
    return (1f - phaseProgress) * LoadedPagerVisualPairMaximumVeilAlpha
}

private fun resolveLoadedPagerVisualPairProgress(
    pagePosition: Float,
    visualFromPage: Int,
    visualTargetPage: Int,
): Float {
    val direction = loadedPagerVisualPairDirection(visualFromPage, visualTargetPage)
    val visualDistance = abs(visualTargetPage - visualFromPage).toFloat().coerceAtLeast(1f)
    return (
        (pagePosition - visualFromPage) * direction / visualDistance
    ).coerceIn(0f, 1f)
}

private fun loadedPagerVisualPairDirection(
    visualFromPage: Int,
    visualTargetPage: Int,
): Float = if (visualTargetPage > visualFromPage) 1f else -1f

internal fun resolveMainLoadedPagerInitialPage(
    pageKeys: List<String>,
    initialPage: Int,
    savedPageKey: String
): Int {
    val safeLastIndex = (pageKeys.size - 1).coerceAtLeast(0)
    val requestedPage = initialPage.coerceIn(0, safeLastIndex)
    val preferredKey = savedPageKey.ifBlank {
        pageKeys.getOrNull(requestedPage).orEmpty()
    }
    return pageKeys.indexOf(preferredKey)
        .takeIf { it >= 0 }
        ?: requestedPage
}
