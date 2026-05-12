package os.kei.ui.page.main.jsonimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar
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
    val accent = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
    val statusText = when (state.stage) {
        KeiOSJsonImportStage.Idle -> stringResource(R.string.json_import_stage_idle)
        KeiOSJsonImportStage.Reading -> stringResource(R.string.json_import_stage_reading)
        KeiOSJsonImportStage.Detecting -> stringResource(R.string.json_import_stage_detecting)
        KeiOSJsonImportStage.Parsing -> stringResource(R.string.json_import_stage_parsing)
        KeiOSJsonImportStage.PreviewReady -> stringResource(R.string.json_import_stage_preview_ready)
        KeiOSJsonImportStage.Importing -> stringResource(R.string.json_import_stage_importing)
        KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_stage_done)
        KeiOSJsonImportStage.Failed -> state.errorMessage.ifBlank {
            stringResource(R.string.common_status_failed)
        }
    }
    AppFeatureCard(
        title = statusText,
        subtitle = statusSubtitle(state),
        eyebrow = stringResource(R.string.json_import_status_eyebrow),
        sectionIcon = if (isError) appLucideWarningIcon() else appLucidePackageIcon(),
        titleColor = accent,
        showIndication = false,
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
        state.errorMessage.takeIf { it.isNotBlank() && state.stage != KeiOSJsonImportStage.Failed }
            ?.let {
                Text(
                    text = it,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight
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
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap
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
        preview.stats.forEach { stat ->
            SettingsInfoItem(
                key = stat.label,
                value = stat.value
            )
        }
    }
}

@Composable
private fun JsonImportSamplesCard(samples: List<KeiOSJsonImportSample>) {
    AppFeatureCard(
        title = stringResource(R.string.json_import_samples_title),
        subtitle = stringResource(R.string.json_import_samples_summary, samples.size),
        eyebrow = stringResource(R.string.json_import_samples_eyebrow),
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
    val primaryText = when {
        state.stage == KeiOSJsonImportStage.Done -> stringResource(R.string.common_close)
        state.preview?.readOnly == true -> stringResource(R.string.common_acknowledge)
        state.busy -> stringResource(R.string.common_processing)
        else -> stringResource(R.string.json_import_action_import)
    }
    AppFeatureCard(
        title = stringResource(R.string.json_import_action_title),
        subtitle = actionSummary(state),
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
        AppDualActionRow(
            first = { modifier ->
                AppStandaloneLiquidTextButton(
                    modifier = modifier,
                    text = stringResource(R.string.common_cancel),
                    textColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    containerColor = MiuixTheme.colorScheme.onBackgroundVariant,
                    variant = GlassVariant.Compact,
                    enabled = !state.busy,
                    onClick = onClose
                )
            },
            second = { modifier ->
                AppStandaloneLiquidTextButton(
                    modifier = modifier,
                    text = primaryText,
                    textColor = MiuixTheme.colorScheme.primary,
                    containerColor = MiuixTheme.colorScheme.primary,
                    leadingIcon = appLucideConfirmIcon(),
                    variant = GlassVariant.Compact,
                    enabled = state.canConfirmImport || state.stage == KeiOSJsonImportStage.Done ||
                            state.preview?.readOnly == true,
                    onClick = {
                        if (state.canConfirmImport) {
                            onConfirmImport()
                        } else {
                            onClose()
                        }
                    }
                )
            }
        )
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
