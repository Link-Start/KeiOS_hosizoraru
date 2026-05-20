package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot

interface GitHubReleaseLookupStrategy {
    val id: String

    fun loadSnapshot(owner: String, repo: String): Result<GitHubRepositoryReleaseSnapshot>

    fun clearCaches()
}
