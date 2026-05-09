package os.kei.ui.page.main.github

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

internal fun VersionCheckUi?.githubRemoteIconUrl(): String {
    this ?: return ""
    return repositoryProfile
        ?.identity
        ?.ownerAvatarUrl
        ?.value
        .orEmpty()
        .ifBlank { latestStableAuthorAvatarUrl }
        .ifBlank { latestPreAuthorAvatarUrl }
}

private fun GitHubTrackedApp.isGeneratedTrackingLabel(label: String): Boolean {
    if (label.isBlank()) return true
    val ownerRepo = "$owner/$repo"
    return label.equals(packageName, ignoreCase = true) ||
            label.equals(ownerRepo, ignoreCase = true) ||
            label.equals(repo, ignoreCase = true)
}
