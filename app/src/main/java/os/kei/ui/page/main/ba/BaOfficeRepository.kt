package os.kei.ui.page.main.ba

import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaPageSnapshot

internal object BaOfficeRepository {
    fun loadSnapshot(): BaPageSnapshot = BASettingsStore.loadSnapshot()

    fun loadServerIndex(): Int = BASettingsStore.loadServerIndex()

    fun clearListScrollState() {
        BASettingsStore.clearListScrollState()
    }

    fun loadIdNickname(serverIndex: Int): String = BASettingsStore.loadIdNickname(serverIndex)

    fun loadIdFriendCode(serverIndex: Int): String = BASettingsStore.loadIdFriendCode(serverIndex)

    fun saveIdIndependentByServerEnabled(enabled: Boolean) {
        BASettingsStore.saveIdIndependentByServerEnabled(enabled)
    }

    fun saveIdNickname(
        name: String,
        serverIndex: Int? = null,
    ) {
        BASettingsStore.saveIdNickname(name, serverIndex)
    }

    fun saveIdFriendCode(
        code: String,
        serverIndex: Int? = null,
    ) {
        BASettingsStore.saveIdFriendCode(code, serverIndex)
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

    fun saveApLastNotifiedLevel(level: Int) {
        BASettingsStore.saveApLastNotifiedLevel(level)
    }

    fun saveApLimit(limit: Int) {
        BASettingsStore.saveApLimit(limit)
    }

    fun saveCafeLevel(level: Int) {
        BASettingsStore.saveCafeLevel(level)
    }

    fun saveServerIndex(index: Int) {
        BASettingsStore.saveServerIndex(index)
    }

    fun saveCafeVisitLastNotifiedSlotMs(slotMs: Long) {
        BASettingsStore.saveCafeVisitLastNotifiedSlotMs(slotMs)
    }

    fun saveArenaRefreshLastNotifiedSlotMs(slotMs: Long) {
        BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(slotMs)
    }

    fun saveCoffeeHeadpatMs(epochMs: Long) {
        BASettingsStore.saveCoffeeHeadpatMs(epochMs)
    }

    fun saveCoffeeInvite1UsedMs(epochMs: Long) {
        BASettingsStore.saveCoffeeInvite1UsedMs(epochMs)
    }

    fun saveCoffeeInvite2UsedMs(epochMs: Long) {
        BASettingsStore.saveCoffeeInvite2UsedMs(epochMs)
    }

    fun saveRuntimeState(
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
}
