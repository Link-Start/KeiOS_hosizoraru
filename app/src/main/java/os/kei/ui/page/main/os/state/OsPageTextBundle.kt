package os.kei.ui.page.main.os.state

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.rememberBuiltInShellCommandCards
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.rememberBuiltInActivityShortcutCards

internal data class OsPageTextBundle(
    val exportSuccessText: String,
    val noRefreshableCardText: String,
    val refreshCompletedText: String,
    val manageCardsContentDescription: String,
    val manageActivitiesContentDescription: String,
    val manageShellCardsContentDescription: String,
    val refreshParamsContentDescription: String,
    val searchLabel: String,
    val visibleCardsTitle: String,
    val visibleActivitiesTitle: String,
    val visibleShellCardsTitle: String,
    val visibleShellCardsDesc: String,
    val googleSystemServiceDefaultTitle: String,
    val shellSavedCountLabel: String,
    val shellCardSavedToast: String,
    val shellCardDeletedToast: String,
    val shellCardCommandRequiredToast: String,
    val shellCardDeleteDialogTitle: String,
    val shellRunNoPermissionText: String,
    val shellRunNoOutputText: String,
    val editShellCommandCardTitle: String,
    val editActivityCardTitle: String,
    val addActivityCardTitle: String,
    val activityCardDeletedToast: String,
    val activityCardDeleteDialogTitle: String,
    val cardImportFailedWithReason: String,
    val noMatchedResultsText: String,
    val googleSystemServiceDefaultIntentFlags: String,
    val googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    val googleSettingsBuiltInSampleDefaults: OsGoogleSystemServiceConfig,
    val builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    val builtInShellCommandCards: List<OsShellCommandCard>,
)

@Composable
internal fun rememberOsPageTextBundle(): OsPageTextBundle {
    val exportSuccessText = stringResource(R.string.common_export_success)
    val noRefreshableCardText = stringResource(R.string.os_toast_no_refreshable_card)
    val refreshCompletedText = stringResource(R.string.os_toast_refresh_completed)
    val manageCardsContentDescription = stringResource(R.string.os_action_manage_cards)
    val manageActivitiesContentDescription = stringResource(R.string.os_action_manage_activities)
    val manageShellCardsContentDescription = stringResource(R.string.os_action_manage_shell_cards)
    val refreshParamsContentDescription = stringResource(R.string.os_action_refresh_params)
    val searchLabel = stringResource(R.string.os_search_label)
    val visibleCardsTitle = stringResource(R.string.os_sheet_visible_cards_title)
    val visibleActivitiesTitle = stringResource(R.string.os_sheet_visible_activities_title)
    val visibleShellCardsTitle = stringResource(R.string.os_sheet_visible_shell_cards_title)
    val visibleShellCardsDesc = stringResource(R.string.os_sheet_visible_shell_cards_desc)
    val googleSystemServiceDefaultTitle = stringResource(R.string.os_section_google_system_service_title)
    val googleSystemServiceDefaultSubtitle =
        stringResource(R.string.os_google_system_service_default_subtitle)
    val googleSystemServiceDefaultAppName =
        stringResource(R.string.os_google_system_service_default_app_name)
    val shellSavedCountLabel = stringResource(R.string.os_shell_card_saved_count_label)
    val shellCardSavedToast = stringResource(R.string.os_shell_card_toast_saved)
    val shellCardDeletedToast = stringResource(R.string.os_shell_card_toast_deleted)
    val shellCardCommandRequiredToast = stringResource(R.string.os_shell_card_toast_command_required)
    val shellCardDeleteDialogTitle = stringResource(R.string.os_shell_card_delete_dialog_title)
    val shellRunNoPermissionText = stringResource(R.string.os_shell_run_requires_permission)
    val shellRunNoOutputText = stringResource(R.string.os_shell_run_empty_output)
    val editShellCommandCardTitle = stringResource(R.string.os_shell_card_sheet_title_edit)
    val editActivityCardTitle = stringResource(R.string.os_activity_sheet_title_edit)
    val addActivityCardTitle = stringResource(R.string.os_activity_sheet_title_add)
    val activityCardDeletedToast = stringResource(R.string.os_activity_card_toast_deleted)
    val activityCardDeleteDialogTitle = stringResource(R.string.os_activity_card_delete_dialog_title)
    val cardImportFailedWithReason = stringResource(R.string.os_card_toast_import_failed_with_reason)
    val noMatchedResultsText = stringResource(R.string.common_no_matched_results)
    val activityBuiltInGoogleSettingsTitle =
        stringResource(R.string.os_activity_builtin_google_settings_title)
    val activityBuiltInGoogleSettingsSubtitle =
        stringResource(R.string.os_activity_builtin_google_settings_subtitle)
    val activityBuiltInGoogleSettingsAppName =
        stringResource(R.string.os_activity_builtin_google_settings_app_name)
    val activityBuiltInGoogleSettingsPackage =
        stringResource(R.string.os_activity_builtin_google_settings_package)
    val activityBuiltInGoogleSettingsClass =
        stringResource(R.string.os_activity_builtin_google_settings_class)
    val googleSystemServiceDefaultIntentFlags =
        stringResource(R.string.os_google_system_service_default_intent_flags)

    val googleSystemServiceDefaults =
        remember(
            googleSystemServiceDefaultTitle,
            googleSystemServiceDefaultSubtitle,
            googleSystemServiceDefaultAppName,
            googleSystemServiceDefaultIntentFlags,
        ) {
            OsGoogleSystemServiceConfig(
                title = googleSystemServiceDefaultTitle,
                subtitle = googleSystemServiceDefaultSubtitle,
                appName = googleSystemServiceDefaultAppName,
                intentFlags = googleSystemServiceDefaultIntentFlags,
            ).normalized()
        }
    val googleSettingsBuiltInSampleDefaults =
        remember(
            activityBuiltInGoogleSettingsTitle,
            activityBuiltInGoogleSettingsSubtitle,
            activityBuiltInGoogleSettingsAppName,
            activityBuiltInGoogleSettingsPackage,
            activityBuiltInGoogleSettingsClass,
            googleSystemServiceDefaultIntentFlags,
            googleSystemServiceDefaults,
        ) {
            OsGoogleSystemServiceConfig(
                title = activityBuiltInGoogleSettingsTitle,
                subtitle = activityBuiltInGoogleSettingsSubtitle,
                appName = activityBuiltInGoogleSettingsAppName,
                packageName = activityBuiltInGoogleSettingsPackage,
                className = activityBuiltInGoogleSettingsClass,
                intentAction = Intent.ACTION_VIEW,
                intentFlags = googleSystemServiceDefaultIntentFlags,
            ).normalized(googleSystemServiceDefaults)
        }
    val builtInActivityShortcutCards =
        rememberBuiltInActivityShortcutCards(
            defaults = googleSystemServiceDefaults,
            defaultIntentFlags = googleSystemServiceDefaultIntentFlags,
        )
    val builtInShellCommandCards = rememberBuiltInShellCommandCards()

    return remember(
        exportSuccessText,
        noRefreshableCardText,
        refreshCompletedText,
        manageCardsContentDescription,
        manageActivitiesContentDescription,
        manageShellCardsContentDescription,
        refreshParamsContentDescription,
        searchLabel,
        visibleCardsTitle,
        visibleActivitiesTitle,
        visibleShellCardsTitle,
        visibleShellCardsDesc,
        googleSystemServiceDefaultTitle,
        shellSavedCountLabel,
        shellCardSavedToast,
        shellCardDeletedToast,
        shellCardCommandRequiredToast,
        shellCardDeleteDialogTitle,
        shellRunNoPermissionText,
        shellRunNoOutputText,
        editShellCommandCardTitle,
        editActivityCardTitle,
        addActivityCardTitle,
        activityCardDeletedToast,
        activityCardDeleteDialogTitle,
        cardImportFailedWithReason,
        noMatchedResultsText,
        googleSystemServiceDefaultIntentFlags,
        googleSystemServiceDefaults,
        googleSettingsBuiltInSampleDefaults,
        builtInActivityShortcutCards,
        builtInShellCommandCards,
    ) {
        OsPageTextBundle(
            exportSuccessText = exportSuccessText,
            noRefreshableCardText = noRefreshableCardText,
            refreshCompletedText = refreshCompletedText,
            manageCardsContentDescription = manageCardsContentDescription,
            manageActivitiesContentDescription = manageActivitiesContentDescription,
            manageShellCardsContentDescription = manageShellCardsContentDescription,
            refreshParamsContentDescription = refreshParamsContentDescription,
            searchLabel = searchLabel,
            visibleCardsTitle = visibleCardsTitle,
            visibleActivitiesTitle = visibleActivitiesTitle,
            visibleShellCardsTitle = visibleShellCardsTitle,
            visibleShellCardsDesc = visibleShellCardsDesc,
            googleSystemServiceDefaultTitle = googleSystemServiceDefaultTitle,
            shellSavedCountLabel = shellSavedCountLabel,
            shellCardSavedToast = shellCardSavedToast,
            shellCardDeletedToast = shellCardDeletedToast,
            shellCardCommandRequiredToast = shellCardCommandRequiredToast,
            shellCardDeleteDialogTitle = shellCardDeleteDialogTitle,
            shellRunNoPermissionText = shellRunNoPermissionText,
            shellRunNoOutputText = shellRunNoOutputText,
            editShellCommandCardTitle = editShellCommandCardTitle,
            editActivityCardTitle = editActivityCardTitle,
            addActivityCardTitle = addActivityCardTitle,
            activityCardDeletedToast = activityCardDeletedToast,
            activityCardDeleteDialogTitle = activityCardDeleteDialogTitle,
            cardImportFailedWithReason = cardImportFailedWithReason,
            noMatchedResultsText = noMatchedResultsText,
            googleSystemServiceDefaultIntentFlags = googleSystemServiceDefaultIntentFlags,
            googleSystemServiceDefaults = googleSystemServiceDefaults,
            googleSettingsBuiltInSampleDefaults = googleSettingsBuiltInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
            builtInShellCommandCards = builtInShellCommandCards,
        )
    }
}
