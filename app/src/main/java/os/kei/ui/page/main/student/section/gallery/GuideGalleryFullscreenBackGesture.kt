package os.kei.ui.page.main.student.section.gallery

import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
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
import os.kei.ui.page.main.back.resolveBackGestureMotion
import os.kei.ui.page.main.back.settleBackGestureProgress
import os.kei.ui.page.main.student.IMAGE_BACK_GESTURE_CONTENT_FADE_FACTOR
import os.kei.ui.page.main.student.IMAGE_BACK_GESTURE_SCRIM_FADE_FACTOR
import os.kei.ui.page.main.student.IMAGE_BACK_GESTURE_TRANSLATION_FACTOR
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled

internal data class GuideFullscreenBackGestureState(
    val motionValues: () -> BackGestureMotionValues,
    val onDialogSizeChanged: (width: Int, height: Int) -> Unit
)

@OptIn(ExperimentalActivityApi::class)
@Composable
internal fun rememberGuideFullscreenBackGestureState(
    onDismiss: () -> Unit
): GuideFullscreenBackGestureState {
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val predictiveBackProgress = remember { Animatable(0f) }
    var predictiveBackSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_NONE) }
    var predictiveBackTouchY by remember { mutableFloatStateOf(0f) }
    var dialogWidthPx by remember { mutableIntStateOf(0) }
    var dialogHeightPx by remember { mutableIntStateOf(0) }
    val motionConfig = remember {
        BackGestureMotionConfig(
            translationFactor = IMAGE_BACK_GESTURE_TRANSLATION_FACTOR,
            contentFadeFactor = IMAGE_BACK_GESTURE_CONTENT_FADE_FACTOR,
            scrimFadeFactor = IMAGE_BACK_GESTURE_SCRIM_FADE_FACTOR,
        )
    }
    val predictiveBackAnimationsEnabled = LocalTransitionAnimationsEnabled.current &&
        LocalPredictiveBackAnimationsEnabled.current

    BackHandler(enabled = true) {
        latestOnDismiss()
    }
    PredictiveBackHandler(enabled = predictiveBackAnimationsEnabled) { backEvents ->
        var dismissed = false
        try {
            backEvents.collect { event ->
                predictiveBackSwipeEdge = event.swipeEdge
                predictiveBackTouchY = event.touchY
                predictiveBackProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            predictiveBackProgress.settleBackGestureProgress(
                targetProgress = 1f,
                maxDurationMillis = BACK_GESTURE_COMMIT_SETTLE_DURATION_MS,
            )
            dismissed = true
            latestOnDismiss()
        } catch (_: CancellationException) {
            predictiveBackProgress.settleBackGestureProgress(
                targetProgress = 0f,
                maxDurationMillis = BACK_GESTURE_CANCEL_SETTLE_DURATION_MS,
            )
        } finally {
            if (!dismissed) {
                predictiveBackProgress.snapTo(0f)
                predictiveBackSwipeEdge = BackEventCompat.EDGE_NONE
                predictiveBackTouchY = 0f
            }
        }
    }

    return GuideFullscreenBackGestureState(
        motionValues = {
            resolveBackGestureMotion(
                progress = predictiveBackProgress.value,
                containerWidthPx = dialogWidthPx,
                containerHeightPx = dialogHeightPx,
                swipeEdge = predictiveBackSwipeEdge,
                touchY = predictiveBackTouchY,
                config = motionConfig,
            )
        },
        onDialogSizeChanged = { width, height ->
            dialogWidthPx = width
            dialogHeightPx = height
        }
    )
}
