package os.kei.ui.page.main.github.share

import androidx.compose.runtime.Immutable
import os.kei.feature.github.data.local.GitHubPendingShareImportPreviewRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile

@Immutable
internal data class GitHubShareImportPreview(
    val sourceUrl: String,
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val releaseTag: String,
    val releaseUrl: String,
    val strategyLabel: String,
    val assets: List<GitHubReleaseAssetFile>,
    val preferredAssetName: String = "",
    val targetDisplayName: String = "",
    val selectedAssetName: String = "",
    val sendInstallActionEnabled: Boolean = false,
) {
    val defaultSelectedIndex: Int
        get() {
            if (assets.isEmpty()) return -1
            val selected = selectedAssetName.trim()
            if (selected.isNotBlank()) {
                val selectedIndex =
                    assets.indexOfFirst { asset ->
                        asset.name.equals(selected, ignoreCase = true)
                    }
                if (selectedIndex >= 0) return selectedIndex
            }
            val preferred = preferredAssetName.trim()
            if (preferred.isBlank()) return 0
            val index =
                assets.indexOfFirst { asset ->
                    asset.name.equals(preferred, ignoreCase = true)
                }
            return if (index >= 0) index else 0
        }

    val selectedAssetForSend: GitHubReleaseAssetFile?
        get() = assets.getOrNull(defaultSelectedIndex)
}

internal fun GitHubShareImportPreview.toPendingPreviewRecord(
    createdAtMillis: Long = GitHubSystemShareImportClock.nowMs(),
): GitHubPendingShareImportPreviewRecord =
    GitHubPendingShareImportPreviewRecord(
        sourceUrl = sourceUrl,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        releaseTag = releaseTag,
        releaseUrl = releaseUrl,
        strategyLabel = strategyLabel,
        assets = assets,
        preferredAssetName = preferredAssetName,
        targetDisplayName = targetDisplayName,
        selectedAssetName = selectedAssetName,
        sendInstallActionEnabled = sendInstallActionEnabled,
        createdAtMillis = createdAtMillis,
    )

internal fun GitHubPendingShareImportPreviewRecord.toShareImportPreview(): GitHubShareImportPreview =
    GitHubShareImportPreview(
        sourceUrl = sourceUrl,
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        releaseTag = releaseTag,
        releaseUrl = releaseUrl,
        strategyLabel = strategyLabel,
        assets = assets,
        preferredAssetName = preferredAssetName,
        targetDisplayName = targetDisplayName,
        selectedAssetName = selectedAssetName,
        sendInstallActionEnabled = sendInstallActionEnabled,
    )
