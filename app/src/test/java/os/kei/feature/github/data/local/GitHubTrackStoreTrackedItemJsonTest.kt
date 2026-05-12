package os.kei.feature.github.data.local

import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.feature.github.model.GitHubTrackedPreciseApkVersionMode
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
            preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Disabled,
            localAppType = GitHubTrackedLocalAppType.System
        )

        val exported = GitHubTrackStore.buildTrackedItemsExportJson(
            listOf(item),
            exportedAtMillis = 1000L
        )
        val payload = GitHubTrackStore.parseTrackedItemsImport(exported)
        val imported = payload.items.single()

        assertEquals(2, payload.schemaVersion)
        assertEquals("keios.github.tracked/v2", payload.format)
        assertEquals(1000L, payload.exportedAtMillis)
        assertEquals(true, imported.preferPreRelease)
        assertEquals(true, imported.alwaysShowLatestReleaseDownloadButton)
        assertEquals(true, imported.checkActionsUpdates)
        assertEquals(GitHubTrackedPreciseApkVersionMode.Disabled, imported.preciseApkVersionMode)
        assertEquals(GitHubTrackedLocalAppType.System, imported.localAppType)
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
        assertEquals(1, counts.preciseApkVersionOverrideCount)
    }
}
