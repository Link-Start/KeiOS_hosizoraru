package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import os.kei.feature.github.model.GitHubPackageRepositoryScanCandidate
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
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
    var trackSourceModeInput by mutableStateOf(GitHubTrackedSourceMode.GitHubRepository)
    var repoUrlScanRunning by mutableStateOf(false)
    var packageNameScanRunning by mutableStateOf(false)
    var selectedApp by mutableStateOf<InstalledAppItem?>(null)
    var appList by mutableStateOf<List<InstalledAppItem>>(emptyList())
    var appListLoaded by mutableStateOf(false)
    var appListRefreshing by mutableStateOf(false)

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
        trackSourceModeInput = GitHubTrackedSourceMode.GitHubRepository
        repoUrlScanRunning = false
        packageNameScanRunning = false
    }
}
