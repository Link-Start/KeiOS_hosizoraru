package os.kei.ui.page.main.github.sheet

import os.kei.R
import os.kei.feature.github.model.InstalledAppItem
import java.util.Locale

internal enum class GitHubTrackAppPickerSortMode(
    val labelRes: Int,
    val storageId: String
) {
    Name(R.string.github_track_sheet_app_sort_name, "name"),
    LastUpdated(R.string.github_track_sheet_app_sort_last_updated, "last_updated"),
    RecentlyInstalled(
        R.string.github_track_sheet_app_sort_recently_installed,
        "recently_installed"
    ),
    InstallSource(R.string.github_track_sheet_app_sort_install_source, "install_source"),
    Package(R.string.github_track_sheet_app_sort_package, "package");

    companion object {
        fun fromStorageId(storageId: String): GitHubTrackAppPickerSortMode {
            return entries.firstOrNull { it.storageId == storageId } ?: Name
        }
    }
}

internal enum class GitHubTrackAppPickerSortDirection(
    val labelRes: Int,
    val storageId: String
) {
    Ascending(R.string.github_track_sheet_app_sort_direction_ascending, "ascending"),
    Descending(R.string.github_track_sheet_app_sort_direction_descending, "descending");

    companion object {
        fun fromStorageId(storageId: String): GitHubTrackAppPickerSortDirection {
            return entries.firstOrNull { it.storageId == storageId } ?: Ascending
        }
    }
}

internal fun GitHubTrackAppPickerSortMode.isTimeSort(): Boolean {
    return this == GitHubTrackAppPickerSortMode.LastUpdated ||
            this == GitHubTrackAppPickerSortMode.RecentlyInstalled
}

internal fun filterAndSortGitHubTrackAppCandidates(
    apps: List<InstalledAppItem>,
    query: String,
    includeUserApps: Boolean,
    includeSystemApps: Boolean,
    includeTrackedApps: Boolean,
    trackedPackageNames: Set<String>,
    pinnedPackageNames: Set<String>,
    sortMode: GitHubTrackAppPickerSortMode,
    sortDirection: GitHubTrackAppPickerSortDirection
): List<InstalledAppItem> {
    val normalizedTrackedPackages = trackedPackageNames
        .mapNotNullTo(LinkedHashSet()) { it.normalizedGitHubTrackAppPackageNameOrNull() }
    val normalizedPinnedPackages = pinnedPackageNames
        .mapNotNullTo(LinkedHashSet()) { it.normalizedGitHubTrackAppPackageNameOrNull() }
    val trimmedQuery = query.trim()
    return apps
        .asSequence()
        .filter { app -> app.matchesAppPickerQuery(trimmedQuery) }
        .filter { app ->
            app.matchesAppPickerScope(
                includeUserApps = includeUserApps,
                includeSystemApps = includeSystemApps,
                includeTrackedApps = includeTrackedApps,
                trackedPackageNames = normalizedTrackedPackages,
                pinnedPackageNames = normalizedPinnedPackages
            )
        }
        .sortedWith(sortMode.comparator(sortDirection))
        .toList()
}

internal fun String.normalizedGitHubTrackAppPackageNameOrNull(): String? {
    val normalized = trim().lowercase(Locale.ROOT)
    return normalized.ifBlank { null }
}

private fun InstalledAppItem.matchesAppPickerQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return label.contains(query, ignoreCase = true) ||
            packageName.contains(query, ignoreCase = true) ||
            installSourceLabel.contains(query, ignoreCase = true) ||
            installSourcePackageName.contains(query, ignoreCase = true)
}

private fun InstalledAppItem.matchesAppPickerScope(
    includeUserApps: Boolean,
    includeSystemApps: Boolean,
    includeTrackedApps: Boolean,
    trackedPackageNames: Set<String>,
    pinnedPackageNames: Set<String>
): Boolean {
    val normalizedPackageName = packageName.normalizedGitHubTrackAppPackageNameOrNull()
    if (normalizedPackageName != null && normalizedPackageName in pinnedPackageNames) {
        return true
    }
    val matchesType = when {
        isSystemApp -> includeSystemApps
        else -> includeUserApps
    }
    if (!matchesType) return false
    val alreadyTracked = normalizedPackageName != null &&
            normalizedPackageName in trackedPackageNames
    return includeTrackedApps || !alreadyTracked
}

private fun GitHubTrackAppPickerSortMode.comparator(
    direction: GitHubTrackAppPickerSortDirection
): Comparator<InstalledAppItem> {
    val baseComparator = when (this) {
        GitHubTrackAppPickerSortMode.Name ->
            compareBy<InstalledAppItem> { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName.lowercase(Locale.ROOT) }

        GitHubTrackAppPickerSortMode.LastUpdated ->
            compareBy<InstalledAppItem> { it.lastUpdateTimeMs }
                .thenBy { it.label.lowercase(Locale.ROOT) }

        GitHubTrackAppPickerSortMode.RecentlyInstalled ->
            compareBy<InstalledAppItem> { it.firstInstallTimeMs }
                .thenBy { it.label.lowercase(Locale.ROOT) }

        GitHubTrackAppPickerSortMode.InstallSource ->
            compareBy<InstalledAppItem> { it.installSourceSortKey().isBlank() }
                .thenBy { it.installSourceSortKey() }
                .thenBy { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName.lowercase(Locale.ROOT) }

        GitHubTrackAppPickerSortMode.Package ->
            compareBy<InstalledAppItem> { it.packageName.lowercase(Locale.ROOT) }
                .thenBy { it.label.lowercase(Locale.ROOT) }
    }
    return when (direction) {
        GitHubTrackAppPickerSortDirection.Ascending -> baseComparator
        GitHubTrackAppPickerSortDirection.Descending -> baseComparator.reversed()
    }
}

private fun InstalledAppItem.installSourceSortKey(): String {
    return installSourceLabel
        .ifBlank { installSourcePackageName }
        .lowercase(Locale.ROOT)
}
