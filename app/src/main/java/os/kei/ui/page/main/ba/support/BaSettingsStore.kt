package os.kei.ui.page.main.ba.support

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import os.kei.ui.page.main.ba.BaReminderCoordinator

internal object BASettingsStore {
    private const val KV_ID = "ba_page_settings"
    private const val KEY_SERVER_INDEX = "server_index"
    private const val KEY_CAFE_LEVEL = "cafe_level"
    private const val KEY_CAFE_STORED_AP = "cafe_stored_ap"
    private const val KEY_CAFE_LAST_HOUR_MS = "cafe_last_hour_ms"
    private const val KEY_CAFE_AP_NOTIFY_ENABLED = "cafe_ap_notify_enabled"
    private const val KEY_CAFE_AP_NOTIFY_THRESHOLD = "cafe_ap_notify_threshold"
    private const val KEY_CAFE_AP_LAST_NOTIFIED_LEVEL = "cafe_ap_last_notified_level"
    private const val KEY_AP_LIMIT = "ap_limit"
    private const val KEY_AP_NOTIFY_ENABLED = "ap_notify_enabled"
    private const val KEY_AP_NOTIFY_THRESHOLD = "ap_notify_threshold"
    private const val KEY_AP_LAST_NOTIFIED_LEVEL = "ap_last_notified_level"
    private const val KEY_ARENA_REFRESH_NOTIFY_ENABLED = "arena_refresh_notify_enabled"
    private const val KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS = "arena_refresh_last_notified_slot_ms"
    private const val KEY_CAFE_VISIT_NOTIFY_ENABLED = "cafe_visit_notify_enabled"
    private const val KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS = "cafe_visit_last_notified_slot_ms"
    private const val KEY_CALENDAR_UPCOMING_NOTIFY_ENABLED = "calendar_upcoming_notify_enabled"
    private const val KEY_CALENDAR_ENDING_NOTIFY_ENABLED = "calendar_ending_notify_enabled"
    private const val KEY_POOL_UPCOMING_NOTIFY_ENABLED = "pool_upcoming_notify_enabled"
    private const val KEY_POOL_ENDING_NOTIFY_ENABLED = "pool_ending_notify_enabled"
    private const val KEY_CALENDAR_POOL_CHANGE_NOTIFY_ENABLED =
        "calendar_pool_change_notify_enabled"
    private const val KEY_CALENDAR_POOL_NOTIFY_LEAD_HOURS = "calendar_pool_notify_lead_hours"
    private const val KEY_CALENDAR_POOL_NOTIFIED_KEYS = "calendar_pool_notified_keys"
    private const val KEY_AP_CURRENT = "ap_current"
    private const val KEY_AP_CURRENT_EXACT = "ap_current_exact"
    private const val KEY_AP_REGEN_BASE_MS = "ap_regen_base_ms"
    private const val KEY_AP_SYNC_MS = "ap_sync_ms"
    private const val KEY_POOL_SHOW_ENDED = "pool_show_ended"
    private const val KEY_ACTIVITY_SHOW_ENDED = "activity_show_ended"
    private const val KEY_SHOW_CALENDAR_POOL_IMAGES = "show_calendar_pool_images"
    private const val KEY_MEDIA_ADAPTIVE_ROTATION_ENABLED = "media_adaptive_rotation_enabled"
    private const val KEY_MEDIA_SAVE_CUSTOM_ENABLED = "media_save_custom_enabled"
    private const val KEY_MEDIA_SAVE_FIXED_TREE_URI = "media_save_fixed_tree_uri"
    private const val KEY_COFFEE_HEADPAT_MS = "coffee_headpat_ms"
    private const val KEY_COFFEE_INVITE1_USED_MS = "coffee_invite1_used_ms"
    private const val KEY_COFFEE_INVITE2_USED_MS = "coffee_invite2_used_ms"
    private const val KEY_LIST_SCROLL_INDEX = "list_scroll_index"
    private const val KEY_LIST_SCROLL_OFFSET = "list_scroll_offset"

    private const val DEFAULT_SERVER_INDEX = 2
    private const val DEFAULT_CAFE_LEVEL = 10
    private const val DEFAULT_CAFE_STORED_AP = 0.0
    private const val DEFAULT_CAFE_AP_NOTIFY_THRESHOLD = 120
    private const val DEFAULT_AP_LIMIT = BA_AP_LIMIT_MAX
    private const val DEFAULT_AP_NOTIFY_THRESHOLD = 120
    private const val DEFAULT_AP_CURRENT = 0.0
    private const val KEY_CALENDAR_CACHE_PREFIX = "calendar_cache_"
    private const val KEY_CALENDAR_SYNC_PREFIX = "calendar_sync_"
    private const val KEY_CALENDAR_CACHE_VERSION_PREFIX = "calendar_cache_version_"
    private const val KEY_POOL_CACHE_PREFIX = "pool_cache_"
    private const val KEY_POOL_SYNC_PREFIX = "pool_sync_"
    private const val KEY_POOL_CACHE_VERSION_PREFIX = "pool_cache_version_"
    private const val KEY_CALENDAR_REFRESH_INTERVAL_HOURS = "calendar_refresh_interval_hours"
    private const val DEFAULT_CALENDAR_REFRESH_INTERVAL_HOURS = 12

    private fun calendarCacheKey(serverIndex: Int): String = "$KEY_CALENDAR_CACHE_PREFIX${serverIndex.coerceIn(0, 2)}"
    private fun calendarSyncKey(serverIndex: Int): String = "$KEY_CALENDAR_SYNC_PREFIX${serverIndex.coerceIn(0, 2)}"
    private fun calendarCacheVersionKey(serverIndex: Int): String =
        "$KEY_CALENDAR_CACHE_VERSION_PREFIX${serverIndex.coerceIn(0, 2)}"
    private fun poolCacheKey(serverIndex: Int): String = "$KEY_POOL_CACHE_PREFIX${serverIndex.coerceIn(0, 2)}"
    private fun poolSyncKey(serverIndex: Int): String = "$KEY_POOL_SYNC_PREFIX${serverIndex.coerceIn(0, 2)}"
    private fun poolCacheVersionKey(serverIndex: Int): String = "$KEY_POOL_CACHE_VERSION_PREFIX${serverIndex.coerceIn(0, 2)}"

    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }
    private fun kv(): MMKV = store
    private fun notifyChanged(notifyHomeOverview: Boolean = true) {
        BASettingsStoreSignals.notifyChanged(notifyHomeOverview = notifyHomeOverview)
    }

    private fun idSettings(): BaIdSettingsAccessor =
        BaIdSettingsAccessor(MmkvBaSettingsKeyValueStore(kv()))

    private class MmkvBaSettingsKeyValueStore(
        private val store: MMKV,
    ) : BaIdKeyValueStore, BaNativeBgmMediaNotificationKeyValueStore {
        override fun decodeBool(key: String, defaultValue: Boolean): Boolean =
            store.decodeBool(key, defaultValue)

        override fun encode(key: String, value: Boolean) {
            store.encode(key, value)
        }

        override fun decodeString(key: String, defaultValue: String): String? =
            store.decodeString(key, defaultValue)

        override fun encode(key: String, value: String) {
            store.encode(key, value)
        }
    }

    fun loadCalendarCache(serverIndex: Int): Pair<String, Long> {
        val store = kv()
        return store.decodeString(calendarCacheKey(serverIndex), "").orEmpty() to
            store.decodeLong(calendarSyncKey(serverIndex), 0L)
    }

    fun saveCalendarCache(serverIndex: Int, encodedEntries: String, syncMs: Long) {
        val store = kv()
        store.encode(calendarCacheKey(serverIndex), encodedEntries)
        store.encode(calendarSyncKey(serverIndex), syncMs.coerceAtLeast(0L))
        store.encode(calendarCacheVersionKey(serverIndex), BA_CALENDAR_CACHE_SCHEMA_VERSION)
        notifyChanged()
    }

    fun loadCalendarCacheVersion(serverIndex: Int): Int {
        return kv().decodeInt(calendarCacheVersionKey(serverIndex), 0)
    }

    fun loadPoolCache(serverIndex: Int): Pair<String, Long> {
        val store = kv()
        return store.decodeString(poolCacheKey(serverIndex), "").orEmpty() to
            store.decodeLong(poolSyncKey(serverIndex), 0L)
    }

    fun savePoolCache(serverIndex: Int, encodedEntries: String, syncMs: Long) {
        val store = kv()
        store.encode(poolCacheKey(serverIndex), encodedEntries)
        store.encode(poolSyncKey(serverIndex), syncMs.coerceAtLeast(0L))
        store.encode(poolCacheVersionKey(serverIndex), BA_POOL_CACHE_SCHEMA_VERSION)
        notifyChanged()
    }

    fun loadPoolCacheVersion(serverIndex: Int): Int {
        return kv().decodeInt(poolCacheVersionKey(serverIndex), 0)
    }

    fun loadPoolShowEnded(): Boolean = kv().decodeBool(KEY_POOL_SHOW_ENDED, false)
    fun savePoolShowEnded(enabled: Boolean) {
        kv().encode(KEY_POOL_SHOW_ENDED, enabled)
        notifyChanged()
    }

    fun loadActivityShowEnded(): Boolean = kv().decodeBool(KEY_ACTIVITY_SHOW_ENDED, false)
    fun saveActivityShowEnded(enabled: Boolean) {
        kv().encode(KEY_ACTIVITY_SHOW_ENDED, enabled)
        notifyChanged()
    }

    fun loadShowCalendarPoolImages(): Boolean = kv().decodeBool(KEY_SHOW_CALENDAR_POOL_IMAGES, true)
    fun saveShowCalendarPoolImages(enabled: Boolean) {
        kv().encode(KEY_SHOW_CALENDAR_POOL_IMAGES, enabled)
        notifyChanged()
    }

    fun loadMediaAdaptiveRotationEnabled(): Boolean =
        kv().decodeBool(KEY_MEDIA_ADAPTIVE_ROTATION_ENABLED, true)

    fun saveMediaAdaptiveRotationEnabled(enabled: Boolean) {
        kv().encode(KEY_MEDIA_ADAPTIVE_ROTATION_ENABLED, enabled)
        notifyChanged()
    }

    fun loadMediaSaveCustomEnabled(): Boolean =
        kv().decodeBool(KEY_MEDIA_SAVE_CUSTOM_ENABLED, false)

    fun saveMediaSaveCustomEnabled(enabled: Boolean) {
        kv().encode(KEY_MEDIA_SAVE_CUSTOM_ENABLED, enabled)
        notifyChanged()
    }

    fun loadMediaSaveFixedTreeUri(): String =
        kv().decodeString(KEY_MEDIA_SAVE_FIXED_TREE_URI, "").orEmpty().trim()

    fun saveMediaSaveFixedTreeUri(uri: String) {
        kv().encode(KEY_MEDIA_SAVE_FIXED_TREE_URI, uri.trim())
        notifyChanged()
    }

    fun loadNativeBgmMediaNotificationEnabled(): Boolean =
        BaNativeBgmMediaNotificationPrefs(MmkvBaSettingsKeyValueStore(kv())).loadEnabled()

    fun saveNativeBgmMediaNotificationEnabled(enabled: Boolean) {
        BaNativeBgmMediaNotificationPrefs(MmkvBaSettingsKeyValueStore(kv())).saveEnabled(enabled)
        notifyChanged()
    }

    fun loadCalendarRefreshIntervalHours(): Int {
        val raw = kv().decodeInt(
            KEY_CALENDAR_REFRESH_INTERVAL_HOURS,
            DEFAULT_CALENDAR_REFRESH_INTERVAL_HOURS
        )
        return BaCalendarRefreshIntervalOption.fromHours(raw).hours
    }

    fun saveCalendarRefreshIntervalHours(hours: Int) {
        kv().encode(
            KEY_CALENDAR_REFRESH_INTERVAL_HOURS,
            BaCalendarRefreshIntervalOption.fromHours(hours).hours
        )
        notifyChanged()
    }

    fun loadSnapshot(): BaPageSnapshot {
        val store = kv()
        val serverIndex = store.decodeInt(KEY_SERVER_INDEX, DEFAULT_SERVER_INDEX).coerceIn(0, 2)
        val idIndependentByServer = loadIdIndependentByServerEnabled()
        val cafeLevel = store.decodeInt(KEY_CAFE_LEVEL, DEFAULT_CAFE_LEVEL).coerceIn(1, 10)
        val cafeStoredAp = normalizeAp(
            store.decodeString(KEY_CAFE_STORED_AP, DEFAULT_CAFE_STORED_AP.toString())
                ?.toDoubleOrNull()
                ?: DEFAULT_CAFE_STORED_AP
        )
        val idNickname = loadIdNickname(serverIndex)
        val idFriendCode = loadIdFriendCode(serverIndex)
        val apCurrent = if (store.containsKey(KEY_AP_CURRENT_EXACT)) {
            store.decodeString(KEY_AP_CURRENT_EXACT, DEFAULT_AP_CURRENT.toString())?.toDoubleOrNull() ?: DEFAULT_AP_CURRENT
        } else {
            store.decodeInt(KEY_AP_CURRENT, DEFAULT_AP_CURRENT.toInt()).toDouble()
        }
        val refreshHours = BaCalendarRefreshIntervalOption.fromHours(
            store.decodeInt(
                KEY_CALENDAR_REFRESH_INTERVAL_HOURS,
                DEFAULT_CALENDAR_REFRESH_INTERVAL_HOURS
            )
        ).hours
        return BaPageSnapshot(
            serverIndex = serverIndex,
            cafeLevel = cafeLevel,
            cafeStoredAp = normalizeAp(cafeStoredAp),
            cafeLastHourMs = store.decodeLong(KEY_CAFE_LAST_HOUR_MS, 0L),
            cafeApNotifyEnabled = store.decodeBool(KEY_CAFE_AP_NOTIFY_ENABLED, false),
            cafeApNotifyThreshold = store.decodeInt(
                KEY_CAFE_AP_NOTIFY_THRESHOLD,
                DEFAULT_CAFE_AP_NOTIFY_THRESHOLD
            ).coerceIn(0, BA_AP_MAX),
            cafeApLastNotifiedLevel = store.decodeInt(
                KEY_CAFE_AP_LAST_NOTIFIED_LEVEL,
                -1
            ).coerceIn(-1, BA_AP_MAX),
            idNickname = idNickname,
            idFriendCode = idFriendCode,
            idIndependentByServer = idIndependentByServer,
            apLimit = store.decodeInt(KEY_AP_LIMIT, DEFAULT_AP_LIMIT).coerceIn(0, BA_AP_LIMIT_MAX),
            apCurrent = normalizeAp(apCurrent.coerceIn(0.0, BA_AP_MAX.toDouble())),
            apRegenBaseMs = store.decodeLong(KEY_AP_REGEN_BASE_MS, 0L),
            apSyncMs = store.decodeLong(KEY_AP_SYNC_MS, 0L),
            apNotifyEnabled = store.decodeBool(KEY_AP_NOTIFY_ENABLED, false),
            apNotifyThreshold = store.decodeInt(
                KEY_AP_NOTIFY_THRESHOLD,
                DEFAULT_AP_NOTIFY_THRESHOLD
            ).coerceIn(
                0,
                BA_AP_MAX
            ),
            apLastNotifiedLevel = store.decodeInt(KEY_AP_LAST_NOTIFIED_LEVEL, -1).coerceIn(
                -1,
                BA_AP_MAX
            ),
            arenaRefreshNotifyEnabled = store.decodeBool(KEY_ARENA_REFRESH_NOTIFY_ENABLED, false),
            arenaRefreshLastNotifiedSlotMs = store.decodeLong(
                KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS,
                0L
            ).coerceAtLeast(0L),
            cafeVisitNotifyEnabled = store.decodeBool(KEY_CAFE_VISIT_NOTIFY_ENABLED, false),
            cafeVisitLastNotifiedSlotMs = store.decodeLong(KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS, 0L)
                .coerceAtLeast(0L),
            calendarUpcomingNotifyEnabled = store.decodeBool(
                KEY_CALENDAR_UPCOMING_NOTIFY_ENABLED,
                false
            ),
            calendarEndingNotifyEnabled = store.decodeBool(
                KEY_CALENDAR_ENDING_NOTIFY_ENABLED,
                false
            ),
            poolUpcomingNotifyEnabled = store.decodeBool(KEY_POOL_UPCOMING_NOTIFY_ENABLED, false),
            poolEndingNotifyEnabled = store.decodeBool(KEY_POOL_ENDING_NOTIFY_ENABLED, false),
            calendarPoolChangeNotifyEnabled = store.decodeBool(
                KEY_CALENDAR_POOL_CHANGE_NOTIFY_ENABLED,
                false
            ),
            calendarPoolNotifyLeadHours = BaCalendarPoolNotifyLeadOption.fromHours(
                store.decodeInt(KEY_CALENDAR_POOL_NOTIFY_LEAD_HOURS, 24)
            ).hours,
            coffeeHeadpatMs = store.decodeLong(KEY_COFFEE_HEADPAT_MS, 0L),
            coffeeInvite1UsedMs = store.decodeLong(KEY_COFFEE_INVITE1_USED_MS, 0L),
            coffeeInvite2UsedMs = store.decodeLong(KEY_COFFEE_INVITE2_USED_MS, 0L),
            showEndedPools = store.decodeBool(KEY_POOL_SHOW_ENDED, false),
            showEndedActivities = store.decodeBool(KEY_ACTIVITY_SHOW_ENDED, false),
            showCalendarPoolImages = store.decodeBool(KEY_SHOW_CALENDAR_POOL_IMAGES, true),
            mediaAdaptiveRotationEnabled = store.decodeBool(
                KEY_MEDIA_ADAPTIVE_ROTATION_ENABLED,
                true
            ),
            mediaSaveCustomEnabled = store.decodeBool(KEY_MEDIA_SAVE_CUSTOM_ENABLED, false),
            mediaSaveFixedTreeUri = store.decodeString(KEY_MEDIA_SAVE_FIXED_TREE_URI, "").orEmpty()
                .trim(),
            nativeBgmMediaNotificationEnabled = loadNativeBgmMediaNotificationEnabled(),
            calendarRefreshIntervalHours = refreshHours
        )
    }

    fun loadCalendarCacheSnapshot(serverIndex: Int): BaCacheSnapshot {
        val store = kv()
        return BaCacheSnapshot(
            raw = store.decodeString(calendarCacheKey(serverIndex), "").orEmpty(),
            syncMs = store.decodeLong(calendarSyncKey(serverIndex), 0L),
            version = store.decodeInt(calendarCacheVersionKey(serverIndex), 0)
        )
    }

    fun loadPoolCacheSnapshot(serverIndex: Int): BaCacheSnapshot {
        val store = kv()
        return BaCacheSnapshot(
            raw = store.decodeString(poolCacheKey(serverIndex), "").orEmpty(),
            syncMs = store.decodeLong(poolSyncKey(serverIndex), 0L),
            version = store.decodeInt(poolCacheVersionKey(serverIndex), 0)
        )
    }

    fun loadServerIndex(): Int = kv().decodeInt(KEY_SERVER_INDEX, DEFAULT_SERVER_INDEX).coerceIn(0, 2)
    fun saveServerIndex(index: Int) {
        kv().encode(KEY_SERVER_INDEX, index.coerceIn(0, 2))
        pruneCalendarPoolNotifiedKeysForCurrentPolicy()
        notifyChanged()
    }

    fun loadCafeLevel(): Int = kv().decodeInt(KEY_CAFE_LEVEL, DEFAULT_CAFE_LEVEL).coerceIn(1, 10)
    fun saveCafeLevel(level: Int) {
        kv().encode(KEY_CAFE_LEVEL, level.coerceIn(1, 10))
        notifyChanged()
    }

    fun loadCafeStoredAp(): Double {
        val raw = kv().decodeString(KEY_CAFE_STORED_AP, DEFAULT_CAFE_STORED_AP.toString())
        return normalizeAp(raw?.toDoubleOrNull() ?: DEFAULT_CAFE_STORED_AP)
    }

    fun saveCafeStoredAp(
        storedAp: Double,
        notifyHomeOverview: Boolean = true,
    ) {
        saveBaRuntimeState(
            cafeStoredAp = storedAp,
            notifyHomeOverview = notifyHomeOverview,
        )
    }

    fun loadCafeLastHourMs(): Long = kv().decodeLong(KEY_CAFE_LAST_HOUR_MS, 0L)
    fun saveCafeLastHourMs(epochMs: Long) {
        saveBaRuntimeState(cafeLastHourMs = epochMs, notifyHomeOverview = false)
    }

    fun loadCafeApNotifyEnabled(): Boolean = kv().decodeBool(KEY_CAFE_AP_NOTIFY_ENABLED, false)
    fun saveCafeApNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CAFE_AP_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCafeApNotifyThreshold(): Int =
        kv().decodeInt(KEY_CAFE_AP_NOTIFY_THRESHOLD, DEFAULT_CAFE_AP_NOTIFY_THRESHOLD)
            .coerceIn(0, BA_AP_MAX)

    fun saveCafeApNotifyThreshold(threshold: Int) {
        kv().encode(KEY_CAFE_AP_NOTIFY_THRESHOLD, threshold.coerceIn(0, BA_AP_MAX))
        notifyChanged()
    }

    fun loadCafeApLastNotifiedLevel(): Int =
        kv().decodeInt(KEY_CAFE_AP_LAST_NOTIFIED_LEVEL, -1).coerceIn(-1, BA_AP_MAX)

    fun saveCafeApLastNotifiedLevel(level: Int) {
        kv().encode(KEY_CAFE_AP_LAST_NOTIFIED_LEVEL, level.coerceIn(-1, BA_AP_MAX))
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadIdIndependentByServerEnabled(): Boolean =
        idSettings().loadIndependentByServerEnabled()

    fun saveIdIndependentByServerEnabled(enabled: Boolean) {
        idSettings().saveIndependentByServerEnabled(enabled)
        notifyChanged()
    }

    fun loadIdNickname(serverIndex: Int? = null): String {
        return idSettings().loadNickname(serverIndex)
    }

    fun saveIdNickname(name: String, serverIndex: Int? = null) {
        idSettings().saveNickname(name, serverIndex)
        notifyChanged()
    }

    fun loadIdFriendCode(serverIndex: Int? = null): String {
        return idSettings().loadFriendCode(serverIndex)
    }

    fun saveIdFriendCode(code: String, serverIndex: Int? = null) {
        idSettings().saveFriendCode(code, serverIndex)
        notifyChanged()
    }

    fun loadApLimit(): Int = kv().decodeInt(KEY_AP_LIMIT, DEFAULT_AP_LIMIT).coerceIn(0,
        BA_AP_LIMIT_MAX
    )
    fun saveApLimit(limit: Int) {
        kv().encode(KEY_AP_LIMIT, limit.coerceIn(0, BA_AP_LIMIT_MAX))
        notifyChanged()
    }

    fun loadApNotifyEnabled(): Boolean = kv().decodeBool(KEY_AP_NOTIFY_ENABLED, false)
    fun saveApNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_AP_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadApNotifyThreshold(): Int =
        kv().decodeInt(KEY_AP_NOTIFY_THRESHOLD, DEFAULT_AP_NOTIFY_THRESHOLD).coerceIn(0, BA_AP_MAX)

    fun saveApNotifyThreshold(threshold: Int) {
        kv().encode(KEY_AP_NOTIFY_THRESHOLD, threshold.coerceIn(0, BA_AP_MAX))
        notifyChanged()
    }

    fun loadApLastNotifiedLevel(): Int =
        kv().decodeInt(KEY_AP_LAST_NOTIFIED_LEVEL, -1).coerceIn(-1, BA_AP_MAX)

    fun saveApLastNotifiedLevel(level: Int) {
        kv().encode(KEY_AP_LAST_NOTIFIED_LEVEL, level.coerceIn(-1, BA_AP_MAX))
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadArenaRefreshNotifyEnabled(): Boolean = kv().decodeBool(KEY_ARENA_REFRESH_NOTIFY_ENABLED, false)
    fun saveArenaRefreshNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_ARENA_REFRESH_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadArenaRefreshLastNotifiedSlotMs(): Long =
        kv().decodeLong(KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS, 0L).coerceAtLeast(0L)

    fun saveArenaRefreshLastNotifiedSlotMs(slotMs: Long) {
        kv().encode(KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS, slotMs.coerceAtLeast(0L))
        notifyChanged()
    }

    fun loadCafeVisitNotifyEnabled(): Boolean = kv().decodeBool(KEY_CAFE_VISIT_NOTIFY_ENABLED, false)
    fun saveCafeVisitNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CAFE_VISIT_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCafeVisitLastNotifiedSlotMs(): Long =
        kv().decodeLong(KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS, 0L).coerceAtLeast(0L)

    fun saveCafeVisitLastNotifiedSlotMs(slotMs: Long) {
        kv().encode(KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS, slotMs.coerceAtLeast(0L))
        notifyChanged()
    }

    fun loadCalendarUpcomingNotifyEnabled(): Boolean =
        kv().decodeBool(KEY_CALENDAR_UPCOMING_NOTIFY_ENABLED, false)

    fun saveCalendarUpcomingNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CALENDAR_UPCOMING_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCalendarEndingNotifyEnabled(): Boolean =
        kv().decodeBool(KEY_CALENDAR_ENDING_NOTIFY_ENABLED, false)

    fun saveCalendarEndingNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CALENDAR_ENDING_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadPoolUpcomingNotifyEnabled(): Boolean =
        kv().decodeBool(KEY_POOL_UPCOMING_NOTIFY_ENABLED, false)

    fun savePoolUpcomingNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_POOL_UPCOMING_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadPoolEndingNotifyEnabled(): Boolean =
        kv().decodeBool(KEY_POOL_ENDING_NOTIFY_ENABLED, false)

    fun savePoolEndingNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_POOL_ENDING_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCalendarPoolChangeNotifyEnabled(): Boolean =
        kv().decodeBool(KEY_CALENDAR_POOL_CHANGE_NOTIFY_ENABLED, false)

    fun saveCalendarPoolChangeNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CALENDAR_POOL_CHANGE_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCalendarPoolNotifyLeadHours(): Int {
        return BaCalendarPoolNotifyLeadOption.fromHours(
            kv().decodeInt(KEY_CALENDAR_POOL_NOTIFY_LEAD_HOURS, 24)
        ).hours
    }

    fun saveCalendarPoolNotifyLeadHours(hours: Int) {
        kv().encode(
            KEY_CALENDAR_POOL_NOTIFY_LEAD_HOURS,
            BaCalendarPoolNotifyLeadOption.fromHours(hours).hours
        )
        notifyChanged()
    }

    fun loadCalendarPoolNotifiedKeys(): Set<String> {
        return kv().decodeString(KEY_CALENDAR_POOL_NOTIFIED_KEYS, "")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun markCalendarPoolNotified(key: String) {
        val normalized = key.trim()
        if (normalized.isBlank()) return
        val keys = (loadCalendarPoolNotifiedKeys() + normalized).toList().takeLast(500)
        kv().encode(KEY_CALENDAR_POOL_NOTIFIED_KEYS, keys.joinToString(separator = "\n"))
        notifyChanged()
    }

    fun replaceCalendarPoolNotifiedKeys(keys: Set<String>) {
        val normalized = keys
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(500)
        if (normalized.isEmpty()) {
            kv().removeValueForKey(KEY_CALENDAR_POOL_NOTIFIED_KEYS)
            notifyChanged()
            return
        }
        kv().encode(KEY_CALENDAR_POOL_NOTIFIED_KEYS, normalized.joinToString(separator = "\n"))
        notifyChanged()
    }

    fun pruneCalendarPoolNotifiedKeysForCurrentPolicy() {
        val snapshot = loadSnapshot()
        pruneCalendarPoolNotifiedKeysForPolicy(
            serverIndex = snapshot.serverIndex,
            leadHours = snapshot.calendarPoolNotifyLeadHours,
            calendarUpcomingEnabled = snapshot.calendarUpcomingNotifyEnabled,
            calendarEndingEnabled = snapshot.calendarEndingNotifyEnabled,
            poolUpcomingEnabled = snapshot.poolUpcomingNotifyEnabled,
            poolEndingEnabled = snapshot.poolEndingNotifyEnabled,
            calendarPoolChangeEnabled = snapshot.calendarPoolChangeNotifyEnabled
        )
    }

    fun pruneCalendarPoolNotifiedKeysForPolicy(
        serverIndex: Int,
        leadHours: Int,
        calendarUpcomingEnabled: Boolean,
        calendarEndingEnabled: Boolean,
        poolUpcomingEnabled: Boolean,
        poolEndingEnabled: Boolean,
        calendarPoolChangeEnabled: Boolean
    ) {
        val current = loadCalendarPoolNotifiedKeys()
        val retained = BaReminderCoordinator.retainNotifiedKeysForPolicy(
            keys = current,
            serverIndex = serverIndex,
            leadHours = leadHours,
            calendarUpcomingEnabled = calendarUpcomingEnabled,
            calendarEndingEnabled = calendarEndingEnabled,
            poolUpcomingEnabled = poolUpcomingEnabled,
            poolEndingEnabled = poolEndingEnabled,
            calendarPoolChangeEnabled = calendarPoolChangeEnabled
        )
        if (retained != current) {
            replaceCalendarPoolNotifiedKeys(retained)
        }
    }

    fun loadApCurrent(): Double {
        val store = kv()
        val value = if (store.containsKey(KEY_AP_CURRENT_EXACT)) {
            store.decodeString(KEY_AP_CURRENT_EXACT, DEFAULT_AP_CURRENT.toString())?.toDoubleOrNull() ?: DEFAULT_AP_CURRENT
        } else {
            store.decodeInt(KEY_AP_CURRENT, DEFAULT_AP_CURRENT.toInt()).toDouble()
        }
        return normalizeAp(value.coerceIn(0.0, BA_AP_MAX.toDouble()))
    }

    fun saveApCurrent(
        current: Double,
        notifyHomeOverview: Boolean = true,
    ) {
        saveBaRuntimeState(
            apCurrent = current,
            notifyHomeOverview = notifyHomeOverview,
        )
    }

    fun loadApRegenBaseMs(): Long = kv().decodeLong(KEY_AP_REGEN_BASE_MS, 0L)
    fun saveApRegenBaseMs(epochMs: Long) {
        saveBaRuntimeState(apRegenBaseMs = epochMs, notifyHomeOverview = false)
    }

    fun loadApSyncMs(): Long = kv().decodeLong(KEY_AP_SYNC_MS, 0L)
    fun saveApSyncMs(epochMs: Long) {
        saveBaRuntimeState(apSyncMs = epochMs, notifyHomeOverview = false)
    }

    fun saveBaRuntimeState(
        apCurrent: Double? = null,
        apRegenBaseMs: Long? = null,
        apSyncMs: Long? = null,
        cafeStoredAp: Double? = null,
        cafeLastHourMs: Long? = null,
        notifyHomeOverview: Boolean = true,
    ) {
        val store = kv()
        apCurrent?.let { current ->
            val normalized = normalizeAp(current)
            store.encode(KEY_AP_CURRENT_EXACT, normalized.toString())
            store.encode(KEY_AP_CURRENT, displayAp(normalized))
        }
        apRegenBaseMs?.let { epochMs ->
            store.encode(KEY_AP_REGEN_BASE_MS, epochMs.coerceAtLeast(0L))
        }
        apSyncMs?.let { epochMs ->
            store.encode(KEY_AP_SYNC_MS, epochMs.coerceAtLeast(0L))
        }
        cafeStoredAp?.let { storedAp ->
            store.encode(KEY_CAFE_STORED_AP, normalizeAp(storedAp).toString())
        }
        cafeLastHourMs?.let { epochMs ->
            store.encode(KEY_CAFE_LAST_HOUR_MS, floorToHourMs(epochMs.coerceAtLeast(0L)))
        }
        notifyChanged(notifyHomeOverview = notifyHomeOverview)
    }

    fun loadCoffeeHeadpatMs(): Long = kv().decodeLong(KEY_COFFEE_HEADPAT_MS, 0L)
    fun saveCoffeeHeadpatMs(epochMs: Long) {
        kv().encode(KEY_COFFEE_HEADPAT_MS, epochMs.coerceAtLeast(0L))
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadCoffeeInvite1UsedMs(): Long = kv().decodeLong(KEY_COFFEE_INVITE1_USED_MS, 0L)
    fun saveCoffeeInvite1UsedMs(epochMs: Long) {
        kv().encode(KEY_COFFEE_INVITE1_USED_MS, epochMs.coerceAtLeast(0L))
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadCoffeeInvite2UsedMs(): Long = kv().decodeLong(KEY_COFFEE_INVITE2_USED_MS, 0L)
    fun saveCoffeeInvite2UsedMs(epochMs: Long) {
        kv().encode(KEY_COFFEE_INVITE2_USED_MS, epochMs.coerceAtLeast(0L))
        notifyChanged(notifyHomeOverview = false)
    }

    fun clearCalendarAndPoolCaches() {
        val store = kv()
        for (serverIndex in 0..2) {
            store.removeValueForKey(calendarCacheKey(serverIndex))
            store.removeValueForKey(calendarSyncKey(serverIndex))
            store.removeValueForKey(calendarCacheVersionKey(serverIndex))
            store.removeValueForKey(poolCacheKey(serverIndex))
            store.removeValueForKey(poolSyncKey(serverIndex))
            store.removeValueForKey(poolCacheVersionKey(serverIndex))
        }
        store.trim()
        notifyChanged()
    }

    fun storageFootprintBytes(): Long = kv().totalSize()
    fun actualDataBytes(): Long = kv().actualSize()

    fun cacheBytesEstimated(): Long {
        var total = 0L
        for (serverIndex in 0..2) {
            val calendar = loadCalendarCacheSnapshot(serverIndex)
            val pool = loadPoolCacheSnapshot(serverIndex)
            total += calendar.raw.length.toLong() * 2 + 16L
            total += pool.raw.length.toLong() * 2 + 16L
        }
        return total
    }

    fun configBytesEstimated(): Long {
        val snapshot = loadSnapshot()
        var bytes = listOf(
            snapshot.idNickname,
            snapshot.idFriendCode
        ).sumOf { it.length.toLong() * 2 } + 160L
        if (snapshot.idIndependentByServer) {
            for (serverIndex in 0..2) {
                bytes += loadIdNickname(serverIndex).length.toLong() * 2
                bytes += loadIdFriendCode(serverIndex).length.toLong() * 2
            }
        }
        return bytes
    }

    fun clearListScrollState() {
        val store = kv()
        store.removeValueForKey(KEY_LIST_SCROLL_INDEX)
        store.removeValueForKey(KEY_LIST_SCROLL_OFFSET)
    }
}
