package os.kei.feature.github.data.local

import org.junit.Test
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubTrackedItemsNormalizationTest {
    @Test
    fun `tracked item normalization collapses duplicate ids and keeps latest config`() {
        val first = tracked(
            label = "Old",
            preferPreRelease = false,
            localAppType = GitHubTrackedLocalAppType.System,
        )
        val other = tracked(
            owner = "other",
            repo = "repo",
            packageName = "com.other.app",
            label = "Other",
        )
        val duplicate = tracked(
            label = "New",
            preferPreRelease = true,
            localAppType = GitHubTrackedLocalAppType.Unknown,
        )

        val normalized = normalizeTrackedItems(listOf(first, other, duplicate))

        assertEquals(listOf(first.id, other.id), normalized.map { it.id })
        assertEquals("New", normalized.first().appLabel)
        assertTrue(normalized.first().preferPreRelease)
        assertEquals(GitHubTrackedLocalAppType.System, normalized.first().localAppType)
    }

    @Test
    fun `tracked item import uses normalization when duplicate item loses app type`() {
        val raw = """
            [
              {
                "repoUrl": "https://github.com/owner/repo",
                "owner": "owner",
                "repo": "repo",
                "packageName": "com.example.app",
                "appLabel": "Old",
                "localAppType": "system"
              },
              {
                "repoUrl": "https://github.com/owner/repo",
                "owner": "owner",
                "repo": "repo",
                "packageName": "com.example.app",
                "appLabel": "New",
                "preferPreRelease": true,
                "localAppType": "unknown"
              }
            ]
        """.trimIndent()

        val payload = GitHubTrackStore.parseTrackedItemsImport(raw)

        assertEquals(2, payload.sourceCount)
        assertEquals(1, payload.duplicateCount)
        assertEquals(1, payload.items.size)
        assertEquals("New", payload.items.first().appLabel)
        assertTrue(payload.items.first().preferPreRelease)
        assertEquals(GitHubTrackedLocalAppType.System, payload.items.first().localAppType)
    }

    private fun tracked(
        owner: String = "owner",
        repo: String = "repo",
        packageName: String = "com.example.app",
        label: String,
        preferPreRelease: Boolean = false,
        localAppType: GitHubTrackedLocalAppType = GitHubTrackedLocalAppType.User,
    ): GitHubTrackedApp =
        GitHubTrackedApp(
            repoUrl = "https://github.com/$owner/$repo",
            owner = owner,
            repo = repo,
            packageName = packageName,
            appLabel = label,
            preferPreRelease = preferPreRelease,
            localAppType = localAppType,
        )
}
