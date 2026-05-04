package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.student.GuideBgmFavoriteImportPreview
import os.kei.ui.page.main.widget.core.AppOverviewMetricTile
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class BaGuideCatalogImportKind {
    All,
    Student,
    Bgm
}

internal data class BaGuideCatalogImportPreviewState(
    val kind: BaGuideCatalogImportKind,
    val raw: String,
    val studentPreview: CatalogFavoritesImportPreview,
    val bgmPreview: GuideBgmFavoriteImportPreview
) {
    val hasImportableData: Boolean
        get() = studentPreview.importedCount > 0 || bgmPreview.importedCount > 0
}

@Composable
internal fun BaGuideCatalogImportPreviewSheet(
    state: BaGuideCatalogImportPreviewState?,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    SnapshotWindowBottomSheet(
        show = state != null,
        title = stringResource(R.string.ba_catalog_import_preview_title),
        onDismissRequest = onDismissRequest
    ) {
        val preview = state ?: return@SnapshotWindowBottomSheet
        val accent = MiuixTheme.colorScheme.primary
        SheetContentColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalSpacing = 12.dp
        ) {
            SheetSectionTitle(stringResource(preview.kind.titleRes))
            SheetSectionCard(verticalSpacing = 12.dp) {
                Text(
                    text = stringResource(
                        if (preview.hasImportableData) {
                            R.string.ba_catalog_import_preview_summary
                        } else {
                            R.string.ba_catalog_import_preview_empty
                        }
                    ),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight
                )
                BaGuideCatalogImportMetricRow(
                    studentPreview = preview.studentPreview,
                    bgmPreview = preview.bgmPreview,
                    accent = accent
                )
                AppStandaloneLiquidTextButton(
                    text = stringResource(R.string.ba_catalog_import_preview_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = preview.hasImportableData,
                    textColor = accent.copy(alpha = if (preview.hasImportableData) 1f else 0.42f),
                    iconTint = accent.copy(alpha = if (preview.hasImportableData) 1f else 0.42f),
                    variant = GlassVariant.SheetAction,
                    textMaxLines = 1,
                    textOverflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BaGuideCatalogImportMetricRow(
    studentPreview: CatalogFavoritesImportPreview,
    bgmPreview: GuideBgmFavoriteImportPreview,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppOverviewMetricTile(
            label = stringResource(R.string.ba_catalog_import_preview_student_count),
            value = studentPreview.importedCount.coerceAtLeast(0).toString(),
            modifier = Modifier.weight(1f),
            valueColor = accent
        )
        AppOverviewMetricTile(
            label = stringResource(R.string.ba_catalog_import_preview_bgm_added),
            value = bgmPreview.addedCount.coerceAtLeast(0).toString(),
            modifier = Modifier.weight(1f),
            valueColor = Color(0xFF22C55E)
        )
        AppOverviewMetricTile(
            label = stringResource(R.string.ba_catalog_import_preview_bgm_updated),
            value = bgmPreview.updatedCount.coerceAtLeast(0).toString(),
            modifier = Modifier.weight(1f),
            valueColor = Color(0xFF38BDF8)
        )
    }
}

private val BaGuideCatalogImportKind.titleRes: Int
    get() = when (this) {
        BaGuideCatalogImportKind.All -> R.string.ba_catalog_import_preview_all
        BaGuideCatalogImportKind.Student -> R.string.ba_catalog_import_preview_student
        BaGuideCatalogImportKind.Bgm -> R.string.ba_catalog_import_preview_bgm
    }
