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

private const val BA_GUIDE_CATALOG_ACTIVE_HEAD_PRELOAD_LIMIT = 20
private const val BA_GUIDE_CATALOG_IMAGE_PRELOAD_LIMIT = 96
private const val BA_GUIDE_CATALOG_SPECIAL_IMAGE_PRELOAD_LIMIT = 72

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

    fun addEntries(
        entries: List<BaGuideCatalogEntry>,
        limit: Int,
    ) {
        entries
            .take(limit.coerceAtLeast(0))
            .forEach { entry -> addUrl(entry.iconUrl) }
    }

    fun addFavorites(
        favorites: List<GuideBgmFavoriteItem>,
        limit: Int,
    ) {
        favorites
            .take(limit.coerceAtLeast(0))
            .forEach { favorite ->
                addUrl(favorite.studentImageUrl.ifBlank { favorite.imageUrl })
            }
    }

    activeTab.catalogTab?.let { tab ->
        addEntries(
            entries = catalogListDerivedStates[tab]?.filteredEntries.orEmpty(),
            limit = BA_GUIDE_CATALOG_ACTIVE_HEAD_PRELOAD_LIMIT,
        )
    }
    if (activeTab.specialTab == BaGuideCatalogSpecialTab.StudentBgm) {
        addEntries(
            entries = studentBgmEntries,
            limit = BA_GUIDE_CATALOG_SPECIAL_IMAGE_PRELOAD_LIMIT,
        )
    }
    if (activeTab.specialTab == BaGuideCatalogSpecialTab.FavoriteBgm) {
        addFavorites(
            favorites = favoriteBgms,
            limit = BA_GUIDE_CATALOG_SPECIAL_IMAGE_PRELOAD_LIMIT,
        )
    }
    addUrl(artworkImageUrl)
    playbackFavorite?.let { favorite ->
        addUrl(favorite.studentImageUrl.ifBlank { favorite.imageUrl })
    }
    if (urls.size < BA_GUIDE_CATALOG_IMAGE_PRELOAD_LIMIT) {
        buildFairCatalogWarmupUrls(
            catalogListDerivedStates = catalogListDerivedStates,
            activeCatalogTab = activeTab.catalogTab,
            limit = BA_GUIDE_CATALOG_IMAGE_PRELOAD_LIMIT - urls.size,
        ).forEach(::addUrl)
    }
    return urls.toList()
}

private fun buildFairCatalogWarmupUrls(
    catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    activeCatalogTab: BaGuideCatalogTab?,
    limit: Int,
): List<String> {
    if (limit <= 0) return emptyList()
    val tabs =
        when (activeCatalogTab) {
            BaGuideCatalogTab.NpcSatellite -> listOf(BaGuideCatalogTab.NpcSatellite, BaGuideCatalogTab.Student)
            else -> listOf(BaGuideCatalogTab.Student, BaGuideCatalogTab.NpcSatellite)
        }
    val urlsByTab =
        tabs.associateWith { tab ->
            catalogListDerivedStates[tab]
                ?.filteredEntries
                .orEmpty()
                .asSequence()
                .drop(
                    if (tab == activeCatalogTab) {
                        BA_GUIDE_CATALOG_ACTIVE_HEAD_PRELOAD_LIMIT
                    } else {
                        0
                    },
                ).map { entry -> entry.iconUrl.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
        }
    val indices = tabs.associateWith { 0 }.toMutableMap()
    val out = mutableListOf<String>()
    while (out.size < limit) {
        var progressed = false
        tabs.forEach { tab ->
            if (out.size >= limit) return@forEach
            val urls = urlsByTab.getValue(tab)
            val index = indices.getValue(tab)
            val url = urls.getOrNull(index) ?: return@forEach
            indices[tab] = index + 1
            out += url
            progressed = true
        }
        if (!progressed) break
    }
    return out
}
