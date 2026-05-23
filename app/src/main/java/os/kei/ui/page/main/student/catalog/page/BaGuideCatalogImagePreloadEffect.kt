@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogRouteState

private const val BA_GUIDE_CATALOG_IMAGE_PRELOAD_LIMIT = 72

@Composable
internal fun BindBaGuideCatalogImagePreloadEffect(
    routeState: BaGuideCatalogRouteState,
    chromePresentation: BaGuideCatalogChromePresentation,
    requestCatalogImages: (List<String>) -> Unit,
) {
    val imageUrls =
        remember(routeState, chromePresentation) {
            buildBaGuideCatalogImagePreloadUrls(
                routeState = routeState,
                chromePresentation = chromePresentation,
            )
        }
    LaunchedEffect(imageUrls) {
        requestCatalogImages(imageUrls)
    }
}

internal fun buildBaGuideCatalogImagePreloadUrls(
    routeState: BaGuideCatalogRouteState,
    chromePresentation: BaGuideCatalogChromePresentation,
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

    chromePresentation.activeTab.catalogTab?.let { tab ->
        addEntries(routeState.catalogListDerivedStates[tab]?.filteredEntries.orEmpty())
    }
    if (chromePresentation.activeTab.specialTab == BaGuideCatalogSpecialTab.StudentBgm) {
        addEntries(routeState.studentBgmListDerivedState.filteredEntries)
    }
    if (chromePresentation.activeTab.specialTab == BaGuideCatalogSpecialTab.FavoriteBgm) {
        addFavorites(routeState.favoriteBgmListDerivedState.displayedFavorites)
    }
    addUrl(chromePresentation.artworkImageUrl)
    chromePresentation.playbackFavorite?.let { favorite ->
        addUrl(favorite.studentImageUrl.ifBlank { favorite.imageUrl })
    }
    return urls.toList()
}
