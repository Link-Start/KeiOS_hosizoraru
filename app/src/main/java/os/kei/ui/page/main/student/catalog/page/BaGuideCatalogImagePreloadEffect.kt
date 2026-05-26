@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogRouteState

private const val BA_GUIDE_CATALOG_IMAGE_PRELOAD_LIMIT = 72

@Composable
internal fun BindBaGuideCatalogImagePreloadEffect(
    routeState: BaGuideCatalogRouteState,
    chromePresentation: BaGuideCatalogChromePresentation,
    requestCatalogImages: (List<String>) -> Unit,
) {
    val activeTab = chromePresentation.activeTab
    val catalogListDerivedStates = routeState.catalogListDerivedStates
    val studentBgmEntries = routeState.studentBgmListDerivedState.filteredEntries
    val favoriteBgms = routeState.favoriteBgmListDerivedState.displayedFavorites
    val artworkImageUrl = chromePresentation.artworkImageUrl
    val playbackFavorite = chromePresentation.playbackFavorite
    val imageUrls =
        remember(
            activeTab,
            catalogListDerivedStates,
            studentBgmEntries,
            favoriteBgms,
            artworkImageUrl,
            playbackFavorite,
        ) {
            buildBaGuideCatalogImagePreloadUrls(
                activeTab = activeTab,
                catalogListDerivedStates = catalogListDerivedStates,
                studentBgmEntries = studentBgmEntries,
                favoriteBgms = favoriteBgms,
                artworkImageUrl = artworkImageUrl,
                playbackFavorite = playbackFavorite,
            )
        }
    LaunchedEffect(imageUrls) {
        requestCatalogImages(imageUrls)
    }
}

internal fun buildBaGuideCatalogImagePreloadUrls(
    activeTab: BaGuideCatalogPageTab,
    catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    studentBgmEntries: List<BaGuideCatalogEntry>,
    favoriteBgms: List<GuideBgmFavoriteItem>,
    artworkImageUrl: String,
    playbackFavorite: GuideBgmFavoriteItem?,
): List<String> {
    val urls = linkedSetOf<String>()

    fun addUrl(url: String) {
        val normalized = url.trim()
        if (normalized.isNotBlank()) {
            urls.add(normalized)
        }
    }

    fun addEntries(entries: List<BaGuideCatalogEntry>) {
        entries
            .take(BA_GUIDE_CATALOG_IMAGE_PRELOAD_LIMIT)
            .forEach { entry -> addUrl(entry.iconUrl) }
    }

    fun addFavorites(favorites: List<GuideBgmFavoriteItem>) {
        favorites
            .take(BA_GUIDE_CATALOG_IMAGE_PRELOAD_LIMIT)
            .forEach { favorite ->
                addUrl(favorite.studentImageUrl.ifBlank { favorite.imageUrl })
            }
    }

    activeTab.catalogTab?.let { tab ->
        addEntries(catalogListDerivedStates[tab]?.filteredEntries.orEmpty())
    }
    if (activeTab.specialTab == BaGuideCatalogSpecialTab.StudentBgm) {
        addEntries(studentBgmEntries)
    }
    if (activeTab.specialTab == BaGuideCatalogSpecialTab.FavoriteBgm) {
        addFavorites(favoriteBgms)
    }
    addUrl(artworkImageUrl)
    playbackFavorite?.let { favorite ->
        addUrl(favorite.studentImageUrl.ifBlank { favorite.imageUrl })
    }
    return urls.toList()
}
