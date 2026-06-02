package os.kei.ui.page.main.ba

import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs

internal data class BaOfficeServerSelectionPersistenceResult(
    val serverIndex: Int,
    val identity: BaOfficeIdentity?,
    val cafeVisitLastNotifiedSlotMs: Long?,
    val arenaRefreshLastNotifiedSlotMs: Long?,
)

internal data class BaOfficeServerRestorePersistenceResult(
    val serverIndex: Int,
    val identity: BaOfficeIdentity?,
)

internal data class BaOfficeSettingsSavePersistenceResult(
    val persisted: BaSettingsPersistenceResult,
)

internal data class BaOfficeNotificationSavePersistenceResult(
    val persisted: BaNotificationSettingsPersistenceResult,
    val savedDraft: BaPageNotificationDraftState,
    val resetCafeApLastNotifiedLevel: Boolean,
    val arenaRefreshLastNotifiedSlotMs: Long?,
    val cafeVisitLastNotifiedSlotMs: Long?,
)

internal class BaOfficePageRepository(
    private val clock: BaOfficeClock = BaSystemOfficeClock,
) {
    suspend fun loadInitialSnapshot(): BaPageSnapshot = BaOfficeRepository.loadSnapshotAsync()

    suspend fun loadAccountState(): BaAccountStoreSnapshot = BaOfficeRepository.loadAccountStateAsync()

    suspend fun selectActiveAccount(accountId: BaAccountId): BaPageSnapshot? =
        BaOfficeRepository.selectActiveAccountAsync(accountId)

    suspend fun clearListScrollState() {
        BaOfficeRepository.clearListScrollStateAsync()
    }

    suspend fun persistServerSelection(
        requestedIndex: Int,
        idIndependentByServer: Boolean,
        cafeVisitNotifyEnabled: Boolean,
        arenaRefreshNotifyEnabled: Boolean,
    ): BaOfficeServerSelectionPersistenceResult =
        withContext(AppDispatchers.baFetch) {
            val selected = requestedIndex.coerceIn(0, 2)
            BaOfficeRepository.saveServerIndex(selected)
            val identity =
                if (idIndependentByServer) {
                    BaOfficeIdentity(
                        nickname = BaOfficeRepository.loadIdNickname(selected),
                        friendCode = BaOfficeRepository.loadIdFriendCode(selected),
                    )
                } else {
                    null
                }
            val nowMs = clock.nowMs()
            val cafeVisitSlot =
                if (cafeVisitNotifyEnabled) {
                    currentCafeStudentRefreshSlotMs(
                        nowMs = nowMs,
                        serverIndex = selected,
                    ).also(BaOfficeRepository::saveCafeVisitLastNotifiedSlotMs)
                } else {
                    null
                }
            val arenaRefreshSlot =
                if (arenaRefreshNotifyEnabled) {
                    currentArenaRefreshSlotMs(
                        nowMs = nowMs,
                        serverIndex = selected,
                    ).also(BaOfficeRepository::saveArenaRefreshLastNotifiedSlotMs)
                } else {
                    null
                }
            BaOfficeServerSelectionPersistenceResult(
                serverIndex = selected,
                identity = identity,
                cafeVisitLastNotifiedSlotMs = cafeVisitSlot,
                arenaRefreshLastNotifiedSlotMs = arenaRefreshSlot,
            )
        }

    suspend fun restoreServerSelection(
        currentServerIndex: Int,
        idIndependentByServer: Boolean,
    ): BaOfficeServerRestorePersistenceResult? =
        withContext(AppDispatchers.baFetch) {
            val savedServerIndex = BaOfficeRepository.loadServerIndex()
            if (savedServerIndex == currentServerIndex) {
                return@withContext null
            }
            val identity =
                if (idIndependentByServer) {
                    BaOfficeIdentity(
                        nickname = BaOfficeRepository.loadIdNickname(savedServerIndex),
                        friendCode = BaOfficeRepository.loadIdFriendCode(savedServerIndex),
                    )
                } else {
                    null
                }
            BaOfficeServerRestorePersistenceResult(
                serverIndex = savedServerIndex,
                identity = identity,
            )
        }

    suspend fun persistSettings(sheetState: BaSettingsSheetState): BaOfficeSettingsSavePersistenceResult =
        withContext(AppDispatchers.baFetch) {
            BaOfficeSettingsSavePersistenceResult(
                persisted = BaSettingsPersistenceRepository.persistSettingsDraft(sheetState),
            )
        }

    suspend fun persistNotificationSettings(
        sheetState: BaNotificationSettingsSheetState,
        previousCafeApNotifyEnabled: Boolean,
        previousCafeApNotifyThreshold: Int,
        previousArenaRefreshNotifyEnabled: Boolean,
        previousCafeVisitNotifyEnabled: Boolean,
        serverIndex: Int,
    ): BaOfficeNotificationSavePersistenceResult =
        withContext(AppDispatchers.baFetch) {
            val persisted = BaSettingsPersistenceRepository.persistNotificationSettingsDraft(sheetState)
            val resetCafeApLastNotifiedLevel =
                !persisted.cafeApNotifyEnabled ||
                    previousCafeApNotifyThreshold != persisted.savedCafeApThreshold ||
                    !previousCafeApNotifyEnabled
            if (resetCafeApLastNotifiedLevel) {
                BaSettingsPersistenceRepository.resetCafeApLastNotifiedLevel()
            }
            val nowMs = clock.nowMs()
            val arenaRefreshSlot =
                when {
                    !persisted.arenaRefreshNotifyEnabled -> {
                        BaSettingsPersistenceRepository.resetArenaRefreshLastNotifiedSlot()
                        0L
                    }

                    !previousArenaRefreshNotifyEnabled -> {
                        currentArenaRefreshSlotMs(
                            nowMs = nowMs,
                            serverIndex = serverIndex,
                        ).also(BaSettingsPersistenceRepository::saveArenaRefreshLastNotifiedSlot)
                    }

                    else -> {
                        null
                    }
                }
            val cafeVisitSlot =
                when {
                    !persisted.cafeVisitNotifyEnabled -> {
                        BaSettingsPersistenceRepository.resetCafeVisitLastNotifiedSlot()
                        0L
                    }

                    !previousCafeVisitNotifyEnabled -> {
                        currentCafeStudentRefreshSlotMs(
                            nowMs = nowMs,
                            serverIndex = serverIndex,
                        ).also(BaSettingsPersistenceRepository::saveCafeVisitLastNotifiedSlot)
                    }

                    else -> {
                        null
                    }
                }
            BaOfficeNotificationSavePersistenceResult(
                persisted = persisted,
                savedDraft = sheetState.toSavedDraft(persisted),
                resetCafeApLastNotifiedLevel = resetCafeApLastNotifiedLevel,
                arenaRefreshLastNotifiedSlotMs = arenaRefreshSlot,
                cafeVisitLastNotifiedSlotMs = cafeVisitSlot,
            )
        }

}

private fun BaNotificationSettingsSheetState.toSavedDraft(
    persisted: BaNotificationSettingsPersistenceResult,
): BaPageNotificationDraftState =
    BaPageNotificationDraftState(
        apNotifyEnabled = apNotifyEnabled,
        cafeApNotifyEnabled = persisted.cafeApNotifyEnabled,
        arenaRefreshNotifyEnabled = persisted.arenaRefreshNotifyEnabled,
        cafeVisitNotifyEnabled = persisted.cafeVisitNotifyEnabled,
        calendarUpcomingNotifyEnabled = persisted.calendarUpcomingNotifyEnabled,
        calendarEndingNotifyEnabled = persisted.calendarEndingNotifyEnabled,
        poolUpcomingNotifyEnabled = persisted.poolUpcomingNotifyEnabled,
        poolEndingNotifyEnabled = persisted.poolEndingNotifyEnabled,
        calendarPoolChangeNotifyEnabled = persisted.calendarPoolChangeNotifyEnabled,
        calendarPoolNotifyLeadHours = persisted.calendarPoolNotifyLeadHours,
        apNotifyThresholdText = persisted.savedThreshold.toString(),
        cafeApNotifyThresholdText = persisted.savedCafeApThreshold.toString(),
    )
