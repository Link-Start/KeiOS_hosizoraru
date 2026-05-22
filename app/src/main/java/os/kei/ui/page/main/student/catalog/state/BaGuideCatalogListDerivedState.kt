package os.kei.ui.page.main.student.catalog.state

import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackSnapshot
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmFavoriteSortMode
import os.kei.ui.page.main.student.catalog.component.BaGuideStudentBgmDisplayedModel
import os.kei.ui.page.main.student.catalog.component.BaGuideStudentBgmLookupState
import os.kei.ui.page.main.student.catalog.filterByQuery
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl

internal class BaGuideCatalogListInput(
    val catalog: BaGuideCatalogBundle,
    val tab: BaGuideCatalogTab,
    val sortMode: BaGuideCatalogSortMode,
    val favoriteCatalogEntries: Map<Long, Long>,
    val selectedFilterOptions: Map<Int, Set<Int>>,
    val searchQuery: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BaGuideCatalogListInput &&
            catalog === other.catalog &&
            tab == other.tab &&
            sortMode == other.sortMode &&
            favoriteCatalogEntries == other.favoriteCatalogEntries &&
            selectedFilterOptions == other.selectedFilterOptions &&
            searchQuery == other.searchQuery
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(catalog)
        result = 31 * result + tab.hashCode()
        result = 31 * result + sortMode.hashCode()
        result = 31 * result + favoriteCatalogEntries.hashCode()
        result = 31 * result + selectedFilterOptions.hashCode()
        result = 31 * result + searchQuery.hashCode()
        return result
    }
}

internal class BaGuideStudentBgmListInput(
    val catalog: BaGuideCatalogBundle,
    val favorites: List<GuideBgmFavoriteItem>,
    val searchQuery: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BaGuideStudentBgmListInput &&
            catalog === other.catalog &&
            favorites == other.favorites &&
            searchQuery == other.searchQuery
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(catalog)
        result = 31 * result + favorites.hashCode()
        result = 31 * result + searchQuery.hashCode()
        return result
    }
}

internal class BaGuideFavoriteBgmListInput(
    val favorites: List<GuideBgmFavoriteItem>,
    val searchQuery: String,
    val sortMode: BaGuideBgmFavoriteSortMode,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BaGuideFavoriteBgmListInput &&
            favorites == other.favorites &&
            searchQuery == other.searchQuery &&
            sortMode == other.sortMode
    }

    override fun hashCode(): Int {
        var result = favorites.hashCode()
        result = 31 * result + searchQuery.hashCode()
        result = 31 * result + sortMode.hashCode()
        return result
    }
}

internal class BaGuideStudentBgmDisplayedInput(
    val displayedEntries: List<BaGuideCatalogEntry>,
    val lookupStates: Map<Long, BaGuideStudentBgmLookupState>,
    val favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>,
    val favoriteAudioUrls: Set<String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is BaGuideStudentBgmDisplayedInput &&
            displayedEntries === other.displayedEntries &&
            lookupStates === other.lookupStates &&
            favoriteByNormalizedSourceUrl === other.favoriteByNormalizedSourceUrl &&
            favoriteAudioUrls === other.favoriteAudioUrls
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(displayedEntries)
        result = 31 * result + System.identityHashCode(lookupStates)
        result = 31 * result + System.identityHashCode(favoriteByNormalizedSourceUrl)
        result = 31 * result + System.identityHashCode(favoriteAudioUrls)
        return result
    }
}

@Immutable
internal data class BaGuideStudentBgmDisplayedDerivedState(
    val input: BaGuideStudentBgmDisplayedInput? = null,
    val model: BaGuideStudentBgmDisplayedModel = BaGuideStudentBgmDisplayedModel.Empty,
    val deriving: Boolean = false,
) {
    companion object {
        val Empty = BaGuideStudentBgmDisplayedDerivedState()
    }
}

@Immutable
internal data class BaGuideFavoriteBgmListDerivedState(
    val displayedFavorites: List<GuideBgmFavoriteItem> = emptyList(),
    val metadataIndex: BaGuideBgmFavoriteMetadataIndex = BaGuideBgmFavoriteMetadataIndex.Empty,
    val playbackSnapshot: GuideBgmFavoritePlaybackSnapshot =
        GuideBgmFavoritePlaybackSnapshot(
            selectedAudioUrl = "",
            queueModeName = "",
            volume = 1f,
            progressByAudioUrl = emptyMap(),
        ),
    val deriving: Boolean = false,
) {
    companion object {
        val Empty = BaGuideFavoriteBgmListDerivedState()
    }
}

@Immutable
internal data class BaGuideStudentBgmListDerivedState(
    val allStudentEntries: List<BaGuideCatalogEntry> = emptyList(),
    val filteredEntries: List<BaGuideCatalogEntry> = emptyList(),
    val favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem> = emptyMap(),
    val favoriteAudioUrls: Set<String> = emptySet(),
    val deriving: Boolean = false,
) {
    companion object {
        val Empty = BaGuideStudentBgmListDerivedState()
    }
}

internal fun favoriteStudentBgmEntryContentIds(
    entries: List<BaGuideCatalogEntry>,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>,
): Set<Long> =
    favoriteStudentBgmEntryContentIds(
        entries = entries,
        favoriteSourceUrls = favoriteByNormalizedSourceUrl.keys,
    )

internal fun favoriteStudentBgmEntryContentIds(
    entries: List<BaGuideCatalogEntry>,
    favoriteSourceUrls: Set<String>,
): Set<Long> {
    if (entries.isEmpty() || favoriteSourceUrls.isEmpty()) return emptySet()
    return buildSet {
        entries.forEach { entry ->
            val detailUrl = normalizeGuideUrl(entry.detailUrl)
            if (detailUrl.isNotBlank() && detailUrl in favoriteSourceUrls) {
                add(entry.contentId)
            }
        }
    }
}

internal fun filterAndSortStudentBgmEntries(
    entries: List<BaGuideCatalogEntry>,
    searchQuery: String,
    favoriteContentIds: Set<Long>,
): List<BaGuideCatalogEntry> {
    val filtered = entries.filterByQuery(searchQuery)
    if (filtered.size <= 1 || favoriteContentIds.isEmpty()) return filtered
    return filtered.sortedWith(
        compareByDescending<BaGuideCatalogEntry> { entry ->
            entry.contentId in favoriteContentIds
        }.thenBy { entry -> entry.order },
    )
}

@Immutable
internal data class BaGuideCatalogListDerivedState(
    val filteredEntries: List<BaGuideCatalogEntry> = emptyList(),
    val activeFilterCount: Int = 0,
    val deriving: Boolean = false,
) {
    companion object {
        val Empty = BaGuideCatalogListDerivedState()
    }
}
