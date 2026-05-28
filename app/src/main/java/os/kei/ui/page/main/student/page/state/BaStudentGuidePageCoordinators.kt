package os.kei.ui.page.main.student.page.state

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.ui.page.main.host.pager.animateTabSwitch
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.widget.chrome.LiquidActionItem
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration

@Composable
internal fun rememberBaStudentGuideTopBarActionItems(
    shareIcon: androidx.compose.ui.graphics.vector.ImageVector,
    refreshIcon: androidx.compose.ui.graphics.vector.ImageVector,
    shareSourceContentDescription: String,
    refreshContentDescription: String,
    onShareSource: () -> Unit,
    onRefresh: () -> Unit,
): List<LiquidActionItem> =
    remember(
        shareIcon,
        refreshIcon,
        shareSourceContentDescription,
        refreshContentDescription,
        onShareSource,
        onRefresh,
    ) {
        listOf(
            LiquidActionItem(
                icon = shareIcon,
                contentDescription = shareSourceContentDescription,
                onClick = onShareSource,
            ),
            LiquidActionItem(
                icon = refreshIcon,
                contentDescription = refreshContentDescription,
                onClick = onRefresh,
            ),
        )
    }

@Composable
internal fun rememberBaStudentGuideTabSelectCoordinator(
    bottomTabs: List<GuideBottomTab>,
    pagerState: PagerState,
    transitionAnimationsEnabled: Boolean,
    farJumpAlpha: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    onShowBottomBarChange: (Boolean) -> Unit,
    onSelectedBottomTabIndexChange: (Int) -> Unit,
    onScrollToTop: () -> Unit = {},
): (Int) -> Unit {
    val pageScope = rememberCoroutineScope()
    val tabJumpJobHolder = remember { BaStudentGuideTabJumpJobHolder() }

    return remember(
        bottomTabs,
        pagerState,
        transitionAnimationsEnabled,
        farJumpAlpha,
        onShowBottomBarChange,
        onSelectedBottomTabIndexChange,
        onScrollToTop,
        pageScope,
    ) {
        { index: Int ->
            if (bottomTabs.isEmpty()) return@remember
            val safeIndex = index.coerceIn(0, bottomTabs.lastIndex)
            val fromIndex =
                if (pagerState.isScrollInProgress) {
                    pagerState.targetPage
                } else {
                    pagerState.settledPage
                }
            if (safeIndex == fromIndex && !pagerState.isScrollInProgress) {
                onShowBottomBarChange(true)
                onScrollToTop()
                return@remember
            }
            onShowBottomBarChange(true)
            onSelectedBottomTabIndexChange(safeIndex)
            tabJumpJobHolder.job?.cancel()
            tabJumpJobHolder.job =
                pageScope.launch {
                    pagerState.animateTabSwitch(
                        fromIndex = fromIndex,
                        targetIndex = safeIndex,
                        animationsEnabled = transitionAnimationsEnabled,
                        onFarJumpBefore = {
                            farJumpAlpha.snapTo(0.94f)
                        },
                        onFarJumpAfter = {
                            farJumpAlpha.animateTo(
                                targetValue = 1f,
                                animationSpec =
                                    tween(
                                        durationMillis =
                                            resolvedMotionDuration(
                                                AppMotionTokens.farJumpRestoreEmphasisMs,
                                                transitionAnimationsEnabled,
                                            ),
                                        easing =
                                            if (transitionAnimationsEnabled) {
                                                FastOutSlowInEasing
                                            } else {
                                                LinearEasing
                                            },
                                    ),
                            )
                        },
                    )
                }
        }
    }
}

private class BaStudentGuideTabJumpJobHolder {
    var job: Job? = null
}
