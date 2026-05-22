package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStore
import kotlin.time.Duration.Companion.milliseconds

internal data class GitHubShareImportStoredFlowSnapshot(
    val pendingTrack: GitHubPendingShareImportTrackRecord? = null,
    val preview: GitHubShareImportPreview? = null,
    val managedInstallProgress: GitHubShareImportManagedInstallProgress? = null,
    val attachCandidate: GitHubPendingShareImportAttachCandidate? = null,
) {
    val phase: GitHubShareImportPhase
        get() =
            when {
                pendingTrack != null -> GitHubShareImportPhase.WaitingInstall
                managedInstallProgress != null -> managedInstallProgress.phase
                preview != null -> GitHubShareImportPhase.AssetReady
                attachCandidate != null -> GitHubShareImportPhase.InstallDetected
                else -> GitHubShareImportPhase.Idle
            }
}

internal sealed interface GitHubShareImportRestoreResult {
    data class Snapshot(
        val snapshot: GitHubShareImportStoredFlowSnapshot,
    ) : GitHubShareImportRestoreResult

    data class Coordinator(
        val result: ShareImportCoordinatorResult,
    ) : GitHubShareImportRestoreResult
}

internal class GitHubShareImportWindowCoordinator(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
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

    suspend fun restoreActiveFlow(context: Context): GitHubShareImportRestoreResult {
        val appContext = context.applicationContext
        val pending =
            withContext(ioDispatcher) {
                GitHubTrackStore.loadPendingShareImportTrack()
            }
        if (pending == null) {
            val snapshot = loadStoredSnapshot()
            snapshot.preview?.let { preview ->
                if (snapshot.managedInstallProgress == null) {
                    notifyShareImportAssetReady(appContext, preview)
                }
            }
            snapshot.attachCandidate?.let { candidate ->
                notifyShareImportInstallDetected(appContext, candidate)
            }
            return GitHubShareImportRestoreResult.Snapshot(snapshot)
        }
        val age = (clock.nowMs() - pending.armedAtMillis).coerceAtLeast(0L)
        if (age > shareImportTrackMaxAgeMs) {
            return GitHubShareImportRestoreResult.Coordinator(
                GitHubShareImportFlowCoordinator.cancelActiveFlow(appContext),
            )
        }
        withContext(ioDispatcher) {
            GitHubShareImportFlowStore.clearActiveFlow()
        }
        return GitHubShareImportRestoreResult.Coordinator(
            GitHubShareImportFlowCoordinator.refreshPendingInstall(appContext),
        )
    }

    suspend fun awaitPendingResolution(
        context: Context,
        armedAtMillis: Long,
    ): ShareImportCoordinatorResult {
        val appContext = context.applicationContext
        var attempts = 0
        while (true) {
            val pending =
                withContext(ioDispatcher) {
                    GitHubTrackStore.loadPendingShareImportTrack()
                } ?: return ShareImportCoordinatorResult.None
            if (pending.armedAtMillis != armedAtMillis) return ShareImportCoordinatorResult.None
            val age = (clock.nowMs() - pending.armedAtMillis).coerceAtLeast(0L)
            if (age > shareImportTrackMaxAgeMs) {
                return GitHubShareImportFlowCoordinator.cancelActiveFlow(appContext)
            }
            when (val result = GitHubShareImportFlowCoordinator.refreshPendingInstall(appContext)) {
                is ShareImportCoordinatorResult.Pending,
                ShareImportCoordinatorResult.None,
                -> Unit

                else -> return result
            }
            val remainingMs = (shareImportTrackMaxAgeMs - age).coerceAtLeast(0L)
            val delayMs =
                when {
                    attempts < FAST_PENDING_POLL_ATTEMPTS -> FAST_PENDING_POLL_MS
                    attempts < MEDIUM_PENDING_POLL_ATTEMPTS -> MEDIUM_PENDING_POLL_MS
                    else -> remainingMs.coerceAtMost(SLOW_PENDING_POLL_MS)
                }.coerceAtMost(remainingMs).coerceAtLeast(1_000L)
            attempts += 1
            delay(delayMs.milliseconds)
        }
    }

    suspend fun hasAttachDuplicate(candidate: GitHubPendingShareImportAttachCandidate): Boolean {
        val candidateId = "${candidate.owner}/${candidate.repo}|${candidate.packageName}"
        return withContext(ioDispatcher) {
            GitHubTrackStore.load().any { it.id == candidateId }
        }
    }

    private companion object {
        private const val FAST_PENDING_POLL_MS = 2_500L
        private const val MEDIUM_PENDING_POLL_MS = 10_000L
        private const val SLOW_PENDING_POLL_MS = 60_000L
        private const val FAST_PENDING_POLL_ATTEMPTS = 4
        private const val MEDIUM_PENDING_POLL_ATTEMPTS = 10
    }
}
