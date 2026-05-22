package os.kei.ui.page.main.ba

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs

internal class BaOfficeActionCoordinator(
    private val context: Context,
    private val office: BaOfficeController,
    private val ui: BaPageUiController,
    private val scope: CoroutineScope,
    private val onRefreshCalendar: () -> Unit,
    private val onRefreshPool: () -> Unit,
    private val onOpenCalendarLink: (String) -> Unit,
    private val onOpenPoolStudentGuide: (String) -> Unit,
    private val onOpenGuideCatalog: () -> Unit,
) {
    fun buildContentActions(): BaPageContentActions =
        BaPageContentActions(
            onApCurrentInputChange = { office.apCurrentInput = it },
            onApCurrentDone = ::saveApCurrentInput,
            onApLimitInputChange = { office.apLimitInput = it },
            onApLimitDone = ::saveApLimitInput,
            onOverviewServerPopupAnchorBoundsChange = { ui.overviewServerPopupAnchorBounds = it },
            onOverviewServerPopupChange = { ui.showOverviewServerPopup = it },
            onCafeLevelPopupAnchorBoundsChange = { ui.cafeLevelPopupAnchorBounds = it },
            onCafeLevelPopupChange = { ui.showCafeLevelPopup = it },
            onCafeLevelChange = ::selectCafeLevel,
            onServerSelected = ::selectServer,
            onClaimCafeStoredAp = ::claimCafeStoredAp,
            onTouchHead = { persistCooldown(office.touchHead(ui.serverIndex)) },
            onForceResetHeadpatCooldown = { persistCooldown(office.forceResetHeadpatCooldown()) },
            onUseInviteTicket1 = { persistCooldown(office.useInviteTicket1()) },
            onForceResetInviteTicket1Cooldown = { persistCooldown(office.forceResetInviteTicket1Cooldown()) },
            onUseInviteTicket2 = { persistCooldown(office.useInviteTicket2()) },
            onForceResetInviteTicket2Cooldown = { persistCooldown(office.forceResetInviteTicket2Cooldown()) },
            onRefreshCalendar = onRefreshCalendar,
            onOpenCalendarLink = onOpenCalendarLink,
            onRefreshPool = onRefreshPool,
            onOpenPoolStudentGuide = onOpenPoolStudentGuide,
            onOpenGuideCatalog = onOpenGuideCatalog,
            onIdNicknameInputChange = { office.idNicknameInput = it },
            onSaveIdNickname = { persistIdentity(office.saveIdNicknameFromInput(ui.serverIndex)) },
            onIdFriendCodeInputChange = { office.idFriendCodeInput = it },
            onSaveIdFriendCode = { persistIdentity(office.saveIdFriendCodeFromInput(context, ui.serverIndex)) },
        )

    private fun saveApCurrentInput() {
        val finalValue = office.apCurrentInput.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 0
        persistRuntime(office.updateCurrentAp(finalValue, markSync = true))
        AppBackgroundScheduler.scheduleBaApThreshold(context)
        office.apCurrentInput = finalValue.toString()
    }

    private fun saveApLimitInput() {
        val finalValue =
            office.apLimitInput.toIntOrNull()?.coerceIn(0, BA_AP_LIMIT_MAX)
                ?: BA_AP_LIMIT_MAX
        val limitUpdate = office.updateApLimit(finalValue)
        scope.launch {
            BaOfficeRepository.saveApLimitAsync(limitUpdate.limit)
            limitUpdate.runtimeUpdate?.persistAsync()
            office.applyApRegen()?.persistAsync()
        }
        AppBackgroundScheduler.scheduleBaApThreshold(context)
        office.apLimitInput = finalValue.toString()
    }

    private fun selectCafeLevel(level: Int) {
        val normalized = level.coerceIn(1, 10)
        val storageUpdate = office.applyCafeStorageUpdate()
        office.cafeLevel = normalized
        val clampUpdate = office.clampCafeStoredToCapUpdate()
        scope.launch {
            storageUpdate?.persistAsync()
            BaOfficeRepository.saveCafeLevelAsync(normalized)
            clampUpdate?.persistAsync()
        }
        ui.sheetCafeLevel = normalized
        ui.showCafeLevelPopup = false
    }

    private fun selectServer(selected: Int) {
        ui.serverIndex = selected
        scope.launch {
            BaOfficeRepository.saveServerIndexAsync(selected)
            if (office.idIndependentByServer) {
                office.applyIdentity(BaOfficeRepository.loadIdentityForServer(selected))
            }
            AppBackgroundScheduler.scheduleBaApThreshold(context)
        }
        resetCafeVisitBaselineIfNeeded(selected)
        resetArenaRefreshBaselineIfNeeded(selected)
        onRefreshCalendar()
        onRefreshPool()
        ui.showOverviewServerPopup = false
    }

    private fun claimCafeStoredAp() {
        persistRuntime(office.claimCafeStoredAp(context))
        AppBackgroundScheduler.scheduleBaApThreshold(context)
    }

    private fun persistRuntime(update: BaRuntimePersistenceUpdate?) {
        if (update == null) return
        scope.launch {
            update.persistAsync()
        }
    }

    private fun persistIdentity(update: BaOfficeIdentityPersistenceUpdate?) {
        if (update == null) return
        scope.launch {
            update.persistAsync()
        }
    }

    private fun persistCooldown(update: BaOfficeCooldownPersistenceUpdate?) {
        if (update == null) return
        scope.launch {
            update.persistAsync()
        }
    }

    private fun resetCafeVisitBaselineIfNeeded(serverIndex: Int) {
        if (!office.cafeVisitNotifyEnabled) return
        val baselineSlotMs =
            currentCafeStudentRefreshSlotMs(
                nowMs = System.currentTimeMillis(),
                serverIndex = serverIndex,
            )
        office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
        scope.launch {
            BaOfficeRepository.saveCafeVisitLastNotifiedSlotMsAsync(baselineSlotMs)
        }
    }

    private fun resetArenaRefreshBaselineIfNeeded(serverIndex: Int) {
        if (!office.arenaRefreshNotifyEnabled) return
        val baselineSlotMs =
            currentArenaRefreshSlotMs(
                nowMs = System.currentTimeMillis(),
                serverIndex = serverIndex,
            )
        office.arenaRefreshLastNotifiedSlotMs = baselineSlotMs
        scope.launch {
            BaOfficeRepository.saveArenaRefreshLastNotifiedSlotMsAsync(baselineSlotMs)
        }
    }
}
