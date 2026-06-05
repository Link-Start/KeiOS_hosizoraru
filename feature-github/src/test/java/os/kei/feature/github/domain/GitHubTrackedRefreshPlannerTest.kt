package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import kotlin.test.assertEquals

class GitHubTrackedRefreshPlannerTest {
    @Test
    fun `background refresh concurrency stays below interactive batch concurrency`() {
        assertEquals(2, GitHubTrackedRefreshBatchScheduler.backgroundRefreshConcurrency(4))
        assertEquals(4, GitHubTrackedRefreshBatchScheduler.backgroundRefreshConcurrency(16))
        assertEquals(6, GitHubTrackedRefreshBatchScheduler.backgroundRefreshConcurrency(75))
    }

    @Test
    fun `interactive refresh concurrency still scales for large user requested batches`() {
        assertEquals(4, GitHubTrackedRefreshBatchScheduler.refreshConcurrency(4))
        assertEquals(6, GitHubTrackedRefreshBatchScheduler.refreshConcurrency(16))
        assertEquals(8, GitHubTrackedRefreshBatchScheduler.refreshConcurrency(75))
    }

    @Test
    fun `partial missing check states refresh only missing items`() {
        val github = tracked(1)
        val direct = tracked(2, sourceMode = GitHubTrackedSourceMode.DirectApk)

        val selected = GitHubTrackedRefreshPlanner.selectPartialMissingCheckStateItems(
            trackedItems = listOf(github, direct),
            cachedTrackIds = setOf(github.id)
        )

        assertEquals(listOf(direct.id), selected.map { it.id })
    }

    @Test
    fun `partial missing check states skip automatic refresh for ignored version tracks`() {
        val cached = tracked(0)
        val active = tracked(1)
        val temporaryIgnored = tracked(2).copy(
            ignoreMode = GitHubTrackedIgnoreMode.Temporary
        )
        val allVersionsIgnored = tracked(3).copy(
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
        val github = tracked(1)
        val direct = tracked(2, sourceMode = GitHubTrackedSourceMode.DirectApk)

        val selected = GitHubTrackedRefreshPlanner.selectPartialMissingCheckStateItems(
            trackedItems = listOf(github, direct),
            cachedTrackIds = emptySet()
        )

        assertEquals(emptyList(), selected)
    }

    @Test
    fun `partial missing check states keep fair source ordering`() {
        val cached = tracked(0)
        val directOne = tracked(1, sourceMode = GitHubTrackedSourceMode.DirectApk)
        val githubTwo = tracked(2)
        val directThree = tracked(3, sourceMode = GitHubTrackedSourceMode.DirectApk)

        val selected = GitHubTrackedRefreshPlanner.selectPartialMissingCheckStateItems(
            trackedItems = listOf(cached, directOne, githubTwo, directThree),
            cachedTrackIds = setOf(cached.id)
        )

        assertEquals(
            listOf(githubTwo.id, directOne.id, directThree.id),
            selected.map { it.id }
        )
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
}
