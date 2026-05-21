package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseNotesTarget
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo

@Stable
internal class GitHubAssetPageStateHolder {
    val apkAssetBundles = mutableStateMapOf<String, GitHubReleaseAssetBundle>()
    val apkAssetLoading = mutableStateMapOf<String, Boolean>()
    val apkAssetErrors = mutableStateMapOf<String, String>()
    val apkAssetExpanded = mutableStateMapOf<String, Boolean>()
    val apkAssetIncludeAll = mutableStateMapOf<String, Boolean>()
    val releaseNotesLoading = mutableStateMapOf<String, Boolean>()
    val releaseNotesErrors = mutableStateMapOf<String, String>()
    val releaseNotesTargets = mutableStateMapOf<String, List<GitHubReleaseNotesTarget>>()
    val releaseNotesSelectedTargets = mutableStateMapOf<String, GitHubReleaseNotesTarget>()
    val releaseNotesBundles = mutableStateMapOf<String, GitHubReleaseAssetBundle>()
    val releaseNotesApkVersions = mutableStateMapOf<String, GitHubRemoteApkVersionInfo>()
    val apkAssetBundleLoadedAtMs = mutableStateMapOf<String, Long>()
    val releaseNotesTargetsLoadedAtMs = mutableStateMapOf<String, Long>()
    val releaseNotesBundleLoadedAtMs = mutableStateMapOf<String, Long>()
    val apkInfoLoading = mutableStateMapOf<String, Boolean>()
    val apkInfoErrors = mutableStateMapOf<String, String>()
    val apkInfoResults = mutableStateMapOf<String, GitHubApkManifestInfo>()
    val apkInfoInstalledResults = mutableStateMapOf<String, GitHubInstalledPackageInfo?>()
}
