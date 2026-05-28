@file:Suppress("FunctionName")

package os.kei.ui.page.main.about.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow.Companion.Clip
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.about.ui.AboutSectionCard
import os.kei.ui.page.main.os.appLucideAppWindowIcon
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideGridIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideVersionIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutReleaseCardSection(
    cardColor: Color,
    accent: Color,
    subtitleColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    AboutSectionCard(
        cardColor = cardColor,
        title = stringResource(R.string.about_card_release_title),
        subtitle = stringResource(R.string.about_card_release_subtitle),
        titleColor = accent,
        subtitleColor = subtitleColor,
        sectionIcon = appLucideVersionIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.sectionGap)) {
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_version),
                value = stringResource(R.string.about_release_value_version),
                icon = appLucideVersionIcon(),
            )
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_focus),
                value = stringResource(R.string.about_release_value_focus),
                icon = appLucideInfoIcon(),
            )
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_github),
                value = stringResource(R.string.about_release_value_github),
                icon = appLucideBranchIcon(),
            )
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_ba_guide),
                value = stringResource(R.string.about_release_value_ba_guide),
                icon = appLucideGridIcon(),
            )
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_navigation),
                value = stringResource(R.string.about_release_value_navigation),
                icon = appLucideAppWindowIcon(),
            )
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_icon),
                value = stringResource(R.string.about_release_value_icon),
                icon = appLucidePackageIcon(),
            )
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_release_gate),
                value = stringResource(R.string.about_release_value_release_gate),
                icon = appLucideConfirmIcon(),
            )
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_performance),
                value = stringResource(R.string.about_release_value_performance),
                icon = appLucideLayersIcon(),
            )
            AboutReleaseHighlightBlock(
                title = stringResource(R.string.about_release_row_next),
                value = stringResource(R.string.about_release_value_next),
                icon = appLucideLayersIcon(),
            )
        }
    }
}

@Composable
private fun AboutReleaseHighlightBlock(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.infoRowGap),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.86f),
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = Clip,
            )
            Text(
                text = value,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                textAlign = TextAlign.Start,
                maxLines = Int.MAX_VALUE,
                overflow = Clip,
            )
        }
    }
}
