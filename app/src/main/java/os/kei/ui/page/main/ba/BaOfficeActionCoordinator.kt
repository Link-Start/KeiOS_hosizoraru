package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.core.background.AppBackgroundScheduler
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX

internal class BaOfficeActionCoordinator(
    private val context: Context,
    private val office: BaOfficeController,
    private val scope: CoroutineScope,
    private val serverIndexProvider: () -> Int,
    private val onServerSelected: (Int) -> Unit,
    private val onSettingsCafeLevelChange: (Int) -> Unit,
    private val onOverviewServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    private val onOverviewServerPopupChange: (Boolean) -> Unit,
    private val onCafeLevelPopupAnchorBoundsChange: (IntRect?) -> Unit,
    private val onCafeLevelPopupChange: (Boolean) -> Unit,
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
            onOverviewServerPopupAnchorBoundsChange = onOverviewServerPopupAnchorBoundsChange,
            onOverviewServerPopupChange = onOverviewServerPopupChange,
            onCafeLevelPopupAnchorBoundsChange = onCafeLevelPopupAnchorBoundsChange,
            onCafeLevelPopupChange = onCafeLevelPopupChange,
            onCafeLevelChange = ::selectCafeLevel,
            onServerSelected = ::selectServer,
            onClaimCafeStoredAp = ::claimCafeStoredAp,
            onTouchHead = { persistCooldown(office.touchHead(serverIndexProvider())) },
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
            onSaveIdNickname = { persistIdentity(office.saveIdNicknameFromInput(serverIndexProvider())) },
            onIdFriendCodeInputChange = { office.idFriendCodeInput = it },
            onSaveIdFriendCode = { persistIdentity(office.saveIdFriendCodeFromInput(context, serverIndexProvider())) },
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
        onSettingsCafeLevelChange(normalized)
        onCafeLevelPopupChange(false)
    }

    private fun selectServer(selected: Int) {
        onServerSelected(selected)
        onOverviewServerPopupChange(false)
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
}
