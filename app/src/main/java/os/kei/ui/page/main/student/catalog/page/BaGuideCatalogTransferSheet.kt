package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideUploadIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogTransferSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onExportStudentFavorites: () -> Unit,
    onImportStudentFavorites: () -> Unit,
    onExportBgmFavorites: () -> Unit,
    onImportBgmFavorites: () -> Unit
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = stringResource(R.string.ba_catalog_transfer_sheet_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BaGuideCatalogTransferGroup(
                title = stringResource(R.string.ba_catalog_transfer_student_favorites),
                exportText = stringResource(R.string.ba_catalog_transfer_export_student_favorites),
                importText = stringResource(R.string.ba_catalog_transfer_import_student_favorites),
                accent = MiuixTheme.colorScheme.primary,
                onExport = onExportStudentFavorites,
                onImport = onImportStudentFavorites
            )
            BaGuideCatalogTransferGroup(
                title = stringResource(R.string.ba_catalog_transfer_bgm_favorites),
                exportText = stringResource(R.string.ba_catalog_transfer_export_bgm_favorites),
                importText = stringResource(R.string.ba_catalog_transfer_import_bgm_favorites),
                accent = Color(0xFF22C55E),
                onExport = onExportBgmFavorites,
                onImport = onImportBgmFavorites
            )
        }
    }
}

@Composable
private fun BaGuideCatalogTransferGroup(
    title: String,
    exportText: String,
    importText: String,
    accent: Color,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight,
            fontWeight = AppTypographyTokens.CardHeader.fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        AppStandaloneLiquidTextButton(
            text = exportText,
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = appLucideDownloadIcon(),
            textColor = accent,
            iconTint = accent,
            variant = GlassVariant.SheetAction,
            textMaxLines = 1,
            textOverflow = TextOverflow.Ellipsis
        )
        AppStandaloneLiquidTextButton(
            text = importText,
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = appLucideUploadIcon(),
            textColor = accent,
            iconTint = accent,
            variant = GlassVariant.SheetAction,
            textMaxLines = 1,
            textOverflow = TextOverflow.Ellipsis
        )
    }
}
