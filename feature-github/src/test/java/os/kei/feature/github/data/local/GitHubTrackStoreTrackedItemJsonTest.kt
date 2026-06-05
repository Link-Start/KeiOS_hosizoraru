package os.kei.feature.github.data.local

import org.junit.Test
import os.kei.core.json.optArray
import os.kei.core.json.optInt
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.feature.github.model.GitHubTrackedActionsUpdateIntervalMode
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedIgnoreMode
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.feature.github.model.GitHubTrackedUpdateIntervalMode
import kotlin.test.assertEquals

class GitHubTrackStoreTrackedItemJsonTest {
    @Test
    fun `tracked item precise mode round trips through export json`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/demo/app",
            owner = "demo",
            repo = "app",
            packageName = "com.demo.app",
            appLabel = "Demo",
            preferPreRelease = true,
            alwaysShowLatestReleaseDownloadButton = true,
            checkActionsUpdates = true,
            updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours6,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes30,
            preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Disabled,
            ignoreMode = GitHubTrackedIgnoreMode.CurrentStable,
            ignoredStableReleaseKey = "release|v2.0.0",
            ignoredPreReleaseKey = "release|v2.1.0-beta",
            localAppType = GitHubTrackedLocalAppType.System
        )

        val exported = GitHubTrackStore.buildTrackedItemsExportJson(
            listOf(item),
            exportedAtMillis = 1000L
        )
        val payload = GitHubTrackStore.parseTrackedItemsImport(exported)
        val imported = payload.items.single()

        assertEquals(3, payload.schemaVersion)
        assertEquals("keios.github.tracked/v3", payload.format)
        assertEquals(1000L, payload.exportedAtMillis)
        assertEquals(true, imported.preferPreRelease)
        assertEquals(true, imported.alwaysShowLatestReleaseDownloadButton)
        assertEquals(true, imported.checkActionsUpdates)
        assertEquals(GitHubTrackedUpdateIntervalMode.Hours6, imported.updateIntervalMode)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.Minutes30,
            imported.actionsUpdateIntervalMode
        )
        assertEquals(GitHubTrackedPreciseApkVersionMode.Disabled, imported.preciseApkVersionMode)
        assertEquals(GitHubTrackedIgnoreMode.CurrentStable, imported.ignoreMode)
        assertEquals("release|v2.0.0", imported.ignoredStableReleaseKey)
        assertEquals("", imported.ignoredPreReleaseKey)
        assertEquals(GitHubTrackedLocalAppType.System, imported.localAppType)
    }

    @Test
    fun `legacy tracked item import defaults to normal tracking`() {
        val payload = GitHubTrackStore.parseTrackedItemsImport(
            """
            [
              {
                "repoUrl": "https://github.com/demo/app",
                "owner": "demo",
                "repo": "app",
                "packageName": "com.demo.app",
                "appLabel": "Demo"
              }
            ]
            """.trimIndent()
        )

        val imported = payload.items.single()

        assertEquals(GitHubTrackedIgnoreMode.None, imported.ignoreMode)
        assertEquals("", imported.ignoredStableReleaseKey)
        assertEquals("", imported.ignoredPreReleaseKey)
    }

    @Test
    fun `legacy precise apk version flag imports as enabled override`() {
        val payload = GitHubTrackStore.parseTrackedItemsImport(
            """
            [
              {
                "repoUrl": "https://github.com/demo/app",
                "owner": "demo",
                "repo": "app",
                "packageName": "com.demo.app",
                "appLabel": "Demo",
                "preciseApkVersionEnabled": true
              }
            ]
            """.trimIndent()
        )

        assertEquals(
            GitHubTrackedPreciseApkVersionMode.Enabled,
            payload.items.single().preciseApkVersionMode
        )
    }

    @Test
    fun `direct apk tracking round trips and closes github only options`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://telegram.org/dl/android/apk-public-beta",
            owner = "telegram.org",
            repo = "dl-android-apk-public-beta",
            packageName = "org.telegram.messenger.beta",
            appLabel = "Telegram Beta",
            sourceMode = GitHubTrackedSourceMode.DirectApk,
            preferPreRelease = true,
            alwaysShowLatestReleaseDownloadButton = true,
            checkActionsUpdates = true,
            updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hour1,
            preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Enabled
        )

        val exported = GitHubTrackStore.buildTrackedItemsExportJson(
            listOf(item),
            exportedAtMillis = 3000L
        )
        val root = exported.parseJsonObjectOrNull() ?: error("export json should parse")
        val exportedItem = root.optArray("items")?.optObject(0)
            ?: error("exported item should exist")
        val sourceCounts = root.optObject("sourceCounts")
            ?: error("source counts should exist")
        val source = exportedItem.optObject("source") ?: error("source should exist")
        val imported = GitHubTrackStore.parseTrackedItemsImport(exported).items.single()

        assertEquals(false, exportedItem.containsKey("owner"))
        assertEquals(false, exportedItem.containsKey("repo"))
        assertEquals("direct_apk", source.optString("mode"))
        assertEquals("telegram.org", source.optString("owner"))
        assertEquals(
            "dl-android-apk-public-beta",
            source.optString("repo")
        )
        assertEquals(0, sourceCounts.optInt("githubRepository"))
        assertEquals(1, sourceCounts.optInt("directApk"))
        assertEquals(GitHubTrackedSourceMode.DirectApk, imported.sourceMode)
        assertEquals("telegram.org", imported.owner)
        assertEquals("dl-android-apk-public-beta", imported.repo)
        assertEquals(true, imported.preferPreRelease)
        assertEquals(false, imported.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, imported.checkActionsUpdates)
        assertEquals(GitHubTrackedUpdateIntervalMode.Hour1, imported.updateIntervalMode)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            imported.actionsUpdateIntervalMode
        )
        assertEquals(GitHubTrackedPreciseApkVersionMode.Enabled, imported.preciseApkVersionMode)
    }

    @Test
    fun `direct apk import derives identity when owner and repo are missing`() {
        val payload = GitHubTrackStore.parseTrackedItemsImport(
            """
            [
              {
                "sourceMode": "direct_apk",
                "repoUrl": "https://telegram.org/dl/android/apk",
                "packageName": "org.telegram.messenger",
                "appLabel": "",
                "settings": {
                  "preferPreRelease": true,
                  "alwaysShowLatestReleaseDownloadButton": true,
                  "checkActionsUpdates": true
                }
              }
            ]
            """.trimIndent()
        )

        val imported = payload.items.single()

        assertEquals(GitHubTrackedSourceMode.DirectApk, imported.sourceMode)
        assertEquals("telegram.org", imported.owner)
        assertEquals("dl-android-apk", imported.repo)
        assertEquals("org.telegram.messenger", imported.appLabel)
        assertEquals(true, imported.preferPreRelease)
        assertEquals(false, imported.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, imported.checkActionsUpdates)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            imported.actionsUpdateIntervalMode
        )
    }

    @Test
    fun `git repository import derives host scoped identity when owner and repo are missing`() {
        val payload = GitHubTrackStore.parseTrackedItemsImport(
            """
            [
              {
                "source": {
                  "mode": "git_repository",
                  "url": "git@gitee.com:demo/app.git"
                },
                "repoUrl": "git@gitee.com:demo/app.git",
                "packageName": "com.demo.app",
                "appLabel": "",
                "settings": {
                  "alwaysShowLatestReleaseDownloadButton": true,
                  "checkActionsUpdates": true,
                  "actionsUpdateIntervalMode": "15m"
                }
              }
            ]
            """.trimIndent()
        )

        val imported = payload.items.single()

        assertEquals(GitHubTrackedSourceMode.GitRepository, imported.sourceMode)
        assertEquals("gitee.com/demo", imported.owner)
        assertEquals("app", imported.repo)
        assertEquals("com.demo.app", imported.appLabel)
        assertEquals(false, imported.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, imported.checkActionsUpdates)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            imported.actionsUpdateIntervalMode
        )
    }

    @Test
    fun `git repository tracking exports source counts and host scoped owner`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://gitlab.com/group/subgroup/app",
            owner = "gitlab.com/group/subgroup",
            repo = "app",
            packageName = "com.demo.app",
            appLabel = "Demo",
            sourceMode = GitHubTrackedSourceMode.GitRepository,
            checkActionsUpdates = true,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15
        )

        val exported = GitHubTrackStore.buildTrackedItemsExportJson(
            listOf(item),
            exportedAtMillis = 4000L
        )
        val root = exported.parseJsonObjectOrNull() ?: error("export json should parse")
        val exportedItem = root.optArray("items")?.optObject(0)
            ?: error("exported item should exist")
        val source = exportedItem.optObject("source") ?: error("source should exist")
        val sourceCounts = root.optObject("sourceCounts")
            ?: error("source counts should exist")
        val imported = GitHubTrackStore.parseTrackedItemsImport(exported).items.single()

        assertEquals("git_repository", source.optString("mode"))
        assertEquals("gitlab.com/group/subgroup", source.optString("owner"))
        assertEquals("app", source.optString("repo"))
        assertEquals("gitlab.com/group/subgroup", exportedItem.optString("owner"))
        assertEquals("app", exportedItem.optString("repo"))
        assertEquals(0, sourceCounts.optInt("githubRepository"))
        assertEquals(1, sourceCounts.optInt("gitRepository"))
        assertEquals(0, sourceCounts.optInt("directApk"))
        assertEquals(GitHubTrackedSourceMode.GitRepository, imported.sourceMode)
        assertEquals(false, imported.checkActionsUpdates)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            imported.actionsUpdateIntervalMode
        )
    }

    @Test
    fun `nested v2 settings import as project options`() {
        val payload = GitHubTrackStore.parseTrackedItemsImport(
            """
            {
              "format": "keios.github.tracked/v2",
              "schemaVersion": 2,
              "exportedAtMillis": 2000,
              "items": [
                {
                  "repoUrl": "https://github.com/demo/app",
                  "owner": "demo",
                  "repo": "app",
                  "packageName": "com.demo.app",
                  "appLabel": "Demo",
                    "settings": {
                      "preferPreRelease": true,
                      "alwaysShowLatestReleaseDownloadButton": true,
                      "checkActionsUpdates": true,
                      "updateIntervalMode": "12h",
                      "actionsUpdateIntervalMode": "15m",
                      "preciseApkVersionMode": "enabled",
                      "localAppType": "system"
                    },
                  "local": {
                    "appType": "system"
                  },
                  "repository": {
                    "archived": true,
                    "fork": true
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val imported = payload.items.single()

        assertEquals(2, payload.schemaVersion)
        assertEquals(2000L, payload.exportedAtMillis)
        assertEquals(true, imported.preferPreRelease)
        assertEquals(true, imported.alwaysShowLatestReleaseDownloadButton)
        assertEquals(true, imported.checkActionsUpdates)
        assertEquals(GitHubTrackedUpdateIntervalMode.Hours12, imported.updateIntervalMode)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.Minutes15,
            imported.actionsUpdateIntervalMode
        )
        assertEquals(GitHubTrackedPreciseApkVersionMode.Enabled, imported.preciseApkVersionMode)
        assertEquals(GitHubTrackedLocalAppType.System, imported.localAppType)
        assertEquals(true, imported.repositoryArchived)
        assertEquals(true, imported.repositoryFork)
    }

    @Test
    fun `option counts summarize imported project settings`() {
        val counts = GitHubTrackStore.calculateTrackedItemsOptionCounts(
            listOf(
                GitHubTrackedApp(
                    repoUrl = "https://github.com/demo/app",
                    owner = "demo",
                    repo = "app",
                    packageName = "com.demo.app",
                    appLabel = "Demo",
                    preferPreRelease = true,
                    alwaysShowLatestReleaseDownloadButton = true,
                    checkActionsUpdates = true,
                    updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours3,
                    preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Disabled
                ),
                GitHubTrackedApp(
                    repoUrl = "https://github.com/demo/other",
                    owner = "demo",
                    repo = "other",
                    packageName = "com.demo.other",
                    appLabel = "Other"
                )
            )
        )

        assertEquals(1, counts.preferPreReleaseCount)
        assertEquals(1, counts.latestReleaseDownloadCount)
        assertEquals(1, counts.actionsUpdateCount)
        assertEquals(1, counts.updateIntervalOverrideCount)
        assertEquals(1, counts.preciseApkVersionOverrideCount)
    }

    @Test
    fun `source counts summarize github and direct apk items`() {
        val counts = GitHubTrackStore.calculateTrackedItemsSourceCounts(
            listOf(
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
                    repoUrl = "https://gitee.com/demo/app",
                    owner = "gitee.com/demo",
                    repo = "app",
                    packageName = "com.demo.git",
                    appLabel = "Git",
                    sourceMode = GitHubTrackedSourceMode.GitRepository
                )
            )
        )

        assertEquals(1, counts.githubRepositoryCount)
        assertEquals(1, counts.gitRepositoryCount)
        assertEquals(1, counts.directApkCount)
    }
}
