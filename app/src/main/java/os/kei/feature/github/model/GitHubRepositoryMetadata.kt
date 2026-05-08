package os.kei.feature.github.model

data class GitHubRepositoryMetadata(
    val fullName: String,
    val archived: Boolean = false,
    val fork: Boolean = false,
    val pushedAtMillis: Long = -1L,
    val upstreamFullName: String = "",
    val upstreamArchived: Boolean = false,
    val upstreamPushedAtMillis: Long = -1L
)
