package os.kei.feature.github.mcp

internal enum class GitHubSortMode(
    val storageId: String
) {
    Update("update"),
    Name("name"),
    PreRelease("pre_release"),
    Changed("changed"),
    Added("added")
}

internal enum class GitHubSortDirection(
    val storageId: String
) {
    Forward("forward"),
    Reverse("reverse")
}

internal enum class GitHubTrackedFilterMode(
    val storageId: String
) {
    All("all"),
    GitHubRepository("github_repository"),
    GitRepository("git_repository"),
    DirectApk("direct_apk"),
    PreReleaseTracked("pre_release_tracked"),
    UpdateAvailable("update_available"),
    Installed("installed"),
    FailedChecks("failed_checks"),
    ActionsCheckEnabled("actions_check_enabled")
}
