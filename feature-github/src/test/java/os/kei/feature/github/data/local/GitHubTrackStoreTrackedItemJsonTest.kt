package os.kei.feature.github.data.local

import org.junit.Test
import os.kei.core.json.optArray
import os.kei.core.json.optBoolean
import os.kei.core.json.optInt
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.feature.github.model.FdroidAntiFeaturePolicy
import os.kei.feature.github.model.FdroidIndexFormat
import os.kei.feature.github.model.FdroidTrackedAppConfig
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.model.FdroidVersionSelectionMode
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
            externalBuildUntilRelease = true,
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

        assertEquals(4, payload.schemaVersion)
        assertEquals("keios.github.tracked/v4", payload.format)
        assertEquals(1000L, payload.exportedAtMillis)
        assertEquals(true, imported.preferPreRelease)
        assertEquals(true, imported.alwaysShowLatestReleaseDownloadButton)
        assertEquals(true, imported.checkActionsUpdates)
        assertEquals(true, imported.externalBuildUntilRelease)
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
        assertEquals(false, imported.externalBuildUntilRelease)
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
    fun `fdroid tracking round trips source config and counts`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://f-droid.org/packages/org.fdroid.fdroid/",
            owner = "f-droid.org",
            repo = "repo",
            packageName = "org.fdroid.fdroid",
            appLabel = "F-Droid",
            sourceMode = GitHubTrackedSourceMode.FdroidRepository,
            updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours24,
            alwaysShowLatestReleaseDownloadButton = true,
            checkActionsUpdates = true,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15,
            fdroidConfig = FdroidTrackedAppConfig(
                selectionMode = FdroidVersionSelectionMode.HighestCompatibleVersionCode,
                versionNameRegex = "^1\\.",
                apkNameRegex = "arm64",
                repoFingerprint = "AA:BB",
                indexFormat = FdroidIndexFormat.V2,
                trustPolicy = FdroidTrustPolicy.RequireApkHash,
                antiFeaturePolicy = FdroidAntiFeaturePolicy.Custom,
                blockedAntiFeatures = listOf("Tracking", "KnownVuln"),
                packagePageUrl = "https://f-droid.org/packages/org.fdroid.fdroid/",
                repoPresetId = "fdroid"
            )
        )

        val exported = GitHubTrackStore.buildTrackedItemsExportJson(
            listOf(item),
            exportedAtMillis = 5000L
        )
        val root = exported.parseJsonObjectOrNull() ?: error("export json should parse")
        val exportedItem = root.optArray("items")?.optObject(0)
            ?: error("exported item should exist")
        val source = exportedItem.optObject("source") ?: error("source should exist")
        val fdroid = source.optObject("fdroid") ?: error("fdroid source config should exist")
        val schemaFeatures = root.optObject("schemaFeatures")
            ?: error("schema features should exist")
        val sourceCounts = root.optObject("sourceCounts")
            ?: error("source counts should exist")
        val imported = GitHubTrackStore.parseTrackedItemsImport(exported).items.single()

        assertEquals(true, schemaFeatures.optBoolean("fdroidRepositorySource"))
        assertEquals(12, schemaFeatures.optInt("fdroidDefaultUpdateIntervalHours"))
        assertEquals(24, schemaFeatures.optInt("fdroidCustomUpdateIntervalMaxHours"))
        assertEquals(true, schemaFeatures.optBoolean("webDavMergeCompatible"))
        assertEquals("fdroid_repository", source.optString("mode"))
        assertEquals("https://f-droid.org/repo", source.optString("url"))
        assertEquals("org.fdroid.fdroid", source.optString("packageName"))
        assertEquals("24h", exportedItem.optObject("settings")?.optString("updateIntervalMode"))
        assertEquals("highest_compatible_version_code", fdroid.optString("selectionMode"))
        assertEquals("require_apk_hash", fdroid.optString("trustPolicy"))
        assertEquals("custom", fdroid.optString("antiFeaturePolicy"))
        assertEquals("Tracking", fdroid.optArray("blockedAntiFeatures")?.optString(0))
        assertEquals(0, sourceCounts.optInt("githubRepository"))
        assertEquals(0, sourceCounts.optInt("gitRepository"))
        assertEquals(0, sourceCounts.optInt("directApk"))
        assertEquals(1, sourceCounts.optInt("fdroidRepository"))
        assertEquals(GitHubTrackedSourceMode.FdroidRepository, imported.sourceMode)
        assertEquals("https://f-droid.org/repo", imported.repoUrl)
        assertEquals("f-droid.org", imported.owner)
        assertEquals("repo", imported.repo)
        assertEquals(false, imported.alwaysShowLatestReleaseDownloadButton)
        assertEquals(false, imported.checkActionsUpdates)
        assertEquals(GitHubTrackedUpdateIntervalMode.Hours24, imported.updateIntervalMode)
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            imported.actionsUpdateIntervalMode
        )
        assertEquals(
            FdroidVersionSelectionMode.HighestCompatibleVersionCode,
            imported.fdroidConfig.selectionMode
        )
        assertEquals(FdroidTrustPolicy.RequireApkHash, imported.fdroidConfig.trustPolicy)
        assertEquals(FdroidAntiFeaturePolicy.Custom, imported.fdroidConfig.antiFeaturePolicy)
        assertEquals(listOf("Tracking", "KnownVuln"), imported.fdroidConfig.blockedAntiFeatures)
        assertEquals(
            "https://f-droid.org/packages/org.fdroid.fdroid/",
            imported.fdroidConfig.packagePageUrl
        )
        assertEquals("fdroid", imported.fdroidConfig.repoPresetId)
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

}
