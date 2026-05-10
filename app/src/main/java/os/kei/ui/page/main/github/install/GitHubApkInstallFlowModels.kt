package os.kei.ui.page.main.github.install

import os.kei.core.install.ApkInstallBackendId
import os.kei.core.install.ApkInstallFailureReason
import os.kei.core.install.LocalApkArchiveInfo
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkTrustSignal
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo

internal enum class GitHubApkInstallSourceKind {
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

internal data class GitHubApkInstallRequestContext(
    val sourceKind: GitHubApkInstallSourceKind,
    val owner: String = "",
    val repo: String = "",
    val releaseTag: String = "",
    val sourceLabel: String = "",
    val expectedPackageName: String = "",
    val externalUrl: String = "",
    val externalFileName: String = ""
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
    val localArchiveInfo: LocalApkArchiveInfo? = null,
    val installedPackageInfo: GitHubInstalledPackageInfo? = null,
    val trustSignal: GitHubApkTrustSignal? = null,
    val backendId: ApkInstallBackendId? = null,
    val failureReason: ApkInstallFailureReason? = null,
    val progress: Float = 0f,
    val bytesDone: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String = "",
    val sheetVisible: Boolean = false,
    val notificationFirst: Boolean = false
) {
    val active: Boolean
        get() = phase != GitHubApkInstallPhase.Idle &&
                phase != GitHubApkInstallPhase.Cancelled

    val needsUserDecision: Boolean
        get() = phase == GitHubApkInstallPhase.SelectingApk ||
                phase == GitHubApkInstallPhase.ReadyToInstall ||
                phase == GitHubApkInstallPhase.PendingUserAction ||
                phase == GitHubApkInstallPhase.Failed ||
                phase == GitHubApkInstallPhase.Success

    val packageName: String
        get() = localArchiveInfo?.packageName.orEmpty()

    val trustLevel: GitHubDecisionLevel?
        get() = trustSignal?.level
}
