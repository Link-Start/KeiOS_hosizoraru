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

data class GitHubLookupConfig(
    val selectedStrategy: GitHubLookupStrategyOption = GitHubLookupStrategyOption.AtomFeed,
    val actionsStrategy: GitHubActionsLookupStrategyOption = GitHubActionsLookupStrategyOption.NightlyLink,
    val apiToken: String = "",
    val checkAllTrackedPreReleases: Boolean = false,
    val aggressiveApkFiltering: Boolean = false,
    val preciseApkVersionEnabled: Boolean = false,
    val shareImportLinkageEnabled: Boolean = false,
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
        preciseApkVersionEnabled.toString()
    ).joinToString("|")
}

fun GitHubLookupConfig.githubAssetSourceSignature(): String {
    return listOf(
        "asset-v3",
        selectedStrategy.storageId,
        actionsStrategy.storageId,
        apiToken.trim().isNotBlank().toString(),
        aggressiveApkFiltering.toString(),
        preciseApkVersionEnabled.toString()
    ).joinToString("|")
}
