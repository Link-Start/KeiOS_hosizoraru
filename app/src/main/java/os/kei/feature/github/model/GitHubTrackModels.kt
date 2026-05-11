package os.kei.feature.github.model

import os.kei.BuildConfig

private const val keiOsTrackOwner = "hosizoraru"
private const val keiOsTrackRepo = "KeiOS"
private const val keiOsTrackRepoUrl = "https://github.com/$keiOsTrackOwner/$keiOsTrackRepo"
private const val keiOsTrackAppLabel = "KeiOS"

data class GitHubTrackedApp(
    val repoUrl: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val preferPreRelease: Boolean = false,
    val alwaysShowLatestReleaseDownloadButton: Boolean = false,
    val checkActionsUpdates: Boolean = false,
    val preciseApkVersionMode: GitHubTrackedPreciseApkVersionMode =
        GitHubTrackedPreciseApkVersionMode.FollowGlobal,
    val repositoryArchived: Boolean = false,
    val repositoryFork: Boolean = false
) {
    val id: String
        get() = "$owner/$repo|$packageName"
}

enum class GitHubTrackedPreciseApkVersionMode(val storageId: String) {
    FollowGlobal("follow_global"),
    Enabled("enabled"),
    Disabled("disabled");

    companion object {
        fun fromStorageId(value: String?): GitHubTrackedPreciseApkVersionMode {
            val normalized = value.orEmpty().trim()
            return entries.firstOrNull { it.storageId.equals(normalized, ignoreCase = true) }
                ?: FollowGlobal
        }

        fun fromLegacyEnabled(value: Boolean): GitHubTrackedPreciseApkVersionMode {
            return if (value) Enabled else FollowGlobal
        }
    }
}

internal fun defaultKeiOsTrackedApp(): GitHubTrackedApp {
    return GitHubTrackedApp(
        repoUrl = keiOsTrackRepoUrl,
        owner = keiOsTrackOwner,
        repo = keiOsTrackRepo,
        packageName = BuildConfig.APPLICATION_ID,
        appLabel = keiOsTrackAppLabel
    )
}

internal fun GitHubTrackedApp.isKeiOsSelfTrack(): Boolean {
    return owner.equals(keiOsTrackOwner, ignoreCase = true) &&
        repo.equals(keiOsTrackRepo, ignoreCase = true) &&
        packageName.equals(BuildConfig.APPLICATION_ID, ignoreCase = true)
}

fun GitHubLookupConfig.forTrackedItem(item: GitHubTrackedApp): GitHubLookupConfig {
    val preciseResolved = when (item.preciseApkVersionMode) {
        GitHubTrackedPreciseApkVersionMode.FollowGlobal -> this
        GitHubTrackedPreciseApkVersionMode.Enabled -> copy(preciseApkVersionEnabled = true)
        GitHubTrackedPreciseApkVersionMode.Disabled -> copy(preciseApkVersionEnabled = false)
    }
    return if (item.preferPreRelease) {
        preciseResolved.copy(checkAllTrackedPreReleases = true)
    } else {
        preciseResolved
    }
}

data class InstalledAppItem(
    val label: String,
    val packageName: String,
    val firstInstallTimeMs: Long = -1L,
    val lastUpdateTimeMs: Long = -1L,
    val isSystemApp: Boolean = false,
    val installSourcePackageName: String = "",
    val installSourceLabel: String = ""
)

data class GitHubCheckCacheEntry(
    val loading: Boolean = false,
    val localVersion: String = "",
    val localVersionCode: Long = -1L,
    val latestTag: String = "",
    val latestStableName: String = "",
    val latestStableRawTag: String = "",
    val latestStableUrl: String = "",
    val latestStableAuthorAvatarUrl: String = "",
    val latestStableUpdatedAtMillis: Long = -1L,
    val latestPreName: String = "",
    val latestPreRawTag: String = "",
    val latestPreUrl: String = "",
    val latestPreAuthorAvatarUrl: String = "",
    val latestPreUpdatedAtMillis: Long = -1L,
    val hasStableRelease: Boolean = true,
    val hasUpdate: Boolean? = null,
    val message: String = "",
    val isPreRelease: Boolean = false,
    val preReleaseInfo: String = "",
    val showPreReleaseInfo: Boolean = false,
    val hasPreReleaseUpdate: Boolean = false,
    val recommendsPreRelease: Boolean = false,
    val releaseHint: String = "",
    val sourceStrategyId: String = "",
    val sourceConfigSignature: String = "",
    val latestStableApkVersion: GitHubRemoteApkVersionInfo? = null,
    val latestPreApkVersion: GitHubRemoteApkVersionInfo? = null,
    val repositoryArchived: Boolean = false,
    val repositoryFork: Boolean = false,
    val repositoryPushedAtMillis: Long = -1L,
    val upstreamFullName: String = "",
    val upstreamArchived: Boolean = false,
    val upstreamPushedAtMillis: Long = -1L,
    val repositoryProfile: GitHubRepositoryProfileSnapshot? = null
)
