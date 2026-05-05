package os.kei.feature.github.domain

import org.junit.Test
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
    fun `benchmark runs strategies concurrently`() {
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
    fun `benchmark runs targets concurrently within each strategy`() {
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
    fun `benchmark includes package and repository scan samples`() {
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

    private fun benchmarkRunner(
        strategyId: String,
        activeLoads: AtomicInteger,
        maxActiveLoads: AtomicInteger,
        firstColdWave: CountDownLatch,
        scanPackageName: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<String>)? = null,
        scanRepository: ((GitHubRepoTarget) -> GitHubStrategyLoadTrace<String>)? = null
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
            scanPackageName = scanPackageName,
            scanRepository = scanRepository
        )
    }

    private fun updateMax(maxActiveLoads: AtomicInteger, active: Int) {
        while (true) {
            val current = maxActiveLoads.get()
            if (active <= current || maxActiveLoads.compareAndSet(current, active)) return
        }
    }
}
