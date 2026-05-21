package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import os.kei.feature.github.model.GitHubApiCredentialStatus
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubStrategyBenchmarkReport

@Stable
internal class GitHubStrategyPageStateHolder {
    var lookupConfig by mutableStateOf(GitHubLookupConfig())
    var selectedStrategyInput by mutableStateOf(lookupConfig.selectedStrategy)
    var selectedActionsStrategyInput by mutableStateOf(lookupConfig.actionsStrategy)
    var githubApiTokenInput by mutableStateOf("")
    var checkAllTrackedPreReleasesInput by mutableStateOf(false)
    var checkAllDirectApkPreReleasesInput by mutableStateOf(false)
    var aggressiveApkFilteringInput by mutableStateOf(false)
    var preciseApkVersionEnabledInput by mutableStateOf(false)
    var scanSystemAppsByDefaultInput by mutableStateOf(false)
    var profileDepthInput by mutableStateOf(GitHubProfileDepth.Basic)
    var shareImportFlowModeInput by mutableStateOf(lookupConfig.shareImportFlowMode)
    var appManagedShareInstallEnabledInput by mutableStateOf(false)
    var onlineShareTargetPackageInput by mutableStateOf("")
    var preferredDownloaderPackageInput by mutableStateOf("")
    var decisionAssistEnabledInput by mutableStateOf(false)
    var repositoryHealthCardEnabledInput by mutableStateOf(false)
    var apkTrustCheckEnabledInput by mutableStateOf(false)
    var showApiTokenPlainText by mutableStateOf(false)
    var showShareImportFlowModePopup by mutableStateOf(false)
    var strategyBenchmarkRunning by mutableStateOf(false)
    var strategyBenchmarkError by mutableStateOf<String?>(null)
    var strategyBenchmarkReport by mutableStateOf<GitHubStrategyBenchmarkReport?>(null)
    var credentialCheckRunning by mutableStateOf(false)
    var credentialCheckError by mutableStateOf<String?>(null)
    var credentialCheckStatus by mutableStateOf<GitHubApiCredentialStatus?>(null)
    var recommendedTokenGuideExpanded by mutableStateOf(false)
    var assetSourceSignature by mutableStateOf("")

    fun dismissStrategySheet() {
        showApiTokenPlainText = false
        credentialCheckRunning = false
        recommendedTokenGuideExpanded = false
    }
}
