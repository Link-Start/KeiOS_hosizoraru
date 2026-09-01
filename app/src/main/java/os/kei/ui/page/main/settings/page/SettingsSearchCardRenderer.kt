package os.kei.ui.page.main.settings.page

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import os.kei.core.log.AppLogLevel
import os.kei.core.prefs.NonHomeBackgroundAlignment
import os.kei.core.prefs.NonHomeBackgroundContentScale
import os.kei.core.prefs.NonHomeBackgroundPageStyle
import os.kei.ui.page.main.feedback.FeedbackIssueActivity
import os.kei.ui.page.main.settings.section.SettingsAnimationSection
import os.kei.ui.page.main.settings.section.SettingsBackgroundSection
import os.kei.ui.page.main.settings.section.SettingsCacheSection
import os.kei.ui.page.main.settings.section.SettingsComponentEffectsSection
import os.kei.ui.page.main.settings.section.SettingsCopySection
import os.kei.ui.page.main.settings.section.SettingsLogSection
import os.kei.ui.page.main.settings.section.SettingsNotifySection
import os.kei.ui.page.main.settings.section.SettingsPermissionKeepAliveSection
import os.kei.ui.page.main.settings.section.SettingsVisualSection
import os.kei.ui.page.main.settings.section.SettingsWebDavSyncSection
import os.kei.ui.page.main.settings.state.SettingsBackgroundController
import os.kei.ui.page.main.settings.state.SettingsCacheUiState
import os.kei.ui.page.main.settings.state.SettingsCardExpansionId
import os.kei.ui.page.main.settings.state.SettingsLogUiState
import os.kei.ui.page.main.settings.state.SettingsPageChromeState
import os.kei.ui.page.main.settings.state.SettingsPageViewModel
import os.kei.ui.page.main.settings.state.SettingsSectionContractBundle
import os.kei.ui.page.main.settings.state.SettingsWebDavSyncUiState

internal fun LazyListScope.settingsCardItem(
    card: SettingsSearchCard,
    input: SettingsSearchCardRenderInput,
    isSearchResult: Boolean = false,
) {
    item(
        key = "settings_card_${card.name}",
        contentType = "settings_card",
    ) {
        SettingsSearchCardContent(card = card, input = input, isSearchResult = isSearchResult)
    }
}

/**
 * One settings card, without the list item around it.
 *
 * Split out so the same card can be a whole row on a phone or half of one on a tablet. The `when` below is
 * the only place that knows how to build each card, and it stayed exactly as it was — the two-column layout
 * is a question about where a card is placed, not about what it is.
 */
@Composable
internal fun SettingsSearchCardContent(
    card: SettingsSearchCard,
    input: SettingsSearchCardRenderInput,
    isSearchResult: Boolean = false,
) {
    when (card) {
        SettingsSearchCard.Permissions,
        SettingsSearchCard.KeepAlive,
        SettingsSearchCard.AccessibilityGuardPolicy,
        SettingsSearchCard.AccessibilityGuardHistory,
        -> {
            val onlyCardId =
                when (card) {
                    SettingsSearchCard.Permissions -> SettingsCardExpansionId.Permissions
                    SettingsSearchCard.KeepAlive -> SettingsCardExpansionId.KeepAlive
                    SettingsSearchCard.AccessibilityGuardPolicy -> {
                        SettingsCardExpansionId.AccessibilityGuardPolicy
                    }

                    else -> SettingsCardExpansionId.AccessibilityGuardHistory
                }
            SettingsPermissionKeepAliveSection(
                state = input.sectionContracts.permissionKeepAliveState,
                actions = input.sectionContracts.permissionKeepAliveActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                isCardExpanded = input::isCardExpanded,
                onCardExpandedChange = input::updateCardExpanded,
                onlyCardId = onlyCardId,
            )
        }

        SettingsSearchCard.ThemeLanguage,
        SettingsSearchCard.Performance,
        SettingsSearchCard.HomeEffects,
        -> {
            val onlyCardId =
                when (card) {
                    SettingsSearchCard.ThemeLanguage -> SettingsCardExpansionId.ThemeLanguage
                    SettingsSearchCard.Performance -> SettingsCardExpansionId.Performance
                    else -> SettingsCardExpansionId.HomeEffects
                }
            SettingsVisualSection(
                state = input.sectionContracts.visualState,
                actions = input.sectionContracts.visualActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                isCardExpanded = input::isCardExpanded,
                onCardExpandedChange = input::updateCardExpanded,
                onlyCardId = onlyCardId,
            )
        }

        SettingsSearchCard.PageMotion -> {
            SettingsAnimationSection(
                state = input.sectionContracts.animationState,
                actions = input.sectionContracts.animationActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                expanded = input.isCardExpanded(SettingsCardExpansionId.PageMotion),
                onExpandedChange = { input.updateCardExpanded(SettingsCardExpansionId.PageMotion, it) },
            )
        }

        SettingsSearchCard.LiquidControls,
        SettingsSearchCard.Interaction,
        -> {
            val onlyCardId =
                when (card) {
                    SettingsSearchCard.LiquidControls -> SettingsCardExpansionId.LiquidControls
                    else -> SettingsCardExpansionId.Interaction
                }
            SettingsComponentEffectsSection(
                state = input.sectionContracts.componentEffectsState,
                actions = input.sectionContracts.componentEffectsActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                isCardExpanded = input::isCardExpanded,
                onCardExpandedChange = input::updateCardExpanded,
                onlyCardId = onlyCardId,
            )
        }

        SettingsSearchCard.BackgroundAsset,
        SettingsSearchCard.BackgroundLayout,
        SettingsSearchCard.BackgroundRendering,
        -> {
            val onlyCardId =
                when (card) {
                    SettingsSearchCard.BackgroundAsset -> SettingsCardExpansionId.BackgroundAsset
                    SettingsSearchCard.BackgroundLayout -> SettingsCardExpansionId.BackgroundLayout
                    else -> SettingsCardExpansionId.BackgroundRendering
                }
            SettingsBackgroundSection(
                nonHomeBackgroundEnabled = input.nonHomeBackgroundEnabled,
                onNonHomeBackgroundEnabledChanged = input.onNonHomeBackgroundEnabledChanged,
                nonHomeBackgroundUri = input.nonHomeBackgroundUri,
                nonHomeBackgroundOpacity = input.nonHomeBackgroundOpacity,
                onNonHomeBackgroundOpacityChanged = input.onNonHomeBackgroundOpacityChanged,
                nonHomeBackgroundContentScale = input.nonHomeBackgroundContentScale,
                onNonHomeBackgroundContentScaleChanged = input.onNonHomeBackgroundContentScaleChanged,
                nonHomeBackgroundAlignment = input.nonHomeBackgroundAlignment,
                onNonHomeBackgroundAlignmentChanged = input.onNonHomeBackgroundAlignmentChanged,
                nonHomeBackgroundPageStyle = input.nonHomeBackgroundPageStyle,
                onNonHomeBackgroundPageStyleChanged = input.onNonHomeBackgroundPageStyleChanged,
                nonHomeBackgroundScrim = input.nonHomeBackgroundScrim,
                onNonHomeBackgroundScrimChanged = input.onNonHomeBackgroundScrimChanged,
                nonHomeBackgroundDepthEnabled = input.nonHomeBackgroundDepthEnabled,
                onNonHomeBackgroundDepthEnabledChanged = input.onNonHomeBackgroundDepthEnabledChanged,
                nonHomeBackgroundSaturation = input.nonHomeBackgroundSaturation,
                onNonHomeBackgroundSaturationChanged = input.onNonHomeBackgroundSaturationChanged,
                onResetNonHomeBackgroundRendering = input.onResetNonHomeBackgroundRendering,
                onApplyNonHomeBackgroundReadableSuggestion = input.onApplyNonHomeBackgroundReadableSuggestion,
                backgroundPickerLauncher = input.backgroundController.backgroundPickerLauncher,
                onClearBackground = input.backgroundController.clearBackground,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                onSliderInteractionChanged = input.onSliderInteractionChanged,
                isCardExpanded = input::isCardExpanded,
                onCardExpandedChange = input::updateCardExpanded,
                onlyCardId = onlyCardId,
            )
        }

        SettingsSearchCard.Notify -> {
            SettingsNotifySection(
                state = input.sectionContracts.notifyState,
                actions = input.sectionContracts.notifyActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                onSliderInteractionChanged = input.onSliderInteractionChanged,
                expanded = input.isCardExpanded(SettingsCardExpansionId.Notifications),
                onExpandedChange = { input.updateCardExpanded(SettingsCardExpansionId.Notifications, it) },
            )
        }

        SettingsSearchCard.Copy -> {
            SettingsCopySection(
                state = input.sectionContracts.copyState,
                actions = input.sectionContracts.copyActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                expanded = input.isCardExpanded(SettingsCardExpansionId.CopySelection),
                onExpandedChange = { input.updateCardExpanded(SettingsCardExpansionId.CopySelection, it) },
            )
        }

        SettingsSearchCard.CacheDiagnostics,
        SettingsSearchCard.CacheItems,
        -> {
            val onlyCardId =
                when (card) {
                    SettingsSearchCard.CacheDiagnostics -> SettingsCardExpansionId.CacheDiagnostics
                    else -> SettingsCardExpansionId.CacheItems
                }
            SettingsCacheSection(
                cacheDiagnosticsEnabled = input.cacheDiagnosticsEnabled,
                onCacheDiagnosticsChanged = input.onCacheDiagnosticsChanged,
                cacheEntries = input.cacheState.cacheEntries,
                cacheEntriesLoading = input.cacheState.cacheEntriesLoading,
                clearingAllCaches = input.cacheState.clearingAllCaches,
                clearingCacheId = input.cacheState.clearingCacheId,
                onClearAllCaches = input::clearAllCaches,
                onClearCache = input::clearCache,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                isCardExpanded = input::isCardExpanded,
                onCardExpandedChange = input::updateCardExpanded,
                onlyCardId = onlyCardId,
            )
        }

        SettingsSearchCard.LogLevel,
        SettingsSearchCard.LogFiles,
        -> {
            val onlyCardId =
                when (card) {
                    SettingsSearchCard.LogLevel -> SettingsCardExpansionId.LogLevel
                    else -> SettingsCardExpansionId.LogFiles
                }
            SettingsLogSection(
                logLevel = input.logLevel,
                onLogLevelChanged = input.onLogLevelChanged,
                logStats = input.logState.logStats,
                exportingLogZip = input.logState.exportingLogZip,
                clearingLogs = input.logState.clearingLogs,
                levelExpanded = input.chromeState.showLogLevelPopup,
                levelAnchorBounds = input.chromeState.logLevelPopupAnchorBounds,
                onLevelExpandedChange = input.settingsPageViewModel::updateShowLogLevelPopup,
                onLevelAnchorBoundsChange = input.settingsPageViewModel::updateLogLevelPopupAnchorBounds,
                onExportZipClick = input.settingsPageViewModel::beginLogExport,
                onClearLogsClick = input::clearLogs,
                onFeedbackClick = input::openFeedbackIssue,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                isCardExpanded = input::isCardExpanded,
                onCardExpandedChange = input::updateCardExpanded,
                onlyCardId = onlyCardId,
            )
        }

        SettingsSearchCard.WebDavSync -> {
            SettingsWebDavSyncSection(
                state = input.webDavSyncState,
                onClick = input::openWebDavSync,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                expanded = input.isCardExpanded(SettingsCardExpansionId.WebDavSync),
                onExpandedChange = { input.updateCardExpanded(SettingsCardExpansionId.WebDavSync, it) },
                isSearchResult = isSearchResult,
            )
        }
    }
}

internal fun LazyListScope.settingsCategoryItems(
    category: SettingsCategory,
    input: SettingsSearchCardRenderInput,
) {
    settingsCardsForCategory(category).forEach { card ->
        settingsCardItem(card, input)
    }
}

/** The cards a settings category shows, in the order they are read. */
internal fun settingsCategoryCards(category: SettingsCategory): List<SettingsSearchCard> =
    settingsCardsForCategory(category)

/**
 * The same cards as cells of a staggered grid.
 *
 * One cell per card, exactly as the column emits one item per card, so a card is still composed only when
 * its column reaches it. The grid decides which column each one lands in, which is the whole point: cards
 * here are accordions and a collapsed one next to an expanded one would otherwise leave a dead half-column.
 */
internal fun LazyStaggeredGridScope.settingsCardCells(
    cards: List<SettingsSearchCard>,
    input: SettingsSearchCardRenderInput,
    isSearchResult: Boolean = false,
) {
    cards.forEach { card ->
        item(
            key = "settings_card_${card.name}",
            contentType = "settings_card",
        ) {
            SettingsSearchCardContent(
                card = card,
                input = input,
                isSearchResult = isSearchResult,
            )
        }
    }
}

private fun settingsCardsForCategory(category: SettingsCategory): List<SettingsSearchCard> =
    when (category) {
        SettingsCategory.Access -> {
            listOf(
                SettingsSearchCard.Permissions,
                SettingsSearchCard.Notify,
            )
        }

        SettingsCategory.KeepAlive -> {
            listOf(
                SettingsSearchCard.KeepAlive,
                SettingsSearchCard.AccessibilityGuardPolicy,
                SettingsSearchCard.AccessibilityGuardHistory,
            )
        }

        SettingsCategory.Interface -> {
            listOf(
                SettingsSearchCard.ThemeLanguage,
                SettingsSearchCard.Performance,
                SettingsSearchCard.HomeEffects,
                SettingsSearchCard.PageMotion,
                SettingsSearchCard.LiquidControls,
                SettingsSearchCard.Interaction,
                SettingsSearchCard.BackgroundAsset,
                SettingsSearchCard.BackgroundLayout,
                SettingsSearchCard.BackgroundRendering,
            )
        }

        SettingsCategory.Data -> {
            listOf(
                SettingsSearchCard.Copy,
                SettingsSearchCard.WebDavSync,
                SettingsSearchCard.CacheDiagnostics,
                SettingsSearchCard.CacheItems,
                SettingsSearchCard.LogLevel,
                SettingsSearchCard.LogFiles,
            )
        }
    }

internal data class SettingsSearchCardRenderInput(
    val context: Context,
    val settingsPageViewModel: SettingsPageViewModel,
    val chromeState: SettingsPageChromeState,
    val sectionContracts: SettingsSectionContractBundle,
    val backgroundController: SettingsBackgroundController,
    val cacheState: SettingsCacheUiState,
    val logState: SettingsLogUiState,
    val webDavSyncState: SettingsWebDavSyncUiState,
    val cacheDiagnosticsEnabled: Boolean,
    val onCacheDiagnosticsChanged: (Boolean) -> Unit,
    val logLevel: AppLogLevel,
    val onLogLevelChanged: (AppLogLevel) -> Unit,
    val nonHomeBackgroundEnabled: Boolean,
    val onNonHomeBackgroundEnabledChanged: (Boolean) -> Unit,
    val nonHomeBackgroundUri: String,
    val nonHomeBackgroundOpacity: Float,
    val onNonHomeBackgroundOpacityChanged: (Float) -> Unit,
    val nonHomeBackgroundContentScale: NonHomeBackgroundContentScale,
    val onNonHomeBackgroundContentScaleChanged: (NonHomeBackgroundContentScale) -> Unit,
    val nonHomeBackgroundAlignment: NonHomeBackgroundAlignment,
    val onNonHomeBackgroundAlignmentChanged: (NonHomeBackgroundAlignment) -> Unit,
    val nonHomeBackgroundPageStyle: NonHomeBackgroundPageStyle,
    val onNonHomeBackgroundPageStyleChanged: (NonHomeBackgroundPageStyle) -> Unit,
    val nonHomeBackgroundScrim: Float,
    val onNonHomeBackgroundScrimChanged: (Float) -> Unit,
    val nonHomeBackgroundDepthEnabled: Boolean,
    val onNonHomeBackgroundDepthEnabledChanged: (Boolean) -> Unit,
    val nonHomeBackgroundSaturation: Float,
    val onNonHomeBackgroundSaturationChanged: (Float) -> Unit,
    val onResetNonHomeBackgroundRendering: () -> Unit,
    val onApplyNonHomeBackgroundReadableSuggestion: (Boolean) -> Unit,
    val enabledCardColor: Color,
    val disabledCardColor: Color,
    val onSliderInteractionChanged: (Boolean) -> Unit,
    val onNavigateToWebDavSync: () -> Unit = {},
) {
    fun clearAllCaches() {
        settingsPageViewModel.requestClearAllCaches(context)
    }

    fun clearCache(cacheId: String) {
        settingsPageViewModel.requestClearCache(context, cacheId)
    }

    fun clearLogs() {
        settingsPageViewModel.requestClearLogs(context)
    }

    fun openFeedbackIssue() {
        FeedbackIssueActivity.launch(context)
    }

    fun openWebDavSync() {
        onNavigateToWebDavSync()
    }

    fun isCardExpanded(id: SettingsCardExpansionId): Boolean = chromeState.trimmedSearchQuery.isNotEmpty() || chromeState.isCardExpanded(id)

    fun updateCardExpanded(
        id: SettingsCardExpansionId,
        expanded: Boolean,
    ) {
        if (!shouldPersistSettingsCardExpansion(chromeState.searchQuery)) return
        settingsPageViewModel.updateCardExpanded(id, expanded)
    }
}

internal fun shouldPersistSettingsCardExpansion(searchQuery: String): Boolean = searchQuery.trim().isEmpty()
