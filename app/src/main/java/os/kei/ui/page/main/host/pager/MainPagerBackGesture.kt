package os.kei.ui.page.main.host.pager

import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import os.kei.ui.page.main.back.BACK_GESTURE_CANCEL_SETTLE_DURATION_MS
import os.kei.ui.page.main.back.BACK_GESTURE_COMMIT_SETTLE_DURATION_MS
import os.kei.ui.page.main.back.BackGestureMotionConfig
import os.kei.ui.page.main.back.BackGestureMotionValues
import os.kei.ui.page.main.back.BackNavigationCommitGate
import os.kei.ui.page.main.back.BackNavigationHandlerMode
import os.kei.ui.page.main.back.BackNavigationSource
import os.kei.ui.page.main.back.LocalBackNavigationRuntimeController
import os.kei.ui.page.main.back.LocalBackNavigationRuntimeState
import os.kei.ui.page.main.back.resolveBackGestureMotion
import os.kei.ui.page.main.back.resolveBackNavigationHandlerMode
import os.kei.ui.page.main.back.settleBackGestureProgress
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled

@Stable
internal class MainPagerHomeBackGestureState internal constructor(
    private val progressProvider: () -> Float,
    private val motionProvider: () -> BackGestureMotionValues,
    private val selectedPageIndexProvider: () -> Int,
    private val homePageIndexProvider: () -> Int,
    val onContainerSizeChanged: (width: Int, height: Int) -> Unit,
) {
    val inProgress: Boolean
        get() = progressProvider() > MAIN_PAGER_BACK_IDLE_EPSILON

    val motionValues: BackGestureMotionValues
        get() = motionProvider()

    fun selectedPagePosition(): Float {
        return resolveMainPagerBackSelectedPosition(
            selectedPageIndex = selectedPageIndexProvider(),
            homePageIndex = homePageIndexProvider(),
            progress = progressProvider(),
        )
    }
}

@OptIn(ExperimentalActivityApi::class)
@Composable
internal fun rememberMainPagerHomeBackGestureState(
    enabled: Boolean,
    selectedPageIndex: Int,
    homePageIndex: Int,
    onNavigateHome: () -> Unit,
): MainPagerHomeBackGestureState {
    val latestOnNavigateHome by rememberUpdatedState(onNavigateHome)
    val latestSelectedPageIndex by rememberUpdatedState(selectedPageIndex)
    val latestHomePageIndex by rememberUpdatedState(homePageIndex)
    val runtimeController = LocalBackNavigationRuntimeController.current
    val runtimeState = LocalBackNavigationRuntimeState.current
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val predictiveBackAnimationsEnabled = LocalPredictiveBackAnimationsEnabled.current
    val predictiveEnabled = enabled && resolveBackNavigationHandlerMode(
        policy = runtimeState.policy,
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
    ) == BackNavigationHandlerMode.ComposePredictive
    val commitGate = remember { BackNavigationCommitGate() }
    val gestureProgress = remember { Animatable(0f) }
    var gestureSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_NONE) }
    var gestureTouchY by remember { mutableFloatStateOf(0f) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }
    val motionConfig = remember {
        BackGestureMotionConfig(
            translationFactor = MAIN_PAGER_BACK_TRANSLATION_FACTOR,
            contentFadeFactor = MAIN_PAGER_BACK_CONTENT_FADE_FACTOR,
            scrimFadeFactor = MAIN_PAGER_BACK_SCRIM_FADE_FACTOR,
            minScale = MAIN_PAGER_BACK_MIN_SCALE,
            pivotEdgeBias = MAIN_PAGER_BACK_PIVOT_EDGE_BIAS,
        )
    }

    if (enabled && !predictiveEnabled) {
        BackHandler {
            commitGate.reset()
            runtimeController.beginCommit(BackNavigationSource.MainPager)
            try {
                commitGate.tryCommit(latestOnNavigateHome)
            } finally {
                runtimeController.reset()
            }
        }
    }

    if (predictiveEnabled) {
        PredictiveBackHandler { backEvents ->
            commitGate.reset()
            runtimeController.beginGesture(BackNavigationSource.MainPager)
            var committed = false
            try {
                backEvents.collect { event ->
                    gestureSwipeEdge = event.swipeEdge
                    gestureTouchY = event.touchY
                    gestureProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
                runtimeController.beginCommit(BackNavigationSource.MainPager)
                gestureProgress.settleBackGestureProgress(
                    targetProgress = 1f,
                    maxDurationMillis = BACK_GESTURE_COMMIT_SETTLE_DURATION_MS,
                )
                committed = commitGate.tryCommit(latestOnNavigateHome)
            } catch (_: CancellationException) {
                gestureProgress.settleBackGestureProgress(
                    targetProgress = 0f,
                    maxDurationMillis = BACK_GESTURE_CANCEL_SETTLE_DURATION_MS,
                )
            } finally {
                gestureProgress.snapTo(0f)
                gestureSwipeEdge = BackEventCompat.EDGE_NONE
                gestureTouchY = 0f
                runtimeController.reset()
            }
        }
    }

    return remember {
        MainPagerHomeBackGestureState(
            progressProvider = { gestureProgress.value },
            motionProvider = {
                resolveBackGestureMotion(
                    progress = gestureProgress.value,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                    swipeEdge = gestureSwipeEdge,
                    touchY = gestureTouchY,
                    config = motionConfig,
                )
            },
            selectedPageIndexProvider = { latestSelectedPageIndex },
            homePageIndexProvider = { latestHomePageIndex },
            onContainerSizeChanged = { width, height ->
                containerWidthPx = width
                containerHeightPx = height
            },
        )
    }
}

internal fun resolveMainPagerBackSelectedPosition(
    selectedPageIndex: Int,
    homePageIndex: Int,
    progress: Float,
): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return selectedPageIndex + (homePageIndex - selectedPageIndex) * clampedProgress
}

private const val MAIN_PAGER_BACK_IDLE_EPSILON = 0.001f
private const val MAIN_PAGER_BACK_TRANSLATION_FACTOR = 0.055f
private const val MAIN_PAGER_BACK_CONTENT_FADE_FACTOR = 0.08f
private const val MAIN_PAGER_BACK_SCRIM_FADE_FACTOR = 0f
private const val MAIN_PAGER_BACK_MIN_SCALE = 0.982f
private const val MAIN_PAGER_BACK_PIVOT_EDGE_BIAS = 0.78f
