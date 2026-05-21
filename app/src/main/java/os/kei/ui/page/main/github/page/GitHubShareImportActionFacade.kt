package os.kei.ui.page.main.github.page

import android.content.Intent
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.page.action.GitHubPageActionEnvironment
import os.kei.ui.page.main.github.share.GitHubShareImportActivity
import os.kei.ui.page.main.github.share.GitHubShareImportResult
import os.kei.ui.page.main.github.share.GitHubShareImportResultKind
import os.kei.ui.page.main.github.share.toShareImportResult

internal class GitHubShareImportActionFacade(
    private val env: GitHubPageActionEnvironment,
) {
    private val pendingTrackMaxAgeMs = 25 * 60 * 1000L

    suspend fun syncActiveFlowFromStore() {
        val flow = env.repository.loadActiveShareImportFlow()
        env.state.pendingShareImportPreview = flow.preview
        env.state.pendingShareImportTrack = flow.pendingTrack
        env.state.pendingShareImportAttachCandidate = flow.attachCandidate
        env.state.pendingShareImportResult = flow.result
    }

    fun cancelPendingTrack(showToast: Boolean = true) {
        val hadPending = env.state.pendingShareImportTrack != null
        val cancelledResult = buildCancelledResult()
        clearPendingTrack(cancelledResult)
        if (hadPending && showToast) {
            env.toast(R.string.github_toast_share_import_pending_cancelled)
        }
    }

    fun openFlow() {
        val intent =
            Intent(env.context, GitHubShareImportActivity::class.java).apply {
                action = GitHubShareImportActivity.ACTION_RESUME_SHARE_IMPORT
                putExtra(GitHubShareImportActivity.EXTRA_FORCE_SHEET, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        env.context.startActivity(intent)
    }

    fun cancelActiveFlow(showToast: Boolean = true) {
        val hadActiveFlow =
            env.state.pendingShareImportPreview != null ||
                env.state.pendingShareImportTrack != null ||
                env.state.pendingShareImportAttachCandidate != null
        val cancelledResult = buildCancelledResult()
        env.state.pendingShareImportPreview = null
        env.state.pendingShareImportTrack = null
        env.state.pendingShareImportAttachCandidate = null
        env.state.pendingShareImportResult = cancelledResult
        env.scope.launch {
            env.repository.clearActiveShareImportFlow()
            if (cancelledResult != null) {
                env.repository.saveShareImportResult(cancelledResult)
            }
            GitHubShareImportNotificationHelper.notifyCancelled(env.context)
        }
        if (hadActiveFlow && showToast) {
            env.toast(R.string.github_toast_share_import_pending_cancelled)
        }
    }

    fun focusResult() {
        val result = env.state.pendingShareImportResult ?: return
        env.state.trackedFilterMode = GitHubTrackedFilterMode.All
        GitHubPageUiStateStore.setTrackedFilterMode(GitHubTrackedFilterMode.All)
        val query =
            result.appDisplayLabel
                .ifBlank { result.packageName }
                .ifBlank { result.repo }
                .ifBlank { result.owner }
        if (query.isNotBlank()) {
            env.state.trackedSearch = query
        }
    }

    fun dismissResult(showToast: Boolean = false) {
        val hadResult = env.state.pendingShareImportResult != null
        env.state.pendingShareImportResult = null
        env.scope.launch {
            env.repository.clearShareImportResult()
            GitHubShareImportNotificationHelper.cancel(env.context)
        }
        if (hadResult && showToast) {
            env.toast(R.string.common_mark_read)
        }
    }

    fun trimExpiredPendingTrack(nowMillis: Long) {
        val pending = env.state.pendingShareImportTrack ?: return
        val age = (nowMillis - pending.armedAtMillis).coerceAtLeast(0L)
        if (age <= pendingTrackMaxAgeMs) return
        clearPendingTrack(
            pending.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = env.string(R.string.github_share_import_notify_content_cancelled),
            ),
        )
    }

    private fun clearPendingTrack(cancelledResult: GitHubShareImportResult? = null) {
        env.state.pendingShareImportTrack = null
        env.state.pendingShareImportResult = cancelledResult
        env.scope.launch {
            env.repository.clearPendingShareImportTrack()
            if (cancelledResult != null) {
                env.repository.saveShareImportResult(cancelledResult)
            }
            GitHubShareImportNotificationHelper.notifyCancelled(env.context)
        }
    }

    private fun buildCancelledResult(): GitHubShareImportResult? {
        val message = env.string(R.string.github_share_import_notify_content_cancelled)
        env.state.pendingShareImportAttachCandidate?.let { candidate ->
            return candidate.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = message,
            )
        }
        env.state.pendingShareImportTrack?.let { pending ->
            return pending.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = message,
            )
        }
        env.state.pendingShareImportPreview?.let { preview ->
            return preview.toShareImportResult(
                kind = GitHubShareImportResultKind.Cancelled,
                message = message,
            )
        }
        return null
    }
}
