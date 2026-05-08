package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsWorkflowRun
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubRemoteApkVersionInfo
import os.kei.feature.github.model.GitHubRepositoryActionsProfile
import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryDistributionProfile
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryLocalFitProfile
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubRepositoryReleasesProfile
import os.kei.feature.github.model.GitHubRepositorySecurityProfile
import os.kei.feature.github.model.GitHubRepositoryUpstreamProfile
import os.kei.feature.github.model.githubCheckSourceSignature
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

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
    private val client: OkHttpClient = githubClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL,
    private val htmlBaseUrl: String = DEFAULT_GITHUB_HTML_BASE_URL
) {
    fun fetchProfile(request: GitHubRepositoryProfileRequest): GitHubRepositoryProfileSnapshot {
        val fetchedAtMillis = System.currentTimeMillis()
        val sourceConfigSignature = request.lookupConfig.githubCheckSourceSignature()
        val availability = mutableListOf<GitHubRepositoryProfileSourceState>()

        val apiProfile = fetchApiRepositoryProfile(
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
                    source = GitHubRepositoryProfileSource.GitHubApiRepository,
                    fetchedAtMillis = fetchedAtMillis,
                    error = error
                )
            }
            .getOrNull()

        val htmlProfile = if (apiProfile?.lifecycle?.archived == null) {
            fetchHtmlRepositoryProfile(
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
                        source = GitHubRepositoryProfileSource.HtmlRepositoryPage,
                        fetchedAtMillis = fetchedAtMillis,
                        error = error
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
            buildReleasesProfileFromSnapshot(snapshot, fetchedAtMillis)
        } ?: run {
            availability += skipped(
                source = GitHubRepositoryProfileSource.AtomReleaseFeed,
                fetchedAtMillis = fetchedAtMillis,
                message = "release snapshot unavailable"
            )
            GitHubRepositoryReleasesProfile()
        }

        val distributionProfile = fetchDistributionProfile(
            request = request,
            fetchedAtMillis = fetchedAtMillis,
            availability = availability
        )

        val actionsProfile = fetchActionsProfile(
            request = request,
            fetchedAtMillis = fetchedAtMillis,
            availability = availability
        )

        val communityProfile = fetchCommunityProfile(
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
                    source = GitHubRepositoryProfileSource.CommunityProfileApi,
                    fetchedAtMillis = fetchedAtMillis,
                    error = error
                )
            }
            .getOrNull()

        availability += skipped(
            source = GitHubRepositoryProfileSource.OptionalEnhancedEndpoint,
            fetchedAtMillis = fetchedAtMillis,
            message = "traffic, security, and contributor endpoints are reserved for deep profile mode"
        )

        val mergedIdentity = apiProfile?.identity
            ?: htmlProfile?.identity
            ?: GitHubRepositoryIdentityProfile(
                owner = field(
                    value = request.owner,
                    source = GitHubRepositoryProfileSource.LocalInstall,
                    fetchedAtMillis = fetchedAtMillis,
                    confidence = GitHubRepositoryProfileConfidence.Medium
                ),
                name = field(
                    value = request.repo,
                    source = GitHubRepositoryProfileSource.LocalInstall,
                    fetchedAtMillis = fetchedAtMillis,
                    confidence = GitHubRepositoryProfileConfidence.Medium
                ),
                fullName = field(
                    value = "${request.owner}/${request.repo}",
                    source = GitHubRepositoryProfileSource.LocalInstall,
                    fetchedAtMillis = fetchedAtMillis,
                    confidence = GitHubRepositoryProfileConfidence.Medium
                )
            )
        val mergedLifecycle = mergeLifecycle(
            primary = apiProfile?.lifecycle,
            fallback = htmlProfile?.lifecycle
        )
        val mergedCommunity = mergeCommunity(
            primary = communityProfile?.community,
            fallback = apiProfile?.community
        )

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
            security = GitHubRepositorySecurityProfile(),
            localFit = buildLocalFitProfile(request, fetchedAtMillis),
            sourceAvailability = availability.distinctBy { it.source }
        )
    }

    internal fun fetchApiRepositoryProfile(
        owner: String,
        repo: String,
        apiToken: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): Result<GitHubRepositoryProfileSnapshot> {
        return fetchJson(buildRepositoryApiUrl(owner, repo), apiToken).mapCatching { body ->
            parseApiRepositoryProfile(
                json = body,
                fallbackOwner = owner,
                fallbackRepo = repo,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
        }
    }

    internal fun parseApiRepositoryProfile(
        json: String,
        fallbackOwner: String,
        fallbackRepo: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): GitHubRepositoryProfileSnapshot {
        val root = JSONObject(json)
        val source = GitHubRepositoryProfileSource.GitHubApiRepository
        val license = root.optJSONObject("license")
        val owner = root.optJSONObject("owner")
        val parent = root.optJSONObject("parent") ?: root.optJSONObject("source")
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
            defaultBranch = stringField(root.optString("default_branch"), source, fetchedAtMillis),
            ownerType = stringField(owner?.optString("type").orEmpty(), source, fetchedAtMillis),
            visibility = stringField(root.optString("visibility"), source, fetchedAtMillis),
            privateRepository = booleanField(
                root.optBoolean("private", false),
                source,
                fetchedAtMillis
            ),
            topics = listField(root.optJSONArray("topics").toStringList(), source, fetchedAtMillis)
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

    internal fun fetchHtmlRepositoryProfile(
        owner: String,
        repo: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): Result<GitHubRepositoryProfileSnapshot> {
        return fetchHtml("$htmlBaseUrl/$owner/$repo").mapCatching { html ->
            parseHtmlRepositoryProfile(
                html = html,
                owner = owner,
                repo = repo,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
        }
    }

    internal fun parseHtmlRepositoryProfile(
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
            owner = field(owner, source, fetchedAtMillis, GitHubRepositoryProfileConfidence.Low),
            name = field(repo, source, fetchedAtMillis, GitHubRepositoryProfileConfidence.Low),
            fullName = field(
                "$owner/$repo",
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            htmlUrl = field(
                "$htmlBaseUrl/$owner/$repo",
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            )
        )
        val lifecycle = GitHubRepositoryLifecycleProfile(
            archived = field(
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

    internal fun buildReleasesProfileFromSnapshot(
        snapshot: GitHubRepositoryReleaseSnapshot,
        fetchedAtMillis: Long
    ): GitHubRepositoryReleasesProfile {
        val stable = snapshot.latestStable.takeIf { snapshot.hasStableRelease }
        val pre = snapshot.latestPreRelease
        return GitHubRepositoryReleasesProfile(
            releaseCount = intField(
                snapshot.feed.entries.size,
                snapshot.releaseProfileSource(),
                fetchedAtMillis
            ),
            hasStableRelease = booleanField(
                snapshot.hasStableRelease,
                snapshot.releaseProfileSource(),
                fetchedAtMillis
            ),
            latestStableTag = stringField(
                stable?.rawTag.orEmpty(),
                stable.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestStableName = stringField(
                stable?.rawName.orEmpty(),
                stable.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestStablePublishedAtMillis = longField(
                stable?.updatedAtMillis ?: -1L,
                stable.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestStableAuthor = stringField(
                stable?.authorName.orEmpty(),
                stable.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestPreReleaseTag = stringField(
                pre?.rawTag.orEmpty(),
                pre.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestPreReleaseName = stringField(
                pre?.rawName.orEmpty(),
                pre.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestPreReleasePublishedAtMillis = longField(
                pre?.updatedAtMillis ?: -1L,
                pre.releaseProfileSource(snapshot),
                fetchedAtMillis
            ),
            latestPreReleaseAuthor = stringField(
                pre?.authorName.orEmpty(),
                pre.releaseProfileSource(snapshot),
                fetchedAtMillis
            )
        )
    }

    internal fun fetchCommunityProfile(
        owner: String,
        repo: String,
        apiToken: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): Result<GitHubRepositoryProfileSnapshot> {
        return fetchJson(buildCommunityProfileUrl(owner, repo), apiToken).mapCatching { body ->
            parseCommunityProfile(
                json = body,
                owner = owner,
                repo = repo,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
        }
    }

    internal fun parseCommunityProfile(
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

    private fun fetchDistributionProfile(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long,
        availability: MutableList<GitHubRepositoryProfileSourceState>
    ): GitHubRepositoryDistributionProfile {
        val stable = request.releaseSnapshot
            ?.latestStable
            ?.takeIf { request.releaseSnapshot.hasStableRelease }
        val rawTag = stable?.rawTag.orEmpty()
        if (rawTag.isBlank()) {
            availability += skipped(
                source = GitHubRepositoryProfileSource.ReleaseAssetsApi,
                fetchedAtMillis = fetchedAtMillis,
                message = "stable release unavailable"
            )
            return buildLocalDistributionProfile(request, fetchedAtMillis)
        }
        val bundleResult = GitHubReleaseAssetRepository.fetchApkAssets(
            owner = request.owner,
            repo = request.repo,
            rawTag = rawTag,
            releaseUrl = stable?.link.orEmpty(),
            preferHtml = request.lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed,
            aggressiveFiltering = false,
            includeAllAssets = true,
            apiToken = request.lookupConfig.apiToken
        )
        val bundle = bundleResult.getOrNull()
        if (bundle == null) {
            availability += failed(
                source = GitHubRepositoryProfileSource.ReleaseAssetsApi,
                fetchedAtMillis = fetchedAtMillis,
                error = bundleResult.exceptionOrNull()
                    ?: IllegalStateException("release asset fetch failed")
            )
            return buildLocalDistributionProfile(request, fetchedAtMillis)
        }
        val source = when (bundle.fetchSource) {
            GitHubReleaseAssetFetchSources.HTML -> GitHubRepositoryProfileSource.ReleaseAssetsHtml
            else -> GitHubRepositoryProfileSource.ReleaseAssetsApi
        }
        availability += loaded(source, fetchedAtMillis)
        val apkLikeAssets = bundle.assets.filter { it.name.androidInstallableAssetLike() }
        val bundleAssets = bundle.assets.filter { it.name.androidBundleLike() }
        val remoteApk = request.preciseStableApkVersion ?: request.precisePreReleaseApkVersion
        return GitHubRepositoryDistributionProfile(
            latestAssetCount = intField(bundle.assets.size, source, fetchedAtMillis),
            apkLikeAssetCount = intField(apkLikeAssets.size, source, fetchedAtMillis),
            androidBundleAssetCount = intField(bundleAssets.size, source, fetchedAtMillis),
            totalDownloadCount = intField(
                bundle.assets.sumOf { it.downloadCount },
                source,
                fetchedAtMillis
            ),
            assetDigestCount = intField(
                bundle.assets.count { it.digest.isNotBlank() },
                source,
                fetchedAtMillis
            ),
            hasInstallableAndroidAsset = booleanField(
                apkLikeAssets.isNotEmpty(),
                source,
                fetchedAtMillis
            ),
            latestStableApkPackageName = stringField(
                remoteApk?.packageName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            latestStableApkVersionName = stringField(
                remoteApk?.versionName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            latestStableApkVersionCode = longField(
                remoteApk?.versionCodeLong ?: -1L,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            )
        )
    }

    private fun buildLocalDistributionProfile(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubRepositoryDistributionProfile {
        val remoteApk = request.preciseStableApkVersion ?: request.precisePreReleaseApkVersion
        return GitHubRepositoryDistributionProfile(
            latestStableApkPackageName = stringField(
                remoteApk?.packageName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            latestStableApkVersionName = stringField(
                remoteApk?.versionName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            latestStableApkVersionCode = longField(
                remoteApk?.versionCodeLong ?: -1L,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            )
        )
    }

    private fun fetchActionsProfile(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long,
        availability: MutableList<GitHubRepositoryProfileSourceState>
    ): GitHubRepositoryActionsProfile {
        val actionsRepository = GitHubActionsRepository(
            apiToken = request.lookupConfig.apiToken,
            client = client,
            apiBaseUrl = apiBaseUrl,
            actionsStrategy = GitHubActionsLookupStrategyOption.GitHubApiToken,
            requireApiTokenForApiStrategy = false
        )
        val runsResult = actionsRepository.fetchRecentRepositoryWorkflowRuns(
            owner = request.owner,
            repo = request.repo,
            limit = ACTIONS_PROFILE_RUN_LIMIT
        ).result
        val artifactsResult = actionsRepository.fetchRecentRepositoryArtifacts(
            owner = request.owner,
            repo = request.repo,
            limit = ACTIONS_PROFILE_ARTIFACT_LIMIT
        ).result
        val runs = runsResult.getOrNull().orEmpty()
        val artifacts = artifactsResult.getOrNull().orEmpty()
        if (runsResult.isSuccess) {
            availability += loaded(GitHubRepositoryProfileSource.ActionsRunsApi, fetchedAtMillis)
        } else {
            availability += failed(
                source = GitHubRepositoryProfileSource.ActionsRunsApi,
                fetchedAtMillis = fetchedAtMillis,
                error = runsResult.exceptionOrNull()
                    ?: IllegalStateException("actions runs unavailable")
            )
        }
        if (artifactsResult.isSuccess) {
            availability += loaded(
                GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis
            )
        } else {
            availability += failed(
                source = GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis = fetchedAtMillis,
                error = artifactsResult.exceptionOrNull()
                    ?: IllegalStateException("actions artifacts unavailable")
            )
        }
        return buildActionsProfile(runs, artifacts, fetchedAtMillis)
    }

    private fun buildActionsProfile(
        runs: List<GitHubActionsWorkflowRun>,
        artifacts: List<GitHubActionsArtifact>,
        fetchedAtMillis: Long
    ): GitHubRepositoryActionsProfile {
        val source = GitHubRepositoryProfileSource.ActionsRunsApi
        val latestRun = runs.maxWithOrNull(
            compareBy<GitHubActionsWorkflowRun> {
                it.updatedAtMillis ?: it.createdAtMillis ?: Long.MIN_VALUE
            }
                .thenBy { it.id }
        )
        return GitHubRepositoryActionsProfile(
            workflowRunCount = intField(runs.size, source, fetchedAtMillis),
            successfulRunCount = intField(runs.count {
                it.conclusion.equals(
                    "success",
                    ignoreCase = true
                )
            }, source, fetchedAtMillis),
            failedRunCount = intField(
                runs.count { it.conclusion.lowercase(Locale.ROOT) in failingActionConclusions },
                source,
                fetchedAtMillis
            ),
            latestRunStatus = stringField(latestRun?.status.orEmpty(), source, fetchedAtMillis),
            latestRunConclusion = stringField(
                latestRun?.conclusion.orEmpty(),
                source,
                fetchedAtMillis
            ),
            latestRunUpdatedAtMillis = longField(
                latestRun?.updatedAtMillis ?: latestRun?.createdAtMillis ?: -1L,
                source,
                fetchedAtMillis
            ),
            artifactCount = intField(
                artifacts.size,
                GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis
            ),
            nonExpiredArtifactCount = intField(
                artifacts.count { !it.expired },
                GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis
            ),
            androidArtifactCount = intField(
                artifacts.count { artifact -> artifact.name.androidBuildArtifactLike() },
                GitHubRepositoryProfileSource.ActionsArtifactsApi,
                fetchedAtMillis
            )
        )
    }

    private fun buildLocalFitProfile(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubRepositoryLocalFitProfile {
        val remoteApk = request.preciseStableApkVersion ?: request.precisePreReleaseApkVersion
        val localPackage = request.localPackageName.trim()
        val remotePackage = remoteApk?.packageName.orEmpty().trim()
        val packageMatched = localPackage.isNotBlank() &&
                remotePackage.isNotBlank() &&
                localPackage.equals(remotePackage, ignoreCase = true)
        val packageMismatchKnown =
            localPackage.isNotBlank() && remotePackage.isNotBlank() && !packageMatched
        return GitHubRepositoryLocalFitProfile(
            localPackageName = stringField(
                localPackage,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            remotePackageName = stringField(
                remotePackage,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            packageNameMatched = when {
                packageMatched -> booleanField(
                    true,
                    GitHubRepositoryProfileSource.LocalInstall,
                    fetchedAtMillis
                )

                packageMismatchKnown -> booleanField(
                    false,
                    GitHubRepositoryProfileSource.LocalInstall,
                    fetchedAtMillis
                )

                else -> null
            },
            localVersionName = stringField(
                request.localVersionName,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            remoteVersionName = stringField(
                remoteApk?.versionName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            localVersionCode = longField(
                request.localVersionCode,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            remoteVersionCode = longField(
                remoteApk?.versionCodeLong ?: -1L,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            )
        )
    }

    private fun fetchJson(url: String, apiToken: String): Result<String> = runCatching {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .header("User-Agent", GITHUB_USER_AGENT)
        val token = apiToken.trim()
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyText = response.body.string()
            if (!response.isSuccessful) {
                error(response.buildErrorMessage(bodyText))
            }
            bodyText
        }
    }

    private fun fetchHtml(url: String): Result<String> = runCatching {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", GITHUB_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body.string()
            if (!response.isSuccessful) {
                error("GitHub repository page request failed (HTTP ${response.code})")
            }
            bodyText
        }
    }

    private fun buildRepositoryApiUrl(owner: String, repo: String): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo"
    }

    private fun buildCommunityProfileUrl(owner: String, repo: String): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/community/profile"
    }

    private fun Response.buildErrorMessage(bodyText: String): String {
        val apiMessage = runCatching {
            JSONObject(bodyText).optString("message").trim()
        }.getOrDefault("")
        return when (code) {
            401 -> "GitHub API token is invalid or expired"
            403, 429 -> "GitHub API is rate limited"
            404 -> "GitHub repository profile resource was not found"
            else -> "GitHub profile request failed (HTTP $code${
                apiMessage.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
            })"
        }
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

    private fun JSONObject.toUpstreamProfile(
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

    private fun GitHubRepositoryReleaseSnapshot.releaseProfileSource(): GitHubRepositoryProfileSource {
        return when (strategyId) {
            GitHubLookupStrategyOption.GitHubApiToken.storageId -> GitHubRepositoryProfileSource.GitHubApiReleases
            else -> GitHubRepositoryProfileSource.AtomReleaseFeed
        }
    }

    private fun os.kei.feature.github.model.GitHubReleaseVersionSignals?.releaseProfileSource(
        snapshot: GitHubRepositoryReleaseSnapshot
    ): GitHubRepositoryProfileSource {
        return when (this?.source) {
            GitHubReleaseSignalSource.GitHubApi -> GitHubRepositoryProfileSource.GitHubApiReleases
            GitHubReleaseSignalSource.LatestRedirect -> GitHubRepositoryProfileSource.HtmlLatestReleaseRedirect
            GitHubReleaseSignalSource.AtomEntry,
            GitHubReleaseSignalSource.AtomFallback -> GitHubRepositoryProfileSource.AtomReleaseFeed

            null -> snapshot.releaseProfileSource()
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        this ?: return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun String.parseIsoInstantOrDefault(): Long {
        return runCatching {
            if (isBlank()) -1L else Instant.parse(this).toEpochMilli()
        }.getOrDefault(-1L)
    }

    private fun String.androidInstallableAssetLike(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return lower.endsWith(".apk") || lower.endsWith(".apks") || lower.endsWith(".xapk")
    }

    private fun String.androidBundleLike(): Boolean {
        return lowercase(Locale.ROOT).endsWith(".aab")
    }

    private fun String.androidBuildArtifactLike(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return lower.endsWith(".apk") ||
                lower.endsWith(".apks") ||
                lower.endsWith(".xapk") ||
                lower.endsWith(".aab") ||
                lower.contains("apk") ||
                lower.contains("android")
    }

    private companion object {
        private const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_GITHUB_HTML_BASE_URL = "https://github.com"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"
        private const val ACTIONS_PROFILE_RUN_LIMIT = 12
        private const val ACTIONS_PROFILE_ARTIFACT_LIMIT = 30
        private val failingActionConclusions = setOf(
            "failure",
            "timed_out",
            "cancelled",
            "startup_failure",
            "action_required"
        )

        private val githubClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(18.seconds)
                .connectTimeout(8.seconds)
                .readTimeout(14.seconds)
                .writeTimeout(8.seconds)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .fastFallback(true)
                .build()
        }
    }
}

private fun <T> field(
    value: T,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<T> {
    return GitHubProfileField(
        value = value,
        source = source,
        fetchedAtMillis = fetchedAtMillis,
        confidence = confidence
    )
}

private fun stringField(
    value: String,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<String>? {
    return value.trim()
        .takeIf { it.isNotBlank() }
        ?.let { field(it, source, fetchedAtMillis, confidence) }
}

private fun intField(
    value: Int,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Int> {
    return field(value.coerceAtLeast(0), source, fetchedAtMillis, confidence)
}

private fun longField(
    value: Long,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Long>? {
    return value
        .takeIf { it > 0L }
        ?.let { field(it, source, fetchedAtMillis, confidence) }
}

private fun booleanField(
    value: Boolean,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Boolean> {
    return field(value, source, fetchedAtMillis, confidence)
}

private fun listField(
    value: List<String>,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<List<String>>? {
    return value
        .takeIf { it.isNotEmpty() }
        ?.let { field(it, source, fetchedAtMillis, confidence) }
}

private fun loaded(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Loaded,
        fetchedAtMillis = fetchedAtMillis
    )
}

private fun skipped(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    message: String = ""
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Skipped,
        fetchedAtMillis = fetchedAtMillis,
        message = message
    )
}

private fun failed(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    error: Throwable
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Failed,
        fetchedAtMillis = fetchedAtMillis,
        message = error.message.orEmpty().take(180)
    )
}
