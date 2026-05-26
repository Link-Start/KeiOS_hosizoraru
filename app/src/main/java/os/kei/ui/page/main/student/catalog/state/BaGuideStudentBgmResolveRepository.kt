package os.kei.ui.page.main.student.catalog.state

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.student.BaGuideDataClock
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.BaGuideSystemDataClock
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.component.BaGuideStudentBgmResolvedItem
import os.kei.ui.page.main.student.fetchGuideInfoAsync
import os.kei.ui.page.main.student.isGuideBgmFavoriteCandidateTitle
import os.kei.ui.page.main.student.normalizeGuideMediaSource

internal class BaGuideStudentBgmResolveRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val clock: BaGuideDataClock = BaGuideSystemDataClock,
) {
    suspend fun loadCachedFavorite(entry: BaGuideCatalogEntry): BaGuideStudentBgmResolvedItem? =
        withContext(ioDispatcher) {
            val info = BaStudentGuideStore.loadInfoSnapshot(entry.detailUrl).info ?: return@withContext null
            info.toStudentBgmFavorite(entry = entry, fromCache = true)
        }

    suspend fun fetchFavorite(entry: BaGuideCatalogEntry): BaGuideStudentBgmResolvedItem? {
        loadCachedFavorite(entry)?.let { return it }
        val info =
            fetchGuideInfoAsync(
                sourceUrl = entry.detailUrl,
                networkDispatcher = ioDispatcher,
                parseDispatcher = parseDispatcher,
                clock = clock,
            )
        withContext(ioDispatcher) {
            BaStudentGuideStore.saveInfo(info)
        }
        return info.toStudentBgmFavorite(entry = entry, fromCache = false)
    }
}

private fun BaStudentGuideInfo.toStudentBgmFavorite(
    entry: BaGuideCatalogEntry,
    fromCache: Boolean,
): BaGuideStudentBgmResolvedItem? {
    val item = firstStudentBgmGalleryItem() ?: return null
    val audioUrl = normalizeGuideMediaSource(item.mediaUrl)
    if (audioUrl.isBlank()) return null
    val studentImage = imageUrl.ifBlank { entry.iconUrl }
    return BaGuideStudentBgmResolvedItem(
        favorite =
            GuideBgmFavoriteItem(
                audioUrl = audioUrl,
                title = item.title,
                studentTitle = entry.name.ifBlank { title },
                studentImageUrl = studentImage,
                imageUrl = item.imageUrl.ifBlank { studentImage },
                sourceUrl = entry.detailUrl.ifBlank { sourceUrl },
                note = item.note.ifBlank { item.memoryUnlockLevel },
                favoritedAtMs = 0L,
            ),
        fromCache = fromCache,
    )
}

private fun BaStudentGuideInfo.firstStudentBgmGalleryItem(): BaGuideGalleryItem? {
    val audios =
        galleryItems.filter { item ->
            item.mediaType.equals("audio", ignoreCase = true) &&
                normalizeGuideMediaSource(item.mediaUrl).isNotBlank()
        }
    if (audios.isEmpty()) return null
    return audios.firstOrNull { item ->
        val title = item.title.trim()
        title.contains("回忆大厅", ignoreCase = true) ||
            title.contains("BGM", ignoreCase = true) ||
            isGuideBgmFavoriteCandidateTitle(title, title)
    } ?: audios.firstOrNull()
}
