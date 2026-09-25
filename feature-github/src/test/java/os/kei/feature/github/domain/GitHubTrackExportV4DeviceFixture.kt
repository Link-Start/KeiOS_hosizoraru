package os.kei.feature.github.domain

import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubTrackedApp

internal object GitHubTrackExportV4DeviceFixture {
    private const val RESOURCE_PATH = "/github/keios-github-tracks-260703-2027.json"
    const val expectedItemCount: Int = 77
    const val expectedGitHubRepositoryCount: Int = 72
    const val expectedGitRepositoryCount: Int = 2
    const val expectedDirectApkCount: Int = 3
    const val expectedFdroidRepositoryCount: Int = 0

    val rawJson: String by lazy {
        requireNotNull(GitHubTrackExportV4DeviceFixture::class.java.getResource(RESOURCE_PATH)) {
            "Missing test resource: $RESOURCE_PATH"
        }.readText()
    }

    val trackedItems: List<GitHubTrackedApp> by lazy {
        GitHubTrackStore.parseTrackedItemsImport(rawJson).items
    }

    fun itemByPackage(packageName: String): GitHubTrackedApp =
        trackedItems.first { item ->
            item.packageName.equals(packageName, ignoreCase = true)
        }
}
