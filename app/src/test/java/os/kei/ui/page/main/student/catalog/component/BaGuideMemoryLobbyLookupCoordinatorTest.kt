package os.kei.ui.page.main.student.catalog.component

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.catalog.testCatalogEntry
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class BaGuideMemoryLobbyLookupCoordinatorTest {
    @Test
    fun `prewarm cached entries stores ready states without network`() =
        runBlocking {
            var networkCalls = 0
            val entry = testCatalogEntry(contentId = 1L)
            val item = resolvedItem("https://example.com/cached.png")
            val coordinator =
                BaGuideMemoryLobbyLookupCoordinator(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    ioDispatcher = Dispatchers.Unconfined,
                    cachedLoader = { BaGuideMemoryLobbyCachedLookupResult.Ready(item) },
                    networkLoader = {
                        networkCalls += 1
                        null
                    },
                )

            coordinator.prewarmCached(listOf(entry))

            val ready =
                assertIs<BaGuideMemoryLobbyLookupState.Ready>(
                    coordinator.states.value.getValue(entry.contentId),
                )
            assertEquals(item.galleryItems.single().mediaUrl, ready.item.galleryItems.single().mediaUrl)
            assertEquals(0, networkCalls)
        }

    @Test
    fun `visible network miss stores missing state`() =
        runBlocking {
            val entry = testCatalogEntry(contentId = 2L)
            val coordinator =
                BaGuideMemoryLobbyLookupCoordinator(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    ioDispatcher = Dispatchers.Unconfined,
                    cachedLoader = { BaGuideMemoryLobbyCachedLookupResult.NoCache },
                    networkLoader = { null },
                )

            coordinator.prewarmVisibleNetwork(listOf(entry))

            assertEquals(BaGuideMemoryLobbyLookupState.Missing, coordinator.states.value[entry.contentId])
        }

    @Test
    fun `resolve entry while loading fans out result to every caller`() =
        runTest {
            val entry = testCatalogEntry(contentId = 3L)
            val item = resolvedItem("https://example.com/network.png")
            val deferred = CompletableDeferred<BaGuideMemoryLobbyResolvedItem?>()
            val coordinator =
                BaGuideMemoryLobbyLookupCoordinator(
                    scope = this,
                    ioDispatcher = Dispatchers.Unconfined,
                    cachedLoader = { BaGuideMemoryLobbyCachedLookupResult.NoCache },
                    networkLoader = { deferred.await() },
                )
            val resolvedUrls = mutableListOf<String?>()

            coordinator.resolveEntry(entry, allowNetwork = true) { resolved ->
                resolvedUrls += resolved?.galleryItems?.single()?.mediaUrl
            }
            coordinator.resolveEntry(entry, allowNetwork = true) { resolved ->
                resolvedUrls += resolved?.galleryItems?.single()?.mediaUrl
            }
            deferred.complete(item)
            advanceUntilIdle()

            assertEquals(
                listOf<String?>(item.galleryItems.single().mediaUrl, item.galleryItems.single().mediaUrl),
                resolvedUrls,
            )
            val ready =
                assertIs<BaGuideMemoryLobbyLookupState.Ready>(
                    coordinator.states.value.getValue(entry.contentId),
                )
            assertEquals(item.galleryItems.single().mediaUrl, ready.item.galleryItems.single().mediaUrl)
        }

    @Test
    fun `fresh cached missing state skips visible network prewarm`() =
        runBlocking {
            var networkCalls = 0
            val entry = testCatalogEntry(contentId = 4L)
            val coordinator =
                BaGuideMemoryLobbyLookupCoordinator(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    ioDispatcher = Dispatchers.Unconfined,
                    cachedLoader = { BaGuideMemoryLobbyCachedLookupResult.FreshMissing },
                    networkLoader = {
                        networkCalls += 1
                        null
                    },
                )

            coordinator.prewarmCached(listOf(entry))
            coordinator.prewarmVisibleNetwork(listOf(entry))

            assertEquals(BaGuideMemoryLobbyLookupState.Missing, coordinator.states.value[entry.contentId])
            assertEquals(0, networkCalls)
        }

    @Test
    fun `cache prewarm does not replace active loading state`() =
        runTest {
            val entry = testCatalogEntry(contentId = 5L)
            val cachedItem = resolvedItem("https://example.com/cached-race.png")
            val networkItem = resolvedItem("https://example.com/network-race.png")
            val cacheStarted = CompletableDeferred<Unit>()
            val cacheRelease = CompletableDeferred<Unit>()
            val networkRelease = CompletableDeferred<BaGuideMemoryLobbyResolvedItem?>()
            val coordinator =
                BaGuideMemoryLobbyLookupCoordinator(
                    scope = this,
                    ioDispatcher = Dispatchers.Unconfined,
                    cachedLoader = {
                        cacheStarted.complete(Unit)
                        cacheRelease.await()
                        BaGuideMemoryLobbyCachedLookupResult.Ready(cachedItem)
                    },
                    networkLoader = {
                        networkRelease.await()
                    },
                )

            coordinator.prewarmCached(listOf(entry))
            advanceUntilIdle()
            cacheStarted.await()
            coordinator.resolveEntry(entry, allowNetwork = true)
            advanceUntilIdle()
            val loadingStateMap = coordinator.states.value

            cacheRelease.complete(Unit)
            advanceUntilIdle()

            assertEquals(BaGuideMemoryLobbyLookupState.Loading, coordinator.states.value[entry.contentId])
            assertSame(loadingStateMap, coordinator.states.value)

            networkRelease.complete(networkItem)
            advanceUntilIdle()
            val ready =
                assertIs<BaGuideMemoryLobbyLookupState.Ready>(
                    coordinator.states.value.getValue(entry.contentId),
                )
            assertEquals(networkItem.galleryItems.single().mediaUrl, ready.item.galleryItems.single().mediaUrl)
        }

    private fun resolvedItem(mediaUrl: String): BaGuideMemoryLobbyResolvedItem =
        BaGuideMemoryLobbyResolvedItem(
            galleryItems =
                listOf(
                    BaGuideGalleryItem(
                        title = "回忆大厅",
                        imageUrl = mediaUrl,
                        mediaUrl = mediaUrl,
                    ),
                ),
            memoryUnlockLevel = "5",
            studentTitle = "Demo",
            studentImageUrl = "",
            sourceUrl = "https://www.gamekee.com/ba/demo",
            fromCache = true,
        )
}
