@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.baCraftGradeLabelRes
import os.kei.ui.page.main.ba.support.BA_CRAFT_GENERATE_MAX_ENTRIES
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.ba.support.BaCraftSlot
import os.kei.ui.page.main.ba.support.effectiveDurationMs
import os.kei.ui.page.main.ba.support.endAtMs
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoSeconds
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.ba.support.isActive
import os.kei.ui.page.main.ba.support.isComplete
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.ba.support.maxEntries
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One cafe cooldown as its own card, in the shape the GitHub page uses for a tracked project.
 *
 * ## Why a card per cooldown rather than three rows in the cafe card
 *
 * The rows were 6 glass surfaces inside one tall card, and a surface inside a card is composed for as
 * long as *any* sliver of that card is on screen — measured at +16.6ms of RenderThread for the craft
 * card's twelve (`docs/planning/ba-craft-card-frame-cost.md`). A card per cooldown is its own lazy list
 * item, so it is composed only while it is itself visible, disposed the moment it is not, and it takes
 * its own place in the edge-stack pile.
 *
 * ## What the expanded card is for
 *
 * Not a second copy of the subtitle. The countdown is already in the header, so opening the card has to
 * add what a countdown cannot say: how far through the wait it is, when it started, and how long this
 * cooldown runs for — the headpat's is not a constant, because the cafe's student refresh can free it
 * early, so the length is derived from the two instants rather than named.
 *
 * The primary action is in the body **always**, disabled while it is not usable. Hiding it made the
 * expanded card worse than the row it replaced: there was nothing to press and no way to tell whether
 * that was the point. A usable cooldown additionally keeps a button in the collapsed header, so using it
 * never costs an expand.
 */
@Composable
internal fun BaCooldownSlotCard(
    backdrop: Backdrop?,
    title: String,
    iconRes: Int,
    ready: Boolean,
    usedAtMs: Long,
    availableAtMs: Long,
    accentColor: Color,
    countdownColor: Color,
    uiNowMs: Long,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onUse: () -> Unit,
    onEditCooldown: () -> Unit,
    actionLabel: String,
    cardTestTag: String? = null,
    adjustTestTag: String? = null,
) {
    val notSyncedText = stringResource(R.string.ba_state_not_synced)
    val readyText = stringResource(R.string.ba_slot_card_ready)
    AppLiquidAccordionCard(
        backdrop = backdrop,
        title = title,
        // The time is the reason this card exists, so it goes beside the title as a pill rather than on
        // a second line under it — the same shape the AP and cafe cards use for their own countdowns.
        // Collapsing to one line is what lets nine of these fit where three rows used to.
        subtitle = "",
        titleAccessory = {
            BaSlotPill(
                label = if (ready) readyText else formatBaRemainingTime(availableAtMs, uiNowMs),
                // Ready reads in the card's own accent because it is actionable; a countdown reads in the
                // shared countdown blue, so every waiting number on the page is the same colour.
                color = if (ready) accentColor else countdownColor,
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = cardTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        headerStartAction = {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        },
        // Usable now: press it without opening the card. Nothing to press otherwise, and a disabled
        // button in every collapsed header would be one more glass surface saying no.
        headerActions =
            if (ready) {
                {
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        text = actionLabel,
                        textColor = accentColor,
                        containerColor = accentColor,
                        variant = GlassVariant.Content,
                        onClick = onUse,
                    )
                }
            } else {
                null
            },
    ) {
        if (!ready && usedAtMs > 0L && availableAtMs > usedAtMs) {
            val span = (availableAtMs - usedAtMs).toFloat()
            LiquidLinearProgressBar(
                progress = { ((uiNowMs - usedAtMs).toFloat() / span).coerceIn(0f, 1f) },
                activeColor = accentColor,
                contentDescription = title,
            )
        }
        // The instant, which the pill deliberately does not carry: a countdown answers "how long", and
        // this answers "when", and only one of the two is worth a glance while scrolling.
        BaSlotCardFact(
            label = stringResource(R.string.ba_slot_card_available_at_format, formatBaDateTimeNoSeconds(availableAtMs, notSyncedText)),
            value = if (ready) readyText else formatBaRemainingTime(availableAtMs, uiNowMs),
            valueColor = if (ready) accentColor else countdownColor,
        )
        if (usedAtMs > 0L) {
            BaSlotCardFact(
                label = stringResource(R.string.ba_slot_card_used_at_format, formatBaDateTimeNoSeconds(usedAtMs, notSyncedText)),
                value =
                    if (availableAtMs > usedAtMs) {
                        stringResource(
                            R.string.ba_slot_card_cooldown_length_format,
                            // Derived, not a constant: the headpat also frees up at the cafe's student
                            // refresh, so its real cooldown is whatever these two instants are apart.
                            formatBaRemainingTime(targetMs = availableAtMs - usedAtMs, nowMs = 0L),
                        )
                    } else {
                        ""
                    },
                valueColor = countdownColor,
            )
        }
        AppDualActionRow(
            spacing = 8.dp,
            first = { rowModifier ->
                AppLiquidTextButton(
                    modifier = rowModifier,
                    backdrop = backdrop,
                    text = actionLabel,
                    textColor = accentColor,
                    containerColor = accentColor,
                    variant = GlassVariant.SheetAction,
                    // Always here, disabled while it cannot be used — see the KDoc.
                    enabled = ready,
                    textMaxLines = 1,
                    textOverflow = TextOverflow.Ellipsis,
                    onClick = onUse,
                )
            },
            second = { rowModifier ->
                AppLiquidTextButton(
                    modifier = rowModifier.then(adjustTestTag?.let { Modifier.testTag(it) } ?: Modifier),
                    backdrop = backdrop,
                    text = stringResource(R.string.ba_slot_card_edit_cooldown),
                    textColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    containerColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    variant = GlassVariant.SheetAction,
                    textMaxLines = 1,
                    textOverflow = TextOverflow.Ellipsis,
                    onClick = onEditCooldown,
                )
            },
        )
    }
}

/**
 * One Craft Chamber slot as its own card.
 *
 * The expanded body is the slot's own composition, the same facts `BaCraftSlotEditSheet` shows and in the
 * same words: which nodes are open at which grade for a Generate slot, the grade and the copy count for a
 * Fusion one, then the summed total and the instant it lands. That is what a craft *is* — the header's
 * countdown is only its remainder — and it is why the card is worth opening at all.
 *
 * Configuring stays in the sheet. The card reports; the sheet edits.
 */
@Composable
internal fun BaCraftSlotCard(
    backdrop: Backdrop?,
    title: String,
    function: BaCraftFunction,
    slot: BaCraftSlot,
    accentColor: Color,
    countdownColor: Color,
    uiNowMs: Long,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onConfigure: () -> Unit,
    onClear: () -> Unit,
    cardTestTag: String? = null,
    buttonTestTag: String? = null,
) {
    val notSyncedText = stringResource(R.string.ba_state_not_synced)
    val idleText = stringResource(R.string.ba_craft_slot_idle)
    val doneText = stringResource(R.string.ba_craft_slot_done)
    val unopenedText = stringResource(R.string.ba_craft_sheet_node_unopened)
    val active = slot.isActive()
    val complete = slot.isComplete(uiNowMs)
    val endAtMs = slot.endAtMs()
    val totalMs = slot.effectiveDurationMs()
    AppLiquidAccordionCard(
        backdrop = backdrop,
        title = title,
        subtitle = "",
        titleAccessory = {
            BaSlotPill(
                label =
                    when {
                        !active -> idleText
                        complete -> doneText
                        else -> formatBaRemainingTime(endAtMs, uiNowMs)
                    },
                // Three states, three readings: a finished craft is the only one needing action, so it
                // takes the completion green; a running one the countdown blue; an idle one recedes.
                color =
                    when {
                        !active -> MiuixTheme.colorScheme.onBackgroundVariant
                        complete -> AppStatusColors.Fresh
                        else -> countdownColor
                    },
            )
            // A craft carries more than a clock, and these are the two facts that decide whether it is
            // worth opening: what it is producing, and how much of it. Flat tags rather than glass —
            // there are two of them on six cards, and the time pill should stay the material one.
            slot.grades.firstOrNull()?.let { grade ->
                BaSlotPill(
                    label = stringResource(baCraftGradeLabelRes(grade)),
                    color = accentColor,
                )
                BaSlotPill(
                    label =
                        if (function == BaCraftFunction.Generate) {
                            stringResource(
                                R.string.ba_craft_pill_nodes_format,
                                slot.grades.size,
                                function.maxEntries(),
                            )
                        } else {
                            stringResource(R.string.ba_craft_pill_copies_format, slot.grades.size)
                        },
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = cardTestTag?.let { Modifier.testTag(it) } ?: Modifier,
    ) {
        if (active && !complete && totalMs > 0L) {
            LiquidLinearProgressBar(
                progress = { ((uiNowMs - slot.startedAtMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) },
                activeColor = accentColor,
                contentDescription = title,
            )
        }

        if (slot.grades.isEmpty()) {
            BaSlotCardFact(
                label = stringResource(R.string.ba_slot_card_craft_idle_detail),
                value = "",
                valueColor = countdownColor,
            )
        } else if (function == BaCraftFunction.Generate) {
            // One line per node, "not opened" included, so the card reads like the editor's own picker.
            repeat(BA_CRAFT_GENERATE_MAX_ENTRIES) { node ->
                val grade = slot.grades.getOrNull(node)
                BaSlotCardFact(
                    label = stringResource(R.string.ba_craft_sheet_node_format, node + 1),
                    value = grade?.let { stringResource(baCraftGradeLabelRes(it)) } ?: unopenedText,
                    valueColor = if (grade != null) accentColor else MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        } else {
            BaSlotCardFact(
                label = stringResource(R.string.ba_craft_sheet_grade_label),
                value = stringResource(baCraftGradeLabelRes(slot.grades.first())),
                valueColor = accentColor,
            )
            BaSlotCardFact(
                label = stringResource(R.string.ba_craft_sheet_quantity),
                value = slot.grades.size.toString(),
                valueColor = accentColor,
            )
        }

        if (totalMs > 0L) {
            BaSlotCardFact(
                label =
                    stringResource(
                        R.string.ba_craft_sheet_total_format,
                        formatBaRemainingTime(targetMs = totalMs, nowMs = 0L),
                    ),
                value =
                    if (active) {
                        stringResource(
                            R.string.ba_craft_sheet_finish_format,
                            formatBaDateTimeNoSeconds(endAtMs, notSyncedText),
                        )
                    } else {
                        ""
                    },
                valueColor = countdownColor,
            )
            // The same "n of m items" the editor's own summary carries, so the card and the sheet agree
            // on what the slot is producing rather than each describing it differently.
            BaSlotCardFact(
                label =
                    stringResource(
                        R.string.ba_craft_sheet_items_format,
                        slot.grades.size,
                        function.maxEntries(),
                    ),
                value = "",
                valueColor = countdownColor,
            )
        }

        if (slot.label.isNotBlank()) {
            BaSlotCardFact(
                label = stringResource(R.string.ba_slot_card_note_label),
                value = slot.label,
                valueColor = MiuixTheme.colorScheme.onBackground,
            )
        }

        AppDualActionRow(
            spacing = 8.dp,
            first = { rowModifier ->
                AppLiquidTextButton(
                    modifier = rowModifier.then(buttonTestTag?.let { Modifier.testTag(it) } ?: Modifier),
                    backdrop = backdrop,
                    text = stringResource(R.string.ba_slot_card_configure),
                    textColor = accentColor,
                    containerColor = accentColor,
                    variant = GlassVariant.SheetAction,
                    textMaxLines = 1,
                    textOverflow = TextOverflow.Ellipsis,
                    onClick = onConfigure,
                )
            },
            second = { rowModifier ->
                AppLiquidTextButton(
                    modifier = rowModifier,
                    backdrop = backdrop,
                    text = stringResource(R.string.ba_slot_card_clear),
                    textColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    containerColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    variant = GlassVariant.SheetAction,
                    // A slot with nothing loaded has nothing to clear, and saying so beats hiding it.
                    enabled = active,
                    textMaxLines = 1,
                    textOverflow = TextOverflow.Ellipsis,
                    onClick = onClear,
                )
            },
        )
    }
}

/**
 * A pill on a slot card's header line, drawn flat rather than as glass.
 *
 * [StatusPill] already has a static path — it takes it when no backdrop can be resolved — and blanking
 * the parent backdrop is how a caller asks for it. That matters here because there are up to three pills
 * on each of nine cards, and a glass pill is its own offscreen layer at roughly 1.4ms of RenderThread
 * while scrolling: the time pills alone measured **+12ms** when they were glass
 * (`docs/planning/ba-craft-card-frame-cost.md`).
 *
 * Flat keeps the colour, the shape and the border, and gives up only the blur. On a pill this small,
 * sitting on a card that is already frosting the page behind it, the blur was resolving to a few pixels
 * of an already-blurred surface — the cheap half of the material to lose. The AP and cafe cards keep
 * their glass pills: there are three of those cards, not nine.
 */
@Composable
private fun BaSlotPill(
    label: String,
    color: Color,
) {
    CompositionLocalProvider(LocalLiquidParentBackdrop provides null) {
        StatusPill(
            label = label,
            color = color,
            size = AppStatusPillSize.Compact,
            backdrop = null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One label-and-value line, the unit both slot cards build their body out of.
 *
 * Plain text rather than a nested panel: a card body inside a card is where the frame cost of this page
 * came from in the first place, and a fact does not need its own glass surface to be readable.
 */
@Composable
private fun BaSlotCardFact(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
