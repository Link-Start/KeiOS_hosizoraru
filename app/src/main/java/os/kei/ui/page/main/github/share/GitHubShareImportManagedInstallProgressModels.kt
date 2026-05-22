package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Immutable
import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord

@Immutable
internal data class GitHubShareImportManagedInstallProgress(
    val phase: GitHubShareImportPhase,
    val assetName: String = "",
    val appLabel: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
    val nativeAbis: List<String> = emptyList(),
    val targetDisplayName: String = "",
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
) {
    val boundedProgressPercent: Int
        get() = progressPercent.coerceIn(0, 100)

    val hasKnownDownloadProgress: Boolean
        get() = phase == GitHubShareImportPhase.InstallDownloading && totalBytes > 0L

    val progressFraction: Float
        get() = boundedProgressPercent.toFloat() / 100f

    val appDisplayName: String
        get() =
            appLabel
                .trim()
                .ifBlank { targetDisplayName.trim() }
                .ifBlank { cleanShareImportAssetName(assetName) }
                .ifBlank { packageName.trim() }
}

internal fun GitHubPendingShareImportManagedInstallRecord.toManagedInstallProgress(): GitHubShareImportManagedInstallProgress {
    val phase =
        GitHubShareImportPhase.entries.firstOrNull { phase ->
            phase.name == progressPhase
        } ?: GitHubShareImportPhase.Installing
    return GitHubShareImportManagedInstallProgress(
        phase = phase,
        assetName = assetName,
        appLabel = appLabel,
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        minSdk = minSdk,
        targetSdk = targetSdk,
        nativeAbis = nativeAbis,
        targetDisplayName = targetDisplayName,
        progressPercent = progressPercent,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
    )
}
