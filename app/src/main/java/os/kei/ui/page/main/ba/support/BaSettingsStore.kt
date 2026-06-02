@file:Suppress("ktlint:standard:filename")

package os.kei.ui.page.main.ba.support

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import os.kei.ui.page.main.ba.BaReminderCoordinator

internal object BASettingsStore {
    private val store: MMKV by lazy { KeiMmkv.byId(BA_SETTINGS_KV_ID) }

    private fun kv(): MMKV = store

    private fun notifyChanged(notifyHomeOverview: Boolean = true) {
        BASettingsStoreSignals.notifyChanged(notifyHomeOverview = notifyHomeOverview)
    }

    private fun idSettings(): BaIdSettingsAccessor = BaIdSettingsAccessor(MmkvBaSettingsKeyValueStore(kv()))

    private fun accountKeyValueStore(): MmkvBaSettingsKeyValueStore = MmkvBaSettingsKeyValueStore(kv())

    private fun accountStore(keyValueStore: MmkvBaSettingsKeyValueStore = accountKeyValueStore()): BaAccountStore =
        BaAccountStore(keyValueStore)

    private fun migratedAccountStore(): BaAccountStore {
        val keyValueStore = accountKeyValueStore()
        val store = accountStore(keyValueStore)
        BaAccountMigration(
            accountStore = store,
            keyValueStore = keyValueStore,
        ).migrateLegacyIfNeeded()
        return store
    }

    private fun cacheStore(): BaSettingsCacheStore = BaSettingsCacheStore(kv()) { notifyChanged() }

    fun loadCalendarCache(serverIndex: Int): Pair<String, Long> = cacheStore().loadCalendarCache(serverIndex)

    fun saveCalendarCache(
        serverIndex: Int,
        encodedEntries: String,
        syncMs: Long,
    ) = cacheStore().saveCalendarCache(serverIndex, encodedEntries, syncMs)

    fun loadCalendarCacheVersion(serverIndex: Int): Int = cacheStore().loadCalendarCacheVersion(serverIndex)

    fun loadPoolCache(serverIndex: Int): Pair<String, Long> = cacheStore().loadPoolCache(serverIndex)

    fun savePoolCache(
        serverIndex: Int,
        encodedEntries: String,
        syncMs: Long,
    ) = cacheStore().savePoolCache(serverIndex, encodedEntries, syncMs)

    fun loadPoolCacheVersion(serverIndex: Int): Int = cacheStore().loadPoolCacheVersion(serverIndex)

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

    fun loadMediaAdaptiveRotationEnabled(): Boolean = kv().decodeBool(KEY_MEDIA_ADAPTIVE_ROTATION_ENABLED, true)

    fun saveMediaAdaptiveRotationEnabled(enabled: Boolean) {
        kv().encode(KEY_MEDIA_ADAPTIVE_ROTATION_ENABLED, enabled)
        notifyChanged()
    }

    fun loadMediaSaveCustomEnabled(): Boolean = kv().decodeBool(KEY_MEDIA_SAVE_CUSTOM_ENABLED, false)

    fun saveMediaSaveCustomEnabled(enabled: Boolean) {
        kv().encode(KEY_MEDIA_SAVE_CUSTOM_ENABLED, enabled)
        notifyChanged()
    }

    fun loadMediaSaveFixedTreeUri(): String = kv().decodeString(KEY_MEDIA_SAVE_FIXED_TREE_URI, "").orEmpty().trim()

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
        val raw =
            kv().decodeInt(
                KEY_CALENDAR_REFRESH_INTERVAL_HOURS,
                DEFAULT_CALENDAR_REFRESH_INTERVAL_HOURS,
            )
        return BaCalendarRefreshIntervalOption.fromHours(raw).hours
    }

    fun saveCalendarRefreshIntervalHours(hours: Int) {
        kv().encode(
            KEY_CALENDAR_REFRESH_INTERVAL_HOURS,
            BaCalendarRefreshIntervalOption.fromHours(hours).hours,
        )
        notifyChanged()
    }

    fun loadAccountState(): BaAccountStoreSnapshot {
        return migratedAccountStore().loadState()
    }

    fun migrateAccountsIfNeeded(): BaAccountMigrationResult {
        val keyValueStore = accountKeyValueStore()
        val store = accountStore(keyValueStore)
        return BaAccountMigration(
            accountStore = store,
            keyValueStore = keyValueStore,
        ).migrateLegacyIfNeeded()
    }

    fun loadSnapshot(): BaPageSnapshot =
        loadBaSettingsSnapshot(kv()).withActiveBaAccount(loadAccountState())

    fun loadReminderSnapshots(): List<BaAccountReminderSnapshot> {
        val accountState = loadAccountState()
        val baseSnapshot = loadBaSettingsSnapshot(kv())
        return accountState
            .accounts
            .filter { it.profile.enabled }
            .map { account ->
                BaAccountReminderSnapshot(
                    accountId = account.profile.id,
                    displayName = account.profile.displayName,
                    snapshot =
                        baseSnapshot.withBaAccount(
                            accountState = accountState,
                            account = account,
                        ),
                )
            }
    }

    fun loadCalendarCacheSnapshot(serverIndex: Int): BaCacheSnapshot = cacheStore().loadCalendarCacheSnapshot(serverIndex)

    fun loadPoolCacheSnapshot(serverIndex: Int): BaCacheSnapshot = cacheStore().loadPoolCacheSnapshot(serverIndex)

    fun loadServerIndex(): Int {
        val accountState = loadAccountState()
        return accountState
            .accounts
            .firstOrNull { it.profile.id == accountState.activeAccountId }
            ?.profile
            ?.serverIndex
            ?: kv().decodeInt(KEY_SERVER_INDEX, DEFAULT_SERVER_INDEX).coerceIn(0, 2)
    }

    fun saveServerIndex(index: Int) {
        val normalized = index.coerceIn(0, 2)
        kv().encode(KEY_SERVER_INDEX, normalized)
        migratedAccountStore().updateActiveAccountProfile { profile ->
            profile.copy(serverIndex = normalized)
        }
        pruneCalendarPoolNotifiedKeysForCurrentPolicy()
        notifyChanged()
    }

    fun loadCafeLevel(): Int = kv().decodeInt(KEY_CAFE_LEVEL, DEFAULT_CAFE_LEVEL).coerceIn(1, 10)

    fun saveCafeLevel(level: Int) {
        val normalized = level.coerceIn(1, 10)
        kv().encode(KEY_CAFE_LEVEL, normalized)
        migratedAccountStore().updateActiveAccountRuntime { runtime ->
            runtime.copy(cafeLevel = normalized)
        }
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
        kv()
            .decodeInt(KEY_CAFE_AP_NOTIFY_THRESHOLD, DEFAULT_CAFE_AP_NOTIFY_THRESHOLD)
            .coerceIn(0, BA_AP_MAX)

    fun saveCafeApNotifyThreshold(threshold: Int) {
        kv().encode(KEY_CAFE_AP_NOTIFY_THRESHOLD, threshold.coerceIn(0, BA_AP_MAX))
        notifyChanged()
    }

    fun loadCafeApLastNotifiedLevel(): Int = kv().decodeInt(KEY_CAFE_AP_LAST_NOTIFIED_LEVEL, -1).coerceIn(-1, BA_AP_MAX)

    fun saveCafeApLastNotifiedLevel(level: Int) {
        val normalized = level.coerceIn(-1, BA_AP_MAX)
        kv().encode(KEY_CAFE_AP_LAST_NOTIFIED_LEVEL, normalized)
        migratedAccountStore().updateActiveAccountReminderRuntime { runtime ->
            runtime.copy(cafeApLastNotifiedLevel = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadIdIndependentByServerEnabled(): Boolean = idSettings().loadIndependentByServerEnabled()

    fun saveIdIndependentByServerEnabled(enabled: Boolean) {
        idSettings().saveIndependentByServerEnabled(enabled)
        notifyChanged()
    }

    fun loadIdNickname(serverIndex: Int? = null): String = idSettings().loadNickname(serverIndex)

    fun saveIdNickname(
        name: String,
        serverIndex: Int? = null,
    ) {
        idSettings().saveNickname(name, serverIndex)
        updateActiveAccountIdentity(
            serverIndex = serverIndex,
            nickname = name,
            friendCode = null,
        )
        notifyChanged()
    }

    fun loadIdFriendCode(serverIndex: Int? = null): String = idSettings().loadFriendCode(serverIndex)

    fun saveIdFriendCode(
        code: String,
        serverIndex: Int? = null,
    ) {
        idSettings().saveFriendCode(code, serverIndex)
        updateActiveAccountIdentity(
            serverIndex = serverIndex,
            nickname = null,
            friendCode = code,
        )
        notifyChanged()
    }

    fun loadApLimit(): Int =
        kv().decodeInt(KEY_AP_LIMIT, DEFAULT_AP_LIMIT).coerceIn(
            0,
            BA_AP_LIMIT_MAX,
        )

    fun saveApLimit(limit: Int) {
        val normalized = limit.coerceIn(0, BA_AP_LIMIT_MAX)
        kv().encode(KEY_AP_LIMIT, normalized)
        migratedAccountStore().updateActiveAccountRuntime { runtime ->
            runtime.copy(apLimit = normalized)
        }
        notifyChanged()
    }

    fun loadApNotifyEnabled(): Boolean = kv().decodeBool(KEY_AP_NOTIFY_ENABLED, false)

    fun saveApNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_AP_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadApNotifyThreshold(): Int = kv().decodeInt(KEY_AP_NOTIFY_THRESHOLD, DEFAULT_AP_NOTIFY_THRESHOLD).coerceIn(0, BA_AP_MAX)

    fun saveApNotifyThreshold(threshold: Int) {
        kv().encode(KEY_AP_NOTIFY_THRESHOLD, threshold.coerceIn(0, BA_AP_MAX))
        notifyChanged()
    }

    fun loadApLastNotifiedLevel(): Int = kv().decodeInt(KEY_AP_LAST_NOTIFIED_LEVEL, -1).coerceIn(-1, BA_AP_MAX)

    fun saveApLastNotifiedLevel(level: Int) {
        val normalized = level.coerceIn(-1, BA_AP_MAX)
        kv().encode(KEY_AP_LAST_NOTIFIED_LEVEL, normalized)
        migratedAccountStore().updateActiveAccountReminderRuntime { runtime ->
            runtime.copy(apLastNotifiedLevel = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadArenaRefreshNotifyEnabled(): Boolean = kv().decodeBool(KEY_ARENA_REFRESH_NOTIFY_ENABLED, false)

    fun saveArenaRefreshNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_ARENA_REFRESH_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadArenaRefreshLastNotifiedSlotMs(): Long = kv().decodeLong(KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS, 0L).coerceAtLeast(0L)

    fun saveArenaRefreshLastNotifiedSlotMs(slotMs: Long) {
        val normalized = slotMs.coerceAtLeast(0L)
        kv().encode(KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS, normalized)
        migratedAccountStore().updateActiveAccountReminderRuntime { runtime ->
            runtime.copy(arenaRefreshLastNotifiedSlotMs = normalized)
        }
        notifyChanged()
    }

    fun loadCafeVisitNotifyEnabled(): Boolean = kv().decodeBool(KEY_CAFE_VISIT_NOTIFY_ENABLED, false)

    fun saveCafeVisitNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CAFE_VISIT_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCafeVisitLastNotifiedSlotMs(): Long = kv().decodeLong(KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS, 0L).coerceAtLeast(0L)

    fun saveCafeVisitLastNotifiedSlotMs(slotMs: Long) {
        val normalized = slotMs.coerceAtLeast(0L)
        kv().encode(KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS, normalized)
        migratedAccountStore().updateActiveAccountReminderRuntime { runtime ->
            runtime.copy(cafeVisitLastNotifiedSlotMs = normalized)
        }
        notifyChanged()
    }

    fun resetReminderRuntimeForAccounts(accountIds: List<BaAccountId>) {
        val store = migratedAccountStore()
        accountIds.forEach { accountId ->
            store.updateAccountReminderRuntime(accountId) {
                BaAccountReminderRuntime()
            }
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadCalendarUpcomingNotifyEnabled(): Boolean = kv().decodeBool(KEY_CALENDAR_UPCOMING_NOTIFY_ENABLED, false)

    fun saveCalendarUpcomingNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CALENDAR_UPCOMING_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCalendarEndingNotifyEnabled(): Boolean = kv().decodeBool(KEY_CALENDAR_ENDING_NOTIFY_ENABLED, false)

    fun saveCalendarEndingNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CALENDAR_ENDING_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadPoolUpcomingNotifyEnabled(): Boolean = kv().decodeBool(KEY_POOL_UPCOMING_NOTIFY_ENABLED, false)

    fun savePoolUpcomingNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_POOL_UPCOMING_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadPoolEndingNotifyEnabled(): Boolean = kv().decodeBool(KEY_POOL_ENDING_NOTIFY_ENABLED, false)

    fun savePoolEndingNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_POOL_ENDING_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCalendarPoolChangeNotifyEnabled(): Boolean = kv().decodeBool(KEY_CALENDAR_POOL_CHANGE_NOTIFY_ENABLED, false)

    fun saveCalendarPoolChangeNotifyEnabled(enabled: Boolean) {
        kv().encode(KEY_CALENDAR_POOL_CHANGE_NOTIFY_ENABLED, enabled)
        notifyChanged()
    }

    fun loadCalendarPoolNotifyLeadHours(): Int =
        BaCalendarPoolNotifyLeadOption
            .fromHours(
                kv().decodeInt(KEY_CALENDAR_POOL_NOTIFY_LEAD_HOURS, 24),
            ).hours

    fun saveCalendarPoolNotifyLeadHours(hours: Int) {
        kv().encode(
            KEY_CALENDAR_POOL_NOTIFY_LEAD_HOURS,
            BaCalendarPoolNotifyLeadOption.fromHours(hours).hours,
        )
        notifyChanged()
    }

    fun loadCalendarPoolNotifiedKeys(): Set<String> =
        kv()
            .decodeString(KEY_CALENDAR_POOL_NOTIFIED_KEYS, "")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun markCalendarPoolNotified(key: String) {
        val normalized = key.trim()
        if (normalized.isBlank()) return
        val keys = (loadCalendarPoolNotifiedKeys() + normalized).toList().takeLast(500)
        kv().encode(KEY_CALENDAR_POOL_NOTIFIED_KEYS, keys.joinToString(separator = "\n"))
        notifyChanged()
    }

    fun replaceCalendarPoolNotifiedKeys(keys: Set<String>) {
        val normalized =
            keys
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
            calendarPoolChangeEnabled = snapshot.calendarPoolChangeNotifyEnabled,
        )
    }

    fun pruneCalendarPoolNotifiedKeysForPolicy(
        serverIndex: Int,
        leadHours: Int,
        calendarUpcomingEnabled: Boolean,
        calendarEndingEnabled: Boolean,
        poolUpcomingEnabled: Boolean,
        poolEndingEnabled: Boolean,
        calendarPoolChangeEnabled: Boolean,
    ) {
        val current = loadCalendarPoolNotifiedKeys()
        val retained =
            BaReminderCoordinator.retainNotifiedKeysForPolicy(
                keys = current,
                serverIndex = serverIndex,
                leadHours = leadHours,
                calendarUpcomingEnabled = calendarUpcomingEnabled,
                calendarEndingEnabled = calendarEndingEnabled,
                poolUpcomingEnabled = poolUpcomingEnabled,
                poolEndingEnabled = poolEndingEnabled,
                calendarPoolChangeEnabled = calendarPoolChangeEnabled,
            )
        if (retained != current) {
            replaceCalendarPoolNotifiedKeys(retained)
        }
    }

    fun loadApCurrent(): Double {
        val store = kv()
        val value =
            if (store.containsKey(KEY_AP_CURRENT_EXACT)) {
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
        migratedAccountStore().updateActiveAccountRuntime { runtime ->
            runtime.copy(
                apCurrent = apCurrent?.let(::normalizeAp) ?: runtime.apCurrent,
                apRegenBaseMs = apRegenBaseMs?.coerceAtLeast(0L) ?: runtime.apRegenBaseMs,
                apSyncMs = apSyncMs?.coerceAtLeast(0L) ?: runtime.apSyncMs,
                cafeStoredAp = cafeStoredAp?.let(::normalizeAp) ?: runtime.cafeStoredAp,
                cafeLastHourMs =
                    cafeLastHourMs
                        ?.coerceAtLeast(0L)
                        ?.let(::floorToHourMs)
                        ?: runtime.cafeLastHourMs,
            )
        }
        notifyChanged(notifyHomeOverview = notifyHomeOverview)
    }

    fun saveAccountBaRuntimeState(
        accountId: BaAccountId,
        apCurrent: Double? = null,
        apRegenBaseMs: Long? = null,
        apSyncMs: Long? = null,
        cafeStoredAp: Double? = null,
        cafeLastHourMs: Long? = null,
        notifyHomeOverview: Boolean = false,
    ) {
        migratedAccountStore().updateAccountRuntime(accountId) { runtime ->
            runtime.copy(
                apCurrent = apCurrent?.let(::normalizeAp) ?: runtime.apCurrent,
                apRegenBaseMs = apRegenBaseMs?.coerceAtLeast(0L) ?: runtime.apRegenBaseMs,
                apSyncMs = apSyncMs?.coerceAtLeast(0L) ?: runtime.apSyncMs,
                cafeStoredAp = cafeStoredAp?.let(::normalizeAp) ?: runtime.cafeStoredAp,
                cafeLastHourMs =
                    cafeLastHourMs
                        ?.coerceAtLeast(0L)
                        ?.let(::floorToHourMs)
                        ?: runtime.cafeLastHourMs,
            )
        }
        notifyChanged(notifyHomeOverview = notifyHomeOverview)
    }

    fun saveAccountApLastNotifiedLevel(
        accountId: BaAccountId,
        level: Int,
    ) {
        val normalized = level.coerceIn(-1, BA_AP_MAX)
        migratedAccountStore().updateAccountReminderRuntime(accountId) { runtime ->
            runtime.copy(apLastNotifiedLevel = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun saveAccountCafeApLastNotifiedLevel(
        accountId: BaAccountId,
        level: Int,
    ) {
        val normalized = level.coerceIn(-1, BA_AP_MAX)
        migratedAccountStore().updateAccountReminderRuntime(accountId) { runtime ->
            runtime.copy(cafeApLastNotifiedLevel = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun saveAccountArenaRefreshLastNotifiedSlotMs(
        accountId: BaAccountId,
        slotMs: Long,
    ) {
        val normalized = slotMs.coerceAtLeast(0L)
        migratedAccountStore().updateAccountReminderRuntime(accountId) { runtime ->
            runtime.copy(arenaRefreshLastNotifiedSlotMs = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun saveAccountCafeVisitLastNotifiedSlotMs(
        accountId: BaAccountId,
        slotMs: Long,
    ) {
        val normalized = slotMs.coerceAtLeast(0L)
        migratedAccountStore().updateAccountReminderRuntime(accountId) { runtime ->
            runtime.copy(cafeVisitLastNotifiedSlotMs = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadCoffeeHeadpatMs(): Long = kv().decodeLong(KEY_COFFEE_HEADPAT_MS, 0L)

    fun saveCoffeeHeadpatMs(epochMs: Long) {
        val normalized = epochMs.coerceAtLeast(0L)
        kv().encode(KEY_COFFEE_HEADPAT_MS, normalized)
        migratedAccountStore().updateActiveAccountRuntime { runtime ->
            runtime.copy(coffeeHeadpatMs = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadCoffeeInvite1UsedMs(): Long = kv().decodeLong(KEY_COFFEE_INVITE1_USED_MS, 0L)

    fun saveCoffeeInvite1UsedMs(epochMs: Long) {
        val normalized = epochMs.coerceAtLeast(0L)
        kv().encode(KEY_COFFEE_INVITE1_USED_MS, normalized)
        migratedAccountStore().updateActiveAccountRuntime { runtime ->
            runtime.copy(coffeeInvite1UsedMs = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun loadCoffeeInvite2UsedMs(): Long = kv().decodeLong(KEY_COFFEE_INVITE2_USED_MS, 0L)

    fun saveCoffeeInvite2UsedMs(epochMs: Long) {
        val normalized = epochMs.coerceAtLeast(0L)
        kv().encode(KEY_COFFEE_INVITE2_USED_MS, normalized)
        migratedAccountStore().updateActiveAccountRuntime { runtime ->
            runtime.copy(coffeeInvite2UsedMs = normalized)
        }
        notifyChanged(notifyHomeOverview = false)
    }

    fun clearCalendarAndPoolCaches() = cacheStore().clearCalendarAndPoolCaches()

    fun storageFootprintBytes(): Long = kv().totalSize()

    fun actualDataBytes(): Long = kv().actualSize()

    fun cacheBytesEstimated(): Long = cacheStore().cacheBytesEstimated()

    fun configBytesEstimated(): Long {
        val snapshot = loadSnapshot()
        var bytes =
            listOf(
                snapshot.idNickname,
                snapshot.idFriendCode,
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

    private fun updateActiveAccountIdentity(
        serverIndex: Int?,
        nickname: String?,
        friendCode: String?,
    ) {
        migratedAccountStore().updateActiveAccountProfile { profile ->
            if (serverIndex != null && serverIndex.coerceIn(0, 2) != profile.serverIndex) {
                return@updateActiveAccountProfile profile
            }
            val nextNickname = nickname?.let(::sanitizeBaAccountNickname) ?: profile.nickname
            val nextFriendCode = friendCode?.let(::sanitizeBaAccountFriendCode) ?: profile.friendCode
            profile.copy(
                displayName =
                    if (nickname != null && profile.displayName == profile.nickname) {
                        nextNickname
                    } else {
                        profile.displayName
                    },
                nickname = nextNickname,
                friendCode = nextFriendCode,
            )
        }
    }
}
