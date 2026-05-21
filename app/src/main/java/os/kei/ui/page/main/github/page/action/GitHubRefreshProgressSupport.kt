package os.kei.ui.page.main.github.page.action

internal const val GITHUB_REFRESH_UI_BATCH_SIZE = 4
internal const val GITHUB_REFRESH_PROGRESS_NOTIFY_BATCH_SIZE = 2
internal const val GITHUB_REFRESH_PROGRESS_NOTIFY_MIN_INTERVAL_MS = 500L
internal const val GITHUB_REFRESH_PROGRESS_NOTIFY_INTERVAL_MS = 850L

internal data class GitHubRefreshProgressSnapshot(
    val current: Int,
    val total: Int,
    val preReleaseUpdateCount: Int,
    val updatableCount: Int,
    val failedCount: Int,
)
