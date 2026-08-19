package os.kei.ui.page.main.ba.support

import kotlinx.serialization.Serializable

/**
 * What one "dailies done" run should do — the teacher's template rather than a fixed guess.
 *
 * This is the record a plain tile tap applies and the tile's long-press editor writes, so a value only
 * belongs here when a run cannot work it out for itself. The idempotence rules stay in [planBaDailyDone]:
 * "is this cooldown already spent", "is this craft slot still counting down" are facts about the account,
 * not choices about the day.
 *
 * Every default reproduces the template that shipped before any of this was configurable, which is what
 * keeps an existing install's tile behaving exactly the way its teacher left it.
 *
 * There is deliberately no cafe-AP field. The cafe pool is claimed in one action and always lands on
 * zero, so a remainder there would be a setting with one correct value.
 */
@Serializable
internal data class BaDailyDoneConfig(
    /**
     * The AP the player pool is *left holding*, not an amount to subtract.
     *
     * Zero stays the default because it is the common case, but it is a bad requirement: only the cafe
     * pool can be emptied exactly, and a teacher who stops at 37 would otherwise have to correct the
     * number by hand after every run. A value above the current pool is accepted on purpose — that is a
     * correction, and refusing it would be the surprising half of the behaviour.
     */
    val apRemaining: Int = 0,
    val startHeadpat: Boolean = true,
    val startInvite1: Boolean = true,
    val startInvite2: Boolean = true,
    /** Generate (製造) or Fusion (物質合成). One run loads one function; the other is left untouched. */
    val craftFunction: BaCraftFunction = BaCraftFunction.Generate,
    /** How many of that function's slots to load, counting from the first. Zero leaves crafts alone. */
    val craftSlots: Int = BA_DAILY_DONE_CRAFT_SLOTS,
    val craftGrade: BaCraftGrade = BA_DAILY_DONE_CRAFT_GRADE,
    /**
     * Items produced per loaded slot: opened Generate nodes, or Fusion copies of the one recipe.
     *
     * A slot's duration is this many of [craftGrade]'s duration summed — see
     * [BaCraftSlot.computedDurationMs] — which is what puts 6h *and* anything past it in reach without
     * giving the teacher a second unit to reason about.
     */
    val craftEntriesPerSlot: Int = 1,
)

internal fun BaDailyDoneConfig.normalized(): BaDailyDoneConfig =
    copy(
        apRemaining = apRemaining.coerceIn(0, BA_AP_MAX),
        craftSlots = craftSlots.coerceIn(0, BA_CRAFT_SLOT_COUNT),
        // Clamped against the *chosen* function, so moving a Fusion count of 5 over to Generate cannot
        // leave behind a node count the mechanic has no room for.
        craftEntriesPerSlot = craftEntriesPerSlot.coerceIn(1, craftFunction.maxEntries()),
    )

/** The grades one loaded slot produces, i.e. exactly what a run writes into [BaCraftSlot.grades]. */
internal fun BaDailyDoneConfig.craftSlotGrades(): List<BaCraftGrade> =
    List(normalized().craftEntriesPerSlot) { craftGrade }

/** How long each loaded slot will run for, by the one formula both craft functions share. */
internal fun BaDailyDoneConfig.craftSlotDurationMs(): Long = craftSlotGrades().sumOf { it.durationMs }
