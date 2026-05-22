package os.kei.ui.page.main.student.catalog.state

import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportKind
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportPreviewState

@Immutable
internal data class BaGuideFavoriteBgmCacheSnapshot(
    val cachedAudioUrls: Set<String> = emptySet(),
    val bytes: Long = 0L,
)

@Immutable
internal data class BaGuideFavoriteBgmOfflineCacheUiState(
    val offlineAudioUrls: Set<String> = emptySet(),
    val cachingAudioUrls: Set<String> = emptySet(),
)

internal sealed interface BaGuideCatalogEvent {
    data class BgmCacheBatchDone(
        val targetCount: Int,
    ) : BaGuideCatalogEvent

    data class BgmCacheCleaned(
        val cleanedCount: Int,
    ) : BaGuideCatalogEvent

    data object FavoriteBgmCacheSuccess : BaGuideCatalogEvent

    data object FavoriteBgmCacheFailed : BaGuideCatalogEvent

    data object FavoriteBgmCacheRemoved : BaGuideCatalogEvent

    data object BgmFavoriteAdded : BaGuideCatalogEvent

    data object BgmFavoriteRemoved : BaGuideCatalogEvent

    data class CatalogImportPreviewReady(
        val preview: BaGuideCatalogImportPreviewState,
    ) : BaGuideCatalogEvent

    data class CatalogImportApplied(
        val kind: BaGuideCatalogImportKind,
        val studentCount: Int,
        val bgmAddedCount: Int,
        val bgmUpdatedCount: Int,
    ) : BaGuideCatalogEvent

    data class CatalogImportFailed(
        val kind: BaGuideCatalogImportKind,
    ) : BaGuideCatalogEvent
}
