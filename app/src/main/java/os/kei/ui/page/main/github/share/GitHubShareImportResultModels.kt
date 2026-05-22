package os.kei.ui.page.main.github.share

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import os.kei.R
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_ALREADY_TRACKED
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_FAILED
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportResultRecord
import os.kei.ui.page.main.github.GitHubStatusPalette

internal enum class GitHubShareImportResultKind(
    val storageValue: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val statusRes: Int,
    @param:StringRes val primaryActionRes: Int,
    val color: Color,
) {
    Added(
        storageValue = GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED,
        titleRes = R.string.github_share_import_notify_title_added,
        statusRes = R.string.github_share_import_notify_short_added,
        primaryActionRes = R.string.github_share_import_notify_action_view_tracking,
        color = GitHubStatusPalette.Update,
    ),
    AlreadyTracked(
        storageValue = GITHUB_SHARE_IMPORT_RESULT_STATUS_ALREADY_TRACKED,
        titleRes = R.string.github_share_import_notify_title_already_tracked,
        statusRes = R.string.github_share_import_notify_short_already_tracked,
        primaryActionRes = R.string.github_share_import_notify_action_view_tracking,
        color = GitHubStatusPalette.Stable,
    ),
    Failed(
        storageValue = GITHUB_SHARE_IMPORT_RESULT_STATUS_FAILED,
        titleRes = R.string.github_share_import_notify_title_failed,
        statusRes = R.string.github_share_import_notify_short_failed,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        color = GitHubStatusPalette.Error,
    ),
    Cancelled(
        storageValue = GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED,
        titleRes = R.string.github_share_import_notify_title_cancelled,
        statusRes = R.string.github_share_import_notify_short_cancelled,
        primaryActionRes = R.string.github_share_import_notify_action_view_github,
        color = GitHubStatusPalette.PreRelease,
    ),
    ;

    companion object {
        fun fromStorageValue(value: String): GitHubShareImportResultKind? {
            val normalized = value.trim()
            return entries.firstOrNull { it.storageValue == normalized }
        }
    }
}

@Immutable
internal data class GitHubShareImportResult(
    val kind: GitHubShareImportResultKind,
    val projectUrl: String = "",
    val owner: String = "",
    val repo: String = "",
    val appLabel: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val targetDisplayName: String = "",
    val message: String = "",
    val completedAtMillis: Long = GitHubSystemShareImportClock.nowMs(),
) {
    val projectLabel: String
        get() {
            val normalizedOwner = owner.trim()
            val normalizedRepo = repo.trim()
            return when {
                normalizedOwner.isNotBlank() && normalizedRepo.isNotBlank() -> {
                    "$normalizedOwner/$normalizedRepo"
                }

                normalizedRepo.isNotBlank() -> {
                    normalizedRepo
                }

                normalizedOwner.isNotBlank() -> {
                    normalizedOwner
                }

                projectUrl.isNotBlank() -> {
                    projectUrl
                        .removePrefix("https://github.com/")
                        .removePrefix("http://github.com/")
                        .removePrefix("https://www.github.com/")
                        .removePrefix("http://www.github.com/")
                        .trim('/')
                        .ifBlank { projectUrl }
                }

                else -> {
                    "GitHub"
                }
            }
        }

    val appDisplayLabel: String
        get() = appLabel.ifBlank { targetDisplayName }.ifBlank { packageName }
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
        versionName = versionName,
        targetDisplayName = targetDisplayName,
        message = message,
        completedAtMillis = completedAtMillis,
    )
}

internal fun GitHubShareImportResult.toRecord(): GitHubShareImportResultRecord =
    GitHubShareImportResultRecord(
        status = kind.storageValue,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        appLabel = appLabel,
        packageName = packageName,
        versionName = versionName,
        targetDisplayName = targetDisplayName,
        message = message,
        completedAtMillis = completedAtMillis,
    )

internal fun GitHubPendingShareImportAttachCandidate.toShareImportResult(
    kind: GitHubShareImportResultKind,
    appLabelOverride: String = "",
    message: String = "",
    completedAtMillis: Long = GitHubSystemShareImportClock.nowMs(),
): GitHubShareImportResult =
    GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        appLabel = appLabelOverride.ifBlank { appLabel },
        packageName = packageName,
        versionName = versionName,
        targetDisplayName =
            buildShareImportTargetDisplayName(
                appLabel = appLabelOverride.ifBlank { appLabel },
                repo = repo,
                packageName = packageName,
            ),
        message = message,
        completedAtMillis = completedAtMillis,
    )

internal fun GitHubShareImportPreview.toShareImportResult(
    kind: GitHubShareImportResultKind,
    message: String = "",
    completedAtMillis: Long = GitHubSystemShareImportClock.nowMs(),
): GitHubShareImportResult =
    GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        targetDisplayName = targetDisplayName,
        message = message,
        completedAtMillis = completedAtMillis,
    )

internal fun GitHubPendingShareImportTrackRecord.toShareImportResult(
    kind: GitHubShareImportResultKind,
    message: String = "",
    completedAtMillis: Long = GitHubSystemShareImportClock.nowMs(),
): GitHubShareImportResult =
    GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        versionName = versionName,
        targetDisplayName = targetDisplayName,
        message = message,
        completedAtMillis = completedAtMillis,
    )

internal fun GitHubPendingShareImportTrack.toShareImportResult(
    kind: GitHubShareImportResultKind,
    message: String = "",
    completedAtMillis: Long = GitHubSystemShareImportClock.nowMs(),
): GitHubShareImportResult =
    GitHubShareImportResult(
        kind = kind,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        versionName = versionName,
        targetDisplayName = targetDisplayName,
        message = message,
        completedAtMillis = completedAtMillis,
    )
