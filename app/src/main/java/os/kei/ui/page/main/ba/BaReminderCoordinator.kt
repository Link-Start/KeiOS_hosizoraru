package os.kei.ui.page.main.ba

import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs
import os.kei.ui.page.main.ba.support.displayAp

internal object BaReminderCoordinator {
    fun evaluateApThreshold(
        snapshot: BaPageSnapshot,
        nowMs: Long
    ): BaApReminderPlan {
        if (!snapshot.apNotifyEnabled) {
            return BaApReminderPlan(resetLastNotifiedLevel = true)
        }

        val (nextAp, nextBase) = applyBaApRegenTick(
            apLimit = snapshot.apLimit,
            apCurrent = snapshot.apCurrent,
            apRegenBaseMs = snapshot.apRegenBaseMs,
            nowMs = nowMs
        )
        val currentDisplay = displayAp(nextAp)
        val threshold = snapshot.apNotifyThreshold.coerceIn(0, BA_AP_MAX)
        val shouldSaveAp = nextAp != snapshot.apCurrent || nextBase != snapshot.apRegenBaseMs
        if (currentDisplay < threshold) {
            return BaApReminderPlan(
                nextAp = nextAp,
                nextApRegenBaseMs = nextBase,
                shouldSaveAp = shouldSaveAp,
                resetLastNotifiedLevel = true
            )
        }
        if (currentDisplay == snapshot.apLastNotifiedLevel) {
            return BaApReminderPlan(
                nextAp = nextAp,
                nextApRegenBaseMs = nextBase,
                shouldSaveAp = shouldSaveAp
            )
        }

        return BaApReminderPlan(
            nextAp = nextAp,
            nextApRegenBaseMs = nextBase,
            shouldSaveAp = shouldSaveAp,
            notification = BaApReminderNotification(
                currentDisplay = currentDisplay,
                limitDisplay = snapshot.apLimit.coerceIn(0, BA_AP_MAX),
                thresholdDisplay = threshold
            )
        )
    }

    fun evaluateCafeApThreshold(
        snapshot: BaPageSnapshot,
        nowMs: Long
    ): BaCafeApReminderPlan {
        if (!snapshot.cafeApNotifyEnabled) {
            return BaCafeApReminderPlan(resetLastNotifiedLevel = true)
        }

        val (nextStoredAp, nextHourMs) = applyBaCafeStorageTick(
            cafeStoredAp = snapshot.cafeStoredAp,
            cafeLevel = snapshot.cafeLevel,
            cafeLastHourMs = snapshot.cafeLastHourMs,
            nowMs = nowMs
        )
        val capDisplay = displayAp(cafeStorageCap(snapshot.cafeLevel))
        val currentDisplay = displayAp(nextStoredAp)
        val threshold = snapshot.cafeApNotifyThreshold.coerceIn(0, capDisplay)
        val shouldSaveCafe = nextStoredAp != snapshot.cafeStoredAp || nextHourMs != snapshot.cafeLastHourMs
        if (currentDisplay < threshold) {
            return BaCafeApReminderPlan(
                nextStoredAp = nextStoredAp,
                nextCafeLastHourMs = nextHourMs,
                shouldSaveCafe = shouldSaveCafe,
                resetLastNotifiedLevel = true
            )
        }
        if (currentDisplay == snapshot.cafeApLastNotifiedLevel) {
            return BaCafeApReminderPlan(
                nextStoredAp = nextStoredAp,
                nextCafeLastHourMs = nextHourMs,
                shouldSaveCafe = shouldSaveCafe
            )
        }

        return BaCafeApReminderPlan(
            nextStoredAp = nextStoredAp,
            nextCafeLastHourMs = nextHourMs,
            shouldSaveCafe = shouldSaveCafe,
            notification = BaCafeApReminderNotification(
                currentDisplay = currentDisplay,
                limitDisplay = capDisplay,
                thresholdDisplay = threshold
            )
        )
    }

    fun evaluateCafeVisit(
        snapshot: BaPageSnapshot,
        nowMs: Long
    ): BaSlotReminderPlan {
        if (!snapshot.cafeVisitNotifyEnabled) return BaSlotReminderPlan.Reset
        val currentSlotMs = currentCafeStudentRefreshSlotMs(
            nowMs = nowMs,
            serverIndex = snapshot.serverIndex
        )
        return evaluateSlot(
            lastSlotMs = snapshot.cafeVisitLastNotifiedSlotMs,
            currentSlotMs = currentSlotMs
        )
    }

    fun evaluateArenaRefresh(
        snapshot: BaPageSnapshot,
        nowMs: Long
    ): BaSlotReminderPlan {
        if (!snapshot.arenaRefreshNotifyEnabled) return BaSlotReminderPlan.Reset
        val currentSlotMs = currentArenaRefreshSlotMs(
            nowMs = nowMs,
            serverIndex = snapshot.serverIndex
        )
        return evaluateSlot(
            lastSlotMs = snapshot.arenaRefreshLastNotifiedSlotMs,
            currentSlotMs = currentSlotMs
        )
    }

    private fun evaluateSlot(
        lastSlotMs: Long,
        currentSlotMs: Long
    ): BaSlotReminderPlan {
        if (lastSlotMs <= 0L) return BaSlotReminderPlan.SeedBaseline(currentSlotMs)
        if (currentSlotMs <= lastSlotMs) return BaSlotReminderPlan.None
        return BaSlotReminderPlan.Notify(currentSlotMs)
    }

    fun calendarUpcomingGroups(
        entries: List<BaCalendarEntry>,
        nowMs: Long,
        serverIndex: Int,
        leadHours: Int,
        notifiedKeys: Set<String>
    ): List<BaReminderEventGroup<BaCalendarEntry>> {
        return entries.asSequence()
            .filter { it.beginAtMs > nowMs && it.beginAtMs - nowMs <= leadMs(leadHours) }
            .sortedBy { it.beginAtMs }
            .toReminderGroups(
                serverIndex = serverIndex,
                type = "calendar_start",
                leadHours = leadHours,
                notifiedKeys = notifiedKeys,
                idOf = { it.id },
                atMsOf = { it.beginAtMs }
            )
    }

    fun calendarEndingGroups(
        entries: List<BaCalendarEntry>,
        nowMs: Long,
        serverIndex: Int,
        leadHours: Int,
        notifiedKeys: Set<String>
    ): List<BaReminderEventGroup<BaCalendarEntry>> {
        return entries.asSequence()
            .filter { it.isRunning && it.endAtMs > nowMs && it.endAtMs - nowMs <= leadMs(leadHours) }
            .sortedBy { it.endAtMs }
            .toReminderGroups(
                serverIndex = serverIndex,
                type = "calendar_end",
                leadHours = leadHours,
                notifiedKeys = notifiedKeys,
                idOf = { it.id },
                atMsOf = { it.endAtMs }
            )
    }

    fun poolUpcomingGroups(
        entries: List<BaPoolEntry>,
        nowMs: Long,
        serverIndex: Int,
        leadHours: Int,
        notifiedKeys: Set<String>
    ): List<BaReminderEventGroup<BaPoolEntry>> {
        return entries.asSequence()
            .filter { it.startAtMs > nowMs && it.startAtMs - nowMs <= leadMs(leadHours) }
            .sortedBy { it.startAtMs }
            .toReminderGroups(
                serverIndex = serverIndex,
                type = "pool_start",
                leadHours = leadHours,
                notifiedKeys = notifiedKeys,
                idOf = { it.id },
                atMsOf = { it.startAtMs }
            )
    }

    fun poolEndingGroups(
        entries: List<BaPoolEntry>,
        nowMs: Long,
        serverIndex: Int,
        leadHours: Int,
        notifiedKeys: Set<String>
    ): List<BaReminderEventGroup<BaPoolEntry>> {
        return entries.asSequence()
            .filter { it.isRunning && it.endAtMs > nowMs && it.endAtMs - nowMs <= leadMs(leadHours) }
            .sortedBy { it.endAtMs }
            .toReminderGroups(
                serverIndex = serverIndex,
                type = "pool_end",
                leadHours = leadHours,
                notifiedKeys = notifiedKeys,
                idOf = { it.id },
                atMsOf = { it.endAtMs }
            )
    }

    fun changeKey(
        serverIndex: Int,
        type: String,
        changedCount: Int,
        fingerprint: Long
    ): String {
        return BaReminderKey(
            serverIndex = serverIndex,
            type = type,
            id = changedCount,
            atMs = fingerprint,
            leadHours = 0
        ).encode()
    }

    fun retainNotifiedKeysForPolicy(
        keys: Set<String>,
        serverIndex: Int,
        leadHours: Int,
        calendarUpcomingEnabled: Boolean,
        calendarEndingEnabled: Boolean,
        poolUpcomingEnabled: Boolean,
        poolEndingEnabled: Boolean,
        calendarPoolChangeEnabled: Boolean
    ): Set<String> {
        val normalizedServerIndex = serverIndex.coerceIn(0, 2)
        val normalizedLeadHours = leadHours.coerceAtLeast(1)
        val allowedTypes = buildSet {
            if (calendarUpcomingEnabled) add(TYPE_CALENDAR_START)
            if (calendarEndingEnabled) add(TYPE_CALENDAR_END)
            if (poolUpcomingEnabled) add(TYPE_POOL_START)
            if (poolEndingEnabled) add(TYPE_POOL_END)
            if (calendarPoolChangeEnabled) {
                add(TYPE_CALENDAR_CHANGE)
                add(TYPE_POOL_CHANGE)
            }
        }
        if (allowedTypes.isEmpty()) return emptySet()
        return keys.mapNotNull { raw ->
            decodeReminderKey(raw)?.takeIf { key ->
                key.serverIndex == normalizedServerIndex &&
                        key.type in allowedTypes &&
                        (key.isChangeKey || key.leadHours == normalizedLeadHours)
            }?.encoded
        }.toCollection(LinkedHashSet())
    }

    internal fun decodeReminderKey(raw: String): BaReminderKeyParts? {
        val parts = raw.trim().split("|")
        if (parts.size != 5) return null
        val serverIndex = parts[0].toIntOrNull()?.coerceIn(0, 2) ?: return null
        val type = parts[1].trim()
        if (type.isBlank()) return null
        val id = parts[2].toIntOrNull() ?: return null
        val atMs = parts[3].toLongOrNull()?.coerceAtLeast(0L) ?: return null
        val leadHours = parts[4].toIntOrNull()?.coerceAtLeast(0) ?: return null
        val encoded = BaReminderKey(
            serverIndex = serverIndex,
            type = type,
            id = id,
            atMs = atMs,
            leadHours = leadHours
        ).encode()
        return BaReminderKeyParts(
            serverIndex = serverIndex,
            type = type,
            id = id,
            atMs = atMs,
            leadHours = leadHours,
            encoded = encoded
        )
    }

    private fun leadMs(leadHours: Int): Long {
        return leadHours.coerceAtLeast(1) * 60L * 60L * 1000L
    }

    private const val TYPE_CALENDAR_START = "calendar_start"
    private const val TYPE_CALENDAR_END = "calendar_end"
    private const val TYPE_POOL_START = "pool_start"
    private const val TYPE_POOL_END = "pool_end"
    private const val TYPE_CALENDAR_CHANGE = "calendar_change"
    private const val TYPE_POOL_CHANGE = "pool_change"

    private fun <T> Sequence<T>.toReminderGroups(
        serverIndex: Int,
        type: String,
        leadHours: Int,
        notifiedKeys: Set<String>,
        idOf: (T) -> Int,
        atMsOf: (T) -> Long
    ): List<BaReminderEventGroup<T>> {
        return map { entry ->
            val atMs = atMsOf(entry)
            val key = BaReminderKey(
                serverIndex = serverIndex,
                type = type,
                id = idOf(entry),
                atMs = atMs,
                leadHours = leadHours
            ).encode()
            BaReminderEvent(entry = entry, atMs = atMs, key = key)
        }
            .filter { it.key !in notifiedKeys }
            .groupBy { it.atMs }
            .map { (_, events) ->
                BaReminderEventGroup(
                    atMs = events.firstOrNull()?.atMs ?: 0L,
                    entries = events.map { it.entry },
                    keys = events.map { it.key }
                )
            }
    }
}

internal data class BaApReminderPlan(
    val nextAp: Double = 0.0,
    val nextApRegenBaseMs: Long = 0L,
    val shouldSaveAp: Boolean = false,
    val resetLastNotifiedLevel: Boolean = false,
    val notification: BaApReminderNotification? = null
)

internal data class BaApReminderNotification(
    val currentDisplay: Int,
    val limitDisplay: Int,
    val thresholdDisplay: Int
)

internal data class BaCafeApReminderPlan(
    val nextStoredAp: Double = 0.0,
    val nextCafeLastHourMs: Long = 0L,
    val shouldSaveCafe: Boolean = false,
    val resetLastNotifiedLevel: Boolean = false,
    val notification: BaCafeApReminderNotification? = null
)

internal data class BaCafeApReminderNotification(
    val currentDisplay: Int,
    val limitDisplay: Int,
    val thresholdDisplay: Int
)

internal sealed interface BaSlotReminderPlan {
    data object None : BaSlotReminderPlan
    data object Reset : BaSlotReminderPlan
    data class SeedBaseline(val slotMs: Long) : BaSlotReminderPlan
    data class Notify(val slotMs: Long) : BaSlotReminderPlan
}

internal data class BaReminderKey(
    val serverIndex: Int,
    val type: String,
    val id: Int,
    val atMs: Long,
    val leadHours: Int
) {
    fun encode(): String {
        return "${serverIndex.coerceIn(0, 2)}|${type.trim()}|$id|${atMs.coerceAtLeast(0L)}|${
            leadHours.coerceAtLeast(0)
        }"
    }
}

internal data class BaReminderEventGroup<T>(
    val atMs: Long,
    val entries: List<T>,
    val keys: List<String>
)

internal data class BaReminderKeyParts(
    val serverIndex: Int,
    val type: String,
    val id: Int,
    val atMs: Long,
    val leadHours: Int,
    val encoded: String
) {
    val isChangeKey: Boolean
        get() = type == "calendar_change" || type == "pool_change"
}

private data class BaReminderEvent<T>(
    val entry: T,
    val atMs: Long,
    val key: String
)
