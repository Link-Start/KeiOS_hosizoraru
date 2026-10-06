@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.feature.github.data.local.GitHubAppPickerPreferences
import os.kei.feature.github.model.FdroidAppSearchCandidate
import os.kei.feature.github.model.FdroidAppSearchFailure
import os.kei.feature.github.model.FdroidAppSearchRepoReport
import os.kei.feature.github.model.FdroidAntiFeaturePolicy
import os.kei.feature.github.model.FdroidRepositoryPreset
import os.kei.feature.github.model.FdroidTrackedAppConfig
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.model.FdroidVersionSelectionMode
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerDerivedState
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerInput
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.sheet.UnsavedSheetDismissConfirmDialog
import os.kei.ui.page.main.widget.sheet.rememberUnsavedSheetDismissHandler

@Composable
internal fun GitHubTrackEditSheet(
    show: Boolean,
    backdrop: Backdrop,
    editingTrackedItem: GitHubTrackedApp?,
    repoUrlInput: String,
    repoScanCandidates: List<GitHubPackageRepositoryScanCandidate>,
    appSearch: String,
    packageNameInput: String,
    repoUrlScanRunning: Boolean,
    packageNameScanRunning: Boolean,
    pickerExpanded: Boolean,
    selectedApp: InstalledAppItem?,
    appList: List<InstalledAppItem>,
    appPickerDerivedState: GitHubTrackAppPickerDerivedState,
    appPickerPreferences: GitHubAppPickerPreferences,
    trackedPackageNames: Set<String>,
    appListRefreshing: Boolean,
    addAppPickerRememberedFirstVisibleItemIndex: Int,
    addAppPickerRememberedFirstVisibleItemScrollOffset: Int,
    sourceModeInput: GitHubTrackedSourceMode,
    preferPreReleaseInput: Boolean,
    alwaysShowLatestReleaseDownloadButtonInput: Boolean,
    checkActionsUpdatesInput: Boolean,
    externalBuildUntilReleaseInput: Boolean,
    updateIntervalModeInput: GitHubTrackedUpdateIntervalMode,
    actionsUpdateIntervalModeInput: GitHubTrackedActionsUpdateIntervalMode,
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode,
    ignoreModeInput: GitHubTrackedIgnoreMode,
    ignoredStableReleaseKeyInput: String,
    ignoredPreReleaseKeyInput: String,
    fdroidVersionSelectionModeInput: FdroidVersionSelectionMode,
    fdroidVersionNameRegexInput: String,
    fdroidApkNameRegexInput: String,
    fdroidTrustPolicyInput: FdroidTrustPolicy,
    fdroidAntiFeaturePolicyInput: FdroidAntiFeaturePolicy,
    fdroidRepoScopeIdInput: String,
    fdroidAppSearchQueryInput: String,
    fdroidAppSearchCandidates: List<FdroidAppSearchCandidate>,
    fdroidAppSearchFailures: List<FdroidAppSearchFailure>,
    fdroidAppSearchFailuresExpanded: Boolean,
    fdroidAppSearchRepoReports: List<FdroidAppSearchRepoReport>,
    fdroidSelectedCandidate: FdroidAppSearchCandidate?,
    fdroidAppSearchRunning: Boolean,
    enabledFdroidCommonRepos: List<FdroidRepositoryPreset>,
    sourceModeDropdownExpanded: Boolean,
    updateIntervalDropdownExpanded: Boolean,
    actionsIntervalDropdownExpanded: Boolean,
    preciseModeDropdownExpanded: Boolean,
    ignoreModeDropdownExpanded: Boolean,
    fdroidVersionSelectionDropdownExpanded: Boolean,
    fdroidTrustPolicyDropdownExpanded: Boolean,
    fdroidAntiFeaturePolicyDropdownExpanded: Boolean,
    fdroidRepoScopeDropdownExpanded: Boolean,
    globalRefreshIntervalHours: Int,
    globalPreciseApkVersionEnabled: Boolean,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
    onRepoUrlInputChange: (String) -> Unit,
    onSourceModeInputChange: (GitHubTrackedSourceMode) -> Unit,
    onAppSearchChange: (String) -> Unit,
    onPackageNameInputChange: (String) -> Unit,
    onScanRepoUrl: () -> Unit,
    onScanPackageName: () -> Unit,
    onRepoScanCandidateSelected: (GitHubPackageRepositoryScanCandidate) -> Unit,
    onPickerExpandedChange: (Boolean) -> Unit,
    onRefreshAppList: () -> Unit,
    onRequestAppIcons: (List<String>) -> Unit,
    onRequestAppPickerState: (GitHubTrackAppPickerInput) -> Unit,
    onAppPickerPreferencesChange: (GitHubAppPickerPreferences) -> Unit,
    onAddAppPickerScrollPositionChange: (Int, Int) -> Unit,
    onSelectedAppChange: (InstalledAppItem?) -> Unit,
    onPreferPreReleaseInputChange: (Boolean) -> Unit,
    onAlwaysShowLatestReleaseDownloadButtonInputChange: (Boolean) -> Unit,
    onCheckActionsUpdatesInputChange: (Boolean) -> Unit,
    onExternalBuildUntilReleaseInputChange: (Boolean) -> Unit,
    onUpdateIntervalModeInputChange: (GitHubTrackedUpdateIntervalMode) -> Unit,
    onActionsUpdateIntervalModeInputChange: (GitHubTrackedActionsUpdateIntervalMode) -> Unit,
    onPreciseApkVersionModeInputChange: (GitHubTrackedPreciseApkVersionMode) -> Unit,
    onIgnoreModeInputChange: (GitHubTrackedIgnoreMode) -> Unit,
    onFdroidVersionSelectionModeInputChange: (FdroidVersionSelectionMode) -> Unit,
    onFdroidVersionNameRegexInputChange: (String) -> Unit,
    onFdroidApkNameRegexInputChange: (String) -> Unit,
    onFdroidTrustPolicyInputChange: (FdroidTrustPolicy) -> Unit,
    onFdroidAntiFeaturePolicyInputChange: (FdroidAntiFeaturePolicy) -> Unit,
    onFdroidRepoScopeIdInputChange: (String) -> Unit,
    onFdroidAppSearchQueryInputChange: (String) -> Unit,
    onSearchFdroidAppsByName: () -> Unit,
    onScanFdroidReposFromPackage: () -> Unit,
    onRetryFdroidSearchFailures: () -> Unit,
    onFdroidSearchFailuresExpandedChange: (Boolean) -> Unit,
    onFdroidAppSearchCandidateSelected: (FdroidAppSearchCandidate) -> Unit,
    onSourceModeDropdownExpandedChange: (Boolean) -> Unit,
    onUpdateIntervalDropdownExpandedChange: (Boolean) -> Unit,
    onActionsIntervalDropdownExpandedChange: (Boolean) -> Unit,
    onPreciseModeDropdownExpandedChange: (Boolean) -> Unit,
    onIgnoreModeDropdownExpandedChange: (Boolean) -> Unit,
    onFdroidVersionSelectionDropdownExpandedChange: (Boolean) -> Unit,
    onFdroidTrustPolicyDropdownExpandedChange: (Boolean) -> Unit,
    onFdroidAntiFeaturePolicyDropdownExpandedChange: (Boolean) -> Unit,
    onFdroidRepoScopeDropdownExpandedChange: (Boolean) -> Unit,
) {
    val editingFdroidConfig = editingTrackedItem?.fdroidConfig ?: FdroidTrackedAppConfig()
    val fdroidConfigChanged =
        sourceModeInput == GitHubTrackedSourceMode.FdroidRepository &&
            (
                fdroidVersionSelectionModeInput != editingFdroidConfig.selectionMode ||
                    fdroidVersionNameRegexInput.trim() != editingFdroidConfig.versionNameRegex.trim() ||
                    fdroidApkNameRegexInput.trim() != editingFdroidConfig.apkNameRegex.trim() ||
                    fdroidTrustPolicyInput != editingFdroidConfig.trustPolicy ||
                    fdroidAntiFeaturePolicyInput != editingFdroidConfig.antiFeaturePolicy
                )
    val hasUnsavedChanges =
        hasGitHubTrackEditorUnsavedChanges(
            editingTrackedItem = editingTrackedItem,
            repoUrlInput = repoUrlInput,
            packageNameInput = packageNameInput,
            selectedApp = selectedApp,
            appSearch = appSearch,
            pickerExpanded = pickerExpanded,
            repoScanCandidates = repoScanCandidates,
            sourceModeInput = sourceModeInput,
            preferPreReleaseInput = preferPreReleaseInput,
            alwaysShowLatestReleaseDownloadButtonInput = alwaysShowLatestReleaseDownloadButtonInput,
            checkActionsUpdatesInput = checkActionsUpdatesInput,
            externalBuildUntilReleaseInput = externalBuildUntilReleaseInput,
            updateIntervalModeInput = updateIntervalModeInput,
            actionsUpdateIntervalModeInput = actionsUpdateIntervalModeInput,
            preciseApkVersionModeInput = preciseApkVersionModeInput,
            ignoreModeInput = ignoreModeInput,
            ignoredStableReleaseKeyInput = ignoredStableReleaseKeyInput,
            ignoredPreReleaseKeyInput = ignoredPreReleaseKeyInput,
            fdroidConfigChanged = fdroidConfigChanged,
        )
    val dismissHandler =
        rememberUnsavedSheetDismissHandler(
            hasUnsavedChanges = hasUnsavedChanges,
            onDismissRequest = onDismissRequest,
        )
    SnapshotWindowBottomSheet(
        show = show,
        preferExportedBackdrop = true,
        title =
            if (editingTrackedItem == null) {
                stringResource(R.string.github_track_sheet_title_add)
            } else {
                stringResource(R.string.github_track_sheet_title_edit)
            },
        onDismissRequest = dismissHandler.requestDismiss,
        allowDismiss = dismissHandler.allowDismiss,
        onBlockedDismissRequest = dismissHandler.requestDismiss,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = dismissHandler.requestDismiss,
            )
        },
        endAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideConfirmIcon(),
                contentDescription =
                    if (editingTrackedItem == null) {
                        stringResource(R.string.github_track_sheet_cd_confirm_add)
                    } else {
                        stringResource(R.string.github_track_sheet_cd_confirm_save)
                    },
                onClick = onApply,
            )
        },
    ) {
        val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
        AnimatedContent(
            targetState = pickerExpanded,
            transitionSpec = {
                if (transitionAnimationsEnabled) {
                    (
                        fadeIn(animationSpec = tween(durationMillis = AppMotionTokens.expandFadeInMs)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = AppMotionTokens.expandFadeOutMs))
                    ).using(
                        SizeTransform(clip = false) { _, _ ->
                            tween(durationMillis = AppMotionTokens.expandSizeInMs)
                        },
                    )
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "github_track_app_picker_content",
            modifier = Modifier.fillMaxWidth(),
        ) { expanded ->
            if (expanded) {
                GitHubTrackAppPickerContent(
                    backdrop = backdrop,
                    active = pickerExpanded,
                    appSearch = appSearch,
                    selectedApp = selectedApp,
                    appList = appList,
                    derivedState = appPickerDerivedState,
                    appPickerPreferences = appPickerPreferences,
                    trackedPackageNames = trackedPackageNames,
                    editingPackageName = editingTrackedItem?.packageName.orEmpty(),
                    appListRefreshing = appListRefreshing,
                    rememberAddPickerScroll = editingTrackedItem == null,
                    rememberedFirstVisibleItemIndex = addAppPickerRememberedFirstVisibleItemIndex,
                    rememberedFirstVisibleItemScrollOffset =
                    addAppPickerRememberedFirstVisibleItemScrollOffset,
                    onAppSearchChange = onAppSearchChange,
                    onPickerExpandedChange = onPickerExpandedChange,
                    onRefreshAppList = onRefreshAppList,
                    onRequestAppIcons = onRequestAppIcons,
                    onRequestAppPickerState = onRequestAppPickerState,
                    onAppPickerPreferencesChange = onAppPickerPreferencesChange,
                    onAddAppPickerScrollPositionChange = onAddAppPickerScrollPositionChange,
                    onSelectedAppChange = onSelectedAppChange,
                )
            } else {
                GitHubTrackEditFormContent(
                    backdrop = backdrop,
                    repoUrlInput = repoUrlInput,
                    repoScanCandidates = repoScanCandidates,
                    packageNameInput = packageNameInput,
                    repoUrlScanRunning = repoUrlScanRunning,
                    packageNameScanRunning = packageNameScanRunning,
                    selectedApp = selectedApp,
                    sourceModeInput = sourceModeInput,
                    preferPreReleaseInput = preferPreReleaseInput,
                    alwaysShowLatestReleaseDownloadButtonInput = alwaysShowLatestReleaseDownloadButtonInput,
                    checkActionsUpdatesInput = checkActionsUpdatesInput,
                    externalBuildUntilReleaseInput = externalBuildUntilReleaseInput,
                    updateIntervalModeInput = updateIntervalModeInput,
                    actionsUpdateIntervalModeInput = actionsUpdateIntervalModeInput,
                    preciseApkVersionModeInput = preciseApkVersionModeInput,
                    ignoreModeInput = ignoreModeInput,
                    sourceModeDropdownExpanded = sourceModeDropdownExpanded,
                    updateIntervalDropdownExpanded = updateIntervalDropdownExpanded,
                    actionsIntervalDropdownExpanded = actionsIntervalDropdownExpanded,
                    preciseModeDropdownExpanded = preciseModeDropdownExpanded,
                    ignoreModeDropdownExpanded = ignoreModeDropdownExpanded,
                    fdroidVersionSelectionModeInput = fdroidVersionSelectionModeInput,
                    fdroidVersionNameRegexInput = fdroidVersionNameRegexInput,
                    fdroidApkNameRegexInput = fdroidApkNameRegexInput,
                    fdroidTrustPolicyInput = fdroidTrustPolicyInput,
                    fdroidAntiFeaturePolicyInput = fdroidAntiFeaturePolicyInput,
                    fdroidRepoScopeIdInput = fdroidRepoScopeIdInput,
                    fdroidAppSearchQueryInput = fdroidAppSearchQueryInput,
                    fdroidAppSearchCandidates = fdroidAppSearchCandidates,
                    fdroidAppSearchFailures = fdroidAppSearchFailures,
                    fdroidAppSearchFailuresExpanded = fdroidAppSearchFailuresExpanded,
                    fdroidAppSearchRepoReports = fdroidAppSearchRepoReports,
                    fdroidSelectedCandidate = fdroidSelectedCandidate,
                    fdroidAppSearchRunning = fdroidAppSearchRunning,
                    enabledFdroidCommonRepos = enabledFdroidCommonRepos,
                    fdroidVersionSelectionDropdownExpanded =
                    fdroidVersionSelectionDropdownExpanded,
                    fdroidTrustPolicyDropdownExpanded = fdroidTrustPolicyDropdownExpanded,
                    fdroidAntiFeaturePolicyDropdownExpanded =
                    fdroidAntiFeaturePolicyDropdownExpanded,
                    fdroidRepoScopeDropdownExpanded = fdroidRepoScopeDropdownExpanded,
                    globalRefreshIntervalHours = globalRefreshIntervalHours,
                    globalPreciseApkVersionEnabled = globalPreciseApkVersionEnabled,
                    onRepoUrlInputChange = onRepoUrlInputChange,
                    onSourceModeInputChange = onSourceModeInputChange,
                    onPackageNameInputChange = onPackageNameInputChange,
                    onScanRepoUrl = onScanRepoUrl,
                    onScanPackageName = onScanPackageName,
                    onRepoScanCandidateSelected = onRepoScanCandidateSelected,
                    onPickerExpandedChange = onPickerExpandedChange,
                    onPreferPreReleaseInputChange = onPreferPreReleaseInputChange,
                    onAlwaysShowLatestReleaseDownloadButtonInputChange =
                    onAlwaysShowLatestReleaseDownloadButtonInputChange,
                    onCheckActionsUpdatesInputChange = onCheckActionsUpdatesInputChange,
                    onExternalBuildUntilReleaseInputChange = onExternalBuildUntilReleaseInputChange,
                    onUpdateIntervalModeInputChange = onUpdateIntervalModeInputChange,
                    onActionsUpdateIntervalModeInputChange =
                    onActionsUpdateIntervalModeInputChange,
                    onPreciseApkVersionModeInputChange = onPreciseApkVersionModeInputChange,
                    onIgnoreModeInputChange = onIgnoreModeInputChange,
                    onFdroidVersionSelectionModeInputChange =
                    onFdroidVersionSelectionModeInputChange,
                    onFdroidVersionNameRegexInputChange = onFdroidVersionNameRegexInputChange,
                    onFdroidApkNameRegexInputChange = onFdroidApkNameRegexInputChange,
                    onFdroidTrustPolicyInputChange = onFdroidTrustPolicyInputChange,
                    onFdroidAntiFeaturePolicyInputChange = onFdroidAntiFeaturePolicyInputChange,
                    onFdroidRepoScopeIdInputChange = onFdroidRepoScopeIdInputChange,
                    onFdroidAppSearchQueryInputChange = onFdroidAppSearchQueryInputChange,
                    onSearchFdroidAppsByName = onSearchFdroidAppsByName,
                    onScanFdroidReposFromPackage = onScanFdroidReposFromPackage,
                    onRetryFdroidSearchFailures = onRetryFdroidSearchFailures,
                    onFdroidSearchFailuresExpandedChange = onFdroidSearchFailuresExpandedChange,
                    onFdroidAppSearchCandidateSelected = onFdroidAppSearchCandidateSelected,
                    onSourceModeDropdownExpandedChange = onSourceModeDropdownExpandedChange,
                    onUpdateIntervalDropdownExpandedChange = onUpdateIntervalDropdownExpandedChange,
                    onActionsIntervalDropdownExpandedChange = onActionsIntervalDropdownExpandedChange,
                    onPreciseModeDropdownExpandedChange = onPreciseModeDropdownExpandedChange,
                    onIgnoreModeDropdownExpandedChange = onIgnoreModeDropdownExpandedChange,
                    onFdroidVersionSelectionDropdownExpandedChange =
                    onFdroidVersionSelectionDropdownExpandedChange,
                    onFdroidTrustPolicyDropdownExpandedChange =
                    onFdroidTrustPolicyDropdownExpandedChange,
                    onFdroidAntiFeaturePolicyDropdownExpandedChange =
                    onFdroidAntiFeaturePolicyDropdownExpandedChange,
                    onFdroidRepoScopeDropdownExpandedChange =
                    onFdroidRepoScopeDropdownExpandedChange,
                )
            }
        }
    }
    UnsavedSheetDismissConfirmDialog(
        show = dismissHandler.showConfirmDialog,
        onKeepEditing = dismissHandler.keepEditing,
        onDiscardChanges = dismissHandler.discardChanges,
    )
}
