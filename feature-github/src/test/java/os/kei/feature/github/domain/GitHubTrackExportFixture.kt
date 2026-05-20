package os.kei.feature.github.domain

import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack

internal object GitHubTrackExportFixture {
    private const val RESOURCE_PATH = "/github/keios-github-tracks-260513-1937.json"
    const val expectedItemCount: Int = 71
    const val expectedGitHubRepositoryCount: Int = 70
    const val expectedDirectApkCount: Int = 1

    val rawJson: String by lazy {
        requireNotNull(GitHubTrackExportFixture::class.java.getResource(RESOURCE_PATH)) {
            "Missing test resource: $RESOURCE_PATH"
        }.readText()
    }

    val trackedItems: List<GitHubTrackedApp> by lazy {
        GitHubTrackStore.parseTrackedItemsImport(rawJson).items
    }

    val gitHubRepositoryItems: List<GitHubTrackedApp> by lazy {
        trackedItems.filter { it.isGitHubRepositoryTrack() }
    }

    val directApkItems: List<GitHubTrackedApp> by lazy {
        trackedItems.filter { it.isDirectApkTrack() }
    }
}
