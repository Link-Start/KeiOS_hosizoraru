package os.kei.feature.github.domain

import org.junit.Test
import os.kei.core.io.SharedHttpClient
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubTrackedRefreshPlannerTest {
    /**
     * The tiers are a tuning decision and have already moved once; what must not move is the shape.
     * Asserting the relationships rather than the numbers is the difference between a test that
     * describes the contract and one that has to be rewritten every time the contract is honoured.
     */
    @Test
    fun `background refresh concurrency stays below interactive batch concurrency`() {
        listOf(1, 4, 16, 75).forEach { itemCount ->
            val background = GitHubTrackedRefreshBatchScheduler.backgroundRefreshConcurrency(itemCount)
            val interactive = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(itemCount)
            assertTrue(background in 1..interactive, "$background vs $interactive at $itemCount")
        }
        // Strictly below once the batch is big enough that the item count is not what caps them
        // both: nobody is waiting on a background refresh, and it competes with what the user is
        // actually doing.
        listOf(16, 75).forEach { itemCount ->
            assertTrue(
                GitHubTrackedRefreshBatchScheduler.backgroundRefreshConcurrency(itemCount) <
                    GitHubTrackedRefreshBatchScheduler.refreshConcurrency(itemCount),
                "background must yield to interactive at $itemCount",
            )
        }
    }

    @Test
    fun `interactive refresh concurrency still scales for large user requested batches`() {
        val small = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(4)
        val medium = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(16)
        val large = GitHubTrackedRefreshBatchScheduler.refreshConcurrency(75)

        assertEquals(1, GitHubTrackedRefreshBatchScheduler.refreshConcurrency(1), "a batch of one needs one worker")
        assertEquals(4, small, "never more workers than there are items to work on")
        assertTrue(medium > small && large > medium, "$small -> $medium -> $large")
    }

    /**
     * These count items, not requests, and the requests are what the host sees. Asking for more
     * items in flight than the shared client will run calls for buys nothing — the excess queues in
     * OkHttp — so the top tier stays inside that budget rather than pretending to exceed it.
     */
    @Test
    fun `no tier asks for more in flight than the shared call budget allows`() {
        listOf(1, 4, 16, 48, 75, 500).forEach { itemCount ->
            assertTrue(
                GitHubTrackedRefreshBatchScheduler.refreshConcurrency(itemCount) <=
                    SharedHttpClient.MAX_CONCURRENT_CALLS_PER_HOST,
                "interactive tier overshoots the per-host budget at $itemCount",
            )
        }
    }

    @Test
    fun `partial missing check states skip automatic refresh for ignored version tracks`() {
        val cached = trackedFixture(0)
        val active = trackedFixture(1)
        val temporaryIgnored = trackedFixture(2).copy(
            ignoreMode = GitHubTrackedIgnoreMode.Temporary
        )
        val allVersionsIgnored = trackedFixture(3).copy(
            ignoreMode = GitHubTrackedIgnoreMode.AllVersions
        )

        val selected = GitHubTrackedRefreshPlanner.selectPartialMissingCheckStateItems(
            trackedItems = listOf(cached, active, temporaryIgnored, allVersionsIgnored),
            cachedTrackIds = setOf(cached.id)
        )

        assertEquals(listOf(active.id), selected.map { it.id })
    }

    @Test
    fun `all missing check states are left to full refresh path`() {
        val github = trackedFixture(1)
        val direct = trackedFixture(2, sourceMode = GitHubTrackedSourceMode.DirectApk)

        val selected = GitHubTrackedRefreshPlanner.selectPartialMissingCheckStateItems(
            trackedItems = listOf(github, direct),
            cachedTrackIds = emptySet()
        )

        assertEquals(emptyList(), selected)
    }

    @Test
    fun `partial missing check states keep fair source ordering`() {
        val cached = trackedFixture(0)
        val directOne = trackedFixture(1, sourceMode = GitHubTrackedSourceMode.DirectApk)
        val githubTwo = trackedFixture(2)
        val directThree = trackedFixture(3, sourceMode = GitHubTrackedSourceMode.DirectApk)

        val selected = GitHubTrackedRefreshPlanner.selectPartialMissingCheckStateItems(
            trackedItems = listOf(cached, directOne, githubTwo, directThree),
            cachedTrackIds = setOf(cached.id)
        )

        assertEquals(
            listOf(githubTwo.id, directOne.id, directThree.id),
            selected.map { it.id }
        )
    }
}
