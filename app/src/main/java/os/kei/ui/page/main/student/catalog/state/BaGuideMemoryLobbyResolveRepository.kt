package os.kei.ui.page.main.student.catalog.state

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.BaGuideDataClock
import os.kei.ui.page.main.student.BA_GUIDE_GALLERY_PARSER_VERSION
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.BaStudentGuideCacheSnapshot
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.BaGuideSystemDataClock
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.component.BaGuideMemoryLobbyCachedLookupResult
import os.kei.ui.page.main.student.catalog.component.BaGuideMemoryLobbyResolvedItem
import os.kei.ui.page.main.student.fetchGuideInfoAsync
import os.kei.ui.page.main.student.isMemoryHallGalleryItem
import os.kei.ui.page.main.student.tabcontent.render.resolveGuideGalleryTabState

internal class BaGuideMemoryLobbyResolveRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val clock: BaGuideDataClock = BaGuideSystemDataClock,
    private val cacheSnapshotLoader: (String) -> BaStudentGuideCacheSnapshot = BaStudentGuideStore::loadInfoSnapshot,
    private val infoFetcher: suspend (String, CoroutineDispatcher, CoroutineDispatcher, BaGuideDataClock) -> BaStudentGuideInfo = ::fetchGuideInfoAsync,
    private val infoSaver: (BaStudentGuideInfo) -> Unit = BaStudentGuideStore::saveInfo,
) {
    suspend fun loadCachedMemoryLobbyLookup(entry: BaGuideCatalogEntry): BaGuideMemoryLobbyCachedLookupResult =
        withContext(ioDispatcher) {
            val snapshot = cacheSnapshotLoader(entry.detailUrl)
            val info = snapshot.info ?: return@withContext BaGuideMemoryLobbyCachedLookupResult.NoCache
            if (info.galleryParserVersion < BA_GUIDE_GALLERY_PARSER_VERSION) {
                return@withContext BaGuideMemoryLobbyCachedLookupResult.NoCache
            }
            info
                .toMemoryLobbyResolvedItem(entry = entry, fromCache = true)
                ?.let(BaGuideMemoryLobbyCachedLookupResult::Ready)
                ?: run {
                    val refreshIntervalHours = BASettingsStore.loadCalendarRefreshIntervalHours()
                    val expired =
                        BaStudentGuideStore.isCacheExpired(
                            snapshot = snapshot,
                            refreshIntervalHours = refreshIntervalHours,
                            nowMs = clock.nowMs(),
                        )
                    if (expired) {
                        BaGuideMemoryLobbyCachedLookupResult.NoCache
                    } else {
                        BaGuideMemoryLobbyCachedLookupResult.FreshMissing
                    }
                }
        }

    suspend fun loadCachedMemoryLobby(entry: BaGuideCatalogEntry): BaGuideMemoryLobbyResolvedItem? =
        when (val result = loadCachedMemoryLobbyLookup(entry)) {
            is BaGuideMemoryLobbyCachedLookupResult.Ready -> result.item
            BaGuideMemoryLobbyCachedLookupResult.FreshMissing,
            BaGuideMemoryLobbyCachedLookupResult.NoCache,
            -> null
        }

    suspend fun fetchMemoryLobby(entry: BaGuideCatalogEntry): BaGuideMemoryLobbyResolvedItem? {
        val cachedInfo = withContext(ioDispatcher) { cacheSnapshotLoader(entry.detailUrl).info }
        val cachedLobby = cachedInfo?.toMemoryLobbyResolvedItem(entry, fromCache = true)
        if (cachedInfo != null && cachedInfo.galleryParserVersion >= BA_GUIDE_GALLERY_PARSER_VERSION && cachedLobby != null) {
            return cachedLobby
        }
        val info =
            try {
                infoFetcher(entry.detailUrl, ioDispatcher, parseDispatcher, clock)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Refresh parser-era caches without losing usable offline images and videos.
                return cachedLobby ?: throw error
            }
        withContext(ioDispatcher) {
            infoSaver(info)
        }
        return info.toMemoryLobbyResolvedItem(entry = entry, fromCache = false)
    }
}

internal fun BaStudentGuideInfo.toMemoryLobbyResolvedItem(
    entry: BaGuideCatalogEntry,
    fromCache: Boolean,
): BaGuideMemoryLobbyResolvedItem? {
    val resolvedGallery = resolveGuideGalleryTabState(this)
    val memoryImages =
        resolvedGallery.displayGalleryItems
            .filter(::isMemoryHallGalleryItem)
    val memoryVideos =
        resolvedGallery.memoryHallVideoGroup
            ?.second
            .orEmpty()
    val fallbackPreview =
        resolvedGallery.memoryHallPreview
            .takeIf { it.isNotBlank() && memoryImages.isEmpty() }
            ?.let { preview ->
                BaGuideGalleryItem(
                    title = MEMORY_LOBBY_FALLBACK_GALLERY_TITLE,
                    imageUrl = preview,
                    mediaType = "image",
                    mediaUrl = preview,
                    memoryUnlockLevel = resolvedGallery.memoryUnlockLevel,
                )
            }
    val galleryItems =
        (memoryImages + listOfNotNull(fallbackPreview) + memoryVideos)
            .distinctBy { item ->
                "${item.mediaType.lowercase()}|${item.mediaUrl.ifBlank { item.imageUrl }}"
            }
    if (galleryItems.isEmpty()) return null
    return BaGuideMemoryLobbyResolvedItem(
        galleryItems = galleryItems,
        memoryUnlockLevel = resolvedGallery.memoryUnlockLevel,
        studentTitle = entry.name.ifBlank { title },
        studentImageUrl = imageUrl.ifBlank { entry.iconUrl },
        sourceUrl = entry.detailUrl.ifBlank { sourceUrl },
        fromCache = fromCache,
    )
}

private const val MEMORY_LOBBY_FALLBACK_GALLERY_TITLE = "回忆大厅"
