package os.kei.ui.page.main.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideUploadIcon
import os.kei.ui.page.main.os.appLucideWarningIcon
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.formatBytes
import os.kei.ui.page.main.settings.support.formatLogTime
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun FeedbackIssuePage(
    state: FeedbackIssueUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onExportZip: () -> Unit,
    onClearLogs: () -> Unit,
    onRequestSubmit: (FeedbackSubmitMode) -> Unit,
    onDismissSubmit: () -> Unit,
    onConfirmBrowserSubmit: () -> Unit,
    onConfirmApiSubmit: () -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)

    KeiOSActivityRootBackHandler(
        needsInterception = state.pendingSubmitMode != null || state.submittingIssue,
        onBack = {
            if (state.pendingSubmitMode != null) {
                onDismissSubmit()
            } else {
                onClose()
            }
        }
    )

    AppPageScaffold(
        title = stringResource(R.string.feedback_issue_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        reserveTopEndActionSpace = false,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onClose,
                backdrop = pageBackdrop
            )
        }
    ) { innerPadding ->
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .layerBackdrop(pageBackdrop),
            sectionSpacing = 10.dp
        ) {
            item {
                FeedbackStatusCard(
                    state = state,
                    onRefresh = onRefresh
                )
            }
            item {
                FeedbackDraftCard(
                    title = state.title,
                    body = state.body,
                    loading = state.loading,
                    submitting = state.submittingIssue,
                    onTitleChange = onTitleChange,
                    onBodyChange = onBodyChange
                )
            }
            item {
                FeedbackLogCard(
                    state = state,
                    onExportZip = onExportZip,
                    onClearLogs = onClearLogs
                )
            }
            item {
                FeedbackDeviceInfoCard(deviceInfo = state.deviceInfo)
            }
            item {
                FeedbackSubmitCard(
                    state = state,
                    onRequestSubmit = onRequestSubmit
                )
            }
        }
    }

    FeedbackSubmitConfirmDialog(
        mode = state.pendingSubmitMode,
        apiTokenAvailable = state.apiTokenAvailable,
        submitting = state.submittingIssue,
        onDismiss = onDismissSubmit,
        onConfirmBrowser = onConfirmBrowserSubmit,
        onConfirmApi = onConfirmApiSubmit
    )
}

@Composable
private fun FeedbackStatusCard(
    state: FeedbackIssueUiState,
    onRefresh: () -> Unit
) {
    val statusText = when {
        state.loading -> stringResource(R.string.common_loading)
        state.errorMessage.isNotBlank() -> state.errorMessage
        state.statusMessage.isNotBlank() -> state.statusMessage
        else -> stringResource(R.string.feedback_issue_status_ready)
    }
    val statusColor = if (state.errorMessage.isNotBlank()) {
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
                variant = GlassVariant.Compact
            )
        },
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap
    ) {
        Text(
            text = stringResource(R.string.feedback_issue_status_summary),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight
        )
    }
}

@Composable
private fun FeedbackDeviceInfoCard(deviceInfo: FeedbackDeviceInfo) {
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_device_title),
        subtitle = stringResource(R.string.feedback_issue_device_summary),
        containerColor = feedbackCardContainerColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.compactSectionGap
    ) {
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_keios),
            value = deviceInfo.appVersionLine
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_android),
            value = deviceInfo.androidLine
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_model),
            value = deviceInfo.deviceLine
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_abi),
            value = deviceInfo.abis
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_device_install_source),
            value = deviceInfo.installSource
        )
    }
}

@Composable
private fun FeedbackLogCard(
    state: FeedbackIssueUiState,
    onExportZip: () -> Unit,
    onClearLogs: () -> Unit
) {
    val latestText = if (state.logStats.latestModifiedAtMs > 0L) {
        formatLogTime(state.logStats.latestModifiedAtMs)
    } else {
        stringResource(R.string.settings_log_stat_latest_empty)
    }
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_logs_title),
        subtitle = stringResource(R.string.feedback_issue_logs_summary),
        containerColor = feedbackCardContainerColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.compactSectionGap
    ) {
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_size),
            value = formatBytes(state.logStats.totalBytes)
        )
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_files),
            value = stringResource(R.string.settings_log_stat_files_count, state.logStats.fileCount)
        )
        SettingsInfoItem(
            key = stringResource(R.string.settings_log_stat_latest),
            value = latestText
        )
        if (state.lastExportedFileName.isNotBlank()) {
            SettingsInfoItem(
                key = stringResource(R.string.feedback_issue_logs_last_export),
                value = state.lastExportedFileName
            )
        }
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    text = if (state.exportingZip) stringResource(R.string.common_processing) else stringResource(
                        R.string.settings_log_action_export_zip
                    ),
                    onClick = onExportZip,
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    enabled = !state.exportingZip && !state.clearingLogs,
                    textColor = MiuixTheme.colorScheme.primary,
                    containerColor = MiuixTheme.colorScheme.primary,
                    leadingIcon = appLucideUploadIcon(),
                    variant = GlassVariant.SheetPrimaryAction
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    text = if (state.clearingLogs) stringResource(R.string.common_processing) else stringResource(
                        R.string.settings_log_action_clear
                    ),
                    onClick = onClearLogs,
                    modifier = modifier,
                    buttonModifier = Modifier.fillMaxWidth(),
                    enabled = !state.exportingZip && !state.clearingLogs,
                    textColor = MiuixTheme.colorScheme.error,
                    containerColor = MiuixTheme.colorScheme.error,
                    variant = GlassVariant.SheetDangerAction
                )
            }
        )
    }
}

@Composable
private fun FeedbackDraftCard(
    title: String,
    body: String,
    loading: Boolean,
    submitting: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit
) {
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_draft_title),
        subtitle = stringResource(R.string.feedback_issue_draft_summary),
        containerColor = feedbackDraftCardContainerColor(),
        subtitleColor = feedbackSecondaryTextColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap
    ) {
        FeedbackFieldLabel(text = stringResource(R.string.feedback_issue_title_placeholder))
        FeedbackLiquidTextField(
            value = title,
            onValueChange = onTitleChange,
            label = stringResource(R.string.feedback_issue_title_placeholder),
            enabled = !loading && !submitting,
            minHeight = 48.dp,
            singleLine = true
        )
        FeedbackFieldLabel(text = stringResource(R.string.feedback_issue_body_placeholder))
        FeedbackLiquidTextField(
            value = body,
            onValueChange = onBodyChange,
            label = stringResource(R.string.feedback_issue_body_placeholder),
            enabled = !loading && !submitting,
            minHeight = 360.dp
        )
    }
}

@Composable
private fun FeedbackSubmitCard(
    state: FeedbackIssueUiState,
    onRequestSubmit: (FeedbackSubmitMode) -> Unit
) {
    AppFeatureCard(
        title = stringResource(R.string.feedback_issue_submit_title),
        subtitle = stringResource(R.string.feedback_issue_submit_summary),
        sectionIcon = appLucideExternalLinkIcon(),
        containerColor = feedbackCardContainerColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap
    ) {
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_submit_browser),
            value = stringResource(R.string.feedback_issue_submit_browser_value)
        )
        SettingsInfoItem(
            key = stringResource(R.string.feedback_issue_submit_api),
            value = if (state.apiTokenAvailable) {
                stringResource(R.string.feedback_issue_submit_api_ready)
            } else {
                stringResource(R.string.feedback_issue_submit_api_missing)
            }
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
                    variant = GlassVariant.SheetPrimaryAction
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    text = if (state.submittingIssue) {
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
                    variant = GlassVariant.SheetAction
                )
            }
        )
    }
}

@Composable
private fun FeedbackLiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    minHeight: Dp,
    singleLine: Boolean = false
) {
    val isBody = minHeight > 120.dp
    val textStyle = TextStyle(
        color = MiuixTheme.colorScheme.onBackground,
        fontSize = if (isBody) 14.sp else AppTypographyTokens.Body.fontSize,
        lineHeight = if (isBody) 20.sp else AppTypographyTokens.Body.lineHeight,
        textAlign = TextAlign.Start,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
    val placeholderStyle = textStyle.copy(color = feedbackSecondaryTextColor())
    FeedbackLiquidPanel(
        minHeight = minHeight,
        fixedHeight = isBody
    ) {
        val fieldHeight = minHeight - 24.dp
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isBody) {
                        Modifier.height(fieldHeight)
                    } else {
                        Modifier.heightIn(min = fieldHeight)
                    }
                ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopStart
                ) {
                    if (value.isBlank()) {
                        BasicText(
                            text = label,
                            style = placeholderStyle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        innerTextField()
                    }
                }
            }
        )
    }
}

@Composable
private fun FeedbackFieldLabel(text: String) {
    Text(
        text = text,
        color = feedbackSecondaryTextColor(),
        fontSize = AppTypographyTokens.Caption.fontSize,
        lineHeight = AppTypographyTokens.Caption.lineHeight,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun FeedbackLiquidPanel(
    minHeight: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    fixedHeight: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedRectangle(18.dp)
    val borderColor = if (isDark) {
        Color(0xFF8ABEFF).copy(alpha = 0.24f)
    } else {
        Color(0xFFB5D7FF).copy(alpha = 0.82f)
    }
    val panelColor = if (isDark) {
        Color(0xFF121A24).copy(alpha = 0.78f)
    } else {
        Color.White.copy(alpha = 0.88f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (fixedHeight) {
                    Modifier.height(minHeight)
                } else {
                    Modifier.heightIn(min = minHeight)
                }
            )
            .background(panelColor, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
private fun feedbackCardContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
}

@Composable
private fun feedbackDraftCardContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        Color(0xFF101824).copy(alpha = 0.72f)
    } else {
        Color(0xFFF8FBFF).copy(alpha = 0.88f)
    }
}

@Composable
private fun feedbackSecondaryTextColor(): Color {
    return if (isSystemInDarkTheme()) {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.84f)
    } else {
        Color(0xFF65718A).copy(alpha = 0.94f)
    }
}

@Composable
private fun FeedbackSubmitConfirmDialog(
    mode: FeedbackSubmitMode?,
    apiTokenAvailable: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirmBrowser: () -> Unit,
    onConfirmApi: () -> Unit
) {
    val confirmText = when (mode) {
        FeedbackSubmitMode.Browser -> stringResource(R.string.feedback_issue_confirm_browser)
        FeedbackSubmitMode.GitHubApi -> stringResource(R.string.feedback_issue_confirm_api)
        null -> ""
    }
    val summary = when (mode) {
        FeedbackSubmitMode.Browser -> stringResource(R.string.feedback_issue_confirm_browser_summary)
        FeedbackSubmitMode.GitHubApi -> if (apiTokenAvailable) {
            stringResource(R.string.feedback_issue_confirm_api_summary)
        } else {
            stringResource(R.string.feedback_issue_confirm_api_missing_token)
        }

        null -> ""
    }
    WindowDialog(
        show = mode != null,
        title = stringResource(R.string.feedback_issue_confirm_title),
        summary = summary,
        onDismissRequest = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FeedbackConfirmChecklist()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = confirmText,
                    enabled = !submitting,
                    containerColor = MiuixTheme.colorScheme.primary,
                    variant = GlassVariant.SheetPrimaryAction,
                    onClick = {
                        when (mode) {
                            FeedbackSubmitMode.Browser -> onConfirmBrowser()
                            FeedbackSubmitMode.GitHubApi -> onConfirmApi()
                            null -> Unit
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedbackConfirmChecklist() {
    val items = listOf(
        stringResource(R.string.feedback_issue_confirm_check_public),
        stringResource(R.string.feedback_issue_confirm_check_sensitive),
        stringResource(R.string.feedback_issue_confirm_check_steps),
        stringResource(R.string.feedback_issue_confirm_check_zip),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = appLucideWarningIcon(),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = item,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight
                )
            }
        }
    }
}
