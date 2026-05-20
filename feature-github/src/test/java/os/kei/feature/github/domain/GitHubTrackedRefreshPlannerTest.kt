package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import kotlin.test.assertEquals

class GitHubTrackedRefreshPlannerTest {
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
