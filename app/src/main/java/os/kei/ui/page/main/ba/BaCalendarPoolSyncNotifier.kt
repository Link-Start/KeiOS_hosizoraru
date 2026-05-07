package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry

internal object BaCalendarPoolSyncNotifier {
    fun dispatchCalendarSyncNotifications(
        context: Context,
        serverIndex: Int,
        previousEntries: List<BaCalendarEntry>,
        nextEntries: List<BaCalendarEntry>,
        nowMs: Long,
        hadCache: Boolean,
    ) {
        val leadHours = BASettingsStore.loadCalendarPoolNotifyLeadHours()
        val notifiedKeys = BASettingsStore.loadCalendarPoolNotifiedKeys()

        if (BASettingsStore.loadCalendarUpcomingNotifyEnabled()) {
            val groups = BaReminderCoordinator.calendarUpcomingGroups(
                entries = nextEntries,
                nowMs = nowMs,
                serverIndex = serverIndex,
                leadHours = leadHours,
                notifiedKeys = notifiedKeys
            )
            groups.forEach { group ->
                if (BaCalendarPoolNotificationDispatcher.sendCalendarUpcomingGroup(
                        context,
                        serverIndex,
                        group.entries
                    )
                ) {
                    group.keys.forEach(BASettingsStore::markCalendarPoolNotified)
                }
            }
        }

        if (BASettingsStore.loadCalendarEndingNotifyEnabled()) {
            val groups = BaReminderCoordinator.calendarEndingGroups(
                entries = nextEntries,
                nowMs = nowMs,
                serverIndex = serverIndex,
                leadHours = leadHours,
                notifiedKeys = notifiedKeys
            )
            groups.forEach { group ->
                if (BaCalendarPoolNotificationDispatcher.sendCalendarEndingGroup(
                        context,
                        serverIndex,
                        group.entries
                    )
                ) {
                    group.keys.forEach(BASettingsStore::markCalendarPoolNotified)
                }
            }
        }

        if (hadCache && BASettingsStore.loadCalendarPoolChangeNotifyEnabled()) {
            val changedCount = countCalendarEntryChanges(previousEntries, nextEntries)
            val changeKey = BaReminderCoordinator.changeKey(
                serverIndex = serverIndex,
                type = "calendar_change",
                changedCount = changedCount,
                fingerprint = calendarEntriesFingerprint(nextEntries)
            )
            if (changedCount > 0 &&
                changeKey !in notifiedKeys &&
                BaCalendarPoolNotificationDispatcher.sendDataChanged(
                    context = context,
                    calendarChangeCount = changedCount,
                    poolChangeCount = 0,
                    detail = firstChangedCalendarTitle(previousEntries, nextEntries)
                )
            ) {
                BASettingsStore.markCalendarPoolNotified(changeKey)
            }
        }
    }

    fun dispatchPoolSyncNotifications(
        context: Context,
        serverIndex: Int,
        previousEntries: List<BaPoolEntry>,
        nextEntries: List<BaPoolEntry>,
        nowMs: Long,
        hadCache: Boolean,
    ) {
        val leadHours = BASettingsStore.loadCalendarPoolNotifyLeadHours()
        val notifiedKeys = BASettingsStore.loadCalendarPoolNotifiedKeys()

        if (BASettingsStore.loadPoolUpcomingNotifyEnabled()) {
            val groups = BaReminderCoordinator.poolUpcomingGroups(
                entries = nextEntries,
                nowMs = nowMs,
                serverIndex = serverIndex,
                leadHours = leadHours,
                notifiedKeys = notifiedKeys
            )
            groups.forEach { group ->
                if (BaCalendarPoolNotificationDispatcher.sendPoolUpcomingGroup(
                        context,
                        serverIndex,
                        group.entries
                    )
                ) {
                    group.keys.forEach(BASettingsStore::markCalendarPoolNotified)
                }
            }
        }

        if (BASettingsStore.loadPoolEndingNotifyEnabled()) {
            val groups = BaReminderCoordinator.poolEndingGroups(
                entries = nextEntries,
                nowMs = nowMs,
                serverIndex = serverIndex,
                leadHours = leadHours,
                notifiedKeys = notifiedKeys
            )
            groups.forEach { group ->
                if (BaCalendarPoolNotificationDispatcher.sendPoolEndingGroup(
                        context,
                        serverIndex,
                        group.entries
                    )
                ) {
                    group.keys.forEach(BASettingsStore::markCalendarPoolNotified)
                }
            }
        }

        if (hadCache && BASettingsStore.loadCalendarPoolChangeNotifyEnabled()) {
            val changedCount = countPoolEntryChanges(previousEntries, nextEntries)
            val changeKey = BaReminderCoordinator.changeKey(
                serverIndex = serverIndex,
                type = "pool_change",
                changedCount = changedCount,
                fingerprint = poolEntriesFingerprint(nextEntries)
            )
            if (changedCount > 0 &&
                changeKey !in notifiedKeys &&
                BaCalendarPoolNotificationDispatcher.sendDataChanged(
                    context = context,
                    calendarChangeCount = 0,
                    poolChangeCount = changedCount,
                    detail = firstChangedPoolTitle(previousEntries, nextEntries)
                )
            ) {
                BASettingsStore.markCalendarPoolNotified(changeKey)
            }
        }
    }

    private fun calendarEntriesFingerprint(entries: List<BaCalendarEntry>): Long {
        return entries
            .sortedBy { it.id }
            .joinToString(separator = "\n") { "${it.id}|${it.title}|${it.kindId}|${it.beginAtMs}|${it.endAtMs}|${it.linkUrl}" }
            .hashCode()
            .toLong()
            .and(0xffffffffL)
    }

    private fun poolEntriesFingerprint(entries: List<BaPoolEntry>): Long {
        return entries
            .sortedBy { it.id }
            .joinToString(separator = "\n") { "${it.id}|${it.name}|${it.tagId}|${it.startAtMs}|${it.endAtMs}|${it.linkUrl}" }
            .hashCode()
            .toLong()
            .and(0xffffffffL)
    }

    private fun countCalendarEntryChanges(
        previousEntries: List<BaCalendarEntry>,
        nextEntries: List<BaCalendarEntry>,
    ): Int {
        val previousSignatures = previousEntries.associateBy(
            keySelector = { it.id },
            valueTransform = { "${it.title}|${it.kindId}|${it.beginAtMs}|${it.endAtMs}|${it.linkUrl}" }
        )
        return nextEntries.count { entry ->
            previousSignatures[entry.id] != "${entry.title}|${entry.kindId}|${entry.beginAtMs}|${entry.endAtMs}|${entry.linkUrl}"
        }
    }

    private fun firstChangedCalendarTitle(
        previousEntries: List<BaCalendarEntry>,
        nextEntries: List<BaCalendarEntry>,
    ): String {
        val previousSignatures = previousEntries.associateBy(
            keySelector = { it.id },
            valueTransform = { "${it.title}|${it.kindId}|${it.beginAtMs}|${it.endAtMs}|${it.linkUrl}" }
        )
        return nextEntries.firstOrNull { entry ->
            previousSignatures[entry.id] != "${entry.title}|${entry.kindId}|${entry.beginAtMs}|${entry.endAtMs}|${entry.linkUrl}"
        }?.title.orEmpty()
    }

    private fun countPoolEntryChanges(
        previousEntries: List<BaPoolEntry>,
        nextEntries: List<BaPoolEntry>,
    ): Int {
        val previousSignatures = previousEntries.associateBy(
            keySelector = { it.id },
            valueTransform = { "${it.name}|${it.tagId}|${it.startAtMs}|${it.endAtMs}|${it.linkUrl}" }
        )
        return nextEntries.count { entry ->
            previousSignatures[entry.id] != "${entry.name}|${entry.tagId}|${entry.startAtMs}|${entry.endAtMs}|${entry.linkUrl}"
        }
    }

    private fun firstChangedPoolTitle(
        previousEntries: List<BaPoolEntry>,
        nextEntries: List<BaPoolEntry>,
    ): String {
        val previousSignatures = previousEntries.associateBy(
            keySelector = { it.id },
            valueTransform = { "${it.name}|${it.tagId}|${it.startAtMs}|${it.endAtMs}|${it.linkUrl}" }
        )
        return nextEntries.firstOrNull { entry ->
            previousSignatures[entry.id] != "${entry.name}|${entry.tagId}|${entry.startAtMs}|${entry.endAtMs}|${entry.linkUrl}"
        }?.name.orEmpty()
    }
}
