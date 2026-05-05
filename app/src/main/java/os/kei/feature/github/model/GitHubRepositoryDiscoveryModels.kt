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
