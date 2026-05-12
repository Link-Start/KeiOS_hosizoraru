package os.kei.ui.page.main.settings.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.core.telemetry.FirebaseTelemetry
import os.kei.core.telemetry.FirebaseTelemetryRecord
import os.kei.core.telemetry.FirebaseTelemetryRecordKind
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.settings.state.SettingsTelemetryUiState
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.SettingsToggleItem
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsTelemetrySection(
    basicStatsEnabled: Boolean,
    onBasicStatsChanged: (Boolean) -> Unit,
    errorLogsEnabled: Boolean,
    onErrorLogsChanged: (Boolean) -> Unit,
    telemetryState: SettingsTelemetryUiState,
    onSendUnsentErrors: () -> Unit,
    onDeleteUnsentErrors: () -> Unit,
    onClearRecords: () -> Unit,
    enabledCardColor: Color,
    disabledCardColor: Color
) {
    val telemetryActive =
        basicStatsEnabled || errorLogsEnabled || telemetryState.recentRecords.isNotEmpty()
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    SettingsGroupCard(
        header = stringResource(R.string.settings_telemetry_header),
        title = stringResource(R.string.settings_telemetry_title),
        sectionIcon = appLucideInfoIcon(),
        containerColor = if (telemetryActive) enabledCardColor else disabledCardColor
    ) {
        Text(
            text = stringResource(R.string.settings_telemetry_summary),
            color = subtitleColor,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_telemetry_basic_title),
            summary = if (basicStatsEnabled) {
                stringResource(R.string.settings_telemetry_basic_summary_enabled)
            } else {
                stringResource(R.string.settings_telemetry_basic_summary_disabled)
            },
            checked = basicStatsEnabled,
            onCheckedChange = onBasicStatsChanged
        )
        SettingsToggleItem(
            title = stringResource(R.string.settings_telemetry_errors_title),
            summary = if (errorLogsEnabled) {
                stringResource(R.string.settings_telemetry_errors_summary_enabled)
            } else {
                stringResource(R.string.settings_telemetry_errors_summary_disabled)
            },
            checked = errorLogsEnabled,
            onCheckedChange = onErrorLogsChanged
        )
        TelemetryInfoBlock(
            title = stringResource(R.string.settings_telemetry_basic_uploads),
            detail = stringResource(R.string.settings_telemetry_basic_fields),
            titleColor = subtitleColor
        )
        TelemetryInfoBlock(
            title = stringResource(R.string.settings_telemetry_error_uploads),
            detail = stringResource(R.string.settings_telemetry_error_fields),
            titleColor = subtitleColor
        )
        TelemetryInfoBlock(
            title = stringResource(R.string.settings_telemetry_not_uploads),
            detail = stringResource(R.string.settings_telemetry_excluded_fields),
            titleColor = subtitleColor
        )
        TelemetryInfoBlock(
            title = stringResource(R.string.settings_telemetry_sdk_mode),
            detail = stringResource(R.string.settings_telemetry_sdk_mode_value),
            titleColor = subtitleColor
        )
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetPrimaryAction,
                    text = if (telemetryState.processingCrashReports) {
                        stringResource(R.string.common_processing)
                    } else {
                        stringResource(R.string.settings_telemetry_action_send_errors)
                    },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.primary,
                    enabled = errorLogsEnabled && !telemetryState.processingCrashReports,
                    onClick = onSendUnsentErrors
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    variant = GlassVariant.SheetDangerAction,
                    text = if (telemetryState.processingCrashReports) {
                        stringResource(R.string.common_processing)
                    } else {
                        stringResource(R.string.settings_telemetry_action_delete_errors)
                    },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    textColor = MiuixTheme.colorScheme.error,
                    enabled = !telemetryState.processingCrashReports,
                    onClick = onDeleteUnsentErrors
                )
            }
        )
        TelemetryRecentRecords(
            records = telemetryState.recentRecords,
            subtitleColor = subtitleColor,
            onClearRecords = onClearRecords
        )
    }
}

@Composable
private fun TelemetryRecentRecords(
    records: List<FirebaseTelemetryRecord>,
    subtitleColor: Color,
    onClearRecords: () -> Unit
) {
    Spacer(modifier = Modifier.height(2.dp))
    SettingsInfoItem(
        key = stringResource(R.string.settings_telemetry_recent_title),
        value = if (records.isEmpty()) {
            stringResource(R.string.settings_telemetry_recent_empty)
        } else {
            stringResource(R.string.settings_telemetry_recent_count, records.size)
        }
    )
    TelemetryInfoBlock(
        title = stringResource(R.string.settings_telemetry_recent_scope_title),
        detail = stringResource(R.string.settings_telemetry_recent_scope_value),
        titleColor = subtitleColor
    )
    if (records.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        records.take(5).forEach { record ->
            val recordTitle = telemetryRecordTitle(record)
            val recordDetail = telemetryRecordDetail(record)
            val deviceLine = telemetryRecordDeviceLine(record)
            val fieldLine = telemetryRecordFields(record)
            Text(
                text = buildString {
                    val time = FirebaseTelemetry.formatRecordTime(record.timestampMs)
                    if (time.isNotBlank()) {
                        append(time)
                        append(" · ")
                    }
                    append(recordTitle)
                    if (recordDetail.isNotBlank()) {
                        append(" · ")
                        append(recordDetail)
                    }
                    if (deviceLine.isNotBlank()) {
                        append("\n")
                        append(deviceLine)
                    }
                    if (fieldLine.isNotBlank()) {
                        append("\n")
                        append(fieldLine)
                    }
                },
                color = subtitleColor,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight
            )
        }
        AppStandaloneLiquidTextButton(
            variant = GlassVariant.Compact,
            text = stringResource(R.string.settings_telemetry_action_clear_recent),
            textColor = MiuixTheme.colorScheme.primary,
            onClick = onClearRecords
        )
    }
}

@Composable
private fun TelemetryInfoBlock(
    title: String,
    detail: String,
    titleColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight
        )
        Text(
            text = detail,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight
        )
    }
}

@Composable
private fun telemetryRecordTitle(record: FirebaseTelemetryRecord): String {
    return when (record.kind) {
        FirebaseTelemetryRecordKind.BasicStats -> stringResource(R.string.settings_telemetry_record_title_basic)
        FirebaseTelemetryRecordKind.ErrorLog -> stringResource(R.string.settings_telemetry_record_title_error)
    }
}

@Composable
private fun telemetryRecordDetail(record: FirebaseTelemetryRecord): String {
    if (record.detail == FirebaseTelemetry.PREVIOUS_CRASH_DETAIL) {
        return stringResource(R.string.settings_telemetry_record_previous_crash)
    }
    if (record.detail.isNotBlank()) return record.detail
    return when (record.kind) {
        FirebaseTelemetryRecordKind.BasicStats -> stringResource(R.string.settings_telemetry_record_basic_detail)
        FirebaseTelemetryRecordKind.ErrorLog -> ""
    }
}

@Composable
private fun telemetryRecordDeviceLine(record: FirebaseTelemetryRecord): String {
    val appVersion = record.appVersion.takeIf { it.isNotBlank() } ?: return ""
    val androidSdk = record.androidSdk.takeIf { it > 0 } ?: return ""
    val manufacturer = record.manufacturer.takeIf { it.isNotBlank() } ?: return ""
    val model = record.model.takeIf { it.isNotBlank() } ?: return ""
    return stringResource(
        R.string.settings_telemetry_record_device_line,
        appVersion,
        androidSdk,
        manufacturer,
        model
    )
}

@Composable
private fun telemetryRecordFields(record: FirebaseTelemetryRecord): String {
    if (record.fields.isNotEmpty()) {
        return record.fields.joinToString(" / ")
    }
    return when (record.kind) {
        FirebaseTelemetryRecordKind.BasicStats -> stringResource(R.string.settings_telemetry_basic_fields)
        FirebaseTelemetryRecordKind.ErrorLog -> stringResource(R.string.settings_telemetry_error_fields)
    }
}
