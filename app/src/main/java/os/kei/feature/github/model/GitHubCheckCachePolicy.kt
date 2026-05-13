package os.kei.feature.github.model

import java.util.Locale

internal const val GITHUB_DIRECT_APK_STRATEGY_ID = "direct_apk"

internal fun GitHubTrackedApp.checkSourceSignature(
    lookupConfig: GitHubLookupConfig
): String {
    return when (sourceMode) {
        GitHubTrackedSourceMode.DirectApk -> directApkCheckSourceSignature()
        GitHubTrackedSourceMode.GitHubRepository -> lookupConfig.githubCheckSourceSignature()
    }
}

internal fun GitHubTrackedApp.directApkCheckSourceSignature(): String {
    return listOf(
        GITHUB_DIRECT_APK_STRATEGY_ID,
        repoUrl.trim().lowercase(Locale.ROOT),
        packageName.trim().lowercase(Locale.ROOT),
        if (preferPreRelease) "pre" else "stable"
    ).joinToString("|")
}

internal fun GitHubCheckCacheEntry.isValidForTrackedItem(
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
            sourceId == GITHUB_DIRECT_APK_STRATEGY_ID && !item.preferPreRelease
        lookupConfig.preciseApkVersionEnabled -> false
        else -> sourceId == activeStrategyId
    }
}
