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
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.ba.support.BaCraftSlot
import os.kei.ui.page.main.ba.support.BaCraftState
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.isActive
import os.kei.ui.page.main.ba.support.slotAt
import os.kei.ui.page.main.ba.support.normalized
import os.kei.ui.page.main.ba.support.started
import os.kei.ui.page.main.ba.support.withSlotAt
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
    val keepApRemindersReadUntilBelowThreshold: Boolean,
    val apSuppressionAnchorAtMs: Long,
    val cafeApSuppressionAnchorAtMs: Long,
    val apDismissedUntilAtMs: Long,
    val cafeApDismissedUntilAtMs: Long,
    val arenaRefreshNotifyEnabled: Boolean,
    val arenaRefreshLastNotifiedSlotMs: Long,
    val cafeVisitNotifyEnabled: Boolean,
    val craftNotifyEnabled: Boolean,
    val cafeVisitLastNotifiedSlotMs: Long,
    val coffeeHeadpatMs: Long,
    val coffeeInvite1UsedMs: Long,
    val coffeeInvite2UsedMs: Long,
    val craft: BaCraftState,
    val apCurrentInput: String,
    val apLimitInput: String,
    val cafeStoredApInput: String,
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
    var keepApRemindersReadUntilBelowThreshold by mutableStateOf(snapshot.keepApRemindersReadUntilBelowThreshold)
    var apSuppressionAnchorAtMs by mutableLongStateOf(snapshot.apSuppressionAnchorAtMs)
    var cafeApSuppressionAnchorAtMs by mutableLongStateOf(snapshot.cafeApSuppressionAnchorAtMs)
    var apDismissedUntilAtMs by mutableLongStateOf(snapshot.apDismissedUntilAtMs)
    var cafeApDismissedUntilAtMs by mutableLongStateOf(snapshot.cafeApDismissedUntilAtMs)
    var arenaRefreshNotifyEnabled by mutableStateOf(snapshot.arenaRefreshNotifyEnabled)
    var arenaRefreshLastNotifiedSlotMs by mutableLongStateOf(snapshot.arenaRefreshLastNotifiedSlotMs)
    var cafeVisitNotifyEnabled by mutableStateOf(snapshot.cafeVisitNotifyEnabled)
    var craftNotifyEnabled by mutableStateOf(snapshot.craftNotifyEnabled)
    var cafeVisitLastNotifiedSlotMs by mutableLongStateOf(snapshot.cafeVisitLastNotifiedSlotMs)
    var coffeeHeadpatMs by mutableLongStateOf(snapshot.coffeeHeadpatMs)
    var coffeeInvite1UsedMs by mutableLongStateOf(snapshot.coffeeInvite1UsedMs)
    var coffeeInvite2UsedMs by mutableLongStateOf(snapshot.coffeeInvite2UsedMs)

    // mutableStateOf, not a primitive variant: BaCraftState is a data class of lists, so `==` is deep
    // and structural, which is what both the equality chain and @Stable on BaOfficeState need.
    var craft by mutableStateOf(snapshot.craft.normalized())

    var apCurrentInput by mutableStateOf(displayAp(apCurrent).toString())
    var apLimitInput by mutableStateOf(apLimit.toString())
    var cafeStoredApInput by mutableStateOf(displayAp(cafeStoredAp).toString())
    var apLastNotifiedLevel by mutableIntStateOf(snapshot.apLastNotifiedLevel)

    fun displayApInputText(): String = displayAp(apCurrent).toString()

    fun displayCafeStoredApInputText(): String = displayAp(cafeStoredAp).toString()

    fun matchesSnapshot(snapshot: BaPageSnapshot): Boolean =
        cafeLevel == snapshot.cafeLevel &&
            cafeStoredAp == snapshot.cafeStoredAp &&
            matchesInitialRuntimeBase(
                currentValue = cafeLastHourMs,
                snapshotValue = snapshot.cafeLastHourMs,
                currentAmount = cafeStoredAp,
                snapshotAmount = snapshot.cafeStoredAp,
            ) &&
            cafeApNotifyEnabled == snapshot.cafeApNotifyEnabled &&
            cafeApNotifyThreshold == snapshot.cafeApNotifyThreshold &&
            cafeApLastNotifiedLevel == snapshot.cafeApLastNotifiedLevel &&
            idNickname == snapshot.idNickname &&
            idFriendCode == snapshot.idFriendCode &&
            apLimit == snapshot.apLimit &&
            apCurrent == snapshot.apCurrent.coerceAtLeast(0.0) &&
            matchesInitialRuntimeBase(
                currentValue = apRegenBaseMs,
                snapshotValue = snapshot.apRegenBaseMs,
                currentAmount = apCurrent,
                snapshotAmount = snapshot.apCurrent.coerceAtLeast(0.0),
            ) &&
            apSyncMs == snapshot.apSyncMs &&
            apNotifyEnabled == snapshot.apNotifyEnabled &&
            apNotifyThreshold == snapshot.apNotifyThreshold &&
            keepApRemindersReadUntilBelowThreshold == snapshot.keepApRemindersReadUntilBelowThreshold &&
            apSuppressionAnchorAtMs == snapshot.apSuppressionAnchorAtMs &&
            cafeApSuppressionAnchorAtMs == snapshot.cafeApSuppressionAnchorAtMs &&
            apDismissedUntilAtMs == snapshot.apDismissedUntilAtMs &&
            cafeApDismissedUntilAtMs == snapshot.cafeApDismissedUntilAtMs &&
            arenaRefreshNotifyEnabled == snapshot.arenaRefreshNotifyEnabled &&
            arenaRefreshLastNotifiedSlotMs == snapshot.arenaRefreshLastNotifiedSlotMs &&
            cafeVisitNotifyEnabled == snapshot.cafeVisitNotifyEnabled &&
            craftNotifyEnabled == snapshot.craftNotifyEnabled &&
            cafeVisitLastNotifiedSlotMs == snapshot.cafeVisitLastNotifiedSlotMs &&
            coffeeHeadpatMs == snapshot.coffeeHeadpatMs &&
            coffeeInvite1UsedMs == snapshot.coffeeInvite1UsedMs &&
            coffeeInvite2UsedMs == snapshot.coffeeInvite2UsedMs &&
            // Against the NORMALIZED snapshot value, like the derived input strings below. The holder
            // pads to BA_CRAFT_SLOT_COUNT, so comparing with the raw field would never hold for a
            // freshly built controller and this would report dirty forever — which permanently skips
            // applySnapshot and the loaded state would never reach the UI.
            craft == snapshot.craft.normalized() &&
            apCurrentInput == displayAp(snapshot.apCurrent.coerceAtLeast(0.0)).toString() &&
            apLimitInput == snapshot.apLimit.toString() &&
            cafeStoredApInput == displayAp(snapshot.cafeStoredAp.coerceAtLeast(0.0)).toString() &&
            apLastNotifiedLevel == snapshot.apLastNotifiedLevel

    private fun matchesInitialRuntimeBase(
        currentValue: Long,
        snapshotValue: Long,
        currentAmount: Double,
        snapshotAmount: Double,
    ): Boolean =
        currentValue == snapshotValue ||
            (
                snapshotValue <= 0L &&
                    currentValue >= 0L &&
                    currentAmount == snapshotAmount
            )

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
        keepApRemindersReadUntilBelowThreshold = snapshot.keepApRemindersReadUntilBelowThreshold
        apSuppressionAnchorAtMs = snapshot.apSuppressionAnchorAtMs
        cafeApSuppressionAnchorAtMs = snapshot.cafeApSuppressionAnchorAtMs
        apDismissedUntilAtMs = snapshot.apDismissedUntilAtMs
        cafeApDismissedUntilAtMs = snapshot.cafeApDismissedUntilAtMs
        arenaRefreshNotifyEnabled = snapshot.arenaRefreshNotifyEnabled
        arenaRefreshLastNotifiedSlotMs = snapshot.arenaRefreshLastNotifiedSlotMs
        cafeVisitNotifyEnabled = snapshot.cafeVisitNotifyEnabled
        craftNotifyEnabled = snapshot.craftNotifyEnabled
        cafeVisitLastNotifiedSlotMs = snapshot.cafeVisitLastNotifiedSlotMs
        coffeeHeadpatMs = snapshot.coffeeHeadpatMs
        coffeeInvite1UsedMs = snapshot.coffeeInvite1UsedMs
        coffeeInvite2UsedMs = snapshot.coffeeInvite2UsedMs
        craft = snapshot.craft.normalized()
        apCurrentInput = displayAp(apCurrent).toString()
        apLimitInput = apLimit.toString()
        cafeStoredApInput = displayAp(cafeStoredAp).toString()
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
            keepApRemindersReadUntilBelowThreshold = keepApRemindersReadUntilBelowThreshold,
            apSuppressionAnchorAtMs = apSuppressionAnchorAtMs,
            cafeApSuppressionAnchorAtMs = cafeApSuppressionAnchorAtMs,
            apDismissedUntilAtMs = apDismissedUntilAtMs,
            cafeApDismissedUntilAtMs = cafeApDismissedUntilAtMs,
            arenaRefreshNotifyEnabled = arenaRefreshNotifyEnabled,
            arenaRefreshLastNotifiedSlotMs = arenaRefreshLastNotifiedSlotMs,
            cafeVisitNotifyEnabled = cafeVisitNotifyEnabled,
            craftNotifyEnabled = craftNotifyEnabled,
            cafeVisitLastNotifiedSlotMs = cafeVisitLastNotifiedSlotMs,
            coffeeHeadpatMs = coffeeHeadpatMs,
            coffeeInvite1UsedMs = coffeeInvite1UsedMs,
            coffeeInvite2UsedMs = coffeeInvite2UsedMs,
            craft = craft,
            apCurrentInput = apCurrentInput,
            apLimitInput = apLimitInput,
            cafeStoredApInput = cafeStoredApInput,
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

    fun updateCafeStoredAp(newValue: Double): BaRuntimePersistenceUpdate {
        val (nextStoredAp, nextHour) =
            applyBaCafeStoredApUpdate(
                newValue = newValue,
                cafeLevel = cafeLevel,
                nowMs = clock.nowMs(),
            )
        cafeStoredAp = nextStoredAp
        cafeLastHourMs = nextHour
        cafeApLastNotifiedLevel = -1
        return BaRuntimePersistenceUpdate(
            cafeStoredAp = nextStoredAp,
            cafeLastHourMs = nextHour,
            cafeApLastNotifiedLevel = -1,
            notifyHomeOverview = true,
        )
    }

    fun clearCafeStoredAp(): BaRuntimePersistenceUpdate = updateCafeStoredAp(0.0)

    fun fillCafeStoredAp(): BaRuntimePersistenceUpdate = updateCafeStoredAp(cafeStorageCap(cafeLevel))

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

    fun updateHeadpatRemainingCooldown(
        remainingMs: Long,
        serverIndex: Int,
    ): BaOfficeCooldownPersistenceUpdate {
        val updatedAt =
            applyBaHeadpatRemainingCooldown(
                remainingMs = remainingMs,
                serverIndex = serverIndex,
                nowMs = clock.nowMs(),
            )
        coffeeHeadpatMs = updatedAt
        return BaOfficeCooldownPersistenceUpdate(headpatMs = updatedAt)
    }

    fun useInviteTicket1(): BaOfficeCooldownPersistenceUpdate? {
        val consumedAt = consumeBaInviteTicket(coffeeInvite1UsedMs, nowMs = clock.nowMs()) ?: return null
        coffeeInvite1UsedMs = consumedAt
        return BaOfficeCooldownPersistenceUpdate(invite1Ms = consumedAt)
    }

    fun updateInviteTicket1RemainingCooldown(remainingMs: Long): BaOfficeCooldownPersistenceUpdate {
        val usedMs = applyBaInviteTicketRemainingCooldown(remainingMs, nowMs = clock.nowMs())
        coffeeInvite1UsedMs = usedMs
        return BaOfficeCooldownPersistenceUpdate(invite1Ms = usedMs)
    }

    /**
     * Writes one craft slot, returning the state to persist or `null` when nothing changed.
     *
     * Returning null on a no-op is what makes the caller skip both the write and the reschedule — the
     * same contract the cooldown mutators use. It matters more here: a craft write re-arms the single BA
     * reminder alarm, so a redundant write would re-arm it for no reason.
     */
    fun writeCraftSlot(
        function: BaCraftFunction,
        index: Int,
        slot: BaCraftSlot,
    ): BaCraftState? {
        val next = craft.withSlotAt(function, index, slot)
        if (next == craft) return null
        craft = next
        return next
    }

    /** Starts the slot at the current clock, or returns `null` when it has no resolvable duration. */
    fun startCraftSlot(
        function: BaCraftFunction,
        index: Int,
        slot: BaCraftSlot,
    ): BaCraftState? {
        val started = slot.started(nowMs = clock.nowMs())
        if (!started.isActive()) return null
        return writeCraftSlot(function = function, index = index, slot = started)
    }

    /**
     * Rewrites a running slot's composition **without moving its start**, so the countdown is not reset.
     *
     * The editor used to have one way out — start — which re-anchored to now, so correcting a grade on a
     * craft that had been running for two hours threw those two hours away. This is the edit that was
     * missing: the slot keeps the instant it began, and only its duration changes. A slot that is not
     * running has no start to keep, so it falls through to [startCraftSlot].
     *
     * Returns `null` when the edit would leave the slot with no resolvable duration — the same guard
     * [startCraftSlot] applies, because a zero-duration slot cannot be active and silently clearing one
     * from an edit would look like data loss.
     */
    fun editCraftSlot(
        function: BaCraftFunction,
        index: Int,
        slot: BaCraftSlot,
    ): BaCraftState? {
        val current = craft.slotAt(function, index)
        if (!current.isActive()) return startCraftSlot(function = function, index = index, slot = slot)
        val edited = slot.copy(startedAtMs = current.startedAtMs)
        if (!edited.isActive()) return null
        return writeCraftSlot(function = function, index = index, slot = edited)
    }

    fun clearCraftSlot(
        function: BaCraftFunction,
        index: Int,
    ): BaCraftState? = writeCraftSlot(function = function, index = index, slot = BaCraftSlot())

    fun useInviteTicket2(): BaOfficeCooldownPersistenceUpdate? {
        val consumedAt = consumeBaInviteTicket(coffeeInvite2UsedMs, nowMs = clock.nowMs()) ?: return null
        coffeeInvite2UsedMs = consumedAt
        return BaOfficeCooldownPersistenceUpdate(invite2Ms = consumedAt)
    }

    fun updateInviteTicket2RemainingCooldown(remainingMs: Long): BaOfficeCooldownPersistenceUpdate {
        val usedMs = applyBaInviteTicketRemainingCooldown(remainingMs, nowMs = clock.nowMs())
        coffeeInvite2UsedMs = usedMs
        return BaOfficeCooldownPersistenceUpdate(invite2Ms = usedMs)
    }

    fun sendApTestNotification(
        context: Context,
        showToast: Boolean = true,
        thresholdTriggered: Boolean = false,
        notificationId: Int = BaAccountNotificationKind.Ap.legacyId,
        accountDisplayName: String = "",
        accountId: BaAccountId? = null,
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
                accountId = accountId,
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
        accountId: BaAccountId? = null,
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
                accountId = accountId,
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
        accountId: BaAccountId? = null,
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
                accountId = accountId,
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
        accountId: BaAccountId? = null,
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
                accountId = accountId,
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

    /**
     * Posts a daily-done card without running the template.
     *
     * The real path only fires from a quick-settings tile or a launcher shortcut, and only when a run
     * actually changed something — so checking the Super Island's wording used to mean owning a bound
     * tile, having dailies left to do, and mutating real account state to see one card.
     *
     * [craftSlotsStarted] is a parameter rather than a constant because the two values take different
     * text paths: a run that started craft slots reports them, and one that did not omits the clause
     * entirely rather than printing a zero. Both are worth being able to look at.
     */
    fun sendDailyDoneTestNotification(
        context: Context,
        showToast: Boolean = true,
        changedAccounts: Int = 1,
        craftSlotsStarted: Int = 2,
        accountId: BaAccountId? = null,
    ): Boolean {
        val sent =
            BaDailyDoneNotificationDispatcher.send(
                context = context,
                changedAccounts = changedAccounts,
                craftSlotsStarted = craftSlotsStarted,
                targetAccountId = accountId,
            )
        if (!sent) {
            if (showToast) {
                context.showToast(R.string.ba_toast_notification_permission_required)
            }
            return false
        }
        if (showToast) {
            context.showToast(R.string.ba_toast_daily_done_notification_sent)
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
