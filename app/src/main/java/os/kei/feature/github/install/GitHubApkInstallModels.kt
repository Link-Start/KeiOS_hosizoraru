package os.kei.feature.github.install

import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig

data class GitHubApkInstallRequest(
    val owner: String,
    val repo: String,
    val releaseTag: String,
    val projectUrl: String,
    val asset: GitHubReleaseAssetFile,
    val lookupConfig: GitHubLookupConfig,
    val targetDisplayName: String = "",
    val scannedPackageName: String = "",
    val resolvedDownloadUrl: String = "",
    val requestId: String = GitHubApkInstallRequestIds.newId(),
    val startedAtMillis: Long = System.currentTimeMillis()
)

enum class GitHubApkInstallStage {
    Preparing,
    Downloading,
    Staging,
    Committing,
    Succeeded,
    Failed,
    Cancelled
}

data class GitHubApkInstallProgress(
    val stage: GitHubApkInstallStage,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val sessionId: Int = -1
) {
    val boundedProgressPercent: Int
        get() = progressPercent.coerceIn(0, 100)
}

sealed interface GitHubApkInstallResult {
    data class Succeeded(
        val requestId: String,
        val sessionId: Int,
        val packageName: String,
        val appLabel: String = "",
        val firstInstallTimeMs: Long = -1L
    ) : GitHubApkInstallResult

    data class Failed(
        val reason: GitHubApkInstallFailureReason,
        val message: String,
        val sessionId: Int = -1,
        val statusCode: Int = -1,
        val legacyStatus: Int = Int.MIN_VALUE,
        val packageName: String = ""
    ) : GitHubApkInstallResult

    data class Cancelled(
        val requestId: String,
        val sessionId: Int = -1
    ) : GitHubApkInstallResult
}

enum class GitHubApkInstallFailureReason {
    ShizukuUnavailable,
    ShizukuPermissionMissing,
    RemoteInstallPermissionMissing,
    DownloadUrlInvalid,
    DownloadFailed,
    SessionCreateFailed,
    SessionOpenFailed,
    SessionWriteFailed,
    CommitFailed,
    ResultTimeout,
    PackageNameMissing,
    Unknown
}

internal object GitHubApkInstallRequestIds {
    fun newId(): String {
        return "github-apk-${System.currentTimeMillis()}-${randomSuffix()}"
    }

    private fun randomSuffix(): String {
        return java.util.UUID.randomUUID().toString().substring(0, 8)
    }
}
