package os.kei.ui.page.main.github.page

import org.junit.Test
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import kotlin.test.assertEquals

class GitHubPageUiStateStoreTest {
    @Test
    fun `decode page sort mode accepts persisted storage ids`() {
        assertEquals(GitHubSortMode.Changed, decodeGitHubSortMode("changed"))
        assertEquals(GitHubSortMode.Update, decodeGitHubSortMode("unknown"))
    }

    @Test
    fun `decode page sort direction accepts persisted storage ids`() {
        assertEquals(GitHubSortDirection.Reverse, decodeGitHubSortDirection("reverse"))
        assertEquals(GitHubSortDirection.Forward, decodeGitHubSortDirection("unknown"))
    }

    @Test
    fun `decode page filter mode accepts persisted storage ids`() {
        assertEquals(
            GitHubTrackedFilterMode.ActionsCheckEnabled,
            decodeGitHubTrackedFilterMode("actions_check_enabled")
        )
        assertEquals(GitHubTrackedFilterMode.All, decodeGitHubTrackedFilterMode("unknown"))
    }
}
