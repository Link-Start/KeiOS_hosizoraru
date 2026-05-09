package os.kei.feature.github.data.remote

import org.json.JSONArray
import org.json.JSONObject
import os.kei.feature.github.GitHubExecution
import os.kei.feature.github.model.GitHubRepositoryForkSyncProfile
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import os.kei.feature.github.model.GitHubRepositorySecurityProfile
import os.kei.feature.github.model.GitHubRepositoryTrafficProfile

internal data class GitHubDeepRepositoryProfileResult(
    val traffic: GitHubRepositoryTrafficProfile = GitHubRepositoryTrafficProfile(),
    val forkSync: GitHubRepositoryForkSyncProfile = GitHubRepositoryForkSyncProfile(),
    val security: GitHubRepositorySecurityProfile = GitHubRepositorySecurityProfile(),
    val availability: List<GitHubRepositoryProfileSourceState> = emptyList()
)

internal class GitHubDeepRepositoryProfileSource(
    private val http: GitHubRepositoryProfileHttpClient
) {
    suspend fun fetch(
        request: GitHubRepositoryProfileRequest,
        identity: GitHubRepositoryIdentityProfile,
        lifecycle: GitHubRepositoryLifecycleProfile,
        fetchedAtMillis: Long
    ): GitHubDeepRepositoryProfileResult {
        val tasks = listOf<() -> GitHubDeepProfileChunk>(
            { fetchTrafficViews(request, fetchedAtMillis) },
            { fetchTrafficClones(request, fetchedAtMillis) },
            { fetchForkCompareChunk(request, identity, lifecycle, fetchedAtMillis) },
            { fetchDependabotAlerts(request, fetchedAtMillis) },
            { fetchCodeScanningAlerts(request, fetchedAtMillis) }
        )
        val chunks = GitHubExecution.mapOrderedBounded(
            items = tasks,
            maxConcurrency = DEEP_SOURCE_CONCURRENCY
        ) { task ->
            task()
        }
        var traffic = GitHubRepositoryTrafficProfile()
        var forkSync = GitHubRepositoryForkSyncProfile()
        var security = GitHubRepositorySecurityProfile()
        val availability = mutableListOf<GitHubRepositoryProfileSourceState>()
        chunks.forEach { chunk ->
            traffic = traffic.merge(chunk.traffic)
            forkSync = forkSync.merge(chunk.forkSync)
            security = security.merge(chunk.security)
            availability += chunk.availability
        }
        return GitHubDeepRepositoryProfileResult(
            traffic = traffic,
            forkSync = forkSync,
            security = security,
            availability = availability
        )
    }

    fun parseTrafficViews(
        json: String,
        fetchedAtMillis: Long
    ): GitHubRepositoryTrafficProfile {
        val root = JSONObject(json)
        val source = GitHubRepositoryProfileSource.TrafficViewsApi
        return GitHubRepositoryTrafficProfile(
            viewCount = intField(
                root.optInt("count", 0),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            viewUniques = intField(
                root.optInt("uniques", 0),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            latestViewBucketAtMillis = longField(
                root.optJSONArray("views").latestTrafficBucketAtMillis(),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            )
        )
    }

    fun parseTrafficClones(
        json: String,
        fetchedAtMillis: Long
    ): GitHubRepositoryTrafficProfile {
        val root = JSONObject(json)
        val source = GitHubRepositoryProfileSource.TrafficClonesApi
        return GitHubRepositoryTrafficProfile(
            cloneCount = intField(
                root.optInt("count", 0),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            cloneUniques = intField(
                root.optInt("uniques", 0),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            latestCloneBucketAtMillis = longField(
                root.optJSONArray("clones").latestTrafficBucketAtMillis(),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            )
        )
    }

    fun parseForkCompare(
        json: String,
        owner: String,
        repo: String,
        upstreamFullName: String,
        fetchedAtMillis: Long
    ): GitHubRepositoryForkSyncProfile {
        val source = GitHubRepositoryProfileSource.ForkCompareApi
        val root = JSONObject(json)
        return GitHubRepositoryForkSyncProfile(
            baseFullName = stringField(
                upstreamFullName,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            headFullName = stringField(
                "$owner/$repo",
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            aheadBy = intField(
                root.optInt("ahead_by", 0),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            behindBy = intField(
                root.optInt("behind_by", 0),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            status = stringField(
                root.optString("status"),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            totalCommits = intField(
                root.optInt("total_commits", 0),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            comparedAtMillis = longField(
                fetchedAtMillis,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            )
        )
    }

    fun parseDependabotAlerts(
        json: String,
        fetchedAtMillis: Long
    ): GitHubRepositorySecurityProfile {
        val source = GitHubRepositoryProfileSource.DependabotAlertsApi
        return GitHubRepositorySecurityProfile(
            dependabotAlertsAvailable = booleanField(
                true,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            openDependabotAlertsCount = intField(
                JSONArray(json).length(),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            )
        )
    }

    fun parseCodeScanningAlerts(
        json: String,
        fetchedAtMillis: Long
    ): GitHubRepositorySecurityProfile {
        val source = GitHubRepositoryProfileSource.CodeScanningAlertsApi
        return GitHubRepositorySecurityProfile(
            codeScanningAvailable = booleanField(
                true,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            ),
            openCodeScanningAlertsCount = intField(
                JSONArray(json).length(),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Medium
            )
        )
    }

    private fun fetchTrafficViews(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubDeepProfileChunk {
        val source = GitHubRepositoryProfileSource.TrafficViewsApi
        val startNs = System.nanoTime()
        return http.fetchJson(
            http.trafficViewsUrl(request.owner, request.repo),
            request.lookupConfig.apiToken
        ).fold(
            onSuccess = { body ->
                GitHubDeepProfileChunk(
                    traffic = parseTrafficViews(body, fetchedAtMillis),
                    availability = listOf(
                        loaded(
                            source = source,
                            fetchedAtMillis = fetchedAtMillis,
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                )
            },
            onFailure = { error ->
                GitHubDeepProfileChunk(
                    availability = listOf(
                        failed(
                            source = source,
                            fetchedAtMillis = fetchedAtMillis,
                            error = error,
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                )
            }
        )
    }

    private fun fetchTrafficClones(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubDeepProfileChunk {
        val source = GitHubRepositoryProfileSource.TrafficClonesApi
        val startNs = System.nanoTime()
        return http.fetchJson(
            http.trafficClonesUrl(request.owner, request.repo),
            request.lookupConfig.apiToken
        ).fold(
            onSuccess = { body ->
                GitHubDeepProfileChunk(
                    traffic = parseTrafficClones(body, fetchedAtMillis),
                    availability = listOf(
                        loaded(
                            source = source,
                            fetchedAtMillis = fetchedAtMillis,
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                )
            },
            onFailure = { error ->
                GitHubDeepProfileChunk(
                    availability = listOf(
                        failed(
                            source = source,
                            fetchedAtMillis = fetchedAtMillis,
                            error = error,
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                )
            }
        )
    }

    private fun fetchForkCompareChunk(
        request: GitHubRepositoryProfileRequest,
        identity: GitHubRepositoryIdentityProfile,
        lifecycle: GitHubRepositoryLifecycleProfile,
        fetchedAtMillis: Long
    ): GitHubDeepProfileChunk {
        val startNs = System.nanoTime()
        val localAvailability = mutableListOf<GitHubRepositoryProfileSourceState>()
        val forkSync = fetchForkCompare(
            request = request,
            identity = identity,
            lifecycle = lifecycle,
            fetchedAtMillis = fetchedAtMillis,
            availability = localAvailability
        )
        return GitHubDeepProfileChunk(
            forkSync = forkSync,
            availability = localAvailability.withMetadata(
                elapsedMs = elapsedMsSince(startNs),
                required = true
            )
        )
    }

    private fun fetchDependabotAlerts(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubDeepProfileChunk {
        val source = GitHubRepositoryProfileSource.DependabotAlertsApi
        val startNs = System.nanoTime()
        return http.fetchJson(
            http.dependabotAlertsUrl(request.owner, request.repo),
            request.lookupConfig.apiToken
        ).fold(
            onSuccess = { body ->
                GitHubDeepProfileChunk(
                    security = parseDependabotAlerts(body, fetchedAtMillis),
                    availability = listOf(
                        loaded(
                            source = source,
                            fetchedAtMillis = fetchedAtMillis,
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                )
            },
            onFailure = { error ->
                GitHubDeepProfileChunk(
                    security = unavailableDependabotAlerts(fetchedAtMillis),
                    availability = listOf(
                        failed(
                            source = source,
                            fetchedAtMillis = fetchedAtMillis,
                            error = error,
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                )
            }
        )
    }

    private fun fetchCodeScanningAlerts(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubDeepProfileChunk {
        val source = GitHubRepositoryProfileSource.CodeScanningAlertsApi
        val startNs = System.nanoTime()
        return http.fetchJson(
            http.codeScanningAlertsUrl(request.owner, request.repo),
            request.lookupConfig.apiToken
        ).fold(
            onSuccess = { body ->
                GitHubDeepProfileChunk(
                    security = parseCodeScanningAlerts(body, fetchedAtMillis),
                    availability = listOf(
                        loaded(
                            source = source,
                            fetchedAtMillis = fetchedAtMillis,
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                )
            },
            onFailure = { error ->
                GitHubDeepProfileChunk(
                    security = unavailableCodeScanningAlerts(fetchedAtMillis),
                    availability = listOf(
                        failed(
                            source = source,
                            fetchedAtMillis = fetchedAtMillis,
                            error = error,
                            elapsedMs = elapsedMsSince(startNs),
                            required = true
                        )
                    )
                )
            }
        )
    }

    private fun fetchForkCompare(
        request: GitHubRepositoryProfileRequest,
        identity: GitHubRepositoryIdentityProfile,
        lifecycle: GitHubRepositoryLifecycleProfile,
        fetchedAtMillis: Long,
        availability: MutableList<GitHubRepositoryProfileSourceState>
    ): GitHubRepositoryForkSyncProfile {
        if (lifecycle.fork?.value != true) {
            availability += skipped(
                source = GitHubRepositoryProfileSource.ForkCompareApi,
                fetchedAtMillis = fetchedAtMillis,
                message = "repository is independent"
            )
            return GitHubRepositoryForkSyncProfile()
        }
        val upstreamFullName = lifecycle.upstream?.fullName?.value.orEmpty()
        val upstreamParts = upstreamFullName.split('/').takeIf { it.size == 2 }
        if (upstreamParts == null) {
            availability += skipped(
                source = GitHubRepositoryProfileSource.ForkCompareApi,
                fetchedAtMillis = fetchedAtMillis,
                message = "upstream repository unavailable"
            )
            return GitHubRepositoryForkSyncProfile()
        }
        val headBranch = identity.defaultBranch?.value ?: DEFAULT_COMPARE_BRANCH
        val baseBranch = lifecycle.upstream?.defaultBranch?.value ?: headBranch
        return http.fetchJson(
            http.compareUrl(
                upstreamOwner = upstreamParts[0],
                upstreamRepo = upstreamParts[1],
                baseBranch = baseBranch,
                headOwner = request.owner,
                headBranch = headBranch
            ),
            request.lookupConfig.apiToken
        ).fold(
            onSuccess = { body ->
                availability += loaded(
                    GitHubRepositoryProfileSource.ForkCompareApi,
                    fetchedAtMillis
                )
                parseForkCompare(
                    json = body,
                    owner = request.owner,
                    repo = request.repo,
                    upstreamFullName = upstreamFullName,
                    fetchedAtMillis = fetchedAtMillis
                )
            },
            onFailure = { error ->
                availability += failed(
                    GitHubRepositoryProfileSource.ForkCompareApi,
                    fetchedAtMillis,
                    error
                )
                GitHubRepositoryForkSyncProfile()
            }
        )
    }

    private fun unavailableDependabotAlerts(
        fetchedAtMillis: Long
    ): GitHubRepositorySecurityProfile {
        val source = GitHubRepositoryProfileSource.DependabotAlertsApi
        return GitHubRepositorySecurityProfile(
            dependabotAlertsAvailable = booleanField(
                false,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            )
        )
    }

    private fun unavailableCodeScanningAlerts(
        fetchedAtMillis: Long
    ): GitHubRepositorySecurityProfile {
        val source = GitHubRepositoryProfileSource.CodeScanningAlertsApi
        return GitHubRepositorySecurityProfile(
            codeScanningAvailable = booleanField(
                false,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            )
        )
    }

    private fun JSONArray?.latestTrafficBucketAtMillis(): Long {
        this ?: return -1L
        var latest = -1L
        for (index in 0 until length()) {
            val bucket = optJSONObject(index) ?: continue
            latest = maxOf(latest, bucket.optString("timestamp").parseIsoInstantOrDefault())
        }
        return latest
    }

    private fun GitHubRepositoryTrafficProfile.merge(
        other: GitHubRepositoryTrafficProfile
    ): GitHubRepositoryTrafficProfile {
        return GitHubRepositoryTrafficProfile(
            viewCount = other.viewCount ?: viewCount,
            viewUniques = other.viewUniques ?: viewUniques,
            cloneCount = other.cloneCount ?: cloneCount,
            cloneUniques = other.cloneUniques ?: cloneUniques,
            latestViewBucketAtMillis = other.latestViewBucketAtMillis ?: latestViewBucketAtMillis,
            latestCloneBucketAtMillis = other.latestCloneBucketAtMillis ?: latestCloneBucketAtMillis
        )
    }

    private fun GitHubRepositoryForkSyncProfile.merge(
        other: GitHubRepositoryForkSyncProfile
    ): GitHubRepositoryForkSyncProfile {
        return GitHubRepositoryForkSyncProfile(
            baseFullName = other.baseFullName ?: baseFullName,
            headFullName = other.headFullName ?: headFullName,
            aheadBy = other.aheadBy ?: aheadBy,
            behindBy = other.behindBy ?: behindBy,
            status = other.status ?: status,
            totalCommits = other.totalCommits ?: totalCommits,
            comparedAtMillis = other.comparedAtMillis ?: comparedAtMillis
        )
    }

    private fun GitHubRepositorySecurityProfile.merge(
        other: GitHubRepositorySecurityProfile
    ): GitHubRepositorySecurityProfile {
        return GitHubRepositorySecurityProfile(
            dependabotAlertsAvailable = other.dependabotAlertsAvailable
                ?: dependabotAlertsAvailable,
            openDependabotAlertsCount = other.openDependabotAlertsCount
                ?: openDependabotAlertsCount,
            codeScanningAvailable = other.codeScanningAvailable ?: codeScanningAvailable,
            openCodeScanningAlertsCount = other.openCodeScanningAlertsCount
                ?: openCodeScanningAlertsCount,
            secretScanningAvailable = other.secretScanningAvailable ?: secretScanningAvailable
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

    private data class GitHubDeepProfileChunk(
        val traffic: GitHubRepositoryTrafficProfile = GitHubRepositoryTrafficProfile(),
        val forkSync: GitHubRepositoryForkSyncProfile = GitHubRepositoryForkSyncProfile(),
        val security: GitHubRepositorySecurityProfile = GitHubRepositorySecurityProfile(),
        val availability: List<GitHubRepositoryProfileSourceState> = emptyList()
    )

    companion object {
        fun skippedAvailability(
            fetchedAtMillis: Long,
            message: String = "deep profile disabled"
        ): List<GitHubRepositoryProfileSourceState> {
            return listOf(
                GitHubRepositoryProfileSource.TrafficViewsApi,
                GitHubRepositoryProfileSource.TrafficClonesApi,
                GitHubRepositoryProfileSource.ForkCompareApi,
                GitHubRepositoryProfileSource.DependabotAlertsApi,
                GitHubRepositoryProfileSource.CodeScanningAlertsApi
            ).map { source ->
                skipped(
                    source = source,
                    fetchedAtMillis = fetchedAtMillis,
                    message = message
                )
            }
        }

        private const val DEFAULT_COMPARE_BRANCH = "main"
        private const val DEEP_SOURCE_CONCURRENCY = 3
    }
}
