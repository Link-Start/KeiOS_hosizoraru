package os.kei.feature.github.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import os.kei.R
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED
import os.kei.feature.github.data.local.GitHubPendingShareImportAttachCandidateRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportPreviewRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubShareImportResultRecord
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import kotlin.concurrent.thread

class GitHubShareImportActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in supportedActions) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        thread(name = "github-share-import-action") {
            try {
                when (action) {
                    ACTION_CANCEL_SHARE_IMPORT -> {
                        val cancelledResult = buildCancelledResult(appContext)
                        GitHubShareImportFlowStore.clearActiveFlow()
                        GitHubTrackStore.savePendingShareImportTrack(null)
                        if (cancelledResult != null) {
                            GitHubShareImportFlowStore.saveActiveResult(cancelledResult)
                        }
                        GitHubTrackStoreSignals.notifyChanged()
                        GitHubShareImportNotificationHelper.notifyCancelled(appContext)
                    }

                    ACTION_MARK_READ_SHARE_IMPORT -> {
                        GitHubShareImportFlowStore.clearActiveResult()
                        GitHubTrackStoreSignals.notifyChanged()
                        GitHubShareImportNotificationHelper.cancel(appContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildCancelledResult(context: Context): GitHubShareImportResultRecord? {
        GitHubShareImportFlowStore.loadActiveAttachCandidate()?.let { candidate ->
            return candidate.toCancelledResult(context)
        }
        GitHubTrackStore.loadPendingShareImportTrack()?.let { pending ->
            return pending.toCancelledResult(context)
        }
        GitHubShareImportFlowStore.loadActivePreview()?.let { preview ->
            return preview.toCancelledResult(context)
        }
        return null
    }

    private fun GitHubPendingShareImportAttachCandidateRecord.toCancelledResult(
        context: Context
    ): GitHubShareImportResultRecord {
        return GitHubShareImportResultRecord(
            status = GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED,
            projectUrl = projectUrl,
            owner = owner,
            repo = repo,
            appLabel = appLabel,
            packageName = packageName,
            targetDisplayName = appLabel.ifBlank { packageName },
            message = context.getString(R.string.github_share_import_notify_content_cancelled)
        )
    }

    private fun GitHubPendingShareImportTrackRecord.toCancelledResult(
        context: Context
    ): GitHubShareImportResultRecord {
        return GitHubShareImportResultRecord(
            status = GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED,
            projectUrl = projectUrl,
            owner = owner,
            repo = repo,
            packageName = packageName,
            targetDisplayName = targetDisplayName,
            message = context.getString(R.string.github_share_import_notify_content_cancelled)
        )
    }

    private fun GitHubPendingShareImportPreviewRecord.toCancelledResult(
        context: Context
    ): GitHubShareImportResultRecord {
        return GitHubShareImportResultRecord(
            status = GITHUB_SHARE_IMPORT_RESULT_STATUS_CANCELLED,
            projectUrl = projectUrl,
            owner = owner,
            repo = repo,
            targetDisplayName = targetDisplayName,
            message = context.getString(R.string.github_share_import_notify_content_cancelled)
        )
    }

    companion object {
        const val ACTION_CANCEL_SHARE_IMPORT = "os.kei.github.share_import.action.CANCEL"
        const val ACTION_MARK_READ_SHARE_IMPORT = "os.kei.github.share_import.action.MARK_READ"

        private val supportedActions = setOf(
            ACTION_CANCEL_SHARE_IMPORT,
            ACTION_MARK_READ_SHARE_IMPORT
        )
    }
}
