package os.kei.ui.page.main.github.page.action

import os.kei.feature.github.model.GitHubTrackedApp
import org.junit.Test
import kotlin.test.assertEquals

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
    fun `visible refresh returns empty list when no active targets exist`() {
        val selected = selectActiveTrackedRefreshTargetIds(
            requestedTrackIds = listOf("one", "two"),
            validTrackIds = emptySet()
        )

        assertEquals(emptyList(), selected)
    }
}
