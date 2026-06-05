package os.kei.ui.page.main.github.page

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerInput
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerSortDirection
import os.kei.ui.page.main.github.picker.GitHubTrackAppPickerSortMode
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GitHubPageRepositoryTrackEditorTest {
    private val repository = GitHubPageRepository(defaultDispatcher = Dispatchers.Unconfined)

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
                updateIntervalMode = GitHubTrackedUpdateIntervalMode.FollowGlobal,
                actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
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
                updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours6,
                actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15,
                preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.FollowGlobal,
                appList = emptyList()
            )
        )

        val item = assertIs<GitHubTrackEditorResult.Ready>(result).item

        assertEquals(GitHubTrackedSourceMode.DirectApk, item.sourceMode)
        assertEquals("telegram.org", item.owner)
        assertEquals("dl-android-apk-public-beta", item.repo)
        assertEquals("org.telegram.messenger.beta", item.packageName)
        assertEquals(true, item.preferPreRelease)
        assertEquals(false, item.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, item.checkActionsUpdates)
        assertEquals(GitHubTrackedUpdateIntervalMode.Hours6, item.updateIntervalMode)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            item.actionsUpdateIntervalMode
        )
    }

    @Test
    fun `git repository source builds host scoped track and closes github options`() = runBlocking {
        val result = repository.buildTrackedItem(
            GitHubTrackEditorDraft(
                sourceMode = GitHubTrackedSourceMode.GitRepository,
                repoUrl = "git@gitee.com:demo/app.git",
                packageName = "com.demo.app",
                preferPreRelease = true,
                alwaysShowLatestReleaseDownloadButton = true,
                checkActionsUpdates = true,
                updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours3,
                actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15,
                preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Enabled,
                appList = emptyList()
            )
        )

        val item = assertIs<GitHubTrackEditorResult.Ready>(result).item

        assertEquals(GitHubTrackedSourceMode.GitRepository, item.sourceMode)
        assertEquals("gitee.com/demo", item.owner)
        assertEquals("app", item.repo)
        assertEquals(false, item.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, item.checkActionsUpdates)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            item.actionsUpdateIntervalMode
        )
        assertEquals(GitHubTrackedPreciseApkVersionMode.Enabled, item.preciseApkVersionMode)
    }

    @Test
    fun `gitee git repository source builds pronto track with package bridge`() = runBlocking {
        val result = repository.buildTrackedItem(
            GitHubTrackEditorDraft(
                sourceMode = GitHubTrackedSourceMode.GitRepository,
                repoUrl = "https://gitee.com/hugedog233/Pronto",
                packageName = "com.mt.pronto",
                preferPreRelease = false,
                alwaysShowLatestReleaseDownloadButton = true,
                checkActionsUpdates = true,
                updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours6,
                actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes30,
                preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.FollowGlobal,
                appList =
                    listOf(
                        InstalledAppItem(
                            label = "Pronto",
                            packageName = "com.mt.pronto",
                            isSystemApp = false,
                        )
                    )
            )
        )

        val item = assertIs<GitHubTrackEditorResult.Ready>(result).item

        assertEquals(GitHubTrackedSourceMode.GitRepository, item.sourceMode)
        assertEquals("https://gitee.com/hugedog233/Pronto", item.repoUrl)
        assertEquals("gitee.com/hugedog233", item.owner)
        assertEquals("Pronto", item.repo)
        assertEquals("com.mt.pronto", item.packageName)
        assertEquals("Pronto", item.appLabel)
        assertEquals(false, item.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, item.checkActionsUpdates)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            item.actionsUpdateIntervalMode
        )
    }

    @Test
    fun `track editor draft preserves ignore policy`() = runBlocking {
        val result = repository.buildTrackedItem(
            GitHubTrackEditorDraft(
                sourceMode = GitHubTrackedSourceMode.GitHubRepository,
                repoUrl = "https://github.com/demo/app",
                packageName = "com.demo.app",
                preferPreRelease = false,
                alwaysShowLatestReleaseDownloadButton = false,
                checkActionsUpdates = false,
                updateIntervalMode = GitHubTrackedUpdateIntervalMode.FollowGlobal,
                actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
                preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.FollowGlobal,
                ignoreMode = GitHubTrackedIgnoreMode.CurrentPreRelease,
                ignoredStableReleaseKey = "release|v2.0.0",
                ignoredPreReleaseKey = "release|v2.1.0-beta",
                appList = emptyList()
            )
        )

        val item = assertIs<GitHubTrackEditorResult.Ready>(result).item

        assertEquals(GitHubTrackedIgnoreMode.CurrentPreRelease, item.ignoreMode)
        assertEquals("", item.ignoredStableReleaseKey)
        assertEquals("release|v2.1.0-beta", item.ignoredPreReleaseKey)
    }

    @Test
    fun `import preview summarizes github git and direct apk source counts`() = runBlocking {
        val preview = repository.buildTrackedItemsImportPreview(
            payload = GitHubTrackedItemsImportPayload(
                items = listOf(
                    GitHubTrackedApp(
                        repoUrl = "https://github.com/demo/app",
                        owner = "demo",
                        repo = "app",
                        packageName = "com.demo.app",
                        appLabel = "Demo"
                    ),
                    GitHubTrackedApp(
                        repoUrl = "https://telegram.org/dl/android/apk",
                        owner = "telegram.org",
                        repo = "dl-android-apk",
                        packageName = "org.telegram.messenger",
                        appLabel = "Telegram",
                        sourceMode = GitHubTrackedSourceMode.DirectApk
                    ),
                    GitHubTrackedApp(
                        repoUrl = "https://gitee.com/demo/git-app",
                        owner = "gitee.com/demo",
                        repo = "git-app",
                        packageName = "com.demo.git",
                        appLabel = "Git",
                        sourceMode = GitHubTrackedSourceMode.GitRepository
                    )
                ),
                sourceCount = 3
            ),
            existingItems = emptyList()
        )

        assertEquals(1, preview.githubRepositoryCount)
        assertEquals(1, preview.gitRepositoryCount)
        assertEquals(1, preview.directApkCount)
        assertEquals(true, preview.hasSourceBreakdown)
    }

    @Test
    fun `app picker state derives candidates off ui component`() = runBlocking {
        val result =
            repository.buildAppPickerState(
                GitHubTrackAppPickerInput(
                    appList =
                        listOf(
                            InstalledAppItem(
                                label = "Alpha",
                                packageName = "com.demo.alpha",
                                isSystemApp = false,
                            ),
                            InstalledAppItem(
                                label = "Beta",
                                packageName = "com.demo.beta",
                                isSystemApp = false,
                            ),
                            InstalledAppItem(
                                label = "System",
                                packageName = "com.demo.system",
                                isSystemApp = true,
                            ),
                        ),
                    query = "",
                    includeUserApps = true,
                    includeSystemApps = false,
                    includeTrackedApps = false,
                    trackedPackageNames = setOf("com.demo.beta"),
                    pinnedPackageNames = setOf("com.demo.beta"),
                    sortMode = GitHubTrackAppPickerSortMode.Name,
                    sortDirection = GitHubTrackAppPickerSortDirection.Ascending,
                ),
            )

        assertEquals(listOf("com.demo.alpha", "com.demo.beta"), result.filteredApps.map { it.packageName })
        assertEquals(listOf("com.demo.alpha", "com.demo.beta"), result.filteredIconPreloadPackages)
        assertEquals(false, result.deriving)
    }
}
