package os.kei.feature.github.domain

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubRepoTarget
import os.kei.feature.github.model.GitHubRepositoryReleaseSnapshot
import os.kei.feature.github.model.GitHubStrategyBenchmarkTestType
import os.kei.feature.github.model.GitHubStrategyLoadTrace
import os.kei.feature.github.model.GitHubTrackedApp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubStrategyBenchmarkServiceTest {
    @Test
    fun `benchmark runs strategies concurrently`() = runBlocking {
        val activeLoads = AtomicInteger(0)
        val maxActiveLoads = AtomicInteger(0)
        val firstColdWave = CountDownLatch(2)
        val runners = listOf(
            benchmarkRunner("atom", activeLoads, maxActiveLoads, firstColdWave),
            benchmarkRunner("api", activeLoads, maxActiveLoads, firstColdWave)
        )

        val report = GitHubStrategyBenchmarkService.compareTargetsWithRunners(
            targets = listOf(GitHubRepoTarget("demo", "app")),
            runners = runners,
            maxConcurrency = 2
        )

        assertEquals(listOf("atom", "api"), report.results.map { it.strategyId })
        assertTrue(maxActiveLoads.get() >= 2)
    }

    @Test
    fun `benchmark runs targets concurrently within each strategy`() = runBlocking {
        val activeLoads = AtomicInteger(0)
        val maxActiveLoads = AtomicInteger(0)
        val firstColdWave = CountDownLatch(2)
        val runner = benchmarkRunner("atom", activeLoads, maxActiveLoads, firstColdWave)

        val report = GitHubStrategyBenchmarkService.compareTargetsWithRunners(
            targets = listOf(
                GitHubRepoTarget("demo", "app"),
                GitHubRepoTarget("demo", "lib")
            ),
            runners = listOf(runner),
            maxConcurrency = 2
        )

        assertEquals(2, report.results.single().coldSamples.size)
        assertTrue(maxActiveLoads.get() >= 2)
    }

    @Test
    fun `benchmark targets retain package metadata for scan tests`() {
        val targets = GitHubStrategyBenchmarkService.buildTargets(
            listOf(
                GitHubTrackedApp(
                    repoUrl = "https://github.com/demo/app",
                    owner = "demo",
                    repo = "app",
                    packageName = "com.demo.app",
                    appLabel = "Demo App"
                )
            )
        )

        assertEquals("com.demo.app", targets.single().packageName)
        assertEquals("Demo App", targets.single().appLabel)
        assertEquals("https://github.com/demo/app", targets.single().normalizedRepoUrl)
    }

    @Test
    fun `benchmark target skips package scan metadata for repository variants`() {
        val targets = GitHubStrategyBenchmarkService.buildTargets(
            listOf(
                GitHubTrackedApp(
                    repoUrl = "https://github.com/demo/app",
                    owner = "demo",
                    repo = "app",
                    packageName = "com.demo.app",
                    appLabel = "Demo App"
                ),
                GitHubTrackedApp(
                    repoUrl = "https://github.com/demo/app",
                    owner = "demo",
                    repo = "app",
                    packageName = "com.demo.app.debug",
                    appLabel = "Demo App Debug"
                )
            )
        )

        assertEquals(1, targets.size)
        assertEquals("", targets.single().packageName)
        assertEquals("", targets.single().appLabel)
    }

    @Test
    fun `benchmark keeps exported track fixture bounded across dual mode tasks`() = runBlocking {
        val items = GitHubTrackExportFixture.trackedItems
        val expectedTargetCount = GitHubTrackExportFixture.gitHubRepositoryItems
            .distinctBy { item -> "${item.owner.lowercase()}/${item.repo.lowercase()}" }
            .size
        val targets = GitHubStrategyBenchmarkService.buildTargets(
            trackedItems = items,
            limit = items.size
        )
        val activeLoads = AtomicInteger(0)
        val maxActiveLoads = AtomicInteger(0)
        val firstColdWave = CountDownLatch(4)
        val runner = benchmarkRunner(
            strategyId = "fixture",
            activeLoads = activeLoads,
            maxActiveLoads = maxActiveLoads,
            firstColdWave = firstColdWave,
            loadReleaseAssets = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(releaseBundle(target = target, notes = "")),
                    fromCache = false,
                    elapsedMs = 4L
                )
            },
            loadReleaseNotes = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(
                        releaseBundle(
                            target = target,
                            notes = "Fixture notes"
                        )
                    ),
                    fromCache = false,
                    elapsedMs = 5L
                )
            },
            inspectApkManifest = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(
                        GitHubApkManifestInfo(
                            assetName = "${target.repo}.apk",
                            packageName = target.packageName
                        )
                    ),
                    fromCache = false,
                    elapsedMs = 6L
                )
            },
            scanPackageName = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(target.packageName),
                    fromCache = false,
                    elapsedMs = 2L
                )
            },
            scanRepository = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(target.id),
                    fromCache = false,
                    elapsedMs = 3L
                )
            }
        )

        val report = GitHubStrategyBenchmarkService.compareTargetsWithRunners(
            targets = targets,
            runners = listOf(runner),
            maxConcurrency = 4
        )
        val result = report.results.single()

        assertEquals(GitHubTrackExportFixture.expectedItemCount, items.size)
        assertEquals(expectedTargetCount, targets.size)
        assertEquals(expectedTargetCount, result.totalTargets)
        assertEquals(expectedTargetCount, result.warmSamples.size)
        assertEquals(3, result.samplesFor(GitHubStrategyBenchmarkTestType.ReleaseAssets).size)
        assertEquals(3, result.samplesFor(GitHubStrategyBenchmarkTestType.ReleaseNotes).size)
        assertEquals(2, result.samplesFor(GitHubStrategyBenchmarkTestType.ApkManifest).size)
        assertEquals(3, result.samplesFor(GitHubStrategyBenchmarkTestType.PackageNameScan).size)
        assertEquals(2, result.samplesFor(GitHubStrategyBenchmarkTestType.RepositoryScan).size)
        assertTrue(maxActiveLoads.get() >= 4)
    }

    @Test
    fun `benchmark includes package and repository scan samples`() = runBlocking {
        val runner = benchmarkRunner(
            strategyId = "atom",
            activeLoads = AtomicInteger(0),
            maxActiveLoads = AtomicInteger(0),
            firstColdWave = CountDownLatch(1),
            scanPackageName = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(target.packageName),
                    fromCache = false,
                    elapsedMs = 2L
                )
            },
            scanRepository = { target ->
                GitHubStrategyLoadTrace(
                    result = Result.success(target.id),
                    fromCache = false,
                    elapsedMs = 3L
                )
            }
        )

        val report = GitHubStrategyBenchmarkService.compareTargetsWithRunners(
            targets = listOf(
                GitHubRepoTarget(
                    owner = "demo",
                    repo = "app",
                    packageName = "com.demo.app",
                    appLabel = "Demo App"
                )
            ),
            runners = listOf(runner),
            maxConcurrency = 1
        )
        val result = report.results.single()

        assertEquals(1, result.samplesFor(GitHubStrategyBenchmarkTestType.PackageNameScan).size)
        assertEquals(1, result.samplesFor(GitHubStrategyBenchmarkTestType.RepositoryScan).size)
        assertEquals(1, result.successCountFor(GitHubStrategyBenchmarkTestType.PackageNameScan))
        assertEquals(1, result.successCountFor(GitHubStrategyBenchmarkTestType.RepositoryScan))
    }

    @Test
    fun `benchmark includes release asset notes and apk manifest samples`() = runBlocking {
        val runner = benchmarkRunner(
            strategyId = "atom",
            activeLoads = AtomicInteger(0),
            maxActiveLoads = AtomicInteger(0),
            firstColdWave = CountDownLatch(1),
            loadReleaseAssets = {
                GitHubStrategyLoadTrace(
                    result = Result.success(releaseBundle(notes = "")),
                    fromCache = false,
                    elapsedMs = 4L
                )
            },
            loadReleaseNotes = {
                GitHubStrategyLoadTrace(
                    result = Result.success(releaseBundle(notes = "Fixed bugs")),
                    fromCache = false,
                    elapsedMs = 5L
                )
            },
            inspectApkManifest = {
                GitHubStrategyLoadTrace(
                    result = Result.success(
                        GitHubApkManifestInfo(
                            assetName = "demo.apk",
                            packageName = "com.demo.app"
                        )
                    ),
                    fromCache = false,
                    elapsedMs = 6L
                )
            }
        )

        val report = GitHubStrategyBenchmarkService.compareTargetsWithRunners(
            targets = listOf(GitHubRepoTarget("demo", "app")),
            runners = listOf(runner),
            maxConcurrency = 1
        )
        val result = report.results.single()

        assertEquals(1, result.successCountFor(GitHubStrategyBenchmarkTestType.ReleaseAssets))
        assertEquals(1, result.successCountFor(GitHubStrategyBenchmarkTestType.ReleaseNotes))
        assertEquals(1, result.successCountFor(GitHubStrategyBenchmarkTestType.ApkManifest))
        assertEquals(
            1,
            result.samplesFor(GitHubStrategyBenchmarkTestType.ReleaseAssets).single().assetCount
        )
        assertEquals(
            10,
            result.samplesFor(GitHubStrategyBenchmarkTestType.ReleaseNotes)
                .single().releaseNotesLength
        )
        assertEquals(
            "com.demo.app",
            result.samplesFor(GitHubStrategyBenchmarkTestType.ApkManifest).single().packageName
        )
    }

    @Test
    fun `benchmark runs extended dual mode tasks concurrently`() = runBlocking {
        val activeLoads = AtomicInteger(0)
        val maxActiveLoads = AtomicInteger(0)
        val firstExtraWave = CountDownLatch(3)
        val runner = benchmarkRunner(
            strategyId = "atom",
            activeLoads = AtomicInteger(0),
            maxActiveLoads = AtomicInteger(0),
            firstColdWave = CountDownLatch(1),
            loadReleaseAssets = {
                enterExtraTask(activeLoads, maxActiveLoads, firstExtraWave)
                GitHubStrategyLoadTrace(
                    result = Result.success(releaseBundle(notes = "")),
                    fromCache = false,
                    elapsedMs = 4L
                )
            },
            loadReleaseNotes = {
                enterExtraTask(activeLoads, maxActiveLoads, firstExtraWave)
                GitHubStrategyLoadTrace(
                    result = Result.success(releaseBundle(notes = "Fixed bugs")),
                    fromCache = false,
                    elapsedMs = 5L
                )
            },
            inspectApkManifest = {
                enterExtraTask(activeLoads, maxActiveLoads, firstExtraWave)
                GitHubStrategyLoadTrace(
                    result = Result.success(
                        GitHubApkManifestInfo(
                            assetName = "demo.apk",
                            packageName = "com.demo.app"
                        )
                    ),
                    fromCache = false,
                    elapsedMs = 6L
                )
            }
        )

        GitHubStrategyBenchmarkService.compareTargetsWithRunners(
            targets = listOf(GitHubRepoTarget("demo", "app")),
            runners = listOf(runner),
            maxConcurrency = 3
        )

        assertTrue(maxActiveLoads.get() >= 2)
    }

    private fun benchmarkRunner(
        strategyId: String,
        activeLoads: AtomicInteger,
        maxActiveLoads: AtomicInteger,
        firstColdWave: CountDownLatch,
        loadReleaseAssets: (suspend (GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubReleaseAssetBundle>)? = null,
        loadReleaseNotes: (suspend (GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubReleaseAssetBundle>)? = null,
        inspectApkManifest: (suspend (GitHubRepoTarget) -> GitHubStrategyLoadTrace<GitHubApkManifestInfo>)? = null,
        scanPackageName: (suspend (GitHubRepoTarget) -> GitHubStrategyLoadTrace<String>)? = null,
        scanRepository: (suspend (GitHubRepoTarget) -> GitHubStrategyLoadTrace<String>)? = null
    ): GitHubStrategyBenchmarkRunner {
        return GitHubStrategyBenchmarkRunner(
            strategyId = strategyId,
            displayName = strategyId,
            clearCaches = {},
            load = {
                val active = activeLoads.incrementAndGet()
                updateMax(maxActiveLoads, active)
                firstColdWave.countDown()
                firstColdWave.await(500, TimeUnit.MILLISECONDS)
                activeLoads.decrementAndGet()
                GitHubStrategyLoadTrace<GitHubRepositoryReleaseSnapshot>(
                    result = Result.failure(IllegalStateException("demo")),
                    fromCache = false,
                    elapsedMs = 1L
                )
            },
            loadReleaseAssets = loadReleaseAssets,
            loadReleaseNotes = loadReleaseNotes,
            inspectApkManifest = inspectApkManifest,
            scanPackageName = scanPackageName,
            scanRepository = scanRepository
        )
    }

    private fun releaseBundle(
        notes: String,
        target: GitHubRepoTarget = GitHubRepoTarget("demo", "app")
    ): GitHubReleaseAssetBundle {
        return GitHubReleaseAssetBundle(
            releaseName = target.repo,
            tagName = "v1.0.0",
            htmlUrl = "${target.normalizedRepoUrl}/releases/tag/v1.0.0",
            releaseNotesBody = notes,
            assets = listOf(
                GitHubReleaseAssetFile(
                    name = "${target.repo}.apk",
                    downloadUrl = "${target.normalizedRepoUrl}/releases/download/v1.0.0/${target.repo}.apk",
                    sizeBytes = 1L,
                    downloadCount = 1
                )
            )
        )
    }

    private fun enterExtraTask(
        activeLoads: AtomicInteger,
        maxActiveLoads: AtomicInteger,
        firstExtraWave: CountDownLatch
    ) {
        val active = activeLoads.incrementAndGet()
        updateMax(maxActiveLoads, active)
        firstExtraWave.countDown()
        firstExtraWave.await(500, TimeUnit.MILLISECONDS)
        activeLoads.decrementAndGet()
    }

    private fun updateMax(maxActiveLoads: AtomicInteger, active: Int) {
        while (true) {
            val current = maxActiveLoads.get()
            if (active <= current || maxActiveLoads.compareAndSet(current, active)) return
        }
    }
}
