package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubRepositoryReleasesProfile
import os.kei.feature.github.model.githubCheckSourceSignature

data class GitHubRepositoryProfileRequest(
    val owner: String,
    val repo: String,
    val lookupConfig: GitHubLookupConfig,
    val releaseSnapshot: GitHubRepositoryReleaseSnapshot? = null,
    val localPackageName: String = "",
    val localVersionName: String = "",
    val localVersionCode: Long = -1L,
    val preciseStableApkVersion: GitHubRemoteApkVersionInfo? = null,
    val precisePreReleaseApkVersion: GitHubRemoteApkVersionInfo? = null
)

class GitHubRepositoryProfileRepository(
    private val client: OkHttpClient = GitHubRepositoryProfileHttpClient.githubClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL,
    private val htmlBaseUrl: String = DEFAULT_GITHUB_HTML_BASE_URL
) {
    private val http = GitHubRepositoryProfileHttpClient(client, apiBaseUrl, htmlBaseUrl)
    private val apiSource = GitHubApiRepositoryProfileSource(http)
    private val htmlSource = GitHubHtmlRepositoryProfileSource(http)
    private val communitySource = GitHubCommunityProfileSource(http)
    private val actionsSource = GitHubActionsProfileSource(client, apiBaseUrl)
    private val deepSource = GitHubDeepRepositoryProfileSource(http)

    fun fetchProfile(request: GitHubRepositoryProfileRequest): GitHubRepositoryProfileSnapshot {
        val fetchedAtMillis = System.currentTimeMillis()
        val sourceConfigSignature = request.lookupConfig.githubCheckSourceSignature()
        val availability = mutableListOf<GitHubRepositoryProfileSourceState>()

        val apiProfile = apiSource.fetch(
            owner = request.owner,
            repo = request.repo,
            apiToken = request.lookupConfig.apiToken,
            fetchedAtMillis = fetchedAtMillis,
            sourceConfigSignature = sourceConfigSignature
        )
            .onSuccess {
                availability += loaded(
                    GitHubRepositoryProfileSource.GitHubApiRepository,
                    fetchedAtMillis
                )
            }
            .onFailure { error ->
                availability += failed(
                    GitHubRepositoryProfileSource.GitHubApiRepository,
                    fetchedAtMillis,
                    error
                )
            }
            .getOrNull()

        val htmlProfile = if (apiProfile?.lifecycle?.archived == null) {
            htmlSource.fetch(
                owner = request.owner,
                repo = request.repo,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
                .onSuccess {
                    availability += loaded(
                        GitHubRepositoryProfileSource.HtmlRepositoryPage,
                        fetchedAtMillis
                    )
                }
                .onFailure { error ->
                    availability += failed(
                        GitHubRepositoryProfileSource.HtmlRepositoryPage,
                        fetchedAtMillis,
                        error
                    )
                }
                .getOrNull()
        } else {
            availability += skipped(
                source = GitHubRepositoryProfileSource.HtmlRepositoryPage,
                fetchedAtMillis = fetchedAtMillis,
                message = "api repository profile loaded"
            )
            null
        }

        val releaseProfile = request.releaseSnapshot?.let { snapshot ->
            availability += loaded(snapshot.releaseProfileSource(), fetchedAtMillis)
            GitHubReleaseProfileSource.build(snapshot, fetchedAtMillis)
        } ?: run {
            availability += skipped(
                source = GitHubRepositoryProfileSource.AtomReleaseFeed,
                fetchedAtMillis = fetchedAtMillis,
                message = "release snapshot unavailable"
            )
            GitHubRepositoryReleasesProfile()
        }

        val distributionProfile = GitHubDistributionProfileSource.fetch(
            request = request,
            fetchedAtMillis = fetchedAtMillis,
            availability = availability
        )
        val actionsProfile = actionsSource.fetch(
            request = request,
            fetchedAtMillis = fetchedAtMillis,
            availability = availability
        )

        val communityProfile = communitySource.fetch(
            owner = request.owner,
            repo = request.repo,
            apiToken = request.lookupConfig.apiToken,
            fetchedAtMillis = fetchedAtMillis,
            sourceConfigSignature = sourceConfigSignature
        )
            .onSuccess {
                availability += loaded(
                    GitHubRepositoryProfileSource.CommunityProfileApi,
                    fetchedAtMillis
                )
            }
            .onFailure { error ->
                availability += failed(
                    GitHubRepositoryProfileSource.CommunityProfileApi,
                    fetchedAtMillis,
                    error
                )
            }
            .getOrNull()

        val mergedIdentity = apiProfile?.identity
            ?: htmlProfile?.identity
            ?: localIdentity(request, fetchedAtMillis)
        val mergedLifecycle = mergeLifecycle(
            primary = apiProfile?.lifecycle,
            fallback = htmlProfile?.lifecycle
        )
        val mergedCommunity = mergeCommunity(
            primary = communityProfile?.community,
            fallback = apiProfile?.community
        )
        val deepProfile = if (request.lookupConfig.profileDepth == GitHubProfileDepth.Deep) {
            deepSource.fetch(
                request = request,
                identity = mergedIdentity,
                lifecycle = mergedLifecycle,
                fetchedAtMillis = fetchedAtMillis
            )
        } else {
            GitHubDeepRepositoryProfileResult(
                availability = GitHubDeepRepositoryProfileSource.skippedAvailability(fetchedAtMillis)
            )
        }
        availability += deepProfile.availability

        return GitHubRepositoryProfileSnapshot(
            owner = request.owner,
            repo = request.repo,
            sourceConfigSignature = sourceConfigSignature,
            fetchedAtMillis = fetchedAtMillis,
            identity = mergedIdentity,
            lifecycle = mergedLifecycle,
            activity = apiProfile?.activity ?: GitHubRepositoryActivityProfile(),
            releases = releaseProfile,
            distribution = distributionProfile,
            actions = actionsProfile,
            community = mergedCommunity,
            traffic = deepProfile.traffic,
            forkSync = deepProfile.forkSync,
            security = deepProfile.security,
            localFit = GitHubLocalFitProfileSource.build(request, fetchedAtMillis),
            sourceAvailability = availability.distinctBy { it.source }
        )
    }

    private fun localIdentity(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubRepositoryIdentityProfile {
        val source = GitHubRepositoryProfileSource.LocalInstall
        return GitHubRepositoryIdentityProfile(
            owner = profileField(
                request.owner,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            name = profileField(
                request.repo,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            fullName = profileField(
                "${request.owner}/${request.repo}",
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            )
        )
    }

    private fun mergeLifecycle(
        primary: GitHubRepositoryLifecycleProfile?,
        fallback: GitHubRepositoryLifecycleProfile?
    ): GitHubRepositoryLifecycleProfile {
        return GitHubRepositoryLifecycleProfile(
            archived = primary?.archived ?: fallback?.archived,
            disabled = primary?.disabled ?: fallback?.disabled,
            fork = primary?.fork ?: fallback?.fork,
            mirrorUrl = primary?.mirrorUrl ?: fallback?.mirrorUrl,
            upstream = primary?.upstream ?: fallback?.upstream
        )
    }

    private fun mergeCommunity(
        primary: GitHubRepositoryCommunityProfile?,
        fallback: GitHubRepositoryCommunityProfile?
    ): GitHubRepositoryCommunityProfile {
        return GitHubRepositoryCommunityProfile(
            healthPercentage = primary?.healthPercentage ?: fallback?.healthPercentage,
            hasReadme = primary?.hasReadme ?: fallback?.hasReadme,
            hasLicense = primary?.hasLicense ?: fallback?.hasLicense,
            licenseName = primary?.licenseName ?: fallback?.licenseName,
            licenseSpdxId = primary?.licenseSpdxId ?: fallback?.licenseSpdxId,
            hasContributing = primary?.hasContributing ?: fallback?.hasContributing,
            hasCodeOfConduct = primary?.hasCodeOfConduct ?: fallback?.hasCodeOfConduct,
            hasIssueTemplate = primary?.hasIssueTemplate ?: fallback?.hasIssueTemplate,
            hasPullRequestTemplate = primary?.hasPullRequestTemplate
                ?: fallback?.hasPullRequestTemplate
        )
    }

    private companion object {
        private const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_GITHUB_HTML_BASE_URL = "https://github.com"
    }
}
