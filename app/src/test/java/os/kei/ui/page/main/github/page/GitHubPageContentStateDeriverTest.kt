package os.kei.ui.page.main.github.page

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import kotlin.test.assertEquals
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

    @Test
    fun `all filter keeps searched tracked items`() = runBlocking {
        val items = sampleTrackedItems()
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                trackedSearch = "demo",
                trackedFilterMode = GitHubTrackedFilterMode.All
            )
        )

        assertEquals(
            listOf("demo.stable", "demo.pre", "demo.actions"),
            derived.trackedUi.filteredTracked.map { it.packageName }
        )
    }

    @Test
    fun `pre release filter follows check state prerelease semantic`() = runBlocking {
        val items = sampleTrackedItems()
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                trackedFilterMode = GitHubTrackedFilterMode.PreReleaseTracked,
                checkStates = mapOf(
                    items[0].id to VersionCheckUi(isPreRelease = false),
                    items[1].id to VersionCheckUi(isPreRelease = true)
                )
            )
        )

        assertEquals(listOf("demo.pre"), derived.trackedUi.filteredTracked.map { it.packageName })
    }

    @Test
    fun `update filter includes stable and pre release updates`() = runBlocking {
        val items = sampleTrackedItems()
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                trackedFilterMode = GitHubTrackedFilterMode.UpdateAvailable,
                checkStates = mapOf(
                    items[0].id to VersionCheckUi(hasUpdate = true),
                    items[1].id to VersionCheckUi(hasPreReleaseUpdate = true),
                    items[2].id to VersionCheckUi(hasUpdate = false, hasPreReleaseUpdate = false)
                )
            )
        )

        assertEquals(
            listOf("demo.stable", "demo.pre"),
            derived.trackedUi.filteredTracked.map { it.packageName })
    }

    @Test
    fun `installed filter matches installed package list`() = runBlocking {
        val items = sampleTrackedItems()
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                trackedFilterMode = GitHubTrackedFilterMode.Installed,
                appList = listOf(InstalledAppItem(label = "Stable", packageName = "demo.stable"))
            )
        )

        assertEquals(
            listOf("demo.stable"),
            derived.trackedUi.filteredTracked.map { it.packageName })
    }

    @Test
    fun `failed checks filter keeps old failed only behavior`() = runBlocking {
        val items = sampleTrackedItems()
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                trackedFilterMode = GitHubTrackedFilterMode.FailedChecks,
                checkStates = mapOf(
                    items[0].id to VersionCheckUi(failed = true),
                    items[1].id to VersionCheckUi(failed = false)
                )
            )
        )

        assertEquals(
            listOf("demo.stable"),
            derived.trackedUi.filteredTracked.map { it.packageName })
    }

    @Test
    fun `actions check filter keeps actions enabled items`() = runBlocking {
        val items = sampleTrackedItems()
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                trackedFilterMode = GitHubTrackedFilterMode.ActionsCheckEnabled
            )
        )

        assertEquals(
            listOf("demo.actions"),
            derived.trackedUi.filteredTracked.map { it.packageName })
    }

    @Test
    fun `source filters split github repositories and direct apk tracks`() = runBlocking {
        val items = sampleTrackedItems() + GitHubTrackedApp(
            repoUrl = "https://telegram.org/dl/android/apk",
            owner = "telegram.org",
            repo = "dl-android-apk",
            packageName = "org.telegram.messenger",
            appLabel = "Telegram",
            sourceMode = GitHubTrackedSourceMode.DirectApk
        )

        val githubDerived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                trackedFilterMode = GitHubTrackedFilterMode.GitHubRepository
            )
        )
        val directDerived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                trackedFilterMode = GitHubTrackedFilterMode.DirectApk
            )
        )

        assertEquals(
            listOf("demo.stable", "demo.pre", "demo.actions"),
            githubDerived.trackedUi.filteredTracked.map { it.packageName }
        )
        assertEquals(
            listOf("org.telegram.messenger"),
            directDerived.trackedUi.filteredTracked.map { it.packageName }
        )
    }

    private fun baseInput(
        trackedItems: List<GitHubTrackedApp> = emptyList(),
        trackedSearch: String = "",
        trackedFilterMode: GitHubTrackedFilterMode = GitHubTrackedFilterMode.All,
        sortMode: GitHubSortMode = GitHubSortMode.UpdateFirst,
        checkStates: Map<String, VersionCheckUi> = emptyMap(),
        appList: List<InstalledAppItem> = emptyList(),
        pendingShareImportTrack: GitHubPendingShareImportTrack? = null,
        nowMillis: Long = 0L
    ): GitHubPageContentInput {
        return GitHubPageContentInput(
            trackedItems = trackedItems,
            trackedSearch = trackedSearch,
            trackedFilterMode = trackedFilterMode,
            sortMode = sortMode,
            checkStates = checkStates,
            appList = appList,
            trackedFirstInstallAtByPackage = emptyMap(),
            trackedAddedAtById = emptyMap(),
            pendingShareImportTrack = pendingShareImportTrack,
            nowMillis = nowMillis
        )
    }

    private fun sampleTrackedItems(): List<GitHubTrackedApp> {
        return listOf(
            GitHubTrackedApp(
                repoUrl = "https://github.com/owner/stable",
                owner = "owner",
                repo = "stable",
                packageName = "demo.stable",
                appLabel = "Demo Stable"
            ),
            GitHubTrackedApp(
                repoUrl = "https://github.com/owner/pre",
                owner = "owner",
                repo = "pre",
                packageName = "demo.pre",
                appLabel = "Demo Pre"
            ),
            GitHubTrackedApp(
                repoUrl = "https://github.com/owner/actions",
                owner = "owner",
                repo = "actions",
                packageName = "demo.actions",
                appLabel = "Demo Actions",
                checkActionsUpdates = true
            )
        )
    }
}
