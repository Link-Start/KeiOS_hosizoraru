package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode

/** A tracked app `demo/repo-<index>` (package `demo.repo<index>`) with a repo URL that fits [sourceMode]. */
internal fun trackedFixture(
    index: Int,
    sourceMode: GitHubTrackedSourceMode = GitHubTrackedSourceMode.GitHubRepository
): GitHubTrackedApp {
    return GitHubTrackedApp(
        repoUrl = when (sourceMode) {
            GitHubTrackedSourceMode.GitHubRepository -> "https://github.com/demo/repo-$index"
            GitHubTrackedSourceMode.GitRepository -> "https://gitee.com/demo/repo-$index"
            GitHubTrackedSourceMode.DirectApk -> "https://example.com/download/repo-$index.apk"
            GitHubTrackedSourceMode.FdroidRepository -> "https://f-droid.org/repo"
        },
        owner = "demo",
        repo = "repo-$index",
        packageName = "demo.repo$index",
        appLabel = "Repo $index",
        sourceMode = sourceMode
    )
}
