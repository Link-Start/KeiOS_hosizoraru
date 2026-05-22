@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogTransferSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    mediaSaveCustomEnabled: Boolean,
    mediaSaveFixedTreeUri: String,
    playbackSettingsState: BaGuideCatalogPlaybackSettingsState,
    onMediaSaveCustomEnabledChange: (Boolean) -> Unit,
    onPickMediaSaveLocation: () -> Unit,
    onExportAllFavorites: () -> Unit,
    onImportAllFavorites: () -> Unit,
    onExportStudentFavorites: () -> Unit,
    onImportStudentFavorites: () -> Unit,
    onExportBgmFavorites: () -> Unit,
    onImportBgmFavorites: () -> Unit,
    bgmCacheSummary: String,
    onCacheAllBgm: () -> Unit,
    onCleanInvalidBgmCache: () -> Unit,
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_catalog_transfer_sheet_title),
        onDismissRequest = onDismissRequest,
    ) {
        SheetContentColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalSpacing = 12.dp,
        ) {
            BaGuideCatalogTransferHeroCard()
            BaGuideCatalogTransferRecommendedGroup(
                title = stringResource(R.string.ba_catalog_transfer_all_bundle),
                summary = stringResource(R.string.ba_catalog_transfer_recommended_summary),
                exportText = stringResource(R.string.ba_catalog_transfer_export_all),
                importText = stringResource(R.string.ba_catalog_transfer_import_all),
                accent = MiuixTheme.colorScheme.primary,
                onExport = onExportAllFavorites,
                onImport = onImportAllFavorites,
            )
            SheetSectionTitle(stringResource(R.string.ba_catalog_transfer_settings_section))
            BaGuideCatalogTransferSaveLocationGroup(
                mediaSaveCustomEnabled = mediaSaveCustomEnabled,
                mediaSaveFixedTreeUri = mediaSaveFixedTreeUri,
                onMediaSaveCustomEnabledChange = onMediaSaveCustomEnabledChange,
                onPickMediaSaveLocation = onPickMediaSaveLocation,
            )
            BaGuideCatalogPlaybackSettingsGroup(playbackSettingsState)
            SheetSectionTitle(stringResource(R.string.ba_catalog_transfer_parts_section))
            BaGuideCatalogTransferGroup(
                title = stringResource(R.string.ba_catalog_transfer_student_favorites),
                summary = stringResource(R.string.ba_catalog_transfer_student_favorites_summary),
                exportText = stringResource(R.string.ba_catalog_transfer_export_student_favorites),
                importText = stringResource(R.string.ba_catalog_transfer_import_student_favorites),
                accent = MiuixTheme.colorScheme.primary,
                onExport = onExportStudentFavorites,
                onImport = onImportStudentFavorites,
            )
            BaGuideCatalogTransferGroup(
                title = stringResource(R.string.ba_catalog_transfer_bgm_favorites),
                summary = stringResource(R.string.ba_catalog_transfer_bgm_favorites_summary),
                exportText = stringResource(R.string.ba_catalog_transfer_export_bgm_favorites),
                importText = stringResource(R.string.ba_catalog_transfer_import_bgm_favorites),
                accent = Color(0xFF22C55E),
                onExport = onExportBgmFavorites,
                onImport = onImportBgmFavorites,
            )
            BaGuideCatalogBgmCacheGroup(
                summary = bgmCacheSummary,
                onCacheAll = onCacheAllBgm,
                onCleanInvalid = onCleanInvalidBgmCache,
            )
        }
    }
}
