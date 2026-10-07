package os.kei.ui.page.main.about.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.about.ui.AboutCompactInfoRow
import os.kei.ui.page.main.about.ui.AboutSectionCard
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun AboutAcknowledgementsCardSection(
    cardColor: Color,
    accent: Color,
    subtitleColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenSourceUrl: (String) -> Unit,
) {
    val wikiUrl = stringResource(R.string.about_acknowledgements_wiki_url)
    val modelsUrl = stringResource(R.string.about_acknowledgements_models_url)
    AboutSectionCard(
        cardColor = cardColor,
        title = stringResource(R.string.about_card_acknowledgements_title),
        subtitle = stringResource(R.string.about_card_acknowledgements_subtitle),
        titleColor = accent,
        subtitleColor = subtitleColor,
        sectionIcon = appLucideInfoIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Text(
            text = stringResource(R.string.about_acknowledgements_intro),
            color = subtitleColor,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
        AboutCompactInfoRow(
            title = stringResource(R.string.about_acknowledgements_wiki_role),
            value = stringResource(R.string.about_acknowledgements_wiki_name),
            titleIcon = appLucideExternalLinkIcon(),
            valueColor = accent,
            enableLongPressCopy = false,
            onClick = { onOpenSourceUrl(wikiUrl) },
        )
        AboutCompactInfoRow(
            title = stringResource(R.string.about_acknowledgements_models_role),
            value = stringResource(R.string.about_acknowledgements_models_name),
            titleIcon = appLucideExternalLinkIcon(),
            valueColor = accent,
            enableLongPressCopy = false,
            onClick = { onOpenSourceUrl(modelsUrl) },
        )
    }
}
