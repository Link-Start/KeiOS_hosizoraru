package os.kei.feature.github.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubTrackedRefreshBatchRunnerTest {
    @Test
    fun `run checks tracked items with bounded concurrency and aggregates counts`() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val items = (1..6).map { index -> tracked(index) }

        val result = GitHubTrackedRefreshBatchRunner.run(
            trackedItems = items,
            maxConcurrency = 2,
            dispatcher = Dispatchers.Default,
            refreshTimestampMs = NOW_MS
        ) { item ->
            val current = active.incrementAndGet()
            maxActive.updateAndGet { old -> maxOf(old, current) }
            Thread.sleep(30)
            active.decrementAndGet()
            when (item.repo) {
                "repo-1" -> check(
                    status = GitHubTrackedReleaseStatus.UpdateAvailable,
                    hasUpdate = true
                )

                "repo-2" -> check(
                    status = GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable,
                    hasUpdate = true,
                    hasPreReleaseUpdate = true
                )

                "repo-3" -> check(status = GitHubTrackedReleaseStatus.Failed)
                else -> check(status = GitHubTrackedReleaseStatus.UpToDate, hasUpdate = false)
            }
        }

        assertTrue(maxActive.get() <= 2)
        assertEquals(items.size, result.totalCount)
        assertEquals(items.size, result.cacheEntries.size)
        assertEquals(NOW_MS, result.refreshTimestampMs)
        assertEquals(2, result.updatableCount)
        assertEquals(1, result.preReleaseUpdateCount)
        assertEquals(1, result.failedCount)
        assertTrue(result.performance.elapsedMs > 0L)
        assertTrue(result.performance.p95ItemMs >= result.performance.p50ItemMs)
        assertTrue(result.hasNotifiableOutcome)
    }

    @Test
    fun `run converts thrown evaluator failures into failed cache entries`() = runBlocking {
        val item = tracked(1)

        val result = GitHubTrackedRefreshBatchRunner.run(
            trackedItems = listOf(item),
            maxConcurrency = 1,
            dispatcher = Dispatchers.Default,
            refreshTimestampMs = NOW_MS
        ) {
            error("network unavailable")
        }

        val entry = result.cacheEntries.getValue(item.id)
        assertEquals(1, result.failedCount)
        assertTrue(entry.message.contains("network unavailable"))
        assertTrue(result.hasNotifiableOutcome)
    }

    @Test
    fun `run exposes performance evidence for 30 and 100 item fixtures`() = runBlocking {
        listOf(30, 100).forEach { count ->
            val result = GitHubTrackedRefreshBatchRunner.run(
                trackedItems = (1..count).map { index -> tracked(index) },
                maxConcurrency = 8,
                dispatcher = Dispatchers.Default,
                refreshTimestampMs = NOW_MS
            ) { item ->
                Thread.sleep((item.repo.removePrefix("repo-").toInt() % 3 + 5).toLong())
                check(
                    status = when {
                        item.repo.endsWith("7") -> GitHubTrackedReleaseStatus.Failed
                        item.repo.endsWith("3") -> GitHubTrackedReleaseStatus.PreReleaseUpdateAvailable
                        item.repo.endsWith("1") -> GitHubTrackedReleaseStatus.UpdateAvailable
                        else -> GitHubTrackedReleaseStatus.UpToDate
                    },
                    hasUpdate = item.repo.endsWith("1") || item.repo.endsWith("3"),
                    hasPreReleaseUpdate = item.repo.endsWith("3")
                )
            }

            assertEquals(count, result.totalCount)
            assertEquals(count, result.cacheEntries.size)
            assertTrue(result.performance.elapsedMs > 0L)
            assertTrue(result.performance.p50ItemMs > 0L)
            assertTrue(result.performance.p95ItemMs >= result.performance.p50ItemMs)
            assertTrue(result.performance.maxItemMs >= result.performance.p95ItemMs)
        }
    }

    private fun tracked(index: Int): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://github.com/demo/repo-$index",
            owner = "demo",
            repo = "repo-$index",
            packageName = "demo.repo$index",
            appLabel = "Repo $index"
        )
    }

    private fun check(
        status: GitHubTrackedReleaseStatus,
        hasUpdate: Boolean? = null,
        hasPreReleaseUpdate: Boolean = false
    ): GitHubTrackedReleaseCheck {
        return GitHubTrackedReleaseCheck(
            strategyId = "test",
            localVersion = "1.0",
            hasUpdate = hasUpdate,
            hasPreReleaseUpdate = hasPreReleaseUpdate,
            status = status,
            message = status.defaultMessage
        )
    }

    private companion object {
        private const val NOW_MS = 1_777_392_000_000L
    }
}
