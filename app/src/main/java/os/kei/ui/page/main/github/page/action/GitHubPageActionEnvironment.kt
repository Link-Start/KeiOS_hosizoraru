package os.kei.ui.page.main.github.page.action

import android.content.Context
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.core.ext.showToast
import os.kei.feature.github.domain.GitHubActionsService
import os.kei.ui.page.main.github.page.GitHubPageRepository
import os.kei.ui.page.main.github.page.GitHubPageState
import os.kei.ui.page.main.github.page.GitHubPageViewModel
import os.kei.ui.page.main.github.query.DownloaderOption

internal class GitHubPageActionEnvironment(
    val context: Context,
    val scope: CoroutineScope,
    val state: GitHubPageState,
    val viewModel: GitHubPageViewModel,
    val repository: GitHubPageRepository,
    val actionsRepository: GitHubActionsService = GitHubActionsService(),
    val systemDmOption: DownloaderOption,
    val openLinkFailureMessage: String,
    val clock: GitHubActionClock = GitHubSystemActionClock,
) {
    fun string(
        @StringRes resId: Int,
        vararg args: Any,
    ): String = context.getString(resId, *args)

    fun toast(
        @StringRes resId: Int,
        vararg args: Any,
    ) {
        context.showToast(string(resId, *args))
    }

    fun toast(message: String?) {
        if (message.isNullOrBlank()) return
        context.showToast(message)
    }

    fun saveTrackedItems(
        refreshTrackIds: Set<String> = emptySet(),
        emitStoreSignal: Boolean = true,
    ) {
        state.retainTrackedFirstInstallAtByTrackedItems()
        state.retainTrackedAddedAtByTrackedItems()
        state.retainTrackedModifiedAtByTrackedItems()
        val items = state.trackedItems.toList()
        val trackedFirstInstallAtByPackage = state.trackedFirstInstallAtByPackage.toMap()
        val trackedAddedAtById = state.trackedAddedAtById.toMap()
        val trackedModifiedAtById = state.trackedModifiedAtById.toMap()
        scope.launch {
            repository.saveTrackedItems(
                context = context,
                items = items,
                trackedFirstInstallAtByPackage = trackedFirstInstallAtByPackage,
                trackedAddedAtById = trackedAddedAtById,
                trackedModifiedAtById = trackedModifiedAtById,
                refreshTrackIds = refreshTrackIds,
                emitStoreSignal = emitStoreSignal,
            )
        }
    }
}
