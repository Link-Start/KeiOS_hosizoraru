package os.kei.ui.page.main.settings.page

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.log.AppLogLevel
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
import os.kei.ui.page.main.settings.state.SettingsBackgroundController
import os.kei.ui.page.main.settings.state.SettingsCacheUiState
import os.kei.ui.page.main.settings.state.SettingsLogUiState
import os.kei.ui.page.main.settings.state.SettingsPageViewModel
import os.kei.ui.page.main.settings.state.SettingsSectionContractBundle

internal fun LazyListScope.settingsCardItem(
    card: SettingsSearchCard,
    input: SettingsSearchCardRenderInput
) {
    item(key = "settings_card_${card.name}") {
        when (card) {
            SettingsSearchCard.Permissions -> SettingsPermissionKeepAliveSection(
                state = input.sectionContracts.permissionKeepAliveState,
                actions = input.sectionContracts.permissionKeepAliveActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
            )

            SettingsSearchCard.Visual -> SettingsVisualSection(
                state = input.sectionContracts.visualState,
                actions = input.sectionContracts.visualActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
            )

            SettingsSearchCard.Animation -> SettingsAnimationSection(
                state = input.sectionContracts.animationState,
                actions = input.sectionContracts.animationActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
            )

            SettingsSearchCard.ComponentEffects -> SettingsComponentEffectsSection(
                state = input.sectionContracts.componentEffectsState,
                actions = input.sectionContracts.componentEffectsActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
            )

            SettingsSearchCard.Background -> SettingsBackgroundSection(
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

            SettingsSearchCard.Notify -> SettingsNotifySection(
                state = input.sectionContracts.notifyState,
                actions = input.sectionContracts.notifyActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
                onSliderInteractionChanged = input.onSliderInteractionChanged,
            )

            SettingsSearchCard.Copy -> SettingsCopySection(
                state = input.sectionContracts.copyState,
                actions = input.sectionContracts.copyActions,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
            )

            SettingsSearchCard.Cache -> SettingsCacheSection(
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

            SettingsSearchCard.Log -> SettingsLogSection(
                logLevel = input.logLevel,
                onLogLevelChanged = input.onLogLevelChanged,
                logStats = input.logState.logStats,
                exportingLogZip = input.logState.exportingLogZip,
                clearingLogs = input.logState.clearingLogs,
                onExportZipClick = input.settingsPageViewModel::beginLogExport,
                onClearLogsClick = input::clearLogs,
                onFeedbackClick = input::openFeedbackIssue,
                enabledCardColor = input.enabledCardColor,
                disabledCardColor = input.disabledCardColor,
            )
        }
    }
}

internal fun LazyListScope.settingsCategoryItems(
    category: SettingsCategory,
    input: SettingsSearchCardRenderInput
) {
    settingsCardsForCategory(category).forEach { card ->
        settingsCardItem(card, input)
    }
}

private fun settingsCardsForCategory(category: SettingsCategory): List<SettingsSearchCard> {
    return when (category) {
        SettingsCategory.Access -> listOf(SettingsSearchCard.Permissions)
        SettingsCategory.Appearance -> listOf(
            SettingsSearchCard.Visual,
            SettingsSearchCard.Animation,
            SettingsSearchCard.ComponentEffects,
            SettingsSearchCard.Background
        )

        SettingsCategory.Notify -> listOf(SettingsSearchCard.Notify)
        SettingsCategory.Data -> listOf(
            SettingsSearchCard.Copy,
            SettingsSearchCard.Cache,
            SettingsSearchCard.Log
        )
    }
}

internal data class SettingsSearchCardRenderInput(
    val context: Context,
    val scope: CoroutineScope,
    val settingsPageViewModel: SettingsPageViewModel,
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
    val onSliderInteractionChanged: (Boolean) -> Unit
) {
    fun clearAllCaches() {
        scope.launch {
            val result = settingsPageViewModel.clearAllCaches(context)
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_cache_toast_cleared_all),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                val reason = result.exceptionOrNull()?.javaClass?.simpleName
                    ?: context.getString(R.string.common_unknown)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_cache_toast_clear_all_failed, reason),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun clearCache(cacheId: String) {
        scope.launch {
            settingsPageViewModel.clearCache(context, cacheId)
        }
    }

    fun clearLogs() {
        scope.launch {
            val result = settingsPageViewModel.clearLogs(context)
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_log_toast_cleared),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                val reason = result.exceptionOrNull()?.javaClass?.simpleName
                    ?: context.getString(R.string.common_unknown)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_log_toast_clear_failed, reason),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun openFeedbackIssue() {
        FeedbackIssueActivity.launch(context)
    }
}
