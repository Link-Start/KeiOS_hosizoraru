package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs

internal class BaOfficeActionCoordinator(
    private val context: Context,
    private val office: BaOfficeController,
    private val ui: BaPageUiController,
    private val onRefreshCalendar: () -> Unit,
    private val onRefreshPool: () -> Unit,
    private val onOpenCalendarLink: (String) -> Unit,
    private val onOpenPoolStudentGuide: (String) -> Unit,
    private val onOpenGuideCatalog: () -> Unit,
) {
    fun buildContentActions(): BaPageContentActions {
        return BaPageContentActions(
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
            onTouchHead = { office.touchHead(ui.serverIndex) },
            onForceResetHeadpatCooldown = { office.forceResetHeadpatCooldown() },
            onUseInviteTicket1 = { office.useInviteTicket1() },
            onForceResetInviteTicket1Cooldown = { office.forceResetInviteTicket1Cooldown() },
            onUseInviteTicket2 = { office.useInviteTicket2() },
            onForceResetInviteTicket2Cooldown = { office.forceResetInviteTicket2Cooldown() },
            onRefreshCalendar = onRefreshCalendar,
            onOpenCalendarLink = onOpenCalendarLink,
            onRefreshPool = onRefreshPool,
            onOpenPoolStudentGuide = onOpenPoolStudentGuide,
            onOpenGuideCatalog = onOpenGuideCatalog,
            onIdNicknameInputChange = { office.idNicknameInput = it },
            onSaveIdNickname = { office.saveIdNicknameFromInput(ui.serverIndex) },
            onIdFriendCodeInputChange = { office.idFriendCodeInput = it },
            onSaveIdFriendCode = { office.saveIdFriendCodeFromInput(context, ui.serverIndex) },
        )
    }

    private fun saveApCurrentInput() {
        val finalValue = office.apCurrentInput.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 0
        office.updateCurrentAp(finalValue, markSync = true)
        AppBackgroundScheduler.scheduleBaApThreshold(context)
        office.apCurrentInput = finalValue.toString()
    }

    private fun saveApLimitInput() {
        val finalValue = office.apLimitInput.toIntOrNull()?.coerceIn(0, BA_AP_LIMIT_MAX)
            ?: BA_AP_LIMIT_MAX
        office.updateApLimit(finalValue)
        office.applyApRegen()
        AppBackgroundScheduler.scheduleBaApThreshold(context)
        office.apLimitInput = finalValue.toString()
    }

    private fun selectCafeLevel(level: Int) {
        val normalized = level.coerceIn(1, 10)
        office.applyCafeStorage()
        office.cafeLevel = normalized
        office.clampCafeStoredToCap()
        BASettingsStore.saveCafeLevel(normalized)
        ui.sheetCafeLevel = normalized
        ui.showCafeLevelPopup = false
    }

    private fun selectServer(selected: Int) {
        ui.serverIndex = selected
        BASettingsStore.saveServerIndex(selected)
        office.loadIdForServer(selected)
        resetCafeVisitBaselineIfNeeded(selected)
        resetArenaRefreshBaselineIfNeeded(selected)
        onRefreshCalendar()
        onRefreshPool()
        AppBackgroundScheduler.scheduleBaApThreshold(context)
        ui.showOverviewServerPopup = false
    }

    private fun claimCafeStoredAp() {
        office.claimCafeStoredAp(context)
        AppBackgroundScheduler.scheduleBaApThreshold(context)
    }

    private fun resetCafeVisitBaselineIfNeeded(serverIndex: Int) {
        if (!office.cafeVisitNotifyEnabled) return
        val baselineSlotMs = currentCafeStudentRefreshSlotMs(
            nowMs = System.currentTimeMillis(),
            serverIndex = serverIndex
        )
        office.cafeVisitLastNotifiedSlotMs = baselineSlotMs
        BASettingsStore.saveCafeVisitLastNotifiedSlotMs(baselineSlotMs)
    }

    private fun resetArenaRefreshBaselineIfNeeded(serverIndex: Int) {
        if (!office.arenaRefreshNotifyEnabled) return
        val baselineSlotMs = currentArenaRefreshSlotMs(
            nowMs = System.currentTimeMillis(),
            serverIndex = serverIndex
        )
        office.arenaRefreshLastNotifiedSlotMs = baselineSlotMs
        BASettingsStore.saveArenaRefreshLastNotifiedSlotMs(baselineSlotMs)
    }
}
