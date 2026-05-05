package os.kei.feature.github.model

enum class GitHubRepositoryDiscoverySourceType {
    AuthenticatedStars,
    PublicUserStars,
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
    val username: String = "",
    val apiToken: String = "",
    val limit: Int = 300
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
    val lookupConfig: GitHubLookupConfig,
    val candidateLimit: Int = 20,
    val verificationLimit: Int = 8
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
