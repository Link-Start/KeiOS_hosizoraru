package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.InstalledAppItem
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.githubTrackedDisplayTitle
import os.kei.ui.page.main.github.section.GitHubOverviewMetrics
import os.kei.ui.page.main.github.share.GitHubPendingShareImportTrack
import os.kei.ui.page.main.github.share.shareImportTrackMaxAgeMs
import java.util.Locale

@Immutable
internal data class GitHubPageContentInput(
    val trackedItems: List<GitHubTrackedApp>,
    val trackedSearch: String,
    val trackedFilterMode: GitHubTrackedFilterMode,
    val sortMode: GitHubSortMode,
    val sortDirection: GitHubSortDirection,
    val checkStates: Map<String, VersionCheckUi>,
    val appList: List<InstalledAppItem>,
    val trackedFirstInstallAtByPackage: Map<String, Long>,
    val trackedAddedAtById: Map<String, Long>,
    val trackedModifiedAtById: Map<String, Long>,
    val pendingShareImportTrack: GitHubPendingShareImportTrack?,
    val nowMillis: Long
)

internal class GitHubPageContentStateDeriver(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun build(input: GitHubPageContentInput): GitHubPageContentDerivedState {
        return withContext(dispatcher) {
            val displayTitleById = input.trackedItems.associate { item ->
                item.id to item.githubTrackedDisplayTitle(input.checkStates[item.id])
            }
            val searchedTracked = input.trackedItems.filter { item ->
                val displayTitle = displayTitleById[item.id].orEmpty()
                input.trackedSearch.isBlank() ||
                        item.owner.contains(input.trackedSearch, ignoreCase = true) ||
                        item.repo.contains(input.trackedSearch, ignoreCase = true) ||
                        item.appLabel.contains(input.trackedSearch, ignoreCase = true) ||
                        displayTitle.contains(input.trackedSearch, ignoreCase = true) ||
                        item.packageName.contains(input.trackedSearch, ignoreCase = true)
            }
            val installedPackages = input.appList
                .asSequence()
                .map { it.packageName.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
                .toSet()
            val filteredTracked = when (input.trackedFilterMode) {
                GitHubTrackedFilterMode.All -> searchedTracked
                GitHubTrackedFilterMode.GitHubRepository ->
                    searchedTracked.filter { item -> item.isGitHubRepositoryTrack() }

                GitHubTrackedFilterMode.DirectApk ->
                    searchedTracked.filter { item -> item.isDirectApkTrack() }

                GitHubTrackedFilterMode.PreReleaseTracked ->
                    searchedTracked.filter { item -> input.checkStates[item.id]?.isPreRelease == true }

                GitHubTrackedFilterMode.UpdateAvailable ->
                    searchedTracked.filter { item ->
                        val itemState = input.checkStates[item.id]
                        itemState?.hasUpdate == true || itemState?.hasPreReleaseUpdate == true
                    }

                GitHubTrackedFilterMode.Installed ->
                    searchedTracked.filter { item ->
                        item.packageName.trim().lowercase(Locale.ROOT) in installedPackages
                    }

                GitHubTrackedFilterMode.FailedChecks ->
                    searchedTracked.filter { item -> input.checkStates[item.id]?.failed == true }

                GitHubTrackedFilterMode.ActionsCheckEnabled ->
                    searchedTracked.filter { item -> item.checkActionsUpdates }
            }
            val isSortUpdatable: (GitHubTrackedApp) -> Boolean = { item ->
                item.alwaysShowLatestReleaseDownloadButton || input.checkStates[item.id]?.hasUpdate == true
            }
            val addedAtForSortNewest: (GitHubTrackedApp) -> Long = { item ->
                input.trackedAddedAtById[item.id]?.takeIf { it > 0L } ?: Long.MIN_VALUE
            }
            val addedAtForSortOldest: (GitHubTrackedApp) -> Long = { item ->
                input.trackedAddedAtById[item.id]?.takeIf { it > 0L } ?: Long.MAX_VALUE
            }
            val modifiedAtForSortNewest: (GitHubTrackedApp) -> Long = { item ->
                input.trackedModifiedAtById[item.id]?.takeIf { it > 0L }
                    ?: input.trackedAddedAtById[item.id]?.takeIf { it > 0L }
                    ?: Long.MIN_VALUE
            }
            val modifiedAtForSortOldest: (GitHubTrackedApp) -> Long = { item ->
                input.trackedModifiedAtById[item.id]?.takeIf { it > 0L }
                    ?: input.trackedAddedAtById[item.id]?.takeIf { it > 0L }
                    ?: Long.MAX_VALUE
            }
            val titleForSort: (GitHubTrackedApp) -> String = { item ->
                displayTitleById[item.id].orEmpty().lowercase(Locale.ROOT)
            }
            val sortedTracked = when (input.sortMode) {
                GitHubSortMode.Update -> when (input.sortDirection) {
                    GitHubSortDirection.Forward -> filteredTracked.sortedWith(
                        compareByDescending<GitHubTrackedApp> { isSortUpdatable(it) }
                            .thenByDescending {
                                input.checkStates[it.id]?.hasPreReleaseUpdate == true
                            }
                            .thenBy { titleForSort(it) }
                    )

                    GitHubSortDirection.Reverse -> filteredTracked.sortedWith(
                        compareBy<GitHubTrackedApp> { isSortUpdatable(it) }
                            .thenBy { input.checkStates[it.id]?.hasPreReleaseUpdate == true }
                            .thenByDescending { titleForSort(it) }
                    )
                }

                GitHubSortMode.Name -> when (input.sortDirection) {
                    GitHubSortDirection.Forward -> filteredTracked.sortedBy {
                        titleForSort(it)
                    }

                    GitHubSortDirection.Reverse -> filteredTracked.sortedByDescending {
                        titleForSort(it)
                    }
                }

                GitHubSortMode.PreRelease -> when (input.sortDirection) {
                    GitHubSortDirection.Forward -> filteredTracked.sortedWith(
                        compareByDescending<GitHubTrackedApp> {
                            input.checkStates[it.id]?.isPreRelease == true
                        }
                            .thenByDescending { isSortUpdatable(it) }
                            .thenBy { titleForSort(it) }
                    )

                    GitHubSortDirection.Reverse -> filteredTracked.sortedWith(
                        compareBy<GitHubTrackedApp> {
                            input.checkStates[it.id]?.isPreRelease == true
                        }
                            .thenBy { isSortUpdatable(it) }
                            .thenByDescending { titleForSort(it) }
                    )
                }

                GitHubSortMode.Changed -> when (input.sortDirection) {
                    GitHubSortDirection.Forward -> filteredTracked.sortedWith(
                        compareByDescending<GitHubTrackedApp> { modifiedAtForSortNewest(it) }
                            .thenBy { titleForSort(it) }
                    )

                    GitHubSortDirection.Reverse -> filteredTracked.sortedWith(
                        compareBy<GitHubTrackedApp> { modifiedAtForSortOldest(it) }
                            .thenBy { titleForSort(it) }
                    )
                }

                GitHubSortMode.Added -> when (input.sortDirection) {
                    GitHubSortDirection.Forward -> filteredTracked.sortedWith(
                        compareByDescending<GitHubTrackedApp> { addedAtForSortNewest(it) }
                            .thenBy { titleForSort(it) }
                    )

                    GitHubSortDirection.Reverse -> filteredTracked.sortedWith(
                        compareBy<GitHubTrackedApp> { addedAtForSortOldest(it) }
                            .thenBy { titleForSort(it) }
                    )
                }
            }
            val pendingShareImportRepoOverlapCount = input.pendingShareImportTrack?.let { pending ->
                input.trackedItems.count { item ->
                    item.owner.equals(pending.owner, ignoreCase = true) &&
                            item.repo.equals(pending.repo, ignoreCase = true)
                }
            } ?: 0
            val showPendingShareImportCard = input.pendingShareImportTrack?.let { pending ->
                val ageMs = (input.nowMillis - pending.armedAtMillis).coerceAtLeast(0L)
                ageMs <= shareImportTrackMaxAgeMs ||
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
