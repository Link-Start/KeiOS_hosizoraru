package os.kei.ui.page.main.ba

import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal data class BaOfficeIdentity(
    val nickname: String,
    val friendCode: String,
)

/**
 * Repository for the BA office settings backed by [BASettingsStore]. All public APIs are
 * `suspend` and dispatch on [AppDispatchers.baFetch] so callers cannot accidentally execute
 * MMKV IO on the UI thread.
 */
internal object BaOfficeRepository {
    suspend fun loadSnapshotAsync(): BaPageSnapshot =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.loadSnapshot()
        }

    suspend fun loadServerIndexAsync(): Int =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.loadServerIndex()
        }

    suspend fun clearListScrollStateAsync() {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.clearListScrollState()
        }
    }

    suspend fun loadIdentityForServer(serverIndex: Int): BaOfficeIdentity =
        withContext(AppDispatchers.baFetch) {
            BaOfficeIdentity(
                nickname = BASettingsStore.loadIdNickname(serverIndex),
                friendCode = BASettingsStore.loadIdFriendCode(serverIndex),
            )
        }

    suspend fun saveIdIndependentByServerEnabledAsync(enabled: Boolean) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveIdIndependentByServerEnabled(enabled)
        }
    }

    suspend fun saveIdNicknameAsync(
        name: String,
        serverIndex: Int? = null,
    ) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveIdNickname(name, serverIndex)
        }
    }

    suspend fun saveIdFriendCodeAsync(
        code: String,
        serverIndex: Int? = null,
    ) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveIdFriendCode(code, serverIndex)
        }
    }

    suspend fun saveApLimitAsync(limit: Int) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveApLimit(limit)
        }
    }

    suspend fun saveServerIndexAsync(index: Int) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveServerIndex(index)
        }
    }

    suspend fun saveCafeVisitLastNotifiedSlotMsAsync(slotMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCafeVisitLastNotifiedSlotMs(slotMs)
        }
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

    suspend fun saveCoffeeHeadpatMsAsync(epochMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCoffeeHeadpatMs(epochMs)
        }
    }

    suspend fun saveCoffeeInvite1UsedMsAsync(epochMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCoffeeInvite1UsedMs(epochMs)
        }
    }

    suspend fun saveCoffeeInvite2UsedMsAsync(epochMs: Long) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCoffeeInvite2UsedMs(epochMs)
        }
    }

    /**
     * Persist runtime state on the caller's coroutine context. Callers must already be on
     * [AppDispatchers.baFetch] (use [saveRuntimeStateAsync] otherwise).
     */
    internal fun saveRuntimeStateBlocking(
        apCurrent: Double? = null,
        apRegenBaseMs: Long? = null,
        apSyncMs: Long? = null,
        cafeStoredAp: Double? = null,
        cafeLastHourMs: Long? = null,
        notifyHomeOverview: Boolean = true,
    ) {
        BASettingsStore.saveBaRuntimeState(
            apCurrent = apCurrent,
            apRegenBaseMs = apRegenBaseMs,
            apSyncMs = apSyncMs,
            cafeStoredAp = cafeStoredAp,
            cafeLastHourMs = cafeLastHourMs,
            notifyHomeOverview = notifyHomeOverview,
        )
    }

    internal fun saveCafeApLastNotifiedLevelBlocking(level: Int) {
        BASettingsStore.saveCafeApLastNotifiedLevel(level)
    }

    internal fun saveApLastNotifiedLevelBlocking(level: Int) {
        BASettingsStore.saveApLastNotifiedLevel(level)
    }

    suspend fun saveRuntimeStateAsync(
        apCurrent: Double? = null,
        apRegenBaseMs: Long? = null,
        apSyncMs: Long? = null,
        cafeStoredAp: Double? = null,
        cafeLastHourMs: Long? = null,
        notifyHomeOverview: Boolean = true,
    ) {
        withContext(AppDispatchers.baFetch) {
            saveRuntimeStateBlocking(
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
