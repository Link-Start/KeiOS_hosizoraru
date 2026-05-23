package os.kei.ui.page.main.jsonimport

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class JsonImportResultAction(
    val kind: KeiOSJsonImportKind,
    val labelRes: Int,
)

internal fun KeiOSJsonImportPreview.jsonImportResultAction(): JsonImportResultAction? {
    val labelRes =
        when (kind) {
            KeiOSJsonImportKind.GitHubTracked -> R.string.json_import_action_view_github

            KeiOSJsonImportKind.OsActivityCards,
            KeiOSJsonImportKind.OsShellCards,
            KeiOSJsonImportKind.OsCardsBundle,
            KeiOSJsonImportKind.OsInfoCard,
            -> R.string.json_import_action_view_os

            KeiOSJsonImportKind.BaCatalogFavorites,
            KeiOSJsonImportKind.BaBgmFavorites,
            KeiOSJsonImportKind.BaAllFavorites,
            -> R.string.json_import_action_view_ba

            KeiOSJsonImportKind.McpLogs -> R.string.json_import_action_view_mcp

            KeiOSJsonImportKind.Unknown -> return null
        }
    return JsonImportResultAction(kind, labelRes)
}

@Composable
internal fun jsonImportStageTitle(state: KeiOSJsonImportUiState): String =
    when (state.stage) {
        KeiOSJsonImportStage.Idle -> stringResource(R.string.json_import_stage_idle)
        KeiOSJsonImportStage.Reading -> stringResource(R.string.json_import_stage_reading)
        KeiOSJsonImportStage.Detecting -> stringResource(R.string.json_import_stage_detecting)
        KeiOSJsonImportStage.Parsing -> stringResource(R.string.json_import_stage_parsing)
        KeiOSJsonImportStage.PreviewReady -> stringResource(R.string.json_import_stage_preview_ready)
        KeiOSJsonImportStage.Importing -> stringResource(R.string.json_import_stage_importing)
        KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_stage_done)
        KeiOSJsonImportStage.Failed -> stringResource(R.string.common_status_failed)
    }

@Composable
internal fun jsonImportStagePillLabel(state: KeiOSJsonImportUiState): String =
    when (state.stage) {
        KeiOSJsonImportStage.Failed -> stringResource(R.string.common_status_failed)
        KeiOSJsonImportStage.PreviewReady -> stringResource(R.string.common_available)
        KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_stage_done)
        KeiOSJsonImportStage.Idle -> stringResource(R.string.common_not_loaded)
        else -> stringResource(R.string.common_processing)
    }

@Composable
internal fun jsonImportStageColor(stage: KeiOSJsonImportStage): Color =
    when (stage) {
        KeiOSJsonImportStage.Failed -> AppStatusColors.Failed
        KeiOSJsonImportStage.Done -> AppStatusColors.Fresh
        KeiOSJsonImportStage.PreviewReady -> MiuixTheme.colorScheme.primary
        KeiOSJsonImportStage.Idle -> MiuixTheme.colorScheme.onBackgroundVariant
        else -> AppStatusColors.Refreshing
    }

@Composable
internal fun jsonImportHeroCardContainerColor(accent: Color): Color =
    if (isSystemInDarkTheme()) {
        accent.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.78f)
    }

@Composable
internal fun jsonImportCardContainerColor(): Color =
    if (isSystemInDarkTheme()) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    } else {
        Color.White.copy(alpha = 0.76f)
    }

@Composable
internal fun jsonImportSecondaryTextColor(): Color =
    if (isSystemInDarkTheme()) {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.86f)
    } else {
        Color(0xFF64748B).copy(alpha = 0.96f)
    }

@Composable
internal fun statusSubtitle(state: KeiOSJsonImportUiState): String =
    when (state.stage) {
        KeiOSJsonImportStage.Reading -> stringResource(R.string.json_import_stage_reading_summary)
        KeiOSJsonImportStage.Detecting -> stringResource(R.string.json_import_stage_detecting_summary)
        KeiOSJsonImportStage.Parsing -> stringResource(R.string.json_import_stage_parsing_summary)
        KeiOSJsonImportStage.PreviewReady -> stringResource(R.string.json_import_stage_preview_ready_summary)
        KeiOSJsonImportStage.Importing -> stringResource(R.string.json_import_stage_importing_summary)
        KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_stage_done_summary)
        KeiOSJsonImportStage.Failed -> stringResource(R.string.json_import_stage_failed_summary)
        else -> stringResource(R.string.json_import_stage_idle_summary)
    }

@Composable
internal fun actionSummary(state: KeiOSJsonImportUiState): String =
    when {
        state.stage == KeiOSJsonImportStage.Done -> stringResource(R.string.json_import_action_done_summary)
        state.preview?.readOnly == true -> stringResource(R.string.json_import_action_read_only_summary)
        state.preview?.canImport == true -> stringResource(R.string.json_import_action_import_summary)
        state.stage == KeiOSJsonImportStage.Failed -> stringResource(R.string.json_import_action_failed_summary)
        else -> stringResource(R.string.json_import_action_waiting_summary)
    }

internal fun stageProgress(stage: KeiOSJsonImportStage): Float =
    when (stage) {
        KeiOSJsonImportStage.Reading -> 0.18f

        KeiOSJsonImportStage.Detecting -> 0.38f

        KeiOSJsonImportStage.Parsing -> 0.62f

        KeiOSJsonImportStage.Importing -> 0.82f

        KeiOSJsonImportStage.Done,
        KeiOSJsonImportStage.PreviewReady,
        -> 1f

        else -> 0f
    }
