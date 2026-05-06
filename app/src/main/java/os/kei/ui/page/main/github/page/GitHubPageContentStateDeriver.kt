package os.kei.ui.page.main.github.page

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.section.GitHubOverviewMetrics
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack

private const val pendingShareImportCardVisibleWindowMs = 90_000L

internal data class GitHubPageContentInput(
    val trackedItems: List<GitHubTrackedApp>,
    val trackedSearch: String,
    val showFailedOnly: Boolean,
    val sortMode: GitHubSortMode,
    val checkStates: Map<String, VersionCheckUi>,
    val appList: List<InstalledAppItem>,
    val trackedFirstInstallAtByPackage: Map<String, Long>,
    val trackedAddedAtById: Map<String, Long>,
    val pendingShareImportTrack: GitHubPendingShareImportTrack?,
    val nowMillis: Long
)

internal class GitHubPageContentStateDeriver(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun build(input: GitHubPageContentInput): GitHubPageContentDerivedState {
        return withContext(dispatcher) {
            val searchedTracked = input.trackedItems.filter { item ->
                input.trackedSearch.isBlank() ||
                        item.owner.contains(input.trackedSearch, ignoreCase = true) ||
                        item.repo.contains(input.trackedSearch, ignoreCase = true) ||
                        item.appLabel.contains(input.trackedSearch, ignoreCase = true) ||
                        item.packageName.contains(input.trackedSearch, ignoreCase = true)
            }
            val filteredTracked = if (input.showFailedOnly) {
                searchedTracked.filter { item -> input.checkStates[item.id]?.failed == true }
            } else {
                searchedTracked
            }
            val isSortUpdatable: (GitHubTrackedApp) -> Boolean = { item ->
                item.alwaysShowLatestReleaseDownloadButton || input.checkStates[item.id]?.hasUpdate == true
            }
            val sortedTracked = when (input.sortMode) {
                GitHubSortMode.UpdateFirst -> filteredTracked.sortedWith(
                    compareByDescending<GitHubTrackedApp> { isSortUpdatable(it) }
                        .thenByDescending { input.checkStates[it.id]?.hasPreReleaseUpdate == true }
                        .thenBy { it.appLabel.lowercase() }
                )

                GitHubSortMode.NameAsc -> filteredTracked.sortedBy { it.appLabel.lowercase() }
                GitHubSortMode.PreReleaseFirst -> filteredTracked.sortedWith(
                    compareByDescending<GitHubTrackedApp> {
                        input.checkStates[it.id]?.isPreRelease == true
                    }
                        .thenByDescending { isSortUpdatable(it) }
                        .thenBy { it.appLabel.lowercase() }
                )
            }
            val pendingShareImportRepoOverlapCount = input.pendingShareImportTrack?.let { pending ->
                input.trackedItems.count { item ->
                    item.owner.equals(pending.owner, ignoreCase = true) &&
                            item.repo.equals(pending.repo, ignoreCase = true)
                }
            } ?: 0
            val showPendingShareImportCard = input.pendingShareImportTrack?.let { pending ->
                val ageMs = (input.nowMillis - pending.armedAtMillis).coerceAtLeast(0L)
                ageMs <= pendingShareImportCardVisibleWindowMs ||
                        pendingShareImportRepoOverlapCount > 0
            } ?: false
            GitHubPageContentDerivedState(
                trackedUi = GitHubPageDerivedState(
                    filteredTracked = filteredTracked,
                    sortedTracked = sortedTracked,
                    overviewMetrics = buildOverviewMetrics(input)
                ),
                appLastUpdatedAtByTrackId = buildAppLastUpdatedAtByTrackId(input),
                pendingShareImportRepoOverlapCount = pendingShareImportRepoOverlapCount,
                showPendingShareImportCard = showPendingShareImportCard
            )
        }
    }

    private fun buildOverviewMetrics(input: GitHubPageContentInput): GitHubOverviewMetrics {
        val stableUpdateCount =
            input.trackedItems.count { input.checkStates[it.id]?.hasUpdate == true }
        val preReleaseCount =
            input.trackedItems.count { input.checkStates[it.id]?.isPreRelease == true }
        val preReleaseUpdateCount =
            input.trackedItems.count { input.checkStates[it.id]?.hasPreReleaseUpdate == true }
        val totalUpdatableCount = input.trackedItems.count {
            val itemState = input.checkStates[it.id]
            itemState?.hasUpdate == true || itemState?.hasPreReleaseUpdate == true
        }
        val failedCount = input.trackedItems.count { input.checkStates[it.id]?.failed == true }
        val stableLatestCount = input.trackedItems.count {
            val itemState = input.checkStates[it.id]
            itemState?.hasUpdate == false && itemState.isPreRelease.not()
        }
        return GitHubOverviewMetrics(
            trackedCount = input.trackedItems.size,
            stableUpdateCount = stableUpdateCount,
            totalUpdatableCount = totalUpdatableCount,
            stableLatestCount = stableLatestCount,
            preReleaseCount = preReleaseCount,
            preReleaseUpdateCount = preReleaseUpdateCount,
            failedCount = failedCount
        )
    }

    private fun buildAppLastUpdatedAtByTrackId(
        input: GitHubPageContentInput
    ): Map<String, Long> {
        val appUpdatedAtByPackage = buildMap {
            input.trackedFirstInstallAtByPackage.forEach { (packageName, firstInstallAtMillis) ->
                if (packageName.isNotBlank() && firstInstallAtMillis > 0L) {
                    put(packageName, firstInstallAtMillis)
                }
            }
            input.appList
                .filter { it.packageName.isNotBlank() && it.lastUpdateTimeMs > 0L }
                .forEach { put(it.packageName, it.lastUpdateTimeMs) }
        }
        return buildMap {
            input.trackedItems.forEach { item ->
                val byPackage = appUpdatedAtByPackage[item.packageName]
                val byTrackId = input.trackedAddedAtById[item.id]
                val updatedAt = byPackage?.takeIf { it > 0L } ?: byTrackId?.takeIf { it > 0L }
                if (updatedAt != null) {
                    put(item.id, updatedAt)
                }
            }
        }
    }
}
