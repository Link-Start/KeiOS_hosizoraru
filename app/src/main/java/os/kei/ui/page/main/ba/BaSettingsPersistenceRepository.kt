package os.kei.ui.page.main.ba

import org.json.JSONArray
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.normalizeGameKeeImageLink

internal data class BaSettingsPersistenceResult(
    val savedCafeLevel: Int,
    val showEndedPools: Boolean,
    val showEndedActivities: Boolean,
    val showCalendarPoolImages: Boolean,
    val mediaAdaptiveRotationEnabled: Boolean,
    val mediaSaveCustomEnabled: Boolean,
    val mediaSaveFixedTreeUri: String,
    val idIndependentByServer: Boolean,
    val turningEndedActivitiesOn: Boolean,
    val turningImagesOn: Boolean,
)

internal data class BaNotificationSettingsPersistenceResult(
    val savedThreshold: Int,
    val savedCafeApThreshold: Int,
    val cafeApNotifyEnabled: Boolean,
    val arenaRefreshNotifyEnabled: Boolean,
    val cafeVisitNotifyEnabled: Boolean,
    val calendarUpcomingNotifyEnabled: Boolean,
    val calendarEndingNotifyEnabled: Boolean,
    val poolUpcomingNotifyEnabled: Boolean,
    val poolEndingNotifyEnabled: Boolean,
    val calendarPoolChangeNotifyEnabled: Boolean,
    val calendarPoolNotifyLeadHours: Int,
)

internal data class BaRefreshIntervalPersistenceResult(
    val hours: Int,
    val shouldRefresh: Boolean,
)

internal data class BaCalendarPoolNotificationSettingsSnapshot(
    val calendarUpcomingNotifyEnabled: Boolean,
    val calendarEndingNotifyEnabled: Boolean,
    val poolUpcomingNotifyEnabled: Boolean,
    val poolEndingNotifyEnabled: Boolean,
    val calendarPoolChangeNotifyEnabled: Boolean,
    val calendarPoolNotifyLeadHours: Int,
)

internal data class BaCalendarPoolNotificationRuntimeSnapshot(
    val settings: BaCalendarPoolNotificationSettingsSnapshot,
    val notifiedKeys: Set<String>,
)

internal object BaSettingsPersistenceRepository {
    fun hasAnyImageInCalendarCache(serverIdx: Int): Boolean {
        val (raw, _) = BASettingsStore.loadCalendarCache(serverIdx)
        return hasAnyImageInEncodedCache(raw)
    }

    fun hasAnyImageInPoolCache(serverIdx: Int): Boolean {
        val (raw, _) = BASettingsStore.loadPoolCache(serverIdx)
        return hasAnyImageInEncodedCache(raw)
    }

    fun calendarCacheIsBlank(serverIndex: Int): Boolean {
        val (raw, _) = BASettingsStore.loadCalendarCache(serverIndex)
        return raw.isBlank()
    }

    fun loadCalendarPoolNotificationSettings(): BaCalendarPoolNotificationSettingsSnapshot =
        BaCalendarPoolNotificationSettingsSnapshot(
            calendarUpcomingNotifyEnabled = BASettingsStore.loadCalendarUpcomingNotifyEnabled(),
            calendarEndingNotifyEnabled = BASettingsStore.loadCalendarEndingNotifyEnabled(),
            poolUpcomingNotifyEnabled = BASettingsStore.loadPoolUpcomingNotifyEnabled(),
            poolEndingNotifyEnabled = BASettingsStore.loadPoolEndingNotifyEnabled(),
            calendarPoolChangeNotifyEnabled = BASettingsStore.loadCalendarPoolChangeNotifyEnabled(),
            calendarPoolNotifyLeadHours = BASettingsStore.loadCalendarPoolNotifyLeadHours(),
        )

    fun loadCalendarPoolNotificationRuntime(): BaCalendarPoolNotificationRuntimeSnapshot =
        BaCalendarPoolNotificationRuntimeSnapshot(
            settings = loadCalendarPoolNotificationSettings(),
            notifiedKeys = BASettingsStore.loadCalendarPoolNotifiedKeys(),
        )

    fun markCalendarPoolNotified(key: String) {
        BASettingsStore.markCalendarPoolNotified(key)
    }

    fun persistSettingsDraft(
        sheetState: BaSettingsSheetState,
        currentShowEndedActivities: Boolean,
        currentShowCalendarPoolImages: Boolean,
    ): BaSettingsPersistenceResult {
        val savedCafeLevel = sheetState.cafeLevel.coerceIn(1, 10)

        BASettingsStore.saveCafeLevel(savedCafeLevel)
        BASettingsStore.savePoolShowEnded(sheetState.showEndedPools)
        BASettingsStore.saveActivityShowEnded(sheetState.showEndedActivities)
        BASettingsStore.saveShowCalendarPoolImages(sheetState.showCalendarPoolImages)
        BASettingsStore.saveMediaAdaptiveRotationEnabled(sheetState.mediaAdaptiveRotationEnabled)
        BASettingsStore.saveMediaSaveCustomEnabled(sheetState.mediaSaveCustomEnabled)
        BASettingsStore.saveMediaSaveFixedTreeUri(sheetState.mediaSaveFixedTreeUri)
        BASettingsStore.saveIdIndependentByServerEnabled(sheetState.idIndependentByServer)

        return BaSettingsPersistenceResult(
            savedCafeLevel = savedCafeLevel,
            showEndedPools = sheetState.showEndedPools,
            showEndedActivities = sheetState.showEndedActivities,
            showCalendarPoolImages = sheetState.showCalendarPoolImages,
            mediaAdaptiveRotationEnabled = sheetState.mediaAdaptiveRotationEnabled,
            mediaSaveCustomEnabled = sheetState.mediaSaveCustomEnabled,
            mediaSaveFixedTreeUri = sheetState.mediaSaveFixedTreeUri,
            idIndependentByServer = sheetState.idIndependentByServer,
            turningEndedActivitiesOn = !currentShowEndedActivities && sheetState.showEndedActivities,
            turningImagesOn = !currentShowCalendarPoolImages && sheetState.showCalendarPoolImages,
        )
    }

    fun persistNotificationSettingsDraft(sheetState: BaNotificationSettingsSheetState): BaNotificationSettingsPersistenceResult {
        val savedThreshold =
            sheetState.apNotifyThresholdText.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 120
        val savedCafeApThreshold =
            sheetState.cafeApNotifyThresholdText.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 120

        BASettingsStore.saveApNotifyEnabled(sheetState.apNotifyEnabled)
        BASettingsStore.saveCafeApNotifyEnabled(sheetState.cafeApNotifyEnabled)
        BASettingsStore.saveCafeApNotifyThreshold(savedCafeApThreshold)
        BASettingsStore.saveArenaRefreshNotifyEnabled(sheetState.arenaRefreshNotifyEnabled)
        BASettingsStore.saveCafeVisitNotifyEnabled(sheetState.cafeVisitNotifyEnabled)
        BASettingsStore.saveCalendarUpcomingNotifyEnabled(sheetState.calendarUpcomingNotifyEnabled)
        BASettingsStore.saveCalendarEndingNotifyEnabled(sheetState.calendarEndingNotifyEnabled)
        BASettingsStore.savePoolUpcomingNotifyEnabled(sheetState.poolUpcomingNotifyEnabled)
        BASettingsStore.savePoolEndingNotifyEnabled(sheetState.poolEndingNotifyEnabled)
        BASettingsStore.saveCalendarPoolChangeNotifyEnabled(sheetState.calendarPoolChangeNotifyEnabled)
        BASettingsStore.saveCalendarPoolNotifyLeadHours(sheetState.calendarPoolNotifyLeadHours)
        BASettingsStore.saveApNotifyThreshold(savedThreshold)
        BASettingsStore.pruneCalendarPoolNotifiedKeysForPolicy(
            serverIndex = BASettingsStore.loadServerIndex(),
            leadHours = sheetState.calendarPoolNotifyLeadHours,
            calendarUpcomingEnabled = sheetState.calendarUpcomingNotifyEnabled,
            calendarEndingEnabled = sheetState.calendarEndingNotifyEnabled,
            poolUpcomingEnabled = sheetState.poolUpcomingNotifyEnabled,
            poolEndingEnabled = sheetState.poolEndingNotifyEnabled,
            calendarPoolChangeEnabled = sheetState.calendarPoolChangeNotifyEnabled,
        )

        return BaNotificationSettingsPersistenceResult(
            savedThreshold = savedThreshold,
            savedCafeApThreshold = savedCafeApThreshold,
            cafeApNotifyEnabled = sheetState.cafeApNotifyEnabled,
            arenaRefreshNotifyEnabled = sheetState.arenaRefreshNotifyEnabled,
            cafeVisitNotifyEnabled = sheetState.cafeVisitNotifyEnabled,
            calendarUpcomingNotifyEnabled = sheetState.calendarUpcomingNotifyEnabled,
            calendarEndingNotifyEnabled = sheetState.calendarEndingNotifyEnabled,
            poolUpcomingNotifyEnabled = sheetState.poolUpcomingNotifyEnabled,
            poolEndingNotifyEnabled = sheetState.poolEndingNotifyEnabled,
            calendarPoolChangeNotifyEnabled = sheetState.calendarPoolChangeNotifyEnabled,
            calendarPoolNotifyLeadHours = sheetState.calendarPoolNotifyLeadHours,
        )
    }

    fun persistRefreshInterval(
        hours: Int,
        calendarLastSyncMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): BaRefreshIntervalPersistenceResult {
        BASettingsStore.saveCalendarRefreshIntervalHours(hours)
        val elapsed = (nowMs - calendarLastSyncMs).coerceAtLeast(0L)
        return BaRefreshIntervalPersistenceResult(
            hours = hours,
            shouldRefresh = calendarLastSyncMs <= 0L || elapsed >= hours * 60L * 60L * 1000L,
        )
    }

    fun resetCafeApLastNotifiedLevel() {
        BASettingsStore.saveCafeApLastNotifiedLevel(-1)
    }

    fun resetArenaRefreshLastNotifiedSlot() {
        BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(0L)
    }

    fun saveArenaRefreshLastNotifiedSlot(slotMs: Long) {
        BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(slotMs)
    }

    fun resetCafeVisitLastNotifiedSlot() {
        BASettingsStore.saveCafeVisitLastNotifiedSlotMs(0L)
    }

    fun saveCafeVisitLastNotifiedSlot(slotMs: Long) {
        BASettingsStore.saveCafeVisitLastNotifiedSlotMs(slotMs)
    }

    private fun hasAnyImageInEncodedCache(raw: String): Boolean {
        if (raw.isBlank()) return false
        return runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (normalizeGameKeeImageLink(obj.optString("imageUrl")).isNotBlank()) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }
}
