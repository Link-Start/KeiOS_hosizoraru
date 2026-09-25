package os.kei.feature.github.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import os.kei.core.io.BoundedContentReadLimitStage
import os.kei.core.io.BoundedContentTextReadTooLargeException
import os.kei.feature.github.model.GitHubReleaseCheckDiagnostics
import os.kei.feature.github.model.GitHubTrackedReleaseCheck
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubTrackedSourceMode
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubTrackedRefreshBatchRunnerTest {
    @Test
    fun `all DNS failures are classified as a shared transient network failure`() = runBlocking {
        val items = (1..4).map(::trackedFixture)

        val result =
            GitHubTrackedRefreshBatchRunner.run(
                trackedItems = items,
                maxConcurrency = 2,
                dispatcher = Dispatchers.Default,
                retryPolicy = GitHubTrackedRefreshRetryPolicy.None,
            ) {
                throw UnknownHostException("api.github.com")
            }

        assertEquals(items.size, result.failedCount)
        assertTrue(result.isSharedTransientNetworkFailure)
    }

    @Test
    fun `background network breaker stops assigning remaining items`() = runBlocking {
        val evaluated = AtomicInteger(0)
        val items = (1..12).map(::trackedFixture)

        val result =
            GitHubTrackedRefreshBatchRunner.run(
                trackedItems = items,
                maxConcurrency = 1,
                dispatcher = Dispatchers.Default,
                retryPolicy = GitHubTrackedRefreshRetryPolicy.None,
                transientNetworkFailureAbortThreshold = 3,
            ) {
                evaluated.incrementAndGet()
                throw UnknownHostException("github.com")
            }

        assertEquals(3, evaluated.get())
        assertEquals(items.size, result.failedCount)
        assertTrue(result.transientNetworkAbort)
        assertTrue(result.requiresInfrastructureRetry)
    }

    @Test
    fun `background persistence excludes failed cache entries`() = runBlocking {
        val items = (1..3).map(::trackedFixture)
        val result =
            GitHubTrackedRefreshBatchRunner.run(
                trackedItems = items,
                maxConcurrency = 1,
                dispatcher = Dispatchers.Default,
                retryPolicy = GitHubTrackedRefreshRetryPolicy.None,
            ) { item ->
                if (item == items[1]) {
                    throw UnknownHostException("api.github.com")
                }
                check(status = GitHubTrackedReleaseStatus.UpToDate, hasUpdate = false)
            }

        val persistable = result.backgroundPersistableCacheEntries()

        assertEquals(setOf(items[0].id, items[2].id), persistable.keys)
    }

    @Test
    fun `run checks tracked items with bounded concurrency and aggregates counts`() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val items = (1..6).map { index -> trackedFixture(index) }

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
        assertTrue(result.performance.p50ItemMs > 0L)
        assertTrue(result.performance.p95ItemMs >= result.performance.p50ItemMs)
        assertTrue(result.performance.maxItemMs >= result.performance.p95ItemMs)
        assertEquals(2, result.performance.maxConcurrency)
        assertEquals(6, result.performance.repositoryItemCount)
        assertEquals(0, result.performance.directApkItemCount)
        assertEquals(0, result.performance.fdroidItemCount)
        assertTrue(result.hasNotifiableOutcome)
    }

    @Test
    fun `run converts thrown evaluator failures into failed cache entries`() = runBlocking {
        val item = trackedFixture(1)

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
    fun `run records structured oversized response diagnostics`() = runBlocking {
        val item = trackedFixture(1)

        val result = GitHubTrackedRefreshBatchRunner.run(
            trackedItems = listOf(item),
            maxConcurrency = 1,
            dispatcher = Dispatchers.Default,
            refreshTimestampMs = NOW_MS,
            retryPolicy = GitHubTrackedRefreshRetryPolicy.None,
        ) {
            throw BoundedContentTextReadTooLargeException(
                maxBytes = 512L,
                observedBytes = 640L,
                declaredBytes = 640L,
                stage = BoundedContentReadLimitStage.DeclaredLength,
            )
        }

        val diagnostics = result.failures.single().diagnostics
        assertEquals("response_too_large", diagnostics.category)
        assertEquals(item.sourceMode.storageId, diagnostics.responseType)
        assertEquals(512L, diagnostics.limitBytes)
        assertEquals(640L, diagnostics.declaredBytes)
        assertEquals(640L, diagnostics.observedBytes)
        assertEquals("DeclaredLength", diagnostics.limitStage)
    }

    @Test
    fun `run emits progress as each item finishes`() = runBlocking {
        val items = (1..4).map { index -> trackedFixture(index) }
        val progressEvents = Collections.synchronizedList(
            mutableListOf<GitHubTrackedRefreshBatchProgress>()
        )

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

        assertEquals(listOf(1, 2, 3, 4), progressEvents.map { it.current }.sorted())
        assertTrue(progressEvents.all { it.total == items.size })
        val finalProgress = progressEvents.maxBy { progress -> progress.current }
        assertEquals(result.updatableCount, finalProgress.updatableCount)
        assertEquals(result.preReleaseUpdateCount, finalProgress.preReleaseUpdateCount)
        assertEquals(result.failedCount, finalProgress.failedCount)
    }

    @Test
    fun `run converts timed out evaluator into failed item and completes batch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val items = (1..3).map { index -> trackedFixture(index) }
        val progressEvents = mutableListOf<GitHubTrackedRefreshBatchProgress>()

        val result = GitHubTrackedRefreshBatchRunner.run(
            trackedItems = items,
            maxConcurrency = 2,
            dispatcher = dispatcher,
            refreshTimestampMs = NOW_MS,
            itemTimeoutMs = { 100L },
            onProgress = { progressEvents += it }
        ) { item ->
            if (item.repo == "repo-1") {
                delay(Long.MAX_VALUE)
            }
            check(status = GitHubTrackedReleaseStatus.UpToDate, hasUpdate = false)
        }

        assertEquals(items.size, result.totalCount)
        assertEquals(items.size, result.cacheEntries.size)
        assertEquals(1, result.failedCount)
        assertEquals("demo/repo-1|demo.repo1", result.failures.single().trackId)
        assertTrue(result.failures.single().message.contains("Timed out"))
        assertEquals("timeout", result.failures.single().diagnostics.category)
        assertEquals(listOf(1, 2, 3), progressEvents.map { it.current }.sorted())
    }

    @Test
    fun `run retries transient timed out item and keeps completed batch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val item = trackedFixture(1)
        val attempts = AtomicInteger(0)

        val result = GitHubTrackedRefreshBatchRunner.run(
            trackedItems = listOf(item),
            maxConcurrency = 1,
            dispatcher = dispatcher,
            refreshTimestampMs = NOW_MS,
            itemTimeoutMs = { 100L },
            retryPolicy = GitHubTrackedRefreshRetryPolicy(maxAttempts = 2, retryDelayMs = 10L),
        ) {
            if (attempts.incrementAndGet() == 1) {
                delay(Long.MAX_VALUE)
            }
            check(status = GitHubTrackedReleaseStatus.UpToDate, hasUpdate = false)
        }

        assertEquals(2, attempts.get())
        assertEquals(1, result.totalCount)
        assertEquals(0, result.failedCount)
        assertEquals(1, result.cacheEntries.size)
    }

    @Test
    fun `run stops batch after timeout and returns failed unresolved items`() = runBlocking {
        val items = (1..5).map { index -> trackedFixture(index) }
        val started = Collections.synchronizedList(mutableListOf<String>())
        val itemResults = Collections.synchronizedList(mutableListOf<String>())
        val progressEvents = Collections.synchronizedList(
            mutableListOf<GitHubTrackedRefreshBatchProgress>()
        )

        val result = GitHubTrackedRefreshBatchRunner.run(
            trackedItems = items,
            maxConcurrency = 1,
            dispatcher = Dispatchers.Default,
            refreshTimestampMs = NOW_MS,
            itemTimeoutMs = { 1_000L },
            batchTimeoutMs = 10L,
            retryPolicy = GitHubTrackedRefreshRetryPolicy(maxAttempts = 1),
            onItemResult = { item, _, _ -> itemResults += item.repo },
            onProgress = { progress -> progressEvents += progress },
        ) { item ->
            started += item.repo
            Thread.sleep(30L)
            check(status = GitHubTrackedReleaseStatus.UpToDate, hasUpdate = false)
        }

        assertEquals(listOf("repo-1"), started.toList())
        assertEquals(items.size, result.totalCount)
        assertEquals(items.size, result.cacheEntries.size)
        assertEquals(5, result.failedCount)
        assertEquals(listOf("repo-1", "repo-2", "repo-3", "repo-4", "repo-5"), itemResults.toList())
        assertEquals(listOf(1, 2, 3, 4, 5), progressEvents.map { progress -> progress.current })
        assertEquals(5, progressEvents.last().failedCount)
        assertTrue(result.failures.all { failure -> failure.message.contains("Batch timed out") })
    }

    @Test
    fun `run keeps non retryable failure message without attempt suffix`() = runBlocking {
        val item = trackedFixture(1)

        val result = GitHubTrackedRefreshBatchRunner.run(
            trackedItems = listOf(item),
            maxConcurrency = 1,
            dispatcher = Dispatchers.Default,
            refreshTimestampMs = NOW_MS,
            retryPolicy = GitHubTrackedRefreshRetryPolicy(maxAttempts = 2, retryDelayMs = 1L),
        ) {
            check(
                status = GitHubTrackedReleaseStatus.Failed,
                message = GitHubTrackedReleaseStatus.Failed.failureMessage("HTTP 404 not found"),
            )
        }

        assertEquals(1, result.failedCount)
        assertEquals(
            GitHubTrackedReleaseStatus.Failed.failureMessage("HTTP 404 not found"),
            result.failures.single().message,
        )
    }

    @Test
    fun `run records source mix concurrency and slow item evidence`() = runBlocking {
        val items =
            listOf(
                trackedFixture(1, sourceMode = GitHubTrackedSourceMode.GitHubRepository),
                trackedFixture(2, sourceMode = GitHubTrackedSourceMode.GitRepository),
                trackedFixture(3, sourceMode = GitHubTrackedSourceMode.DirectApk),
                trackedFixture(4, sourceMode = GitHubTrackedSourceMode.FdroidRepository),
                trackedFixture(5, sourceMode = GitHubTrackedSourceMode.DirectApk),
                trackedFixture(6, sourceMode = GitHubTrackedSourceMode.FdroidRepository),
            )

        val result =
            GitHubTrackedRefreshBatchRunner.run(
                trackedItems = items,
                maxConcurrency = 3,
                dispatcher = Dispatchers.Default,
                refreshTimestampMs = NOW_MS,
            ) { item ->
                Thread.sleep(item.repo.removePrefix("repo-").toLong() * 8L)
                check(status = GitHubTrackedReleaseStatus.UpToDate, hasUpdate = false)
            }

        assertEquals(3, result.performance.maxConcurrency)
        assertEquals(2, result.performance.directApkConcurrency)
        assertEquals(2, result.performance.fdroidConcurrency)
        assertEquals(2, result.performance.repositoryItemCount)
        assertEquals(2, result.performance.directApkItemCount)
        assertEquals(2, result.performance.fdroidItemCount)
        assertEquals(0, result.performance.otherItemCount)
        assertEquals(5, result.performance.slowItems.size)
        assertTrue(
            result.performance.slowItems.zipWithNext()
                .all { (left, right) -> left.elapsedMs >= right.elapsedMs }
        )
        assertTrue(result.performance.slowItems.any { it.sourceMode == "fdroid_repository" })
        assertTrue(result.performance.slowItems.all { it.message.isNotBlank() })
        assertTrue(result.performance.slowItems.all { it.unclassifiedElapsedMs == it.elapsedMs })
    }

    @Test
    fun `run keeps stage diagnostics for slow refresh items`() = runBlocking {
        val result =
            GitHubTrackedRefreshBatchRunner.run(
                trackedItems = listOf(trackedFixture(1), trackedFixture(2)),
                maxConcurrency = 2,
                dispatcher = Dispatchers.Default,
                refreshTimestampMs = NOW_MS,
            ) { item ->
                Thread.sleep(item.repo.removePrefix("repo-").toLong() * 5L)
                check(
                    status = GitHubTrackedReleaseStatus.UpToDate,
                    hasUpdate = false,
                    diagnostics =
                        GitHubReleaseCheckDiagnostics(
                            snapshotElapsedMs = 120L,
                            snapshotFromCache = item.repo.endsWith("1"),
                            profileElapsedMs = 40L,
                            profileFromCache = true,
                            preciseApkElapsedMs = 8L,
                            preciseApkRequested = true,
                            fallbackStrategyId = "github_api_token",
                        ),
                )
            }

        val slowItem = result.performance.slowItems.first()
        assertEquals("test", slowItem.strategyId)
        assertEquals(120L, slowItem.snapshotElapsedMs)
        assertEquals(40L, slowItem.profileElapsedMs)
        assertEquals(8L, slowItem.preciseApkElapsedMs)
        assertEquals(0L, slowItem.unclassifiedElapsedMs)
        assertTrue(slowItem.profileFromCache)
        assertTrue(slowItem.preciseApkRequested)
        assertEquals("github_api_token", slowItem.fallbackStrategyId)
    }

    @Test
    fun `fair refresh order interleaves source kinds`() {
        val github = GitHubTrackedSourceMode.GitHubRepository
        val git = GitHubTrackedSourceMode.GitRepository
        val direct = GitHubTrackedSourceMode.DirectApk
        val fdroid = GitHubTrackedSourceMode.FdroidRepository
        // (case, source mode of demo.repo1..N in input order, expected refresh order)
        val cases = listOf(
            Triple(
                "github and direct apk",
                listOf(direct, direct, github, direct, github),
                listOf("demo.repo3", "demo.repo1", "demo.repo5", "demo.repo2", "demo.repo4")
            ),
            Triple(
                "git repository counts as repository work",
                listOf(direct, git, github, direct),
                listOf("demo.repo2", "demo.repo1", "demo.repo3", "demo.repo4")
            ),
            Triple(
                "github, direct apk and fdroid",
                listOf(direct, fdroid, github, direct, fdroid, github),
                listOf("demo.repo3", "demo.repo1", "demo.repo2", "demo.repo6", "demo.repo4", "demo.repo5")
            )
        )

        cases.forEach { (case, sourceModes, expected) ->
            val items = sourceModes.mapIndexed { index, mode -> trackedFixture(index + 1, sourceMode = mode) }

            val order = GitHubTrackedRefreshBatchScheduler
                .buildFairRefreshOrder(items)
                .map { it.item.packageName }

            assertEquals(expected, order, case)
        }
    }

    @Test
    fun `per-source concurrency cap holds in mixed batches`() = runBlocking {
        listOf(GitHubTrackedSourceMode.DirectApk, GitHubTrackedSourceMode.FdroidRepository).forEach { limited ->
            val limitedActive = AtomicInteger(0)
            val maxLimitedActive = AtomicInteger(0)
            val items = (1..8).map { index ->
                trackedFixture(
                    index = index,
                    sourceMode = if (index % 2 == 0) limited else GitHubTrackedSourceMode.GitHubRepository
                )
            }

            GitHubTrackedRefreshBatchRunner.run(
                trackedItems = items,
                maxConcurrency = 4,
                dispatcher = Dispatchers.Default,
                refreshTimestampMs = NOW_MS
            ) { item ->
                if (item.sourceMode == limited) {
                    val current = limitedActive.incrementAndGet()
                    maxLimitedActive.updateAndGet { old -> maxOf(old, current) }
                    Thread.sleep(30)
                    limitedActive.decrementAndGet()
                } else {
                    Thread.sleep(5)
                }
                check(status = GitHubTrackedReleaseStatus.UpToDate, hasUpdate = false)
            }

            assertTrue(maxLimitedActive.get() <= 2, "$limited ran ${maxLimitedActive.get()} at once")
        }
    }

    private fun check(
        status: GitHubTrackedReleaseStatus,
        hasUpdate: Boolean? = null,
        hasPreReleaseUpdate: Boolean = false,
        diagnostics: GitHubReleaseCheckDiagnostics = GitHubReleaseCheckDiagnostics(),
        message: String = status.defaultMessage,
    ): GitHubTrackedReleaseCheck {
        return GitHubTrackedReleaseCheck(
            strategyId = "test",
            localVersion = "1.0",
            hasUpdate = hasUpdate,
            hasPreReleaseUpdate = hasPreReleaseUpdate,
            status = status,
            message = message,
            diagnostics = diagnostics
        )
    }

    private companion object {
        private const val NOW_MS = 1_777_392_000_000L
    }
}
