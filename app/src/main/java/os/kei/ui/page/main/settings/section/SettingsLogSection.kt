@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.section

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.core.log.AppLogLevel
import os.kei.core.log.AppLogStore
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.settings.support.SettingsActionItem
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.formatBytes
import os.kei.ui.page.main.settings.support.formatLogTime
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsLogSection(
    logLevel: AppLogLevel,
    onLogLevelChanged: (AppLogLevel) -> Unit,
    logStats: AppLogStore.Stats,
    exportingLogZip: Boolean,
    clearingLogs: Boolean,
    onExportZipClick: () -> Unit,
    onClearLogsClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    enabledCardColor: Color,
    disabledCardColor: Color,
) {
    val presentation = deriveLogPresentation(logLevel, logStats)
    val logLevels = AppLogLevel.entries
    val selectedLevelIndex = logLevels.indexOf(logLevel).coerceAtLeast(0)
    val levelLabels = appLogLevelLabels()
    var levelExpanded by remember { mutableStateOf(false) }
    var levelAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val logLatestText =
        if (logStats.latestModifiedAtMs <= 0L) {
            stringResource(R.string.settings_log_stat_latest_empty)
        } else {
            formatLogTime(logStats.latestModifiedAtMs)
        }
    SettingsGroupCard(
        header = stringResource(R.string.settings_group_log_header),
        title = stringResource(R.string.settings_group_log_title),
        sectionIcon = appLucideNotesIcon(),
        containerColor = settingsSectionContainerColor(presentation, enabledCardColor, disabledCardColor),
    ) {
        SettingsActionItem(
            title = stringResource(R.string.settings_log_level_title),
            summary = stringResource(R.string.settings_log_level_summary),
            infoKey = stringResource(R.string.common_scope),
            infoValue = stringResource(R.string.settings_log_scope),
            onClick = { levelExpanded = true },
            trailing = {
                AppDropdownSelector(
                    selectedText = levelLabels.getOrElse(selectedLevelIndex) { logLevel.storageId },
                    options = levelLabels,
                    selectedIndex = selectedLevelIndex,
                    expanded = levelExpanded,
                    anchorBounds = levelAnchorBounds,
                    onExpandedChange = { expanded -> levelExpanded = expanded },
                    onSelectedIndexChange = { index ->
                        logLevels.getOrNull(index)?.let(onLogLevelChanged)
                    },
                    onAnchorBoundsChange = { bounds -> levelAnchorBounds = bounds },
                    popupMaxWidth = 220.dp,
                    popupMatchAnchorWidth = true,
                )
            },
        )
        SettingsInfoItem(
            key = stringResource(R.string.common_note),
            value =
                if (logLevel == AppLogLevel.Off) {
                    stringResource(R.string.settings_log_note_disabled)
                } else {
                    stringResource(R.string.settings_log_note_enabled)
                },
        )
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_current_level),
            value = levelLabels.getOrElse(selectedLevelIndex) { logLevel.storageId },
        )
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_size),
            value = formatBytes(logStats.totalBytes),
        )
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_files),
            value = stringResource(R.string.settings_log_stat_files_count, logStats.fileCount),
        )
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_latest),
            value = logLatestText,
        )
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetPrimaryAction,
                    text =
                        if (exportingLogZip) {
                            stringResource(R.string.common_processing)
                        } else {
                            stringResource(R.string.settings_log_action_export_zip)
                        },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.primary,
                    enabled = !exportingLogZip && !clearingLogs,
                    onClick = onExportZipClick,
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetDangerAction,
                    text =
                        if (clearingLogs) {
                            stringResource(R.string.common_processing)
                        } else {
                            stringResource(R.string.settings_log_action_clear)
                        },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.error,
                    enabled = !exportingLogZip && !clearingLogs,
                    onClick = onClearLogsClick,
                )
            },
        )
        AppStandaloneLiquidTextButton(
            variant = GlassVariant.SheetAction,
            text = stringResource(R.string.settings_log_feedback_action),
            modifier = Modifier.fillMaxWidth(),
            buttonModifier = Modifier.fillMaxWidth(),
            textColor = MiuixTheme.colorScheme.primary,
            enabled = !exportingLogZip && !clearingLogs,
            onClick = onFeedbackClick,
        )
    }
}

@Composable
private fun appLogLevelLabels(): List<String> =
    listOf(
        stringResource(R.string.settings_log_level_off),
        stringResource(R.string.settings_log_level_error),
        stringResource(R.string.settings_log_level_warning),
        stringResource(R.string.settings_log_level_info),
        stringResource(R.string.settings_log_level_debug),
    )
