package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.R
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry

internal fun sampleCalendarEntry(
    context: Context,
    upcoming: Boolean,
    nowMs: Long,
): BaCalendarEntry {
    val startAtMs = if (upcoming) nowMs + ONE_HOUR_MS else nowMs - TWO_HOURS_MS
    val endAtMs = if (upcoming) nowMs + THREE_HOURS_MS else nowMs + ONE_HOUR_MS
    return BaCalendarEntry(
        id = -10_001,
        title = context.resolveString(R.string.ba_debug_sample_calendar_title),
        kindId = 14,
        kindName = "",
        beginAtMs = startAtMs,
        endAtMs = endAtMs,
        linkUrl = "",
        imageUrl = "",
        isRunning = !upcoming,
    )
}

internal fun samplePoolEntry(
    context: Context,
    upcoming: Boolean,
    nowMs: Long,
): BaPoolEntry {
    val startAtMs = if (upcoming) nowMs + ONE_HOUR_MS else nowMs - TWO_HOURS_MS
    val endAtMs = if (upcoming) nowMs + THREE_HOURS_MS else nowMs + ONE_HOUR_MS
    return BaPoolEntry(
        id = -10_002,
        name = context.resolveString(R.string.ba_debug_sample_pool_title),
        tagId = 6,
        tagName = "",
        startAtMs = startAtMs,
        endAtMs = endAtMs,
        linkUrl = "",
        imageUrl = "",
        isRunning = !upcoming,
    )
}

internal fun resolveCalendarDebugEntries(
    context: Context,
    entries: List<BaCalendarEntry>,
    useRealData: Boolean,
    upcoming: Boolean,
    nowMs: Long,
): List<BaCalendarEntry>? {
    if (!useRealData) return listOf(sampleCalendarEntry(context, upcoming, nowMs))
    val targetTime =
        if (upcoming) {
            entries
                .filter { it.beginAtMs > nowMs }
                .minByOrNull { it.beginAtMs }
                ?.beginAtMs
        } else {
            entries
                .filter { it.endAtMs > nowMs }
                .minByOrNull { it.endAtMs }
                ?.endAtMs
        }
    return when {
        targetTime == null -> null
        upcoming -> entries.filter { it.beginAtMs == targetTime }
        else -> entries.filter { it.endAtMs == targetTime }
    }
}

internal fun resolvePoolDebugEntries(
    context: Context,
    entries: List<BaPoolEntry>,
    useRealData: Boolean,
    upcoming: Boolean,
    nowMs: Long,
): List<BaPoolEntry>? {
    if (!useRealData) return listOf(samplePoolEntry(context, upcoming, nowMs))
    val targetTime =
        if (upcoming) {
            entries
                .filter { it.startAtMs > nowMs }
                .minByOrNull { it.startAtMs }
                ?.startAtMs
        } else {
            entries
                .filter { it.endAtMs > nowMs }
                .minByOrNull { it.endAtMs }
                ?.endAtMs
        }
    return when {
        targetTime == null -> null
        upcoming -> entries.filter { it.startAtMs == targetTime }
        else -> entries.filter { it.endAtMs == targetTime }
    }
}

internal fun resolveRealChangeDebugDetail(
    calendarEntries: List<BaCalendarEntry>,
    poolEntries: List<BaPoolEntry>,
    nowMs: Long,
): String {
    val calendarTitle =
        calendarEntries
            .filter { it.endAtMs > nowMs }
            .minByOrNull { if (it.beginAtMs > nowMs) it.beginAtMs else it.endAtMs }
            ?.title
            .orEmpty()
    val poolTitle =
        poolEntries
            .filter { it.endAtMs > nowMs }
            .minByOrNull { if (it.startAtMs > nowMs) it.startAtMs else it.endAtMs }
            ?.name
            .orEmpty()
    return listOf(calendarTitle, poolTitle)
        .filter { it.isNotBlank() }
        .joinToString(separator = " / ")
}

private const val ONE_HOUR_MS = 60L * 60L * 1000L
private const val TWO_HOURS_MS = 2L * ONE_HOUR_MS
private const val THREE_HOURS_MS = 3L * ONE_HOUR_MS
