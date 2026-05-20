package os.kei.feature.github.data.remote

import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption

object GitHubReleaseStrategyRegistry {
    fun loadLookupConfig(): GitHubLookupConfig = GitHubTrackStore.loadLookupConfig()

    fun resolveConfiguredStrategy(): Result<GitHubReleaseLookupStrategy> {
        val config = loadLookupConfig()
        return when (config.selectedStrategy) {
            GitHubLookupStrategyOption.AtomFeed -> Result.success(GitHubAtomReleaseStrategy)
            GitHubLookupStrategyOption.GitHubApiToken ->
                Result.success(GitHubApiTokenReleaseStrategy(config.apiToken.trim()))
        }
    }

    fun clearAllCaches() {
        GitHubAtomReleaseStrategy.clearCaches()
        GitHubApiTokenReleaseStrategy.clearSharedCaches()
    }
}
