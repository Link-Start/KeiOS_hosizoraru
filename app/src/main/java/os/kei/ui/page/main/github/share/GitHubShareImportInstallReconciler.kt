package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.core.system.AppPackageChangedEvent
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubTrackStore

internal class GitHubShareImportInstallReconciler(
    private val context: Context
) {
    suspend fun reconcileRecentInstall(
        pendingTrack: GitHubPendingShareImportTrackRecord
    ): ShareImportInstallReconcileResult {
        return withContext(Dispatchers.IO) {
            val packageSnapshot = findRecentInstalledCandidateForPendingTrack(context, pendingTrack)
                ?: return@withContext ShareImportInstallReconcileResult.None
            packageSnapshot.toReconcileResult(
                pendingTrack = pendingTrack,
                eventAction = "reconciled"
            )
        }
    }

    suspend fun reconcilePackageEvent(
        pendingTrack: GitHubPendingShareImportTrackRecord,
        event: AppPackageChangedEvent,
        currentCandidate: GitHubPendingShareImportAttachCandidate?
    ): ShareImportInstallReconcileResult {
        val packageName = event.packageName.trim()
        if (packageName.isBlank()) return ShareImportInstallReconcileResult.None
        val expectedPackageName = pendingTrack.packageName.trim()
        if (expectedPackageName.isNotBlank() && packageName != expectedPackageName) {
            return ShareImportInstallReconcileResult.None
        }
        val pendingAge = (event.atMillis - pendingTrack.armedAtMillis).coerceAtLeast(0L)
        if (pendingAge > shareImportTrackMaxAgeMs) {
            return ShareImportInstallReconcileResult.Expired
        }
        if (event.action !in shareImportAttachActions) {
            return ShareImportInstallReconcileResult.None
        }
        return withContext(Dispatchers.IO) {
            val packageSnapshot = loadInstalledPackageSnapshot(context, packageName)
                ?: return@withContext ShareImportInstallReconcileResult.None
            if (
                !isShareImportAttachEventValid(
                    event = event,
                    armedAtMillis = pendingTrack.armedAtMillis,
                    packageLastUpdateTimeMs = packageSnapshot.lastUpdateTimeMs
                )
            ) {
                return@withContext ShareImportInstallReconcileResult.None
            }
            if (
                currentCandidate != null &&
                currentCandidate.packageName == packageName &&
                currentCandidate.owner == pendingTrack.owner &&
                currentCandidate.repo == pendingTrack.repo
            ) {
                return@withContext ShareImportInstallReconcileResult.None
            }
            packageSnapshot.toReconcileResult(
                pendingTrack = pendingTrack,
                eventAction = event.action,
                detectedAtMillis = event.atMillis
            )
        }
    }

    private fun ShareImportInstalledPackageSnapshot.toReconcileResult(
        pendingTrack: GitHubPendingShareImportTrackRecord,
        eventAction: String,
        detectedAtMillis: Long = System.currentTimeMillis()
    ): ShareImportInstallReconcileResult {
        val candidate = pendingTrack.toAttachCandidate(
            packageSnapshot = this,
            eventAction = eventAction,
            detectedAtMillis = detectedAtMillis
        )
        return if (candidate.hasDuplicateTrackedItem()) {
            ShareImportInstallReconcileResult.Duplicate(
                candidate.copy(eventAction = "duplicate")
            )
        } else {
            ShareImportInstallReconcileResult.Detected(candidate)
        }
    }

    private fun GitHubPendingShareImportAttachCandidate.hasDuplicateTrackedItem(): Boolean {
        val duplicateId = "${owner}/${repo}|${packageName}"
        return GitHubTrackStore.load().any { it.id == duplicateId }
    }
}

internal sealed interface ShareImportInstallReconcileResult {
    data object None : ShareImportInstallReconcileResult
    data object Expired : ShareImportInstallReconcileResult
    data class Duplicate(
        val candidate: GitHubPendingShareImportAttachCandidate
    ) : ShareImportInstallReconcileResult

    data class Detected(
        val candidate: GitHubPendingShareImportAttachCandidate
    ) : ShareImportInstallReconcileResult
}
