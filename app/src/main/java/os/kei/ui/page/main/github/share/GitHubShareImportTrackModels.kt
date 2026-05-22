@file:Suppress("ktlint:standard:filename")

package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord

internal data class GitHubPendingShareImportTrack(
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val releaseTag: String = "",
    val assetName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val targetDisplayName: String = "",
    val armedAtMillis: Long = GitHubSystemShareImportClock.nowMs(),
)

internal fun GitHubPendingShareImportTrackRecord.toShareImportTrack(): GitHubPendingShareImportTrack =
    GitHubPendingShareImportTrack(
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        releaseTag = releaseTag,
        assetName = assetName,
        packageName = packageName,
        versionName = versionName,
        targetDisplayName = targetDisplayName,
        armedAtMillis = armedAtMillis,
    )
