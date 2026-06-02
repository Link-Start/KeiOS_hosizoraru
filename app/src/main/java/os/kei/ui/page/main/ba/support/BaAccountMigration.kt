package os.kei.ui.page.main.ba.support

internal enum class BaAccountMigrationStatus {
    Migrated,
    AlreadyInitialized,
}

internal data class BaAccountMigrationResult(
    val status: BaAccountMigrationStatus,
    val accountCount: Int,
    val activeAccountId: BaAccountId?,
)

internal class BaAccountMigration(
    private val accountStore: BaAccountStore,
    private val keyValueStore: BaAccountKeyValueStore,
) {
    fun migrateLegacyIfNeeded(): BaAccountMigrationResult {
        val existingAccounts = accountStore.loadAccounts()
        if (existingAccounts.isNotEmpty()) {
            return BaAccountMigrationResult(
                status = BaAccountMigrationStatus.AlreadyInitialized,
                accountCount = existingAccounts.size,
                activeAccountId = accountStore.repairActiveAccount(),
            )
        }

        val legacy = loadLegacySnapshot()
        val accounts =
            if (legacy.idIndependentByServer) {
                (0..2).map { serverIndex ->
                    buildLegacyServerAccount(
                        serverIndex = serverIndex,
                        legacy = legacy,
                        sortOrder = serverIndex,
                    )
                }
            } else {
                listOf(
                    buildLegacySharedAccount(legacy),
                )
            }
        val activeAccountId =
            if (legacy.idIndependentByServer) {
                BaAccountId("legacy-server-${legacy.serverIndex}")
            } else {
                LEGACY_MAIN_ACCOUNT_ID
            }
        accountStore.replaceAll(
            accounts = accounts,
            activeAccountId = activeAccountId,
        )
        accountStore.saveAllAccountsFollowGlobalNotificationSettings(true)

        return BaAccountMigrationResult(
            status = BaAccountMigrationStatus.Migrated,
            accountCount = accounts.size,
            activeAccountId = activeAccountId,
        )
    }

    private fun buildLegacySharedAccount(legacy: BaLegacyAccountSnapshot): BaAccountRecord =
        BaAccountRecord(
            profile =
                BaAccountProfile(
                    id = LEGACY_MAIN_ACCOUNT_ID,
                    serverIndex = legacy.serverIndex,
                    displayName = legacy.sharedNickname,
                    nickname = legacy.sharedNickname,
                    friendCode = legacy.sharedFriendCode,
                    notificationMode = BaAccountNotificationMode.FollowGlobal,
                    remindersEnabled = true,
                    enabled = true,
                    sortOrder = 0,
                ),
            runtime = legacy.runtime,
            reminderRuntime = legacy.reminderRuntime,
        )

    private fun buildLegacyServerAccount(
        serverIndex: Int,
        legacy: BaLegacyAccountSnapshot,
        sortOrder: Int,
    ): BaAccountRecord {
        val identity = legacy.serverIdentities.getValue(serverIndex)
        val isCurrentServer = serverIndex == legacy.serverIndex
        return BaAccountRecord(
            profile =
                BaAccountProfile(
                    id = BaAccountId("legacy-server-$serverIndex"),
                    serverIndex = serverIndex,
                    displayName = identity.nickname,
                    nickname = identity.nickname,
                    friendCode = identity.friendCode,
                    notificationMode = BaAccountNotificationMode.FollowGlobal,
                    remindersEnabled = true,
                    enabled = true,
                    sortOrder = sortOrder,
                ),
            runtime = if (isCurrentServer) legacy.runtime else BaAccountRuntime(),
            reminderRuntime = if (isCurrentServer) legacy.reminderRuntime else BaAccountReminderRuntime(),
        )
    }

    private fun loadLegacySnapshot(): BaLegacyAccountSnapshot {
        val idSettings = BaIdSettingsAccessor(keyValueStore)
        val serverIndex = keyValueStore.decodeInt(KEY_SERVER_INDEX, DEFAULT_SERVER_INDEX).coerceIn(0, 2)
        val independentByServer = idSettings.loadIndependentByServerEnabled()
        val sharedNickname = idSettings.loadNickname()
        val sharedFriendCode = idSettings.loadFriendCode()
        val identities =
            (0..2).associateWith { index ->
                BaLegacyIdentity(
                    nickname = idSettings.loadNickname(index),
                    friendCode = idSettings.loadFriendCode(index),
                )
            }
        return BaLegacyAccountSnapshot(
            serverIndex = serverIndex,
            idIndependentByServer = independentByServer,
            sharedNickname = sharedNickname,
            sharedFriendCode = sharedFriendCode,
            serverIdentities = identities,
            runtime = loadLegacyRuntime(),
            reminderRuntime = loadLegacyReminderRuntime(),
        )
    }

    private fun loadLegacyRuntime(): BaAccountRuntime {
        val apCurrent =
            if (keyValueStore.containsKey(KEY_AP_CURRENT_EXACT)) {
                keyValueStore
                    .decodeString(KEY_AP_CURRENT_EXACT, DEFAULT_AP_CURRENT.toString())
                    ?.toDoubleOrNull()
                    ?: DEFAULT_AP_CURRENT
            } else {
                keyValueStore.decodeInt(KEY_AP_CURRENT, DEFAULT_AP_CURRENT.toInt()).toDouble()
            }
        val cafeStoredAp =
            keyValueStore
                .decodeString(KEY_CAFE_STORED_AP, DEFAULT_CAFE_STORED_AP.toString())
                ?.toDoubleOrNull()
                ?: DEFAULT_CAFE_STORED_AP
        return BaAccountRuntime(
            apLimit = keyValueStore.decodeInt(KEY_AP_LIMIT, DEFAULT_AP_LIMIT),
            apCurrent = apCurrent,
            apRegenBaseMs = keyValueStore.decodeLong(KEY_AP_REGEN_BASE_MS, 0L),
            apSyncMs = keyValueStore.decodeLong(KEY_AP_SYNC_MS, 0L),
            cafeLevel = keyValueStore.decodeInt(KEY_CAFE_LEVEL, DEFAULT_CAFE_LEVEL),
            cafeStoredAp = cafeStoredAp,
            cafeLastHourMs = keyValueStore.decodeLong(KEY_CAFE_LAST_HOUR_MS, 0L),
            coffeeHeadpatMs = keyValueStore.decodeLong(KEY_COFFEE_HEADPAT_MS, 0L),
            coffeeInvite1UsedMs = keyValueStore.decodeLong(KEY_COFFEE_INVITE1_USED_MS, 0L),
            coffeeInvite2UsedMs = keyValueStore.decodeLong(KEY_COFFEE_INVITE2_USED_MS, 0L),
        ).normalized()
    }

    private fun loadLegacyReminderRuntime(): BaAccountReminderRuntime =
        BaAccountReminderRuntime(
            apLastNotifiedLevel = keyValueStore.decodeInt(KEY_AP_LAST_NOTIFIED_LEVEL, -1),
            cafeApLastNotifiedLevel = keyValueStore.decodeInt(KEY_CAFE_AP_LAST_NOTIFIED_LEVEL, -1),
            arenaRefreshLastNotifiedSlotMs =
                keyValueStore.decodeLong(KEY_ARENA_REFRESH_LAST_NOTIFIED_SLOT_MS, 0L),
            cafeVisitLastNotifiedSlotMs =
                keyValueStore.decodeLong(KEY_CAFE_VISIT_LAST_NOTIFIED_SLOT_MS, 0L),
        ).normalized()

    private data class BaLegacyAccountSnapshot(
        val serverIndex: Int,
        val idIndependentByServer: Boolean,
        val sharedNickname: String,
        val sharedFriendCode: String,
        val serverIdentities: Map<Int, BaLegacyIdentity>,
        val runtime: BaAccountRuntime,
        val reminderRuntime: BaAccountReminderRuntime,
    )

    private data class BaLegacyIdentity(
        val nickname: String,
        val friendCode: String,
    )

    private companion object {
        val LEGACY_MAIN_ACCOUNT_ID = BaAccountId("legacy-main")
    }
}
