package os.kei.ui.page.main.github.page.action

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.ui.page.main.github.page.GitHubActionsPageRepository
import os.kei.ui.page.main.github.page.GitHubPageRepository
import os.kei.ui.page.main.github.page.GitHubPageState
import os.kei.ui.page.main.github.query.DownloaderOption

internal class GitHubPageActionEnvironment(
    val context: Context,
    val scope: CoroutineScope,
    val state: GitHubPageState,
    val repository: GitHubPageRepository,
    val actionsRepository: GitHubActionsPageRepository = GitHubActionsPageRepository(),
    val systemDmOption: DownloaderOption,
    val openLinkFailureMessage: String
) {
    fun string(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }

    fun toast(@StringRes resId: Int, vararg args: Any) {
        Toast.makeText(context, string(resId, *args), Toast.LENGTH_SHORT).show()
    }

    fun toast(message: String?) {
        if (message.isNullOrBlank()) return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun saveTrackedItems(
        refreshTrackIds: Set<String> = emptySet(),
        emitStoreSignal: Boolean = true
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
                emitStoreSignal = emitStoreSignal
            )
        }
    }
}
