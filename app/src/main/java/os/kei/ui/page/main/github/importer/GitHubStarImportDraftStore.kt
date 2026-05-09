package os.kei.ui.page.main.github.importer

import androidx.compose.runtime.Immutable
import com.tencent.mmkv.MMKV
import os.kei.feature.github.model.GitHubStarImportQuality

@Immutable
internal data class GitHubStarImportDraft(
    val source: StarImportUiSource = StarImportUiSource.MyStars,
    val usernameInput: String = "",
    val listUrlInput: String = "",
    val filterInput: String = "",
    val viewFilter: StarImportViewFilter = StarImportViewFilter.All,
    val qualityFilters: Set<GitHubStarImportQuality> = defaultVisibleStarImportQualities(),
    val conflictStrategy: StarImportConflictStrategy = StarImportConflictStrategy.NewOnly,
    val selectedIds: Set<String> = emptySet()
)

internal object GitHubStarImportDraftStore {
    private const val KV_ID = "github_star_import_draft"
    private const val KEY_SOURCE = "source"
    private const val KEY_USERNAME = "username"
    private const val KEY_LIST_URL = "list_url"
    private const val KEY_FILTER = "filter"
    private const val KEY_VIEW_FILTER = "view_filter"
    private const val KEY_QUALITY_FILTERS = "quality_filters"
    private const val KEY_CONFLICT = "conflict"
    private const val KEY_SELECTED_IDS = "selected_ids"
    private const val LIST_SEPARATOR = "\u001F"

    private val store: MMKV by lazy { MMKV.mmkvWithID(KV_ID) }

    fun load(): GitHubStarImportDraft {
        return GitHubStarImportDraft(
            source = enumValueOrDefault(
                store.decodeString(KEY_SOURCE),
                StarImportUiSource.MyStars
            ),
            usernameInput = store.decodeString(KEY_USERNAME).orEmpty(),
            listUrlInput = store.decodeString(KEY_LIST_URL).orEmpty(),
            filterInput = store.decodeString(KEY_FILTER).orEmpty(),
            viewFilter = enumValueOrDefault(
                store.decodeString(KEY_VIEW_FILTER),
                StarImportViewFilter.All
            ),
            qualityFilters = decodeQualityFilters(store.decodeString(KEY_QUALITY_FILTERS)),
            conflictStrategy = enumValueOrDefault(
                store.decodeString(KEY_CONFLICT),
                StarImportConflictStrategy.NewOnly
            ),
            selectedIds = decodeStringSet(store.decodeString(KEY_SELECTED_IDS))
        )
    }

    fun save(draft: GitHubStarImportDraft) {
        store.encode(KEY_SOURCE, draft.source.name)
        store.encode(KEY_USERNAME, draft.usernameInput)
        store.encode(KEY_LIST_URL, draft.listUrlInput)
        store.encode(KEY_FILTER, draft.filterInput)
        store.encode(KEY_VIEW_FILTER, draft.viewFilter.name)
        store.encode(KEY_QUALITY_FILTERS, draft.qualityFilters.joinToString(LIST_SEPARATOR) { it.name })
        store.encode(KEY_CONFLICT, draft.conflictStrategy.name)
        store.encode(KEY_SELECTED_IDS, draft.selectedIds.joinToString(LIST_SEPARATOR))
    }

    fun clearSelection() {
        store.removeValueForKey(KEY_SELECTED_IDS)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, defaultValue: T): T {
        val name = raw?.trim().orEmpty()
        if (name.isBlank()) return defaultValue
        return enumValues<T>().firstOrNull { it.name == name } ?: defaultValue
    }

    private fun decodeQualityFilters(raw: String?): Set<GitHubStarImportQuality> {
        val decoded = decodeStringSet(raw)
            .mapNotNull { name -> GitHubStarImportQuality.entries.firstOrNull { it.name == name } }
            .toSet()
        return decoded.ifEmpty { defaultVisibleStarImportQualities() }
    }

    private fun decodeStringSet(raw: String?): Set<String> {
        return raw.orEmpty()
            .split(LIST_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
