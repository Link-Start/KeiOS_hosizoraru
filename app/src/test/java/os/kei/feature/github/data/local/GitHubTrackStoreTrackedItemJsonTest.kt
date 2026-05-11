package os.kei.feature.github.data.local

import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
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
            preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Disabled
        )

        val payload = GitHubTrackStore.parseTrackedItemsImport(
            GitHubTrackStore.buildTrackedItemsExportJson(listOf(item), exportedAtMillis = 1000L)
        )

        assertEquals(
            GitHubTrackedPreciseApkVersionMode.Disabled,
            payload.items.single().preciseApkVersionMode
        )
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
}
