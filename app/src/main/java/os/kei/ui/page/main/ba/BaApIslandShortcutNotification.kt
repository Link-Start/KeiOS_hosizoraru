package os.kei.ui.page.main.ba

import android.content.Context
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.displayAp

internal data class BaApIslandShortcutNotificationPayload(
    val currentDisplay: Int,
    val limitDisplay: Int,
    val thresholdDisplay: Int,
)

internal data class BaApIslandShortcutNotificationPlan(
    val ap: BaApIslandShortcutNotificationPayload,
    val cafeAp: BaApIslandShortcutNotificationPayload,
    val nextAp: Double,
    val nextApRegenBaseMs: Long,
    val shouldSaveAp: Boolean,
    val nextCafeStoredAp: Double,
    val nextCafeLastHourMs: Long,
    val shouldSaveCafe: Boolean,
) {
    companion object {
        fun fromSnapshot(
            snapshot: BaPageSnapshot,
            nowMs: Long = System.currentTimeMillis(),
        ): BaApIslandShortcutNotificationPlan {
            val (nextAp, nextApRegenBaseMs) = applyBaApRegenTick(
                apLimit = snapshot.apLimit,
                apCurrent = snapshot.apCurrent,
                apRegenBaseMs = snapshot.apRegenBaseMs,
                nowMs = nowMs
            )
            val (nextCafeStoredAp, nextCafeLastHourMs) = applyBaCafeStorageTick(
                cafeStoredAp = snapshot.cafeStoredAp,
                cafeLevel = snapshot.cafeLevel,
                cafeLastHourMs = snapshot.cafeLastHourMs,
                nowMs = nowMs
            )
            val cafeLimitDisplay = displayAp(cafeStorageCap(snapshot.cafeLevel))
            return BaApIslandShortcutNotificationPlan(
                ap = BaApIslandShortcutNotificationPayload(
                    currentDisplay = displayAp(nextAp),
                    limitDisplay = snapshot.apLimit.coerceIn(0, BA_AP_MAX),
                    thresholdDisplay = snapshot.apNotifyThreshold.coerceIn(0, BA_AP_MAX)
                ),
                cafeAp = BaApIslandShortcutNotificationPayload(
                    currentDisplay = displayAp(nextCafeStoredAp),
                    limitDisplay = cafeLimitDisplay,
                    thresholdDisplay = snapshot.cafeApNotifyThreshold.coerceIn(0, cafeLimitDisplay)
                ),
                nextAp = nextAp,
                nextApRegenBaseMs = nextApRegenBaseMs,
                shouldSaveAp = nextAp != snapshot.apCurrent ||
                        nextApRegenBaseMs != snapshot.apRegenBaseMs,
                nextCafeStoredAp = nextCafeStoredAp,
                nextCafeLastHourMs = nextCafeLastHourMs,
                shouldSaveCafe = nextCafeStoredAp != snapshot.cafeStoredAp ||
                        nextCafeLastHourMs != snapshot.cafeLastHourMs
            )
        }
    }
}

internal object BaApIslandShortcutNotificationCoordinator {
    fun send(context: Context): Boolean {
        val snapshot = BASettingsStore.loadSnapshot()
        val plan = BaApIslandShortcutNotificationPlan.fromSnapshot(snapshot)
        persistPlan(plan)
        val apSent = BaApNotificationDispatcher.send(
            context = context,
            currentDisplay = plan.ap.currentDisplay,
            limitDisplay = plan.ap.limitDisplay,
            thresholdDisplay = plan.ap.thresholdDisplay
        )
        val cafeApSent = BaCafeApNotificationDispatcher.send(
            context = context,
            currentDisplay = plan.cafeAp.currentDisplay,
            limitDisplay = plan.cafeAp.limitDisplay,
            thresholdDisplay = plan.cafeAp.thresholdDisplay
        )
        return apSent || cafeApSent
    }

    private fun persistPlan(plan: BaApIslandShortcutNotificationPlan) {
        if (!plan.shouldSaveAp && !plan.shouldSaveCafe) return
        BASettingsStore.saveBaRuntimeState(
            apCurrent = plan.nextAp.takeIf { plan.shouldSaveAp },
            apRegenBaseMs = plan.nextApRegenBaseMs.takeIf { plan.shouldSaveAp },
            cafeStoredAp = plan.nextCafeStoredAp.takeIf { plan.shouldSaveCafe },
            cafeLastHourMs = plan.nextCafeLastHourMs.takeIf { plan.shouldSaveCafe },
            notifyHomeOverview = false,
        )
    }
}
