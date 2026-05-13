package os.kei.ui.page.main.github.sheet

import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
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
    actionsUpdateIntervalModeInput: GitHubTrackedActionsUpdateIntervalMode,
    preciseApkVersionModeInput: GitHubTrackedPreciseApkVersionMode
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
                actionsUpdateIntervalModeInput != item.actionsUpdateIntervalMode ||
                preciseApkVersionModeInput != item.preciseApkVersionMode
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
                    actionsUpdateIntervalModeInput !=
                    GitHubTrackedActionsUpdateIntervalMode.FollowGlobal ||
                    preciseApkVersionModeInput != GitHubTrackedPreciseApkVersionMode.FollowGlobal
            )
}
