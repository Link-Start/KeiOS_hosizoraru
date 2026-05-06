package os.kei.ui.page.main.github.share

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import os.kei.R
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_ALREADY_TRACKED
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_FAILED
import os.kei.feature.github.data.local.GitHubPendingShareImportAttachCandidateRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportPreviewRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportResultRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.GitHubStatusPalette

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

internal fun GitHubPendingShareImportTrackRecord.toShareImportTrack(): GitHubPendingShareImportTrack {
    return GitHubPendingShareImportTrack(
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        releaseTag = releaseTag,
        assetName = assetName,
        armedAtMillis = armedAtMillis
    )
}

internal enum class GitHubShareImportResultKind(
    val storageValue: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val statusRes: Int,
    @param:StringRes val primaryActionRes: Int,
    val color: Color
) {
    Added(
        storageValue = GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED,
        titleRes = R.string.github_share_import_notify_title_added,
        statusRes = R.string.github_share_import_notify_short_added,
        primaryActionRes = R.string.github_share_import_notify_action_view_tracking,
        color = GitHubStatusPalette.Update
    ),
    AlreadyTracked(
        storageValue = GITHUB_SHARE_IMPORT_RESULT_STATUS_ALREADY_TRACKED,
        titleRes = R.string.github_share_import_notify_title_already_tracked,
        statusRes = R.string.github_share_import_notify_short_already_tracked,
        primaryActionRes = R.string.github_share_import_notify_action_view_tracking,
        color = GitHubStatusPalette.Stable
    ),
    Failed(
        storageValue = GITHUB_SHARE_IMPORT_RESULT_STATUS_FAILED,
        titleRes = R.string.github_share_import_notify_title_failed,
        statusRes = R.string.github_share_import_notify_short_failed,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        color = GitHubStatusPalette.Error
    ),
    Cancelled(
        storageValue = GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED,
        titleRes = R.string.github_share_import_notify_title_cancelled,
        statusRes = R.string.github_share_import_notify_short_cancelled,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        color = GitHubStatusPalette.PreRelease
    );

    companion object {
        fun fromStorageValue(value: String): GitHubShareImportResultKind? {
            val normalized = value.trim()
            return entries.firstOrNull { it.storageValue == normalized }
        }
    }
}

internal data class GitHubShareImportResult(
    val kind: GitHubShareImportResultKind,
    val projectUrl: String = "",
    val owner: String = "",
    val repo: String = "",
    val appLabel: String = "",
    val packageName: String = "",
    val message: String = "",
    val completedAtMillis: Long = System.currentTimeMillis()
) {
    val projectLabel: String
        get() {
            val normalizedOwner = owner.trim()
            val normalizedRepo = repo.trim()
            return when {
                normalizedOwner.isNotBlank() && normalizedRepo.isNotBlank() ->
                    "$normalizedOwner/$normalizedRepo"

                normalizedRepo.isNotBlank() -> normalizedRepo
                normalizedOwner.isNotBlank() -> normalizedOwner
                projectUrl.isNotBlank() -> projectUrl
                    .removePrefix("https://github.com/")
                    .removePrefix("http://github.com/")
                    .removePrefix("https://www.github.com/")
                    .removePrefix("http://www.github.com/")
                    .trim('/')
                    .ifBlank { projectUrl }

                else -> "GitHub"
            }
        }

    val appDisplayLabel: String
        get() = appLabel.ifBlank { packageName }
}

internal fun GitHubShareImportResultRecord.toShareImportResult(): GitHubShareImportResult? {
    val kind = GitHubShareImportResultKind.fromStorageValue(status) ?: return null
    return GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        appLabel = appLabel,
        packageName = packageName,
        message = message,
        completedAtMillis = completedAtMillis
    )
}

internal fun GitHubShareImportResult.toRecord(): GitHubShareImportResultRecord {
    return GitHubShareImportResultRecord(
        status = kind.storageValue,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        appLabel = appLabel,
        packageName = packageName,
        message = message,
        completedAtMillis = completedAtMillis
    )
}

internal fun GitHubPendingShareImportAttachCandidate.toShareImportResult(
    kind: GitHubShareImportResultKind,
    appLabelOverride: String = "",
    message: String = "",
    completedAtMillis: Long = System.currentTimeMillis()
): GitHubShareImportResult {
    return GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        appLabel = appLabelOverride.ifBlank { appLabel },
        packageName = packageName,
        message = message,
        completedAtMillis = completedAtMillis
    )
}

internal fun GitHubShareImportPreview.toShareImportResult(
    kind: GitHubShareImportResultKind,
    message: String = "",
    completedAtMillis: Long = System.currentTimeMillis()
): GitHubShareImportResult {
    return GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        message = message,
        completedAtMillis = completedAtMillis
    )
}

internal fun GitHubPendingShareImportTrackRecord.toShareImportResult(
    kind: GitHubShareImportResultKind,
    message: String = "",
    completedAtMillis: Long = System.currentTimeMillis()
): GitHubShareImportResult {
    return GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        message = message,
        completedAtMillis = completedAtMillis
    )
}

internal fun GitHubPendingShareImportTrack.toShareImportResult(
    kind: GitHubShareImportResultKind,
    message: String = "",
    completedAtMillis: Long = System.currentTimeMillis()
): GitHubShareImportResult {
    return GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        message = message,
        completedAtMillis = completedAtMillis
    )
}

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
