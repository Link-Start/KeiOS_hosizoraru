package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Immutable
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.section.GitHubOverviewMetrics

@Immutable
internal data class GitHubPageDerivedState(
    val filteredTracked: List<GitHubTrackedApp> = emptyList(),
    val sortedTracked: List<GitHubTrackedApp> = emptyList(),
    val overviewMetrics: GitHubOverviewMetrics = GitHubOverviewMetrics(
        trackedCount = 0,
        stableUpdateCount = 0,
        totalUpdatableCount = 0,
        stableLatestCount = 0,
        preReleaseCount = 0,
        preReleaseUpdateCount = 0,
        failedCount = 0
    )
)
