package os.kei.ui.page.main.github.page

import os.kei.BuildConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.asKeiOsActionsRunLookupItem
import os.kei.feature.github.model.isKeiOsReleaseTrack
import os.kei.feature.github.model.isKeiOsRepositoryTrack
import os.kei.feature.github.model.isKeiOsSelfTrack

internal data class GitHubActionsDebugNotificationTarget(
    val uiItem: GitHubTrackedApp,
    val lookupItem: GitHubTrackedApp
)

internal fun selectKeiOsActionsDebugNotificationTarget(
    trackedItems: List<GitHubTrackedApp>
): GitHubActionsDebugNotificationTarget? {
    val uiItem = trackedItems.firstOrNull { it.isKeiOsReleaseTrack() }
        ?: trackedItems.firstOrNull { it.isKeiOsSelfTrack(packageName = BuildConfig.APPLICATION_ID) }
        ?: trackedItems.firstOrNull { it.isKeiOsRepositoryTrack() }
        ?: return null
    return GitHubActionsDebugNotificationTarget(
        uiItem = uiItem,
        lookupItem = uiItem.asKeiOsActionsRunLookupItem()
    )
}
