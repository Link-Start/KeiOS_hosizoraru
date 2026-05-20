package os.kei.feature.github.model

import java.net.URI
import java.util.Locale

private const val keiOsTrackOwner = "hosizoraru"
private const val keiOsTrackRepo = "KeiOS"
private const val keiOsTrackRepoUrl = "https://github.com/$keiOsTrackOwner/$keiOsTrackRepo"
private const val keiOsTrackAppLabel = "KeiOS"
const val KEI_OS_RELEASE_PACKAGE_NAME = "os.kei"

data class GitHubTrackedApp(
    val repoUrl: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val sourceMode: GitHubTrackedSourceMode = GitHubTrackedSourceMode.GitHubRepository,
    val preferPreRelease: Boolean = false,
    val alwaysShowLatestReleaseDownloadButton: Boolean = false,
    val checkActionsUpdates: Boolean = false,
    val actionsUpdateIntervalMode: GitHubTrackedActionsUpdateIntervalMode =
        GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
    val preciseApkVersionMode: GitHubTrackedPreciseApkVersionMode =
        GitHubTrackedPreciseApkVersionMode.FollowGlobal,
    val repositoryArchived: Boolean = false,
    val repositoryFork: Boolean = false,
    val localAppType: GitHubTrackedLocalAppType = GitHubTrackedLocalAppType.Unknown
) {
    val id: String
        get() {
            val base = "$owner/$repo|$packageName"
            return when (sourceMode) {
                GitHubTrackedSourceMode.GitHubRepository -> base
                GitHubTrackedSourceMode.DirectApk -> "${sourceMode.storageId}|$base"
            }
        }
}

enum class GitHubTrackedSourceMode(val storageId: String) {
    GitHubRepository("github_repository"),
    DirectApk("direct_apk");

    companion object {
        fun fromStorageId(value: String?): GitHubTrackedSourceMode {
            val normalized = value.orEmpty().trim()
            return entries.firstOrNull { it.storageId.equals(normalized, ignoreCase = true) }
                ?: GitHubRepository
        }
    }
}

data class GitHubDirectApkTrackIdentity(
    val url: String,
    val owner: String,
    val repo: String,
    val displayName: String,
    val assetName: String
)

fun GitHubTrackedApp.isGitHubRepositoryTrack(): Boolean {
    return sourceMode == GitHubTrackedSourceMode.GitHubRepository
}

fun GitHubTrackedApp.isDirectApkTrack(): Boolean {
    return sourceMode == GitHubTrackedSourceMode.DirectApk
}

fun GitHubTrackedApp.withSourceModeConstraints(): GitHubTrackedApp {
    return when (sourceMode) {
        GitHubTrackedSourceMode.GitHubRepository -> {
            if (checkActionsUpdates) {
                this
            } else {
                copy(actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal)
            }
        }

        GitHubTrackedSourceMode.DirectApk -> copy(
            alwaysShowLatestReleaseDownloadButton = false,
            checkActionsUpdates = false,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
        )
    }
}

fun buildDirectApkTrackIdentity(rawUrl: String): GitHubDirectApkTrackIdentity? {
    val normalizedUrl = rawUrl.trim()
    if (normalizedUrl.isBlank()) return null
    val uri = runCatching { URI(normalizedUrl) }.getOrNull() ?: return null
    val scheme = uri.scheme.orEmpty().lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host.orEmpty()
        .lowercase(Locale.ROOT)
        .removePrefix("www.")
        .ifBlank { return null }
    val pathSegments = uri.path
        .orEmpty()
        .split('/')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val pathKey = pathSegments
        .joinToString("-")
        .sanitizeDirectApkIdentityPart()
        .ifBlank { "apk" }
    val displayPath = pathSegments.joinToString("/")
    val displayName = if (displayPath.isBlank()) host else "$host/$displayPath"
    val fileName = pathSegments.lastOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "remote.apk"
    val assetName = fileName
        .substringBefore('?')
        .ifBlank { "remote.apk" }
        .let { name ->
            if (name.endsWith(".apk", ignoreCase = true)) name else "$name.apk"
        }
    return GitHubDirectApkTrackIdentity(
        url = normalizedUrl,
        owner = host,
        repo = pathKey,
        displayName = displayName,
        assetName = assetName
    )
}

fun parseGithubOwnerRepoStrict(urlOrPath: String): Pair<String, String>? {
    val raw = urlOrPath.trim()
        .removePrefix("git+")
        .removeSuffix(".git")
        .trimEnd('/')
    if (raw.isBlank()) return null
    if (raw.contains(":") && raw.contains("@") && raw.contains("github.com")) {
        val afterColon = raw.substringAfter(':', "")
        val ownerRepo = afterColon.removePrefix("/").split("/")
        if (ownerRepo.size >= 2) return ownerRepo[0] to ownerRepo[1]
    }
    val uri = runCatching { URI(raw) }.getOrNull()
    if (uri != null && uri.scheme != null) {
        val host = uri.host.orEmpty()
        if (!host.equals("github.com", ignoreCase = true) &&
            !host.endsWith(".github.com", ignoreCase = true)
        ) {
            return null
        }
        val segments = uri.path.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.size >= 2) return segments[0] to segments[1].removeSuffix(".git")
        return null
    }
    val normalized = raw
        .removePrefix("github.com/")
        .trim('/')
    val parts = normalized.split('/').filter { it.isNotBlank() }
    if (parts.size >= 2) return parts[0] to parts[1].removeSuffix(".git")
    return null
}

private fun String.sanitizeDirectApkIdentityPart(): String {
    return lowercase(Locale.ROOT)
        .replace(Regex("""[^a-z0-9._-]+"""), "-")
        .trim('-', '.', '_')
}

enum class GitHubTrackedLocalAppType(val storageId: String) {
    Unknown("unknown"),
    User("user"),
    System("system");

    companion object {
        fun fromStorageId(value: String?): GitHubTrackedLocalAppType {
            val normalized = value.orEmpty().trim()
            return entries.firstOrNull { it.storageId.equals(normalized, ignoreCase = true) }
                ?: Unknown
        }

        fun fromSystemFlag(isSystemApp: Boolean?): GitHubTrackedLocalAppType {
            return when (isSystemApp) {
                true -> System
                false -> User
                null -> Unknown
            }
        }
    }
}

fun GitHubTrackedApp.hasSameGitHubTrackingConfigIgnoringLocalAppType(
    other: GitHubTrackedApp
): Boolean {
    return copy(localAppType = other.localAppType) == other
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

enum class GitHubTrackedActionsUpdateIntervalMode(
    val storageId: String,
    val intervalMinutes: Int?
) {
    FollowGlobal("follow_global", null),
    Minutes15("15m", 15),
    Minutes30("30m", 30),
    Hour1("1h", 60),
    Hours2("2h", 120),
    Hours3("3h", 180);

    companion object {
        fun fromStorageId(value: String?): GitHubTrackedActionsUpdateIntervalMode {
            val normalized = value.orEmpty().trim()
            return entries.firstOrNull { it.storageId.equals(normalized, ignoreCase = true) }
                ?: FollowGlobal
        }
    }
}

fun GitHubTrackedActionsUpdateIntervalMode.effectiveIntervalMs(
    globalRefreshIntervalHours: Int
): Long {
    val minutes = intervalMinutes ?: globalRefreshIntervalHours.coerceIn(1, 12) * 60
    return minutes.coerceAtLeast(1) * 60L * 1000L
}

fun GitHubTrackedApp.actionsUpdateIntervalMs(globalRefreshIntervalHours: Int): Long {
    return actionsUpdateIntervalMode.effectiveIntervalMs(globalRefreshIntervalHours)
}

fun defaultKeiOsTrackedApp(
    packageName: String = KEI_OS_RELEASE_PACKAGE_NAME
): GitHubTrackedApp {
    return GitHubTrackedApp(
        repoUrl = keiOsTrackRepoUrl,
        owner = keiOsTrackOwner,
        repo = keiOsTrackRepo,
        packageName = packageName,
        appLabel = keiOsTrackAppLabel
    )
}

fun GitHubTrackedApp.asKeiOsActionsRunLookupItem(): GitHubTrackedApp {
    return copy(
        repoUrl = keiOsTrackRepoUrl,
        owner = keiOsTrackOwner,
        repo = keiOsTrackRepo,
        packageName = KEI_OS_RELEASE_PACKAGE_NAME,
        appLabel = appLabel.ifBlank { keiOsTrackAppLabel },
        sourceMode = GitHubTrackedSourceMode.GitHubRepository
    ).withSourceModeConstraints()
}

fun GitHubTrackedApp.isKeiOsRepositoryTrack(): Boolean {
    return sourceMode == GitHubTrackedSourceMode.GitHubRepository &&
            owner.equals(keiOsTrackOwner, ignoreCase = true) &&
            repo.equals(keiOsTrackRepo, ignoreCase = true)
}

fun GitHubTrackedApp.isKeiOsReleaseTrack(): Boolean {
    return isKeiOsRepositoryTrack() &&
            packageName.equals(KEI_OS_RELEASE_PACKAGE_NAME, ignoreCase = true)
}

fun GitHubTrackedApp.isKeiOsSelfTrack(
    packageName: String = KEI_OS_RELEASE_PACKAGE_NAME
): Boolean {
    return isKeiOsRepositoryTrack() &&
        this.packageName.equals(packageName, ignoreCase = true)
}

fun GitHubLookupConfig.forTrackedItem(item: GitHubTrackedApp): GitHubLookupConfig {
    if (item.isDirectApkTrack()) {
        return copy(
            checkAllTrackedPreReleases = checkAllDirectApkPreReleases,
            preciseApkVersionEnabled = true
        )
    }
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
    val repositoryProfile: GitHubRepositoryProfileSnapshot? = null,
    val directApkRemoteHealth: GitHubDirectApkRemoteHealth = GitHubDirectApkRemoteHealth.Unknown,
    val directApkRemoteHealthMessage: String = "",
    val directApkRemoteCheckedAtMillis: Long = -1L
)
