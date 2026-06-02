package os.kei.feature.github.domain

class GitHubRepositoryDiscoveryHttpException(
    val statusCode: Int,
    val responseMessage: String,
) : IllegalStateException(
    "GitHub discovery request failed: HTTP $statusCode ${responseMessage.trim()}".trim(),
)
