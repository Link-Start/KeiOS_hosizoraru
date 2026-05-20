package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubProfileDepth
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryActionsProfile
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryDistributionProfile
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubRepositoryReleasesProfile
import os.kei.feature.github.model.githubProfileSourceSignature

data class GitHubRepositoryProfileRequest(
    val owner: String,
    val repo: String,
    val lookupConfig: GitHubLookupConfig,
    val purpose: GitHubRepositoryProfilePurpose = GitHubRepositoryProfilePurpose.VersionCheckFast,
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

    suspend fun fetchProfile(request: GitHubRepositoryProfileRequest): GitHubRepositoryProfileSnapshot {
        val fetchedAtMillis = System.currentTimeMillis()
        val policy = GitHubRepositoryProfileFetchPolicy.from(request)
        val sourceConfigSignature = request.lookupConfig.githubProfileSourceSignature(
            policy.capabilities
        )
        val availability = mutableListOf<GitHubRepositoryProfileSourceState>()

        val apiProfile = fetchSnapshotSource(
            source = GitHubRepositoryProfileSource.GitHubApiRepository,
            fetchedAtMillis = fetchedAtMillis,
            required = true
        ) {
            apiSource.fetch(
                owner = request.owner,
                repo = request.repo,
                apiToken = request.lookupConfig.apiToken,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
        }.also { availability += it.availability }.value

        val shouldFetchHtml = policy.requiresHtmlRepository ||
                apiProfile?.lifecycle?.archived == null
        val htmlProfile = if (shouldFetchHtml) {
            fetchSnapshotSource(
                source = GitHubRepositoryProfileSource.HtmlRepositoryPage,
                fetchedAtMillis = fetchedAtMillis,
                required = policy.requiresHtmlRepository || apiProfile == null
            ) {
                htmlSource.fetch(
                    owner = request.owner,
                    repo = request.repo,
                    fetchedAtMillis = fetchedAtMillis,
                    sourceConfigSignature = sourceConfigSignature
                )
            }.also { availability += it.availability }.value
        } else {
            availability += skipped(
                source = GitHubRepositoryProfileSource.HtmlRepositoryPage,
                fetchedAtMillis = fetchedAtMillis,
                message = "api repository profile loaded"
            )
            null
        }

        val releaseProfile = buildReleaseProfile(
            request = request,
            fetchedAtMillis = fetchedAtMillis,
            availability = availability
        )

        var distributionProfile = GitHubDistributionProfileSource.buildLocal(
            request = request,
            fetchedAtMillis = fetchedAtMillis
        )
        var actionsProfile = GitHubRepositoryActionsProfile()
        var communityProfile: GitHubRepositoryProfileSnapshot? = null

        fetchSupplementSources(
            request = request,
            policy = policy,
            fetchedAtMillis = fetchedAtMillis,
            sourceConfigSignature = sourceConfigSignature
        ).forEach { chunk ->
            chunk.distribution?.let { distributionProfile = it }
            chunk.actions?.let { actionsProfile = it }
            chunk.community?.let { communityProfile = it }
            availability += chunk.availability
        }

        val mergedIdentity = apiProfile?.identity
            ?: htmlProfile?.identity
            ?: localIdentity(request, fetchedAtMillis)
        val mergedLifecycle = mergeLifecycle(
            primary = apiProfile?.lifecycle,
            fallback = htmlProfile?.lifecycle
        )
        val mergedActivity = mergeActivity(
            primary = apiProfile?.activity,
            fallback = htmlProfile?.activity
        )
        val mergedCommunity = mergeCommunity(
            primary = communityProfile?.community,
            fallback = mergeCommunity(
                primary = apiProfile?.community,
                fallback = htmlProfile?.community
            )
        )
        val deepProfile = if (policy.requiresDeep) {
            deepSource.fetch(
                request = request,
                identity = mergedIdentity,
                lifecycle = mergedLifecycle,
                fetchedAtMillis = fetchedAtMillis
            )
        } else if (request.lookupConfig.profileDepth == GitHubProfileDepth.Deep) {
            GitHubDeepRepositoryProfileResult(
                availability = GitHubDeepRepositoryProfileSource.skippedAvailability(
                    fetchedAtMillis = fetchedAtMillis,
                    message = "deep profile requires detail purpose"
                )
            )
        } else {
            GitHubDeepRepositoryProfileResult()
        }
        availability += deepProfile.availability

        return GitHubRepositoryProfileSnapshot(
            owner = request.owner,
            repo = request.repo,
            sourceConfigSignature = sourceConfigSignature,
            fetchedAtMillis = fetchedAtMillis,
            purpose = policy.purpose,
            capabilities = policy.capabilities,
            identity = mergedIdentity,
            lifecycle = mergedLifecycle,
            activity = mergedActivity,
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

    private fun buildReleaseProfile(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long,
        availability: MutableList<GitHubRepositoryProfileSourceState>
    ): GitHubRepositoryReleasesProfile {
        val snapshot = request.releaseSnapshot
        if (snapshot == null) {
            availability += skipped(
                source = GitHubRepositoryProfileSource.AtomReleaseFeed,
                fetchedAtMillis = fetchedAtMillis,
                message = "release snapshot unavailable",
                required = true
            )
            return GitHubRepositoryReleasesProfile()
        }
        snapshot.releaseProfileSources().forEach { source ->
            availability += loaded(
                source = source,
                fetchedAtMillis = fetchedAtMillis,
                required = true
            )
        }
        return GitHubReleaseProfileSource.build(snapshot, fetchedAtMillis)
    }

    private suspend fun fetchSupplementSources(
        request: GitHubRepositoryProfileRequest,
        policy: GitHubRepositoryProfileFetchPolicy,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): List<GitHubProfileSourceChunk> {
        val tasks = buildList<suspend () -> GitHubProfileSourceChunk> {
            if (policy.requiresDistribution) {
                add {
                    val startNs = System.nanoTime()
                    val localAvailability = mutableListOf<GitHubRepositoryProfileSourceState>()
                    val profile = GitHubDistributionProfileSource.fetch(
                        request = request,
                        fetchedAtMillis = fetchedAtMillis,
                        availability = localAvailability
                    )
                    GitHubProfileSourceChunk(
                        distribution = profile,
                        availability = localAvailability.withMetadata(
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                }
            }
            if (policy.requiresActions) {
                add {
                    val startNs = System.nanoTime()
                    val localAvailability = mutableListOf<GitHubRepositoryProfileSourceState>()
                    val profile = actionsSource.fetch(
                        request = request,
                        fetchedAtMillis = fetchedAtMillis,
                        availability = localAvailability
                    )
                    GitHubProfileSourceChunk(
                        actions = profile,
                        availability = localAvailability.withMetadata(
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                }
            }
            if (policy.requiresCommunity) {
                add {
                    fetchSnapshotSource(
                        source = GitHubRepositoryProfileSource.CommunityProfileApi,
                        fetchedAtMillis = fetchedAtMillis,
                        required = true
                    ) {
                        communitySource.fetch(
                            owner = request.owner,
                            repo = request.repo,
                            apiToken = request.lookupConfig.apiToken,
                            fetchedAtMillis = fetchedAtMillis,
                            sourceConfigSignature = sourceConfigSignature
                        )
                    }.let { result ->
                        GitHubProfileSourceChunk(
                            community = result.value,
                            availability = listOf(result.availability)
                        )
                    }
                }
            }
        }
        if (tasks.isEmpty()) return emptyList()
        return GitHubExecution.mapOrderedBounded(
            items = tasks,
            maxConcurrency = SUPPLEMENT_SOURCE_CONCURRENCY
        ) { task ->
            task()
        }
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
            ),
            ownerAvatarUrl = stringField(
                "${htmlBaseUrl.trimEnd('/')}/${request.owner}.png?size=96",
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
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

    private fun mergeActivity(
        primary: GitHubRepositoryActivityProfile?,
        fallback: GitHubRepositoryActivityProfile?
    ): GitHubRepositoryActivityProfile {
        return GitHubRepositoryActivityProfile(
            createdAtMillis = primary?.createdAtMillis ?: fallback?.createdAtMillis,
            updatedAtMillis = primary?.updatedAtMillis ?: fallback?.updatedAtMillis,
            pushedAtMillis = primary?.pushedAtMillis ?: fallback?.pushedAtMillis,
            stargazersCount = primary?.stargazersCount ?: fallback?.stargazersCount,
            forksCount = primary?.forksCount ?: fallback?.forksCount,
            watchersCount = primary?.watchersCount ?: fallback?.watchersCount,
            subscribersCount = primary?.subscribersCount ?: fallback?.subscribersCount,
            openIssuesCount = primary?.openIssuesCount ?: fallback?.openIssuesCount,
            sizeKb = primary?.sizeKb ?: fallback?.sizeKb
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

    private data class GitHubProfileSourceChunk(
        val distribution: GitHubRepositoryDistributionProfile? = null,
        val actions: GitHubRepositoryActionsProfile? = null,
        val community: GitHubRepositoryProfileSnapshot? = null,
        val availability: List<GitHubRepositoryProfileSourceState> = emptyList()
    )

    private data class TimedSnapshotResult(
        val value: GitHubRepositoryProfileSnapshot?,
        val availability: GitHubRepositoryProfileSourceState
    )

    private fun fetchSnapshotSource(
        source: GitHubRepositoryProfileSource,
        fetchedAtMillis: Long,
        required: Boolean,
        fetch: () -> Result<GitHubRepositoryProfileSnapshot>
    ): TimedSnapshotResult {
        val startNs = System.nanoTime()
        val result = fetch()
        val elapsedMs = elapsedMsSince(startNs)
        return result.fold(
            onSuccess = { profile ->
                TimedSnapshotResult(
                    value = profile,
                    availability = loaded(
                        source = source,
                        fetchedAtMillis = fetchedAtMillis,
                        elapsedMs = elapsedMs,
                        required = required
                    )
                )
            },
            onFailure = { error ->
                TimedSnapshotResult(
                    value = null,
                    availability = failed(
                        source = source,
                        fetchedAtMillis = fetchedAtMillis,
                        error = error,
                        elapsedMs = elapsedMs,
                        required = required
                    )
                )
            }
        )
    }

    private fun List<GitHubRepositoryProfileSourceState>.withMetadata(
        elapsedMs: Long,
        required: Boolean
    ): List<GitHubRepositoryProfileSourceState> {
        return map { state ->
            state.copy(
                elapsedMs = state.elapsedMs.takeIf { it > 0L } ?: elapsedMs,
                required = required
            )
        }
    }

    private fun elapsedMsSince(startNs: Long): Long {
        return ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
    }

    private companion object {
        private const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_GITHUB_HTML_BASE_URL = "https://github.com"
        private const val SUPPLEMENT_SOURCE_CONCURRENCY = 2
    }
}
