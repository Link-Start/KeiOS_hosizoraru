package os.kei.feature.github.domain

import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.data.remote.GitHubApkInfoRepository
import os.kei.feature.github.data.remote.GitHubApkPackageNameScanRepository
import os.kei.feature.github.data.remote.GitHubAtomReleaseStrategy
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetRepository
import os.kei.feature.github.data.remote.GitHubRepositoryDiscoveryRepository
import os.kei.feature.github.model.GitHubApkManifestInfo
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object GitHubStrategyBenchmarkService {
    private const val DEFAULT_TARGET_LIMIT = 6
    private const val DEFAULT_ASSET_TARGET_LIMIT = 3
    private const val DEFAULT_APK_INFO_TARGET_LIMIT = 2
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
            selectedStrategy = GitHubLookupStrategyOption.AtomFeed,
            apiToken = apiToken.trim()
        )
        val apiLookupConfig = GitHubLookupConfig(
            selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
            apiToken = apiToken.trim()
        )
        val atomAssetFetcher = GitHubBenchmarkReleaseAssetFetcher(
            lookupConfig = atomLookupConfig,
            loadSnapshotTrace = { target ->
                GitHubAtomReleaseStrategy.loadSnapshotTrace(target.owner, target.repo)
            }
        )
        val apiAssetFetcher = GitHubBenchmarkReleaseAssetFetcher(
            lookupConfig = apiLookupConfig,
            loadSnapshotTrace = { target ->
                apiStrategy.loadSnapshotTrace(
                    target.owner,
                    target.repo
                )
            }
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
                loadReleaseAssets = releaseAssetsLoader(atomLookupConfig, atomAssetFetcher),
                loadReleaseNotes = releaseNotesLoader(atomLookupConfig, atomAssetFetcher),
                inspectApkManifest = apkManifestLoader(atomLookupConfig, atomAssetFetcher),
                scanPackageName = packageNameScanLoader(atomLookupConfig),
                scanRepository = repositoryScanLoader(atomLookupConfig)
            ),
            GitHubStrategyBenchmarkRunner(
                strategyId = apiStrategy.id,
                displayName = "API",
                clearCaches = { apiStrategy.clearCaches() },
                load = { target -> apiStrategy.loadSnapshotTrace(target.owner, target.repo) },
                loadReleaseAssets = releaseAssetsLoader(apiLookupConfig, apiAssetFetcher),
                loadReleaseNotes = releaseNotesLoader(apiLookupConfig, apiAssetFetcher),
                inspectApkManifest = apkManifestLoader(apiLookupConfig, apiAssetFetcher),
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
        val assetTargets = targets.take(DEFAULT_ASSET_TARGET_LIMIT)
        val releaseAssetSamples = loadReleaseAssetsSamples(
            targets = assetTargets,
            maxConcurrency = maxConcurrency.coerceAtMost(DEFAULT_ASSET_TARGET_LIMIT)
        )
        val releaseNotesSamples = loadReleaseNotesSamples(
            targets = assetTargets,
            maxConcurrency = maxConcurrency.coerceAtMost(DEFAULT_ASSET_TARGET_LIMIT)
        )
        val apkManifestSamples = inspectApkManifestSamples(
            targets = assetTargets.take(DEFAULT_APK_INFO_TARGET_LIMIT),
            maxConcurrency = maxConcurrency.coerceAtMost(DEFAULT_APK_INFO_TARGET_LIMIT)
        )

        return GitHubStrategyBenchmarkResult(
            strategyId = strategyId,
            displayName = displayName,
            authMode = authMode,
            coldSamples = coldSamples.map { it.sample } +
                    releaseAssetSamples.map { it.sample } +
                    releaseNotesSamples.map { it.sample } +
                    apkManifestSamples.map { it.sample } +
                    packageSamples.map { it.sample } +
                    repositorySamples.map { it.sample },
            warmSamples = warmSamples.map { it.sample }
        )
    }

    private fun GitHubStrategyBenchmarkRunner.loadReleaseAssetsSamples(
        targets: List<GitHubRepoTarget>,
        maxConcurrency: Int
    ): List<SampleEnvelope> {
        val loader = loadReleaseAssets ?: return emptyList()
        return runConcurrently(
            items = targets,
            maxConcurrency = maxConcurrency.coerceAtMost(DEFAULT_ASSET_TARGET_LIMIT)
        ) { target ->
            loader(target).toReleaseAssetsSample(target)
        }
    }

    private fun GitHubStrategyBenchmarkRunner.loadReleaseNotesSamples(
        targets: List<GitHubRepoTarget>,
        maxConcurrency: Int
    ): List<SampleEnvelope> {
        val loader = loadReleaseNotes ?: return emptyList()
        return runConcurrently(
            items = targets,
            maxConcurrency = maxConcurrency.coerceAtMost(DEFAULT_ASSET_TARGET_LIMIT)
        ) { target ->
            loader(target).toReleaseNotesSample(target)
        }
    }

    private fun GitHubStrategyBenchmarkRunner.inspectApkManifestSamples(
        targets: List<GitHubRepoTarget>,
        maxConcurrency: Int
    ): List<SampleEnvelope> {
        val loader = inspectApkManifest ?: return emptyList()
        return runConcurrently(
            items = targets,
            maxConcurrency = maxConcurrency.coerceAtMost(DEFAULT_APK_INFO_TARGET_LIMIT)
        ) { target ->
            loader(target).toApkManifestSample(target)
        }
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

    private fun GitHubStrategyLoadTrace<GitHubReleaseAssetBundle>.toReleaseAssetsSample(
        target: GitHubRepoTarget
    ): SampleEnvelope {
        val bundle = result.getOrNull()
        return SampleEnvelope(
            sample = GitHubStrategyBenchmarkSample(
                target = target,
                testType = GitHubStrategyBenchmarkTestType.ReleaseAssets,
                success = result.isSuccess,
                fromCache = fromCache,
                elapsedMs = elapsedMs,
                message = result.exceptionOrNull()?.message.orEmpty().ifBlank {
                    bundle?.tagName.orEmpty()
                },
                assetCount = bundle?.assets?.size ?: 0,
                stableTag = bundle?.tagName.orEmpty()
            ),
            authMode = authMode
        )
    }

    private fun GitHubStrategyLoadTrace<GitHubReleaseAssetBundle>.toReleaseNotesSample(
        target: GitHubRepoTarget
    ): SampleEnvelope {
        val bundle = result.getOrNull()
        val bodyLength = bundle?.releaseNotesBody.orEmpty().length
        return SampleEnvelope(
            sample = GitHubStrategyBenchmarkSample(
                target = target,
                testType = GitHubStrategyBenchmarkTestType.ReleaseNotes,
                success = result.isSuccess,
                fromCache = fromCache,
                elapsedMs = elapsedMs,
                message = result.exceptionOrNull()?.message.orEmpty().ifBlank {
                    if (bodyLength > 0) "${bodyLength} chars" else "empty notes"
                },
                releaseNotesLength = bodyLength,
                stableTag = bundle?.tagName.orEmpty()
            ),
            authMode = authMode
        )
    }

    private fun GitHubStrategyLoadTrace<GitHubApkManifestInfo>.toApkManifestSample(
        target: GitHubRepoTarget
    ): SampleEnvelope {
        val info = result.getOrNull()
        return SampleEnvelope(
            sample = GitHubStrategyBenchmarkSample(
                target = target,
                testType = GitHubStrategyBenchmarkTestType.ApkManifest,
                success = result.isSuccess && info?.packageName.orEmpty().isNotBlank(),
                fromCache = fromCache,
                elapsedMs = elapsedMs,
                message = result.exceptionOrNull()?.message.orEmpty().ifBlank {
                    info?.packageName.orEmpty()
                },
                packageName = info?.packageName.orEmpty()
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

    private fun releaseAssetsLoader(
        lookupConfig: GitHubLookupConfig,
        assetFetcher: GitHubBenchmarkReleaseAssetFetcher
    ): (GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubReleaseAssetBundle> {
        return { target ->
            timedTrace(authMode = lookupConfig.authModeOrNull()) {
                assetFetcher.loadBundle(target, includeAllAssets = false)
            }
        }
    }

    private fun releaseNotesLoader(
        lookupConfig: GitHubLookupConfig,
        assetFetcher: GitHubBenchmarkReleaseAssetFetcher
    ): (GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubReleaseAssetBundle> {
        return { target ->
            timedTrace(authMode = lookupConfig.authModeOrNull()) {
                assetFetcher.loadBundle(target, includeAllAssets = true)
            }
        }
    }

    private fun apkManifestLoader(
        lookupConfig: GitHubLookupConfig,
        assetFetcher: GitHubBenchmarkReleaseAssetFetcher
    ): (GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubApkManifestInfo> {
        val apkInfoRepository = GitHubApkInfoRepository()
        return { target ->
            timedTrace(authMode = lookupConfig.authModeOrNull()) {
                val bundle = assetFetcher.loadBundle(target, includeAllAssets = false)
                val asset =
                    bundle.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                        ?: error("No APK asset found")
                apkInfoRepository.inspect(
                    asset = asset,
                    lookupConfig = lookupConfig
                ).getOrThrow()
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
                        preferredRepoUrl = target.normalizedRepoUrl,
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
    val loadReleaseAssets: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubReleaseAssetBundle>)? = null,
    val loadReleaseNotes: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubReleaseAssetBundle>)? = null,
    val inspectApkManifest: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubApkManifestInfo>)? = null,
    val scanPackageName: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<String>)? = null,
    val scanRepository: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<String>)? = null
)

private class GitHubBenchmarkReleaseAssetFetcher(
    private val lookupConfig: GitHubLookupConfig,
    private val loadSnapshotTrace: (GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubRepositoryReleaseSnapshot>
) {
    private val snapshotCache =
        ConcurrentHashMap<String, Result<GitHubRepositoryReleaseSnapshot>>()
    private val bundleCache =
        ConcurrentHashMap<String, Result<GitHubReleaseAssetBundle>>()

    fun loadBundle(
        target: GitHubRepoTarget,
        includeAllAssets: Boolean
    ): GitHubReleaseAssetBundle {
        val key = "${target.id}|all=$includeAllAssets"
        return bundleCache.computeIfAbsent(key) {
            runCatching {
                val release = loadSnapshotResult(target)
                    .getOrThrow()
                    .let { snapshot ->
                        snapshot.latestStable.takeIf { snapshot.hasStableRelease }
                            ?: snapshot.latestPreRelease
                            ?: error("No release found")
                    }
                GitHubReleaseAssetRepository.fetchApkAssets(
                    owner = target.owner,
                    repo = target.repo,
                    rawTag = release.rawTag,
                    releaseUrl = release.link,
                    preferHtml = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.AtomFeed,
                    aggressiveFiltering = false,
                    includeAllAssets = includeAllAssets,
                    apiToken = lookupConfig.apiToken
                ).getOrThrow()
            }
        }.getOrThrow()
    }

    private fun loadSnapshotResult(target: GitHubRepoTarget): Result<GitHubRepositoryReleaseSnapshot> {
        return snapshotCache.computeIfAbsent(target.id) {
            loadSnapshotTrace.invoke(target).result
        }
    }
}
