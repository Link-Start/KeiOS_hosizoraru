package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.local.GitHubPendingShareImportAttachCandidateRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportPreviewRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile

internal data class GitHubShareImportPreview(
    val sourceUrl: String,
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val releaseTag: String,
    val releaseUrl: String,
    val strategyLabel: String,
    val assets: List<GitHubReleaseAssetFile>,
    val preferredAssetName: String = ""
) {
    val defaultSelectedIndex: Int
        get() {
            if (assets.isEmpty()) return -1
            val preferred = preferredAssetName.trim()
            if (preferred.isBlank()) return 0
            val index = assets.indexOfFirst { asset ->
                asset.name.equals(preferred, ignoreCase = true)
            }
            return if (index >= 0) index else 0
        }
}

internal data class GitHubPendingShareImportTrack(
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val releaseTag: String = "",
    val assetName: String = "",
    val armedAtMillis: Long = System.currentTimeMillis()
)

internal data class GitHubPendingShareImportAttachCandidate(
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val eventAction: String,
    val detectedAtMillis: Long = System.currentTimeMillis(),
    val firstInstallTimeMs: Long = -1L
)

internal fun GitHubShareImportPreview.toPendingPreviewRecord(
    createdAtMillis: Long = System.currentTimeMillis()
): GitHubPendingShareImportPreviewRecord {
    return GitHubPendingShareImportPreviewRecord(
        sourceUrl = sourceUrl,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        releaseTag = releaseTag,
        releaseUrl = releaseUrl,
        strategyLabel = strategyLabel,
        assets = assets,
        preferredAssetName = preferredAssetName,
        createdAtMillis = createdAtMillis
    )
}

internal fun GitHubPendingShareImportPreviewRecord.toShareImportPreview(): GitHubShareImportPreview {
    return GitHubShareImportPreview(
        sourceUrl = sourceUrl,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        releaseTag = releaseTag,
        releaseUrl = releaseUrl,
        strategyLabel = strategyLabel,
        assets = assets,
        preferredAssetName = preferredAssetName
    )
}

internal fun GitHubPendingShareImportAttachCandidate.toPendingAttachCandidateRecord(): GitHubPendingShareImportAttachCandidateRecord {
    return GitHubPendingShareImportAttachCandidateRecord(
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = appLabel,
        eventAction = eventAction,
        detectedAtMillis = detectedAtMillis,
        firstInstallTimeMs = firstInstallTimeMs
    )
}

internal fun GitHubPendingShareImportAttachCandidateRecord.toShareImportAttachCandidate(): GitHubPendingShareImportAttachCandidate {
    return GitHubPendingShareImportAttachCandidate(
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = appLabel,
        eventAction = eventAction,
        detectedAtMillis = detectedAtMillis,
        firstInstallTimeMs = firstInstallTimeMs
    )
}
