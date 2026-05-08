package os.kei.feature.github.data.remote

import org.json.JSONObject
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource

internal class GitHubCommunityProfileSource(
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
        val root = JSONObject(json)
        val files = root.optJSONObject("files")
        val license = files?.optJSONObject("license") ?: root.optJSONObject("license")
        val source = GitHubRepositoryProfileSource.CommunityProfileApi
        val community = GitHubRepositoryCommunityProfile(
            healthPercentage = intField(
                root.optInt("health_percentage", 0),
                source,
                fetchedAtMillis
            ),
            hasReadme = booleanField(
                files?.optJSONObject("readme") != null,
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
                files?.optJSONObject("contributing") != null,
                source,
                fetchedAtMillis
            ),
            hasCodeOfConduct = booleanField(
                files?.optJSONObject("code_of_conduct") != null,
                source,
                fetchedAtMillis
            ),
            hasIssueTemplate = booleanField(
                files?.optJSONObject("issue_template") != null,
                source,
                fetchedAtMillis
            ),
            hasPullRequestTemplate = booleanField(
                files?.optJSONObject("pull_request_template") != null,
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
