package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Immutable
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload

@Immutable
internal data class GitHubTrackImportApplyResult(
    val addedCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val invalidCount: Int,
    val duplicateCount: Int
)

@Immutable
internal data class GitHubTrackImportPreview(
    val payload: GitHubTrackedItemsImportPayload,
    val fileItemCount: Int,
    val validCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
    val newCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val mergedCount: Int,
    val preferPreReleaseCount: Int = 0,
    val latestReleaseDownloadCount: Int = 0,
    val actionsUpdateCount: Int = 0,
    val preciseApkVersionOverrideCount: Int = 0
) {
    val canImport: Boolean
        get() = validCount > 0

    val importedProjectOptionCount: Int
        get() = preferPreReleaseCount +
                latestReleaseDownloadCount +
                actionsUpdateCount +
                preciseApkVersionOverrideCount

    val hasImportedProjectOptions: Boolean
        get() = importedProjectOptionCount > 0
}
