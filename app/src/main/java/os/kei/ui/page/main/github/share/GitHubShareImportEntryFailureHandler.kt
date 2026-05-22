package os.kei.ui.page.main.github.share

import android.content.Context
import os.kei.R
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubShareImportAssetPlan
import os.kei.ui.page.main.github.localizedGitHubShareImportErrorMessage

internal class GitHubShareImportEntryFailureHandler(
    private val resultWriter: GitHubShareImportResultWriter,
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
    suspend fun handleEmptyAssets(
        context: Context,
        notificationFirst: Boolean,
        plan: GitHubShareImportAssetPlan,
    ): ShareImportIncomingCoordinatorResult {
        val reason = context.getString(R.string.github_toast_share_import_no_apk)
        resultWriter.saveResultAfterClearingActiveFlow(
            GitHubShareImportResult(
                kind = GitHubShareImportResultKind.Failed,
                projectUrl = plan.parsedLink.projectUrl,
                owner = plan.parsedLink.owner,
                repo = plan.parsedLink.repo,
                message = reason,
                completedAtMillis = clock.nowMs(),
            ),
        )
        GitHubTrackStoreSignals.notifyChanged()
        notifyShareImportFailed(context, reason)
        return ShareImportIncomingCoordinatorResult(
            coordinatorResult = ShareImportCoordinatorResult.Failed(reason),
            notificationFirst = notificationFirst,
            toastResId = R.string.github_toast_share_import_no_apk,
        )
    }

    suspend fun handleIncomingFailure(
        context: Context,
        error: Throwable,
        notificationFirst: Boolean,
    ): ShareImportIncomingCoordinatorResult {
        if (error.shouldSuppressShareImportFailureToast()) {
            return ShareImportIncomingCoordinatorResult(
                coordinatorResult = ShareImportCoordinatorResult.None,
                notificationFirst = notificationFirst,
            )
        }
        val reason =
            localizedGitHubShareImportErrorMessage(
                context = context,
                rawMessage =
                    error.message?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName,
            )
        resultWriter.saveResultAfterClearingActiveFlow(
            GitHubShareImportResult(
                kind = GitHubShareImportResultKind.Failed,
                message = reason,
                completedAtMillis = clock.nowMs(),
            ),
        )
        GitHubTrackStoreSignals.notifyChanged()
        notifyShareImportFailed(context, reason)
        return ShareImportIncomingCoordinatorResult(
            coordinatorResult = ShareImportCoordinatorResult.Failed(reason),
            notificationFirst = notificationFirst,
            toastResId = R.string.github_toast_share_import_failed,
            toastMessage = reason,
        )
    }
}
