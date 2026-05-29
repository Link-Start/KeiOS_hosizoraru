package os.kei.ui.page.main.settings.page

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import os.kei.core.log.AppLogLevel
import os.kei.ui.page.main.feedback.FeedbackIssueActivity
import os.kei.ui.page.main.settings.section.SettingsAnimationSection
import os.kei.ui.page.main.settings.section.SettingsBackgroundSection
import os.kei.ui.page.main.settings.section.SettingsCacheSection
import os.kei.ui.page.main.settings.section.SettingsComponentEffectsSection
import os.kei.ui.page.main.settings.section.SettingsCopySection
import os.kei.ui.page.main.settings.section.SettingsLogSection
import os.kei.ui.page.main.settings.section.SettingsNotifySection
import os.kei.ui.page.main.settings.section.SettingsWebDavSyncSection
import os.kei.ui.page.main.settings.section.SettingsPermissionKeepAliveSection
import os.kei.ui.page.main.settings.section.SettingsVisualSection
import os.kei.ui.page.main.settings.state.SettingsBackgroundController
import os.kei.ui.page.main.settings.state.SettingsCacheUiState
import os.kei.ui.page.main.settings.state.SettingsLogUiState
import os.kei.ui.page.main.settings.state.SettingsPageChromeState
import os.kei.ui.page.main.settings.state.SettingsPageViewModel
import os.kei.ui.page.main.settings.state.SettingsSectionContractBundle

internal fun LazyListScope.settingsCardItem(
    card: SettingsSearchCard,
    input: SettingsSearchCardRenderInput,
) {
    item(
        key = "settings_card_${card.name}",
        contentType = "settings_card",
    ) {
        when (card) {
            SettingsSearchCard.Permissions -> {
                SettingsPermissionKeepAliveSection(
                    state = input.sectionContracts.permissionKeepAliveState,
                    actions = input.sectionContracts.permissionKeepAliveActions,
                    enabledCardColor = input.enabledCardColor,
                    disabledCardColor = input.disabledCardColor,
                )
            }

            SettingsSearchCard.Visual -> {
                SettingsVisualSection(
                    state = input.sectionContracts.visualState,
                    actions = input.sectionContracts.visualActions,
                    enabledCardColor = input.enabledCardColor,
                    disabledCardColor = input.disabledCardColor,
                )
            }

            SettingsSearchCard.Animation -> {
                SettingsAnimationSection(
                    state = input.sectionContracts.animationState,
                    actions = input.sectionContracts.animationActions,
                    enabledCardColor = input.enabledCardColor,
                    disabledCardColor = input.disabledCardColor,
                )
            }

            SettingsSearchCard.ComponentEffects -> {
                SettingsComponentEffectsSection(
                    state = input.sectionContracts.componentEffectsState,
                    actions = input.sectionContracts.componentEffectsActions,
                    enabledCardColor = input.enabledCardColor,
                    disabledCardColor = input.disabledCardColor,
                )
            }

            SettingsSearchCard.Background -> {
                SettingsBackgroundSection(
                    nonHomeBackgroundEnabled = input.nonHomeBackgroundEnabled,
                    onNonHomeBackgroundEnabledChanged = input.onNonHomeBackgroundEnabledChanged,
                    nonHomeBackgroundUri = input.nonHomeBackgroundUri,
                    nonHomeBackgroundOpacity = input.nonHomeBackgroundOpacity,
                    onNonHomeBackgroundOpacityChanged = input.onNonHomeBackgroundOpacityChanged,
                    backgroundPickerLauncher = input.backgroundController.backgroundPickerLauncher,
                    onClearBackground = input.backgroundController.clearBackground,
                    enabledCardColor = input.enabledCardColor,
                    disabledCardColor = input.disabledCardColor,
                    onSliderInteractionChanged = input.onSliderInteractionChanged,
                )
            }

            SettingsSearchCard.Notify -> {
                SettingsNotifySection(
                    state = input.sectionContracts.notifyState,
                    actions = input.sectionContracts.notifyActions,
                    enabledCardColor = input.enabledCardColor,
                    disabledCardColor = input.disabledCardColor,
                    onSliderInteractionChanged = input.onSliderInteractionChanged,
                )
            }

            SettingsSearchCard.Copy -> {
                SettingsCopySection(
                    state = input.sectionContracts.copyState,
                    actions = input.sectionContracts.copyActions,
                    enabledCardColor = input.enabledCardColor,
                    disabledCardColor = input.disabledCardColor,
                )
            }

            SettingsSearchCard.Cache -> {
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
                )
            }

            SettingsSearchCard.Log -> {
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
                )
            }

            SettingsSearchCard.WebDavSync -> {
                SettingsWebDavSyncSection(
                    onClick = input::openWebDavSync,
                    enabledCardColor = input.enabledCardColor,
                    disabledCardColor = input.disabledCardColor,
                )
            }
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

private fun settingsCardsForCategory(category: SettingsCategory): List<SettingsSearchCard> =
    when (category) {
        SettingsCategory.Access -> {
            listOf(
                SettingsSearchCard.Permissions,
                SettingsSearchCard.Notify,
            )
        }

        SettingsCategory.Appearance -> {
            listOf(
                SettingsSearchCard.Visual,
                SettingsSearchCard.Background,
            )
        }

        SettingsCategory.Effects -> {
            listOf(
                SettingsSearchCard.Animation,
                SettingsSearchCard.ComponentEffects,
            )
        }

        SettingsCategory.Data -> {
            listOf(
                SettingsSearchCard.Copy,
                SettingsSearchCard.WebDavSync,
                SettingsSearchCard.Cache,
                SettingsSearchCard.Log,
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
    val cacheDiagnosticsEnabled: Boolean,
    val onCacheDiagnosticsChanged: (Boolean) -> Unit,
    val logLevel: AppLogLevel,
    val onLogLevelChanged: (AppLogLevel) -> Unit,
    val nonHomeBackgroundEnabled: Boolean,
    val onNonHomeBackgroundEnabledChanged: (Boolean) -> Unit,
    val nonHomeBackgroundUri: String,
    val nonHomeBackgroundOpacity: Float,
    val onNonHomeBackgroundOpacityChanged: (Float) -> Unit,
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
}
