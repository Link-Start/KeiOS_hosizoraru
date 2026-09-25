package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubTrackModelsTest {
    @Test
    fun `kei os actions lookup target uses release package name`() {
        // The debug build tracks itself under its own package; its Actions lookup is still the release track.
        val item = defaultKeiOsTrackedApp(packageName = "os.kei.debug")
        assertTrue(item.isKeiOsSelfTrack(packageName = "os.kei.debug"))
        assertFalse(item.isKeiOsReleaseTrack())
        val lookupItem = item.asKeiOsActionsRunLookupItem()

        assertEquals("hosizoraru/KeiOS|os.kei", lookupItem.id)
        assertTrue(lookupItem.isKeiOsReleaseTrack())
    }

    @Test
    fun `direct apk identity preserves host and full path`() {
        val identity =
            buildDirectApkTrackIdentity("https://telegram.org/dl/android/apk-public-beta")

        assertEquals("telegram.org", identity?.owner)
        assertEquals("dl-android-apk-public-beta", identity?.repo)
        assertEquals("telegram.org/dl/android/apk-public-beta", identity?.displayName)
        assertEquals("apk-public-beta.apk", identity?.assetName)
    }

    @Test
    fun `track ids are stable per source mode`() {
        val cases = listOf(
            "GitHubRepository" to GitHubTrackedApp(
                repoUrl = "https://github.com/telegram/telegram-android",
                owner = "telegram",
                repo = "telegram-android",
                packageName = "org.telegram.messenger",
                appLabel = "Telegram"
            ) to "telegram/telegram-android|org.telegram.messenger",
            "DirectApk" to GitHubTrackedApp(
                repoUrl = "https://telegram.org/dl/android/apk",
                owner = "telegram.org",
                repo = "dl-android-apk",
                packageName = "org.telegram.messenger",
                appLabel = "Telegram",
                sourceMode = GitHubTrackedSourceMode.DirectApk
            ) to "direct_apk|telegram.org/dl-android-apk|org.telegram.messenger",
            "GitRepository host scoped owner" to GitHubTrackedApp(
                repoUrl = "https://gitee.com/demo/app",
                owner = "gitee.com/demo",
                repo = "app",
                packageName = "com.demo.app",
                appLabel = "Demo",
                sourceMode = GitHubTrackedSourceMode.GitRepository
            ) to "git_repository|gitee.com/demo/app|com.demo.app",
            "FdroidRepository normalized url and package" to GitHubTrackedApp(
                repoUrl = "https://f-droid.org/repo",
                owner = "f-droid.org",
                repo = "repo",
                packageName = "org.fdroid.fdroid",
                appLabel = "F-Droid",
                sourceMode = GitHubTrackedSourceMode.FdroidRepository
            ) to "fdroid_repository|https://f-droid.org/repo|org.fdroid.fdroid",
        )

        cases.forEach { (labelled, expectedId) ->
            val (label, item) = labelled
            assertEquals(expectedId, item.id, label)
        }
    }

    @Test
    fun `fdroid package urls normalize to repository identity`() {
        val official =
            buildFdroidRepositoryTrackIdentity(
                rawUrl = "https://f-droid.org/packages/org.fdroid.fdroid/",
                rawPackageName = ""
            )
        val izzy =
            buildFdroidRepositoryTrackIdentity(
                rawUrl = "https://apt.izzysoft.de/fdroid/index/apk/dev.imranr.obtainium",
                rawPackageName = ""
            )

        assertEquals("https://f-droid.org/repo", official?.normalizedRepoUrl)
        assertEquals("f-droid.org", official?.host)
        assertEquals("repo", official?.repo)
        assertEquals("F-Droid", official?.repoDisplayName)
        assertEquals("org.fdroid.fdroid", official?.packageName)
        assertEquals("https://apt.izzysoft.de/fdroid/repo", izzy?.normalizedRepoUrl)
        assertEquals("IzzyOnDroid", izzy?.repoDisplayName)
        assertEquals("dev.imranr.obtainium", izzy?.packageName)
    }

    @Test
    fun `persisted aliases parse`() {
        val sourceMode: (String?) -> Enum<*> = { GitHubTrackedSourceMode.fromStorageId(it) }
        val ignoreMode: (String?) -> Enum<*> = { GitHubTrackedIgnoreMode.fromStorageId(it) }
        val cases = listOf<Triple<(String?) -> Enum<*>, String?, Enum<*>>>(
            Triple(sourceMode, "git", GitHubTrackedSourceMode.GitRepository),
            Triple(sourceMode, "gitee", GitHubTrackedSourceMode.GitRepository),
            Triple(sourceMode, "gitlab", GitHubTrackedSourceMode.GitRepository),
            Triple(sourceMode, "subscription", GitHubTrackedSourceMode.DirectApk),
            Triple(sourceMode, "fdroid", GitHubTrackedSourceMode.FdroidRepository),
            Triple(sourceMode, "izzyondroid", GitHubTrackedSourceMode.FdroidRepository),
            Triple(ignoreMode, null, GitHubTrackedIgnoreMode.None),
            Triple(ignoreMode, "paused", GitHubTrackedIgnoreMode.Temporary),
            Triple(ignoreMode, "all", GitHubTrackedIgnoreMode.AllVersions),
            Triple(ignoreMode, "stable", GitHubTrackedIgnoreMode.CurrentStable),
            Triple(ignoreMode, "pre_release", GitHubTrackedIgnoreMode.CurrentPreRelease),
        )

        cases.forEach { (parse, alias, expected) ->
            assertEquals(
                expected,
                parse(alias),
                "${expected::class.simpleName}.fromStorageId(${alias?.let { "\"$it\"" }})",
            )
        }
    }

    @Test
    fun `github git repository can map to release lookup item`() {
        val git = GitHubTrackedApp(
            repoUrl = "git@github.com:demo/app.git",
            owner = "github.com/demo",
            repo = "app",
            packageName = "com.demo.app",
            appLabel = "Demo",
            sourceMode = GitHubTrackedSourceMode.GitRepository,
            checkActionsUpdates = true,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15
        )

        val lookupItem = git.githubReleaseLookupItemOrNull()

        assertEquals(GitHubTrackedSourceMode.GitHubRepository, lookupItem?.sourceMode)
        assertEquals("https://github.com/demo/app", lookupItem?.repoUrl)
        assertEquals("demo", lookupItem?.owner)
        assertEquals("app", lookupItem?.repo)
        assertEquals(false, lookupItem?.checkActionsUpdates)
        assertEquals(GitHubTrackedActionsUpdateIntervalMode.FollowGlobal, lookupItem?.actionsUpdateIntervalMode)
    }

    @Test
    fun `actions interval clears when actions check is disabled`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/demo/app",
            owner = "demo",
            repo = "app",
            packageName = "com.demo.app",
            appLabel = "Demo",
            checkActionsUpdates = false,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15
        ).withSourceModeConstraints()

        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            item.actionsUpdateIntervalMode
        )
    }

    @Test
    fun `fdroid source constraints clear github only options`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://f-droid.org/repo",
            owner = "f-droid.org",
            repo = "repo",
            packageName = "org.fdroid.fdroid",
            appLabel = "F-Droid",
            sourceMode = GitHubTrackedSourceMode.FdroidRepository,
            alwaysShowLatestReleaseDownloadButton = true,
            checkActionsUpdates = true,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15
        ).withSourceModeConstraints()

        assertEquals(false, item.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, item.checkActionsUpdates)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            item.actionsUpdateIntervalMode
        )
    }

    @Test
    fun `release ignore key prefers apk version identity`() {
        val key = buildGitHubReleaseIgnoreKey(
            displayVersion = "Demo v2",
            rawTag = "v2.0.0",
            rawName = "Demo 2.0",
            link = "https://github.com/demo/app/releases/tag/v2.0.0",
            preciseApkVersion = GitHubRemoteApkVersionInfo(
                packageName = "com.demo.app",
                versionName = "2.0.0",
                versionCode = "20",
                releaseTag = "v2.0.0"
            )
        )

        assertEquals("apk|com.demo.app|2.0.0|20", key)
        assertTrue(githubReleaseIgnoreKeyMatches(key.uppercase(), key))
    }

    @Test
    fun `rolling release ignore key collapses commit hash variants`() {
        val first = buildGitHubReleaseIgnoreKey(
            rawTag = "v0.4.0-master.26071105.9ba3ba5",
        )
        val second = buildGitHubReleaseIgnoreKey(
            rawTag = "v0.4.0-master.26071105.cbde2a0",
        )

        assertEquals(first, second)
        assertEquals("version|0.4.0|DEV|26071105|", first)
    }

    @Test
    fun `legacy apk ignore key remains compatible after release tag removal`() {
        val current = buildGitHubReleaseIgnoreKey(
            preciseApkVersion = GitHubRemoteApkVersionInfo(
                packageName = "com.demo.app",
                versionName = "2.0.0",
                versionCode = "20",
                releaseTag = "v2.0.0",
            ),
        )

        assertTrue(
            githubReleaseIgnoreKeyMatches(
                storedKey = "apk|com.demo.app|2.0.0|20|v2.0.0",
                releaseKey = current,
            ),
        )
    }

    /**
     * A rolling tag is re-pointed by CI, and the name it carries moves with every build. Keying on
     * the name means "ignore this pre-release" lasts until the next CI run — which for a tag whose
     * whole purpose is continuous builds is until tomorrow.
     */
    @Test
    fun `a rolling tag keeps one ignore key across the builds published under it`() {
        val today = buildGitHubReleaseIgnoreKey(
            rawTag = "preview",
            rawName = "pre-1.4.2-20260202-1",
            displayVersion = "pre-1.4.2-20260202-1",
            preciseApkVersion = GitHubRemoteApkVersionInfo(
                packageName = "moe.matsuri.lite",
                versionName = "1.4.2",
                versionCode = "231",
            ),
        )
        val tomorrow = buildGitHubReleaseIgnoreKey(
            rawTag = "preview",
            rawName = "pre-1.4.3-20260203-1",
            displayVersion = "pre-1.4.3-20260203-1",
            preciseApkVersion = GitHubRemoteApkVersionInfo(
                packageName = "moe.matsuri.lite",
                versionName = "1.4.3",
                versionCode = "232",
            ),
        )

        assertEquals("release|preview", today)
        assertEquals(today, tomorrow)
        assertTrue(githubReleaseIgnoreKeyMatches(storedKey = today, releaseKey = tomorrow))
    }

    @Test
    fun `release ignore key falls back to release tag`() {
        val key = buildGitHubReleaseIgnoreKey(rawTag = "v1.0.0")

        assertEquals("version|1.0.0|STABLE|0|", key)
        assertTrue(githubReleaseIgnoreKeyMatches("release|v1.0.0", key))
        assertFalse(githubReleaseIgnoreKeyMatches("", key))
    }

    @Test
    fun `tracked item ignore mode keeps only relevant release key`() {
        val item = defaultKeiOsTrackedApp()
            .withReleaseIgnoreMode(
                mode = GitHubTrackedIgnoreMode.CurrentStable,
                stableReleaseKey = "release|v1.0.0",
                preReleaseKey = "release|v1.1.0-beta"
            )

        assertEquals(GitHubTrackedIgnoreMode.CurrentStable, item.ignoreMode)
        assertEquals("release|v1.0.0", item.ignoredStableReleaseKey)
        assertEquals("", item.ignoredPreReleaseKey)
        assertEquals(
            GitHubTrackedIgnoreMode.None,
            item.withReleaseIgnoreMode(GitHubTrackedIgnoreMode.None).ignoreMode
        )
        assertEquals(
            "",
            item.withReleaseIgnoreMode(GitHubTrackedIgnoreMode.None).ignoredStableReleaseKey
        )
    }
}
