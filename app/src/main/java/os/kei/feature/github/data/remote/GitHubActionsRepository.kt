package os.kei.feature.github.data.remote

import okhttp3.OkHttpClient
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsArtifactDownloadResolution
import os.kei.feature.github.model.GitHubActionsLookupStrategyOption
import os.kei.feature.github.model.GitHubActionsRepositoryInfo
import os.kei.feature.github.model.GitHubActionsRunArtifacts
import os.kei.feature.github.model.GitHubActionsRunStatusSnapshot
import os.kei.feature.github.model.GitHubActionsWorkflow
import os.kei.feature.github.model.GitHubActionsWorkflowArtifactsSnapshot
import os.kei.feature.github.model.GitHubActionsWorkflowRun
import os.kei.feature.github.model.GitHubApiAuthMode
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import java.util.concurrent.TimeUnit

class GitHubActionsRepository(
    private val apiToken: String = "",
    private val client: OkHttpClient = githubClient,
    private val apiBaseUrl: String = DEFAULT_GITHUB_API_BASE_URL,
    private val actionsStrategy: GitHubActionsLookupStrategyOption = GitHubActionsLookupStrategyOption.GitHubApiToken,
    private val requireApiTokenForApiStrategy: Boolean = false,
    private val githubHtmlBaseUrl: String = DEFAULT_GITHUB_HTML_BASE_URL,
    private val nightlyLinkBaseUrl: String = DEFAULT_NIGHTLY_LINK_BASE_URL
) {
    private val sanitizedToken: String = apiToken.trim()
    private val useNightlyLink: Boolean
        get() = actionsStrategy == GitHubActionsLookupStrategyOption.NightlyLink
    private val nightlyRepository: GitHubActionsNightlyLinkRepository by lazy {
        GitHubActionsNightlyLinkRepository(
            client = client,
            githubHtmlBaseUrl = githubHtmlBaseUrl,
            nightlyLinkBaseUrl = nightlyLinkBaseUrl
        )
    }
    private val noRedirectClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
    private val apiUrls = GitHubActionsApiUrlBuilder(
        apiBaseUrl = apiBaseUrl,
        nightlyLinkBaseUrl = nightlyLinkBaseUrl
    )
    private val apiClient: GitHubActionsApiClient by lazy {
        GitHubActionsApiClient(
            apiToken = sanitizedToken,
            client = client,
            noRedirectClient = noRedirectClient,
            apiBaseUrl = apiBaseUrl
        )
    }
    private val runArtifactFetcher = GitHubActionsRunArtifactFetcher(
        maxConcurrency = MAX_ARTIFACT_FETCH_CONCURRENCY
    ) { fetchOwner, fetchRepo, runId, limit ->
        fetchRunArtifacts(
            owner = fetchOwner,
            repo = fetchRepo,
            runId = runId,
            limit = limit
        ).result
    }

    val authMode: GitHubApiAuthMode
        get() = apiClient.authMode

    fun fetchRepositoryInfo(
        owner: String,
        repo: String
    ): GitHubStrategyLoadTrace<GitHubActionsRepositoryInfo> {
        val startedAt = System.currentTimeMillis()
        val result = if (useNightlyLink) {
            nightlyRepository.fetchRepositoryInfo(owner, repo)
        } else {
            requireActionsApiToken().mapCatching {
                fetchJson(
                    url = apiUrls.repository(owner, repo),
                    cacheTtlMillis = ACTIONS_METADATA_CACHE_TTL_MS
                ).getOrThrow()
                    .let { json -> parseRepositoryInfo(json, owner, repo) }
            }
        }
        return result.toTrace(startedAt)
    }

    fun fetchRepositoryDefaultBranch(
        owner: String,
        repo: String
    ): GitHubStrategyLoadTrace<String> {
        val startedAt = System.currentTimeMillis()
        val result = if (useNightlyLink) {
            nightlyRepository.fetchRepositoryInfo(owner, repo).mapCatching { it.defaultBranch }
        } else {
            requireActionsApiToken().mapCatching {
                fetchJson(
                    url = apiUrls.repository(owner, repo),
                    cacheTtlMillis = ACTIONS_METADATA_CACHE_TTL_MS
                ).getOrThrow()
                    .let { json -> parseRepositoryInfo(json, owner, repo).defaultBranch }
            }
        }
        return result.toTrace(startedAt)
    }

    fun fetchWorkflows(
        owner: String,
        repo: String,
        limit: Int = DEFAULT_WORKFLOW_LIMIT
    ): GitHubStrategyLoadTrace<List<GitHubActionsWorkflow>> {
        val startedAt = System.currentTimeMillis()
        val result = if (useNightlyLink) {
            nightlyRepository.fetchWorkflows(owner, repo, limit)
        } else {
            requireActionsApiToken().mapCatching {
                fetchJson(
                    url = apiUrls.workflows(owner, repo, limit),
                    cacheTtlMillis = ACTIONS_METADATA_CACHE_TTL_MS
                ).getOrThrow()
                    .let(::parseWorkflows)
            }
        }
        return result.toTrace(startedAt)
    }

    fun fetchWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: String,
        limit: Int = DEFAULT_RUN_LIMIT,
        branch: String = "",
        event: String = "",
        status: String = "",
        actor: String = "",
        created: String = "",
        headSha: String = "",
        excludePullRequests: Boolean = false
    ): GitHubStrategyLoadTrace<List<GitHubActionsWorkflowRun>> {
        val startedAt = System.currentTimeMillis()
        val result = if (useNightlyLink) {
            nightlyRepository.fetchWorkflowRuns(
                owner = owner,
                repo = repo,
                workflowId = workflowId,
                branch = branch
            )
        } else {
            requireActionsApiToken().mapCatching {
                fetchJson(
                    url = apiUrls.workflowRuns(
                        owner = owner,
                        repo = repo,
                        workflowId = workflowId,
                        limit = limit,
                        branch = branch,
                        event = event,
                        status = status,
                        actor = actor,
                        created = created,
                        headSha = headSha,
                        excludePullRequests = excludePullRequests
                    ),
                    cacheTtlMillis = ACTIONS_RUNS_CACHE_TTL_MS
                ).getOrThrow().let(::parseWorkflowRuns)
            }
        }
        return result.toTrace(startedAt)
    }

    fun fetchWorkflowRun(
        owner: String,
        repo: String,
        runId: Long
    ): GitHubStrategyLoadTrace<GitHubActionsWorkflowRun> {
        val startedAt = System.currentTimeMillis()
        val result = if (useNightlyLink) {
            nightlyRepository.fetchWorkflowRun(owner = owner, repo = repo, runId = runId)
        } else {
            requireActionsApiToken().mapCatching {
                fetchJson(apiUrls.workflowRun(owner, repo, runId)).getOrThrow()
                    .let(::parseWorkflowRun)
            }
        }
        return result.toTrace(startedAt)
    }

    fun fetchRunArtifacts(
        owner: String,
        repo: String,
        runId: Long,
        limit: Int = DEFAULT_ARTIFACT_LIMIT
    ): GitHubStrategyLoadTrace<List<GitHubActionsArtifact>> {
        val startedAt = System.currentTimeMillis()
        val result = if (useNightlyLink) {
            nightlyRepository.fetchRunArtifacts(owner = owner, repo = repo, runId = runId, limit = limit)
        } else {
            requireActionsApiToken().mapCatching {
                fetchJson(
                    url = apiUrls.runArtifacts(owner, repo, runId, limit),
                    cacheTtlMillis = ACTIONS_ARTIFACT_CACHE_TTL_MS
                ).getOrThrow()
                    .let { json -> parseArtifacts(json, fallbackWorkflowRunId = runId) }
            }
        }
        return result.toTrace(startedAt)
    }

    fun fetchRunStatusSnapshot(
        owner: String,
        repo: String,
        runId: Long,
        artifactsLimit: Int = DEFAULT_ARTIFACT_LIMIT,
        includeArtifactsWhenCompleted: Boolean = true
    ): GitHubStrategyLoadTrace<GitHubActionsRunStatusSnapshot> {
        val startedAt = System.currentTimeMillis()
        if (useNightlyLink) {
            val result = nightlyRepository.fetchRunStatusSnapshot(
                owner = owner,
                repo = repo,
                runId = runId,
                artifactsLimit = artifactsLimit,
                includeArtifactsWhenCompleted = includeArtifactsWhenCompleted
            )
            return result.toTrace(startedAt)
        }
        requireActionsApiToken().onFailure { error ->
            return Result.failure<GitHubActionsRunStatusSnapshot>(error).toTrace(startedAt)
        }
        val run = fetchWorkflowRun(owner, repo, runId).result.getOrElse { error ->
            return Result.failure<GitHubActionsRunStatusSnapshot>(error).toTrace(startedAt)
        }
        val artifacts = if (
            includeArtifactsWhenCompleted &&
            run.status.equals("completed", ignoreCase = true)
        ) {
            fetchRunArtifacts(
                owner = owner,
                repo = repo,
                runId = runId,
                limit = artifactsLimit
            ).result.getOrElse { error ->
                return Result.failure<GitHubActionsRunStatusSnapshot>(error).toTrace(startedAt)
            }
        } else {
            emptyList()
        }
        return Result.success(
            GitHubActionsRunStatusSnapshot(
                owner = owner,
                repo = repo,
                run = run,
                artifacts = artifacts
            )
        ).toTrace(startedAt)
    }

    fun fetchWorkflowArtifactSnapshot(
        owner: String,
        repo: String,
        workflowId: String,
        runLimit: Int = DEFAULT_RUN_LIMIT,
        artifactsPerRun: Int = DEFAULT_ARTIFACT_LIMIT,
        artifactRunLimit: Int = Int.MAX_VALUE,
        branch: String = "",
        event: String = "",
        status: String = "",
        actor: String = "",
        created: String = "",
        headSha: String = "",
        excludePullRequests: Boolean = false,
        resolveNightlyRunDetail: Boolean = true
    ): GitHubStrategyLoadTrace<GitHubActionsWorkflowArtifactsSnapshot> {
        val startedAt = System.currentTimeMillis()
        if (useNightlyLink) {
            val nightlyResult = nightlyRepository.fetchWorkflowArtifactSnapshot(
                owner = owner,
                repo = repo,
                workflowId = workflowId,
                branch = branch,
                artifactsPerRun = artifactsPerRun,
                resolveRunDetail = resolveNightlyRunDetail
            )
            val nightlySnapshot = nightlyResult.getOrNull()
            if (
                nightlySnapshot != null &&
                nightlySnapshot.artifacts.isNotEmpty() &&
                !nightlySnapshot.requiresPublicApiMetadataForNightlyDownload()
            ) {
                return nightlyResult.toTrace(startedAt)
            }
            val fallbackResult = fetchNightlyCompatibleWorkflowArtifactSnapshotFromPublicApi(
                owner = owner,
                repo = repo,
                workflowId = workflowId,
                runLimit = runLimit,
                artifactsPerRun = artifactsPerRun,
                artifactRunLimit = artifactRunLimit,
                branch = branch,
                event = event,
                status = status,
                actor = actor,
                created = created,
                headSha = headSha,
                excludePullRequests = excludePullRequests
            )
            val fallbackSnapshot = fallbackResult.getOrNull()
            val branchFallbackResult = if (
                branch.isNotBlank() &&
                fallbackSnapshot?.artifacts.orEmpty().isEmpty()
            ) {
                fetchNightlyCompatibleWorkflowArtifactSnapshotFromPublicApi(
                    owner = owner,
                    repo = repo,
                    workflowId = workflowId,
                    runLimit = runLimit,
                    artifactsPerRun = artifactsPerRun,
                    artifactRunLimit = artifactRunLimit,
                    branch = "",
                    event = event,
                    status = status,
                    actor = actor,
                    created = created,
                    headSha = headSha,
                    excludePullRequests = excludePullRequests
                )
            } else {
                null
            }
            val branchFallbackSnapshot = branchFallbackResult?.getOrNull()
            return when {
                fallbackSnapshot != null && fallbackSnapshot.artifacts.isNotEmpty() ->
                    fallbackResult.toTrace(startedAt)
                branchFallbackResult != null &&
                    branchFallbackSnapshot != null &&
                    branchFallbackSnapshot.artifacts.isNotEmpty() ->
                    branchFallbackResult.toTrace(startedAt)
                nightlyResult.isSuccess -> nightlyResult.toTrace(startedAt)
                fallbackResult.isSuccess -> fallbackResult.toTrace(startedAt)
                branchFallbackResult != null && branchFallbackResult.isSuccess ->
                    branchFallbackResult.toTrace(startedAt)
                else -> nightlyResult.toTrace(startedAt)
            }
        }
        requireActionsApiToken().onFailure { error ->
            return Result.failure<GitHubActionsWorkflowArtifactsSnapshot>(error).toTrace(startedAt)
        }
        val runs = fetchWorkflowRuns(
            owner = owner,
            repo = repo,
            workflowId = workflowId,
            limit = runLimit,
            branch = branch,
            event = event,
            status = status,
            actor = actor,
            created = created,
            headSha = headSha,
            excludePullRequests = excludePullRequests
        ).result.getOrElse { error ->
            return Result.failure<GitHubActionsWorkflowArtifactsSnapshot>(error).toTrace(startedAt)
        }
        val artifactRuns = runs.take(artifactRunLimit.coerceAtLeast(0))
        val artifactsByRun = runCatching {
            runArtifactFetcher.fetchForRuns(
                owner = owner,
                repo = repo,
                runs = artifactRuns,
                limit = artifactsPerRun
            )
        }.getOrElse { error ->
            return Result.failure<GitHubActionsWorkflowArtifactsSnapshot>(error).toTrace(startedAt)
        }
        val artifactsByRunId = mutableMapOf<Long, List<GitHubActionsArtifact>>()
        artifactsByRun.forEach { (run, artifactsResult) ->
            val artifacts = artifactsResult.getOrElse { error ->
                return Result.failure<GitHubActionsWorkflowArtifactsSnapshot>(error).toTrace(startedAt)
            }
            artifactsByRunId[run.id] = artifacts
        }
        val runArtifacts = runs.map { run ->
            GitHubActionsRunArtifacts(
                run = run,
                artifacts = artifactsByRunId[run.id].orEmpty()
            )
        }
        return Result.success(
            GitHubActionsWorkflowArtifactsSnapshot(
                owner = owner,
                repo = repo,
                workflowId = workflowId,
                runs = runArtifacts
            )
        ).toTrace(startedAt)
    }

    private fun fetchNightlyCompatibleWorkflowArtifactSnapshotFromPublicApi(
        owner: String,
        repo: String,
        workflowId: String,
        runLimit: Int,
        artifactsPerRun: Int,
        artifactRunLimit: Int,
        branch: String,
        event: String,
        status: String,
        actor: String,
        created: String,
        headSha: String,
        excludePullRequests: Boolean
    ): Result<GitHubActionsWorkflowArtifactsSnapshot> = runCatching {
        val workflow = findPublicApiWorkflowForNightly(owner, repo, workflowId).getOrThrow()
        val runs = fetchJson(
            url = apiUrls.workflowRuns(
                owner = owner,
                repo = repo,
                workflowId = workflow.id.toString(),
                limit = runLimit,
                branch = branch,
                event = event,
                status = status.nightlyPublicApiRunStatus(),
                actor = actor,
                created = created,
                headSha = headSha,
                excludePullRequests = excludePullRequests
            ),
            cacheTtlMillis = ACTIONS_RUNS_CACHE_TTL_MS
        ).getOrThrow().let(::parseWorkflowRuns)
        val artifactRuns = runs.take(artifactRunLimit.coerceAtLeast(0))
        val artifactsByRunId = mutableMapOf<Long, List<GitHubActionsArtifact>>()
        artifactRuns.forEach { run ->
            val artifacts = fetchJson(
                url = apiUrls.runArtifacts(owner, repo, run.id, artifactsPerRun),
                cacheTtlMillis = ACTIONS_ARTIFACT_CACHE_TTL_MS
            ).getOrThrow()
                .let { json -> parseArtifacts(json, fallbackWorkflowRunId = run.id) }
                .map { artifact ->
                    artifact.copy(
                        archiveDownloadUrl = apiUrls.nightlyRunArtifactDownload(
                            owner = owner,
                            repo = repo,
                            runId = run.id,
                            artifactName = artifact.name
                        )
                    )
                }
            artifactsByRunId[run.id] = artifacts
        }
        GitHubActionsWorkflowArtifactsSnapshot(
            owner = owner,
            repo = repo,
            workflowId = workflow.id.toString(),
            runs = runs.map { run ->
                GitHubActionsRunArtifacts(
                    run = run,
                    artifacts = artifactsByRunId[run.id].orEmpty()
                )
            }
        )
    }

    private fun findPublicApiWorkflowForNightly(
        owner: String,
        repo: String,
        workflowId: String
    ): Result<GitHubActionsWorkflow> = runCatching {
        workflowId.trim().toLongOrNull()?.takeIf { it > 0L }?.let { id ->
            return@runCatching GitHubActionsWorkflow(
                id = id,
                name = id.toString(),
                path = workflowId.trim()
            )
        }
        val workflows = fetchJson(
            url = apiUrls.workflows(owner, repo, DEFAULT_WORKFLOW_LIMIT),
            cacheTtlMillis = ACTIONS_METADATA_CACHE_TTL_MS
        ).getOrThrow().let(::parseWorkflows)
        selectPublicApiWorkflowForNightly(workflows, workflowId)
            ?: error("GitHub public API found no matching workflow: $workflowId")
    }

    private fun selectPublicApiWorkflowForNightly(
        workflows: List<GitHubActionsWorkflow>,
        workflowId: String
    ): GitHubActionsWorkflow? {
        val lookup = workflowId.trim()
            .substringBefore('?')
            .trim()
        val lookupFile = lookup.substringAfterLast('/').trim()
        val lookupPath = lookup.trimStart('/')
        return workflows.firstOrNull { workflow -> workflow.id.toString() == lookup } ?:
            workflows.firstOrNull { workflow -> workflow.path.equals(lookupPath, ignoreCase = true) } ?:
            workflows.firstOrNull { workflow ->
                workflow.path.substringAfterLast('/').equals(lookupFile, ignoreCase = true)
            } ?:
            workflows.firstOrNull { workflow -> workflow.name.equals(lookup, ignoreCase = true) }
    }

    fun resolveArtifactDownloadUrl(
        artifact: GitHubActionsArtifact,
        owner: String = "",
        repo: String = "",
        preferApiTokenRedirect: Boolean = false
    ): Result<GitHubActionsArtifactDownloadResolution> {
        if (useNightlyLink) {
            val nightlyResult = nightlyRepository.resolveArtifactDownloadUrl(
                artifact = artifact,
                owner = owner,
                repo = repo
            )
            val requiresApiRedirect = artifact.requiresApiBackedNightlyDownload()
            if (
                !preferApiTokenRedirect ||
                sanitizedToken.isBlank() ||
                owner.isBlank() ||
                repo.isBlank() ||
                artifact.id <= 0L
            ) {
                if (requiresApiRedirect) {
                    return Result.failure(IllegalStateException(buildNightlyRawArtifactDownloadMessage(artifact.name)))
                }
                return nightlyResult
            }
            return resolveArtifactDownloadUrl(apiUrls.artifactDownload(owner, repo, artifact.id))
                .map { resolvedUrl ->
                    GitHubActionsArtifactDownloadResolution(
                        artifactId = artifact.id,
                        downloadUrl = resolvedUrl
                    )
                }
                .recoverCatching { error ->
                    if (requiresApiRedirect) throw error
                    nightlyResult.getOrThrow()
                }
        }
        if (sanitizedToken.isBlank()) {
            return Result.failure(IllegalStateException("A GitHub token is required to download Actions artifacts"))
        }
        val url = artifact.archiveDownloadUrl.trim().ifBlank {
            if (owner.isBlank() || repo.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("The artifact is missing a download URL and repository information")
                )
            }
            apiUrls.artifactDownload(owner, repo, artifact.id)
        }
        return resolveArtifactDownloadUrl(url).map { resolvedUrl ->
            GitHubActionsArtifactDownloadResolution(
                artifactId = artifact.id,
                downloadUrl = resolvedUrl
            )
        }
    }

    fun resolveArtifactShareUrl(
        artifact: GitHubActionsArtifact,
        owner: String = "",
        repo: String = ""
    ): Result<GitHubActionsArtifactDownloadResolution> {
        if (useNightlyLink) {
            return nightlyRepository.resolveArtifactDownloadUrl(
                artifact = artifact,
                owner = owner,
                repo = repo
            )
        }
        return resolveArtifactDownloadUrl(
            artifact = artifact,
            owner = owner,
            repo = repo
        )
    }

    internal fun parseWorkflows(json: String): List<GitHubActionsWorkflow> =
        GitHubActionsJsonParser.parseWorkflows(json)

    internal fun parseRepositoryInfo(
        json: String,
        fallbackOwner: String,
        fallbackRepo: String
    ): GitHubActionsRepositoryInfo =
        GitHubActionsJsonParser.parseRepositoryInfo(json, fallbackOwner, fallbackRepo)

    internal fun parseWorkflowRuns(json: String): List<GitHubActionsWorkflowRun> =
        GitHubActionsJsonParser.parseWorkflowRuns(json)

    internal fun parseWorkflowRun(json: String): GitHubActionsWorkflowRun =
        GitHubActionsJsonParser.parseWorkflowRun(json)

    internal fun parseArtifacts(
        json: String,
        fallbackWorkflowRunId: Long = 0L
    ): List<GitHubActionsArtifact> =
        GitHubActionsJsonParser.parseArtifacts(json, fallbackWorkflowRunId)

    private fun resolveArtifactDownloadUrl(url: String): Result<String> =
        apiClient.resolveRedirectDownloadUrl(url)

    private fun fetchJson(
        url: String,
        cacheTtlMillis: Long = 0L
    ): Result<String> = apiClient.fetchJson(url, cacheTtlMillis)

    private fun <T> Result<T>.toTrace(startedAt: Long): GitHubStrategyLoadTrace<T> {
        return GitHubStrategyLoadTrace(
            result = this,
            fromCache = false,
            elapsedMs = System.currentTimeMillis() - startedAt,
            authMode = authMode
        )
    }

    private fun requireActionsApiToken(): Result<Unit> {
        return if (requireApiTokenForApiStrategy && sanitizedToken.isBlank()) {
            Result.failure(IllegalStateException("GitHub Actions API Token mode requires a token"))
        } else {
            Result.success(Unit)
        }
    }

    private fun String.nightlyPublicApiRunStatus(): String {
        val normalized = trim()
        return when {
            normalized.isBlank() -> "success"
            normalized.equals("completed", ignoreCase = true) -> "success"
            else -> normalized
        }
    }

    private fun GitHubActionsWorkflowArtifactsSnapshot.requiresPublicApiMetadataForNightlyDownload(): Boolean {
        return artifacts.any { artifact ->
            artifact.requiresApiBackedNightlyDownload() &&
                (
                    artifact.sizeBytes <= 0L ||
                        artifact.digest.isBlank() ||
                        artifact.workflowRunHeadSha.isBlank()
                    )
        }
    }

    private fun GitHubActionsArtifact.requiresApiBackedNightlyDownload(): Boolean {
        val normalizedName = name.trim().lowercase()
        if (normalizedName.isBlank()) return false
        val nightlyUrl = apiUrls.isNightlyUrl(archiveDownloadUrl)
        val rawAndroidArtifact = RAW_ANDROID_ARTIFACT_EXTENSIONS.any { extension ->
            normalizedName.endsWith(extension)
        }
        return nightlyUrl && rawAndroidArtifact
    }

    private fun buildNightlyRawArtifactDownloadMessage(artifactName: String): String {
        val name = artifactName.trim().ifBlank { "artifact" }
        return "nightly.link cannot directly download raw APK artifact: $name. " +
            "Enter a GitHub API Token or switch to the GitHub API Token path."
    }

    companion object {
        private const val DEFAULT_WORKFLOW_LIMIT = 50
        private const val DEFAULT_RUN_LIMIT = 20
        private const val DEFAULT_ARTIFACT_LIMIT = 100
        private const val MAX_ARTIFACT_FETCH_CONCURRENCY = 4
        private const val ACTIONS_METADATA_CACHE_TTL_MS = 90_000L
        private const val ACTIONS_RUNS_CACHE_TTL_MS = 10_000L
        private const val ACTIONS_ARTIFACT_CACHE_TTL_MS = 45_000L
        private const val DEFAULT_GITHUB_API_BASE_URL = "https://api.github.com"
        private const val DEFAULT_GITHUB_HTML_BASE_URL = "https://github.com"
        private const val DEFAULT_NIGHTLY_LINK_BASE_URL = "https://nightly.link"
        private val RAW_ANDROID_ARTIFACT_EXTENSIONS = setOf(".apk", ".apks", ".aab")

        private val githubClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(18, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(14, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .fastFallback(true)
                .build()
        }

        fun fromLookupConfig(config: GitHubLookupConfig): GitHubActionsRepository {
            return GitHubActionsRepository(
                apiToken = config.apiToken,
                actionsStrategy = config.actionsStrategy,
                requireApiTokenForApiStrategy = true
            )
        }
    }
}
