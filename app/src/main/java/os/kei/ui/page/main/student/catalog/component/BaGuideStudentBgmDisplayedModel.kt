package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.filterByQuery
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl

@Immutable
internal data class BaGuideStudentBgmDisplayedModel(
    val contentIds: List<Long>,
    val rows: List<BaGuideStudentBgmRowModel>,
    val playableFavorites: List<GuideBgmFavoriteItem>,
    val resolvedCount: Int,
    val loadingCount: Int,
) {
    companion object {
        val Empty =
            BaGuideStudentBgmDisplayedModel(
                contentIds = emptyList(),
                rows = emptyList(),
                playableFavorites = emptyList(),
                resolvedCount = 0,
                loadingCount = 0,
            )
    }
}

@Immutable
internal data class BaGuideStudentBgmRowModel(
    val entry: BaGuideCatalogEntry,
    val displayState: BaGuideStudentBgmLookupState,
    val readyAudioUrl: String?,
    val favorite: Boolean,
)

internal fun favoriteForStudentBgmEntry(
    entry: BaGuideCatalogEntry,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>,
): GuideBgmFavoriteItem? {
    val detailUrl = normalizeGuideUrl(entry.detailUrl)
    if (detailUrl.isBlank()) return null
    return favoriteByNormalizedSourceUrl[detailUrl]
}

internal fun favoriteStudentBgmEntryContentIds(
    entries: List<BaGuideCatalogEntry>,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>,
): Set<Long> {
    if (entries.isEmpty() || favoriteByNormalizedSourceUrl.isEmpty()) return emptySet()
    return buildSet {
        entries.forEach { entry ->
            val detailUrl = normalizeGuideUrl(entry.detailUrl)
            if (detailUrl.isNotBlank() && detailUrl in favoriteByNormalizedSourceUrl) {
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

internal fun studentBgmStateWithFavoriteFallback(
    entry: BaGuideCatalogEntry,
    lookupState: BaGuideStudentBgmLookupState,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>,
): BaGuideStudentBgmLookupState {
    if (lookupState is BaGuideStudentBgmLookupState.Ready) return lookupState
    if (lookupState == BaGuideStudentBgmLookupState.Loading) return lookupState
    val favorite =
        favoriteForStudentBgmEntry(entry, favoriteByNormalizedSourceUrl) ?: return lookupState
    return BaGuideStudentBgmLookupState.Ready(
        BaGuideStudentBgmResolvedItem(
            favorite = favorite,
            fromCache = false,
            fromFavorite = true,
        ),
    )
}

internal fun buildBaGuideStudentBgmDisplayedModel(
    displayedEntries: List<BaGuideCatalogEntry>,
    lookupStates: Map<Long, BaGuideStudentBgmLookupState>,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>,
    favoriteAudioUrls: Set<String>,
): BaGuideStudentBgmDisplayedModel {
    val contentIds = ArrayList<Long>(displayedEntries.size)
    val rows = ArrayList<BaGuideStudentBgmRowModel>(displayedEntries.size)
    val playableFavorites = ArrayList<GuideBgmFavoriteItem>()
    val seenAudioUrls = mutableSetOf<String>()
    var resolvedCount = 0
    var loadingCount = 0
    displayedEntries.forEach { entry ->
        contentIds += entry.contentId
        val lookupState = lookupStates[entry.contentId] ?: BaGuideStudentBgmLookupState.Idle
        if (lookupState == BaGuideStudentBgmLookupState.Loading) {
            loadingCount += 1
        }
        val displayState =
            studentBgmStateWithFavoriteFallback(
                entry = entry,
                lookupState = lookupState,
                favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl,
            )
        val readyFavorite = displayState.readyFavoriteOrNull()
        val readyAudioUrl = readyFavorite?.audioUrl
        val favorite =
            if (!readyAudioUrl.isNullOrBlank()) {
                readyAudioUrl in favoriteAudioUrls
            } else {
                favoriteForStudentBgmEntry(entry, favoriteByNormalizedSourceUrl) != null
            }
        rows +=
            BaGuideStudentBgmRowModel(
                entry = entry,
                displayState = displayState,
                readyAudioUrl = readyAudioUrl,
                favorite = favorite,
            )
        if (readyFavorite != null) {
            resolvedCount += 1
            if (seenAudioUrls.add(readyFavorite.audioUrl)) {
                playableFavorites += readyFavorite
            }
        }
    }
    return BaGuideStudentBgmDisplayedModel(
        contentIds = contentIds.toList(),
        rows = rows.toList(),
        playableFavorites = playableFavorites.toList(),
        resolvedCount = resolvedCount,
        loadingCount = loadingCount,
    )
}
