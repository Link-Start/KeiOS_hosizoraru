package os.kei.ui.page.main.student.catalog.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntryFilterAttributes
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterDefinition
import os.kei.ui.page.main.student.catalog.BaGuideCatalogFilterOption
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.BaGuideBgmFavoriteSortMode
import kotlin.test.assertEquals

class BaGuideCatalogRepositoryTest {
    @Test
    fun `refresh failure keeps complete cached bundle`() = runBlocking {
        val cached = catalogBundle("缓存学生")
        val repository = BaGuideCatalogRepository(
            ioDispatcher = Dispatchers.Unconfined,
            refreshIntervalLoader = { 1 },
            cachedBundleLoader = { cached },
            catalogFetcher = { _, _, _ -> error("blocked") },
            completeChecker = { it === cached },
            expiredChecker = { _, _, _ -> true }
        )

        val result = repository.loadCatalog(
            context = null,
            currentCatalog = BaGuideCatalogBundle.EMPTY,
            manualRefresh = true,
            loadFailedText = "加载失败",
            refreshFailedKeepCacheText = "保留缓存"
        )

        assertEquals(cached, result.catalog)
        assertEquals("保留缓存", result.error)
    }

    @Test
    fun `fresh complete cache returns without network fetch`() = runBlocking {
        val cached = catalogBundle("新缓存")
        var fetchCalled = false
        val repository = BaGuideCatalogRepository(
            ioDispatcher = Dispatchers.Unconfined,
            refreshIntervalLoader = { 12 },
            cachedBundleLoader = { cached },
            catalogFetcher = { _, _, _ ->
                fetchCalled = true
                BaGuideCatalogBundle.EMPTY
            },
            completeChecker = { it === cached },
            expiredChecker = { _, _, _ -> false }
        )

        val result = repository.loadCatalog(
            context = null,
            currentCatalog = BaGuideCatalogBundle.EMPTY,
            manualRefresh = false,
            loadFailedText = "加载失败",
            refreshFailedKeepCacheText = "保留缓存"
        )

        assertEquals(cached, result.catalog)
        assertEquals(null, result.error)
        assertEquals(false, fetchCalled)
    }

    @Test
    fun `catalog list derivation filters current tab and pins favorites`() = runBlocking {
        val starDefinition =
            BaGuideCatalogFilterDefinition(
                id = 68,
                name = "星级",
                type = 0,
                options =
                    listOf(
                        BaGuideCatalogFilterOption(176, "三星"),
                        BaGuideCatalogFilterOption(70, "二星"),
                    ),
            )
        val favorite = catalogEntry(
            name = "阿露",
            tab = BaGuideCatalogTab.Student,
            order = 2,
            attributes = BaGuideCatalogEntryFilterAttributes(optionIdsByFilterId = mapOf(68 to setOf(176))),
        )
        val regular = catalogEntry(
            name = "日富美",
            tab = BaGuideCatalogTab.Student,
            order = 1,
            attributes = BaGuideCatalogEntryFilterAttributes(optionIdsByFilterId = mapOf(68 to setOf(176))),
        )
        val filteredOut = catalogEntry(
            name = "晴",
            tab = BaGuideCatalogTab.Student,
            order = 3,
            attributes = BaGuideCatalogEntryFilterAttributes(optionIdsByFilterId = mapOf(68 to setOf(70))),
        )
        val bundle =
            BaGuideCatalogBundle(
                entriesByTab =
                    mapOf(
                        BaGuideCatalogTab.Student to listOf(regular, favorite, filteredOut),
                        BaGuideCatalogTab.NpcSatellite to emptyList(),
                    ),
                syncedAtMs = 2_000L,
                filterDefinitionsByTab = mapOf(BaGuideCatalogTab.Student to listOf(starDefinition)),
            )
        val repository = BaGuideCatalogRepository(parseDispatcher = Dispatchers.Unconfined)

        val result =
            repository.deriveCatalogListState(
                BaGuideCatalogListInput(
                    catalog = bundle,
                    tab = BaGuideCatalogTab.Student,
                    sortMode = BaGuideCatalogSortMode.Default,
                    favoriteCatalogEntries = mapOf(favorite.contentId to 1L),
                    selectedFilterOptions = mapOf(68 to setOf(176), 9999 to setOf(1)),
                    searchQuery = "",
                ),
            )

        assertEquals(1, result.activeFilterCount)
        assertEquals(listOf("阿露", "日富美"), result.filteredEntries.map { it.name })
        assertEquals(false, result.deriving)
    }

    @Test
    fun `student bgm list derivation pins favorite guide entries`() = runBlocking {
        val regular =
            catalogEntry(
                name = "日富美",
                tab = BaGuideCatalogTab.Student,
                order = 1,
            )
        val favorite =
            catalogEntry(
                name = "阿露",
                tab = BaGuideCatalogTab.Student,
                order = 2,
            )
        val bundle =
            BaGuideCatalogBundle(
                entriesByTab =
                    mapOf(
                        BaGuideCatalogTab.Student to listOf(regular, favorite),
                        BaGuideCatalogTab.NpcSatellite to emptyList(),
                    ),
                syncedAtMs = 3_000L,
            )
        val repository = BaGuideCatalogRepository(parseDispatcher = Dispatchers.Unconfined)

        val result =
            repository.deriveStudentBgmListState(
                BaGuideStudentBgmListInput(
                    catalog = bundle,
                    favorites = listOf(bgmFavorite(sourceUrl = favorite.detailUrl)),
                    searchQuery = "",
                ),
            )

        assertEquals(listOf("阿露", "日富美"), result.filteredEntries.map { it.name })
        assertEquals(listOf("日富美", "阿露"), result.allStudentEntries.map { it.name })
        assertEquals(false, result.deriving)
    }

    @Test
    fun `favorite bgm list derivation sorts off composable path`() = runBlocking {
        val older =
            bgmFavorite(
                sourceUrl = "https://www.gamekee.com/ba/1.html",
                title = "Old Track",
                studentTitle = "日富美",
                favoritedAtMs = 1L,
            )
        val newer =
            bgmFavorite(
                sourceUrl = "https://www.gamekee.com/ba/2.html",
                title = "New Track",
                studentTitle = "阿露",
                favoritedAtMs = 2L,
            )
        val repository = BaGuideCatalogRepository(parseDispatcher = Dispatchers.Unconfined)

        val result =
            repository.deriveFavoriteBgmListState(
                BaGuideFavoriteBgmListInput(
                    favorites = listOf(older, newer),
                    searchQuery = "",
                    sortMode = BaGuideBgmFavoriteSortMode.Recent,
                ),
            )

        assertEquals(listOf("New Track", "Old Track"), result.displayedFavorites.map { it.title })
        assertEquals(false, result.deriving)
    }

    private fun catalogBundle(name: String): BaGuideCatalogBundle {
        return BaGuideCatalogBundle(
            entriesByTab = mapOf(
                BaGuideCatalogTab.Student to listOf(catalogEntry(name, BaGuideCatalogTab.Student)),
                BaGuideCatalogTab.NpcSatellite to listOf(
                    catalogEntry("卫星", BaGuideCatalogTab.NpcSatellite)
                )
            ),
            syncedAtMs = 1_000L
        )
    }

    private fun catalogEntry(
        name: String,
        tab: BaGuideCatalogTab,
        order: Int = 0,
        attributes: BaGuideCatalogEntryFilterAttributes = BaGuideCatalogEntryFilterAttributes.EMPTY,
    ): BaGuideCatalogEntry {
        return BaGuideCatalogEntry(
            entryId = name.hashCode() + order,
            pid = 1,
            contentId = name.hashCode().toLong().let { if (it < 0L) -it else it } + order + 1L,
            name = name,
            alias = "",
            aliasDisplay = "",
            iconUrl = "https://example.com/icon.png",
            type = 1,
            order = order,
            createdAtSec = 1L,
            detailUrl = "https://www.gamekee.com/ba/tj/${name.hashCode().let { if (it < 0) -it else it } + order}.html",
            tab = tab,
            filterAttributes = attributes,
        )
    }

    private fun bgmFavorite(
        sourceUrl: String,
        title: String = "BGM",
        studentTitle: String = "学生",
        favoritedAtMs: Long = 1L,
    ): GuideBgmFavoriteItem =
        GuideBgmFavoriteItem(
            audioUrl = "https://example.com/audio.ogg",
            title = title,
            studentTitle = studentTitle,
            studentImageUrl = "",
            imageUrl = "",
            sourceUrl = sourceUrl,
            note = "",
            favoritedAtMs = favoritedAtMs,
        )
}
