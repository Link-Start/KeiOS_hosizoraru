@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideTrashIcon
import os.kei.ui.page.main.os.appLucideUploadIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogBgmCacheGroup(
    summary: String,
    onCacheAll: () -> Unit,
    onCleanInvalid: () -> Unit,
) {
    val accent = Color(0xFF38BDF8)
    SheetSummaryCard(
        title = stringResource(R.string.ba_catalog_bgm_cache_manage_title),
        badgeLabel = stringResource(R.string.ba_catalog_transfer_cache_badge),
        accentColor = accent,
        verticalSpacing = 10.dp,
    ) {
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        BaGuideCatalogTransferActionRow(
            exportText = stringResource(R.string.ba_catalog_bgm_action_cache_all),
            importText = stringResource(R.string.ba_catalog_bgm_cache_clean_invalid),
            accent = accent,
            exportIcon = appLucideDownloadIcon(),
            importIcon = appLucideTrashIcon(),
            onExport = onCacheAll,
            onImport = onCleanInvalid,
        )
    }
}

@Composable
internal fun BaGuideCatalogTransferRecommendedGroup(
    title: String,
    summary: String,
    exportText: String,
    importText: String,
    accent: Color,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SheetSummaryCard(
        title = title,
        badgeLabel = stringResource(R.string.ba_catalog_transfer_recommended),
        accentColor = accent,
        verticalSpacing = 10.dp,
    ) {
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        BaGuideCatalogTransferActionRow(
            exportText = exportText,
            importText = importText,
            accent = accent,
            exportIcon = appLucidePackageIcon(),
            importIcon = appLucideUploadIcon(),
            onExport = onExport,
            onImport = onImport,
        )
    }
}

@Composable
internal fun BaGuideCatalogTransferGroup(
    title: String,
    summary: String,
    exportText: String,
    importText: String,
    accent: Color,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SheetSectionCard(verticalSpacing = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon =
                if (accent == Color(0xFF22C55E)) {
                    appLucideMusicIcon()
                } else {
                    appLucideHeartIcon()
                }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    color = accent,
                    fontSize = AppTypographyTokens.CardHeader.fontSize,
                    lineHeight = AppTypographyTokens.CardHeader.lineHeight,
                    fontWeight = AppTypographyTokens.CardHeader.fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BaGuideCatalogTransferActionRow(
            exportText = exportText,
            importText = importText,
            accent = accent,
            exportIcon = appLucideDownloadIcon(),
            importIcon = appLucideUploadIcon(),
            onExport = onExport,
            onImport = onImport,
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
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
            textOverflow = TextOverflow.Ellipsis,
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
            textOverflow = TextOverflow.Ellipsis,
        )
    }
}
