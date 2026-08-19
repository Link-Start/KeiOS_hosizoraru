@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.support.BA_CRAFT_FUSION_MAX_ENTRIES
import os.kei.ui.page.main.ba.support.BA_CRAFT_GENERATE_MAX_ENTRIES
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.ba.support.BaCraftGrade
import os.kei.ui.page.main.ba.support.BaCraftSlot
import os.kei.ui.page.main.ba.support.baCraftCustomDurationMinutesText
import os.kei.ui.page.main.ba.support.baCraftCustomDurationMsFromMinutes
import os.kei.ui.page.main.ba.support.computedDurationMs
import os.kei.ui.page.main.ba.support.effectiveDurationMs
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoSeconds
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.ba.support.maxEntries
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionHeader
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close

/** Which slot the edit sheet is pointed at; `null` closes it. */
internal data class BaCraftSlotEditTarget(
    val function: BaCraftFunction,
    val index: Int,
)

/**
 * Configures one craft slot.
 *
 * The output is a *duration*, and the input mirrors the mechanic that produces it. Generate gets one
 * picker per node ("解"), each including "not opened", because the three nodes can land on different
 * grades and the slot total is their sum. Fusion gets one grade plus a count, because it is one recipe
 * repeated. Both are the same summed multiset underneath — see `BaCraftSlot.computedDurationMs`.
 *
 * A custom total sits below as a first-class field, not an advanced escape hatch: the game only ever
 * shows the slot's total, so copying that number is the fastest correct path and the only exact one
 * after a booster ticket has been spent partway.
 */
@Composable
internal fun BaCraftSlotEditSheet(
    show: Boolean,
    target: BaCraftSlotEditTarget?,
    backdrop: Backdrop?,
    slot: BaCraftSlot,
    uiNowMs: Long,
    onStart: (BaCraftSlot) -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val function = target?.function ?: BaCraftFunction.Generate
    val slotIndex = target?.index ?: 0
    val maxEntries = function.maxEntries()

    var grades by rememberSaveable(target) { mutableStateOf(slot.grades) }
    var customText by rememberSaveable(target) {
        mutableStateOf(baCraftCustomDurationMinutesText(slot.customDurationMs))
    }
    var labelText by rememberSaveable(target) { mutableStateOf(slot.label) }

    // Reseed from the store every time the sheet opens: the slot may have been changed by a reminder
    // sweep or another surface while the sheet was closed.
    LaunchedEffect(show, target) {
        if (show) {
            grades = slot.grades
            customText = baCraftCustomDurationMinutesText(slot.customDurationMs)
            labelText = slot.label
        }
    }

    val draft =
        BaCraftSlot(
            startedAtMs = 0L,
            grades = grades,
            customDurationMs = baCraftCustomDurationMsFromMinutes(customText),
            label = labelText,
        )
    val totalMs = draft.effectiveDurationMs()
    val gradeNames = BaCraftGrade.entries.map { stringResource(baCraftGradeLabelRes(it)) }
    val unopenedName = stringResource(R.string.ba_craft_sheet_node_unopened)
    val notSyncedText = stringResource(R.string.ba_state_not_synced)

    SnapshotWindowBottomSheet(
        show = show,
        title =
            stringResource(
                R.string.ba_craft_slot_button_format,
                stringResource(baCraftFunctionLabelRes(function)),
                slotIndex + 1,
            ),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                icon = MiuixIcons.Regular.Close,
                contentDescription = stringResource(R.string.common_close),
                variant = GlassVariant.Bar,
                onClick = onDismissRequest,
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 14.dp) {
            SheetSectionHeader(
                text = stringResource(R.string.ba_craft_sheet_add_title),
                summary =
                    stringResource(
                        if (function == BaCraftFunction.Fusion) {
                            R.string.ba_craft_sheet_fusion_summary
                        } else {
                            R.string.ba_craft_sheet_generate_summary
                        },
                    ),
            )

            SheetSectionCard(verticalSpacing = 10.dp) {
                if (function == BaCraftFunction.Generate) {
                    repeat(BA_CRAFT_GENERATE_MAX_ENTRIES) { node ->
                        BaSheetDropdownRow(
                            backdrop = backdrop,
                            label = stringResource(R.string.ba_craft_sheet_node_format, node + 1),
                            options = listOf(unopenedName) + gradeNames,
                            // A node is only selectable once the one before it is open, matching the
                            // game: the second node cannot exist without the first.
                            selectedIndex = grades.getOrNull(node)?.let { it.ordinal + 1 } ?: 0,
                            enabled = node == 0 || grades.size >= node,
                            onSelectedIndexChange = { picked ->
                                grades =
                                    if (picked == 0) {
                                        // Closing a node closes every node after it; a gap is not a
                                        // state the game can be in.
                                        grades.take(node)
                                    } else {
                                        val grade = BaCraftGrade.entries[picked - 1]
                                        if (node < grades.size) {
                                            grades.toMutableList().also { it[node] = grade }
                                        } else {
                                            grades + grade
                                        }
                                    }
                            },
                        )
                    }
                } else {
                    BaSheetDropdownRow(
                        backdrop = backdrop,
                        label = stringResource(R.string.ba_craft_sheet_grade_label),
                        options = listOf(unopenedName) + gradeNames,
                        selectedIndex = grades.firstOrNull()?.let { it.ordinal + 1 } ?: 0,
                        enabled = true,
                        onSelectedIndexChange = { picked ->
                            grades =
                                if (picked == 0) {
                                    emptyList()
                                } else {
                                    val grade = BaCraftGrade.entries[picked - 1]
                                    List(grades.size.coerceAtLeast(1)) { grade }
                                }
                        },
                    )
                    BaSheetDropdownRow(
                        backdrop = backdrop,
                        label = stringResource(R.string.ba_craft_sheet_quantity),
                        options = (1..BA_CRAFT_FUSION_MAX_ENTRIES).map(Int::toString),
                        selectedIndex = (grades.size - 1).coerceIn(0, BA_CRAFT_FUSION_MAX_ENTRIES - 1),
                        enabled = grades.isNotEmpty(),
                        onSelectedIndexChange = { picked ->
                            val grade = grades.firstOrNull() ?: return@BaSheetDropdownRow
                            grades = List(picked + 1) { grade }
                        },
                    )
                }
            }

            SheetSummaryCard(
                title =
                    if (totalMs <= 0L) {
                        stringResource(R.string.ba_craft_sheet_empty)
                    } else {
                        stringResource(
                            R.string.ba_craft_sheet_total_format,
                            // Formatting a pure duration by asking the shared countdown formatter for
                            // the remainder from zero, so the sheet total and the card countdown read
                            // identically.
                            formatBaRemainingTime(targetMs = totalMs, nowMs = 0L),
                        )
                    },
                badgeLabel = stringResource(R.string.ba_craft_sheet_items_format, grades.size, maxEntries),
            ) {
                if (totalMs > 0L) {
                    Text(
                        text =
                            stringResource(
                                R.string.ba_craft_sheet_finish_format,
                                formatBaDateTimeNoSeconds(uiNowMs + totalMs, notSyncedText),
                            ),
                    )
                }
            }

            SheetSectionHeader(
                text = stringResource(R.string.ba_craft_sheet_custom_title),
                summary = stringResource(R.string.ba_craft_sheet_custom_summary),
            )
            SheetSectionCard(verticalSpacing = 10.dp) {
                // No SheetControlRow label here: the section header already says "custom total", and a
                // row label would just repeat the field's own unit hint back at the reader.
                AppLiquidSearchField(
                    backdrop = backdrop,
                    value = customText,
                    onValueChange = { customText = it.filter(Char::isDigit).take(5) },
                    label = stringResource(R.string.ba_craft_sheet_custom_hint),
                    variant = GlassVariant.SheetInput,
                    singleLine = true,
                    textAlign = TextAlign.Center,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SheetSectionHeader(text = stringResource(R.string.ba_craft_sheet_label_title))
            SheetSectionCard(verticalSpacing = 10.dp) {
                AppLiquidSearchField(
                    backdrop = backdrop,
                    value = labelText,
                    onValueChange = { labelText = it.take(24) },
                    label = stringResource(R.string.ba_craft_sheet_label_hint),
                    variant = GlassVariant.SheetInput,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AppDualActionRow(
                spacing = 8.dp,
                first = { modifier ->
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        text = stringResource(R.string.ba_craft_sheet_start),
                        variant = GlassVariant.SheetAction,
                        enabled = totalMs > 0L,
                        textMaxLines = 1,
                        textOverflow = TextOverflow.Ellipsis,
                        modifier = modifier,
                        onClick = { onStart(draft) },
                    )
                },
                second = { modifier ->
                    AppLiquidTextButton(
                        backdrop = backdrop,
                        text = stringResource(R.string.ba_craft_sheet_clear),
                        variant = GlassVariant.SheetAction,
                        textMaxLines = 1,
                        textOverflow = TextOverflow.Ellipsis,
                        modifier = modifier,
                        onClick = onClear,
                    )
                },
            )
        }
    }
}
