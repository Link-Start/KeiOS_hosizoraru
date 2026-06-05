package os.kei.ui.page.main.github.sheet

import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import os.kei.feature.github.model.InstalledAppItem

internal fun hasGitHubTrackEditorUnsavedChanges(
    editingTrackedItem: GitHubTrackedApp?,
    repoUrlInput: String,
    packageNameInput: String,
    selectedApp: InstalledAppItem?,
    appSearch: String,
    pickerExpanded: Boolean,
    repoScanCandidates: List<GitHubPackageRepositoryScanCandidate>,
    sourceModeInput: GitHubTrackedSourceMode,
    preferPreReleaseInput: Boolean,
    alwaysShowLatestReleaseDownloadButtonInput: Boolean,
    checkActionsUpdatesInput: Boolean,
    updateIntervalModeInput: GitHubTrackedUpdateIntervalMode,
    actionsUpdateIntervalModeInput: GitHubTrackedActionsUpdateIntervalMode,
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode,
    ignoreModeInput: GitHubTrackedIgnoreMode,
    ignoredStableReleaseKeyInput: String,
    ignoredPreReleaseKeyInput: String
): Boolean {
    val repoUrl = repoUrlInput.trim()
    val packageName = packageNameInput.trim()
    return editingTrackedItem?.let { item ->
        repoUrl != item.repoUrl.trim() ||
                packageName != item.packageName.trim() ||
                sourceModeInput != item.sourceMode ||
                preferPreReleaseInput != item.preferPreRelease ||
                alwaysShowLatestReleaseDownloadButtonInput != item.alwaysShowLatestReleaseDownloadButton ||
                checkActionsUpdatesInput != item.checkActionsUpdates ||
                updateIntervalModeInput != item.updateIntervalMode ||
                actionsUpdateIntervalModeInput != item.actionsUpdateIntervalMode ||
                preciseApkVersionModeInput != item.preciseApkVersionMode ||
                ignoreModeInput != item.ignoreMode ||
                ignoredStableReleaseKeyInput.trim() != item.ignoredStableReleaseKey.trim() ||
                ignoredPreReleaseKeyInput.trim() != item.ignoredPreReleaseKey.trim()
    } ?: (
            repoUrl.isNotBlank() ||
                    packageName.isNotBlank() ||
                    selectedApp != null ||
                    appSearch.trim().isNotBlank() ||
                    pickerExpanded ||
                    repoScanCandidates.isNotEmpty() ||
                    sourceModeInput != GitHubTrackedSourceMode.GitHubRepository ||
                    preferPreReleaseInput ||
                    alwaysShowLatestReleaseDownloadButtonInput ||
                    checkActionsUpdatesInput ||
                    updateIntervalModeInput != GitHubTrackedUpdateIntervalMode.FollowGlobal ||
                    actionsUpdateIntervalModeInput !=
                    GitHubTrackedActionsUpdateIntervalMode.FollowGlobal ||
                    preciseApkVersionModeInput != GitHubTrackedPreciseApkVersionMode.FollowGlobal ||
                    ignoreModeInput != GitHubTrackedIgnoreMode.None ||
                    ignoredStableReleaseKeyInput.trim().isNotBlank() ||
                    ignoredPreReleaseKeyInput.trim().isNotBlank()
            )
}
