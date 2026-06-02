package os.kei.ui.page.main.github.share

import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore

internal class GitHubShareImportResultWriter(
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
    suspend fun saveAttachCandidateResult(
        candidate: GitHubPendingShareImportAttachCandidate,
        kind: GitHubShareImportResultKind,
        appLabelOverride: String = "",
        message: String = "",
        clearActiveAttachCandidate: Boolean = false,
        clearActiveFlow: Boolean = false,
        clearPendingTrack: Boolean = false,
    ) {
        withContext(AppDispatchers.githubLocal) {
            if (clearPendingTrack) {
                GitHubTrackStore.savePendingShareImportTrack(null)
            }
            if (clearActiveFlow) {
                GitHubShareImportFlowStore.clearActiveFlow()
            } else if (clearActiveAttachCandidate) {
                GitHubShareImportFlowStore.clearActiveAttachCandidate()
            }
            GitHubShareImportFlowStore.saveActiveResult(
                candidate
                    .toShareImportResult(
                        kind = kind,
                        appLabelOverride = appLabelOverride,
                        message = message,
                        completedAtMillis = clock.nowMs(),
                    ).toRecord(),
            )
        }
    }

    suspend fun savePendingTrackResultAndClearFlow(
        pending: GitHubPendingShareImportTrackRecord,
        kind: GitHubShareImportResultKind,
        message: String = "",
    ): GitHubShareImportResult {
        val result =
            pending.toShareImportResult(
                kind = kind,
                message = message,
                completedAtMillis = clock.nowMs(),
            )
        withContext(AppDispatchers.githubLocal) {
            GitHubTrackStore.savePendingShareImportTrack(null)
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
        }
        return result
    }

    suspend fun saveResultAfterClearingActiveFlow(result: GitHubShareImportResult) {
        withContext(AppDispatchers.githubLocal) {
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
        }
    }

    suspend fun saveResultAndClearActiveFlow(result: GitHubShareImportResult?) {
        withContext(AppDispatchers.githubLocal) {
            GitHubShareImportFlowStore.clearActiveFlow()
            GitHubTrackStore.savePendingShareImportTrack(null)
            if (result != null) {
                GitHubShareImportFlowStore.saveActiveResult(result.toRecord())
            }
        }
    }
}
