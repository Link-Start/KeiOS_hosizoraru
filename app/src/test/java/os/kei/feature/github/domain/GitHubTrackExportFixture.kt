package os.kei.feature.github.domain

import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubTrackedApp

internal object GitHubTrackExportFixture {
    private const val RESOURCE_PATH = "/github/keios-github-tracks-260506-0414.json"

    val rawJson: String by lazy {
        requireNotNull(GitHubTrackExportFixture::class.java.getResource(RESOURCE_PATH)) {
            "Missing test resource: $RESOURCE_PATH"
        }.readText()
    }

    val trackedItems: List<GitHubTrackedApp> by lazy {
        GitHubTrackStore.parseTrackedItemsImport(rawJson).items
    }
}
