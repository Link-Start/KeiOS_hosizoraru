package os.kei.ui.page.main.github.page

import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.data.local.GitHubActionsRecommendedRunStore
import os.kei.feature.github.domain.GitHubActionsUpdateCheckService
import os.kei.feature.github.notification.GitHubActionsUpdateNotificationHelper
import os.kei.ui.page.main.github.localizedGitHubActionsErrorMessage
import os.kei.ui.page.main.github.page.action.GitHubPageActionEnvironment

internal class GitHubDebugNotificationActionFacade(
    private val env: GitHubPageActionEnvironment,
) {
    fun sendActionsUpdateNotification() {
        if (env.state.debugActionsUpdateNotificationLoading) return
        env.state.debugActionsUpdateNotificationLoading = true
        env.scope.launch {
            try {
                val target = selectKeiOsActionsDebugNotificationTarget(env.state.trackedItems)
                if (target == null) {
                    env.toast(R.string.github_actions_update_debug_toast_track_missing)
                    return@launch
                }
                val previous =
                    GitHubActionsRecommendedRunStore.load(target.uiItem.id)
                        ?: env.state.actionsRecommendedRunSnapshots[target.uiItem.id]
                val snapshot =
                    GitHubActionsUpdateCheckService()
                        .fetchRecommendedRunSnapshot(
                            item = target.lookupItem,
                            lookupConfig = env.state.lookupConfig,
                            previousWorkflowId = previous?.workflowId,
                        ).getOrElse { error ->
                            env.toast(
                                R.string.github_actions_update_debug_toast_fetch_failed,
                                localizedGitHubActionsErrorMessage(env.context, error.message),
                            )
                            return@launch
                        }
                val routedSnapshot =
                    snapshot.copy(
                        trackId = target.uiItem.id,
                        appLabel = target.uiItem.appLabel.ifBlank { snapshot.appLabel },
                    )
                if (env.state.trackedItems.any { it.id == target.uiItem.id }) {
                    GitHubActionsRecommendedRunStore.save(routedSnapshot)
                    env.state.actionsRecommendedRunSnapshots[target.uiItem.id] = routedSnapshot
                }
                val sent =
                    GitHubActionsUpdateNotificationHelper.notifyUpdateAvailable(
                        context = env.context,
                        snapshot = routedSnapshot,
                    )
                env.toast(
                    if (sent) {
                        R.string.github_actions_update_debug_toast_sent
                    } else {
                        R.string.github_actions_update_debug_toast_failed
                    },
                )
            } finally {
                env.state.debugActionsUpdateNotificationLoading = false
            }
        }
    }
}
