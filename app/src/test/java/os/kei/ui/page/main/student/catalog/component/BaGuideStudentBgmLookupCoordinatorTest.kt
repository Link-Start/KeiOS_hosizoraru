package os.kei.ui.page.main.student.catalog.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BaGuideStudentBgmLookupCoordinatorTest {
    @Test
    fun `prewarm cached entries stores ready states without network`() = runBlocking {
        var networkCalls = 0
        val entry = catalogEntry(contentId = 1L)
        val item = resolvedItem("cached.mp3")
        val coordinator = BaGuideStudentBgmLookupCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            cachedLoader = { item },
            networkLoader = {
                networkCalls += 1
                null
            }
        )

        coordinator.prewarmCached(listOf(entry))

        val ready = assertIs<BaGuideStudentBgmLookupState.Ready>(
            coordinator.states.value.getValue(entry.contentId)
        )
        assertEquals(item.favorite.audioUrl, ready.item.favorite.audioUrl)
        assertEquals(0, networkCalls)
    }

    @Test
    fun `prewarm skips entries already checked as cache misses`() = runBlocking {
        val firstEntry = catalogEntry(contentId = 11L)
        val secondEntry = catalogEntry(contentId = 12L)
        val checkedContentIds = mutableListOf<Long>()
        val coordinator = BaGuideStudentBgmLookupCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            cachedLoader = { entry ->
                checkedContentIds += entry.contentId
                null
            },
            networkLoader = { null }
        )

        coordinator.prewarmCached(listOf(firstEntry))
        coordinator.prewarmCached(listOf(firstEntry, secondEntry))

        assertEquals(listOf(firstEntry.contentId, secondEntry.contentId), checkedContentIds)
    }

    @Test
    fun `resolve entry reuses ready state before loaders`() = runBlocking {
        var cacheCalls = 0
        var networkCalls = 0
        val entry = catalogEntry(contentId = 2L)
        val item = resolvedItem("ready.mp3")
        val coordinator = BaGuideStudentBgmLookupCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            cachedLoader = {
                cacheCalls += 1
                null
            },
            networkLoader = {
                networkCalls += 1
                null
            }
        )
        coordinator.markReadyFromFavorite(entry, item)
        var resolved: BaGuideStudentBgmResolvedItem? = null

        coordinator.resolveEntry(entry, allowNetwork = true) { resolved = it }

        assertEquals(item.favorite.audioUrl, resolved?.favorite?.audioUrl)
        assertEquals(0, cacheCalls)
        assertEquals(0, networkCalls)
    }

    @Test
    fun `network miss stores missing state`() = runBlocking {
        val entry = catalogEntry(contentId = 3L)
        val coordinator = BaGuideStudentBgmLookupCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            cachedLoader = { null },
            networkLoader = { null }
        )
        var resolved: BaGuideStudentBgmResolvedItem? = resolvedItem("stale.mp3")

        coordinator.resolveEntry(entry, allowNetwork = true) { resolved = it }

        assertNull(resolved)
        assertEquals(
            BaGuideStudentBgmLookupState.Missing,
            coordinator.states.value[entry.contentId]
        )
    }

    @Test
    fun `resolve entry while loading fans out result to every caller`() = runTest {
        val entry = catalogEntry(contentId = 4L)
        val item = resolvedItem("network.mp3")
        val deferred = CompletableDeferred<BaGuideStudentBgmResolvedItem?>()
        val coordinator = BaGuideStudentBgmLookupCoordinator(
            scope = this,
            ioDispatcher = Dispatchers.Unconfined,
            cachedLoader = { null },
            networkLoader = { deferred.await() },
        )
        val resolvedAudioUrls = mutableListOf<String?>()

        coordinator.resolveEntry(entry, allowNetwork = true) { resolved ->
            resolvedAudioUrls += resolved?.favorite?.audioUrl
        }
        coordinator.resolveEntry(entry, allowNetwork = true) { resolved ->
            resolvedAudioUrls += resolved?.favorite?.audioUrl
        }
        deferred.complete(item)
        advanceUntilIdle()

        assertEquals(listOf<String?>(item.favorite.audioUrl, item.favorite.audioUrl), resolvedAudioUrls)
        val ready = assertIs<BaGuideStudentBgmLookupState.Ready>(
            coordinator.states.value.getValue(entry.contentId),
        )
        assertEquals(item.favorite.audioUrl, ready.item.favorite.audioUrl)
    }

    private fun catalogEntry(contentId: Long): BaGuideCatalogEntry {
        return BaGuideCatalogEntry(
            entryId = contentId.toInt(),
            pid = 49443,
            contentId = contentId,
            name = "Demo",
            alias = "",
            aliasDisplay = "",
            iconUrl = "",
            type = 0,
            order = contentId.toInt(),
            createdAtSec = 0L,
            detailUrl = "https://www.gamekee.com/ba/$contentId",
            tab = BaGuideCatalogTab.Student
        )
    }

    private fun resolvedItem(audioUrl: String): BaGuideStudentBgmResolvedItem {
        return BaGuideStudentBgmResolvedItem(
            favorite = GuideBgmFavoriteItem(
                audioUrl = audioUrl,
                title = "BGM",
                studentTitle = "Demo",
                studentImageUrl = "",
                imageUrl = "",
                sourceUrl = "https://www.gamekee.com/ba/demo",
                note = "",
                favoritedAtMs = 0L
            ),
            fromCache = true
        )
    }
}
