package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BA_DEFAULT_FRIEND_CODE
import os.kei.ui.page.main.ba.support.BA_DEFAULT_NICKNAME
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.currentArenaRefreshSlotMs
import os.kei.ui.page.main.ba.support.currentCafeStudentRefreshSlotMs
import os.kei.ui.page.main.ba.support.displayAp
import os.kei.ui.page.main.ba.support.floorToHourMs
import os.kei.ui.page.main.ba.support.normalizeAp
import kotlin.math.roundToInt

@Stable
internal data class BaOfficeState(
    val cafeLevel: Int,
    val cafeStoredAp: Double,
    val cafeLastHourMs: Long,
    val cafeApNotifyEnabled: Boolean,
    val cafeApNotifyThreshold: Int,
    val cafeApLastNotifiedLevel: Int,
    val idNickname: String,
    val idFriendCode: String,
    val apLimit: Int,
    val apCurrent: Double,
    val apRegenBaseMs: Long,
    val apSyncMs: Long,
    val apNotifyEnabled: Boolean,
    val apNotifyThreshold: Int,
    val arenaRefreshNotifyEnabled: Boolean,
    val arenaRefreshLastNotifiedSlotMs: Long,
    val cafeVisitNotifyEnabled: Boolean,
    val cafeVisitLastNotifiedSlotMs: Long,
    val coffeeHeadpatMs: Long,
    val coffeeInvite1UsedMs: Long,
    val coffeeInvite2UsedMs: Long,
    val apCurrentInput: String,
    val apLimitInput: String,
    val idNicknameInput: String,
    val idFriendCodeInput: String,
    val apLastNotifiedLevel: Int,
)

@Stable
internal class BaOfficeController(
    snapshot: BaPageSnapshot,
    private val clock: BaOfficeClock = BaSystemOfficeClock,
) {
    var cafeLevel by mutableIntStateOf(snapshot.cafeLevel)
    var cafeStoredAp by mutableStateOf(snapshot.cafeStoredAp)
    var cafeLastHourMs by mutableLongStateOf(snapshot.cafeLastHourMs)
    var cafeApNotifyEnabled by mutableStateOf(snapshot.cafeApNotifyEnabled)
    var cafeApNotifyThreshold by mutableIntStateOf(snapshot.cafeApNotifyThreshold)
    var cafeApLastNotifiedLevel by mutableIntStateOf(snapshot.cafeApLastNotifiedLevel)
    var idNickname by mutableStateOf(snapshot.idNickname)
    var idFriendCode by mutableStateOf(snapshot.idFriendCode)
    var apLimit by mutableIntStateOf(snapshot.apLimit)
    var apCurrent by mutableStateOf(snapshot.apCurrent.coerceAtLeast(0.0))
    var apRegenBaseMs by mutableLongStateOf(snapshot.apRegenBaseMs)
    var apSyncMs by mutableLongStateOf(snapshot.apSyncMs)
    var apNotifyEnabled by mutableStateOf(snapshot.apNotifyEnabled)
    var apNotifyThreshold by mutableIntStateOf(snapshot.apNotifyThreshold)
    var arenaRefreshNotifyEnabled by mutableStateOf(snapshot.arenaRefreshNotifyEnabled)
    var arenaRefreshLastNotifiedSlotMs by mutableLongStateOf(snapshot.arenaRefreshLastNotifiedSlotMs)
    var cafeVisitNotifyEnabled by mutableStateOf(snapshot.cafeVisitNotifyEnabled)
    var cafeVisitLastNotifiedSlotMs by mutableLongStateOf(snapshot.cafeVisitLastNotifiedSlotMs)
    var coffeeHeadpatMs by mutableLongStateOf(snapshot.coffeeHeadpatMs)
    var coffeeInvite1UsedMs by mutableLongStateOf(snapshot.coffeeInvite1UsedMs)
    var coffeeInvite2UsedMs by mutableLongStateOf(snapshot.coffeeInvite2UsedMs)

    var apCurrentInput by mutableStateOf(displayAp(apCurrent).toString())
    var apLimitInput by mutableStateOf(apLimit.toString())
    var idNicknameInput by mutableStateOf(idNickname)
    var idFriendCodeInput by mutableStateOf(idFriendCode)
    var apLastNotifiedLevel by mutableIntStateOf(snapshot.apLastNotifiedLevel)

    fun displayApInputText(): String = displayAp(apCurrent).toString()

    fun matchesSnapshot(snapshot: BaPageSnapshot): Boolean =
        cafeLevel == snapshot.cafeLevel &&
            cafeStoredAp == snapshot.cafeStoredAp &&
            cafeLastHourMs == snapshot.cafeLastHourMs &&
            cafeApNotifyEnabled == snapshot.cafeApNotifyEnabled &&
            cafeApNotifyThreshold == snapshot.cafeApNotifyThreshold &&
            cafeApLastNotifiedLevel == snapshot.cafeApLastNotifiedLevel &&
            idNickname == snapshot.idNickname &&
            idFriendCode == snapshot.idFriendCode &&
            apLimit == snapshot.apLimit &&
            apCurrent == snapshot.apCurrent.coerceAtLeast(0.0) &&
            apRegenBaseMs == snapshot.apRegenBaseMs &&
            apSyncMs == snapshot.apSyncMs &&
            apNotifyEnabled == snapshot.apNotifyEnabled &&
            apNotifyThreshold == snapshot.apNotifyThreshold &&
            arenaRefreshNotifyEnabled == snapshot.arenaRefreshNotifyEnabled &&
            arenaRefreshLastNotifiedSlotMs == snapshot.arenaRefreshLastNotifiedSlotMs &&
            cafeVisitNotifyEnabled == snapshot.cafeVisitNotifyEnabled &&
            cafeVisitLastNotifiedSlotMs == snapshot.cafeVisitLastNotifiedSlotMs &&
            coffeeHeadpatMs == snapshot.coffeeHeadpatMs &&
            coffeeInvite1UsedMs == snapshot.coffeeInvite1UsedMs &&
            coffeeInvite2UsedMs == snapshot.coffeeInvite2UsedMs &&
            apCurrentInput == displayAp(snapshot.apCurrent.coerceAtLeast(0.0)).toString() &&
            apLimitInput == snapshot.apLimit.toString() &&
            idNicknameInput == snapshot.idNickname &&
            idFriendCodeInput == snapshot.idFriendCode &&
            apLastNotifiedLevel == snapshot.apLastNotifiedLevel

    fun applySnapshot(snapshot: BaPageSnapshot) {
        cafeLevel = snapshot.cafeLevel
        cafeStoredAp = snapshot.cafeStoredAp
        cafeLastHourMs = snapshot.cafeLastHourMs
        cafeApNotifyEnabled = snapshot.cafeApNotifyEnabled
        cafeApNotifyThreshold = snapshot.cafeApNotifyThreshold
        cafeApLastNotifiedLevel = snapshot.cafeApLastNotifiedLevel
        idNickname = snapshot.idNickname
        idFriendCode = snapshot.idFriendCode
        apLimit = snapshot.apLimit
        apCurrent = snapshot.apCurrent.coerceAtLeast(0.0)
        apRegenBaseMs = snapshot.apRegenBaseMs
        apSyncMs = snapshot.apSyncMs
        apNotifyEnabled = snapshot.apNotifyEnabled
        apNotifyThreshold = snapshot.apNotifyThreshold
        arenaRefreshNotifyEnabled = snapshot.arenaRefreshNotifyEnabled
        arenaRefreshLastNotifiedSlotMs = snapshot.arenaRefreshLastNotifiedSlotMs
        cafeVisitNotifyEnabled = snapshot.cafeVisitNotifyEnabled
        cafeVisitLastNotifiedSlotMs = snapshot.cafeVisitLastNotifiedSlotMs
        coffeeHeadpatMs = snapshot.coffeeHeadpatMs
        coffeeInvite1UsedMs = snapshot.coffeeInvite1UsedMs
        coffeeInvite2UsedMs = snapshot.coffeeInvite2UsedMs
        apCurrentInput = displayAp(apCurrent).toString()
        apLimitInput = apLimit.toString()
        idNicknameInput = idNickname
        idFriendCodeInput = idFriendCode
        apLastNotifiedLevel = snapshot.apLastNotifiedLevel
    }

    fun state(): BaOfficeState =
        BaOfficeState(
            cafeLevel = cafeLevel,
            cafeStoredAp = cafeStoredAp,
            cafeLastHourMs = cafeLastHourMs,
            cafeApNotifyEnabled = cafeApNotifyEnabled,
            cafeApNotifyThreshold = cafeApNotifyThreshold,
            cafeApLastNotifiedLevel = cafeApLastNotifiedLevel,
            idNickname = idNickname,
            idFriendCode = idFriendCode,
            apLimit = apLimit,
            apCurrent = apCurrent,
            apRegenBaseMs = apRegenBaseMs,
            apSyncMs = apSyncMs,
            apNotifyEnabled = apNotifyEnabled,
            apNotifyThreshold = apNotifyThreshold,
            arenaRefreshNotifyEnabled = arenaRefreshNotifyEnabled,
            arenaRefreshLastNotifiedSlotMs = arenaRefreshLastNotifiedSlotMs,
            cafeVisitNotifyEnabled = cafeVisitNotifyEnabled,
            cafeVisitLastNotifiedSlotMs = cafeVisitLastNotifiedSlotMs,
            coffeeHeadpatMs = coffeeHeadpatMs,
            coffeeInvite1UsedMs = coffeeInvite1UsedMs,
            coffeeInvite2UsedMs = coffeeInvite2UsedMs,
            apCurrentInput = apCurrentInput,
            apLimitInput = apLimitInput,
            idNicknameInput = idNicknameInput,
            idFriendCodeInput = idFriendCodeInput,
            apLastNotifiedLevel = apLastNotifiedLevel,
        )

    fun ensureRegenBase(nowMs: Long = clock.nowMs()): BaRuntimePersistenceUpdate? {
        if (apRegenBaseMs <= 0L) {
            apRegenBaseMs = nowMs
            return BaRuntimePersistenceUpdate(
                apRegenBaseMs = nowMs,
                notifyHomeOverview = false,
            )
        }
        return null
    }

    fun ensureCafeHourBase(nowMs: Long = clock.nowMs()): BaRuntimePersistenceUpdate? {
        val currentHour = floorToHourMs(nowMs)
        if (cafeLastHourMs <= 0L || cafeLastHourMs > currentHour) {
            cafeLastHourMs = currentHour
            return BaRuntimePersistenceUpdate(
                cafeLastHourMs = currentHour,
                notifyHomeOverview = false,
            )
        }
        return null
    }

    fun clampCafeStoredToCapUpdate(): BaRuntimePersistenceUpdate? {
        val cap = cafeStorageCap(cafeLevel)
        val clamped = normalizeAp(cafeStoredAp.coerceIn(0.0, cap))
        if (clamped != cafeStoredAp) {
            cafeStoredAp = clamped
            return BaRuntimePersistenceUpdate(
                cafeStoredAp = clamped,
                notifyHomeOverview = false,
            )
        }
        return null
    }

    fun normalizeRuntimeState(nowMs: Long = clock.nowMs()): BaRuntimePersistenceUpdate? {
        var update: BaRuntimePersistenceUpdate? = null
        ensureRegenBase(nowMs)?.let { next ->
            update = update?.mergedWith(next) ?: next
        }
        ensureCafeHourBase(nowMs)?.let { next ->
            update = update?.mergedWith(next) ?: next
        }
        val clampUpdate = clampCafeStoredToCapUpdate()
        if (clampUpdate != null) {
            update = update?.mergedWith(clampUpdate) ?: clampUpdate
        }
        return update
    }

    fun saveIdNicknameFromInput(serverIndex: Int): BaOfficeIdentityPersistenceUpdate {
        val sanitized = idNicknameInput.take(10).ifEmpty { BA_DEFAULT_NICKNAME }
        idNickname = sanitized
        idNicknameInput = sanitized
        return BaOfficeIdentityPersistenceUpdate(
            nickname = sanitized,
            serverIndex = serverIndex,
        )
    }

    fun saveIdFriendCodeFromInput(
        context: Context,
        serverIndex: Int,
    ): BaOfficeIdentityPersistenceUpdate? {
        val sanitized = sanitizeBaFriendCodeInput(idFriendCodeInput)
        if (sanitized.length != 8) {
            context.showToast(R.string.ba_toast_friend_code_invalid)
            idFriendCodeInput = idFriendCode
            return null
        }
        idFriendCode = sanitized
        idFriendCodeInput = sanitized
        return BaOfficeIdentityPersistenceUpdate(
            friendCode = sanitized,
            serverIndex = serverIndex,
        )
    }

    fun updateCurrentAp(
        newValue: Int,
        markSync: Boolean,
    ): BaRuntimePersistenceUpdate {
        val (next, nowMs) =
            applyBaCurrentApUpdate(
                currentAp = apCurrent,
                newValue = newValue,
                nowMs = clock.nowMs(),
            )
        apCurrent = next
        apRegenBaseMs = nowMs
        if (markSync) {
            apSyncMs = nowMs
        }
        return BaRuntimePersistenceUpdate(
            apCurrent = next,
            apRegenBaseMs = nowMs,
            apSyncMs = nowMs.takeIf { markSync },
            notifyHomeOverview = true,
        )
    }

    fun addCurrentAp(
        delta: Double,
        markSync: Boolean,
    ): BaRuntimePersistenceUpdate? {
        val result =
            applyBaCurrentApDelta(
                currentAp = apCurrent,
                delta = delta,
                nowMs = clock.nowMs(),
            ) ?: return null
        val (next, nowMs) = result
        apCurrent = next
        apRegenBaseMs = nowMs
        if (markSync) {
            apSyncMs = nowMs
        }
        return BaRuntimePersistenceUpdate(
            apCurrent = next,
            apRegenBaseMs = nowMs,
            apSyncMs = nowMs.takeIf { markSync },
            notifyHomeOverview = true,
        )
    }

    fun updateApLimit(newLimit: Int): BaOfficeApLimitUpdate {
        val clamped = coerceBaApLimit(newLimit)
        apLimit = clamped
        return BaOfficeApLimitUpdate(
            limit = clamped,
            runtimeUpdate = ensureRegenBase(),
        )
    }

    fun applyApRegen(nowMs: Long = clock.nowMs()): BaRuntimePersistenceUpdate? {
        if (apLimit.coerceIn(0, BA_AP_LIMIT_MAX) <= 0) {
            apRegenBaseMs = nowMs
            val correctedAp = if (apCurrent < 0.0) 0.0 else null
            if (apCurrent < 0.0) {
                apCurrent = 0.0
            }
            return BaRuntimePersistenceUpdate(
                apCurrent = correctedAp,
                apRegenBaseMs = nowMs,
                notifyHomeOverview = false,
            )
        }
        val (nextAp, nextBase) =
            applyBaApRegenTick(
                apLimit = apLimit,
                apCurrent = apCurrent,
                apRegenBaseMs = apRegenBaseMs,
                nowMs = nowMs,
            )
        val shouldSaveAp = nextAp != apCurrent
        val shouldSaveBase = nextBase != apRegenBaseMs
        if (shouldSaveAp) {
            apCurrent = nextAp
        }
        if (shouldSaveBase) {
            apRegenBaseMs = nextBase
        }
        return if (shouldSaveAp || shouldSaveBase) {
            BaRuntimePersistenceUpdate(
                apCurrent = nextAp.takeIf { shouldSaveAp },
                apRegenBaseMs = nextBase.takeIf { shouldSaveBase },
                notifyHomeOverview = false,
            )
        } else {
            null
        }
    }

    fun applyRuntimeTick(nowMs: Long = clock.nowMs()): BaRuntimePersistenceUpdate? {
        var nextApToSave: Double? = null
        var nextApBaseToSave: Long? = null
        var nextCafeStoredToSave: Double? = null
        var nextCafeHourToSave: Long? = null

        val (nextStoredAp, nextHour) =
            applyBaCafeStorageTick(
                cafeStoredAp = cafeStoredAp,
                cafeLevel = cafeLevel,
                cafeLastHourMs = cafeLastHourMs,
                nowMs = nowMs,
            )
        if (nextStoredAp != cafeStoredAp) {
            cafeStoredAp = nextStoredAp
            nextCafeStoredToSave = nextStoredAp
        }
        if (nextHour != cafeLastHourMs) {
            cafeLastHourMs = nextHour
            nextCafeHourToSave = nextHour
        }

        if (apLimit.coerceIn(0, BA_AP_LIMIT_MAX) <= 0) {
            apRegenBaseMs = nowMs
            nextApBaseToSave = nowMs
            if (apCurrent < 0.0) {
                apCurrent = 0.0
                nextApToSave = 0.0
            }
        } else {
            val (nextAp, nextBase) =
                applyBaApRegenTick(
                    apLimit = apLimit,
                    apCurrent = apCurrent,
                    apRegenBaseMs = apRegenBaseMs,
                    nowMs = nowMs,
                )
            if (nextAp != apCurrent) {
                apCurrent = nextAp
                nextApToSave = nextAp
            }
            if (nextBase != apRegenBaseMs) {
                apRegenBaseMs = nextBase
                nextApBaseToSave = nextBase
            }
        }

        if (
            nextApToSave != null ||
            nextApBaseToSave != null ||
            nextCafeStoredToSave != null ||
            nextCafeHourToSave != null
        ) {
            return BaRuntimePersistenceUpdate(
                apCurrent = nextApToSave,
                apRegenBaseMs = nextApBaseToSave,
                cafeStoredAp = nextCafeStoredToSave,
                cafeLastHourMs = nextCafeHourToSave,
                notifyHomeOverview = false,
            )
        }
        return null
    }

    fun applyCafeStorageUpdate(nowMs: Long = clock.nowMs()): BaRuntimePersistenceUpdate? {
        val (nextStoredAp, nextHour) =
            applyBaCafeStorageTick(
                cafeStoredAp = cafeStoredAp,
                cafeLevel = cafeLevel,
                cafeLastHourMs = cafeLastHourMs,
                nowMs = nowMs,
            )
        val shouldSaveStoredAp = nextStoredAp != cafeStoredAp
        val shouldSaveHour = nextHour != cafeLastHourMs
        if (shouldSaveStoredAp) {
            cafeStoredAp = nextStoredAp
        }
        if (shouldSaveHour) {
            cafeLastHourMs = nextHour
        }
        if (shouldSaveStoredAp || shouldSaveHour) {
            return BaRuntimePersistenceUpdate(
                cafeStoredAp = nextStoredAp.takeIf { shouldSaveStoredAp },
                cafeLastHourMs = nextHour.takeIf { shouldSaveHour },
                notifyHomeOverview = false,
            )
        }
        return null
    }

    fun claimCafeStoredAp(context: Context): BaRuntimePersistenceUpdate? {
        var update = applyCafeStorageUpdate()
        val claim = applyBaCafeClaim(cafeStoredAp)
        if (claim <= 0.0) {
            context.showToast(R.string.ba_toast_cafe_no_ap)
            return update
        }
        val apUpdate = addCurrentAp(claim, markSync = true)
        if (apUpdate != null) {
            update = update?.mergedWith(apUpdate) ?: apUpdate
        }
        cafeStoredAp = 0.0
        cafeApLastNotifiedLevel = -1
        val clearUpdate =
            BaRuntimePersistenceUpdate(
                cafeStoredAp = 0.0,
                cafeApLastNotifiedLevel = -1,
                notifyHomeOverview = true,
            )
        update = update?.mergedWith(clearUpdate) ?: clearUpdate
        context.showToast(context.getString(R.string.ba_toast_cafe_claimed_ap, claim.roundToInt()))
        return update
    }

    fun testCafePlus3Hours(context: Context): BaRuntimePersistenceUpdate? {
        var update = applyCafeStorageUpdate()
        val (nextStoredAp, gainedInt) =
            applyBaCafeDebugGain(
                cafeStoredAp = cafeStoredAp,
                cafeLevel = cafeLevel,
            )
        cafeStoredAp = nextStoredAp
        val gainedUpdate =
            BaRuntimePersistenceUpdate(
                cafeStoredAp = cafeStoredAp,
                notifyHomeOverview = true,
            )
        update = update?.mergedWith(gainedUpdate) ?: gainedUpdate
        context.showToast(context.getString(R.string.ba_toast_cafe_debug_added, gainedInt))
        return update
    }

    fun touchHead(serverIndex: Int): BaOfficeCooldownPersistenceUpdate? {
        val consumedAt =
            consumeBaHeadpat(
                coffeeHeadpatMs = coffeeHeadpatMs,
                serverIndex = serverIndex,
                nowMs = clock.nowMs(),
            ) ?: return null
        coffeeHeadpatMs = consumedAt
        return BaOfficeCooldownPersistenceUpdate(headpatMs = consumedAt)
    }

    fun forceResetHeadpatCooldown(): BaOfficeCooldownPersistenceUpdate {
        coffeeHeadpatMs = 0L
        return BaOfficeCooldownPersistenceUpdate(headpatMs = 0L)
    }

    fun useInviteTicket1(): BaOfficeCooldownPersistenceUpdate? {
        val consumedAt = consumeBaInviteTicket(coffeeInvite1UsedMs, nowMs = clock.nowMs()) ?: return null
        coffeeInvite1UsedMs = consumedAt
        return BaOfficeCooldownPersistenceUpdate(invite1Ms = consumedAt)
    }

    fun forceResetInviteTicket1Cooldown(): BaOfficeCooldownPersistenceUpdate {
        coffeeInvite1UsedMs = 0L
        return BaOfficeCooldownPersistenceUpdate(invite1Ms = 0L)
    }

    fun useInviteTicket2(): BaOfficeCooldownPersistenceUpdate? {
        val consumedAt = consumeBaInviteTicket(coffeeInvite2UsedMs, nowMs = clock.nowMs()) ?: return null
        coffeeInvite2UsedMs = consumedAt
        return BaOfficeCooldownPersistenceUpdate(invite2Ms = consumedAt)
    }

    fun forceResetInviteTicket2Cooldown(): BaOfficeCooldownPersistenceUpdate {
        coffeeInvite2UsedMs = 0L
        return BaOfficeCooldownPersistenceUpdate(invite2Ms = 0L)
    }

    fun sendApTestNotification(
        context: Context,
        showToast: Boolean = true,
        thresholdTriggered: Boolean = false,
        notificationId: Int = BaAccountNotificationKind.Ap.legacyId,
        accountDisplayName: String = "",
    ): Boolean {
        val currentDisplay = displayAp(apCurrent)
        val limitDisplay = apLimit.coerceIn(0, BA_AP_MAX)
        val thresholdDisplay = apNotifyThreshold.coerceIn(0, BA_AP_MAX)
        val sent =
            BaApNotificationDispatcher.send(
                context = context,
                currentDisplay = currentDisplay,
                limitDisplay = limitDisplay,
                thresholdDisplay = thresholdDisplay,
                notificationId = notificationId,
                accountDisplayName = accountDisplayName,
            )
        if (!sent) {
            if (showToast) {
                context.showToast(R.string.ba_toast_notification_permission_required)
            }
            return false
        }
        if (showToast) {
            val notifyText =
                context.getString(
                    if (thresholdTriggered) {
                        R.string.ba_toast_ap_threshold_notification_sent
                    } else {
                        R.string.ba_toast_ap_notification_sent
                    },
                )
            context.showToast(notifyText)
        }
        return true
    }

    fun sendCafeApTestNotification(
        context: Context,
        showToast: Boolean = true,
        notificationId: Int = BaAccountNotificationKind.CafeAp.legacyId,
        accountDisplayName: String = "",
        onRuntimeUpdate: (BaRuntimePersistenceUpdate?) -> Unit = {},
    ): Boolean {
        onRuntimeUpdate(applyCafeStorageUpdate())
        val currentDisplay = displayAp(cafeStoredAp)
        val limitDisplay = displayAp(cafeStorageCap(cafeLevel))
        val thresholdDisplay = cafeApNotifyThreshold.coerceIn(0, limitDisplay)
        val sent =
            BaCafeApNotificationDispatcher.send(
                context = context,
                currentDisplay = currentDisplay,
                limitDisplay = limitDisplay,
                thresholdDisplay = thresholdDisplay,
                notificationId = notificationId,
                accountDisplayName = accountDisplayName,
            )
        if (!sent) {
            if (showToast) {
                context.showToast(R.string.ba_toast_notification_permission_required)
            }
            return false
        }
        if (showToast) {
            context.showToast(R.string.ba_toast_cafe_ap_notification_sent)
        }
        return true
    }

    fun sendCafeVisitTestNotification(
        context: Context,
        serverIndex: Int,
        showToast: Boolean = true,
        notificationId: Int = BaAccountNotificationKind.CafeVisit.legacyId,
        accountDisplayName: String = "",
    ): Boolean {
        val slotMs =
            currentCafeStudentRefreshSlotMs(
                nowMs = clock.nowMs(),
                serverIndex = serverIndex,
            )
        val sent =
            BaCafeVisitNotificationDispatcher.send(
                context = context,
                serverIndex = serverIndex,
                slotMs = slotMs,
                notificationId = notificationId,
                accountDisplayName = accountDisplayName,
            )
        if (!sent) {
            if (showToast) {
                context.showToast(R.string.ba_toast_notification_permission_required)
            }
            return false
        }
        if (showToast) {
            context.showToast(R.string.ba_toast_cafe_visit_notification_sent)
        }
        return true
    }

    fun sendArenaRefreshTestNotification(
        context: Context,
        serverIndex: Int,
        showToast: Boolean = true,
        notificationId: Int = BaAccountNotificationKind.ArenaRefresh.legacyId,
        accountDisplayName: String = "",
    ): Boolean {
        val slotMs =
            currentArenaRefreshSlotMs(
                nowMs = clock.nowMs(),
                serverIndex = serverIndex,
            )
        val sent =
            BaArenaRefreshNotificationDispatcher.send(
                context = context,
                serverIndex = serverIndex,
                slotMs = slotMs,
                notificationId = notificationId,
                accountDisplayName = accountDisplayName,
            )
        if (!sent) {
            if (showToast) {
                context.showToast(R.string.ba_toast_notification_permission_required)
            }
            return false
        }
        if (showToast) {
            context.showToast(R.string.ba_toast_arena_refresh_notification_sent)
        }
        return true
    }

    fun applyApLastNotifiedLevel(level: Int): BaRuntimePersistenceUpdate? {
        val normalized = level.coerceIn(-1, BA_AP_MAX)
        if (apLastNotifiedLevel == normalized) return null
        apLastNotifiedLevel = normalized
        return BaRuntimePersistenceUpdate(apLastNotifiedLevel = normalized)
    }
}

@Composable
internal fun rememberBaOfficeController(snapshot: BaPageSnapshot): BaOfficeController = remember(snapshot) { BaOfficeController(snapshot) }
