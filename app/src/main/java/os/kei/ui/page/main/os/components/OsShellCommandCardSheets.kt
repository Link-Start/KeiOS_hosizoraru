@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.formatEpochMillis
import os.kei.ui.page.main.os.osLucideCardIcon
import os.kei.ui.page.main.os.osLucideRunIcon
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.ShellCommandInputField
import os.kei.ui.page.main.os.shell.defaultOsShellCommandCardTitle
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.glass.AppLiquidAccordionCard
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetFieldBlock
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.sheet.UnsavedSheetDismissConfirmDialog
import os.kei.ui.page.main.widget.sheet.rememberUnsavedSheetDismissHandler
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.addShellCommandCards(
    cards: List<OsShellCommandCard>,
    contentBackdrop: LayerBackdrop,
    expandedStates: Map<String, Boolean>,
    runningCardIds: Set<String>,
    onExpandedChange: (String, Boolean) -> Unit,
    onHeaderLongClick: (OsShellCommandCard) -> Unit,
    onRunCard: (OsShellCommandCard) -> Unit,
) {
    cards.forEach { card ->
        item(key = "os-shell-command-${card.id}") {
            val cardIsRunning = runningCardIds.contains(card.id)
            AppLiquidAccordionCard(
                backdrop = contentBackdrop,
                title = card.title.ifBlank { defaultOsShellCommandCardTitle(card.command) },
                subtitle = card.subtitle,
                expanded = expandedStates[card.id] == true,
                onExpandedChange = { expanded -> onExpandedChange(card.id, expanded) },
                headerStartAction = {
                    Icon(
                        imageVector = osLucideCardIcon(),
                        contentDescription = card.title,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .size(22.dp)
                                .defaultMinSize(minHeight = 22.dp),
                    )
                },
                headerActions = {
                    if (cardIsRunning) {
                        LiquidCircularProgressBar(
                            progress = { 0.42f },
                            size = 16.dp,
                            strokeWidth = 2.dp,
                            activeColor = MiuixTheme.colorScheme.primary,
                            inactiveColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.24f),
                        )
                    } else {
                        AppCompactIconAction(
                            icon = osLucideRunIcon(),
                            contentDescription = stringResource(R.string.os_shell_card_cd_run_saved),
                            onClick = { onRunCard(card) },
                        )
                    }
                },
                onHeaderLongClick = { onHeaderLongClick(card) },
            ) {
                OsSectionInfoRow(
                    label = stringResource(R.string.os_shell_card_saved_command_label),
                    value = card.command,
                    copyValueOnly = true,
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_shell_card_run_output_label),
                    value =
                        card.runOutput.ifBlank {
                            stringResource(R.string.os_shell_card_run_output_not_ran)
                        },
                    copyValueOnly = true,
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_shell_card_last_ran_at_label),
                    value =
                        if (card.lastRunAtMillis > 0L) {
                            formatEpochMillis(card.lastRunAtMillis)
                        } else {
                            stringResource(R.string.os_shell_card_run_output_not_ran)
                        },
                    copyValueOnly = true,
                )
                OsSectionInfoRow(
                    label = stringResource(R.string.os_shell_card_updated_at_label),
                    value =
                        if (card.updatedAtMillis > 0L) {
                            formatEpochMillis(card.updatedAtMillis)
                        } else {
                            stringResource(R.string.common_unknown)
                        },
                    copyValueOnly = true,
                )
            }
        }
        item(key = "os-shell-command-space-${card.id}") { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
internal fun OsShellCommandVisibilityManagerSheet(
    show: Boolean,
    title: String,
    sheetBackdrop: LayerBackdrop,
    shellHintText: String,
    shellRunnerVisible: Boolean,
    onShellRunnerVisibilityChange: (Boolean) -> Unit,
    cards: List<OsShellCommandCard>,
    transferInProgress: Boolean,
    onExportAllCards: () -> Unit,
    onImportAllCards: () -> Unit,
    onDismissRequest: () -> Unit,
    onCardVisibilityChange: (String, Boolean) -> Unit,
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = sheetBackdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest,
            )
        },
    ) {
        SheetContentColumn(
            verticalSpacing = 10.dp,
        ) {
            var query by remember(show) { mutableStateOf("") }
            val shellRunnerTitle = stringResource(R.string.os_shell_card_title)
            val filteredCards =
                remember(cards, query) {
                    cards.filter { card ->
                        card.matchesShellVisibilityQuery(query)
                    }
                }
            val showShellRunner =
                shellRunnerTitle.contains(query.trim(), ignoreCase = true) ||
                    query.isBlank()
            SheetSectionCard(verticalSpacing = 8.dp) {
                AppLiquidSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.os_visibility_search_shell_label),
                    backdrop = sheetBackdrop,
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.SheetInput,
                    textColor = MiuixTheme.colorScheme.primary,
                )
            }
            if (showShellRunner) {
                SheetSectionTitle(
                    text =
                        visibilityGroupTitle(
                            title = stringResource(R.string.os_visibility_group_shell_runner),
                            count = 1,
                        ),
                )
                SheetSectionCard(verticalSpacing = 10.dp) {
                    ShellRunnerVisibilityRow(
                        title = shellRunnerTitle,
                        visible = shellRunnerVisible,
                        onVisibleChange = onShellRunnerVisibilityChange,
                    )
                }
            }
            if (filteredCards.isNotEmpty()) {
                SheetSectionTitle(
                    text =
                        visibilityGroupTitle(
                            title = stringResource(R.string.os_visibility_group_custom),
                            count = filteredCards.size,
                        ),
                )
                SheetSectionCard(verticalSpacing = 10.dp) {
                    filteredCards.forEach { item ->
                        ShellCommandVisibilityRow(
                            card = item,
                            onCardVisibilityChange = onCardVisibilityChange,
                        )
                    }
                }
            }
            if (!showShellRunner && filteredCards.isEmpty()) {
                SheetSectionCard(verticalSpacing = 10.dp) {
                    Text(
                        text = stringResource(R.string.common_no_matched_results),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
            SheetSectionCard(verticalSpacing = 8.dp) {
                Text(
                    text = stringResource(R.string.os_shell_sheet_transfer_title),
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppLiquidTextButton(
                            backdrop = sheetBackdrop,
                            text = stringResource(R.string.os_shell_sheet_action_export_backup),
                            onClick = onExportAllCards,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !transferInProgress,
                            variant = GlassVariant.SheetAction,
                            pressOverlayEnabled = true,
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppLiquidTextButton(
                            backdrop = sheetBackdrop,
                            text = stringResource(R.string.os_shell_sheet_action_import_backup),
                            onClick = onImportAllCards,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !transferInProgress,
                            variant = GlassVariant.SheetAction,
                            pressOverlayEnabled = true,
                        )
                    }
                }
            }
            SheetDescriptionText(text = stringResource(R.string.os_shell_sheet_transfer_desc))
            SheetDescriptionText(text = shellHintText)
        }
    }
}

@Composable
private fun ShellRunnerVisibilityRow(
    title: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
) {
    SheetControlRow(
        labelContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    label = stringResource(R.string.os_shell_card_builtin_badge),
                    color = Color(0xFF3B82F6),
                    size = AppStatusPillSize.Compact,
                )
            }
        },
    ) {
        AppSwitch(
            checked = visible,
            onCheckedChange = onVisibleChange,
        )
    }
}

@Composable
private fun ShellCommandVisibilityRow(
    card: OsShellCommandCard,
    onCardVisibilityChange: (String, Boolean) -> Unit,
) {
    SheetControlRow(
        labelContent = {
            Text(
                text = card.title.ifBlank { defaultOsShellCommandCardTitle(card.command) },
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        AppSwitch(
            checked = card.visible,
            onCheckedChange = { checked ->
                onCardVisibilityChange(card.id, checked)
            },
        )
    }
}

private fun OsShellCommandCard.matchesShellVisibilityQuery(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isBlank()) return true
    return title.contains(normalized, ignoreCase = true) ||
        subtitle.contains(normalized, ignoreCase = true) ||
        command.contains(normalized, ignoreCase = true)
}

@Composable
internal fun OsShellCommandCardEditorSheet(
    show: Boolean,
    title: String,
    sheetBackdrop: LayerBackdrop,
    draft: OsShellCommandCard,
    onDraftChange: (OsShellCommandCard) -> Unit,
    showDeleteAction: Boolean,
    hasUnsavedChanges: Boolean,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
) {
    val dismissHandler =
        rememberUnsavedSheetDismissHandler(
            hasUnsavedChanges = hasUnsavedChanges,
            onDismissRequest = onDismissRequest,
        )
    SnapshotWindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = dismissHandler.requestDismiss,
        allowDismiss = dismissHandler.allowDismiss,
        onBlockedDismissRequest = dismissHandler.requestDismiss,
        startAction = {
            AppLiquidIconButton(
                backdrop = sheetBackdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = dismissHandler.requestDismiss,
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = sheetBackdrop,
                variant = GlassVariant.Bar,
                icon = appLucideConfirmIcon(),
                contentDescription = stringResource(R.string.common_save),
                onClick = onSave,
            )
        },
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSectionCard(verticalSpacing = 10.dp) {
                SheetFieldBlock(title = stringResource(R.string.os_shell_card_field_title)) {
                    AppLiquidSearchField(
                        value = draft.title,
                        onValueChange = { onDraftChange(draft.copy(title = it)) },
                        label = stringResource(R.string.os_shell_card_hint_title),
                        backdrop = sheetBackdrop,
                        variant = GlassVariant.SheetInput,
                        textColor = MiuixTheme.colorScheme.primary,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SheetFieldBlock(title = stringResource(R.string.os_shell_card_field_subtitle)) {
                    AppLiquidSearchField(
                        value = draft.subtitle,
                        onValueChange = { onDraftChange(draft.copy(subtitle = it)) },
                        label = stringResource(R.string.os_shell_card_hint_subtitle),
                        backdrop = sheetBackdrop,
                        variant = GlassVariant.SheetInput,
                        textColor = MiuixTheme.colorScheme.primary,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SheetFieldBlock(title = stringResource(R.string.os_shell_card_field_command)) {
                    ShellCommandInputField(
                        value = draft.command,
                        onValueChange = { onDraftChange(draft.copy(command = it)) },
                        label = stringResource(R.string.os_shell_card_hint_command),
                        minHeight = 132.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (showDeleteAction) {
                SheetSectionTitle(
                    text = stringResource(R.string.os_shell_card_danger_title),
                    danger = true,
                )
                SheetSectionCard {
                    AppLiquidTextButton(
                        backdrop = sheetBackdrop,
                        variant = GlassVariant.SheetDangerAction,
                        text = stringResource(R.string.common_delete),
                        textColor = MiuixTheme.colorScheme.error,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
    UnsavedSheetDismissConfirmDialog(
        show = dismissHandler.showConfirmDialog,
        onKeepEditing = dismissHandler.keepEditing,
        onDiscardChanges = dismissHandler.discardChanges,
    )
}
