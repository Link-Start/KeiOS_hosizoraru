package os.kei.ui.page.main.ba.support

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import os.kei.core.json.KeiJson

internal const val KEY_BA_ACCOUNTS_V1 = "ba_accounts_v1"
internal const val KEY_BA_ACTIVE_ACCOUNT_ID = "active_account_id"
internal const val KEY_BA_ACCOUNTS_ALL_FOLLOW_GLOBAL_NOTIFICATIONS =
    "ba_accounts_all_follow_global_notifications"

internal interface BaAccountKeyValueStore : BaIdKeyValueStore {
    fun decodeInt(key: String, defaultValue: Int): Int
    fun encode(key: String, value: Int)
    fun decodeLong(key: String, defaultValue: Long): Long
    fun encode(key: String, value: Long)
    fun containsKey(key: String): Boolean
    fun removeValueForKey(key: String)
}

internal class BaAccountStore(
    private val store: BaAccountKeyValueStore,
) {
    fun loadState(): BaAccountStoreSnapshot {
        val accounts = loadAccounts()
        return BaAccountStoreSnapshot(
            accounts = accounts,
            activeAccountId = resolveActiveAccountId(accounts),
            allAccountsFollowGlobalNotificationSettings = loadAllAccountsFollowGlobalNotificationSettings(),
            globalReminderSettings = loadGlobalReminderSettings(),
        )
    }

    fun loadAccounts(): List<BaAccountRecord> {
        val raw = store.decodeString(KEY_BA_ACCOUNTS_V1, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            KeiJson.lenient
                .decodeFromString<List<BaAccountRecord>>(raw)
                .normalizedAccounts()
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    fun saveAccounts(accounts: List<BaAccountRecord>) {
        val normalized = accounts.normalizedAccounts()
        store.encode(KEY_BA_ACCOUNTS_V1, KeiJson.lenient.encodeToString(normalized))
        repairActiveAccount(normalized)
    }

    fun replaceAll(
        accounts: List<BaAccountRecord>,
        activeAccountId: BaAccountId?,
    ) {
        val normalized = accounts.normalizedAccounts()
        store.encode(KEY_BA_ACCOUNTS_V1, KeiJson.lenient.encodeToString(normalized))
        val resolvedActive =
            activeAccountId?.takeIf { candidate ->
                normalized.any { it.profile.id == candidate }
            } ?: normalized.firstOrNull()?.profile?.id
        if (resolvedActive == null) {
            store.removeValueForKey(KEY_BA_ACTIVE_ACCOUNT_ID)
        } else {
            store.encode(KEY_BA_ACTIVE_ACCOUNT_ID, resolvedActive.value)
        }
    }

    fun addAccount(account: BaAccountRecord) {
        val current = loadAccounts()
        val accountId = account.profile.id
        val merged = current.filterNot { it.profile.id == accountId } + account
        saveAccounts(merged)
        if (loadActiveAccountId() == null) {
            selectActiveAccount(accountId)
        }
    }

    fun updateAccount(account: BaAccountRecord): Boolean {
        val current = loadAccounts()
        if (current.none { it.profile.id == account.profile.id }) return false
        saveAccounts(current.map { if (it.profile.id == account.profile.id) account else it })
        return true
    }

    fun updateActiveAccountProfile(transform: (BaAccountProfile) -> BaAccountProfile): Boolean =
        updateActiveAccountRecord { account ->
            account.copy(
                profile =
                    transform(account.profile).copy(
                        id = account.profile.id,
                    ),
            )
        }

    fun updateActiveAccountRuntime(transform: (BaAccountRuntime) -> BaAccountRuntime): Boolean =
        updateActiveAccountRecord { account ->
            account.copy(runtime = transform(account.runtime).normalized())
        }

    fun updateAccountRuntime(
        accountId: BaAccountId,
        transform: (BaAccountRuntime) -> BaAccountRuntime,
    ): Boolean =
        updateAccountRecord(accountId) { account ->
            account.copy(runtime = transform(account.runtime).normalized())
        }

    fun updateActiveAccountReminderRuntime(
        transform: (BaAccountReminderRuntime) -> BaAccountReminderRuntime,
    ): Boolean =
        updateActiveAccountRecord { account ->
            account.copy(reminderRuntime = transform(account.reminderRuntime).normalized())
        }

    fun updateAccountReminderRuntime(
        accountId: BaAccountId,
        transform: (BaAccountReminderRuntime) -> BaAccountReminderRuntime,
    ): Boolean =
        updateAccountRecord(accountId) { account ->
            account.copy(reminderRuntime = transform(account.reminderRuntime).normalized())
        }

    fun deleteAccount(accountId: BaAccountId): Boolean {
        val current = loadAccounts()
        val updated = current.filterNot { it.profile.id == accountId }
        if (updated.size == current.size) return false
        saveAccounts(updated)
        return true
    }

    fun moveAccount(
        accountId: BaAccountId,
        offset: Int,
    ): Boolean {
        if (offset == 0) return false
        val current = loadAccounts()
        val fromIndex = current.indexOfFirst { it.profile.id == accountId }
        if (fromIndex < 0) return false
        val toIndex = (fromIndex + offset).coerceIn(0, current.lastIndex)
        if (fromIndex == toIndex) return false
        val mutable = current.toMutableList()
        val account = mutable.removeAt(fromIndex)
        mutable.add(toIndex, account)
        saveAccounts(
            mutable.mapIndexed { index, record ->
                record.copy(profile = record.profile.copy(sortOrder = index))
            },
        )
        return true
    }

    fun loadActiveAccountId(): BaAccountId? =
        store
            .decodeString(KEY_BA_ACTIVE_ACCOUNT_ID, "")
            .orEmpty()
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let(::BaAccountId)

    fun selectActiveAccount(accountId: BaAccountId): Boolean {
        if (loadAccounts().none { it.profile.id == accountId }) return false
        store.encode(KEY_BA_ACTIVE_ACCOUNT_ID, accountId.value)
        return true
    }

    fun repairActiveAccount(): BaAccountId? = repairActiveAccount(loadAccounts())

    fun loadAllAccountsFollowGlobalNotificationSettings(): Boolean =
        store.decodeBool(KEY_BA_ACCOUNTS_ALL_FOLLOW_GLOBAL_NOTIFICATIONS, true)

    fun saveAllAccountsFollowGlobalNotificationSettings(enabled: Boolean) {
        store.encode(KEY_BA_ACCOUNTS_ALL_FOLLOW_GLOBAL_NOTIFICATIONS, enabled)
    }

    fun loadGlobalReminderSettings(): BaGlobalReminderSettings =
        BaGlobalReminderSettings(
            apNotifyEnabled = store.decodeBool(KEY_AP_NOTIFY_ENABLED, false),
            apNotifyThreshold =
                store
                    .decodeInt(KEY_AP_NOTIFY_THRESHOLD, DEFAULT_AP_NOTIFY_THRESHOLD)
                    .coerceIn(0, BA_AP_MAX),
            cafeApNotifyEnabled = store.decodeBool(KEY_CAFE_AP_NOTIFY_ENABLED, false),
            cafeApNotifyThreshold =
                store
                    .decodeInt(KEY_CAFE_AP_NOTIFY_THRESHOLD, DEFAULT_CAFE_AP_NOTIFY_THRESHOLD)
                    .coerceIn(0, BA_AP_MAX),
            arenaRefreshNotifyEnabled = store.decodeBool(KEY_ARENA_REFRESH_NOTIFY_ENABLED, false),
            cafeVisitNotifyEnabled = store.decodeBool(KEY_CAFE_VISIT_NOTIFY_ENABLED, false),
        )

    fun saveGlobalReminderSettings(settings: BaGlobalReminderSettings) {
        val normalized = settings.normalized()
        store.encode(KEY_AP_NOTIFY_ENABLED, normalized.apNotifyEnabled)
        store.encode(KEY_AP_NOTIFY_THRESHOLD, normalized.apNotifyThreshold)
        store.encode(KEY_CAFE_AP_NOTIFY_ENABLED, normalized.cafeApNotifyEnabled)
        store.encode(KEY_CAFE_AP_NOTIFY_THRESHOLD, normalized.cafeApNotifyThreshold)
        store.encode(KEY_ARENA_REFRESH_NOTIFY_ENABLED, normalized.arenaRefreshNotifyEnabled)
        store.encode(KEY_CAFE_VISIT_NOTIFY_ENABLED, normalized.cafeVisitNotifyEnabled)
    }

    private fun repairActiveAccount(accounts: List<BaAccountRecord>): BaAccountId? {
        val resolved = resolveActiveAccountId(accounts)
        if (resolved == null) {
            store.removeValueForKey(KEY_BA_ACTIVE_ACCOUNT_ID)
            return null
        }
        if (loadActiveAccountId() != resolved) {
            store.encode(KEY_BA_ACTIVE_ACCOUNT_ID, resolved.value)
        }
        return resolved
    }

    private fun resolveActiveAccountId(accounts: List<BaAccountRecord>): BaAccountId? {
        val active = loadActiveAccountId()
        if (active != null && accounts.any { it.profile.id == active }) return active
        return accounts.firstOrNull()?.profile?.id
    }

    private fun updateActiveAccountRecord(transform: (BaAccountRecord) -> BaAccountRecord): Boolean {
        val current = loadAccounts()
        val activeAccountId = resolveActiveAccountId(current) ?: return false
        return updateAccountRecord(
            accounts = current,
            accountId = activeAccountId,
            transform = transform,
        )
    }

    private fun updateAccountRecord(
        accountId: BaAccountId,
        transform: (BaAccountRecord) -> BaAccountRecord,
    ): Boolean =
        updateAccountRecord(
            accounts = loadAccounts(),
            accountId = accountId,
            transform = transform,
        )

    private fun updateAccountRecord(
        accounts: List<BaAccountRecord>,
        accountId: BaAccountId,
        transform: (BaAccountRecord) -> BaAccountRecord,
    ): Boolean {
        var changed = false
        val updated =
            accounts.map { account ->
                if (account.profile.id != accountId) {
                    account
                } else {
                    changed = true
                    val transformed = transform(account)
                    transformed.copy(
                        profile =
                            transformed.profile.copy(
                                id = accountId,
                            ),
                    )
                }
            }
        if (!changed) return false
        saveAccounts(updated)
        return true
    }
}

internal fun List<BaAccountRecord>.normalizedAccounts(): List<BaAccountRecord> {
    val seen = LinkedHashSet<String>()
    return mapIndexedNotNull { index, account ->
        val normalized = account.normalized(defaultSortOrder = index) ?: return@mapIndexedNotNull null
        if (!seen.add(normalized.profile.id.value)) return@mapIndexedNotNull null
        normalized
    }.sortedWith(
        compareBy<BaAccountRecord> { it.profile.sortOrder }
            .thenBy { it.profile.id.value },
    )
}
