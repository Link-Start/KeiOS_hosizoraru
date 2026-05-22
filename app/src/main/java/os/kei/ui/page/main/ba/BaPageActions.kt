package os.kei.ui.page.main.ba

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.intent.SafeExternalIntents
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BA_CAFE_HOURLY_INTERVAL_MS
import os.kei.ui.page.main.ba.support.cafeHourlyGain
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.calculateInviteTicketAvailableMs
import os.kei.ui.page.main.ba.support.calculateNextHeadpatAvailableMs
import os.kei.ui.page.main.ba.support.floorToHourMs
import os.kei.ui.page.main.ba.support.fractionalApPart
import os.kei.ui.page.main.ba.support.normalizeAp
import kotlin.math.roundToInt

internal fun copyBaFriendCodeToClipboard(
    context: Context,
    friendCode: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(
        ClipData.newPlainText(context.getString(R.string.ba_friend_code_clipboard_label), friendCode),
    )
    context.showToast(R.string.ba_toast_friend_code_copied)
}

internal fun openBaExternalLink(
    context: Context,
    url: String,
    failureMessage: String = context.getString(R.string.ba_error_open_activity_link),
) {
    if (!SafeExternalIntents.startBrowsableUrl(context, url, newTask = true)) {
        context.showToast(failureMessage)
    }
}

internal fun sanitizeBaFriendCodeInput(raw: String): String = raw.uppercase().filter { it in 'A'..'Z' }.take(8)

internal fun applyBaCurrentApUpdate(
    currentAp: Double,
    newValue: Int,
): Pair<Double, Long> {
    val nowMs = System.currentTimeMillis()
    val integerPart = newValue.coerceIn(0, BA_AP_MAX)
    val fractionPart = fractionalApPart(currentAp)
    val next = normalizeAp(integerPart.toDouble() + fractionPart)
    return next to nowMs
}

internal fun applyBaCurrentApDelta(
    currentAp: Double,
    delta: Double,
): Pair<Double, Long>? {
    if (delta <= 0.0) return null
    val nowMs = System.currentTimeMillis()
    return normalizeAp(currentAp + delta) to nowMs
}

internal fun coerceBaApLimit(newLimit: Int): Int = newLimit.coerceIn(0, BA_AP_LIMIT_MAX)

internal fun applyBaApRegenTick(
    apLimit: Int,
    apCurrent: Double,
    apRegenBaseMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): Pair<Double, Long> {
    val limit = apLimit.coerceIn(0, BA_AP_LIMIT_MAX)
    if (limit <= 0) return apCurrent.coerceAtLeast(0.0) to nowMs

    val current = apCurrent.coerceAtLeast(0.0)
    val ensuredBase = if (apRegenBaseMs <= 0L) nowMs else apRegenBaseMs
    if (current >= limit.toDouble()) return current to ensuredBase

    val elapsed = (nowMs - ensuredBase).coerceAtLeast(0L)
    val gained = (elapsed / BA_AP_REGEN_INTERVAL_MS).toInt()
    if (gained <= 0) return current to ensuredBase

    val pointsUntilStop =
        kotlin.math
            .ceil(limit.toDouble() - current)
            .toInt()
            .coerceAtLeast(0)
    val pointsApplied = gained.coerceAtMost(pointsUntilStop)
    if (pointsApplied <= 0) return current to ensuredBase

    val nextAp = normalizeAp(current + pointsApplied.toDouble())
    val nextBase =
        if (nextAp >= limit.toDouble()) {
            nowMs
        } else {
            ensuredBase + pointsApplied * BA_AP_REGEN_INTERVAL_MS
        }
    return nextAp to nextBase
}

internal fun applyBaCafeStorageTick(
    cafeStoredAp: Double,
    cafeLevel: Int,
    cafeLastHourMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): Pair<Double, Long> {
    val currentHour = floorToHourMs(nowMs)
    val baseHour = if (cafeLastHourMs <= 0L || cafeLastHourMs > currentHour) currentHour else cafeLastHourMs
    if (currentHour <= baseHour) return cafeStoredAp to baseHour
    val hoursPassed = ((currentHour - baseHour) / BA_CAFE_HOURLY_INTERVAL_MS).toInt()
    if (hoursPassed <= 0) return cafeStoredAp to baseHour
    val gained = hoursPassed * cafeHourlyGain(cafeLevel)
    val cap = cafeStorageCap(cafeLevel)
    return normalizeAp((cafeStoredAp + gained).coerceAtMost(cap)) to currentHour
}

internal fun applyBaCafeClaim(cafeStoredAp: Double): Double = normalizeAp(cafeStoredAp)

internal fun applyBaCafeDebugGain(
    cafeStoredAp: Double,
    cafeLevel: Int,
): Pair<Double, Int> {
    val gained = normalizeAp(cafeHourlyGain(cafeLevel) * 3.0)
    val cap = cafeStorageCap(cafeLevel)
    val next = normalizeAp((cafeStoredAp + gained).coerceAtMost(cap))
    return next to gained.roundToInt()
}

internal fun consumeBaHeadpat(
    coffeeHeadpatMs: Long,
    serverIndex: Int,
): Long? {
    val nowMs = System.currentTimeMillis()
    val availableAt = calculateNextHeadpatAvailableMs(coffeeHeadpatMs, serverIndex)
    if (coffeeHeadpatMs > 0L && availableAt > nowMs) return null
    return nowMs
}

internal fun consumeBaInviteTicket(usedMs: Long): Long? {
    val nowMs = System.currentTimeMillis()
    val availableAt = calculateInviteTicketAvailableMs(usedMs)
    if (usedMs > 0L && availableAt > nowMs) return null
    return nowMs
}
