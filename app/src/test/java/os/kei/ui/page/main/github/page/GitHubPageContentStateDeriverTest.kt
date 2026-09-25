package os.kei.ui.page.main.github.page

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubPageContentStateDeriverTest {
    @Test
    fun `pending share import card stays visible only during linkage window`() = runBlocking {
        val armedAtMillis = 1_000L
        listOf(
            Triple("24 minutes after arming", armedAtMillis + 24 * 60 * 1000L, true),
            Triple("26 minutes after arming", armedAtMillis + 26 * 60 * 1000L, false),
        ).forEach { (case, nowMillis, expected) ->
            val derived = GitHubPageContentStateDeriver().build(
                baseInput(
                    pendingShareImportTrack = GitHubPendingShareImportTrack(
                        projectUrl = "https://github.com/owner/repo",
                        owner = "owner",
                        repo = "repo",
                        assetName = "demo.apk",
                        armedAtMillis = armedAtMillis
                    ),
                    nowMillis = nowMillis
                )
            )

            assertEquals(expected, derived.showPendingShareImportCard, case)
        }
    }

    @Test
    fun `tracked filter modes keep only matching tracked items`() = runBlocking {
        data class Case(
            val name: String,
            val mode: GitHubTrackedFilterMode,
            val search: String = "",
            val withDirectApk: Boolean = false,
            val checkStates: (List<GitHubTrackedApp>) -> Map<String, VersionCheckUi> = { emptyMap() },
            val appList: List<InstalledAppItem> = emptyList(),
            val expected: List<String>,
        )
        val directApkItem = GitHubTrackedApp(
            repoUrl = "https://telegram.org/dl/android/apk",
            owner = "telegram.org",
            repo = "dl-android-apk",
            packageName = "org.telegram.messenger",
            appLabel = "Telegram",
            sourceMode = GitHubTrackedSourceMode.DirectApk
        )
        listOf(
            Case(
                name = "all filter keeps searched tracked items",
                mode = GitHubTrackedFilterMode.All,
                search = "demo",
                expected = listOf("demo.stable", "demo.pre", "demo.actions"),
            ),
            Case(
                name = "pre release filter follows check state prerelease semantic",
                mode = GitHubTrackedFilterMode.PreReleaseTracked,
                checkStates = { items ->
                    mapOf(
                        items[0].id to VersionCheckUi(isPreRelease = false),
                        items[1].id to VersionCheckUi(isPreRelease = true)
                    )
                },
                expected = listOf("demo.pre"),
            ),
            Case(
                name = "update filter includes stable and pre release updates",
                mode = GitHubTrackedFilterMode.UpdateAvailable,
                checkStates = { items ->
                    mapOf(
                        items[0].id to VersionCheckUi(hasUpdate = true),
                        items[1].id to VersionCheckUi(hasPreReleaseUpdate = true),
                        items[2].id to VersionCheckUi(hasUpdate = false, hasPreReleaseUpdate = false)
                    )
                },
                expected = listOf("demo.stable", "demo.pre"),
            ),
            Case(
                name = "installed filter matches installed package list",
                mode = GitHubTrackedFilterMode.Installed,
                appList = listOf(InstalledAppItem(label = "Stable", packageName = "demo.stable")),
                expected = listOf("demo.stable"),
            ),
            Case(
                name = "failed checks filter keeps old failed only behavior",
                mode = GitHubTrackedFilterMode.FailedChecks,
                checkStates = { items ->
                    mapOf(
                        items[0].id to VersionCheckUi(failed = true),
                        items[1].id to VersionCheckUi(failed = false)
                    )
                },
                expected = listOf("demo.stable"),
            ),
            Case(
                name = "actions check filter keeps actions enabled items",
                mode = GitHubTrackedFilterMode.ActionsCheckEnabled,
                expected = listOf("demo.actions"),
            ),
            Case(
                name = "github repository filter excludes direct apk tracks",
                mode = GitHubTrackedFilterMode.GitHubRepository,
                withDirectApk = true,
                expected = listOf("demo.stable", "demo.pre", "demo.actions"),
            ),
            Case(
                name = "direct apk filter keeps only direct apk tracks",
                mode = GitHubTrackedFilterMode.DirectApk,
                withDirectApk = true,
                expected = listOf("org.telegram.messenger"),
            ),
        ).forEach { case ->
            val items = sampleTrackedItems() + listOfNotNull(directApkItem.takeIf { case.withDirectApk })
            val derived = GitHubPageContentStateDeriver().build(
                baseInput(
                    trackedItems = items,
                    trackedSearch = case.search,
                    trackedFilterMode = case.mode,
                    checkStates = case.checkStates(items),
                    appList = case.appList
                )
            )

            assertEquals(
                case.expected,
                derived.trackedUi.filteredTracked.map { it.packageName },
                case.name
            )
        }
    }

    @Test
    fun `failed checks filter resets only when no tracked item is failed`() = runBlocking {
        val items = sampleTrackedItems()
        listOf(
            Triple("no tracked item is failed", false, true),
            Triple("a failed item remains", true, false),
        ).forEach { (case, firstItemFailed, expectedReset) ->
            val input = baseInput(
                trackedItems = items,
                trackedFilterMode = GitHubTrackedFilterMode.FailedChecks,
                checkStates = mapOf(
                    items[0].id to VersionCheckUi(failed = firstItemFailed),
                    items[1].id to VersionCheckUi(failed = false)
                )
            )
            val derived = GitHubPageContentStateDeriver().build(input)

            assertEquals(expectedReset, shouldResetFailedTrackedFilter(input, derived), case)
        }
    }

    @Test
    fun `changed sort uses modified time with added time fallback`() = runBlocking {
        val items = sampleTrackedItems()
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                sortMode = GitHubSortMode.Changed,
                sortDirection = GitHubSortDirection.Forward,
                trackedAddedAtById = mapOf(
                    items[0].id to 100L,
                    items[1].id to 200L,
                    items[2].id to 300L
                ),
                trackedModifiedAtById = mapOf(
                    items[0].id to 700L,
                    items[1].id to 500L
                )
            )
        )

        assertEquals(
            listOf("demo.stable", "demo.pre", "demo.actions"),
            derived.trackedUi.sortedTracked.map { it.packageName }
        )
    }

    @Test
    fun `added sort orders by tracking creation time`() = runBlocking {
        val items = sampleTrackedItems()
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items,
                sortMode = GitHubSortMode.Added,
                sortDirection = GitHubSortDirection.Reverse,
                trackedAddedAtById = mapOf(
                    items[0].id to 300L,
                    items[1].id to 100L,
                    items[2].id to 200L
                )
            )
        )

        assertEquals(
            listOf("demo.pre", "demo.actions", "demo.stable"),
            derived.trackedUi.sortedTracked.map { it.packageName }
        )
    }

    @Test
    fun `archived repositories stay after active repositories for every sort option`() = runBlocking {
        val active = sampleTrackedItems()
        val archivedFromTrack =
            GitHubTrackedApp(
                repoUrl = "https://github.com/archive/track",
                owner = "archive",
                repo = "track",
                packageName = "demo.archived.track",
                appLabel = "A Archived Track",
                repositoryArchived = true,
            )
        val archivedFromRefresh =
            GitHubTrackedApp(
                repoUrl = "https://github.com/archive/refresh",
                owner = "archive",
                repo = "refresh",
                packageName = "demo.archived.refresh",
                appLabel = "B Archived Refresh",
            )
        val items = listOf(archivedFromTrack, archivedFromRefresh) + active
        val states =
            mapOf(
                archivedFromRefresh.id to
                    VersionCheckUi(
                        repositoryArchived = true,
                        checkedAtMillis = 1_000L,
                    ),
            )

        GitHubSortMode.entries.forEach { sortMode ->
            GitHubSortDirection.entries.forEach { sortDirection ->
                val derived =
                    GitHubPageContentStateDeriver().build(
                        baseInput(
                            trackedItems = items,
                            sortMode = sortMode,
                            sortDirection = sortDirection,
                            checkStates = states,
                        ),
                    )

                assertEquals(
                    setOf("demo.archived.track", "demo.archived.refresh"),
                    derived.trackedUi.sortedTracked.takeLast(2).map { it.packageName }.toSet(),
                )
            }
        }
    }

    @Test
    fun `fresh unarchived refresh restores normal sorting`() = runBlocking {
        val formerlyArchived =
            GitHubTrackedApp(
                repoUrl = "https://github.com/owner/alpha",
                owner = "owner",
                repo = "alpha",
                packageName = "demo.alpha",
                appLabel = "Alpha",
                repositoryArchived = true,
            )
        val active =
            GitHubTrackedApp(
                repoUrl = "https://github.com/owner/zulu",
                owner = "owner",
                repo = "zulu",
                packageName = "demo.zulu",
                appLabel = "Zulu",
            )
        val derived =
            GitHubPageContentStateDeriver().build(
                baseInput(
                    trackedItems = listOf(active, formerlyArchived),
                    sortMode = GitHubSortMode.Name,
                    sortDirection = GitHubSortDirection.Forward,
                    checkStates =
                        mapOf(
                            formerlyArchived.id to
                                VersionCheckUi(
                                    repositoryArchived = false,
                                    checkedAtMillis = 2_000L,
                                ),
                        ),
                ),
            )

        assertEquals(
            listOf("demo.alpha", "demo.zulu"),
            derived.trackedUi.sortedTracked.map { it.packageName },
        )
    }

    @Test
    fun `content derivation exposes stable page keys and self track flag`() = runBlocking {
        val items = sampleTrackedItems() + GitHubTrackedApp(
            repoUrl = "https://github.com/MuntashirAkon/AppManager",
            owner = "MuntashirAkon",
            repo = "AppManager",
            packageName = "io.github.muntashirakon.AppManager",
            appLabel = "App Manager"
        )
        val selfTrack = GitHubTrackedApp(
            repoUrl = "https://github.com/hosizoraru/KeiOS",
            owner = "hosizoraru",
            repo = "KeiOS",
            packageName = "os.kei",
            appLabel = "KeiOS"
        )
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                trackedItems = items + selfTrack,
                selfPackageName = "os.kei"
            )
        )

        assertEquals((items + selfTrack).joinToString(separator = "\n") { it.id }, derived.trackedItemIdKey)
        assertEquals(derived.trackedUi.sortedTracked.map { it.id }, derived.sortedTrackIds)
        assertEquals(
            derived.trackedUi.sortedTracked.map { it.packageName },
            derived.trackedIconPreloadPackages
        )
        assertTrue(derived.hasKeiOsSelfTrack)
    }

    @Test
    fun `content derivation exposes installed icon preload packages`() = runBlocking {
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(
                appList = listOf(
                    InstalledAppItem(label = "Alpha", packageName = " com.demo.alpha "),
                    InstalledAppItem(label = "Duplicate", packageName = "com.demo.alpha"),
                    InstalledAppItem(label = "Beta", packageName = "com.demo.beta"),
                    InstalledAppItem(label = "Blank", packageName = " ")
                )
            )
        )

        assertEquals(
            listOf("com.demo.alpha", "com.demo.beta"),
            derived.installedIconPreloadPackages
        )
    }

    @Test
    fun `content derivation keeps all tracked icon preload packages beyond initial viewport`() = runBlocking {
        val items =
            (0 until 45).map { index ->
                GitHubTrackedApp(
                    repoUrl = "https://github.com/owner/repo-$index",
                    owner = "owner",
                    repo = "repo-$index",
                    packageName = "demo.tracked.$index",
                    appLabel = "Demo $index"
                )
            }
        val derived = GitHubPageContentStateDeriver().build(
            baseInput(trackedItems = items)
        )

        assertEquals(45, derived.trackedIconPreloadPackages.size)
        assertEquals(
            items.map { it.packageName }.toSet(),
            derived.trackedIconPreloadPackages.toSet()
        )
    }

    private fun baseInput(
        trackedItems: List<GitHubTrackedApp> = emptyList(),
        trackedSearch: String = "",
        trackedFilterMode: GitHubTrackedFilterMode = GitHubTrackedFilterMode.All,
        sortMode: GitHubSortMode = GitHubSortMode.Update,
        sortDirection: GitHubSortDirection = GitHubSortDirection.Forward,
        checkStates: Map<String, VersionCheckUi> = emptyMap(),
        appList: List<InstalledAppItem> = emptyList(),
        trackedAddedAtById: Map<String, Long> = emptyMap(),
        trackedModifiedAtById: Map<String, Long> = emptyMap(),
        pendingShareImportTrack: GitHubPendingShareImportTrack? = null,
        selfPackageName: String = "os.kei",
        pinnedTrackIds: List<String> = emptyList(),
        nowMillis: Long = 0L
    ): GitHubPageContentInput {
        return GitHubPageContentInput(
            trackedItems = trackedItems,
            trackedSearch = trackedSearch,
            trackedFilterMode = trackedFilterMode,
            sortMode = sortMode,
            sortDirection = sortDirection,
            checkStates = checkStates,
            appList = appList,
            trackedFirstInstallAtByPackage = emptyMap(),
            trackedAddedAtById = trackedAddedAtById,
            trackedModifiedAtById = trackedModifiedAtById,
            pendingShareImportTrack = pendingShareImportTrack,
            pinnedTrackIds = pinnedTrackIds,
            selfPackageName = selfPackageName,
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
