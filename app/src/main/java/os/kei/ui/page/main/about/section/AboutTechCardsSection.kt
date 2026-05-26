package os.kei.ui.page.main.about.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.about.model.AboutInfoIcon
import os.kei.ui.page.main.about.model.AboutInfoRowModel
import os.kei.ui.page.main.about.model.AboutTechDetails
import os.kei.ui.page.main.about.ui.AboutCompactInfoRow
import os.kei.ui.page.main.about.ui.AboutSectionCard
import os.kei.ui.page.main.os.appLucideAlertIcon
import os.kei.ui.page.main.os.appLucideAppWindowIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideFilterIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.appLucideListIcon
import os.kei.ui.page.main.os.appLucideLockIcon
import os.kei.ui.page.main.os.appLucideMediaIcon
import os.kei.ui.page.main.os.appLucideNotesIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideVersionIcon
import os.kei.ui.page.main.os.osLucideSettingsIcon

@Composable
internal fun AboutBuildSdkCardSection(
    rows: List<AboutInfoRowModel>,
    cardColor: Color,
    accent: Color,
    subtitleColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    AboutSectionCard(
        cardColor = cardColor,
        title = stringResource(R.string.about_card_build_title),
        subtitle = stringResource(R.string.about_card_build_subtitle),
        titleColor = accent,
        subtitleColor = subtitleColor,
        sectionIcon = appLucideConfigIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            rows.forEach { row ->
                AboutCompactInfoRow(
                    title = stringResource(row.titleRes),
                    value = row.value,
                    titleIcon = row.icon.aboutInfoIconVector(),
                )
            }
        }
    }
}

@Composable
internal fun AboutUiFrameworkCardSection(
    rows: List<AboutInfoRowModel>,
    cardColor: Color,
    accent: Color,
    subtitleColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    AboutSectionCard(
        cardColor = cardColor,
        title = stringResource(R.string.about_card_ui_title),
        subtitle = stringResource(R.string.about_card_ui_subtitle),
        titleColor = accent,
        subtitleColor = subtitleColor,
        sectionIcon = appLucideAppWindowIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            rows.forEach { row ->
                AboutCompactInfoRow(
                    title = stringResource(row.titleRes),
                    value = row.value,
                    titleIcon = row.icon.aboutInfoIconVector(),
                )
            }
        }
    }
}

@Composable
internal fun AboutNetworkServiceCardSection(
    rows: List<AboutInfoRowModel>,
    cardColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    AboutSectionCard(
        cardColor = cardColor,
        title = stringResource(R.string.about_card_network_title),
        subtitle = stringResource(R.string.about_card_network_subtitle),
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        sectionIcon = osLucideSettingsIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            rows.forEach { row ->
                AboutCompactInfoRow(
                    title = stringResource(row.titleRes),
                    value = row.value,
                    titleIcon = row.icon.aboutInfoIconVector(),
                )
            }
        }
    }
}

@Composable
internal fun AboutGitHubCardSection(
    details: AboutTechDetails,
    cardColor: Color,
    accent: Color,
    subtitleColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenProjectUrl: (String) -> Unit,
) {
    AboutSectionCard(
        cardColor = cardColor,
        title = stringResource(R.string.about_card_github_title),
        subtitle = stringResource(R.string.about_card_github_subtitle),
        titleColor = accent,
        subtitleColor = subtitleColor,
        sectionIcon = appLucideLayersIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            AboutCompactInfoRow(
                title = stringResource(R.string.about_label_project_url),
                value = details.githubProjectUrl,
                titleIcon = appLucideLayersIcon(),
                valueColor = accent,
                onClick = { onOpenProjectUrl(details.githubProjectUrl) },
            )
            details.githubRows.forEach { row ->
                AboutCompactInfoRow(
                    title = stringResource(row.titleRes),
                    value = row.value,
                    titleIcon = row.icon.aboutInfoIconVector(),
                )
            }
        }
    }
}

@Composable
internal fun AboutMediaStorageCardSection(
    rows: List<AboutInfoRowModel>,
    cardColor: Color,
    accent: Color,
    subtitleColor: Color,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    AboutSectionCard(
        cardColor = cardColor,
        title = stringResource(R.string.about_card_media_title),
        subtitle = stringResource(R.string.about_card_media_subtitle),
        titleColor = accent,
        subtitleColor = subtitleColor,
        sectionIcon = appLucideMediaIcon(),
        collapsible = true,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            rows.forEach { row ->
                AboutCompactInfoRow(
                    title = stringResource(row.titleRes),
                    value = row.value,
                    titleIcon = row.icon.aboutInfoIconVector(),
                )
            }
        }
    }
}

@Composable
private fun AboutInfoIcon.aboutInfoIconVector(): ImageVector =
    when (this) {
        AboutInfoIcon.Alert -> appLucideAlertIcon()
        AboutInfoIcon.AppWindow -> appLucideAppWindowIcon()
        AboutInfoIcon.Config -> appLucideConfigIcon()
        AboutInfoIcon.Filter -> appLucideFilterIcon()
        AboutInfoIcon.Info -> appLucideInfoIcon()
        AboutInfoIcon.Layers -> appLucideLayersIcon()
        AboutInfoIcon.List -> appLucideListIcon()
        AboutInfoIcon.Lock -> appLucideLockIcon()
        AboutInfoIcon.Media -> appLucideMediaIcon()
        AboutInfoIcon.Notes -> appLucideNotesIcon()
        AboutInfoIcon.Refresh -> appLucideRefreshIcon()
        AboutInfoIcon.Settings -> osLucideSettingsIcon()
        AboutInfoIcon.Version -> appLucideVersionIcon()
    }
