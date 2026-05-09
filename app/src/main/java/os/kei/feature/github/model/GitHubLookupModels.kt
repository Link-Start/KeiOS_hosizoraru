package os.kei.feature.github.model

enum class GitHubLookupStrategyOption(
    val storageId: String,
    val label: String
) {
    AtomFeed(
        storageId = "atom_feed",
        label = "Atom Feed"
    ),
    GitHubApiToken(
        storageId = "github_api_token",
        label = "GitHub API Token"
    );

    companion object {
        fun fromStorageId(value: String): GitHubLookupStrategyOption {
            return entries.firstOrNull { it.storageId == value } ?: AtomFeed
        }
    }
}

enum class GitHubActionsLookupStrategyOption(
    val storageId: String,
    val label: String
) {
    NightlyLink(
        storageId = "nightly_link",
        label = "nightly.link"
    ),
    GitHubApiToken(
        storageId = "github_api_token",
        label = "GitHub API Token"
    );

    companion object {
        fun fromStorageId(value: String): GitHubActionsLookupStrategyOption {
            return entries.firstOrNull { it.storageId == value } ?: NightlyLink
        }
    }
}

enum class GitHubReleaseNotesMode(
    val storageId: String
) {
    Off("off"),
    Compact("compact"),
    Expanded("expanded");

    companion object {
        fun fromStorageId(value: String): GitHubReleaseNotesMode {
            return entries.firstOrNull { it.storageId == value } ?: Off
        }
    }
}

enum class GitHubShareImportFlowMode(
    val storageId: String
) {
    SheetAssisted("sheet_assisted"),
    NotificationFirst("notification_first");

    companion object {
        fun fromStorageId(value: String): GitHubShareImportFlowMode {
            return entries.firstOrNull { it.storageId == value } ?: SheetAssisted
        }
    }
}

enum class GitHubProfileDepth(
    val storageId: String
) {
    Basic("basic"),
    Deep("deep");

    companion object {
        fun fromStorageId(value: String): GitHubProfileDepth {
            return entries.firstOrNull { it.storageId == value } ?: Basic
        }
    }
}

data class GitHubLookupConfig(
    val selectedStrategy: GitHubLookupStrategyOption = GitHubLookupStrategyOption.AtomFeed,
    val actionsStrategy: GitHubActionsLookupStrategyOption = GitHubActionsLookupStrategyOption.NightlyLink,
    val apiToken: String = "",
    val checkAllTrackedPreReleases: Boolean = false,
    val aggressiveApkFiltering: Boolean = false,
    val preciseApkVersionEnabled: Boolean = false,
    val profileDepth: GitHubProfileDepth = GitHubProfileDepth.Basic,
    val shareImportLinkageEnabled: Boolean = false,
    val shareImportFlowMode: GitHubShareImportFlowMode = GitHubShareImportFlowMode.SheetAssisted,
    val onlineShareTargetPackage: String = "",
    val preferredDownloaderPackage: String = "",
    val decisionAssistEnabled: Boolean = false,
    val repositoryHealthCardEnabled: Boolean = false,
    val apkTrustCheckEnabled: Boolean = false,
    val releaseNotesMode: GitHubReleaseNotesMode = GitHubReleaseNotesMode.Off
) {
    val actionsRequireApiToken: Boolean
        get() = actionsStrategy == GitHubActionsLookupStrategyOption.GitHubApiToken

    val actionsArtifactDownloadsAvailable: Boolean
        get() = actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink ||
            apiToken.trim().isNotBlank()
}

fun GitHubLookupConfig.githubCheckSourceSignature(): String {
    return listOf(
        "check-v2",
        selectedStrategy.storageId,
        apiToken.trim().isNotBlank().toString(),
        checkAllTrackedPreReleases.toString(),
        aggressiveApkFiltering.toString(),
        preciseApkVersionEnabled.toString(),
        profileDepth.storageId
    ).joinToString("|")
}

fun GitHubLookupConfig.defaultRepositoryProfilePurpose(): GitHubRepositoryProfilePurpose {
    return if (decisionAssistEnabled && repositoryHealthCardEnabled) {
        GitHubRepositoryProfilePurpose.HealthCard
    } else {
        GitHubRepositoryProfilePurpose.VersionCheckFast
    }
}

fun GitHubLookupConfig.githubProfileSourceSignature(
    purpose: GitHubRepositoryProfilePurpose = defaultRepositoryProfilePurpose()
): String {
    return githubProfileSourceSignature(purpose.requiredCapabilities(profileDepth))
}

fun GitHubLookupConfig.githubProfileSourceSignature(
    capabilities: Set<GitHubRepositoryProfileCapability>
): String {
    return listOf(
        "profile-v1",
        selectedStrategy.storageId,
        apiToken.trim().isNotBlank().toString(),
        checkAllTrackedPreReleases.toString(),
        aggressiveApkFiltering.toString(),
        preciseApkVersionEnabled.toString(),
        profileDepth.storageId,
        capabilities.sortedBy { it.name }.joinToString(",") { it.name }
    ).joinToString("|")
}

fun GitHubLookupConfig.githubAssetSourceSignature(): String {
    return listOf(
        "asset-v4",
        selectedStrategy.storageId,
        actionsStrategy.storageId,
        apiToken.trim().isNotBlank().toString(),
        aggressiveApkFiltering.toString(),
        preciseApkVersionEnabled.toString()
    ).joinToString("|")
}
