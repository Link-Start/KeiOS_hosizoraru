package os.kei.ui.page.main.ba

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_TICK_MS
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

internal data class BaRuntimeTickerFrame(
    val nowMs: Long,
    val applyRuntimeTick: Boolean,
    val updateUiNow: Boolean,
    val updateUiMinute: Boolean
)

internal class BaRuntimeTickerCoordinator(
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        isPageActive: () -> Boolean,
        isScrollInProgress: () -> Boolean,
        onFrame: suspend (BaRuntimeTickerFrame) -> Unit,
    ) {
        var lastRuntimeTickMs = 0L
        var lastUiNowMs = 0L
        var lastUiMinuteBucket = -1L
        while (currentCoroutineContext().isActive) {
            val active = isPageActive()
            val nowMs = nowProvider()
            if (!active) {
                delay(BA_RUNTIME_TICKER_OFFSCREEN_DELAY_MS.milliseconds)
                continue
            }

            val scrolling = isScrollInProgress()
            val minuteBucket = nowMs / BA_UI_MINUTE_TICK_MS
            val applyRuntimeTick = lastRuntimeTickMs == 0L ||
                nowMs - lastRuntimeTickMs >= BA_AP_REGEN_TICK_MS
            val updateUiNow = !scrolling &&
                (lastUiNowMs == 0L || nowMs - lastUiNowMs >= BA_UI_SECOND_TICK_MS)
            val updateUiMinute = !scrolling && minuteBucket != lastUiMinuteBucket

            if (applyRuntimeTick || updateUiNow || updateUiMinute) {
                onFrame(
                    BaRuntimeTickerFrame(
                        nowMs = nowMs,
                        applyRuntimeTick = applyRuntimeTick,
                        updateUiNow = updateUiNow,
                        updateUiMinute = updateUiMinute,
                    )
                )
                if (applyRuntimeTick) lastRuntimeTickMs = nowMs
                if (updateUiNow) lastUiNowMs = nowMs
                if (updateUiMinute) lastUiMinuteBucket = minuteBucket
            }

            delay(resolveNextDelayMs(nowMs, scrolling).milliseconds)
        }
    }

    private fun resolveNextDelayMs(nowMs: Long, scrolling: Boolean): Long {
        if (scrolling) return BA_RUNTIME_TICKER_SCROLL_DELAY_MS
        val nextSecondDelay = BA_UI_SECOND_TICK_MS - (nowMs % BA_UI_SECOND_TICK_MS)
        val nextMinuteDelay = baUiMinuteTickDelayMs(nowMs)
        return min(nextSecondDelay, nextMinuteDelay)
            .coerceIn(BA_RUNTIME_TICKER_MIN_DELAY_MS, BA_UI_SECOND_TICK_MS)
    }
}

@Composable
internal fun rememberBaRuntimeTickerCoordinator(): BaRuntimeTickerCoordinator {
    return remember { BaRuntimeTickerCoordinator() }
}

private const val BA_RUNTIME_TICKER_SCROLL_DELAY_MS = 250L
private const val BA_RUNTIME_TICKER_OFFSCREEN_DELAY_MS = 5_000L
private const val BA_RUNTIME_TICKER_MIN_DELAY_MS = 250L
