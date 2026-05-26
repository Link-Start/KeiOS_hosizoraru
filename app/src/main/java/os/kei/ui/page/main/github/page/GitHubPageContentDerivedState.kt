package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Immutable

@Immutable
internal data class GitHubPageContentDerivedState(
    val trackedUi: GitHubPageDerivedState = GitHubPageDerivedState(),
    val appLastUpdatedAtByTrackId: Map<String, Long> = emptyMap(),
    val installedAppLabelsByPackage: Map<String, String> = emptyMap(),
    val pendingShareImportRepoOverlapCount: Int = 0,
    val showPendingShareImportCard: Boolean = false,
    val pendingShareImportNowMillis: Long = 0L,
    val relativeTimeNowMillis: Long = 0L,
    val trackedItemIdKey: String = "",
    val sortedTrackIds: List<String> = emptyList(),
    val hasKeiOsSelfTrack: Boolean = false
)
