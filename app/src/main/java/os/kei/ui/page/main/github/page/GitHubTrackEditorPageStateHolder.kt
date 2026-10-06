package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import os.kei.feature.github.model.FdroidAppSearchCandidate
import os.kei.feature.github.model.FdroidAppSearchFailure
import os.kei.feature.github.model.FdroidAppSearchRepoReport
import os.kei.feature.github.model.FdroidAntiFeaturePolicy
import os.kei.feature.github.model.FdroidRepositoryPresets
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.model.FdroidVersionSelectionMode
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
    var externalBuildUntilReleaseInput by mutableStateOf(false)
    var updateIntervalModeInput by mutableStateOf(GitHubTrackedUpdateIntervalMode.FollowGlobal)
    var actionsUpdateIntervalModeInput by mutableStateOf(
        GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
    )
    var preciseApkVersionModeInput by mutableStateOf(GitHubTrackedPreciseApkVersionMode.FollowGlobal)
    var ignoreModeInput by mutableStateOf(GitHubTrackedIgnoreMode.None)
    var ignoredStableReleaseKeyInput by mutableStateOf("")
    var ignoredPreReleaseKeyInput by mutableStateOf("")
    var fdroidVersionSelectionModeInput by mutableStateOf(FdroidVersionSelectionMode.SuggestedVersionCode)
    var fdroidVersionNameRegexInput by mutableStateOf("")
    var fdroidApkNameRegexInput by mutableStateOf("")
    var fdroidTrustPolicyInput by mutableStateOf(FdroidTrustPolicy.TrackOnlyWarn)
    var fdroidAntiFeaturePolicyInput by mutableStateOf(FdroidAntiFeaturePolicy.ShowAndWarn)
    var fdroidRepoScopeIdInput by mutableStateOf(FdroidRepositoryPresets.COMMON_ID)
    var fdroidAppSearchQueryInput by mutableStateOf("")
    var fdroidAppSearchCandidates by mutableStateOf<List<FdroidAppSearchCandidate>>(emptyList())
    var fdroidAppSearchFailures by mutableStateOf<List<FdroidAppSearchFailure>>(emptyList())
    var fdroidAppSearchFailuresExpanded by mutableStateOf(false)
    var fdroidAppSearchRepoReports by mutableStateOf<List<FdroidAppSearchRepoReport>>(emptyList())
    var fdroidSelectedCandidate by mutableStateOf<FdroidAppSearchCandidate?>(null)
    var fdroidAppSearchRunning by mutableStateOf(false)
    var trackSourceModeInput by mutableStateOf(GitHubTrackedSourceMode.GitHubRepository)
    var repoUrlScanRunning by mutableStateOf(false)
    var packageNameScanRunning by mutableStateOf(false)
    var selectedApp by mutableStateOf<InstalledAppItem?>(null)
    var appList by mutableStateOf<List<InstalledAppItem>>(emptyList())
    var appListLoaded by mutableStateOf(false)
    var appListRefreshing by mutableStateOf(false)
    var sourceModeDropdownExpanded by mutableStateOf(false)
    var updateIntervalDropdownExpanded by mutableStateOf(false)
    var actionsIntervalDropdownExpanded by mutableStateOf(false)
    var preciseModeDropdownExpanded by mutableStateOf(false)
    var ignoreModeDropdownExpanded by mutableStateOf(false)
    var fdroidVersionSelectionDropdownExpanded by mutableStateOf(false)
    var fdroidTrustPolicyDropdownExpanded by mutableStateOf(false)
    var fdroidAntiFeaturePolicyDropdownExpanded by mutableStateOf(false)
    var fdroidRepoScopeDropdownExpanded by mutableStateOf(false)

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
        externalBuildUntilReleaseInput = false
        updateIntervalModeInput = GitHubTrackedUpdateIntervalMode.FollowGlobal
        actionsUpdateIntervalModeInput = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
        preciseApkVersionModeInput = GitHubTrackedPreciseApkVersionMode.FollowGlobal
        ignoreModeInput = GitHubTrackedIgnoreMode.None
        ignoredStableReleaseKeyInput = ""
        ignoredPreReleaseKeyInput = ""
        fdroidVersionSelectionModeInput = FdroidVersionSelectionMode.SuggestedVersionCode
        fdroidVersionNameRegexInput = ""
        fdroidApkNameRegexInput = ""
        fdroidTrustPolicyInput = FdroidTrustPolicy.TrackOnlyWarn
        fdroidAntiFeaturePolicyInput = FdroidAntiFeaturePolicy.ShowAndWarn
        fdroidRepoScopeIdInput = FdroidRepositoryPresets.COMMON_ID
        fdroidAppSearchQueryInput = ""
        fdroidAppSearchCandidates = emptyList()
        fdroidAppSearchFailures = emptyList()
        fdroidAppSearchFailuresExpanded = false
        fdroidAppSearchRepoReports = emptyList()
        fdroidSelectedCandidate = null
        fdroidAppSearchRunning = false
        trackSourceModeInput = GitHubTrackedSourceMode.GitHubRepository
        repoUrlScanRunning = false
        packageNameScanRunning = false
        resetDropdownState()
    }

    fun resetDropdownState() {
        sourceModeDropdownExpanded = false
        updateIntervalDropdownExpanded = false
        actionsIntervalDropdownExpanded = false
        preciseModeDropdownExpanded = false
        ignoreModeDropdownExpanded = false
        fdroidVersionSelectionDropdownExpanded = false
        fdroidTrustPolicyDropdownExpanded = false
        fdroidAntiFeaturePolicyDropdownExpanded = false
        fdroidRepoScopeDropdownExpanded = false
    }
}
