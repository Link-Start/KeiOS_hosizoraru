package os.kei.ui.page.main.student.catalog.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import os.kei.ui.page.main.student.catalog.testBgmFavorite
import os.kei.ui.page.main.student.catalog.testCatalogEntry

@OptIn(ExperimentalCoroutinesApi::class)
class BaGuideStudentBgmLookupCoordinatorTest {
    @Test
    fun `prewarm cached entries stores ready states without network`() = runBlocking {
        var networkCalls = 0
        val entry = testCatalogEntry(contentId = 1L)
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
        val firstEntry = testCatalogEntry(contentId = 11L)
        val secondEntry = testCatalogEntry(contentId = 12L)
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
    fun `visible network prewarm stores ready state when resolved`() = runBlocking {
        var networkCalls = 0
        val entry = testCatalogEntry(contentId = 21L)
        val item = resolvedItem("visible.mp3")
        val coordinator =
            BaGuideStudentBgmLookupCoordinator(
                scope = CoroutineScope(Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined,
                cachedLoader = { null },
                networkLoader = {
                    networkCalls += 1
                    item
                },
            )

        coordinator.prewarmVisibleNetwork(listOf(entry))

        val ready =
            assertIs<BaGuideStudentBgmLookupState.Ready>(
                coordinator.states.value.getValue(entry.contentId),
            )
        assertEquals(item.favorite.audioUrl, ready.item.favorite.audioUrl)
        assertEquals(1, networkCalls)
    }

    @Test
    fun `visible network prewarm keeps idle state when unresolved`() = runBlocking {
        var networkCalls = 0
        val entry = testCatalogEntry(contentId = 22L)
        val coordinator =
            BaGuideStudentBgmLookupCoordinator(
                scope = CoroutineScope(Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined,
                cachedLoader = { null },
                networkLoader = {
                    networkCalls += 1
                    null
                },
            )

        coordinator.prewarmVisibleNetwork(listOf(entry))

        assertNull(coordinator.states.value[entry.contentId])
        assertEquals(1, networkCalls)
    }

    @Test
    fun `resolve entry reuses ready state before loaders`() = runBlocking {
        var cacheCalls = 0
        var networkCalls = 0
        val entry = testCatalogEntry(contentId = 2L)
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
    fun `mark ready skips state map replacement when state is unchanged`() = runBlocking {
        val entry = testCatalogEntry(contentId = 31L)
        val item = resolvedItem("same.mp3")
        val coordinator =
            BaGuideStudentBgmLookupCoordinator(
                scope = CoroutineScope(Dispatchers.Unconfined),
                ioDispatcher = Dispatchers.Unconfined,
                cachedLoader = { null },
                networkLoader = { null },
            )

        coordinator.markReadyFromFavorite(entry, item)
        val firstStateMap = coordinator.states.value
        coordinator.markReadyFromFavorite(entry, item)

        assertEquals(firstStateMap, coordinator.states.value)
        assertSame(firstStateMap, coordinator.states.value)
    }

    @Test
    fun `cache prewarm does not replace active loading state`() = runTest {
        val entry = testCatalogEntry(contentId = 32L)
        val cachedItem = resolvedItem("cached-race.mp3")
        val networkItem = resolvedItem("network-race.mp3")
        val cacheStarted = CompletableDeferred<Unit>()
        val cacheRelease = CompletableDeferred<Unit>()
        val networkRelease = CompletableDeferred<BaGuideStudentBgmResolvedItem?>()
        val coordinator =
            BaGuideStudentBgmLookupCoordinator(
                scope = this,
                ioDispatcher = Dispatchers.Unconfined,
                cachedLoader = {
                    cacheStarted.complete(Unit)
                    cacheRelease.await()
                    cachedItem
                },
                networkLoader = {
                    networkRelease.await()
                },
            )

        coordinator.prewarmCached(listOf(entry))
        advanceUntilIdle()
        cacheStarted.await()
        coordinator.resolveEntry(entry, allowNetwork = true) {}
        advanceUntilIdle()
        val loadingStateMap = coordinator.states.value

        cacheRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(BaGuideStudentBgmLookupState.Loading, coordinator.states.value[entry.contentId])
        assertSame(loadingStateMap, coordinator.states.value)

        networkRelease.complete(networkItem)
        advanceUntilIdle()
        val ready =
            assertIs<BaGuideStudentBgmLookupState.Ready>(
                coordinator.states.value.getValue(entry.contentId),
            )
        assertEquals(networkItem.favorite.audioUrl, ready.item.favorite.audioUrl)
    }

    @Test
    fun `network miss stores missing state`() = runBlocking {
        val entry = testCatalogEntry(contentId = 3L)
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
        val entry = testCatalogEntry(contentId = 4L)
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

    private fun resolvedItem(audioUrl: String): BaGuideStudentBgmResolvedItem {
        return BaGuideStudentBgmResolvedItem(
            favorite = testBgmFavorite(
                audioUrl = audioUrl,
                sourceUrl = "https://www.gamekee.com/ba/demo",
                title = "BGM",
                studentTitle = "Demo",
                favoritedAtMs = 0L,
            ),
            fromCache = true
        )
    }
}
