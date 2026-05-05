package os.kei.feature.github.domain

import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubAtomReleaseStrategy
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import os.kei.feature.github.model.GitHubRepoTarget
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubStrategyBenchmarkReport
import os.kei.feature.github.model.GitHubStrategyBenchmarkResult
import os.kei.feature.github.model.GitHubStrategyBenchmarkSample
import os.kei.feature.github.model.GitHubStrategyBenchmarkTestType
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedApp
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object GitHubStrategyBenchmarkService {
    private const val DEFAULT_TARGET_LIMIT = 6
    private const val DEFAULT_SCAN_TARGET_LIMIT = 3
    private const val DEFAULT_REPOSITORY_SCAN_TARGET_LIMIT = 2
    private const val DEFAULT_BENCHMARK_CONCURRENCY = 4

    fun buildTargets(
        trackedItems: List<GitHubTrackedApp>,
        limit: Int = DEFAULT_TARGET_LIMIT
    ): List<GitHubRepoTarget> {
        return trackedItems
            .groupBy { item ->
                "${item.owner.lowercase()}/${item.repo.lowercase()}"
            }
            .values
            .map { repoItems ->
                val item = repoItems.first()
                val packageNames = repoItems
                    .map { it.packageName.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                val packageNameForScan = packageNames.singleOrNull().orEmpty()
                GitHubRepoTarget(
                    owner = item.owner,
                    repo = item.repo,
                    packageName = packageNameForScan,
                    appLabel = if (packageNameForScan.isBlank()) "" else item.appLabel,
                    repoUrl = item.repoUrl
                )
            }
            .take(limit)
    }

    fun compareTargets(
        targets: List<GitHubRepoTarget>,
        apiToken: String = ""
    ): GitHubStrategyBenchmarkReport {
        val distinctTargets = targets
            .distinctBy { it.id }
        if (distinctTargets.isEmpty()) {
            return GitHubStrategyBenchmarkReport(
                targets = emptyList(),
                results = emptyList()
            )
        }

        val apiStrategy = GitHubApiTokenReleaseStrategy(apiToken = apiToken.trim())
        val atomLookupConfig = GitHubLookupConfig(
            selectedStrategy = GitHubLookupStrategyOption.AtomFeed
        )
        val apiLookupConfig = GitHubLookupConfig(
            selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
            apiToken = apiToken.trim()
        )
        val runners = listOf(
            GitHubStrategyBenchmarkRunner(
                strategyId = GitHubAtomReleaseStrategy.id,
                displayName = "Atom",
                clearCaches = { GitHubAtomReleaseStrategy.clearCaches() },
                load = { target ->
                    GitHubAtomReleaseStrategy.loadSnapshotTrace(
                        target.owner,
                        target.repo
                    )
                },
                scanPackageName = packageNameScanLoader(atomLookupConfig),
                scanRepository = repositoryScanLoader(atomLookupConfig)
            ),
            GitHubStrategyBenchmarkRunner(
                strategyId = apiStrategy.id,
                displayName = "API",
                clearCaches = { apiStrategy.clearCaches() },
                load = { target -> apiStrategy.loadSnapshotTrace(target.owner, target.repo) },
                scanPackageName = packageNameScanLoader(apiLookupConfig),
                scanRepository = repositoryScanLoader(apiLookupConfig)
            )
        )

        return compareTargetsWithRunners(
            targets = distinctTargets,
            runners = runners
        )
    }

    internal fun compareTargetsWithRunners(
        targets: List<GitHubRepoTarget>,
        runners: List<GitHubStrategyBenchmarkRunner>,
        maxConcurrency: Int = DEFAULT_BENCHMARK_CONCURRENCY
    ): GitHubStrategyBenchmarkReport {
        if (targets.isEmpty() || runners.isEmpty()) {
            return GitHubStrategyBenchmarkReport(
                targets = targets,
                results = emptyList()
            )
        }
        return GitHubStrategyBenchmarkReport(
            targets = targets,
            results = runConcurrently(
                items = runners,
                maxConcurrency = maxConcurrency.coerceAtLeast(1)
            ) { runner ->
                runner.run(
                    targets = targets,
                    maxConcurrency = maxConcurrency
                )
            }
        )
    }

    private fun GitHubStrategyBenchmarkRunner.run(
        targets: List<GitHubRepoTarget>,
        maxConcurrency: Int
    ): GitHubStrategyBenchmarkResult {
        clearCaches()
        val coldSamples = loadSamples(
            targets = targets,
            maxConcurrency = maxConcurrency
        )
        val warmSamples = loadSamples(
            targets = targets,
            maxConcurrency = maxConcurrency
        )
        val packageSamples = scanPackageNameSamples(
            targets = targets
                .filter { it.normalizedRepoUrl.isNotBlank() }
                .take(DEFAULT_SCAN_TARGET_LIMIT),
            maxConcurrency = maxConcurrency
        )
        val repositorySamples = scanRepositorySamples(
            targets = targets
                .filter { it.packageName.isNotBlank() }
                .take(DEFAULT_REPOSITORY_SCAN_TARGET_LIMIT),
            maxConcurrency = maxConcurrency.coerceAtMost(2)
        )
        val authMode = coldSamples.firstOrNull()?.authMode ?: warmSamples.firstOrNull()?.authMode

        return GitHubStrategyBenchmarkResult(
            strategyId = strategyId,
            displayName = displayName,
            authMode = authMode,
            coldSamples = coldSamples.map { it.sample } +
                    packageSamples.map { it.sample } +
                    repositorySamples.map { it.sample },
            warmSamples = warmSamples.map { it.sample }
        )
    }

    private fun GitHubStrategyBenchmarkRunner.loadSamples(
        targets: List<GitHubRepoTarget>,
        maxConcurrency: Int
    ): List<SampleEnvelope> {
        return runConcurrently(
            items = targets,
            maxConcurrency = maxConcurrency
        ) { target ->
            load(target).toReleaseSample(target)
        }
    }

    private fun GitHubStrategyBenchmarkRunner.scanPackageNameSamples(
        targets: List<GitHubRepoTarget>,
        maxConcurrency: Int
    ): List<SampleEnvelope> {
        val loader = scanPackageName ?: return emptyList()
        return runConcurrently(
            items = targets,
            maxConcurrency = maxConcurrency.coerceAtMost(DEFAULT_SCAN_TARGET_LIMIT)
        ) { target ->
            loader(target).toPackageNameSample(target)
        }
    }

    private fun GitHubStrategyBenchmarkRunner.scanRepositorySamples(
        targets: List<GitHubRepoTarget>,
        maxConcurrency: Int
    ): List<SampleEnvelope> {
        val loader = scanRepository ?: return emptyList()
        return runConcurrently(
            items = targets,
            maxConcurrency = maxConcurrency.coerceAtMost(DEFAULT_REPOSITORY_SCAN_TARGET_LIMIT)
        ) { target ->
            loader(target).toRepositoryScanSample(target)
        }
    }

    private fun <T, R> runConcurrently(
        items: List<T>,
        maxConcurrency: Int,
        block: (T) -> R
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        val concurrency = items.size.coerceAtMost(maxConcurrency.coerceAtLeast(1))
        if (concurrency <= 1) return items.map(block)
        val executor = Executors.newFixedThreadPool(concurrency)
        return try {
            executor.invokeAll(items.map { item -> Callable { block(item) } })
                .map { future -> future.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private data class SampleEnvelope(
        val sample: GitHubStrategyBenchmarkSample,
        val authMode: os.kei.feature.github.model.GitHubApiAuthMode?
    )

    private fun GitHubStrategyLoadTrace<GitHubRepositoryReleaseSnapshot>.toReleaseSample(
        target: GitHubRepoTarget
    ): SampleEnvelope {
        val snapshot = result.getOrNull()
        val errorMessage = result.exceptionOrNull()?.message.orEmpty()
        return SampleEnvelope(
            sample = GitHubStrategyBenchmarkSample(
                target = target,
                testType = GitHubStrategyBenchmarkTestType.ReleaseSnapshot,
                success = result.isSuccess,
                fromCache = fromCache,
                elapsedMs = elapsedMs,
                message = errorMessage,
                stableTag = snapshot?.takeIf { it.hasStableRelease }?.latestStable?.rawTag.orEmpty(),
                preReleaseTag = snapshot?.latestPreRelease?.rawTag.orEmpty()
            ),
            authMode = authMode
        )
    }

    private fun GitHubStrategyLoadTrace<String>.toPackageNameSample(
        target: GitHubRepoTarget
    ): SampleEnvelope {
        val packageName = result.getOrNull().orEmpty()
        val expectedPackageName = target.packageName.trim()
        val success = result.isSuccess &&
                (expectedPackageName.isBlank() || packageName.equals(
                    expectedPackageName,
                    ignoreCase = true
                ))
        val message = when {
            result.isFailure -> result.exceptionOrNull()?.message.orEmpty()
            !success -> "Package mismatch: $packageName"
            else -> packageName
        }
        return SampleEnvelope(
            sample = GitHubStrategyBenchmarkSample(
                target = target,
                testType = GitHubStrategyBenchmarkTestType.PackageNameScan,
                success = success,
                fromCache = fromCache,
                elapsedMs = elapsedMs,
                message = message,
                packageName = packageName
            ),
            authMode = authMode
        )
    }

    private fun GitHubStrategyLoadTrace<String>.toRepositoryScanSample(
        target: GitHubRepoTarget
    ): SampleEnvelope {
        val matchedRepository = result.getOrNull().orEmpty()
        val success = result.isSuccess && matchedRepository.equals(target.id, ignoreCase = true)
        val message = when {
            result.isFailure -> result.exceptionOrNull()?.message.orEmpty()
            !success -> "Repository mismatch: $matchedRepository"
            else -> matchedRepository
        }
        return SampleEnvelope(
            sample = GitHubStrategyBenchmarkSample(
                target = target,
                testType = GitHubStrategyBenchmarkTestType.RepositoryScan,
                success = success,
                fromCache = fromCache,
                elapsedMs = elapsedMs,
                message = message,
                matchedRepository = matchedRepository
            ),
            authMode = authMode
        )
    }

    private fun packageNameScanLoader(
        lookupConfig: GitHubLookupConfig
    ): (GitHubRepoTarget) -> GitHubStrategyLoadTrace<String> {
        val scanner = GitHubApkPackageNameScanner(GitHubApkPackageNameScanRepository())
        return { target ->
            timedTrace(authMode = lookupConfig.authModeOrNull()) {
                scanner.scan(
                    GitHubApkPackageNameScanRequest(
                        repoUrl = target.normalizedRepoUrl,
                        lookupConfig = lookupConfig
                    )
                ).getOrThrow().packageName
            }
        }
    }

    private fun repositoryScanLoader(
        lookupConfig: GitHubLookupConfig
    ): (GitHubRepoTarget) -> GitHubStrategyLoadTrace<String> {
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = GitHubRepositoryDiscoveryRepository(apiToken = lookupConfig.apiToken),
            packageNameScanner = GitHubApkPackageNameScanner(GitHubApkPackageNameScanRepository())
        )
        return { target ->
            timedTrace(authMode = lookupConfig.authModeOrNull()) {
                val result = resolver.scanRepositoriesForPackage(
                    GitHubPackageRepositoryScanRequest(
                        packageName = target.packageName,
                        appLabel = target.appLabel,
                        lookupConfig = lookupConfig,
                        candidateLimit = 10,
                        verificationLimit = 3
                    )
                ).getOrThrow()
                val match = result.matchedCandidates.firstOrNull()
                    ?: error("No matching repository found")
                "${match.repository.owner}/${match.repository.repo}"
            }
        }
    }

    private fun <T> timedTrace(
        authMode: os.kei.feature.github.model.GitHubApiAuthMode?,
        block: () -> T
    ): GitHubStrategyLoadTrace<T> {
        val startedAt = System.currentTimeMillis()
        val result = runCatching(block)
        return GitHubStrategyLoadTrace(
            result = result,
            fromCache = false,
            elapsedMs = System.currentTimeMillis() - startedAt,
            authMode = authMode
        )
    }

    private fun GitHubLookupConfig.authModeOrNull(): os.kei.feature.github.model.GitHubApiAuthMode? {
        return when (selectedStrategy) {
            GitHubLookupStrategyOption.AtomFeed -> null
            GitHubLookupStrategyOption.GitHubApiToken -> {
                if (apiToken.isBlank()) {
                    os.kei.feature.github.model.GitHubApiAuthMode.Guest
                } else {
                    os.kei.feature.github.model.GitHubApiAuthMode.Token
                }
            }
        }
    }
}

internal data class GitHubStrategyBenchmarkRunner(
    val strategyId: String,
    val displayName: String,
    val clearCaches: () -> Unit,
    val load: (GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubRepositoryReleaseSnapshot>,
    val scanPackageName: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<String>)? = null,
    val scanRepository: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<String>)? = null
)
