package os.kei.feature.github.model

enum class GitHubRepositoryDiscoverySourceType {
    AuthenticatedStars,
    PublicUserStars,
    PreferredRepository,
    StarList,
    RepositorySearch
}

enum class GitHubRepositoryCandidateMatchReason {
    Starred,
    PackageName,
    AppLabel,
    RepositoryName
}

data class GitHubRepositoryCandidate(
    val owner: String,
    val repo: String,
    val repoUrl: String,
    val description: String = "",
    val language: String = "",
    val starCount: Int = 0,
    val forkCount: Int = 0,
    val archived: Boolean = false,
    val fork: Boolean = false,
    val updatedAtMillis: Long = -1L,
    val sourceType: GitHubRepositoryDiscoverySourceType,
    val matchReason: GitHubRepositoryCandidateMatchReason
) {
    val fullName: String
        get() = "$owner/$repo"
}

data class GitHubStarredRepositoryImportRequest(
    val source: GitHubStarredRepositoryImportSource = GitHubStarredRepositoryImportSource.Auto,
    val username: String = "",
    val starListUrl: String = "",
    val apiToken: String = "",
    val limit: Int = 300
)

enum class GitHubStarredRepositoryImportSource {
    Auto,
    AuthenticatedUser,
    PublicUser,
    StarListUrl
}

data class GitHubStarListSummary(
    val name: String,
    val repositoryCount: Int,
    val url: String
)

data class GitHubAppRepositorySearchRequest(
    val app: InstalledAppItem,
    val apiToken: String = "",
    val limit: Int = 20
)

data class GitHubRepositoryImportCandidate(
    val repository: GitHubRepositoryCandidate,
    val trackedApp: GitHubTrackedApp,
    val alreadyTracked: Boolean,
    val score: Int
)

data class StarImportApplyResult(
    val addedCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val affectedTrackIds: Set<String> = emptySet(),
    val removedTrackIds: Set<String> = emptySet(),
    val affectedPackages: Set<String> = emptySet()
) {
    val changedCount: Int
        get() = addedCount + updatedCount

    val hasChanges: Boolean
        get() = changedCount > 0 || removedTrackIds.isNotEmpty()
}

enum class GitHubStarImportQuality {
    LikelyAndroid,
    NeedsReview,
    OtherPlatform,
    ArchivedOrFork
}

enum class GitHubStarImportApkVerificationStatus {
    HasApk,
    NoApk,
    Failed
}

data class GitHubStarImportApkVerification(
    val owner: String,
    val repo: String,
    val status: GitHubStarImportApkVerificationStatus,
    val releaseTag: String = "",
    val releaseUrl: String = "",
    val apkAssetCount: Int = 0,
    val sampleAssetName: String = "",
    val packageName: String = "",
    val checkedAtMillis: Long = 0L,
    val errorMessage: String = "",
    val fromCache: Boolean = false
)

data class GitHubStarredRepositoryImportPreview(
    val sourceLabel: String,
    val totalFetchedCount: Int,
    val importableCount: Int,
    val alreadyTrackedCount: Int,
    val candidates: List<GitHubRepositoryImportCandidate>
)

data class GitHubAppRepositorySearchResult(
    val app: InstalledAppItem,
    val queryCount: Int,
    val candidates: List<GitHubRepositoryImportCandidate>
)

data class GitHubPackageRepositoryScanRequest(
    val packageName: String,
    val appLabel: String = "",
    val preferredRepoUrl: String = "",
    val lookupConfig: GitHubLookupConfig,
    val candidateLimit: Int = 16,
    val verificationLimit: Int = 5
)

data class GitHubPackageRepositoryScanCandidate(
    val repository: GitHubRepositoryCandidate,
    val trackedApp: GitHubTrackedApp,
    val score: Int,
    val releaseTag: String,
    val releaseUrl: String,
    val assetName: String
)

data class GitHubPackageRepositoryScanResult(
    val packageName: String,
    val appLabel: String,
    val queryCount: Int,
    val fetchedCandidateCount: Int,
    val scannedCandidateCount: Int,
    val matchedCandidates: List<GitHubPackageRepositoryScanCandidate>,
    val mismatchedCandidateCount: Int,
    val failedCandidateCount: Int
)

data class GitHubApkPackageNameScanRequest(
    val repoUrl: String,
    val lookupConfig: GitHubLookupConfig
)

data class GitHubApkPackageNameScanResult(
    val owner: String,
    val repo: String,
    val releaseTag: String,
    val releaseUrl: String,
    val assetName: String,
    val packageName: String
)
