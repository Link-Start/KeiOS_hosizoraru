package os.kei.feature.github.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubTrackedSourceMode
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
        assertEquals("demo/repo-3|demo.repo3", result.failures.single().trackId)
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
        assertEquals(item.id, result.failures.single().trackId)
        assertEquals(item.owner, result.failures.single().owner)
        assertEquals(item.repo, result.failures.single().repo)
        assertTrue(result.failures.single().message.contains("network unavailable"))
        assertTrue(entry.message.contains("network unavailable"))
        assertTrue(result.hasNotifiableOutcome)
    }

    @Test
    fun `run emits progress as each item finishes`() = runBlocking {
        val items = (1..4).map { index -> tracked(index) }
        val progressEvents = mutableListOf<GitHubTrackedRefreshBatchProgress>()

        val result = GitHubTrackedRefreshBatchRunner.run(
            trackedItems = items,
            maxConcurrency = 2,
            dispatcher = Dispatchers.Default,
            refreshTimestampMs = NOW_MS,
            onProgress = { progressEvents += it }
        ) { item ->
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

        assertEquals(listOf(1, 2, 3, 4), progressEvents.map { it.current })
        assertTrue(progressEvents.all { it.total == items.size })
        assertEquals(result.updatableCount, progressEvents.last().updatableCount)
        assertEquals(result.preReleaseUpdateCount, progressEvents.last().preReleaseUpdateCount)
        assertEquals(result.failedCount, progressEvents.last().failedCount)
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

    @Test
    fun `scheduler interleaves github and direct apk sources fairly`() {
        val items = listOf(
            tracked(1, sourceMode = GitHubTrackedSourceMode.DirectApk),
            tracked(2, sourceMode = GitHubTrackedSourceMode.DirectApk),
            tracked(3),
            tracked(4, sourceMode = GitHubTrackedSourceMode.DirectApk),
            tracked(5)
        )

        val order = GitHubTrackedRefreshBatchScheduler
            .buildFairRefreshOrder(items)
            .map { it.item.packageName }

        assertEquals(
            listOf("demo.repo3", "demo.repo1", "demo.repo5", "demo.repo2", "demo.repo4"),
            order
        )
    }

    @Test
    fun `scheduler treats git repository sources as repository work`() {
        val items = listOf(
            tracked(1, sourceMode = GitHubTrackedSourceMode.DirectApk),
            tracked(2, sourceMode = GitHubTrackedSourceMode.GitRepository),
            tracked(3),
            tracked(4, sourceMode = GitHubTrackedSourceMode.DirectApk)
        )

        val order = GitHubTrackedRefreshBatchScheduler
            .buildFairRefreshOrder(items)
            .map { it.item.packageName }

        assertEquals(
            listOf("demo.repo2", "demo.repo1", "demo.repo3", "demo.repo4"),
            order
        )
    }

    @Test
    fun `scheduler increases refresh concurrency for larger batches`() {
        assertEquals(1, GitHubTrackedRefreshBatchScheduler.refreshConcurrency(1))
        assertEquals(4, GitHubTrackedRefreshBatchScheduler.refreshConcurrency(8))
        assertEquals(6, GitHubTrackedRefreshBatchScheduler.refreshConcurrency(16))
        assertEquals(8, GitHubTrackedRefreshBatchScheduler.refreshConcurrency(48))
    }

    @Test
    fun `run limits direct apk manifest checks inside mixed refresh batches`() = runBlocking {
        val directActive = AtomicInteger(0)
        val maxDirectActive = AtomicInteger(0)
        val items = (1..8).map { index ->
            tracked(
                index = index,
                sourceMode = if (index % 2 == 0) {
                    GitHubTrackedSourceMode.DirectApk
                } else {
                    GitHubTrackedSourceMode.GitHubRepository
                }
            )
        }

        GitHubTrackedRefreshBatchRunner.run(
            trackedItems = items,
            maxConcurrency = 4,
            dispatcher = Dispatchers.Default,
            refreshTimestampMs = NOW_MS
        ) { item ->
            if (item.sourceMode == GitHubTrackedSourceMode.DirectApk) {
                val current = directActive.incrementAndGet()
                maxDirectActive.updateAndGet { old -> maxOf(old, current) }
                Thread.sleep(30)
                directActive.decrementAndGet()
            } else {
                Thread.sleep(5)
            }
            check(status = GitHubTrackedReleaseStatus.UpToDate, hasUpdate = false)
        }

        assertTrue(maxDirectActive.get() <= 2)
    }

    private fun tracked(
        index: Int,
        sourceMode: GitHubTrackedSourceMode = GitHubTrackedSourceMode.GitHubRepository
    ): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = when (sourceMode) {
                GitHubTrackedSourceMode.GitHubRepository -> "https://github.com/demo/repo-$index"
                GitHubTrackedSourceMode.GitRepository -> "https://gitee.com/demo/repo-$index"
                GitHubTrackedSourceMode.DirectApk -> "https://example.com/download/repo-$index.apk"
            },
            owner = "demo",
            repo = "repo-$index",
            packageName = "demo.repo$index",
            appLabel = "Repo $index",
            sourceMode = sourceMode
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
