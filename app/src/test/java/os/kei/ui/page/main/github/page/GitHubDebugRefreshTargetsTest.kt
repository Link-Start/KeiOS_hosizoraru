package os.kei.ui.page.main.github.page

import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import kotlin.test.assertEquals

class GitHubDebugRefreshTargetsTest {
    @Test
    fun `visible debug refresh keeps visible order filters blanks and applies default limit`() {
        val alpha = tracked("alpha")
        val beta = tracked("beta")
        val gamma = tracked("gamma")
        val delta = tracked("delta")
        val epsilon = tracked("epsilon")

        val selected =
            selectGitHubDebugVisibleRefreshTargets(
                visibleItems = listOf(alpha, beta, beta, blankPackageTrack(), gamma, delta, epsilon),
            )

        assertEquals(listOf(alpha, beta, gamma, delta), selected)
    }

    @Test
    fun `visible debug refresh returns empty list for zero limit`() {
        val selected =
            selectGitHubDebugVisibleRefreshTargets(
                visibleItems = listOf(tracked("alpha")),
                limit = 0,
            )

        assertEquals(emptyList(), selected)
    }

    private fun tracked(name: String): GitHubTrackedApp =
        GitHubTrackedApp(
            repoUrl = "https://github.com/owner/$name",
            owner = "owner",
            repo = name,
            packageName = "app.$name",
            appLabel = name,
        )

    private fun blankPackageTrack(): GitHubTrackedApp =
        GitHubTrackedApp(
            repoUrl = "https://github.com/owner/blank",
            owner = "owner",
            repo = "blank",
            packageName = " ",
            appLabel = "blank",
        )
}
