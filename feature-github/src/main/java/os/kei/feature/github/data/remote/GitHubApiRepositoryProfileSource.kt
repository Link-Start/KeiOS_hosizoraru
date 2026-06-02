package os.kei.feature.github.data.remote

import kotlinx.serialization.json.JsonObject
import os.kei.core.json.optArray
import os.kei.core.json.optBoolean
import os.kei.core.json.optInt
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile

class GitHubApiRepositoryProfileSource(
    private val http: GitHubRepositoryProfileHttpClient
) {
    fun fetch(
        owner: String,
        repo: String,
        apiToken: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): Result<GitHubRepositoryProfileSnapshot> {
        return http.fetchJson(http.repositoryApiUrl(owner, repo), apiToken).mapCatching { body ->
            parse(
                json = body,
                fallbackOwner = owner,
                fallbackRepo = repo,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
        }
    }

    fun parse(
        json: String,
        fallbackOwner: String,
        fallbackRepo: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): GitHubRepositoryProfileSnapshot {
        val root = json.parseJsonObjectOrNull() ?: throw IllegalArgumentException("invalid repository profile payload")
        val source = GitHubRepositoryProfileSource.GitHubApiRepository
        val license = root.optObject("license")
        val owner = root.optObject("owner")
        val parent = root.optObject("parent") ?: root.optObject("source")
        val identity = GitHubRepositoryIdentityProfile(
            owner = stringField(
                owner?.optString("login").orEmpty().ifBlank { fallbackOwner },
                source,
                fetchedAtMillis
            ),
            name = stringField(
                root.optString("name").ifBlank { fallbackRepo },
                source,
                fetchedAtMillis
            ),
            fullName = stringField(
                root.optString("full_name").ifBlank { "$fallbackOwner/$fallbackRepo" },
                source,
                fetchedAtMillis
            ),
            htmlUrl = stringField(root.optString("html_url"), source, fetchedAtMillis),
            ownerAvatarUrl = stringField(
                owner?.optString("avatar_url").orEmpty(),
                source,
                fetchedAtMillis
            ),
            defaultBranch = stringField(root.optString("default_branch"), source, fetchedAtMillis),
            ownerType = stringField(owner?.optString("type").orEmpty(), source, fetchedAtMillis),
            visibility = stringField(root.optString("visibility"), source, fetchedAtMillis),
            privateRepository = booleanField(
                root.optBoolean("private", false),
                source,
                fetchedAtMillis
            ),
            topics = listField(root.optArray("topics").toStringList(), source, fetchedAtMillis)
        )
        val lifecycle = GitHubRepositoryLifecycleProfile(
            archived = booleanField(root.optBoolean("archived", false), source, fetchedAtMillis),
            disabled = booleanField(root.optBoolean("disabled", false), source, fetchedAtMillis),
            fork = booleanField(root.optBoolean("fork", false), source, fetchedAtMillis),
            mirrorUrl = stringField(root.optString("mirror_url"), source, fetchedAtMillis),
            upstream = parent?.toUpstreamProfile(source, fetchedAtMillis)
        )
        val activity = GitHubRepositoryActivityProfile(
            createdAtMillis = longField(
                root.optString("created_at").parseIsoInstantOrDefault(),
                source,
                fetchedAtMillis
            ),
            updatedAtMillis = longField(
                root.optString("updated_at").parseIsoInstantOrDefault(),
                source,
                fetchedAtMillis
            ),
            pushedAtMillis = longField(
                root.optString("pushed_at").parseIsoInstantOrDefault(),
                source,
                fetchedAtMillis
            ),
            stargazersCount = intField(root.optInt("stargazers_count", 0), source, fetchedAtMillis),
            forksCount = intField(root.optInt("forks_count", 0), source, fetchedAtMillis),
            watchersCount = intField(root.optInt("watchers_count", 0), source, fetchedAtMillis),
            subscribersCount = intField(
                root.optInt("subscribers_count", 0),
                source,
                fetchedAtMillis
            ),
            openIssuesCount = intField(
                root.optInt("open_issues_count", 0),
                source,
                fetchedAtMillis
            ),
            sizeKb = intField(root.optInt("size", 0), source, fetchedAtMillis)
        )
        val community = GitHubRepositoryCommunityProfile(
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
            )
        )
        return GitHubRepositoryProfileSnapshot(
            owner = fallbackOwner,
            repo = fallbackRepo,
            sourceConfigSignature = sourceConfigSignature,
            fetchedAtMillis = fetchedAtMillis,
            identity = identity,
            lifecycle = lifecycle,
            activity = activity,
            community = community,
            sourceAvailability = listOf(loaded(source, fetchedAtMillis))
        )
    }

    private fun JsonObject.toUpstreamProfile(
        source: GitHubRepositoryProfileSource,
        fetchedAtMillis: Long
    ): GitHubRepositoryUpstreamProfile {
        return GitHubRepositoryUpstreamProfile(
            fullName = stringField(optString("full_name"), source, fetchedAtMillis),
            htmlUrl = stringField(optString("html_url"), source, fetchedAtMillis),
            archived = booleanField(optBoolean("archived", false), source, fetchedAtMillis),
            disabled = booleanField(optBoolean("disabled", false), source, fetchedAtMillis),
            pushedAtMillis = longField(
                optString("pushed_at").parseIsoInstantOrDefault(),
                source,
                fetchedAtMillis
            ),
            defaultBranch = stringField(optString("default_branch"), source, fetchedAtMillis)
        )
    }
}
