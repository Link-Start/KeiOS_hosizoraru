package os.kei.ui.page.main.ba

import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountProfileInput
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal data class BaOfficeIdentity(
    val nickname: String,
    val friendCode: String,
)

internal object BaOfficeRepository {
    fun loadSnapshot(): BaPageSnapshot = BASettingsStore.loadSnapshot()

    suspend fun loadSnapshotAsync(): BaPageSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.loadSnapshot()
        }

    fun loadAccountState(): BaAccountStoreSnapshot = BASettingsStore.loadAccountState()

    suspend fun loadAccountStateAsync(): BaAccountStoreSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.loadAccountState()
        }

    suspend fun selectActiveAccountAsync(accountId: BaAccountId): BaPageSnapshot? =
        withContext(AppDispatchers.baFetch) {
            if (BASettingsStore.selectActiveAccount(accountId)) {
                BASettingsStore.loadSnapshot()
            } else {
                null
            }
        }

    suspend fun saveAllAccountsFollowGlobalNotificationSettingsAsync(enabled: Boolean): BaAccountStoreSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveAllAccountsFollowGlobalNotificationSettings(enabled)
            BASettingsStore.loadAccountState()
        }

    suspend fun saveAccountEnabledAsync(
        accountId: BaAccountId,
        enabled: Boolean,
    ): BaAccountStoreSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveAccountEnabled(accountId, enabled)
            BASettingsStore.loadAccountState()
        }

    suspend fun addAccountAsync(input: BaAccountProfileInput): BaAccountStoreSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.addAccount(input)
        }

    suspend fun updateAccountProfileAsync(
        accountId: BaAccountId,
        input: BaAccountProfileInput,
    ): BaAccountStoreSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.updateAccountProfile(
                accountId = accountId,
                input = input,
            )
        }

    suspend fun deleteAccountAsync(accountId: BaAccountId): BaAccountStoreSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.deleteAccount(accountId)
        }

    suspend fun moveAccountAsync(
        accountId: BaAccountId,
        offset: Int,
    ): BaAccountStoreSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.moveAccount(accountId = accountId, offset = offset)
        }

    fun loadServerIndex(): Int = BASettingsStore.loadServerIndex()

    suspend fun loadServerIndexAsync(): Int =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.loadServerIndex()
        }

    fun clearListScrollState() {
        BASettingsStore.clearListScrollState()
    }

    suspend fun clearListScrollStateAsync() {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.clearListScrollState()
        }
    }

    fun loadIdNickname(serverIndex: Int): String = BASettingsStore.loadIdNickname(serverIndex)

    fun loadIdFriendCode(serverIndex: Int): String = BASettingsStore.loadIdFriendCode(serverIndex)

    suspend fun loadIdentityForServer(serverIndex: Int): BaOfficeIdentity =
        withContext(AppDispatchers.baFetch) {
            BaOfficeIdentity(
                nickname = BASettingsStore.loadIdNickname(serverIndex),
                friendCode = BASettingsStore.loadIdFriendCode(serverIndex),
            )
        }

    fun saveIdIndependentByServerEnabled(enabled: Boolean) {
        BASettingsStore.saveIdIndependentByServerEnabled(enabled)
    }

    suspend fun saveIdIndependentByServerEnabledAsync(enabled: Boolean) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveIdIndependentByServerEnabled(enabled)
        }
    }

    fun saveIdNickname(
        name: String,
        serverIndex: Int? = null,
    ) {
        BASettingsStore.saveIdNickname(name, serverIndex)
    }

    suspend fun saveIdNicknameAsync(
        name: String,
        serverIndex: Int? = null,
    ) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveIdNickname(name, serverIndex)
        }
    }

    fun saveIdFriendCode(
        code: String,
        serverIndex: Int? = null,
    ) {
        BASettingsStore.saveIdFriendCode(code, serverIndex)
    }

    suspend fun saveIdFriendCodeAsync(
        code: String,
        serverIndex: Int? = null,
    ) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveIdFriendCode(code, serverIndex)
        }
    }

    fun saveApRegenBaseMs(epochMs: Long) {
        BASettingsStore.saveApRegenBaseMs(epochMs)
    }

    fun saveCafeLastHourMs(epochMs: Long) {
        BASettingsStore.saveCafeLastHourMs(epochMs)
    }

    fun saveCafeStoredAp(storedAp: Double) {
        BASettingsStore.saveCafeStoredAp(storedAp)
    }

    fun saveCafeApLastNotifiedLevel(level: Int) {
        BASettingsStore.saveCafeApLastNotifiedLevel(level)
    }

    fun saveAccountCafeApLastNotifiedLevel(
        accountId: BaAccountId,
        level: Int,
    ) {
        BASettingsStore.saveAccountCafeApLastNotifiedLevel(accountId, level)
    }

    fun saveApLastNotifiedLevel(level: Int) {
        BASettingsStore.saveApLastNotifiedLevel(level)
    }

    fun saveAccountApLastNotifiedLevel(
        accountId: BaAccountId,
        level: Int,
    ) {
        BASettingsStore.saveAccountApLastNotifiedLevel(accountId, level)
    }

    fun saveApLimit(limit: Int) {
        BASettingsStore.saveApLimit(limit)
    }

    suspend fun saveApLimitAsync(limit: Int) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveApLimit(limit)
        }
    }

    fun saveCafeLevel(level: Int) {
        BASettingsStore.saveCafeLevel(level)
    }

    fun saveServerIndex(index: Int) {
        BASettingsStore.saveServerIndex(index)
    }

    suspend fun saveServerIndexAsync(index: Int) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveServerIndex(index)
        }
    }

    fun saveCafeVisitLastNotifiedSlotMs(slotMs: Long) {
        BASettingsStore.saveCafeVisitLastNotifiedSlotMs(slotMs)
    }

    suspend fun saveCafeVisitLastNotifiedSlotMsAsync(slotMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCafeVisitLastNotifiedSlotMs(slotMs)
        }
    }

    fun saveArenaRefreshLastNotifiedSlotMs(slotMs: Long) {
        BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(slotMs)
    }

    suspend fun saveArenaRefreshLastNotifiedSlotMsAsync(slotMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(slotMs)
        }
    }

    suspend fun saveCafeLevelAsync(level: Int) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCafeLevel(level)
        }
    }

    fun saveCoffeeHeadpatMs(epochMs: Long) {
        BASettingsStore.saveCoffeeHeadpatMs(epochMs)
    }

    suspend fun saveCoffeeHeadpatMsAsync(epochMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCoffeeHeadpatMs(epochMs)
        }
    }

    fun saveCoffeeInvite1UsedMs(epochMs: Long) {
        BASettingsStore.saveCoffeeInvite1UsedMs(epochMs)
    }

    suspend fun saveCoffeeInvite1UsedMsAsync(epochMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCoffeeInvite1UsedMs(epochMs)
        }
    }

    fun saveCoffeeInvite2UsedMs(epochMs: Long) {
        BASettingsStore.saveCoffeeInvite2UsedMs(epochMs)
    }

    suspend fun saveCoffeeInvite2UsedMsAsync(epochMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCoffeeInvite2UsedMs(epochMs)
        }
    }

    fun saveRuntimeState(
        accountId: BaAccountId? = null,
        apCurrent: Double? = null,
        apRegenBaseMs: Long? = null,
        apSyncMs: Long? = null,
        cafeStoredAp: Double? = null,
        cafeLastHourMs: Long? = null,
        notifyHomeOverview: Boolean = true,
    ) {
        if (accountId == null) {
            BASettingsStore.saveBaRuntimeState(
                apCurrent = apCurrent,
                apRegenBaseMs = apRegenBaseMs,
                apSyncMs = apSyncMs,
                cafeStoredAp = cafeStoredAp,
                cafeLastHourMs = cafeLastHourMs,
                notifyHomeOverview = notifyHomeOverview,
            )
        } else {
            BASettingsStore.saveAccountBaRuntimeState(
                accountId = accountId,
                apCurrent = apCurrent,
                apRegenBaseMs = apRegenBaseMs,
                apSyncMs = apSyncMs,
                cafeStoredAp = cafeStoredAp,
                cafeLastHourMs = cafeLastHourMs,
                notifyHomeOverview = notifyHomeOverview,
            )
        }
    }
}
