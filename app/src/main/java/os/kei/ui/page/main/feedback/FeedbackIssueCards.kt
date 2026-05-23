@file:Suppress("FunctionName")

package os.kei.ui.page.main.feedback

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideUploadIcon
import os.kei.ui.page.main.os.appLucideWarningIcon
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.formatBytes
import os.kei.ui.page.main.settings.support.formatLogTime
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FeedbackStatusCard(
    state: FeedbackIssueUiState,
    onRefresh: () -> Unit,
) {
    val statusText =
        when {
            state.loading -> stringResource(R.string.common_loading)
            state.errorMessage.isNotBlank() -> state.errorMessage
            state.statusMessage.isNotBlank() -> state.statusMessage
            else -> stringResource(R.string.feedback_issue_status_ready)
        }
    val statusColor =
        if (state.errorMessage.isNotBlank()) {
            MiuixTheme.colorScheme.error
        } else {
            MiuixTheme.colorScheme.primary
        }
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_status_title),
        subtitle = statusText,
        eyebrow = stringResource(R.string.feedback_issue_header),
        sectionIcon = appLucideWarningIcon(),
        titleColor = statusColor,
        containerColor = feedbackCardContainerColor(),
        showIndication = false,
        headerEndActions = {
            AppStandaloneLiquidTextButton(
                text = stringResource(R.string.common_refresh),
                onClick = onRefresh,
                enabled = !state.loading,
                textColor = MiuixTheme.colorScheme.primary,
                containerColor = MiuixTheme.colorScheme.primary,
                leadingIcon = appLucideRefreshIcon(),
                variant = GlassVariant.Compact,
            )
        },
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap,
    ) {
        Text(
            text = stringResource(R.string.feedback_issue_status_summary),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
    }
}

@Composable
internal fun FeedbackDeviceInfoCard(deviceInfo: FeedbackDeviceInfo) {
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_device_title),
        subtitle = stringResource(R.string.feedback_issue_device_summary),
        containerColor = feedbackCardContainerColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.compactSectionGap,
    ) {
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_keios),
            value = deviceInfo.appVersionLine,
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_android),
            value = deviceInfo.androidLine,
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_model),
            value = deviceInfo.deviceLine,
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_abi),
            value = deviceInfo.abis,
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_install_source),
            value = deviceInfo.installSource,
        )
    }
}

@Composable
internal fun FeedbackLogCard(
    state: FeedbackIssueUiState,
    onExportZip: () -> Unit,
    onClearLogs: () -> Unit,
) {
    val latestText =
        if (state.logStats.latestModifiedAtMs > 0L) {
            formatLogTime(state.logStats.latestModifiedAtMs)
        } else {
            stringResource(R.string.settings_log_stat_latest_empty)
        }
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_logs_title),
        subtitle = stringResource(R.string.feedback_issue_logs_summary),
        containerColor = feedbackCardContainerColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.compactSectionGap,
    ) {
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_size),
            value = formatBytes(state.logStats.totalBytes),
        )
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_files),
            value = stringResource(R.string.settings_log_stat_files_count, state.logStats.fileCount),
        )
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_latest),
            value = latestText,
        )
        if (state.lastExportedFileName.isNotBlank()) {
            SettingsInfoItem(
                key = stringResource(R.string.feedback_issue_logs_last_export),
                value = state.lastExportedFileName,
            )
        }
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    text =
                        if (state.exportingZip) {
                            stringResource(R.string.common_processing)
                        } else {
                            stringResource(R.string.settings_log_action_export_zip)
                        },
                    onClick = onExportZip,
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    enabled = !state.exportingZip && !state.clearingLogs,
                    textColor = MiuixTheme.colorScheme.primary,
                    containerColor = MiuixTheme.colorScheme.primary,
                    leadingIcon = appLucideUploadIcon(),
                    variant = GlassVariant.SheetPrimaryAction,
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    text =
                        if (state.clearingLogs) {
                            stringResource(R.string.common_processing)
                        } else {
                            stringResource(R.string.settings_log_action_clear)
                        },
                    onClick = onClearLogs,
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    enabled = !state.exportingZip && !state.clearingLogs,
                    textColor = MiuixTheme.colorScheme.error,
                    containerColor = MiuixTheme.colorScheme.error,
                    variant = GlassVariant.SheetDangerAction,
                )
            },
        )
    }
}

@Composable
internal fun FeedbackDraftCard(
    title: String,
    body: String,
    loading: Boolean,
    submitting: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
) {
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_draft_title),
        subtitle = stringResource(R.string.feedback_issue_draft_summary),
        containerColor = feedbackDraftCardContainerColor(),
        subtitleColor = feedbackSecondaryTextColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap,
    ) {
        FeedbackFieldLabel(text = stringResource(R.string.feedback_issue_title_placeholder))
        FeedbackLiquidTextField(
            value = title,
            onValueChange = onTitleChange,
            label = stringResource(R.string.feedback_issue_title_placeholder),
            enabled = !loading && !submitting,
            minHeight = 48.dp,
            singleLine = true,
        )
        FeedbackFieldLabel(text = stringResource(R.string.feedback_issue_body_placeholder))
        FeedbackLiquidTextField(
            value = body,
            onValueChange = onBodyChange,
            label = stringResource(R.string.feedback_issue_body_placeholder),
            enabled = !loading && !submitting,
            minHeight = 360.dp,
        )
    }
}

@Composable
internal fun FeedbackSubmitCard(
    state: FeedbackIssueUiState,
    onRequestSubmit: (FeedbackSubmitMode) -> Unit,
) {
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_submit_title),
        subtitle = stringResource(R.string.feedback_issue_submit_summary),
        sectionIcon = appLucideExternalLinkIcon(),
        containerColor = feedbackCardContainerColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap,
    ) {
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_submit_browser),
            value = stringResource(R.string.feedback_issue_submit_browser_value),
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_submit_api),
            value =
                if (state.apiTokenAvailable) {
                    stringResource(R.string.feedback_issue_submit_api_ready)
                } else {
                    stringResource(R.string.feedback_issue_submit_api_missing)
                },
        )
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    text = stringResource(R.string.feedback_issue_action_browser),
                    onClick = { onRequestSubmit(FeedbackSubmitMode.Browser) },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && !state.submittingIssue,
                    textColor = MiuixTheme.colorScheme.primary,
                    containerColor = MiuixTheme.colorScheme.primary,
                    variant = GlassVariant.SheetPrimaryAction,
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    text =
                        if (state.submittingIssue) {
                            stringResource(R.string.common_processing)
                        } else {
                            stringResource(R.string.feedback_issue_action_api)
                        },
                    onClick = { onRequestSubmit(FeedbackSubmitMode.GitHubApi) },
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && !state.submittingIssue,
                    textColor = MiuixTheme.colorScheme.primary,
                    containerColor = MiuixTheme.colorScheme.primary,
                    variant = GlassVariant.SheetAction,
                )
            },
        )
    }
}
