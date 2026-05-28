package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.withContext
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.domain.GitHubReleaseCheckService
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedLocalAppType
import os.kei.ui.page.main.github.state.toCacheEntry
import os.kei.ui.page.main.github.state.toUi

internal sealed interface ShareImportAttachResult {
    data class Added(
        val appLabel: String,
    ) : ShareImportAttachResult

    data object Duplicate : ShareImportAttachResult

    data class Failed(
        val message: String,
    ) : ShareImportAttachResult
}

internal suspend fun attachCandidateToTracked(
    context: Context,
    candidate: GitHubPendingShareImportAttachCandidate,
    prefetchLatestCheck: Boolean = true,
    clock: GitHubShareImportClock = GitHubSystemShareImportClock,
): ShareImportAttachResult =
    withContext(AppDispatchers.githubNetwork) {
        val trackedItems = GitHubTrackStore.load().toMutableList()
        val candidateId = "${candidate.owner}/${candidate.repo}|${candidate.packageName}"
        if (trackedItems.any { it.id == candidateId }) {
            return@withContext ShareImportAttachResult.Duplicate
        }

        val trackedItem = candidate.toTrackedApp(context)
        trackedItems.add(trackedItem)
        GitHubTrackStore.save(trackedItems)
        saveTrackedFirstInstallAtFallback(candidate)
        saveTrackedAddedAtFallback(trackedItem.id, candidate.detectedAtMillis)
        saveTrackedModifiedAtFallback(trackedItem.id, candidate.detectedAtMillis)
        AppBackgroundScheduler.scheduleGitHubRefresh(context)

        if (prefetchLatestCheck) {
            prefetchAttachedTrackLatestCheck(
                context = context,
                trackedItem = trackedItem,
                clock = clock,
            )
        }
        GitHubTrackStoreSignals.requestTrackRefresh(trackedItem.id)

        ShareImportAttachResult.Added(trackedItem.appLabel.ifBlank { trackedItem.packageName })
    }

private fun GitHubPendingShareImportAttachCandidate.toTrackedApp(context: Context): GitHubTrackedApp =
    GitHubTrackedApp(
        repoUrl = projectUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = appLabel.ifBlank { packageName },
        localAppType =
            GitHubVersionUtils
                .localVersionInfoOrNull(
                    context = context,
                    packageName = packageName,
                )?.let { info ->
                    GitHubTrackedLocalAppType.fromSystemFlag(info.isSystemApp)
                } ?: GitHubTrackedLocalAppType.Unknown,
    )

private suspend fun prefetchAttachedTrackLatestCheck(
    context: Context,
    trackedItem: GitHubTrackedApp,
    clock: GitHubShareImportClock,
) {
    runCatching {
        val nowMs = clock.nowMs()
        val refreshedUi =
            GitHubReleaseCheckService
                .evaluateTrackedApp(context, trackedItem)
                .toUi()
                .copy(checkedAtMillis = nowMs)
        val (cache, _) = GitHubTrackStore.loadCheckCache()
        val updatedCache =
            cache.toMutableMap().apply {
                put(trackedItem.id, refreshedUi.toCacheEntry())
            }
        GitHubTrackStore.saveCheckCache(updatedCache, nowMs)
        GitHubTrackStoreSignals.notifyChanged(nowMs)
    }
}

internal fun saveTrackedFirstInstallAtFallback(candidate: GitHubPendingShareImportAttachCandidate) {
    val packageName = candidate.packageName.trim()
    if (packageName.isBlank()) return
    val firstInstallAtMillis =
        candidate.firstInstallTimeMs
            .takeIf { it > 0L }
            ?: candidate.detectedAtMillis
    if (firstInstallAtMillis <= 0L) return

    val existing = GitHubTrackStore.loadTrackedFirstInstallAtByPackage().toMutableMap()
    val current = existing[packageName]
    if (current == null || current <= 0L || firstInstallAtMillis < current) {
        existing[packageName] = firstInstallAtMillis
        GitHubTrackStore.saveTrackedFirstInstallAtByPackage(existing)
    }
}

internal fun saveTrackedAddedAtFallback(
    trackId: String,
    detectedAtMillis: Long,
    clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
    val normalizedTrackId = trackId.trim()
    if (normalizedTrackId.isBlank()) return
    val addedAtMillis = detectedAtMillis.takeIf { it > 0L } ?: clock.nowMs()
    val existing = GitHubTrackStore.loadTrackedAddedAtById().toMutableMap()
    val current = existing[normalizedTrackId]
    if (current == null || current <= 0L || addedAtMillis < current) {
        existing[normalizedTrackId] = addedAtMillis
        GitHubTrackStore.saveTrackedAddedAtById(existing)
    }
}

internal fun saveTrackedModifiedAtFallback(
    trackId: String,
    detectedAtMillis: Long,
    clock: GitHubShareImportClock = GitHubSystemShareImportClock,
) {
    val normalizedTrackId = trackId.trim()
    if (normalizedTrackId.isBlank()) return
    val modifiedAtMillis = detectedAtMillis.takeIf { it > 0L } ?: clock.nowMs()
    val existing = GitHubTrackStore.loadTrackedModifiedAtById().toMutableMap()
    existing[normalizedTrackId] = modifiedAtMillis
    GitHubTrackStore.saveTrackedModifiedAtById(existing)
}
