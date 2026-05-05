package os.kei.ui.page.main.os.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetActionGroup
import os.kei.ui.page.main.widget.sheet.SheetChoiceCard
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet

@Composable
internal fun OsShellBehaviorSettingsSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    settings: OsShellRunnerSettings,
    onPersistInputEnabledChange: (Boolean) -> Unit,
    onTimeoutSecondsChange: (Int) -> Unit,
    onDangerousCommandConfirmChange: (Boolean) -> Unit,
    onCompletionToastChange: (Boolean) -> Unit,
    onStartupBehaviorChange: (OsShellRunnerStartupBehavior) -> Unit,
    onExitCleanupModeChange: (OsShellRunnerExitCleanupMode) -> Unit,
) {
    var timeoutExpanded by remember { mutableStateOf(false) }
    var timeoutAnchorBounds by remember { mutableStateOf<IntRect?>(null) }

    OsShellSettingsBottomSheet(
        show = show,
        title = stringResource(R.string.os_shell_behavior_settings_sheet_title),
        onDismissRequest = onDismissRequest
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSectionTitle(text = stringResource(R.string.os_shell_settings_section_general))
            SheetSectionCard(verticalSpacing = 10.dp) {
                SheetControlRow(
                    label = stringResource(R.string.os_shell_settings_persist_input_label),
                    summary = stringResource(R.string.os_shell_settings_persist_input_summary)
                ) {
                    AppSwitch(
                        checked = settings.persistInput,
                        onCheckedChange = onPersistInputEnabledChange
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.os_shell_settings_timeout_title),
                    summary = stringResource(R.string.os_shell_settings_timeout_option_summary)
                ) {
                    OsShellSettingsDropdown(
                        selectedText = stringResource(
                            R.string.os_shell_settings_timeout_option_seconds,
                            settings.commandTimeoutSeconds
                        ),
                        options = shellRunnerTimeoutOptionsSeconds.map { seconds ->
                            stringResource(
                                R.string.os_shell_settings_timeout_option_seconds,
                                seconds
                            )
                        },
                        selectedIndex = shellRunnerTimeoutOptionsSeconds.indexOf(settings.commandTimeoutSeconds),
                        expanded = timeoutExpanded,
                        anchorBounds = timeoutAnchorBounds,
                        onExpandedChange = { timeoutExpanded = it },
                        onAnchorBoundsChange = { timeoutAnchorBounds = it },
                        onSelectedIndexChange = { index ->
                            shellRunnerTimeoutOptionsSeconds.getOrNull(index)
                                ?.let(onTimeoutSecondsChange)
                            timeoutExpanded = false
                        }
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.os_shell_settings_danger_confirm_label),
                    summary = stringResource(R.string.os_shell_settings_danger_confirm_summary)
                ) {
                    AppSwitch(
                        checked = settings.dangerousCommandConfirm,
                        onCheckedChange = onDangerousCommandConfirmChange
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.os_shell_settings_completion_toast_label),
                    summary = stringResource(R.string.os_shell_settings_completion_toast_summary)
                ) {
                    AppSwitch(
                        checked = settings.completionToast,
                        onCheckedChange = onCompletionToastChange
                    )
                }
            }

            SheetSectionTitle(text = stringResource(R.string.os_shell_settings_startup_behavior_title))
            SheetActionGroup(verticalSpacing = 8.dp) {
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_startup_behavior_focus_title),
                    summary = stringResource(R.string.os_shell_settings_startup_behavior_focus_summary),
                    selected = settings.startupBehavior == OsShellRunnerStartupBehavior.FocusInput,
                    onSelect = { onStartupBehaviorChange(OsShellRunnerStartupBehavior.FocusInput) }
                )
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_startup_behavior_silent_title),
                    summary = stringResource(R.string.os_shell_settings_startup_behavior_silent_summary),
                    selected = settings.startupBehavior == OsShellRunnerStartupBehavior.Silent,
                    onSelect = { onStartupBehaviorChange(OsShellRunnerStartupBehavior.Silent) }
                )
            }

            SheetSectionTitle(text = stringResource(R.string.os_shell_settings_exit_cleanup_title))
            SheetActionGroup(verticalSpacing = 8.dp) {
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_exit_cleanup_keep_all_title),
                    summary = stringResource(R.string.os_shell_settings_exit_cleanup_keep_all_summary),
                    selected = settings.exitCleanupMode == OsShellRunnerExitCleanupMode.KeepAll,
                    onSelect = { onExitCleanupModeChange(OsShellRunnerExitCleanupMode.KeepAll) }
                )
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_exit_cleanup_clear_input_title),
                    summary = stringResource(R.string.os_shell_settings_exit_cleanup_clear_input_summary),
                    selected = settings.exitCleanupMode == OsShellRunnerExitCleanupMode.ClearInput,
                    onSelect = { onExitCleanupModeChange(OsShellRunnerExitCleanupMode.ClearInput) }
                )
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_exit_cleanup_clear_output_title),
                    summary = stringResource(R.string.os_shell_settings_exit_cleanup_clear_output_summary),
                    selected = settings.exitCleanupMode == OsShellRunnerExitCleanupMode.ClearOutput,
                    onSelect = { onExitCleanupModeChange(OsShellRunnerExitCleanupMode.ClearOutput) }
                )
            }
        }
    }
}

@Composable
internal fun OsShellOutputSettingsSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    settings: OsShellRunnerSettings,
    onPersistOutputEnabledChange: (Boolean) -> Unit,
    onAutoFormatOutputChange: (Boolean) -> Unit,
    onAutoScrollOutputChange: (Boolean) -> Unit,
    onOutputLimitCharsChange: (Int) -> Unit,
    onOutputSaveModeChange: (OsShellRunnerOutputSaveMode) -> Unit,
    onCopyModeChange: (OsShellRunnerCopyMode) -> Unit,
) {
    var outputLimitExpanded by remember { mutableStateOf(false) }
    var outputLimitAnchorBounds by remember { mutableStateOf<IntRect?>(null) }

    OsShellSettingsBottomSheet(
        show = show,
        title = stringResource(R.string.os_shell_output_settings_sheet_title),
        onDismissRequest = onDismissRequest
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSectionTitle(text = stringResource(R.string.os_shell_output_settings_section_display))
            SheetSectionCard(verticalSpacing = 10.dp) {
                SheetControlRow(
                    label = stringResource(R.string.os_shell_settings_persist_output_label),
                    summary = stringResource(R.string.os_shell_settings_persist_output_summary)
                ) {
                    AppSwitch(
                        checked = settings.persistOutput,
                        onCheckedChange = onPersistOutputEnabledChange
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.os_shell_settings_auto_format_output_label),
                    summary = stringResource(R.string.os_shell_settings_auto_format_output_summary)
                ) {
                    AppSwitch(
                        checked = settings.autoFormatOutput,
                        onCheckedChange = onAutoFormatOutputChange
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.os_shell_settings_auto_scroll_output_label),
                    summary = stringResource(R.string.os_shell_settings_auto_scroll_output_summary)
                ) {
                    AppSwitch(
                        checked = settings.autoScrollOutput,
                        onCheckedChange = onAutoScrollOutputChange
                    )
                }
                SheetControlRow(
                    label = stringResource(R.string.os_shell_settings_output_limit_title),
                    summary = stringResource(R.string.os_shell_settings_output_limit_option_summary)
                ) {
                    OsShellSettingsDropdown(
                        selectedText = stringResource(
                            R.string.os_shell_settings_output_limit_option_kilo,
                            settings.outputLimitChars / 1000
                        ),
                        options = shellRunnerOutputLimitOptionsChars.map { maxChars ->
                            stringResource(
                                R.string.os_shell_settings_output_limit_option_kilo,
                                maxChars / 1000
                            )
                        },
                        selectedIndex = shellRunnerOutputLimitOptionsChars.indexOf(settings.outputLimitChars),
                        expanded = outputLimitExpanded,
                        anchorBounds = outputLimitAnchorBounds,
                        onExpandedChange = { outputLimitExpanded = it },
                        onAnchorBoundsChange = { outputLimitAnchorBounds = it },
                        onSelectedIndexChange = { index ->
                            shellRunnerOutputLimitOptionsChars.getOrNull(index)
                                ?.let(onOutputLimitCharsChange)
                            outputLimitExpanded = false
                        }
                    )
                }
            }

            SheetSectionTitle(text = stringResource(R.string.os_shell_settings_output_save_mode_title))
            SheetActionGroup(verticalSpacing = 8.dp) {
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_output_save_mode_full_title),
                    summary = stringResource(R.string.os_shell_settings_output_save_mode_full_summary),
                    selected = settings.outputSaveMode == OsShellRunnerOutputSaveMode.FullHistory,
                    onSelect = { onOutputSaveModeChange(OsShellRunnerOutputSaveMode.FullHistory) }
                )
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_output_save_mode_latest_title),
                    summary = stringResource(R.string.os_shell_settings_output_save_mode_latest_summary),
                    selected = settings.outputSaveMode == OsShellRunnerOutputSaveMode.LatestOnly,
                    onSelect = { onOutputSaveModeChange(OsShellRunnerOutputSaveMode.LatestOnly) }
                )
            }

            SheetSectionTitle(text = stringResource(R.string.os_shell_settings_copy_mode_title))
            SheetActionGroup(verticalSpacing = 8.dp) {
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_copy_mode_full_title),
                    summary = stringResource(R.string.os_shell_settings_copy_mode_full_summary),
                    selected = settings.copyMode == OsShellRunnerCopyMode.FullHistory,
                    onSelect = { onCopyModeChange(OsShellRunnerCopyMode.FullHistory) }
                )
                OsShellSettingsChoiceCard(
                    title = stringResource(R.string.os_shell_settings_copy_mode_latest_title),
                    summary = stringResource(R.string.os_shell_settings_copy_mode_latest_summary),
                    selected = settings.copyMode == OsShellRunnerCopyMode.LatestResult,
                    onSelect = { onCopyModeChange(OsShellRunnerCopyMode.LatestResult) }
                )
            }
        }
    }
}

@Composable
private fun OsShellSettingsChoiceCard(
    title: String,
    summary: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    SheetChoiceCard(
        title = title,
        summary = summary,
        selected = selected,
        onSelect = onSelect,
        accentColor = Color(0xFF3B82F6),
        selectedLabel = stringResource(R.string.common_selected)
    )
}

@Composable
private fun OsShellSettingsBottomSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        startAction = {
            AppStandaloneLiquidIconButton(
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest
            )
        },
        content = content
    )
}

@Composable
private fun OsShellSettingsDropdown(
    selectedText: String,
    options: List<String>,
    selectedIndex: Int,
    expanded: Boolean,
    anchorBounds: IntRect?,
    onExpandedChange: (Boolean) -> Unit,
    onAnchorBoundsChange: (IntRect?) -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
) {
    AppDropdownSelector(
        selectedText = selectedText,
        options = options,
        selectedIndex = selectedIndex.coerceAtLeast(0),
        expanded = expanded,
        anchorBounds = anchorBounds,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = onSelectedIndexChange,
        onAnchorBoundsChange = onAnchorBoundsChange,
        variant = GlassVariant.SheetAction,
        anchorAlignment = Alignment.CenterEnd,
    )
}
