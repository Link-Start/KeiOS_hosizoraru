package os.kei.feature.github.model

import java.util.Locale

const val GITHUB_DIRECT_APK_STRATEGY_ID = "direct_apk"

fun GitHubTrackedApp.checkSourceSignature(
    lookupConfig: GitHubLookupConfig
): String {
    return when (sourceMode) {
        GitHubTrackedSourceMode.DirectApk ->
            directApkCheckSourceSignature(lookupConfig.checkAllTrackedPreReleases)
        GitHubTrackedSourceMode.GitRepository -> gitRepositoryCheckSourceSignature(lookupConfig)
        GitHubTrackedSourceMode.GitHubRepository -> lookupConfig.githubCheckSourceSignature()
    }
}

fun GitHubTrackedApp.gitRepositoryCheckSourceSignature(
    lookupConfig: GitHubLookupConfig
): String {
    val identity = buildGitRepositoryTrackIdentity(repoUrl)
    return listOf(
        "git_repository-v1",
        identity?.platform?.storageId.orEmpty().ifBlank { "unknown" },
        identity?.host.orEmpty().ifBlank { owner.substringBefore('/').trim().lowercase(Locale.ROOT) },
        repoUrl.trim().lowercase(Locale.ROOT),
        packageName.trim().lowercase(Locale.ROOT),
        if (preferPreRelease) "pre" else "stable",
        lookupConfig.preciseApkVersionEnabled.toString()
    ).joinToString("|")
}

fun GitHubTrackedApp.directApkCheckSourceSignature(
    checkAllPreReleases: Boolean = false
): String {
    return listOf(
        GITHUB_DIRECT_APK_STRATEGY_ID,
        repoUrl.trim().lowercase(Locale.ROOT),
        packageName.trim().lowercase(Locale.ROOT),
        if (preferPreRelease) "pre" else "stable",
        if (checkAllPreReleases) "all-pre" else "single-channel"
    ).joinToString("|")
}

fun GitHubCheckCacheEntry.isValidForTrackedItem(
    item: GitHubTrackedApp,
    lookupConfig: GitHubLookupConfig,
    activeStrategyId: String
): Boolean {
    val sourceId = sourceStrategyId.ifBlank {
        GitHubLookupStrategyOption.AtomFeed.storageId
    }
    return when {
        sourceConfigSignature.isNotBlank() ->
            sourceConfigSignature == item.checkSourceSignature(lookupConfig)

        item.isDirectApkTrack() ->
            sourceId == GITHUB_DIRECT_APK_STRATEGY_ID &&
                    !item.preferPreRelease &&
                    !lookupConfig.checkAllTrackedPreReleases
        item.isGitRepositoryTrack() -> false
        lookupConfig.preciseApkVersionEnabled -> false
        else -> sourceId == activeStrategyId
    }
}
