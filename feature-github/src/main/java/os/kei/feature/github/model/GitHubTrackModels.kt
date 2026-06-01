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
    val updateIntervalMode: GitHubTrackedUpdateIntervalMode =
        GitHubTrackedUpdateIntervalMode.FollowGlobal,
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
                GitHubTrackedSourceMode.GitRepository -> "${sourceMode.storageId}|$base"
                GitHubTrackedSourceMode.DirectApk -> "${sourceMode.storageId}|$base"
            }
        }
}

enum class GitHubTrackedSourceMode(val storageId: String) {
    GitHubRepository("github_repository"),
    GitRepository("git_repository"),
    DirectApk("direct_apk");

    companion object {
        fun fromStorageId(value: String?): GitHubTrackedSourceMode {
            val normalized = value.orEmpty().trim().lowercase(Locale.ROOT)
            return entries.firstOrNull { it.storageId == normalized }
                ?: when (normalized) {
                    "git", "gitee", "gitlab" -> GitRepository
                    "github", "repo", "repository" -> GitHubRepository
                    "direct", "apk", "subscription", "subscription_project" -> DirectApk
                    else -> GitHubRepository
                }
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

enum class GitRepositoryPlatform(val storageId: String) {
    GitHub("github"),
    Gitee("gitee"),
    GitLab("gitlab"),
    Generic("generic")
}

data class GitRepositoryTrackIdentity(
    val url: String,
    val host: String,
    val namespace: String,
    val repo: String,
    val owner: String,
    val displayName: String,
    val platform: GitRepositoryPlatform
)

fun GitHubTrackedApp.isGitHubRepositoryTrack(): Boolean {
    return sourceMode == GitHubTrackedSourceMode.GitHubRepository
}

fun GitHubTrackedApp.isGitRepositoryTrack(): Boolean {
    return sourceMode == GitHubTrackedSourceMode.GitRepository
}

fun GitHubTrackedApp.isGitBackedRepositoryTrack(): Boolean {
    return isGitHubRepositoryTrack() || isGitRepositoryTrack()
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

        GitHubTrackedSourceMode.GitRepository -> copy(
            alwaysShowLatestReleaseDownloadButton = false,
            checkActionsUpdates = false,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
        )

        GitHubTrackedSourceMode.DirectApk -> copy(
            alwaysShowLatestReleaseDownloadButton = false,
            checkActionsUpdates = false,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
        )
    }
}

fun buildGitRepositoryTrackIdentity(rawUrl: String): GitRepositoryTrackIdentity? {
    val normalizedUrl = rawUrl.trim()
        .removePrefix("git+")
        .trimEnd('/')
    if (normalizedUrl.isBlank()) return null
    val parsed = parseGitRepositoryHostAndPath(normalizedUrl) ?: return null
    val host = parsed.first
        .lowercase(Locale.ROOT)
        .removePrefix("www.")
        .ifBlank { return null }
    val pathSegments = normalizeGitRepositoryPathSegments(parsed.second)
    if (pathSegments.size < 2) return null
    val namespace = pathSegments
        .dropLast(1)
        .joinToString("/")
        .ifBlank { return null }
    val repo = pathSegments.last().removeSuffix(".git").ifBlank { return null }
    val owner = "$host/$namespace"
    val displayName = "$owner/$repo"
    return GitRepositoryTrackIdentity(
        url = normalizedUrl,
        host = host,
        namespace = namespace,
        repo = repo,
        owner = owner,
        displayName = displayName,
        platform = gitRepositoryPlatform(host)
    )
}

fun GitHubTrackedApp.githubReleaseLookupItemOrNull(): GitHubTrackedApp? {
    if (isGitHubRepositoryTrack()) return this
    if (!isGitRepositoryTrack()) return null
    val identity = buildGitRepositoryTrackIdentity(repoUrl) ?: return null
    if (identity.platform != GitRepositoryPlatform.GitHub) return null
    if ('/' in identity.namespace) return null
    return copy(
        repoUrl = "https://github.com/${identity.namespace}/${identity.repo}",
        owner = identity.namespace,
        repo = identity.repo,
        sourceMode = GitHubTrackedSourceMode.GitHubRepository,
        checkActionsUpdates = false,
        actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal
    ).withSourceModeConstraints()
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

private fun parseGitRepositoryHostAndPath(rawUrl: String): Pair<String, String>? {
    parseScpLikeGitRepositoryUrl(rawUrl)?.let { return it }
    val uri = runCatching { URI(rawUrl) }.getOrNull()
    if (uri?.scheme != null) {
        val scheme = uri.scheme.orEmpty().lowercase(Locale.ROOT)
        if (scheme !in setOf("http", "https", "ssh", "git")) return null
        val host = uri.host.orEmpty().ifBlank { return null }
        val path = uri.path.orEmpty()
        return host to path
    }
    val parts = rawUrl
        .split('/')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.size >= 3 && looksLikeGitHost(parts.first())) {
        return parts.first().substringAfter('@') to parts.drop(1).joinToString("/")
    }
    return null
}

private fun parseScpLikeGitRepositoryUrl(rawUrl: String): Pair<String, String>? {
    if ("://" in rawUrl) return null
    val colonIndex = rawUrl.indexOf(':')
    if (colonIndex <= 0) return null
    val beforeColon = rawUrl.substring(0, colonIndex)
    if ('/' in beforeColon) return null
    val host = beforeColon.substringAfter('@').trim()
    val path = rawUrl.substring(colonIndex + 1).trim()
    if (host.isBlank() || path.isBlank() || '/' !in path) return null
    return host to path
}

private fun normalizeGitRepositoryPathSegments(path: String): List<String> {
    val markers = setOf(
        "-",
        "tree",
        "blob",
        "src",
        "commits",
        "commit",
        "releases",
        "tags",
        "issues",
        "pulls",
        "merge_requests"
    )
    val rawSegments = path
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
        .split('/')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val markerIndex = rawSegments.indexOfFirst { segment ->
        segment.lowercase(Locale.ROOT) in markers
    }
    val repositorySegments = if (markerIndex >= 2) {
        rawSegments.take(markerIndex)
    } else {
        rawSegments
    }
    if (repositorySegments.isEmpty()) return emptyList()
    return repositorySegments.dropLast(1) +
        repositorySegments.last().removeSuffix(".git")
}

private fun gitRepositoryPlatform(host: String): GitRepositoryPlatform {
    val normalized = host.lowercase(Locale.ROOT).removePrefix("www.")
    return when {
        normalized == "github.com" -> GitRepositoryPlatform.GitHub
        normalized == "gitee.com" -> GitRepositoryPlatform.Gitee
        normalized == "gitlab.com" || normalized.endsWith(".gitlab.com") -> GitRepositoryPlatform.GitLab
        else -> GitRepositoryPlatform.Generic
    }
}

private fun looksLikeGitHost(value: String): Boolean {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return '.' in normalized ||
        normalized == "localhost" ||
        normalized.startsWith("git@")
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

enum class GitHubTrackedUpdateIntervalMode(
    val storageId: String,
    val intervalHours: Int?
) {
    FollowGlobal("follow_global", null),
    Hour1("1h", 1),
    Hours3("3h", 3),
    Hours6("6h", 6),
    Hours12("12h", 12);

    companion object {
        fun fromStorageId(value: String?): GitHubTrackedUpdateIntervalMode {
            val normalized = value.orEmpty().trim()
            return entries.firstOrNull { it.storageId.equals(normalized, ignoreCase = true) }
                ?: FollowGlobal
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

fun GitHubTrackedUpdateIntervalMode.effectiveIntervalMs(
    globalRefreshIntervalHours: Int
): Long {
    val hours = intervalHours ?: globalRefreshIntervalHours.coerceIn(1, 12)
    return hours.coerceIn(1, 12) * 60L * 60L * 1000L
}

fun GitHubTrackedApp.updateIntervalMs(globalRefreshIntervalHours: Int): Long {
    return updateIntervalMode.effectiveIntervalMs(globalRefreshIntervalHours)
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
    val directApkRemoteCheckedAtMillis: Long = -1L,
    val checkedAtMillis: Long = -1L
)
