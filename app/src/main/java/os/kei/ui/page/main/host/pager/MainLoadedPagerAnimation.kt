package os.kei.ui.page.main.host.pager

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.withFrameNanos

internal suspend fun animateLoadedPagerPosition(
    start: Float,
    target: Float,
    durationMillis: Int,
    onFrame: (Float) -> Unit
) {
    val durationNanos = durationMillis.coerceAtLeast(1) * 1_000_000L
    val startNanos = withFrameNanos { it }
    var lastValue = start
    while (true) {
        val frameNanos = withFrameNanos { it }
        val linearProgress = ((frameNanos - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
        val easedProgress = FastOutSlowInEasing.transform(linearProgress)
        val nextValue = start + (target - start) * easedProgress
        if (nextValue != lastValue) {
            onFrame(nextValue)
            lastValue = nextValue
        }
        if (linearProgress >= 1f) break
    }
    onFrame(target)
}
