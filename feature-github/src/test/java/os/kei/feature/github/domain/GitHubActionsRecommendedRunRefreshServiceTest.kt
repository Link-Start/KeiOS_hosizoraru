package os.kei.feature.github.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubActionsRecommendedRunRefreshServiceTest {
    @Test
    fun `refresh filters disabled actions tracks`() = runBlocking {
        val enabled = trackedApp("enabled")
        val disabled = trackedApp("disabled", checkActionsUpdates = false)
        val source = FakeRecommendedRunRefreshSource().apply {
            responses[enabled.id] = Result.success(snapshot(enabled, runId = 10L))
            responses[disabled.id] = Result.success(snapshot(disabled, runId = 20L))
        }

        val result = service(source).refreshItems(
            items = listOf(enabled, disabled),
            lookupConfig = GitHubLookupConfig(),
            maxConcurrency = 2,
        )

        assertEquals(1, result.checkedCount)
        assertEquals(1, result.succeededCount)
        assertEquals(listOf(enabled.id), source.fetchRequests.map { it.trackId })
        assertEquals(snapshot(enabled, runId = 10L), source.loadRecommendedRunSnapshot(enabled.id))
        assertNull(source.loadRecommendedRunSnapshot(disabled.id))
    }

    @Test
    fun `refresh saves current snapshot and reports newer run`() = runBlocking {
        val item = trackedApp("demo")
        val previous = snapshot(item, runId = 100L, runNumber = 10L)
        val current = snapshot(item, runId = 101L, runNumber = 11L)
        val source = FakeRecommendedRunRefreshSource().apply {
            snapshots[item.id] = previous
            responses[item.id] = Result.success(current)
        }

        val result = service(source).refreshItems(
            items = listOf(item),
            lookupConfig = GitHubLookupConfig(),
            maxConcurrency = 1,
        )
        val outcome = result.outcomes.single()

        assertEquals(1, result.checkedCount)
        assertEquals(1, result.succeededCount)
        assertEquals(0, result.failedCount)
        assertEquals(previous, outcome.previous)
        assertEquals(current, outcome.current)
        assertTrue(outcome.newerThanPrevious)
        assertEquals(listOf(current), result.newerSnapshots)
        assertEquals(current, source.loadRecommendedRunSnapshot(item.id))
        assertEquals(previous.workflowId, source.fetchRequests.single().previousWorkflowId)
    }

    @Test
    fun `refresh failure keeps previous snapshot in outcome`() = runBlocking {
        val item = trackedApp("failed")
        val previous = snapshot(item, runId = 30L, runNumber = 3L)
        val source = FakeRecommendedRunRefreshSource().apply {
            snapshots[item.id] = previous
            responses[item.id] = Result.failure(IllegalStateException("network down"))
        }

        val result = service(source).refreshItems(
            items = listOf(item),
            lookupConfig = GitHubLookupConfig(),
            maxConcurrency = 1,
        )
        val outcome = result.outcomes.single()

        assertEquals(1, result.checkedCount)
        assertEquals(0, result.succeededCount)
        assertEquals(1, result.failedCount)
        assertFalse(outcome.succeeded)
        assertEquals(previous, outcome.previous)
        assertNull(outcome.current)
        assertEquals("network down", outcome.errorMessage)
        assertEquals(previous, source.loadRecommendedRunSnapshot(item.id))
        assertTrue(result.newerSnapshots.isEmpty())
    }

    @Test
    fun `refresh retains cached snapshots when no target is checked`() = runBlocking {
        val kept = trackedApp("kept")
        val removed = trackedApp("removed")
        val source = FakeRecommendedRunRefreshSource().apply {
            snapshots[kept.id] = snapshot(kept, runId = 1L)
            snapshots[removed.id] = snapshot(removed, runId = 2L)
        }

        val result = service(source).refreshItems(
            items = emptyList(),
            lookupConfig = GitHubLookupConfig(),
            retainTrackIds = setOf(kept.id),
        )

        assertEquals(0, result.checkedCount)
        assertEquals(setOf(kept.id), source.retainedTrackIds.single())
        assertEquals(snapshot(kept, runId = 1L), source.loadRecommendedRunSnapshot(kept.id))
        assertNull(source.loadRecommendedRunSnapshot(removed.id))
    }

    private fun service(
        source: GitHubActionsRecommendedRunRefreshSource,
    ): GitHubActionsRecommendedRunRefreshService =
        GitHubActionsRecommendedRunRefreshService(
            source = source,
            networkDispatcher = Dispatchers.Default,
            localDispatcher = Dispatchers.Default,
        )

    private class FakeRecommendedRunRefreshSource : GitHubActionsRecommendedRunRefreshSource {
        val snapshots = ConcurrentHashMap<String, GitHubActionsRecommendedRunSnapshot>()
        val responses = ConcurrentHashMap<String, Result<GitHubActionsRecommendedRunSnapshot>>()
        val fetchRequests = Collections.synchronizedList(mutableListOf<FetchRequest>())
        val retainedTrackIds = Collections.synchronizedList(mutableListOf<Set<String>>())

        override fun loadRecommendedRunSnapshot(trackId: String): GitHubActionsRecommendedRunSnapshot? =
            snapshots[trackId]

        override fun loadRecommendedRunSnapshots(): Map<String, GitHubActionsRecommendedRunSnapshot> =
            snapshots.toMap()

        override suspend fun fetchRecommendedRunSnapshot(
            item: GitHubTrackedApp,
            lookupConfig: GitHubLookupConfig,
            previousWorkflowId: Long?,
            nowMs: Long,
        ): Result<GitHubActionsRecommendedRunSnapshot> {
            fetchRequests += FetchRequest(
                trackId = item.id,
                previousWorkflowId = previousWorkflowId,
                nowMs = nowMs,
            )
            return responses[item.id]
                ?: Result.failure(IllegalStateException("missing fake response"))
        }

        override fun saveRecommendedRunSnapshot(snapshot: GitHubActionsRecommendedRunSnapshot) {
            snapshots[snapshot.trackId] = snapshot
        }

        override fun removeRecommendedRunSnapshot(trackId: String) {
            snapshots.remove(trackId)
        }

        override fun retainRecommendedRunSnapshots(trackIds: Set<String>) {
            retainedTrackIds += trackIds
            snapshots.keys.removeIf { key -> key !in trackIds }
        }
    }

    private data class FetchRequest(
        val trackId: String,
        val previousWorkflowId: Long?,
        val nowMs: Long,
    )

    private fun trackedApp(
        name: String,
        checkActionsUpdates: Boolean = true,
    ): GitHubTrackedApp =
        GitHubTrackedApp(
            repoUrl = "https://github.com/acme/$name",
            owner = "acme",
            repo = name,
            packageName = "com.acme.$name",
            appLabel = name,
            checkActionsUpdates = checkActionsUpdates,
        )

    private fun snapshot(
        item: GitHubTrackedApp,
        runId: Long,
        runNumber: Long = runId,
    ): GitHubActionsRecommendedRunSnapshot =
        GitHubActionsRecommendedRunSnapshot(
            trackId = item.id,
            owner = item.owner,
            repo = item.repo,
            appLabel = item.appLabel,
            workflowId = 42L,
            workflowName = "Build",
            workflowPath = ".github/workflows/build.yml",
            runId = runId,
            runNumber = runNumber,
            runAttempt = 1,
            runDisplayName = "Build",
            headBranch = "main",
            headSha = "sha$runId",
            event = "push",
            status = "completed",
            conclusion = "success",
            htmlUrl = "https://github.com/${item.owner}/${item.repo}/actions/runs/$runId",
            artifactCount = 2,
            androidArtifactCount = 1,
            createdAtMillis = runId,
            updatedAtMillis = runId,
            checkedAtMillis = runId,
        )
}
