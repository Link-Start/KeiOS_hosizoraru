package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import os.kei.feature.github.model.InstalledAppItem

@Stable
internal class GitHubTrackEditorPageStateHolder {
    var repoUrlInput by mutableStateOf("")
    var packageNameInput by mutableStateOf("")
    var repoScanCandidates by mutableStateOf<List<GitHubPackageRepositoryScanCandidate>>(emptyList())
    var appSearch by mutableStateOf("")
    var addTrackAppPickerFirstVisibleItemIndex by mutableIntStateOf(0)
    var addTrackAppPickerFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    var pickerExpanded by mutableStateOf(false)
    var preferPreReleaseInput by mutableStateOf(false)
    var alwaysShowLatestReleaseDownloadButtonInput by mutableStateOf(false)
    var checkActionsUpdatesInput by mutableStateOf(false)
    var updateIntervalModeInput by mutableStateOf(GitHubTrackedUpdateIntervalMode.FollowGlobal)
    var actionsUpdateIntervalModeInput by mutableStateOf(
        GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
    )
    var preciseApkVersionModeInput by mutableStateOf(GitHubTrackedPreciseApkVersionMode.FollowGlobal)
    var ignoreModeInput by mutableStateOf(GitHubTrackedIgnoreMode.None)
    var ignoredStableReleaseKeyInput by mutableStateOf("")
    var ignoredPreReleaseKeyInput by mutableStateOf("")
    var trackSourceModeInput by mutableStateOf(GitHubTrackedSourceMode.GitHubRepository)
    var repoUrlScanRunning by mutableStateOf(false)
    var packageNameScanRunning by mutableStateOf(false)
    var selectedApp by mutableStateOf<InstalledAppItem?>(null)
    var appList by mutableStateOf<List<InstalledAppItem>>(emptyList())
    var appListLoaded by mutableStateOf(false)
    var appListRefreshing by mutableStateOf(false)
    var sourceModeDropdownExpanded by mutableStateOf(false)
    var sourceModeDropdownAnchorBounds by mutableStateOf<IntRect?>(null)
    var updateIntervalDropdownExpanded by mutableStateOf(false)
    var updateIntervalDropdownAnchorBounds by mutableStateOf<IntRect?>(null)
    var actionsIntervalDropdownExpanded by mutableStateOf(false)
    var actionsIntervalDropdownAnchorBounds by mutableStateOf<IntRect?>(null)
    var preciseModeDropdownExpanded by mutableStateOf(false)
    var preciseModeDropdownAnchorBounds by mutableStateOf<IntRect?>(null)
    var ignoreModeDropdownExpanded by mutableStateOf(false)
    var ignoreModeDropdownAnchorBounds by mutableStateOf<IntRect?>(null)

    fun reset() {
        repoUrlInput = ""
        packageNameInput = ""
        repoScanCandidates = emptyList()
        selectedApp = null
        appSearch = ""
        pickerExpanded = false
        preferPreReleaseInput = false
        alwaysShowLatestReleaseDownloadButtonInput = false
        checkActionsUpdatesInput = false
        updateIntervalModeInput = GitHubTrackedUpdateIntervalMode.FollowGlobal
        actionsUpdateIntervalModeInput = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
        preciseApkVersionModeInput = GitHubTrackedPreciseApkVersionMode.FollowGlobal
        ignoreModeInput = GitHubTrackedIgnoreMode.None
        ignoredStableReleaseKeyInput = ""
        ignoredPreReleaseKeyInput = ""
        trackSourceModeInput = GitHubTrackedSourceMode.GitHubRepository
        repoUrlScanRunning = false
        packageNameScanRunning = false
        resetDropdownState()
    }

    fun resetDropdownState() {
        sourceModeDropdownExpanded = false
        sourceModeDropdownAnchorBounds = null
        updateIntervalDropdownExpanded = false
        updateIntervalDropdownAnchorBounds = null
        actionsIntervalDropdownExpanded = false
        actionsIntervalDropdownAnchorBounds = null
        preciseModeDropdownExpanded = false
        preciseModeDropdownAnchorBounds = null
        ignoreModeDropdownExpanded = false
        ignoreModeDropdownAnchorBounds = null
    }
}
