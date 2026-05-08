package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource

internal class GitHubHtmlRepositoryProfileSource(
    private val http: GitHubRepositoryProfileHttpClient
) {
    fun fetch(
        owner: String,
        repo: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): Result<GitHubRepositoryProfileSnapshot> {
        return http.fetchHtml(http.htmlRepositoryUrl(owner, repo)).mapCatching { html ->
            parse(
                html = html,
                owner = owner,
                repo = repo,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
        }
    }

    fun parse(
        html: String,
        owner: String,
        repo: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): GitHubRepositoryProfileSnapshot {
        val source = GitHubRepositoryProfileSource.HtmlRepositoryPage
        val archived = html.contains("repository has been archived", ignoreCase = true) ||
                html.contains("This repository has been archived", ignoreCase = true) ||
                html.contains("archived by the owner", ignoreCase = true)
        val identity = GitHubRepositoryIdentityProfile(
            owner = profileField(
                owner,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            name = profileField(
                repo,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            fullName = profileField(
                "$owner/$repo",
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            htmlUrl = profileField(
                http.htmlRepositoryUrl(owner, repo),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            )
        )
        val lifecycle = GitHubRepositoryLifecycleProfile(
            archived = profileField(
                archived,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            )
        )
        return GitHubRepositoryProfileSnapshot(
            owner = owner,
            repo = repo,
            sourceConfigSignature = sourceConfigSignature,
            fetchedAtMillis = fetchedAtMillis,
            identity = identity,
            lifecycle = lifecycle,
            sourceAvailability = listOf(loaded(source, fetchedAtMillis))
        )
    }
}
