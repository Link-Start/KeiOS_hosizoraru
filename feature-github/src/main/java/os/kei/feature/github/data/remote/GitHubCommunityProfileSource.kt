package os.kei.feature.github.data.remote

import os.kei.core.json.optInt
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource

class GitHubCommunityProfileSource(
    private val http: GitHubRepositoryProfileHttpClient
) {
    fun fetch(
        owner: String,
        repo: String,
        apiToken: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): Result<GitHubRepositoryProfileSnapshot> {
        return http.fetchJson(http.communityProfileUrl(owner, repo), apiToken).mapCatching { body ->
            parse(
                json = body,
                owner = owner,
                repo = repo,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
        }
    }

    fun parse(
        json: String,
        owner: String,
        repo: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): GitHubRepositoryProfileSnapshot {
        val root = json.parseJsonObjectOrNull() ?: throw IllegalArgumentException("invalid community profile payload")
        val files = root.optObject("files")
        val license = files?.optObject("license") ?: root.optObject("license")
        val source = GitHubRepositoryProfileSource.CommunityProfileApi
        val community = GitHubRepositoryCommunityProfile(
            healthPercentage = intField(
                root.optInt("health_percentage", 0),
                source,
                fetchedAtMillis
            ),
            hasReadme = booleanField(
                files?.optObject("readme") != null,
                source,
                fetchedAtMillis
            ),
            hasLicense = booleanField(license != null, source, fetchedAtMillis),
            licenseName = stringField(
                license?.optString("name").orEmpty(),
                source,
                fetchedAtMillis
            ),
            licenseSpdxId = stringField(
                license?.optString("spdx_id").orEmpty(),
                source,
                fetchedAtMillis
            ),
            hasContributing = booleanField(
                files?.optObject("contributing") != null,
                source,
                fetchedAtMillis
            ),
            hasCodeOfConduct = booleanField(
                files?.optObject("code_of_conduct") != null,
                source,
                fetchedAtMillis
            ),
            hasIssueTemplate = booleanField(
                files?.optObject("issue_template") != null,
                source,
                fetchedAtMillis
            ),
            hasPullRequestTemplate = booleanField(
                files?.optObject("pull_request_template") != null,
                source,
                fetchedAtMillis
            )
        )
        return GitHubRepositoryProfileSnapshot(
            owner = owner,
            repo = repo,
            sourceConfigSignature = sourceConfigSignature,
            fetchedAtMillis = fetchedAtMillis,
            community = community,
            sourceAvailability = listOf(loaded(source, fetchedAtMillis))
        )
    }
}
