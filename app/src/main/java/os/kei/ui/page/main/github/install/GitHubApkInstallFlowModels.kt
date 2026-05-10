package os.kei.ui.page.main.github.install

import os.kei.core.install.ApkInstallBackendId
import os.kei.core.install.ApkInstallFailureReason
import os.kei.core.install.LocalApkArchiveInfo
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkTrustSignal
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo

internal enum class GitHubApkInstallSourceKind {
    TrackedReleaseAsset,
    ReleaseAsset,
    ShareImport,
    ActionsArtifact
}

internal enum class GitHubApkInstallPhase {
    Idle,
    Downloading,
    SelectingApk,
    Inspecting,
    ReadyToInstall,
    Installing,
    PendingUserAction,
    Success,
    Failed,
    Cancelled
}

internal enum class GitHubApkInstallProgressKind {
    None,
    Download,
    Inspect,
    Staging,
    Commit,
    Waiting
}

internal data class GitHubApkInstallRequestContext(
    val sourceKind: GitHubApkInstallSourceKind,
    val owner: String = "",
    val repo: String = "",
    val releaseTag: String = "",
    val sourceLabel: String = "",
    val expectedPackageName: String = "",
    val externalUrl: String = "",
    val externalFileName: String = "",
    val remoteManifestInfo: GitHubApkManifestInfo? = null
) {
    val displayLabel: String
        get() = sourceLabel.ifBlank {
            when {
                owner.isNotBlank() && repo.isNotBlank() -> "$owner/$repo"
                repo.isNotBlank() -> repo
                owner.isNotBlank() -> owner
                else -> "GitHub"
            }
        }
}

internal data class GitHubApkInstallCandidate(
    val index: Int,
    val name: String,
    val sizeBytes: Long
)

internal data class GitHubApkInstallFlowState(
    val sessionId: Long = 0L,
    val phase: GitHubApkInstallPhase = GitHubApkInstallPhase.Idle,
    val request: GitHubApkInstallRequestContext = GitHubApkInstallRequestContext(
        sourceKind = GitHubApkInstallSourceKind.ReleaseAsset
    ),
    val asset: GitHubReleaseAssetFile? = null,
    val candidates: List<GitHubApkInstallCandidate> = emptyList(),
    val selectedCandidateName: String = "",
    val selectedCandidateSizeBytes: Long = 0L,
    val remoteManifestInfo: GitHubApkManifestInfo? = null,
    val localArchiveInfo: LocalApkArchiveInfo? = null,
    val installedPackageInfo: GitHubInstalledPackageInfo? = null,
    val trustSignal: GitHubApkTrustSignal? = null,
    val backendId: ApkInstallBackendId? = null,
    val failureReason: ApkInstallFailureReason? = null,
    val progress: Float = 0f,
    val stageProgress: Float = progress,
    val overallProgress: Float = progress,
    val progressKind: GitHubApkInstallProgressKind = GitHubApkInstallProgressKind.None,
    val bytesDone: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String = "",
    val rawMessage: String = "",
    val sheetVisible: Boolean = false,
    val notificationFirst: Boolean = false
) {
    val active: Boolean
        get() = phase != GitHubApkInstallPhase.Idle

    val needsUserDecision: Boolean
        get() = phase == GitHubApkInstallPhase.SelectingApk ||
                phase == GitHubApkInstallPhase.ReadyToInstall ||
                phase == GitHubApkInstallPhase.PendingUserAction ||
                phase == GitHubApkInstallPhase.Failed ||
                phase == GitHubApkInstallPhase.Success ||
                phase == GitHubApkInstallPhase.Cancelled

    val cancellable: Boolean
        get() = phase == GitHubApkInstallPhase.Downloading ||
                phase == GitHubApkInstallPhase.SelectingApk ||
                phase == GitHubApkInstallPhase.Inspecting ||
                phase == GitHubApkInstallPhase.ReadyToInstall ||
                phase == GitHubApkInstallPhase.Installing ||
                phase == GitHubApkInstallPhase.PendingUserAction

    val overallProgressPercent: Int
        get() = (overallProgress.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)

    val stageProgressPercent: Int
        get() = (stageProgress.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)

    val showsDeterminateDownloadProgress: Boolean
        get() = phase == GitHubApkInstallPhase.Downloading &&
                progressKind == GitHubApkInstallProgressKind.Download

    val packageName: String
        get() = localArchiveInfo?.packageName.orEmpty()
            .ifBlank { remoteManifestInfo?.packageName.orEmpty() }
            .ifBlank { request.expectedPackageName }

    val trustLevel: GitHubDecisionLevel?
        get() = trustSignal?.level
}
