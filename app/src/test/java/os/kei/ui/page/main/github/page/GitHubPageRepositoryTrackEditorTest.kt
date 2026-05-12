package os.kei.ui.page.main.github.page

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GitHubPageRepositoryTrackEditorTest {
    private val repository = GitHubPageRepository()

    @Test
    fun `github source rejects non github url`() = runBlocking {
        val result = repository.buildTrackedItem(
            GitHubTrackEditorDraft(
                sourceMode = GitHubTrackedSourceMode.GitHubRepository,
                repoUrl = "https://telegram.org/dl/android/apk",
                packageName = "org.telegram.messenger",
                preferPreRelease = false,
                alwaysShowLatestReleaseDownloadButton = false,
                checkActionsUpdates = false,
                preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.FollowGlobal,
                appList = emptyList()
            )
        )

        assertEquals(GitHubTrackEditorResult.InvalidRepository, result)
    }

    @Test
    fun `direct apk source builds direct track and closes github options`() = runBlocking {
        val result = repository.buildTrackedItem(
            GitHubTrackEditorDraft(
                sourceMode = GitHubTrackedSourceMode.DirectApk,
                repoUrl = "https://telegram.org/dl/android/apk-public-beta",
                packageName = "org.telegram.messenger.beta",
                preferPreRelease = true,
                alwaysShowLatestReleaseDownloadButton = true,
                checkActionsUpdates = true,
                preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.FollowGlobal,
                appList = emptyList()
            )
        )

        val item = assertIs<GitHubTrackEditorResult.Ready>(result).item

        assertEquals(GitHubTrackedSourceMode.DirectApk, item.sourceMode)
        assertEquals("telegram.org", item.owner)
        assertEquals("dl-android-apk-public-beta", item.repo)
        assertEquals("org.telegram.messenger.beta", item.packageName)
        assertEquals(false, item.preferPreRelease)
        assertEquals(false, item.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, item.checkActionsUpdates)
    }
}
