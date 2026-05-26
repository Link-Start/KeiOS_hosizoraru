package os.kei.ui.page.main.github.share

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubLookupConfig

internal class GitHubShareImportWindowRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubLocal,
) {
    suspend fun resolveIncomingShare(sharedText: String): GitHubShareImportResolvedIncomingShare =
        withContext(ioDispatcher) {
            val lookupConfig = GitHubTrackStore.loadLookupConfig()
            GitHubShareImportResolvedIncomingShare(
                sharedText = sharedText.trim(),
                lookupConfig = lookupConfig,
                displayState =
                    GitHubShareImportActivityLaunchPolicy.forIncomingShare(
                        sharedText = sharedText,
                        lookupConfig = lookupConfig,
                    ),
            )
        }

    suspend fun loadPendingTrack(): GitHubPendingShareImportTrackRecord? =
        withContext(ioDispatcher) {
            GitHubTrackStore.loadPendingShareImportTrack()
        }

    suspend fun loadStoredSnapshot(): GitHubShareImportStoredFlowSnapshot =
        withContext(ioDispatcher) {
            val pendingTrack = GitHubTrackStore.loadPendingShareImportTrack()
            val preview =
                GitHubShareImportFlowStore
                    .loadActivePreview()
                    ?.toShareImportPreview()
            val managedInstallProgress =
                GitHubShareImportFlowStore
                    .loadActiveManagedInstall()
                    ?.toManagedInstallProgress()
            val attachCandidate =
                GitHubShareImportFlowStore
                    .loadActiveAttachCandidate()
                    ?.toShareImportAttachCandidate()
            GitHubShareImportStoredFlowSnapshot(
                pendingTrack = pendingTrack,
                preview = if (pendingTrack == null) preview else null,
                managedInstallProgress = if (pendingTrack == null) managedInstallProgress else null,
                attachCandidate =
                    if (pendingTrack == null && preview == null) {
                        attachCandidate
                    } else {
                        null
                    },
            )
        }

    suspend fun clearActiveFlow() {
        withContext(ioDispatcher) {
            GitHubShareImportFlowStore.clearActiveFlow()
        }
    }

    suspend fun hasAttachDuplicate(candidate: GitHubPendingShareImportAttachCandidate): Boolean {
        val candidateId = "${candidate.owner}/${candidate.repo}|${candidate.packageName}"
        return withContext(ioDispatcher) {
            GitHubTrackStore.load().any { it.id == candidateId }
        }
    }
}

internal data class GitHubShareImportResolvedIncomingShare(
    val sharedText: String,
    val lookupConfig: GitHubLookupConfig,
    val displayState: GitHubShareImportActivityDisplayState,
)
