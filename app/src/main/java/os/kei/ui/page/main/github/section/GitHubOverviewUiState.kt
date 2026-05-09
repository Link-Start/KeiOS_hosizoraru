package os.kei.ui.page.main.github.section

import androidx.annotation.StringRes
import com.tencent.mmkv.MMKV
import os.kei.R

internal data class GitHubOverviewUiState(
    val expanded: Boolean = true,
    val visibleEntries: Set<GitHubOverviewEntry> = defaultGitHubOverviewEntries()
)

internal enum class GitHubOverviewEntry(
    val storageId: String,
    @param:StringRes val labelRes: Int
) {
    Strategy("strategy", R.string.github_overview_label_strategy),
    Api("api", R.string.github_overview_label_api),
    Tracked("tracked", R.string.github_overview_label_tracked),
    StableUpdate("stable_update", R.string.github_overview_label_stable_update),
    StableLatest("stable_latest", R.string.github_overview_label_stable_latest),
    PreReleaseTracked("pre_release_tracked", R.string.github_overview_label_prerelease_tracked),
    PreReleaseUpdate("pre_release_update", R.string.github_overview_label_prerelease_update),
    CheckFailed("check_failed", R.string.github_overview_label_check_failed);

    companion object {
        fun fromStorageId(value: String): GitHubOverviewEntry? {
            return entries.firstOrNull { it.storageId == value }
        }
    }
}

internal fun defaultGitHubOverviewEntries(): Set<GitHubOverviewEntry> =
    GitHubOverviewEntry.entries.toSet()

internal fun Set<GitHubOverviewEntry>.orDefaultGitHubOverviewEntries(): Set<GitHubOverviewEntry> {
    return if (isEmpty()) defaultGitHubOverviewEntries() else this
}

internal object GitHubOverviewUiStateStore {
    private const val KV_ID = "github_overview_ui_state"
    private const val KEY_EXPANDED = "overview_expanded"
    private const val KEY_VISIBLE_ENTRIES = "overview_visible_entries"
    private val store: MMKV by lazy { MMKV.mmkvWithID(KV_ID) }

    fun load(): GitHubOverviewUiState {
        return GitHubOverviewUiState(
            expanded = store.decodeBool(KEY_EXPANDED, true),
            visibleEntries = parseVisibleEntries(
                store.decodeString(KEY_VISIBLE_ENTRIES).orEmpty()
            )
        )
    }

    fun setExpanded(value: Boolean) {
        store.encode(KEY_EXPANDED, value)
    }

    fun setVisibleEntries(entries: Set<GitHubOverviewEntry>) {
        store.encode(
            KEY_VISIBLE_ENTRIES,
            entries.orDefaultGitHubOverviewEntries()
                .joinToString(separator = ",") { it.storageId }
        )
    }

    private fun parseVisibleEntries(raw: String): Set<GitHubOverviewEntry> {
        val entries = raw
            .split(',')
            .mapNotNull { GitHubOverviewEntry.fromStorageId(it.trim()) }
            .toSet()
        return entries.orDefaultGitHubOverviewEntries()
    }
}
