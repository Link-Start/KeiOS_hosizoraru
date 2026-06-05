package os.kei.ui.page.main.github.page.action

import os.kei.feature.github.domain.GitHubRefreshScope
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubRefreshActionsTest {
    @Test
    fun `tracked update selector respects twelve hour global interval`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "com.example.app",
            appLabel = "Example"
        )
        val nowMs = 12L * 60L * 60L * 1000L
        val selected = selectDueTrackedUpdateItems(
            trackedItems = listOf(item),
            checkedAtMillisById = mapOf(item.id to 1L),
            lastRefreshMs = 0L,
            refreshIntervalHours = 12,
            nowMs = nowMs,
        )

        assertEquals(emptyList(), selected)
    }

    @Test
    fun `tracked update selector refreshes after twelve hour global interval`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "com.example.app",
            appLabel = "Example"
        )
        val nowMs = 12L * 60L * 60L * 1000L + 1L
        val selected = selectDueTrackedUpdateItems(
            trackedItems = listOf(item),
            checkedAtMillisById = mapOf(item.id to 1L),
            lastRefreshMs = 0L,
            refreshIntervalHours = 12,
            nowMs = nowMs,
        )

        assertEquals(listOf(item), selected)
    }

    @Test
    fun `tracked update selector skips automatic refresh for ignored version tracks`() {
        val active = tracked("active")
        val temporaryIgnored = tracked("temporary").copy(
            ignoreMode = GitHubTrackedIgnoreMode.Temporary
        )
        val allVersionsIgnored = tracked("all").copy(
            ignoreMode = GitHubTrackedIgnoreMode.AllVersions
        )
        val nowMs = 12L * 60L * 60L * 1000L + 1L

        val selected = selectDueTrackedUpdateItems(
            trackedItems = listOf(active, temporaryIgnored, allVersionsIgnored),
            checkedAtMillisById = mapOf(
                active.id to 1L,
                temporaryIgnored.id to 1L,
                allVersionsIgnored.id to 1L,
            ),
            lastRefreshMs = 0L,
            refreshIntervalHours = 12,
            nowMs = nowMs,
        )

        assertEquals(listOf(active), selected)
    }

    @Test
    fun `track mutation refresh selects all affected ids within limit`() {
        val selected = selectImmediateTrackMutationRefreshIds(
            affectedTrackIds = setOf("one", "two", "three"),
            validTrackIds = setOf("one", "two", "three", "four")
        )

        assertEquals(listOf("one", "two", "three"), selected)
    }

    @Test
    fun `track mutation refresh limits large imports to first affected ids`() {
        val affected = (1..12).map { "track-$it" }.toCollection(LinkedHashSet())

        val selected = selectImmediateTrackMutationRefreshIds(
            affectedTrackIds = affected,
            validTrackIds = affected
        )

        assertEquals((1..8).map { "track-$it" }, selected)
    }

    @Test
    fun `track mutation refresh ignores invalid and blank ids`() {
        val selected = selectImmediateTrackMutationRefreshIds(
            affectedTrackIds = linkedSetOf(" one ", "", "removed", "two", "one"),
            validTrackIds = setOf("one", "two"),
            limit = 8
        )

        assertEquals(listOf("one", "two"), selected)
    }

    @Test
    fun `track mutation refresh respects explicit zero limit`() {
        val selected = selectImmediateTrackMutationRefreshIds(
            affectedTrackIds = setOf("one"),
            validTrackIds = setOf("one"),
            limit = 0
        )

        assertEquals(emptyList(), selected)
    }

    @Test
    fun `visible refresh keeps requested order and removes inactive ids`() {
        val selected = selectActiveTrackedRefreshTargetIds(
            requestedTrackIds = listOf("beta", "missing", "alpha", "beta", " gamma "),
            validTrackIds = setOf("alpha", "beta", "gamma", "delta")
        )

        assertEquals(listOf("beta", "alpha", "gamma"), selected)
    }

    @Test
    fun `visible refresh target selector resolves ids against active tracked items`() {
        val alpha = tracked("alpha")
        val beta = tracked("beta")
        val selected = selectActiveTrackedRefreshTargets(
            requestedTrackIds = listOf(beta.id, "missing", alpha.id, beta.id),
            activeItems = listOf(alpha, beta),
        )

        assertEquals(listOf(beta, alpha), selected)
    }

    @Test
    fun `visible refresh plan promotes full active coverage to all tracked`() {
        val alpha = tracked("alpha")
        val beta = tracked("beta")
        val plan = planGitHubTrackedBatchRefresh(
            requestedTargetIds = listOf(beta.id, "missing", alpha.id, beta.id),
            activeItems = listOf(alpha, beta),
            refreshScope = GitHubRefreshScope.VisibleTracked,
            updateGlobalRefreshTimestamp = false,
        )

        assertEquals(listOf(beta, alpha), plan.targets)
        assertEquals(listOf(beta.id, alpha.id), plan.targetIds)
        assertTrue(plan.coversAllActiveItems)
        assertEquals(GitHubRefreshScope.AllTracked, plan.refreshScope)
        assertTrue(plan.updateGlobalRefreshTimestamp)
    }

    @Test
    fun `visible refresh plan keeps partial active coverage scoped`() {
        val alpha = tracked("alpha")
        val beta = tracked("beta")
        val plan = planGitHubTrackedBatchRefresh(
            requestedTargetIds = listOf(alpha.id),
            activeItems = listOf(alpha, beta),
            refreshScope = GitHubRefreshScope.VisibleTracked,
            updateGlobalRefreshTimestamp = false,
        )

        assertEquals(listOf(alpha), plan.targets)
        assertFalse(plan.coversAllActiveItems)
        assertEquals(GitHubRefreshScope.VisibleTracked, plan.refreshScope)
        assertFalse(plan.updateGlobalRefreshTimestamp)
    }

    @Test
    fun `visible refresh returns empty list when no active targets exist`() {
        val selected = selectActiveTrackedRefreshTargetIds(
            requestedTrackIds = listOf("one", "two"),
            validTrackIds = emptySet()
        )

        assertEquals(emptyList(), selected)
    }

    private fun tracked(name: String): GitHubTrackedApp =
        GitHubTrackedApp(
            repoUrl = "https://github.com/owner/$name",
            owner = "owner",
            repo = name,
            packageName = "com.example.$name",
            appLabel = name,
        )
}
