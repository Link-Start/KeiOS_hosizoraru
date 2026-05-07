package os.kei.ui.page.main.student.catalog.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import kotlin.test.assertEquals

class BaGuideCatalogRepositoryTest {
    @Test
    fun `refresh failure keeps complete cached bundle`() = runBlocking {
        val cached = catalogBundle("缓存学生")
        val repository = BaGuideCatalogRepository(
            ioDispatcher = Dispatchers.Unconfined,
            refreshIntervalLoader = { 1 },
            cachedBundleLoader = { cached },
            catalogFetcher = { error("blocked") },
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
            catalogFetcher = {
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
        tab: BaGuideCatalogTab
    ): BaGuideCatalogEntry {
        return BaGuideCatalogEntry(
            entryId = name.hashCode(),
            pid = 1,
            contentId = name.hashCode().toLong().let { if (it < 0L) -it else it } + 1L,
            name = name,
            alias = "",
            aliasDisplay = "",
            iconUrl = "https://example.com/icon.png",
            type = 1,
            order = 0,
            createdAtSec = 1L,
            detailUrl = "https://www.gamekee.com/ba/tj/1.html",
            tab = tab
        )
    }
}
