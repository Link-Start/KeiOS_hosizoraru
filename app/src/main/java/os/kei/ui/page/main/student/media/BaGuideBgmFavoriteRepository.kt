package os.kei.ui.page.main.student

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers

internal class BaGuideBgmFavoriteRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
) {
    fun favoritesFlow(): StateFlow<List<GuideBgmFavoriteItem>> = GuideBgmFavoriteStore.observeFavoritesState()

    fun favoritesSnapshot(): List<GuideBgmFavoriteItem> = GuideBgmFavoriteStore.favoritesSnapshot()

    suspend fun hydrateFavorites(): List<GuideBgmFavoriteItem> =
        withContext(ioDispatcher) {
            GuideBgmFavoriteStore.favoritesSnapshot()
        }

    fun playbackSnapshot(): GuideBgmFavoritePlaybackSnapshot =
        runCatching {
            GuideBgmFavoritePlaybackStore.snapshot()
        }.getOrDefault(GuideBgmFavoritePlaybackSnapshot.Empty)

    suspend fun toggleFavorite(item: GuideBgmFavoriteItem): Boolean =
        withContext(ioDispatcher) {
            GuideBgmFavoriteStore.toggleFavorite(item)
        }

    suspend fun removeFavorite(audioUrl: String) {
        withContext(ioDispatcher) {
            GuideBgmFavoriteStore.removeFavorite(audioUrl)
        }
    }

    suspend fun previewImport(raw: String): GuideBgmFavoriteImportPreview =
        withContext(ioDispatcher) {
            GuideBgmFavoriteStore.previewFavoritesJsonImport(raw)
        }

    suspend fun importMerged(raw: String): GuideBgmFavoriteImportResult =
        withContext(ioDispatcher) {
            GuideBgmFavoriteStore.importFavoritesJsonMerged(raw)
        }

    suspend fun buildExportJson(): String =
        withContext(ioDispatcher) {
            GuideBgmFavoriteStore.buildFavoritesExportJson()
        }
}
