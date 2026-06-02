package os.kei.ui.page.main.ba.support

import com.tencent.mmkv.MMKV

internal fun loadBaSettingsSnapshot(store: MMKV): BaPageSnapshot {
    val keyValueStore = MmkvBaSettingsKeyValueStore(store)
    val idSettings = BaIdSettingsAccessor(keyValueStore)
    val serverIndex = store.decodeInt(KEY_SERVER_INDEX, DEFAULT_SERVER_INDEX).coerceIn(0, 2)
    val cafeLevel = store.decodeInt(KEY_CAFE_LEVEL, DEFAULT_CAFE_LEVEL).coerceIn(1, 10)
    val cafeStoredAp =
        normalizeAp(
            store
                .decodeString(KEY_CAFE_STORED_AP, DEFAULT_CAFE_STORED_AP.toString())
                ?.toDoubleOrNull()
                ?: DEFAULT_CAFE_STORED_AP,
        )
    val apCurrent =
        if (store.containsKey(KEY_AP_CURRENT_EXACT)) {
            store.decodeString(KEY_AP_CURRENT_EXACT, DEFAULT_AP_CURRENT.toString())?.toDoubleOrNull()
                ?: DEFAULT_AP_CURRENT
        } else {
            store.decodeInt(KEY_AP_CURRENT, DEFAULT_AP_CURRENT.toInt()).toDouble()
        }
    val refreshHours =
        BaCalendarRefreshIntervalOption
            .fromHours(
                store.decodeInt(
                    KEY_CALENDAR_REFRESH_INTERVAL_HOURS,
                    DEFAULT_CALENDAR_REFRESH_INTERVAL_HOURS,
                ),
            ).hours
    return BaPageSnapshot(
        serverIndex = serverIndex,
        cafeLevel = cafeLevel,
        cafeStoredAp = normalizeAp(cafeStoredAp),
        cafeLastHourMs = store.decodeLong(KEY_CAFE_LAST_HOUR_MS, 0L),
        cafeApNotifyEnabled = store.decodeBool(KEY_CAFE_AP_NOTIFY_ENABLED, false),
        cafeApNotifyThreshold =
            store
                .decodeInt(
                    KEY_CAFE_AP_NOTIFY_THRESHOLD,
                    DEFAULT_CAFE_AP_NOTIFY_THRESHOLD,
                ).coerceIn(0, BA_AP_MAX),
        cafeApLastNotifiedLevel =
            store
                .decodeInt(
                    KEY_CAFE_AP_LAST_NOTIFIED_LEVEL,
                    -1,
                ).coerceIn(-1, BA_AP_MAX),
        idNickname = idSettings.loadNickname(serverIndex),
        idFriendCode = idSettings.loadFriendCode(serverIndex),
        apLimit = store.decodeInt(KEY_AP_LIMIT, DEFAULT_AP_LIMIT).coerceIn(0, BA_AP_LIMIT_MAX),
        apCurrent = normalizeAp(apCurrent.coerceIn(0.0, BA_AP_MAX.toDouble())),
        apRegenBaseMs = store.decodeLong(KEY_AP_REGEN_BASE_MS, 0L),
        apSyncMs = store.decodeLong(KEY_AP_SYNC_MS, 0L),
        apNotifyEnabled = store.decodeBool(KEY_AP_NOTIFY_ENABLED, false),
        apNotifyThreshold =
            store
                .decodeInt(
                    KEY_AP_NOTIFY_THRESHOLD,
                    DEFAULT_AP_NOTIFY_THRESHOLD,
                ).coerceIn(0, BA_AP_MAX),
        apLastNotifiedLevel = store.decodeInt(KEY_AP_LAST_NOTIFIED_LEVEL, -1).coerceIn(-1, BA_AP_MAX),
        arenaRefreshNotifyEnabled = store.decodeBool(KEY_ARENA_REFRESH_NOTIFY_ENABLED, false),
        arenaRefreshLastNotifiedSlotMs =
            store
                .decodeLong(
                    KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS,
                    0L,
                ).coerceAtLeast(0L),
        cafeVisitNotifyEnabled = store.decodeBool(KEY_CAFE_VISIT_NOTIFY_ENABLED, false),
        cafeVisitLastNotifiedSlotMs =
            store.decodeLong(KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS, 0L).coerceAtLeast(0L),
        calendarUpcomingNotifyEnabled = store.decodeBool(KEY_CALENDAR_UPCOMING_NOTIFY_ENABLED, false),
        calendarEndingNotifyEnabled = store.decodeBool(KEY_CALENDAR_ENDING_NOTIFY_ENABLED, false),
        poolUpcomingNotifyEnabled = store.decodeBool(KEY_POOL_UPCOMING_NOTIFY_ENABLED, false),
        poolEndingNotifyEnabled = store.decodeBool(KEY_POOL_ENDING_NOTIFY_ENABLED, false),
        calendarPoolChangeNotifyEnabled = store.decodeBool(KEY_CALENDAR_POOL_CHANGE_NOTIFY_ENABLED, false),
        calendarPoolNotifyLeadHours =
            BaCalendarPoolNotifyLeadOption
                .fromHours(
                    store.decodeInt(KEY_CALENDAR_POOL_NOTIFY_LEAD_HOURS, 24),
                ).hours,
        coffeeHeadpatMs = store.decodeLong(KEY_COFFEE_HEADPAT_MS, 0L),
        coffeeInvite1UsedMs = store.decodeLong(KEY_COFFEE_INVITE1_USED_MS, 0L),
        coffeeInvite2UsedMs = store.decodeLong(KEY_COFFEE_INVITE2_USED_MS, 0L),
        showEndedPools = store.decodeBool(KEY_POOL_SHOW_ENDED, false),
        showEndedActivities = store.decodeBool(KEY_ACTIVITY_SHOW_ENDED, false),
        showCalendarPoolImages = store.decodeBool(KEY_SHOW_CALENDAR_POOL_IMAGES, true),
        mediaAdaptiveRotationEnabled = store.decodeBool(KEY_MEDIA_ADAPTIVE_ROTATION_ENABLED, true),
        mediaSaveCustomEnabled = store.decodeBool(KEY_MEDIA_SAVE_CUSTOM_ENABLED, false),
        mediaSaveFixedTreeUri = store.decodeString(KEY_MEDIA_SAVE_FIXED_TREE_URI, "").orEmpty().trim(),
        nativeBgmMediaNotificationEnabled = BaNativeBgmMediaNotificationPrefs(keyValueStore).loadEnabled(),
        calendarRefreshIntervalHours = refreshHours,
    )
}
