package os.kei.ui.page.main.ba.support

/**
 * How many Generate slots the daily template loads *by default*.
 *
 * Two, not three. Sensei's habitual daily opens the first two and leaves the third for whatever the
 * day actually calls for. Configurable since the tile grew a long-press editor — see
 * [BaDailyDoneConfig.craftSlots] — but this is still what a fresh install starts from.
 */
internal const val BA_DAILY_DONE_CRAFT_SLOTS = 2

/**
 * The grade the template assumes for each loaded craft, by default.
 *
 * [BaCraftGrade.High] is 3h for a single node, which is what an advanced material or a gift comes out
 * as — the outcome a one-node craft is actually run for, and the best value per keystone. Opening more
 * nodes multiplies the cost far faster than the reward, so the default deliberately stops at one node
 * per slot rather than guessing at a bigger craft the teacher did not ask for. A teacher who *does*
 * want the bigger craft now says so in [BaDailyDoneConfig] instead of living with the guess.
 */
internal val BA_DAILY_DONE_CRAFT_GRADE = BaCraftGrade.High

/** What a daily-done application actually changed, so the caller can report it honestly. */
internal data class BaDailyDoneOutcome(
    /**
     * The player AP pool was written to a different number.
     *
     * Not named "cleared": the template leaves [BaDailyDoneConfig.apRemaining] behind, which is usually
     * but not always zero, and a teacher correcting a stale reading upwards is a legitimate change too.
     */
    val apAdjusted: Boolean = false,
    val cafeApCleared: Boolean = false,
    val headpatStarted: Boolean = false,
    val invite1Started: Boolean = false,
    val invite2Started: Boolean = false,
    val craftSlotsStarted: Int = 0,
) {
    val changedAnything: Boolean
        get() = apAdjusted ||
            cafeApCleared ||
            headpatStarted ||
            invite1Started ||
            invite2Started ||
            craftSlotsStarted > 0
}

/** The new per-account values a daily-done application would write. */
internal data class BaDailyDonePlan(
    val apCurrent: Double,
    val apRegenBaseMs: Long,
    val apSyncMs: Long,
    val apLastNotifiedLevel: Int,
    val cafeStoredAp: Double,
    val cafeLastHourMs: Long,
    val cafeApLastNotifiedLevel: Int,
    val coffeeHeadpatMs: Long,
    val coffeeInvite1UsedMs: Long,
    val coffeeInvite2UsedMs: Long,
    val craft: BaCraftState,
    val outcome: BaDailyDoneOutcome,
)

/**
 * "I did my dailies" — the one-tap template, computed for a single account.
 *
 * Deliberately **idempotent on anything already spent**: only a cooldown that has actually elapsed is
 * restarted, and only a craft slot that is idle or already finished is loaded. Tapping twice in a row
 * therefore does not push a cooldown out or overwrite a craft still in flight, which is what makes it
 * safe to bind to a quick-settings tile where a stray tap costs nothing.
 *
 * [config] says what the teacher's daily looks like; everything else here is a fact about the account
 * and stays out of it. The AP pool lands on [BaDailyDoneConfig.apRemaining] with its regeneration anchor
 * re-based to now, so the next cycle starts from this moment however much was left. The cafe pool goes
 * to **zero**, which is not the same as claiming it: claiming moves cafe AP into the player pool, while
 * this template says the teacher already spent both, so each is written independently.
 */
internal fun planBaDailyDone(
    snapshot: BaPageSnapshot,
    config: BaDailyDoneConfig = BaDailyDoneConfig(),
    nowMs: Long = System.currentTimeMillis(),
): BaDailyDonePlan {
    val template = config.normalized()
    val apTarget = normalizeAp(template.apRemaining.toDouble())
    val apAdjusted = normalizeAp(snapshot.apCurrent) != apTarget
    val cafeApCleared = snapshot.cafeStoredAp > 0.0

    val headpatAt =
        if (template.startHeadpat) {
            consumeBaHeadpatIfReady(snapshot.coffeeHeadpatMs, snapshot.serverIndex, nowMs)
        } else {
            null
        }
    val invite1At =
        if (template.startInvite1) consumeBaInviteTicketIfReady(snapshot.coffeeInvite1UsedMs, nowMs) else null
    val invite2At =
        if (template.startInvite2) consumeBaInviteTicketIfReady(snapshot.coffeeInvite2UsedMs, nowMs) else null

    var craft = snapshot.craft.normalized()
    var craftStarted = 0
    val slotGrades = template.craftSlotGrades()
    repeat(template.craftSlots) { index ->
        val slot = craft.slotAt(template.craftFunction, index)
        // Free means idle OR already finished: a completed slot has been collected, so reusing it is
        // exactly what the teacher would do next. A slot still counting down is left alone.
        val free = !slot.isActive() || slot.isComplete(nowMs)
        if (free) {
            craft =
                craft.withSlotAt(
                    function = template.craftFunction,
                    index = index,
                    slot =
                        BaCraftSlot(
                            startedAtMs = nowMs,
                            grades = slotGrades,
                        ),
                )
            craftStarted++
        }
    }

    return BaDailyDonePlan(
        apCurrent = apTarget,
        apRegenBaseMs = nowMs,
        apSyncMs = nowMs,
        // Reset only when the pool really is empty. That case matches what the in-app cafe claim does —
        // a reminder that fired at yesterday's level must not dedupe away the next one — but a
        // configured remainder is different: it can sit above the teacher's own threshold, and clearing
        // the marker there would re-announce a level they have already been told about. Keeping it costs
        // nothing, because the dedup is an equality against the level and a changed pool no longer
        // matches; and the reminder sweep clears the marker itself once AP is back under the threshold.
        apLastNotifiedLevel = if (apTarget <= 0.0) -1 else snapshot.apLastNotifiedLevel,
        cafeStoredAp = 0.0,
        cafeLastHourMs = floorToHourMs(nowMs),
        cafeApLastNotifiedLevel = -1,
        coffeeHeadpatMs = headpatAt ?: snapshot.coffeeHeadpatMs,
        coffeeInvite1UsedMs = invite1At ?: snapshot.coffeeInvite1UsedMs,
        coffeeInvite2UsedMs = invite2At ?: snapshot.coffeeInvite2UsedMs,
        craft = craft,
        outcome =
            BaDailyDoneOutcome(
                apAdjusted = apAdjusted,
                cafeApCleared = cafeApCleared,
                headpatStarted = headpatAt != null,
                invite1Started = invite1At != null,
                invite2Started = invite2At != null,
                craftSlotsStarted = craftStarted,
            ),
    )
}

/**
 * `nowMs` when the headpat cooldown has elapsed, else `null`.
 *
 * Mirrors the in-app `consumeBaHeadpat` rule, including its server dependence: the headpat also frees up
 * at the cafe's student refresh, so the earlier of cooldown-end and refresh wins.
 */
private fun consumeBaHeadpatIfReady(
    coffeeHeadpatMs: Long,
    serverIndex: Int,
    nowMs: Long,
): Long? {
    if (coffeeHeadpatMs <= 0L) return nowMs
    return nowMs.takeIf { calculateNextHeadpatAvailableMs(coffeeHeadpatMs, serverIndex) <= nowMs }
}

private fun consumeBaInviteTicketIfReady(
    usedMs: Long,
    nowMs: Long,
): Long? {
    if (usedMs <= 0L) return nowMs
    return nowMs.takeIf { calculateInviteTicketAvailableMs(usedMs) <= nowMs }
}
