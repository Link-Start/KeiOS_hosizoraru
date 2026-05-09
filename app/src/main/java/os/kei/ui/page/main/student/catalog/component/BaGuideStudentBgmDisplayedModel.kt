package os.kei.ui.page.main.student.catalog.component

import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl

@Immutable
internal data class BaGuideStudentBgmDisplayedModel(
    val contentIds: List<Long>,
    val playableFavorites: List<GuideBgmFavoriteItem>,
    val resolvedCount: Int,
    val loadingCount: Int
)

internal fun favoriteForStudentBgmEntry(
    entry: BaGuideCatalogEntry,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>
): GuideBgmFavoriteItem? {
    val detailUrl = normalizeGuideUrl(entry.detailUrl)
    if (detailUrl.isBlank()) return null
    return favoriteByNormalizedSourceUrl[detailUrl]
}

internal fun studentBgmStateWithFavoriteFallback(
    entry: BaGuideCatalogEntry,
    lookupState: BaGuideStudentBgmLookupState,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>
): BaGuideStudentBgmLookupState {
    if (lookupState is BaGuideStudentBgmLookupState.Ready) return lookupState
    if (lookupState == BaGuideStudentBgmLookupState.Loading) return lookupState
    val favorite =
        favoriteForStudentBgmEntry(entry, favoriteByNormalizedSourceUrl) ?: return lookupState
    return BaGuideStudentBgmLookupState.Ready(
        BaGuideStudentBgmResolvedItem(
            favorite = favorite,
            fromCache = false,
            fromFavorite = true
        )
    )
}

internal fun buildBaGuideStudentBgmDisplayedModel(
    displayedEntries: List<BaGuideCatalogEntry>,
    lookupStates: Map<Long, BaGuideStudentBgmLookupState>,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>
): BaGuideStudentBgmDisplayedModel {
    val contentIds = ArrayList<Long>(displayedEntries.size)
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
        val readyFavorite = studentBgmStateWithFavoriteFallback(
            entry = entry,
            lookupState = lookupState,
            favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl
        ).readyFavoriteOrNull()
        if (readyFavorite != null) {
            resolvedCount += 1
            if (seenAudioUrls.add(readyFavorite.audioUrl)) {
                playableFavorites += readyFavorite
            }
        }
    }
    return BaGuideStudentBgmDisplayedModel(
        contentIds = contentIds.toList(),
        playableFavorites = playableFavorites.toList(),
        resolvedCount = resolvedCount,
        loadingCount = loadingCount
    )
}
