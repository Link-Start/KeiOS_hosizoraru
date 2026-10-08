package os.kei.feature.github.model

import java.util.Locale

const val GITHUB_DIRECT_APK_STRATEGY_ID = "direct_apk"
const val GITHUB_FDROID_STRATEGY_ID = "fdroid_repository"

fun GitHubTrackedApp.checkSourceSignature(
    lookupConfig: GitHubLookupConfig
): String {
    return when (sourceMode) {
        GitHubTrackedSourceMode.DirectApk ->
            directApkCheckSourceSignature(lookupConfig.checkAllTrackedPreReleases)
        GitHubTrackedSourceMode.GitRepository -> gitRepositoryCheckSourceSignature(lookupConfig)
        GitHubTrackedSourceMode.FdroidRepository -> fdroidRepositoryCheckSourceSignature()
        GitHubTrackedSourceMode.GitHubRepository -> {
            val base = "${lookupConfig.githubCheckSourceSignature()}|package=${packageName.trim().lowercase(Locale.ROOT)}"
            if (externalBuildUntilRelease) "$base|external-build" else base
        }
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

fun GitHubTrackedApp.fdroidRepositoryCheckSourceSignature(): String {
    val identity = buildFdroidRepositoryTrackIdentity(repoUrl, packageName)
    val config = fdroidConfig
    return listOf(
        "fdroid_repository-v1",
        identity?.normalizedRepoUrl ?: repoUrl.trim().lowercase(Locale.ROOT),
        identity?.packageName?.lowercase(Locale.ROOT)
            ?: packageName.trim().lowercase(Locale.ROOT),
        config.selectionMode.storageId,
        config.versionNameRegex.trim(),
        config.apkNameRegex.trim(),
        config.repoFingerprint.trim().lowercase(Locale.ROOT),
        config.indexFormat.storageId,
        config.trustPolicy.storageId,
        config.antiFeaturePolicy.storageId,
        config.blockedAntiFeatures
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .sorted()
            .joinToString(","),
        if (preferPreRelease) "pre" else "stable"
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
        item.isFdroidRepositoryTrack() -> false
        item.isGitRepositoryTrack() -> false
        item.packageName.isNotBlank() -> false // Legacy checks did not establish release product identity.
        lookupConfig.preciseApkVersionEnabled -> false
        else -> sourceId == activeStrategyId
    }
}
