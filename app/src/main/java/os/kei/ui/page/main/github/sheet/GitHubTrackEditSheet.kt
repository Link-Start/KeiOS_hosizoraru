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
import androidx.compose.ui.unit.IntRect
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.local.GitHubAppPickerPreferences
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
    backdrop: LayerBackdrop,
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
    updateIntervalModeInput: GitHubTrackedUpdateIntervalMode,
    actionsUpdateIntervalModeInput: GitHubTrackedActionsUpdateIntervalMode,
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode,
    ignoreModeInput: GitHubTrackedIgnoreMode,
    ignoredStableReleaseKeyInput: String,
    ignoredPreReleaseKeyInput: String,
    sourceModeDropdownExpanded: Boolean,
    sourceModeDropdownAnchorBounds: IntRect?,
    updateIntervalDropdownExpanded: Boolean,
    updateIntervalDropdownAnchorBounds: IntRect?,
    actionsIntervalDropdownExpanded: Boolean,
    actionsIntervalDropdownAnchorBounds: IntRect?,
    preciseModeDropdownExpanded: Boolean,
    preciseModeDropdownAnchorBounds: IntRect?,
    ignoreModeDropdownExpanded: Boolean,
    ignoreModeDropdownAnchorBounds: IntRect?,
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
    onRequestAppPickerState: (GitHubTrackAppPickerInput) -> Unit,
    onAppPickerPreferencesChange: (GitHubAppPickerPreferences) -> Unit,
    onAddAppPickerScrollPositionChange: (Int, Int) -> Unit,
    onSelectedAppChange: (InstalledAppItem?) -> Unit,
    onPreferPreReleaseInputChange: (Boolean) -> Unit,
    onAlwaysShowLatestReleaseDownloadButtonInputChange: (Boolean) -> Unit,
    onCheckActionsUpdatesInputChange: (Boolean) -> Unit,
    onUpdateIntervalModeInputChange: (GitHubTrackedUpdateIntervalMode) -> Unit,
    onActionsUpdateIntervalModeInputChange: (GitHubTrackedActionsUpdateIntervalMode) -> Unit,
    onPreciseApkVersionModeInputChange: (GitHubTrackedPreciseApkVersionMode) -> Unit,
    onIgnoreModeInputChange: (GitHubTrackedIgnoreMode) -> Unit,
    onSourceModeDropdownExpandedChange: (Boolean) -> Unit,
    onSourceModeDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    onUpdateIntervalDropdownExpandedChange: (Boolean) -> Unit,
    onUpdateIntervalDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    onActionsIntervalDropdownExpandedChange: (Boolean) -> Unit,
    onActionsIntervalDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    onPreciseModeDropdownExpandedChange: (Boolean) -> Unit,
    onPreciseModeDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    onIgnoreModeDropdownExpandedChange: (Boolean) -> Unit,
    onIgnoreModeDropdownAnchorBoundsChange: (IntRect?) -> Unit,
) {
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
            updateIntervalModeInput = updateIntervalModeInput,
            actionsUpdateIntervalModeInput = actionsUpdateIntervalModeInput,
            preciseApkVersionModeInput = preciseApkVersionModeInput,
            ignoreModeInput = ignoreModeInput,
            ignoredStableReleaseKeyInput = ignoredStableReleaseKeyInput,
            ignoredPreReleaseKeyInput = ignoredPreReleaseKeyInput,
        )
    val dismissHandler =
        rememberUnsavedSheetDismissHandler(
            hasUnsavedChanges = hasUnsavedChanges,
            onDismissRequest = onDismissRequest,
        )
    SnapshotWindowBottomSheet(
        show = show,
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
                    updateIntervalModeInput = updateIntervalModeInput,
                    actionsUpdateIntervalModeInput = actionsUpdateIntervalModeInput,
                    preciseApkVersionModeInput = preciseApkVersionModeInput,
                    ignoreModeInput = ignoreModeInput,
                    sourceModeDropdownExpanded = sourceModeDropdownExpanded,
                    sourceModeDropdownAnchorBounds = sourceModeDropdownAnchorBounds,
                    updateIntervalDropdownExpanded = updateIntervalDropdownExpanded,
                    updateIntervalDropdownAnchorBounds = updateIntervalDropdownAnchorBounds,
                    actionsIntervalDropdownExpanded = actionsIntervalDropdownExpanded,
                    actionsIntervalDropdownAnchorBounds = actionsIntervalDropdownAnchorBounds,
                    preciseModeDropdownExpanded = preciseModeDropdownExpanded,
                    preciseModeDropdownAnchorBounds = preciseModeDropdownAnchorBounds,
                    ignoreModeDropdownExpanded = ignoreModeDropdownExpanded,
                    ignoreModeDropdownAnchorBounds = ignoreModeDropdownAnchorBounds,
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
                    onUpdateIntervalModeInputChange = onUpdateIntervalModeInputChange,
                    onActionsUpdateIntervalModeInputChange =
                    onActionsUpdateIntervalModeInputChange,
                    onPreciseApkVersionModeInputChange = onPreciseApkVersionModeInputChange,
                    onIgnoreModeInputChange = onIgnoreModeInputChange,
                    onSourceModeDropdownExpandedChange = onSourceModeDropdownExpandedChange,
                    onSourceModeDropdownAnchorBoundsChange = onSourceModeDropdownAnchorBoundsChange,
                    onUpdateIntervalDropdownExpandedChange = onUpdateIntervalDropdownExpandedChange,
                    onUpdateIntervalDropdownAnchorBoundsChange = onUpdateIntervalDropdownAnchorBoundsChange,
                    onActionsIntervalDropdownExpandedChange = onActionsIntervalDropdownExpandedChange,
                    onActionsIntervalDropdownAnchorBoundsChange =
                    onActionsIntervalDropdownAnchorBoundsChange,
                    onPreciseModeDropdownExpandedChange = onPreciseModeDropdownExpandedChange,
                    onPreciseModeDropdownAnchorBoundsChange = onPreciseModeDropdownAnchorBoundsChange,
                    onIgnoreModeDropdownExpandedChange = onIgnoreModeDropdownExpandedChange,
                    onIgnoreModeDropdownAnchorBoundsChange = onIgnoreModeDropdownAnchorBoundsChange,
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
