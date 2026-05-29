// Copyright 2026, KeiOS contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("FunctionName")

package os.kei.core.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.max
import kotlin.math.roundToInt

// ── Constants ────────────────────────────────────────────────────────────────

/** Rolling window duration in nanoseconds (5 seconds). */
private const val WINDOW_NS = 5_000_000_000L

/** Maximum samples in the deque (~240 Hz × 5 s). */
private const val MAX_SAMPLES = 1_200

/** Minimum samples before showing any stats (~0.5 s at 60 Hz). */
private const val MIN_SAMPLES = 30

/** Stats refresh interval in nanoseconds (2 Hz). */
private const val REFRESH_INTERVAL_NS = 500_000_000L

/** Frame gaps longer than this are treated as idle and discarded. */
private const val IDLE_THRESHOLD_NS = 500_000_000L

/** Best-FPS history window in refresh ticks (~30 seconds at 2 Hz). */
private const val REF_HISTORY_MAX = 60

private const val NS_PER_SECOND = 1_000_000_000.0

// ── Data ─────────────────────────────────────────────────────────────────────

internal data class FpsStats(val avg: Float, val low1pct: Float)

// ── Measurement ──────────────────────────────────────────────────────────────

/**
 * Measures Compose frame deltas via [withFrameNanos] and computes rolling
 * average FPS and 1% low FPS. Returns [FpsStats] updated at 2 Hz.
 */
@Composable
internal fun rememberFpsStats(): FpsStats {
    var stats by remember { mutableStateOf(FpsStats(0f, 0f)) }
    LaunchedEffect(Unit) {
        val deltas = ArrayDeque<Long>(MAX_SAMPLES)
        var sumNs = 0L
        var prevNanos = -1L
        var lastRefresh = 0L
        var totalNs = 0L

        while (true) {
            val now = withFrameNanos { it }

            // First frame: just record timestamp.
            if (prevNanos < 0) {
                prevNanos = now
                lastRefresh = now
                continue
            }

            val delta = now - prevNanos
            prevNanos = now

            // Discard idle gaps.
            if (delta > IDLE_THRESHOLD_NS) continue

            // Evict old samples to maintain the rolling window.
            while (deltas.size >= MAX_SAMPLES ||
                (deltas.isNotEmpty() && sumNs > WINDOW_NS && deltas.size > MIN_SAMPLES)
            ) {
                sumNs -= deltas.removeFirst()
            }

            deltas.addLast(delta)
            sumNs += delta
            totalNs += delta

            // Refresh stats at 2 Hz.
            if (now - lastRefresh >= REFRESH_INTERVAL_NS) {
                lastRefresh = now
                if (deltas.size >= MIN_SAMPLES) {
                    stats = computeFpsStats(deltas)
                }
            }
        }
    }
    return stats
}

private fun computeFpsStats(deltas: ArrayDeque<Long>): FpsStats {
    // Copy and sort to find the 1% slowest frames.
    val sorted = LongArray(deltas.size)
    var i = 0
    for (d in deltas) sorted[i++] = d
    sorted.sort()

    val size = sorted.size
    var sum = 0L
    for (d in sorted) sum += d
    val avg = (NS_PER_SECOND * size / sum).toFloat()

    // 1% low: average of the slowest 1% of frames (at least 1).
    val lowCount = max(1, size / 100)
    var lowSum = 0L
    for (j in size - lowCount until size) lowSum += sorted[j]
    val low1pct = (NS_PER_SECOND * lowCount / lowSum).toFloat()

    return FpsStats(avg = avg, low1pct = low1pct)
}

// ── Overlay ──────────────────────────────────────────────────────────────────

/**
 * A draggable FPS overlay pill showing average and 1% low FPS.
 * Health-graded colors: green (≥55), yellow (≥40), red (<40).
 * Starts at top-center; drag to reposition.
 */
@Composable
fun DebugFpsOverlay(
    modifier: Modifier = Modifier,
    refFps: Float = 60f,
) {
    val stats = rememberFpsStats()
    if (stats.avg <= 0f) return

    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(
        modifier =
            modifier
                .onSizeChanged { parentSize = it }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset =
                            Offset(
                                x = (offset.x + dragAmount.x)
                                    .coerceIn(
                                        -parentSize.width / 2f + 40f,
                                        parentSize.width / 2f - 40f,
                                    ),
                                y = (offset.y + dragAmount.y)
                                    .coerceIn(0f, parentSize.height.toFloat() - 40f),
                            )
                    }
                },
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier =
                Modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .background(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = fpsColor(stats.avg, refFps), fontSize = 11.sp)) {
                        append("AVG ")
                    }
                    withStyle(
                        SpanStyle(
                            color = fpsColor(stats.avg, refFps),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    ) {
                        append(stats.avg.roundToInt().toString())
                    }
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)) {
                        append("  ")
                    }
                    withStyle(SpanStyle(color = fpsColor(stats.low1pct, refFps), fontSize = 11.sp)) {
                        append("1%L ")
                    }
                    withStyle(
                        SpanStyle(
                            color = fpsColor(stats.low1pct, refFps),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    ) {
                        append(stats.low1pct.roundToInt().toString())
                    }
                },
            )
        }
    }
}

private fun fpsColor(fps: Float, refFps: Float): Color {
    val ratio = fps / refFps
    return when {
        ratio >= 0.92f -> Color(0xFF4CAF50) // green
        ratio >= 0.67f -> Color(0xFFFFC107) // yellow
        else -> Color(0xFFF44336) // red
    }
}
