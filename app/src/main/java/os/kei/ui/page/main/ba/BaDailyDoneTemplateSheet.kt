@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BA_CRAFT_SLOT_COUNT
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.ba.support.BaCraftGrade
import os.kei.ui.page.main.ba.support.BaDailyDoneConfig
import os.kei.ui.page.main.ba.support.craftSlotDurationMs
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.ba.support.maxEntries
import os.kei.ui.page.main.ba.support.normalized
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionHeader
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.sheet.UnsavedSheetDismissConfirmDialog
import os.kei.ui.page.main.widget.sheet.rememberUnsavedSheetDismissHandler
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close

/** Digits the AP field accepts, taken from the store bound rather than restated as a literal. */
private val AP_REMAINING_DIGITS = BA_AP_MAX.toString().length

/** What "apply now" in the template editor would run against. */
internal sealed interface BaDailyDoneTemplateScope {
    /** The all-accounts tile, the launcher shortcut, and the in-app entry point. */
    data object AllAccounts : BaDailyDoneTemplateScope

    data class Account(val displayName: String) : BaDailyDoneTemplateScope

    /**
     * A per-account tile whose account has been deleted.
     *
     * Still an editing surface, because the template is shared and worth fixing from here; just not a
     * runnable one, so apply is disabled rather than silently doing nothing.
     */
    data object Unbound : BaDailyDoneTemplateScope
}

/**
 * The daily-done template, edited in place.
 *
 * Reached by long-pressing a daily quick-settings tile, which is the gesture the platform reserves for
 * "configure this tile" — before this existed the long-press fell through to the system's app-info
 * screen, which is the fallback for a tile with nothing to configure.
 *
 * Two actions, because the two intents are genuinely different: **save** records the template a later
 * tap will apply, **apply now** does that and then runs it. Neither is a preview — the whole point of
 * the editor is that the numbers it writes are the numbers the one-tap path uses, so there is only ever
 * one template in play.
 */
@Composable
internal fun BaDailyDoneTemplateSheet(
    show: Boolean,
    scope: BaDailyDoneTemplateScope,
    config: BaDailyDoneConfig,
    backdrop: Backdrop? = null,
    applying: Boolean = false,
    onSave: (BaDailyDoneConfig) -> Unit,
    onApply: (BaDailyDoneConfig) -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
) {
    var apText by rememberSaveable(show) { mutableStateOf(config.apRemaining.toString()) }
    var startHeadpat by rememberSaveable(show) { mutableStateOf(config.startHeadpat) }
    var startInvite1 by rememberSaveable(show) { mutableStateOf(config.startInvite1) }
    var startInvite2 by rememberSaveable(show) { mutableStateOf(config.startInvite2) }
    var craftFunction by rememberSaveable(show) { mutableStateOf(config.craftFunction) }
    var craftSlots by rememberSaveable(show) { mutableStateOf(config.craftSlots) }
    var craftGrade by rememberSaveable(show) { mutableStateOf(config.craftGrade) }
    var craftEntries by rememberSaveable(show) { mutableStateOf(config.craftEntriesPerSlot) }

    // Reseed on every opening: the template is global, so another surface — the in-app entry point, a
    // WebDAV merge — may have rewritten it while this sheet was closed.
    LaunchedEffect(show, config) {
        if (!show) return@LaunchedEffect
        apText = config.apRemaining.toString()
        startHeadpat = config.startHeadpat
        startInvite1 = config.startInvite1
        startInvite2 = config.startInvite2
        craftFunction = config.craftFunction
        craftSlots = config.craftSlots
        craftGrade = config.craftGrade
        craftEntries = config.craftEntriesPerSlot
    }

    val draft =
        BaDailyDoneConfig(
            // Blank reads as zero rather than as "unchanged": an empty field looks like an emptied pool,
            // and the alternative — refusing to save until something is typed — blocks the commonest value.
            apRemaining = apText.trim().toIntOrNull() ?: 0,
            startHeadpat = startHeadpat,
            startInvite1 = startInvite1,
            startInvite2 = startInvite2,
            craftFunction = craftFunction,
            craftSlots = craftSlots,
            craftGrade = craftGrade,
            craftEntriesPerSlot = craftEntries,
        ).normalized()

    val dismissHandler =
        rememberUnsavedSheetDismissHandler(
            hasUnsavedChanges = draft != config,
            onDismissRequest = onDismissRequest,
        )

    val accentGreen = AppStatusColors.Fresh
    val slotDurationMs = draft.craftSlotDurationMs()
    val functionNames = BaCraftFunction.entries.map { stringResource(baCraftFunctionLabelRes(it)) }
    val gradeNames =
        BaCraftGrade.entries.map { grade ->
            stringResource(
                R.string.ba_daily_template_craft_grade_format,
                stringResource(baCraftGradeLabelRes(grade)),
                // The duration is the whole reason the grade matters here: "6h or shorter" is the choice
                // being made, and the grade name alone does not say which is which.
                formatBaRemainingTime(targetMs = grade.durationMs, nowMs = 0L),
            )
        }
    val slotCountNames =
        listOf(stringResource(R.string.ba_daily_template_craft_slots_none)) +
            (1..BA_CRAFT_SLOT_COUNT).map { count ->
                stringResource(R.string.ba_daily_template_craft_slots_format, count)
            }

    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_daily_template_title),
        onDismissRequest = dismissHandler.requestDismiss,
        onDismissFinished = onDismissFinished,
        allowDismiss = dismissHandler.allowDismiss,
        onBlockedDismissRequest = dismissHandler.requestDismiss,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Close,
                contentDescription = stringResource(R.string.common_close),
                variant = GlassVariant.Bar,
                onClick = dismissHandler.requestDismiss,
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 14.dp) {
            SheetSectionHeader(
                text =
                    when (scope) {
                        BaDailyDoneTemplateScope.AllAccounts ->
                            stringResource(R.string.ba_daily_template_scope_all)

                        is BaDailyDoneTemplateScope.Account ->
                            stringResource(
                                R.string.ba_daily_template_scope_account_format,
                                scope.displayName,
                            )

                        BaDailyDoneTemplateScope.Unbound ->
                            stringResource(R.string.ba_daily_template_scope_unbound)
                    },
                summary = stringResource(R.string.ba_daily_template_scope_summary),
            )

            SheetSectionHeader(
                text = stringResource(R.string.ba_daily_template_ap_title),
                summary = stringResource(R.string.ba_daily_template_ap_summary),
            )
            SheetSectionCard(verticalSpacing = 10.dp) {
                SheetControlRow(label = stringResource(R.string.ba_daily_template_ap_label)) {
                    AppLiquidSearchField(
                        modifier = Modifier.width(116.dp),
                        value = apText,
                        onValueChange = { input ->
                            apText = input.filter(Char::isDigit).trimStart('0').take(AP_REMAINING_DIGITS)
                        },
                        // The hint is the empty-field meaning, which is also the default: spent to zero.
                        label = "0",
                        backdrop = backdrop,
                        variant = GlassVariant.SheetInput,
                        singleLine = true,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        textColor = accentGreen,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                    )
                }
            }

            SheetSectionHeader(
                text = stringResource(R.string.ba_daily_template_cooldown_title),
                summary = stringResource(R.string.ba_daily_template_cooldown_summary),
            )
            SheetSectionCard(verticalSpacing = 10.dp) {
                SheetControlRow(label = stringResource(R.string.ba_daily_template_headpat)) {
                    AppSwitch(
                        checked = startHeadpat,
                        onCheckedChange = { startHeadpat = it },
                        // The baseline profile's handle on this sheet — see KeiOsTestTags.
                        modifier = Modifier.testTag(KeiOsTestTags.BaDailyTemplateHeadpatSwitch),
                    )
                }
                SheetControlRow(label = stringResource(R.string.ba_daily_template_invite1)) {
                    AppSwitch(checked = startInvite1, onCheckedChange = { startInvite1 = it })
                }
                SheetControlRow(label = stringResource(R.string.ba_daily_template_invite2)) {
                    AppSwitch(checked = startInvite2, onCheckedChange = { startInvite2 = it })
                }
            }

            SheetSectionHeader(
                text = stringResource(R.string.ba_daily_template_craft_title),
                summary = stringResource(R.string.ba_daily_template_craft_summary),
            )
            SheetSectionCard(verticalSpacing = 10.dp) {
                BaSheetDropdownRow(
                    backdrop = backdrop,
                    label = stringResource(R.string.ba_daily_template_craft_function),
                    options = functionNames,
                    selectedIndex = craftFunction.ordinal,
                    onSelectedIndexChange = { picked ->
                        val next = BaCraftFunction.entries[picked]
                        craftFunction = next
                        // Fusion allows five copies, Generate only three nodes. Clamping here rather than
                        // leaving it to normalization keeps the count dropdown showing a value it offers.
                        craftEntries = craftEntries.coerceAtMost(next.maxEntries())
                    },
                )
                BaSheetDropdownRow(
                    backdrop = backdrop,
                    label = stringResource(R.string.ba_daily_template_craft_slots),
                    options = slotCountNames,
                    selectedIndex = craftSlots,
                    onSelectedIndexChange = { picked -> craftSlots = picked },
                )
                BaSheetDropdownRow(
                    backdrop = backdrop,
                    label = stringResource(R.string.ba_daily_template_craft_grade),
                    options = gradeNames,
                    selectedIndex = craftGrade.ordinal,
                    enabled = craftSlots > 0,
                    onSelectedIndexChange = { picked -> craftGrade = BaCraftGrade.entries[picked] },
                )
                BaSheetDropdownRow(
                    backdrop = backdrop,
                    label = stringResource(R.string.ba_daily_template_craft_entries),
                    options = (1..craftFunction.maxEntries()).map(Int::toString),
                    selectedIndex = (craftEntries - 1).coerceIn(0, craftFunction.maxEntries() - 1),
                    enabled = craftSlots > 0,
                    onSelectedIndexChange = { picked -> craftEntries = picked + 1 },
                )
            }

            SheetSummaryCard(
                title =
                    if (draft.craftSlots <= 0) {
                        stringResource(R.string.ba_daily_template_summary_idle)
                    } else {
                        stringResource(
                            R.string.ba_daily_template_summary_format,
                            // Formatting a pure duration by asking the shared countdown formatter for the
                            // remainder from zero, so this reads identically to the craft card's countdown.
                            formatBaRemainingTime(targetMs = slotDurationMs, nowMs = 0L),
                        )
                    },
                accentColor = accentGreen,
                badgeLabel =
                    draft.craftSlots.takeIf { it > 0 }?.let { slots ->
                        stringResource(
                            R.string.ba_daily_template_summary_badge_format,
                            functionNames[draft.craftFunction.ordinal],
                            slots,
                        )
                    },
                // Title and badge carry the whole summary; a body line would only restate the rows above.
                content = {},
            )

            AppDualActionRow(
                spacing = 8.dp,
                first = { modifier ->
                    AppLiquidTextButton(
                        modifier = modifier,
                        backdrop = backdrop,
                        text = stringResource(R.string.ba_daily_template_apply),
                        textColor = accentGreen,
                        containerColor = accentGreen,
                        variant = GlassVariant.SheetAction,
                        // Nothing to apply to once the tile's account is gone; the template still saves.
                        enabled = !applying && scope != BaDailyDoneTemplateScope.Unbound,
                        textMaxLines = 1,
                        textOverflow = TextOverflow.Ellipsis,
                        pressOverlayEnabled = true,
                        onClick = { onApply(draft) },
                    )
                },
                second = { modifier ->
                    AppLiquidTextButton(
                        modifier = modifier,
                        backdrop = backdrop,
                        text = stringResource(R.string.ba_daily_template_save),
                        variant = GlassVariant.SheetAction,
                        enabled = !applying,
                        textMaxLines = 1,
                        textOverflow = TextOverflow.Ellipsis,
                        onClick = { onSave(draft) },
                    )
                },
            )
        }
    }

    UnsavedSheetDismissConfirmDialog(
        show = dismissHandler.showConfirmDialog,
        onKeepEditing = dismissHandler.keepEditing,
        onDiscardChanges = dismissHandler.discardChanges,
    )
}
