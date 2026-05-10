package os.kei.ui.page.main.github

import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubTrackedApp

internal fun GitHubTrackedApp.githubTrackedDisplayTitle(state: VersionCheckUi?): String {
    val localLabel = appLabel.trim()
    val remoteName = state.githubRemoteDisplayName(repo)
    val shouldUseRemoteName = state?.isLocalAppUninstalled() == true &&
            remoteName.isNotBlank() &&
            isGeneratedTrackingLabel(localLabel)
    return when {
        shouldUseRemoteName -> remoteName
        localLabel.isNotBlank() -> localLabel
        remoteName.isNotBlank() -> remoteName
        repo.isNotBlank() -> repo
        else -> owner
    }
}

internal fun GitHubTrackedApp.githubTrackedDisplaySubtitle(
    state: VersionCheckUi?,
    title: String = githubTrackedDisplayTitle(state)
): String {
    val packageNameLabel = packageName.trim()
    val ownerRepo = listOf(owner.trim(), repo.trim())
        .filter { it.isNotBlank() }
        .joinToString("/")
    return when {
        packageNameLabel.isNotBlank() && !title.equals(packageNameLabel, ignoreCase = true) ->
            packageNameLabel

        ownerRepo.isNotBlank() && !title.equals(ownerRepo, ignoreCase = true) -> ownerRepo
        else -> repo
    }
}

internal fun VersionCheckUi?.githubRemoteDisplayName(fallbackRepo: String = ""): String {
    this ?: return fallbackRepo
    return repositoryProfile
        ?.identity
        ?.name
        ?.value
        .orEmpty()
        .ifBlank {
            repositoryProfile
                ?.identity
                ?.fullName
                ?.value
                .orEmpty()
                .substringAfter('/')
        }
        .ifBlank { fallbackRepo }
}

internal fun VersionCheckUi.githubStableReleaseLinkUrl(owner: String, repo: String): String {
    return githubReleaseLinkUrl(
        owner = owner,
        repo = repo,
        releaseUrl = latestStableUrl,
        rawTag = latestStableRawTag.ifBlank { latestTag },
        apkVersionInfo = latestStableApkVersion
    )
}

internal fun VersionCheckUi.githubPreReleaseLinkUrl(owner: String, repo: String): String {
    return githubReleaseLinkUrl(
        owner = owner,
        repo = repo,
        releaseUrl = latestPreUrl,
        rawTag = latestPreRawTag.ifBlank { preReleaseInfo },
        apkVersionInfo = latestPreApkVersion
    )
}

private fun githubReleaseLinkUrl(
    owner: String,
    repo: String,
    releaseUrl: String,
    rawTag: String,
    apkVersionInfo: GitHubRemoteApkVersionInfo?
): String {
    return releaseUrl.trim()
        .ifBlank { apkVersionInfo?.releaseUrl.orEmpty().trim() }
        .ifBlank {
            rawTag.trim().takeIf { it.isNotBlank() }?.let { tag ->
                GitHubVersionUtils.buildReleaseTagUrl(owner, repo, tag)
            }.orEmpty()
        }
        .ifBlank { GitHubVersionUtils.buildReleaseUrl(owner, repo) }
}

private fun GitHubTrackedApp.isGeneratedTrackingLabel(label: String): Boolean {
    if (label.isBlank()) return true
    val ownerRepo = "$owner/$repo"
    return label.equals(packageName, ignoreCase = true) ||
            label.equals(ownerRepo, ignoreCase = true) ||
            label.equals(repo, ignoreCase = true)
}
