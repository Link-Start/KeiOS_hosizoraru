package os.kei.ui.page.main.jsonimport

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideWarningIcon
import os.kei.ui.page.main.settings.support.SettingsInfoItem
import os.kei.ui.page.main.settings.support.formatBytes
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppDualActionRow
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppOverviewMetricTile
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun KeiOSJsonImportPage(
    state: KeiOSJsonImportUiState,
    onConfirmImport: () -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)

    KeiOSActivityRootBackHandler(
        needsInterception = state.busy,
        onBack = onClose
    )

    AppPageScaffold(
        title = stringResource(R.string.json_import_title),
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
                JsonImportStatusCard(state)
            }
            item {
                JsonImportSourceCard(state)
            }
            state.preview?.let { preview ->
                item {
                    JsonImportPreviewCard(preview)
                }
                if (preview.samples.isNotEmpty()) {
                    item {
                        JsonImportSamplesCard(preview.samples)
                    }
                }
            }
            item {
                JsonImportActionCard(
                    state = state,
                    onConfirmImport = onConfirmImport,
                    onClose = onClose
                )
            }
        }
    }
}

@Composable
private fun JsonImportStatusCard(state: KeiOSJsonImportUiState) {
    val isError = state.stage == KeiOSJsonImportStage.Failed
    val accent = jsonImportStageColor(state.stage)
    val statusText = jsonImportStageTitle(state)
    AppFeatureCard(
        title = statusText,
        subtitle = statusSubtitle(state),
        eyebrow = stringResource(R.string.json_import_status_eyebrow),
        sectionIcon = if (isError) appLucideWarningIcon() else appLucidePackageIcon(),
        titleColor = accent,
        subtitleColor = jsonImportSecondaryTextColor(),
        containerColor = jsonImportHeroCardContainerColor(accent),
        showIndication = false,
        headerEndActions = {
            StatusPill(
                label = jsonImportStagePillLabel(state),
                color = accent,
                size = AppStatusPillSize.Compact
            )
        },
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap,
        contentPadding = PaddingValues(
            start = CardLayoutRhythm.cardHorizontalPadding,
            end = CardLayoutRhythm.cardHorizontalPadding,
            bottom = CardLayoutRhythm.cardVerticalPadding
        )
    ) {
        if (state.busy) {
            LiquidLinearProgressBar(
                progress = { stageProgress(state.stage) },
                activeColor = accent,
                contentDescription = statusText
            )
        }
        state.errorMessage.takeIf { it.isNotBlank() }
            ?.let {
                Text(
                    text = it,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
    }
}

@Composable
private fun JsonImportSourceCard(state: KeiOSJsonImportUiState) {
    AppFeatureCard(
        title = stringResource(R.string.json_import_source_title),
        subtitle = state.sourceName.ifBlank { stringResource(R.string.json_import_source_unknown) },
        eyebrow = stringResource(R.string.json_import_source_eyebrow),
        sectionIcon = appLucideInfoIcon(),
        containerColor = jsonImportCardContainerColor(),
        subtitleColor = jsonImportSecondaryTextColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap
    ) {
        SettingsInfoItem(
            key = stringResource(R.string.json_import_source_file_name),
            value = state.sourceName
        )
        SettingsInfoItem(
            key = stringResource(R.string.json_import_source_size),
            value = state.sourceSizeBytes.takeIf { it >= 0L }?.let(::formatBytes).orEmpty()
        )
        SettingsInfoItem(
            key = stringResource(R.string.json_import_source_limit),
            value = formatBytes(KEIOS_JSON_IMPORT_MAX_BYTES)
        )
    }
}

@Composable
private fun JsonImportPreviewCard(preview: KeiOSJsonImportPreview) {
    val title = stringResource(preview.kind.titleRes)
    val subtitle = when {
        preview.readOnly -> stringResource(R.string.json_import_read_only_summary)
        preview.highVersion -> stringResource(R.string.json_import_high_version_summary)
        preview.legacyFormat -> stringResource(R.string.json_import_legacy_summary)
        else -> stringResource(R.string.json_import_preview_summary)
    }
    AppFeatureCard(
        title = title,
        subtitle = subtitle,
        eyebrow = stringResource(R.string.json_import_preview_eyebrow),
        sectionIcon = appLucidePackageIcon(),
        containerColor = jsonImportCardContainerColor(),
        subtitleColor = jsonImportSecondaryTextColor(),
        showIndication = false,
        headerEndActions = {
            JsonImportPreviewPills(preview)
        },
        contentVerticalSpacing = CardLayoutRhythm.compactSectionGap
    ) {
        if (preview.marker.isNotBlank()) {
            SettingsInfoItem(
                key = stringResource(R.string.json_import_marker),
                value = preview.marker
            )
        }
        if (preview.version > 0) {
            SettingsInfoItem(
                key = stringResource(R.string.json_import_version),
                value = preview.version.toString()
            )
        }
        JsonImportMetricRows(preview)
    }
}

@Composable
private fun JsonImportSamplesCard(samples: List<KeiOSJsonImportSample>) {
    AppFeatureCard(
        title = stringResource(R.string.json_import_samples_title),
        subtitle = stringResource(R.string.json_import_samples_summary, samples.size),
        eyebrow = stringResource(R.string.json_import_samples_eyebrow),
        containerColor = jsonImportCardContainerColor(),
        subtitleColor = jsonImportSecondaryTextColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap
    ) {
        samples.forEach { sample ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = sample.title.ifBlank { stringResource(R.string.common_unknown) },
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                sample.subtitle.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = AppTypographyTokens.Supporting.fontSize,
                        lineHeight = AppTypographyTokens.Supporting.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun JsonImportActionCard(
    state: KeiOSJsonImportUiState,
    onConfirmImport: () -> Unit,
    onClose: () -> Unit
) {
    AppFeatureCard(
        title = stringResource(R.string.json_import_action_title),
        subtitle = actionSummary(state),
        containerColor = jsonImportCardContainerColor(),
        subtitleColor = jsonImportSecondaryTextColor(),
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap
    ) {
        state.applyResult?.let { result ->
            SettingsInfoItem(
                key = stringResource(R.string.json_import_stat_new),
                value = result.addedCount.toString()
            )
            SettingsInfoItem(
                key = stringResource(R.string.json_import_stat_updated),
                value = result.updatedCount.toString()
            )
        }
        JsonImportActionButtons(
            state = state,
            onConfirmImport = onConfirmImport,
            onClose = onClose
        )
    }
}

@Composable
private fun JsonImportPreviewPills(preview: KeiOSJsonImportPreview) {
    if (preview.readOnly) {
        StatusPill(
            label = stringResource(R.string.json_import_mode_read_only),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            size = AppStatusPillSize.Compact
        )
    } else if (preview.highVersion || preview.legacyFormat) {
        StatusPill(
            label = stringResource(R.string.common_status_cached),
            color = AppStatusColors.Cached,
            size = AppStatusPillSize.Compact
        )
    } else if (preview.canImport) {
        StatusPill(
            label = stringResource(R.string.common_available),
            color = AppStatusColors.Fresh,
            size = AppStatusPillSize.Compact
        )
    }
}

@Composable
private fun JsonImportMetricRows(preview: KeiOSJsonImportPreview) {
    val rows = preview.stats.chunked(3)
    rows.forEach { rowStats ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowStats.forEach { stat ->
                AppOverviewMetricTile(
                    label = stat.label,
                    value = stat.value,
                    modifier = Modifier.weight(1f),
                    valueColor = if (stat.emphasized) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    },
                    labelColor = jsonImportSecondaryTextColor(),
                    valueMaxLines = 2
                )
            }
            repeat(3 - rowStats.size) {
                Column(modifier = Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun JsonImportActionButtons(
    state: KeiOSJsonImportUiState,
    onConfirmImport: () -> Unit,
    onClose: () -> Unit
) {
    when {
        state.canConfirmImport -> AppDualActionRow(
            first = { modifier ->
                JsonImportNeutralButton(
                    modifier = modifier,
                    text = stringResource(R.string.common_cancel),
                    onClick = onClose
                )
            },
            second = { modifier ->
                JsonImportPrimaryButton(
                    modifier = modifier,
                    text = stringResource(R.string.json_import_action_import),
                    enabled = true,
                    onClick = onConfirmImport
                )
            }
        )

        state.busy -> AppDualActionRow(
            first = { modifier ->
                JsonImportNeutralButton(
                    modifier = modifier,
                    text = stringResource(R.string.common_cancel),
                    enabled = false,
                    onClick = onClose
                )
            },
            second = { modifier ->
                JsonImportPrimaryButton(
                    modifier = modifier,
                    text = stringResource(R.string.common_processing),
                    enabled = false,
                    onClick = {}
                )
            }
        )

        else -> {
            val text = when {
                state.preview?.readOnly == true -> stringResource(R.string.common_acknowledge)
                else -> stringResource(R.string.common_close)
            }
            JsonImportNeutralButton(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                leadingIcon = appLucideCloseIcon(),
                onClick = onClose
            )
        }
    }
}

@Composable
private fun JsonImportPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppLiquidTextButton(
        backdrop = null,
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        textColor = MiuixTheme.colorScheme.primary,
        containerColor = MiuixTheme.colorScheme.primary,
        leadingIcon = appLucideConfirmIcon(),
        variant = GlassVariant.SheetPrimaryAction,
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun JsonImportNeutralButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    AppLiquidTextButton(
        backdrop = null,
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        textColor = MiuixTheme.colorScheme.onBackgroundVariant,
        containerColor = null,
        leadingIcon = leadingIcon,
        iconTint = MiuixTheme.colorScheme.onBackgroundVariant,
        variant = GlassVariant.Content,
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun jsonImportStageTitle(state: KeiOSJsonImportUiState): String {
    return when (state.stage) {
        KeiOSJsonImportStage.Idle -> stringResource(R.string.json_import_stage_idle)
        KeiOSJsonImportStage.Reading -> stringResource(R.string.json_import_stage_reading)
        KeiOSJsonImportStage.Detecting -> stringResource(R.string.json_import_stage_detecting)
        KeiOSJsonImportStage.Parsing -> stringResource(R.string.json_import_stage_parsing)
        KeiOSJsonImportStage.PreviewReady -> stringResource(R.string.json_import_stage_preview_ready)
        KeiOSJsonImportStage.Importing -> stringResource(R.string.json_import_stage_importing)
        KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_stage_done)
        KeiOSJsonImportStage.Failed -> stringResource(R.string.common_status_failed)
    }
}

@Composable
private fun jsonImportStagePillLabel(state: KeiOSJsonImportUiState): String {
    return when (state.stage) {
        KeiOSJsonImportStage.Failed -> stringResource(R.string.common_status_failed)
        KeiOSJsonImportStage.PreviewReady -> stringResource(R.string.common_available)
        KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_stage_done)
        KeiOSJsonImportStage.Idle -> stringResource(R.string.common_not_loaded)
        else -> stringResource(R.string.common_processing)
    }
}

@Composable
private fun jsonImportStageColor(stage: KeiOSJsonImportStage): Color {
    return when (stage) {
        KeiOSJsonImportStage.Failed -> AppStatusColors.Failed
        KeiOSJsonImportStage.Done -> AppStatusColors.Fresh
        KeiOSJsonImportStage.PreviewReady -> MiuixTheme.colorScheme.primary
        KeiOSJsonImportStage.Idle -> MiuixTheme.colorScheme.onBackgroundVariant
        else -> AppStatusColors.Refreshing
    }
}

@Composable
private fun jsonImportHeroCardContainerColor(accent: Color): Color {
    return if (isSystemInDarkTheme()) {
        accent.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.78f)
    }
}

@Composable
private fun jsonImportCardContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    } else {
        Color.White.copy(alpha = 0.76f)
    }
}

@Composable
private fun jsonImportSecondaryTextColor(): Color {
    return if (isSystemInDarkTheme()) {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.86f)
    } else {
        Color(0xFF64748B).copy(alpha = 0.96f)
    }
}

@Composable
private fun statusSubtitle(state: KeiOSJsonImportUiState): String {
    return when (state.stage) {
        KeiOSJsonImportStage.Reading -> stringResource(R.string.json_import_stage_reading_summary)
        KeiOSJsonImportStage.Detecting -> stringResource(R.string.json_import_stage_detecting_summary)
        KeiOSJsonImportStage.Parsing -> stringResource(R.string.json_import_stage_parsing_summary)
        KeiOSJsonImportStage.PreviewReady -> stringResource(R.string.json_import_stage_preview_ready_summary)
        KeiOSJsonImportStage.Importing -> stringResource(R.string.json_import_stage_importing_summary)
        KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_stage_done_summary)
        KeiOSJsonImportStage.Failed -> stringResource(R.string.json_import_stage_failed_summary)
        else -> stringResource(R.string.json_import_stage_idle_summary)
    }
}

@Composable
private fun actionSummary(state: KeiOSJsonImportUiState): String {
    return when {
        state.stage == KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_action_done_summary)
        state.preview?.readOnly == true -> stringResource(R.string.json_import_action_read_only_summary)
        state.preview?.canImport == true -> stringResource(R.string.json_import_action_import_summary)
        state.stage == KeiOSJsonImportStage.Failed -> stringResource(R.string.json_import_action_failed_summary)
        else -> stringResource(R.string.json_import_action_waiting_summary)
    }
}

private fun stageProgress(stage: KeiOSJsonImportStage): Float {
    return when (stage) {
        KeiOSJsonImportStage.Reading -> 0.18f
        KeiOSJsonImportStage.Detecting -> 0.38f
        KeiOSJsonImportStage.Parsing -> 0.62f
        KeiOSJsonImportStage.Importing -> 0.82f
        KeiOSJsonImportStage.Done,
        KeiOSJsonImportStage.PreviewReady -> 1f

        else -> 0f
    }
}
