package os.kei.ui.page.main.host.pager

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import kotlin.math.abs
import kotlin.math.sqrt

internal suspend fun animateLoadedPagerPosition(
    start: Float,
    target: Float,
    onFrame: (Float) -> Unit
) {
    animate(
        initialValue = start,
        targetValue = target,
        animationSpec =
            spring(
                dampingRatio = PagerNavigationSpringSpec.dampingRatio,
                stiffness = PagerNavigationSpringSpec.stiffness,
                visibilityThreshold = MainLoadedPagerNavigationVisibilityThreshold,
            ),
    ) { value, _ ->
        onFrame(value)
    }
}

internal suspend fun animateLoadedPagerSettlePosition(
    start: Float,
    target: Float,
    gestureVelocityPagesPerSecond: Float,
    onFrame: (Float) -> Unit,
) {
    animate(
        initialValue = start,
        targetValue = target,
        initialVelocity =
            resolveLoadedPagerSettleInitialVelocity(
                start = start,
                target = target,
                gestureVelocityPagesPerSecond = gestureVelocityPagesPerSecond,
            ),
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = MainLoadedPagerSettleStiffness,
                visibilityThreshold = MainLoadedPagerSettleVisibilityThreshold,
            ),
    ) { value, _ ->
        onFrame(value)
    }
}

/**
 * Keeps gesture release velocity continuous while preventing a short remaining distance from
 * carrying enough momentum to cross the destination and visibly correct back.
 */
internal fun resolveLoadedPagerSettleInitialVelocity(
    start: Float,
    target: Float,
    gestureVelocityPagesPerSecond: Float,
): Float {
    if (!start.isFinite() || !target.isFinite() || !gestureVelocityPagesPerSecond.isFinite()) {
        return 0f
    }
    val remainingDistance = target - start
    if (abs(remainingDistance) <= MainLoadedPagerSettleVisibilityThreshold) return 0f

    val targetDirectedVelocity =
        if (remainingDistance > 0f) {
            gestureVelocityPagesPerSecond.coerceAtLeast(0f)
        } else {
            gestureVelocityPagesPerSecond.coerceAtMost(0f)
        }
    val distanceLimitedVelocity =
        (abs(remainingDistance) * sqrt(MainLoadedPagerSettleStiffness))
            .coerceAtMost(MainLoadedPagerMaximumSettleVelocity)
    return targetDirectedVelocity.coerceIn(-distanceLimitedVelocity, distanceLimitedVelocity)
}

private const val MainLoadedPagerSettleStiffness = 1_200f
private const val MainLoadedPagerSettleVisibilityThreshold = 0.001f
private const val MainLoadedPagerMaximumSettleVelocity = 4.5f

/** Miuix's own threshold is half a pixel of travel; this is about that on a phone-width page. */
private const val MainLoadedPagerNavigationVisibilityThreshold = 0.0005f
