package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubTrackedApp

internal object GitHubTrackFixtureSources {
    fun actionArtifactEntryNames(
        items: List<GitHubTrackedApp>,
        selectedItem: GitHubTrackedApp = items.first()
    ): List<Pair<String, GitHubTrackedApp>> {
        return items.mapIndexed { index, item ->
            actionArtifactEntryName(
                index = index,
                item = item,
                selectedItem = selectedItem
            ) to item
        }
    }

    fun actionArtifactEntryName(
        index: Int,
        item: GitHubTrackedApp,
        selectedItem: GitHubTrackedApp
    ): String {
        val prefix = index.toString().padStart(2, '0')
        val repoName = item.repo.replace(Regex("""[^A-Za-z0-9_.-]+"""), "-")
        val variant = if (item.id == selectedItem.id) {
            "universal-release"
        } else {
            "arm64-debug"
        }
        return "outputs/$prefix-$repoName-$variant.apk"
    }
}
