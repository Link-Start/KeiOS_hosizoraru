package os.kei.ui.page.main.about.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.about.ui.AboutCompactInfoRow
import os.kei.ui.page.main.about.ui.AboutSectionCard
import os.kei.ui.page.main.os.appLucideAppWindowIcon
import os.kei.ui.page.main.os.appLucideFlaskIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.appLucideMediaIcon

@Composable
fun AboutComponentLabCardSection(
    cardColor: Color,
    accent: Color,
    subtitleColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenComponentLab: () -> Unit
) {
    AboutSectionCard(
        cardColor = cardColor,
        title = stringResource(R.string.about_card_component_lab_title),
        subtitle = stringResource(R.string.about_card_component_lab_subtitle),
        titleColor = accent,
        subtitleColor = subtitleColor,
        sectionIcon = appLucideFlaskIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        AboutCompactInfoRow(
            title = stringResource(R.string.about_component_lab_row_entry),
            value = stringResource(R.string.debug_component_lab_title),
            titleIcon = appLucideFlaskIcon(),
            valueColor = accent,
            onClick = onOpenComponentLab
        )
        AboutCompactInfoRow(
            title = stringResource(R.string.about_component_lab_row_scope),
            value = stringResource(R.string.debug_component_lab_liquid_entry_note),
            titleIcon = appLucideLayersIcon()
        )
        AboutCompactInfoRow(
            title = stringResource(R.string.about_component_lab_row_activity),
            value = stringResource(R.string.debug_component_lab_liquid_row_entry_value),
            titleIcon = appLucideAppWindowIcon()
        )
        AboutCompactInfoRow(
            title = stringResource(R.string.about_component_lab_row_components),
            value = stringResource(R.string.debug_component_lab_liquid_row_components_value),
            titleIcon = appLucideMediaIcon()
        )
    }
}
