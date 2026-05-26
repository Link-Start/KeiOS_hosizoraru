@file:Suppress("FunctionName")

package os.kei.ui.page.main.about.page

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import os.kei.ui.page.main.about.model.AboutAppDetails
import os.kei.ui.page.main.about.model.AboutComponentEntry
import os.kei.ui.page.main.about.model.AboutPermissionEntry
import os.kei.ui.page.main.about.model.AboutTechDetails
import os.kei.ui.page.main.about.section.AboutAppCardSection
import os.kei.ui.page.main.about.section.AboutBuildSdkCardSection
import os.kei.ui.page.main.about.section.AboutComponentCardSection
import os.kei.ui.page.main.about.section.AboutComponentLabCardSection
import os.kei.ui.page.main.about.section.AboutGitHubCardSection
import os.kei.ui.page.main.about.section.AboutLicenseCardSection
import os.kei.ui.page.main.about.section.AboutMediaStorageCardSection
import os.kei.ui.page.main.about.section.AboutNetworkServiceCardSection
import os.kei.ui.page.main.about.section.AboutPermissionCardSection
import os.kei.ui.page.main.about.section.AboutProjectLicenseCardSection
import os.kei.ui.page.main.about.section.AboutReleaseCardSection
import os.kei.ui.page.main.about.section.AboutRuntimeStatusCardSection
import os.kei.ui.page.main.about.section.AboutUiFrameworkCardSection
import os.kei.ui.page.main.about.state.AboutPageColorPalette
import os.kei.ui.page.main.about.state.AboutPageSectionExpansionState

internal data class AboutCardRenderState(
    val appDetails: AboutAppDetails,
    val palette: AboutPageColorPalette,
    val searchActive: Boolean,
    val expansionState: AboutPageSectionExpansionState,
    val shizukuReady: Boolean,
    val notificationPermissionGranted: Boolean,
    val shizukuDetailMap: Map<String, String>,
    val permissionEntries: List<AboutPermissionEntry>,
    val componentEntries: List<AboutComponentEntry>,
    val techDetails: AboutTechDetails,
)

internal data class AboutCardActions(
    val onExpandedChange: (AboutSearchCard, Boolean) -> Unit,
    val onCheckShizuku: () -> Unit,
    val onOpenExternalUrl: (String) -> Unit,
    val onOpenComponentLab: () -> Unit,
)

internal fun LazyListScope.aboutCategoryCards(
    category: AboutCategory,
    matchingCards: Set<AboutSearchCard>,
    state: AboutCardRenderState,
    actions: AboutCardActions,
) {
    aboutCardsForCategory(category)
        .filter { card -> !state.searchActive || card in matchingCards }
        .forEach { card ->
            aboutCardItem(
                card = card,
                state = state,
                actions = actions,
            )
        }
}

internal fun LazyListScope.aboutCardItem(
    card: AboutSearchCard,
    state: AboutCardRenderState,
    actions: AboutCardActions,
) {
    item(
        key = "about_card_${card.name}",
        contentType = "about_card",
    ) {
        val palette = state.palette
        when (card) {
            AboutSearchCard.App -> {
                AboutAppCardSection(
                    details = state.appDetails,
                    cardColor = palette.infoCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.App),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.App, it) },
                )
            }

            AboutSearchCard.GitHub -> {
                AboutGitHubCardSection(
                    details = state.techDetails,
                    cardColor = palette.githubCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.GitHub),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.GitHub, it) },
                    onOpenProjectUrl = actions.onOpenExternalUrl,
                )
            }

            AboutSearchCard.Release -> {
                AboutReleaseCardSection(
                    cardColor = palette.releaseCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Release),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Release, it) },
                )
            }

            AboutSearchCard.Runtime -> {
                AboutRuntimeStatusCardSection(
                    cardColor = palette.runtimeCardColor,
                    accent = palette.accent,
                    shizukuReady = state.shizukuReady,
                    readyColor = palette.readyColor,
                    notReadyColor = palette.notReadyColor,
                    subtitleColor = palette.subtitleColor,
                    notificationPermissionGranted = state.notificationPermissionGranted,
                    shizukuDetailMap = state.shizukuDetailMap,
                    permissionCount = state.permissionEntries.size,
                    componentCount = state.componentEntries.size,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Runtime),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Runtime, it) },
                    onCheckShizuku = actions.onCheckShizuku,
                )
            }

            AboutSearchCard.Network -> {
                AboutNetworkServiceCardSection(
                    rows = state.techDetails.networkRows,
                    cardColor = palette.networkServiceCardColor,
                    titleColor = palette.readyColor,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Network),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Network, it) },
                )
            }

            AboutSearchCard.Media -> {
                AboutMediaStorageCardSection(
                    rows = state.techDetails.mediaRows,
                    cardColor = palette.mediaStorageCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Media),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Media, it) },
                )
            }

            AboutSearchCard.Permission -> {
                AboutPermissionCardSection(
                    cardColor = palette.githubCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    readyColor = palette.readyColor,
                    notReadyColor = palette.notReadyColor,
                    entries = state.permissionEntries,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Permission),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Permission, it) },
                )
            }

            AboutSearchCard.Component -> {
                AboutComponentCardSection(
                    cardColor = Color(0x2234D399),
                    titleColor = palette.readyColor,
                    subtitleColor = palette.subtitleColor,
                    accent = palette.accent,
                    entries = state.componentEntries,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Component),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Component, it) },
                )
            }

            AboutSearchCard.Build -> {
                AboutBuildSdkCardSection(
                    rows = state.techDetails.buildRows,
                    cardColor = palette.buildCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Build),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Build, it) },
                )
            }

            AboutSearchCard.Ui -> {
                AboutUiFrameworkCardSection(
                    rows = state.techDetails.uiRows,
                    cardColor = palette.uiFrameworkCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Ui),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Ui, it) },
                )
            }

            AboutSearchCard.ProjectLicense -> {
                AboutProjectLicenseCardSection(
                    cardColor = palette.projectLicenseCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded =
                        aboutCardExpanded(
                            state.searchActive,
                            state.expansionState,
                            AboutSearchCard.ProjectLicense,
                        ),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.ProjectLicense, it) },
                    onOpenLicenseUrl = actions.onOpenExternalUrl,
                )
            }

            AboutSearchCard.License -> {
                AboutLicenseCardSection(
                    cardColor = palette.licenseCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.License),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.License, it) },
                    onOpenSourceUrl = actions.onOpenExternalUrl,
                )
            }

            AboutSearchCard.Lab -> {
                AboutComponentLabCardSection(
                    cardColor = palette.componentLabCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = aboutCardExpanded(state.searchActive, state.expansionState, AboutSearchCard.Lab),
                    onExpandedChange = { actions.onExpandedChange(AboutSearchCard.Lab, it) },
                    onOpenComponentLab = actions.onOpenComponentLab,
                )
            }
        }
    }
}

private val AboutOverviewCards = listOf(AboutSearchCard.App, AboutSearchCard.Release, AboutSearchCard.GitHub)
private val AboutSystemCards =
    listOf(
        AboutSearchCard.Runtime,
        AboutSearchCard.Network,
        AboutSearchCard.Media,
        AboutSearchCard.Permission,
        AboutSearchCard.Component,
    )
private val AboutTechCards =
    listOf(
        AboutSearchCard.Build,
        AboutSearchCard.Ui,
        AboutSearchCard.ProjectLicense,
        AboutSearchCard.License,
    )
private val AboutLabCards = listOf(AboutSearchCard.Lab)

private fun aboutCardsForCategory(category: AboutCategory): List<AboutSearchCard> =
    when (category) {
        AboutCategory.Overview -> AboutOverviewCards
        AboutCategory.System -> AboutSystemCards
        AboutCategory.Tech -> AboutTechCards
        AboutCategory.Lab -> AboutLabCards
    }

private fun aboutCardExpanded(
    searchActive: Boolean,
    expansionState: AboutPageSectionExpansionState,
    card: AboutSearchCard,
): Boolean {
    if (searchActive) return true
    return when (card) {
        AboutSearchCard.App -> expansionState.appExpanded
        AboutSearchCard.Release -> expansionState.releaseExpanded
        AboutSearchCard.GitHub -> expansionState.githubExpanded
        AboutSearchCard.Runtime -> expansionState.runtimeExpanded
        AboutSearchCard.Network -> expansionState.networkExpanded
        AboutSearchCard.Media -> expansionState.mediaExpanded
        AboutSearchCard.Permission -> expansionState.permissionExpanded
        AboutSearchCard.Component -> expansionState.componentExpanded
        AboutSearchCard.Build -> expansionState.buildExpanded
        AboutSearchCard.Ui -> expansionState.uiFrameworkExpanded
        AboutSearchCard.ProjectLicense -> expansionState.projectLicenseExpanded
        AboutSearchCard.License -> expansionState.licenseExpanded
        AboutSearchCard.Lab -> expansionState.componentLabExpanded
    }
}
