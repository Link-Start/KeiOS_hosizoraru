package os.kei.feature.github.model

private const val DEFAULT_PROFILE_CACHE_TTL_MS = 1000L * 60L * 60L * 6L

enum class GitHubRepositoryProfileSource {
    GitHubApiRepository,
    GitHubApiReleases,
    AtomReleaseFeed,
    HtmlRepositoryPage,
    HtmlLatestReleaseRedirect,
    ReleaseAssetsApi,
    ReleaseAssetsHtml,
    ActionsRunsApi,
    ActionsArtifactsApi,
    CommunityProfileApi,
    TrafficViewsApi,
    TrafficClonesApi,
    ForkCompareApi,
    DependabotAlertsApi,
    CodeScanningAlertsApi,
    LocalInstall,
    OptionalEnhancedEndpoint,
    Cache
}

enum class GitHubRepositoryProfileConfidence {
    High,
    Medium,
    Low
}

enum class GitHubRepositoryProfileAvailabilityStatus {
    Loaded,
    Failed,
    Skipped
}

data class GitHubProfileField<T>(
    val value: T,
    val source: GitHubRepositoryProfileSource,
    val fetchedAtMillis: Long,
    val confidence: GitHubRepositoryProfileConfidence
)

data class GitHubRepositoryProfileSourceState(
    val source: GitHubRepositoryProfileSource,
    val status: GitHubRepositoryProfileAvailabilityStatus,
    val fetchedAtMillis: Long,
    val message: String = ""
)

data class GitHubRepositoryUpstreamProfile(
    val fullName: GitHubProfileField<String>? = null,
    val htmlUrl: GitHubProfileField<String>? = null,
    val archived: GitHubProfileField<Boolean>? = null,
    val disabled: GitHubProfileField<Boolean>? = null,
    val pushedAtMillis: GitHubProfileField<Long>? = null,
    val defaultBranch: GitHubProfileField<String>? = null
)

data class GitHubRepositoryIdentityProfile(
    val owner: GitHubProfileField<String>? = null,
    val name: GitHubProfileField<String>? = null,
    val fullName: GitHubProfileField<String>? = null,
    val htmlUrl: GitHubProfileField<String>? = null,
    val defaultBranch: GitHubProfileField<String>? = null,
    val ownerType: GitHubProfileField<String>? = null,
    val visibility: GitHubProfileField<String>? = null,
    val privateRepository: GitHubProfileField<Boolean>? = null,
    val topics: GitHubProfileField<List<String>>? = null
)

data class GitHubRepositoryLifecycleProfile(
    val archived: GitHubProfileField<Boolean>? = null,
    val disabled: GitHubProfileField<Boolean>? = null,
    val fork: GitHubProfileField<Boolean>? = null,
    val mirrorUrl: GitHubProfileField<String>? = null,
    val upstream: GitHubRepositoryUpstreamProfile? = null
)

data class GitHubRepositoryActivityProfile(
    val createdAtMillis: GitHubProfileField<Long>? = null,
    val updatedAtMillis: GitHubProfileField<Long>? = null,
    val pushedAtMillis: GitHubProfileField<Long>? = null,
    val stargazersCount: GitHubProfileField<Int>? = null,
    val forksCount: GitHubProfileField<Int>? = null,
    val watchersCount: GitHubProfileField<Int>? = null,
    val subscribersCount: GitHubProfileField<Int>? = null,
    val openIssuesCount: GitHubProfileField<Int>? = null,
    val sizeKb: GitHubProfileField<Int>? = null
)

data class GitHubRepositoryReleasesProfile(
    val releaseCount: GitHubProfileField<Int>? = null,
    val hasStableRelease: GitHubProfileField<Boolean>? = null,
    val latestStableTag: GitHubProfileField<String>? = null,
    val latestStableName: GitHubProfileField<String>? = null,
    val latestStablePublishedAtMillis: GitHubProfileField<Long>? = null,
    val latestStableAuthor: GitHubProfileField<String>? = null,
    val latestPreReleaseTag: GitHubProfileField<String>? = null,
    val latestPreReleaseName: GitHubProfileField<String>? = null,
    val latestPreReleasePublishedAtMillis: GitHubProfileField<Long>? = null,
    val latestPreReleaseAuthor: GitHubProfileField<String>? = null
)

data class GitHubRepositoryDistributionProfile(
    val latestAssetCount: GitHubProfileField<Int>? = null,
    val apkLikeAssetCount: GitHubProfileField<Int>? = null,
    val androidBundleAssetCount: GitHubProfileField<Int>? = null,
    val totalDownloadCount: GitHubProfileField<Int>? = null,
    val assetDigestCount: GitHubProfileField<Int>? = null,
    val hasInstallableAndroidAsset: GitHubProfileField<Boolean>? = null,
    val latestStableApkPackageName: GitHubProfileField<String>? = null,
    val latestStableApkVersionName: GitHubProfileField<String>? = null,
    val latestStableApkVersionCode: GitHubProfileField<Long>? = null
)

data class GitHubRepositoryActionsProfile(
    val workflowRunCount: GitHubProfileField<Int>? = null,
    val successfulRunCount: GitHubProfileField<Int>? = null,
    val failedRunCount: GitHubProfileField<Int>? = null,
    val latestRunStatus: GitHubProfileField<String>? = null,
    val latestRunConclusion: GitHubProfileField<String>? = null,
    val latestRunUpdatedAtMillis: GitHubProfileField<Long>? = null,
    val artifactCount: GitHubProfileField<Int>? = null,
    val nonExpiredArtifactCount: GitHubProfileField<Int>? = null,
    val androidArtifactCount: GitHubProfileField<Int>? = null
)

data class GitHubRepositoryCommunityProfile(
    val healthPercentage: GitHubProfileField<Int>? = null,
    val hasReadme: GitHubProfileField<Boolean>? = null,
    val hasLicense: GitHubProfileField<Boolean>? = null,
    val licenseName: GitHubProfileField<String>? = null,
    val licenseSpdxId: GitHubProfileField<String>? = null,
    val hasContributing: GitHubProfileField<Boolean>? = null,
    val hasCodeOfConduct: GitHubProfileField<Boolean>? = null,
    val hasIssueTemplate: GitHubProfileField<Boolean>? = null,
    val hasPullRequestTemplate: GitHubProfileField<Boolean>? = null
)

data class GitHubRepositoryTrafficProfile(
    val viewCount: GitHubProfileField<Int>? = null,
    val viewUniques: GitHubProfileField<Int>? = null,
    val cloneCount: GitHubProfileField<Int>? = null,
    val cloneUniques: GitHubProfileField<Int>? = null,
    val latestViewBucketAtMillis: GitHubProfileField<Long>? = null,
    val latestCloneBucketAtMillis: GitHubProfileField<Long>? = null
)

data class GitHubRepositoryForkSyncProfile(
    val baseFullName: GitHubProfileField<String>? = null,
    val headFullName: GitHubProfileField<String>? = null,
    val aheadBy: GitHubProfileField<Int>? = null,
    val behindBy: GitHubProfileField<Int>? = null,
    val status: GitHubProfileField<String>? = null,
    val totalCommits: GitHubProfileField<Int>? = null,
    val comparedAtMillis: GitHubProfileField<Long>? = null
)

data class GitHubRepositorySecurityProfile(
    val dependabotAlertsAvailable: GitHubProfileField<Boolean>? = null,
    val openDependabotAlertsCount: GitHubProfileField<Int>? = null,
    val codeScanningAvailable: GitHubProfileField<Boolean>? = null,
    val openCodeScanningAlertsCount: GitHubProfileField<Int>? = null,
    val secretScanningAvailable: GitHubProfileField<Boolean>? = null
)

data class GitHubRepositoryLocalFitProfile(
    val localPackageName: GitHubProfileField<String>? = null,
    val remotePackageName: GitHubProfileField<String>? = null,
    val packageNameMatched: GitHubProfileField<Boolean>? = null,
    val localVersionName: GitHubProfileField<String>? = null,
    val remoteVersionName: GitHubProfileField<String>? = null,
    val localVersionCode: GitHubProfileField<Long>? = null,
    val remoteVersionCode: GitHubProfileField<Long>? = null
)

data class GitHubRepositoryProfileSnapshot(
    val owner: String,
    val repo: String,
    val sourceConfigSignature: String,
    val fetchedAtMillis: Long,
    val identity: GitHubRepositoryIdentityProfile = GitHubRepositoryIdentityProfile(),
    val lifecycle: GitHubRepositoryLifecycleProfile = GitHubRepositoryLifecycleProfile(),
    val activity: GitHubRepositoryActivityProfile = GitHubRepositoryActivityProfile(),
    val releases: GitHubRepositoryReleasesProfile = GitHubRepositoryReleasesProfile(),
    val distribution: GitHubRepositoryDistributionProfile = GitHubRepositoryDistributionProfile(),
    val actions: GitHubRepositoryActionsProfile = GitHubRepositoryActionsProfile(),
    val community: GitHubRepositoryCommunityProfile = GitHubRepositoryCommunityProfile(),
    val traffic: GitHubRepositoryTrafficProfile = GitHubRepositoryTrafficProfile(),
    val forkSync: GitHubRepositoryForkSyncProfile = GitHubRepositoryForkSyncProfile(),
    val security: GitHubRepositorySecurityProfile = GitHubRepositorySecurityProfile(),
    val localFit: GitHubRepositoryLocalFitProfile = GitHubRepositoryLocalFitProfile(),
    val sourceAvailability: List<GitHubRepositoryProfileSourceState> = emptyList()
) {
    fun isFreshFor(
        activeSourceConfigSignature: String,
        nowMillis: Long = System.currentTimeMillis(),
        ttlMillis: Long = DEFAULT_PROFILE_CACHE_TTL_MS
    ): Boolean {
        return sourceConfigSignature == activeSourceConfigSignature &&
                nowMillis - fetchedAtMillis in 0 until ttlMillis
    }
}

data class GitHubRepositoryHealth(
    val score: Int,
    val level: GitHubDecisionLevel,
    val reasons: List<GitHubRepositoryHealthReason>
)

enum class GitHubDecisionLevel {
    Good,
    Review,
    Risk
}

enum class GitHubRepositoryHealthReason {
    RepositoryArchived,
    RepositoryDisabled,
    RepositoryFork,
    ForkUpstreamArchived,
    ForkBehindUpstream,
    ForkCompareCurrent,
    ForkCompareBehind,
    ForkMaintainedIndependently,
    ForkTracksUpstream,
    StaleRepositoryActivity,
    StaleRelease,
    TrafficRecentlyActive,
    ActionsHealthy,
    ActionsFailing,
    AndroidAssetsDetected,
    MissingAndroidAssets,
    CommunityProfileComplete,
    MissingReadme,
    MissingLicense,
    SecuritySignalsAvailable,
    OpenSecurityAlerts,
    LocalPackageMatched,
    LocalPackageMismatch,
    UpdateAvailable,
    PreReleaseRecommended,
    CheckFailed,
    MissingPackageName,
    MissingStableRelease,
    LocalMissing,
    StableDetected,
    FreshRelease
}

data class GitHubRepositoryHealthInput(
    val packageName: String = "",
    val localVersion: String = "",
    val localVersionCode: Long = -1L,
    val checkFailed: Boolean = false,
    val hasStableRelease: Boolean = true,
    val hasUpdate: Boolean? = null,
    val recommendsPreRelease: Boolean = false,
    val latestStableRawTag: String = "",
    val latestStableUpdatedAtMillis: Long = -1L,
    val latestPreUpdatedAtMillis: Long = -1L,
    val repositoryArchived: Boolean = false,
    val repositoryFork: Boolean = false,
    val repositoryPushedAtMillis: Long = -1L,
    val upstreamFullName: String = "",
    val upstreamArchived: Boolean = false,
    val upstreamPushedAtMillis: Long = -1L,
    val profile: GitHubRepositoryProfileSnapshot? = null
)
