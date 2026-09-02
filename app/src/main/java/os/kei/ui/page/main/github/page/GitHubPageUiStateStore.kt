package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Immutable
import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import os.kei.ui.page.main.github.GitHubSortDirection
import os.kei.ui.page.main.github.GitHubSortMode
import os.kei.ui.page.main.github.GitHubTrackedFilterMode

@Immutable
internal data class GitHubPageUiState(
    val sortMode: GitHubSortMode = GitHubSortMode.Update,
    val sortDirection: GitHubSortDirection = GitHubSortDirection.Forward,
    val trackedFilterMode: GitHubTrackedFilterMode = GitHubTrackedFilterMode.All,
    /**
     * Track ids the reader has pinned, newest pin first.
     *
     * Ordered rather than a set because two pinned items still need a stable order between them, and the
     * order the reader pinned them in is the only one the app knows anything about.
     */
    val pinnedTrackIds: List<String> = emptyList(),
)

internal object GitHubPageUiStateStore {
    private const val KV_ID = "github_page_ui_state"
    private const val KEY_SORT_MODE = "sort_mode"
    private const val KEY_SORT_DIRECTION = "sort_direction"
    private const val KEY_TRACKED_FILTER_MODE = "tracked_filter_mode"
    private const val KEY_PINNED_TRACK_IDS = "pinned_track_ids"

    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }

    fun load(): GitHubPageUiState {
        return GitHubPageUiState(
            sortMode = decodeGitHubSortMode(store.decodeString(KEY_SORT_MODE).orEmpty()),
            sortDirection = decodeGitHubSortDirection(
                store.decodeString(KEY_SORT_DIRECTION).orEmpty()
            ),
            trackedFilterMode = decodeGitHubTrackedFilterMode(
                store.decodeString(KEY_TRACKED_FILTER_MODE).orEmpty()
            ),
            pinnedTrackIds = decodeGitHubPinnedTrackIds(
                store.decodeString(KEY_PINNED_TRACK_IDS).orEmpty()
            )
        )
    }

    fun setSortMode(value: GitHubSortMode) {
        store.encode(KEY_SORT_MODE, value.storageId)
    }

    fun setSortDirection(value: GitHubSortDirection) {
        store.encode(KEY_SORT_DIRECTION, value.storageId)
    }

    fun setTrackedFilterMode(value: GitHubTrackedFilterMode) {
        store.encode(KEY_TRACKED_FILTER_MODE, value.storageId)
    }

    fun setPinnedTrackIds(value: List<String>) {
        store.encode(KEY_PINNED_TRACK_IDS, encodeGitHubPinnedTrackIds(value))
    }
}

/**
 * Newline-separated, because a track id is opaque to this store and a newline is the one character it
 * cannot contain.
 */
internal fun encodeGitHubPinnedTrackIds(value: List<String>): String =
    value.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(separator = "\n")

internal fun decodeGitHubPinnedTrackIds(value: String): List<String> =
    value.split('\n')
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()

internal fun decodeGitHubSortMode(value: String): GitHubSortMode {
    return GitHubSortMode.fromStorageId(value.trim()) ?: GitHubSortMode.Update
}

internal fun decodeGitHubSortDirection(value: String): GitHubSortDirection {
    return GitHubSortDirection.fromStorageId(value.trim()) ?: GitHubSortDirection.Forward
}

internal fun decodeGitHubTrackedFilterMode(value: String): GitHubTrackedFilterMode {
    return GitHubTrackedFilterMode.fromStorageId(value.trim()) ?: GitHubTrackedFilterMode.All
}
