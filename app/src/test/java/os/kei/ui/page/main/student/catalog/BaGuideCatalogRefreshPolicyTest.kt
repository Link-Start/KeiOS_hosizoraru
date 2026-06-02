package os.kei.ui.page.main.student.catalog

import org.junit.Test
import kotlin.test.assertEquals

class BaGuideCatalogRefreshPolicyTest {
    @Test
    fun `incremental refresh interval resolves to supported choices`() {
        assertEquals(12, resolvedBaGuideCatalogIncrementalRefreshIntervalHours(1))
        assertEquals(12, resolvedBaGuideCatalogIncrementalRefreshIntervalHours(3))
        assertEquals(12, resolvedBaGuideCatalogIncrementalRefreshIntervalHours(12))
        assertEquals(24, resolvedBaGuideCatalogIncrementalRefreshIntervalHours(24))
        assertEquals(24, resolvedBaGuideCatalogIncrementalRefreshIntervalHours(48))
    }

    @Test
    fun `cache expiry uses resolved incremental interval`() {
        val syncedAtMs = 1_000L
        val bundle = catalogBundle(syncedAtMs = syncedAtMs)

        assertEquals(
            false,
            isBaGuideCatalogCacheExpired(
                bundle = bundle,
                refreshIntervalHours = 3,
                nowMs = syncedAtMs + 11L * 60L * 60L * 1000L,
            ),
        )
        assertEquals(
            true,
            isBaGuideCatalogCacheExpired(
                bundle = bundle,
                refreshIntervalHours = 3,
                nowMs = syncedAtMs + 12L * 60L * 60L * 1000L,
            ),
        )
        assertEquals(
            false,
            isBaGuideCatalogCacheExpired(
                bundle = bundle,
                refreshIntervalHours = 24,
                nowMs = syncedAtMs + 23L * 60L * 60L * 1000L,
            ),
        )
    }

    @Test
    fun `refresh mode selects full for manual incomplete or full cadence expiry`() {
        val dayMs = 24L * 60L * 60L * 1000L
        val nowMs = 5L * dayMs
        val bundle =
            catalogBundle(
                syncedAtMs = nowMs - 13L * 60L * 60L * 1000L,
                fullSyncedAtMs = nowMs - dayMs,
            )

        assertEquals(
            BaGuideCatalogRefreshMode.Incremental,
            selectBaGuideCatalogRefreshMode(
                cachedBundle = bundle,
                manualRefresh = false,
                cacheComplete = true,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            BaGuideCatalogRefreshMode.Full,
            selectBaGuideCatalogRefreshMode(
                cachedBundle = bundle,
                manualRefresh = true,
                cacheComplete = true,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            BaGuideCatalogRefreshMode.Full,
            selectBaGuideCatalogRefreshMode(
                cachedBundle = bundle,
                manualRefresh = false,
                cacheComplete = false,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            BaGuideCatalogRefreshMode.Full,
            selectBaGuideCatalogRefreshMode(
                cachedBundle = bundle.copy(fullSyncedAtMs = nowMs - 4L * dayMs),
                manualRefresh = false,
                cacheComplete = true,
                nowMs = nowMs,
            ),
        )
    }

    private fun catalogBundle(
        syncedAtMs: Long,
        fullSyncedAtMs: Long = syncedAtMs,
    ): BaGuideCatalogBundle =
        BaGuideCatalogBundle(
            entriesByTab =
                mapOf(
                    BaGuideCatalogTab.Student to listOf(catalogEntry(BaGuideCatalogTab.Student)),
                    BaGuideCatalogTab.NpcSatellite to listOf(catalogEntry(BaGuideCatalogTab.NpcSatellite)),
                ),
            syncedAtMs = syncedAtMs,
            filterDefinitionsByTab =
                mapOf(
                    BaGuideCatalogTab.Student to
                        listOf(
                            BaGuideCatalogFilterDefinition(
                                id = 1,
                                name = "筛选",
                                type = 0,
                                options = emptyList(),
                            ),
                        ),
                    BaGuideCatalogTab.NpcSatellite to
                        listOf(
                            BaGuideCatalogFilterDefinition(
                                id = 2,
                                name = "筛选",
                                type = 0,
                                options = emptyList(),
                            ),
                        ),
                ),
            fullSyncedAtMs = fullSyncedAtMs,
        )

    private fun catalogEntry(tab: BaGuideCatalogTab): BaGuideCatalogEntry =
        BaGuideCatalogEntry(
            entryId = tab.ordinal + 1,
            pid = tab.ordinal + 1,
            contentId = (tab.ordinal + 1).toLong(),
            name = tab.name,
            alias = "",
            aliasDisplay = "",
            iconUrl = "https://example.com/${tab.name}.png",
            type = 1,
            order = tab.ordinal,
            createdAtSec = 1L,
            detailUrl = "https://www.gamekee.com/ba/tj/${tab.ordinal + 1}.html",
            tab = tab,
        )
}
