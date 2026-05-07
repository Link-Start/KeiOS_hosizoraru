package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideUploadIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
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
    onCleanInvalidBgmCache: () -> Unit
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_catalog_transfer_sheet_title),
        onDismissRequest = onDismissRequest
    ) {
        SheetContentColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalSpacing = 12.dp
        ) {
            BaGuideCatalogTransferRecommendedGroup(
                title = stringResource(R.string.ba_catalog_transfer_recommended),
                summary = stringResource(R.string.ba_catalog_transfer_recommended_summary),
                exportText = stringResource(R.string.ba_catalog_transfer_export_all),
                importText = stringResource(R.string.ba_catalog_transfer_import_all),
                accent = MiuixTheme.colorScheme.primary,
                onExport = onExportAllFavorites,
                onImport = onImportAllFavorites
            )
            BaGuideCatalogTransferSaveLocationGroup(
                mediaSaveCustomEnabled = mediaSaveCustomEnabled,
                mediaSaveFixedTreeUri = mediaSaveFixedTreeUri,
                onMediaSaveCustomEnabledChange = onMediaSaveCustomEnabledChange,
                onPickMediaSaveLocation = onPickMediaSaveLocation
            )
            BaGuideCatalogPlaybackSettingsGroup(playbackSettingsState)
            BaGuideCatalogTransferGroup(
                title = stringResource(R.string.ba_catalog_transfer_student_favorites),
                summary = stringResource(R.string.ba_catalog_transfer_student_favorites_summary),
                exportText = stringResource(R.string.ba_catalog_transfer_export_student_favorites),
                importText = stringResource(R.string.ba_catalog_transfer_import_student_favorites),
                accent = MiuixTheme.colorScheme.primary,
                onExport = onExportStudentFavorites,
                onImport = onImportStudentFavorites
            )
            BaGuideCatalogTransferGroup(
                title = stringResource(R.string.ba_catalog_transfer_bgm_favorites),
                summary = stringResource(R.string.ba_catalog_transfer_bgm_favorites_summary),
                exportText = stringResource(R.string.ba_catalog_transfer_export_bgm_favorites),
                importText = stringResource(R.string.ba_catalog_transfer_import_bgm_favorites),
                accent = Color(0xFF22C55E),
                onExport = onExportBgmFavorites,
                onImport = onImportBgmFavorites
            )
            BaGuideCatalogBgmCacheGroup(
                summary = bgmCacheSummary,
                onCacheAll = onCacheAllBgm,
                onCleanInvalid = onCleanInvalidBgmCache
            )
        }
    }
}

internal data class BaGuideCatalogPlaybackSettingsState(
    val nativeBgmMediaNotificationEnabled: Boolean,
    val notificationPermissionGranted: Boolean,
    val onNativeBgmMediaNotificationChange: (Boolean) -> Unit
)

@Composable
private fun BaGuideCatalogPlaybackSettingsGroup(
    state: BaGuideCatalogPlaybackSettingsState
) {
    SheetSectionTitle(stringResource(R.string.ba_catalog_playback_settings_title))
    SheetSectionCard(verticalSpacing = 10.dp) {
        SheetControlRow(
            label = stringResource(R.string.ba_catalog_bgm_native_media_notification),
            summary = if (state.nativeBgmMediaNotificationEnabled && !state.notificationPermissionGranted) {
                stringResource(R.string.ba_catalog_bgm_native_media_notification_permission_summary)
            } else {
                stringResource(R.string.ba_catalog_bgm_native_media_notification_summary)
            }
        ) {
            AppSwitch(
                checked = state.nativeBgmMediaNotificationEnabled,
                onCheckedChange = state.onNativeBgmMediaNotificationChange
            )
        }
    }
}

@Composable
private fun BaGuideCatalogBgmCacheGroup(
    summary: String,
    onCacheAll: () -> Unit,
    onCleanInvalid: () -> Unit
) {
    val accent = Color(0xFF38BDF8)
    SheetSectionTitle(stringResource(R.string.ba_catalog_bgm_cache_manage_title))
    SheetSectionCard(verticalSpacing = 10.dp) {
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        BaGuideCatalogTransferActionRow(
            exportText = stringResource(R.string.ba_catalog_bgm_action_cache_all),
            importText = stringResource(R.string.ba_catalog_bgm_cache_clean_invalid),
            accent = accent,
            exportIcon = appLucideDownloadIcon(),
            importIcon = appLucideUploadIcon(),
            onExport = onCacheAll,
            onImport = onCleanInvalid
        )
    }
}

@Composable
private fun BaGuideCatalogTransferSaveLocationGroup(
    mediaSaveCustomEnabled: Boolean,
    mediaSaveFixedTreeUri: String,
    onMediaSaveCustomEnabledChange: (Boolean) -> Unit,
    onPickMediaSaveLocation: () -> Unit
) {
    SheetSectionTitle(stringResource(R.string.ba_catalog_transfer_save_location))
    SheetSectionCard(verticalSpacing = 10.dp) {
        SheetControlRow(
            label = stringResource(R.string.ba_catalog_transfer_save_location_fixed),
            summary = when {
                !mediaSaveCustomEnabled -> stringResource(R.string.ba_catalog_transfer_save_location_saf)
                mediaSaveFixedTreeUri.isBlank() -> stringResource(R.string.ba_catalog_transfer_save_location_fixed_empty)
                else -> stringResource(R.string.ba_catalog_transfer_save_location_fixed_ready)
            }
        ) {
            AppSwitch(
                checked = mediaSaveCustomEnabled,
                onCheckedChange = onMediaSaveCustomEnabledChange
            )
        }
        if (mediaSaveCustomEnabled) {
            AppStandaloneLiquidTextButton(
                text = stringResource(R.string.ba_settings_action_pick_media_save_location),
                onClick = onPickMediaSaveLocation,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = appLucideUploadIcon(),
                textColor = MiuixTheme.colorScheme.primary,
                iconTint = MiuixTheme.colorScheme.primary,
                variant = GlassVariant.SheetAction,
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BaGuideCatalogTransferRecommendedGroup(
    title: String,
    summary: String,
    exportText: String,
    importText: String,
    accent: Color,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    SheetSectionTitle(title)
    SheetSectionCard(verticalSpacing = 10.dp) {
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        BaGuideCatalogTransferActionRow(
            exportText = exportText,
            importText = importText,
            accent = accent,
            exportIcon = appLucidePackageIcon(),
            importIcon = appLucideUploadIcon(),
            onExport = onExport,
            onImport = onImport
        )
    }
}

@Composable
private fun BaGuideCatalogTransferGroup(
    title: String,
    summary: String,
    exportText: String,
    importText: String,
    accent: Color,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    SheetSectionTitle(title)
    SheetSectionCard(verticalSpacing = 10.dp) {
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        BaGuideCatalogTransferActionRow(
            exportText = exportText,
            importText = importText,
            accent = accent,
            exportIcon = appLucideDownloadIcon(),
            importIcon = appLucideUploadIcon(),
            onExport = onExport,
            onImport = onImport
        )
    }
}

@Composable
private fun BaGuideCatalogTransferActionRow(
    exportText: String,
    importText: String,
    accent: Color,
    exportIcon: ImageVector,
    importIcon: ImageVector,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppStandaloneLiquidTextButton(
            text = exportText,
            onClick = onExport,
            modifier = Modifier.weight(1f),
            leadingIcon = exportIcon,
            textColor = accent,
            iconTint = accent,
            variant = GlassVariant.SheetAction,
            textMaxLines = 1,
            textOverflow = TextOverflow.Ellipsis
        )
        AppStandaloneLiquidTextButton(
            text = importText,
            onClick = onImport,
            modifier = Modifier.weight(1f),
            leadingIcon = importIcon,
            textColor = accent,
            iconTint = accent,
            variant = GlassVariant.SheetAction,
            textMaxLines = 1,
            textOverflow = TextOverflow.Ellipsis
        )
    }
}
