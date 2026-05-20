package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubRepositoryProfileCapability
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.requiredCapabilities

data class GitHubRepositoryProfileFetchPolicy(
    val purpose: GitHubRepositoryProfilePurpose,
    val profileDepth: GitHubProfileDepth,
    val capabilities: Set<GitHubRepositoryProfileCapability>
) {
    val requiresDistribution: Boolean
        get() = GitHubRepositoryProfileCapability.Distribution in capabilities

    val requiresActions: Boolean
        get() = GitHubRepositoryProfileCapability.Actions in capabilities

    val requiresCommunity: Boolean
        get() = GitHubRepositoryProfileCapability.Community in capabilities

    val requiresHtmlRepository: Boolean
        get() = GitHubRepositoryProfileCapability.HtmlRepository in capabilities

    val requiresDeep: Boolean
        get() = capabilities.any {
            it == GitHubRepositoryProfileCapability.Traffic ||
                    it == GitHubRepositoryProfileCapability.ForkSync ||
                    it == GitHubRepositoryProfileCapability.Security
        }

    companion object {
        fun from(request: GitHubRepositoryProfileRequest): GitHubRepositoryProfileFetchPolicy {
            return GitHubRepositoryProfileFetchPolicy(
                purpose = request.purpose,
                profileDepth = request.lookupConfig.profileDepth,
                capabilities = request.purpose.requiredCapabilities(request.lookupConfig.profileDepth)
            )
        }
    }
}
