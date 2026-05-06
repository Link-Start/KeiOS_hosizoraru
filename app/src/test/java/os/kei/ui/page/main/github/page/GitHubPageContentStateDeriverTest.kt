package os.kei.ui.page.main.github.page

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubPageContentStateDeriverTest {
    @Test
    fun `pending share import card stays visible during linkage window`() = runBlocking {
        val armedAtMillis = 1_000L
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                pendingShareImportTrack = GitHubPendingShareImportTrack(
                    projectUrl = "https://github.com/owner/repo",
                    owner = "owner",
                    repo = "repo",
                    assetName = "demo.apk",
                    armedAtMillis = armedAtMillis
                ),
                nowMillis = armedAtMillis + 24 * 60 * 1000L
            )
        )

        assertTrue(derived.showPendingShareImportCard)
    }

    @Test
    fun `pending share import card hides after linkage window`() = runBlocking {
        val armedAtMillis = 1_000L
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                pendingShareImportTrack = GitHubPendingShareImportTrack(
                    projectUrl = "https://github.com/owner/repo",
                    owner = "owner",
                    repo = "repo",
                    assetName = "demo.apk",
                    armedAtMillis = armedAtMillis
                ),
                nowMillis = armedAtMillis + 26 * 60 * 1000L
            )
        )

        assertFalse(derived.showPendingShareImportCard)
    }

    private fun baseInput(
        pendingShareImportTrack: GitHubPendingShareImportTrack?,
        nowMillis: Long
    ): GitHubPageContentInput {
        return GitHubPageContentInput(
            trackedItems = emptyList(),
            trackedSearch = "",
            showFailedOnly = false,
            sortMode = GitHubSortMode.UpdateFirst,
            checkStates = emptyMap(),
            appList = emptyList(),
            trackedFirstInstallAtByPackage = emptyMap(),
            trackedAddedAtById = emptyMap(),
            pendingShareImportTrack = pendingShareImportTrack,
            nowMillis = nowMillis
        )
    }
}
