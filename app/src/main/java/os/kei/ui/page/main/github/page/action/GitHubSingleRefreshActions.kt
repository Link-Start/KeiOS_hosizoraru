package os.kei.ui.page.main.github.page.action

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.model.GitHubRepositoryProfilePurpose
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.statusMessage

internal class GitHubSingleRefreshActions(
    private val owner: GitHubRefreshActions,
    private val assetActions: GitHubAssetActions,
    private val actionsRunRefreshCoordinator: GitHubActionsRecommendedRunRefreshCoordinator,
) {
    private val context get() = owner.context
    private val scope get() = owner.scope
    private val state get() = owner.state
    private val clock get() = owner.clock

    fun refreshItem(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = false,
        keepCurrentVisualWhileRefreshing: Boolean = false,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        onUpdated: ((VersionCheckUi) -> Unit)? = null,
    ): Job =
        scope.launch {
            refreshItemNow(
                item = item,
                showToastOnError = showToastOnError,
                keepCurrentVisualWhileRefreshing = keepCurrentVisualWhileRefreshing,
                profilePurposeOverride = profilePurposeOverride,
                forceRefresh = forceRefresh,
                onUpdated = onUpdated,
            )
        }

    suspend fun refreshItemNow(
        item: GitHubTrackedApp,
        showToastOnError: Boolean = false,
        keepCurrentVisualWhileRefreshing: Boolean = false,
        profilePurposeOverride: GitHubRepositoryProfilePurpose? = null,
        forceRefresh: Boolean = false,
        persistAfterUpdate: Boolean = true,
        refreshActionsAfterUpdate: Boolean = true,
        onUpdated: ((VersionCheckUi) -> Unit)? = null,
    ) {
        val previousState = state.checkStates[item.id] ?: VersionCheckUi()
        assetActions.clearApkAssetCacheNow(
            item = item,
            itemState = previousState,
            allowLatestReleaseFallback = true,
        )
        val checkingMessage = context.getString(R.string.github_msg_checking)
        state.checkStates[item.id] =
            if (keepCurrentVisualWhileRefreshing) {
                previousState.copy(message = checkingMessage)
            } else {
                previousState.copy(
                    loading = true,
                    message = checkingMessage,
                )
            }
        val itemState =
            owner
                .mergeDirectApkRemoteFallback(
                    item = item,
                    resolvedState =
                        owner.resolveItemState(
                            item = item,
                            profilePurposeOverride = profilePurposeOverride,
                            forceRefresh = forceRefresh,
                        ),
                    previousState = previousState,
                ).copy(checkedAtMillis = clock.nowMs())
        if (state.trackedItems.none { it.id == item.id }) return
        if (showToastOnError && itemState.failed) {
            owner.env.toast(itemState.statusMessage(context))
        }
        state.checkStates[item.id] = itemState
        if (persistAfterUpdate) owner.mergeCheckCacheNow(targetIds = setOf(item.id))
        if (refreshActionsAfterUpdate) actionsRunRefreshCoordinator.refreshItemInBackground(item)
        onUpdated?.invoke(itemState)
    }
}
