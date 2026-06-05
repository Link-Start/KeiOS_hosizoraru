package os.kei.ui.page.main.back

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class BackGestureMotionConfig(
    val translationFactor: Float,
    val contentFadeFactor: Float,
    val scrimFadeFactor: Float,
    val minScale: Float = 0.965f,
    val pivotEdgeBias: Float = 0.84f,
)

internal data class BackGestureMotionValues(
    val progress: Float,
    val translationX: Float,
    val contentAlpha: Float,
    val scrimAlpha: Float,
    val scale: Float,
    val pivotX: Float,
    val pivotY: Float,
)

internal fun resolveBackGestureMotion(
    progress: Float,
    containerWidthPx: Int,
    containerHeightPx: Int,
    swipeEdge: Int,
    touchY: Float,
    config: BackGestureMotionConfig,
): BackGestureMotionValues {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val edgeDirection = when (swipeEdge) {
        BackEventCompat.EDGE_LEFT -> 1f
        BackEventCompat.EDGE_RIGHT -> -1f
        else -> 0f
    }
    val pivotX = when (swipeEdge) {
        BackEventCompat.EDGE_LEFT -> config.pivotEdgeBias
        BackEventCompat.EDGE_RIGHT -> 1f - config.pivotEdgeBias
        else -> 0.5f
    }
    val pivotY =
        if (containerHeightPx > 0) {
            (touchY / containerHeightPx.toFloat()).coerceIn(0.14f, 0.86f)
        } else {
            0.5f
        }
    val visualProgress = resolveBackGestureVisualProgress(clampedProgress)
    val translationX =
        containerWidthPx.toFloat() * config.translationFactor * edgeDirection * clampedProgress
    val scale = 1f - ((1f - config.minScale) * visualProgress)
    val contentAlpha = (1f - visualProgress * config.contentFadeFactor).coerceIn(0f, 1f)
    val scrimAlpha = (1f - visualProgress * config.scrimFadeFactor).coerceIn(0f, 1f)

    return BackGestureMotionValues(
        progress = clampedProgress,
        translationX = translationX,
        contentAlpha = contentAlpha,
        scrimAlpha = scrimAlpha,
        scale = scale,
        pivotX = pivotX,
        pivotY = pivotY,
    )
}

internal fun resolveBackGestureVisualProgress(progress: Float): Float =
    BackGestureVisualEasing.transform(progress.coerceIn(0f, 1f)).coerceIn(0f, 1f)

internal fun resolveBackGestureSettleDurationMillis(
    currentProgress: Float,
    targetProgress: Float,
    maxDurationMillis: Int,
): Int {
    val distance = abs(targetProgress.coerceIn(0f, 1f) - currentProgress.coerceIn(0f, 1f))
    if (distance <= BACK_GESTURE_SETTLE_EPSILON) return 0
    return (maxDurationMillis * distance)
        .roundToInt()
        .coerceIn(BACK_GESTURE_MIN_SETTLE_DURATION_MS, maxDurationMillis)
}

internal suspend fun Animatable<Float, AnimationVector1D>.settleBackGestureProgress(
    targetProgress: Float,
    maxDurationMillis: Int,
) {
    val clampedTarget = targetProgress.coerceIn(0f, 1f)
    val durationMillis =
        resolveBackGestureSettleDurationMillis(
            currentProgress = value,
            targetProgress = clampedTarget,
            maxDurationMillis = maxDurationMillis,
        )
    if (durationMillis == 0) {
        snapTo(clampedTarget)
    } else {
        animateTo(
            targetValue = clampedTarget,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = BackGestureSettleEasing,
            ),
        )
    }
}

internal const val BACK_GESTURE_COMMIT_SETTLE_DURATION_MS = 500
internal const val BACK_GESTURE_CANCEL_SETTLE_DURATION_MS = 260

private const val BACK_GESTURE_MIN_SETTLE_DURATION_MS = 128
private const val BACK_GESTURE_SETTLE_EPSILON = 0.001f
private val BackGestureVisualEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val BackGestureSettleEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
