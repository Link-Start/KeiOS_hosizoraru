@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideCatalogTransferHeroCard() {
    SheetSummaryCard(
        title = stringResource(R.string.ba_catalog_transfer_hero_title),
        badgeLabel = stringResource(R.string.ba_catalog_transfer_hero_badge),
        accentColor = MiuixTheme.colorScheme.primary,
        badgeContentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(R.string.ba_catalog_transfer_hero_summary),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                label = stringResource(R.string.ba_catalog_transfer_hero_students),
                color = MiuixTheme.colorScheme.primary,
                size = AppStatusPillSize.Compact,
            )
            StatusPill(
                label = stringResource(R.string.ba_catalog_transfer_hero_bgm),
                color = Color(0xFF22C55E),
                size = AppStatusPillSize.Compact,
            )
            StatusPill(
                label = stringResource(R.string.ba_catalog_transfer_hero_settings),
                color = Color(0xFF38BDF8),
                size = AppStatusPillSize.Compact,
            )
        }
    }
}
