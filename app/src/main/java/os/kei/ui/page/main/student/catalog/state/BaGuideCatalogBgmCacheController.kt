package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.GuideBgmFavoriteItem

internal class BaGuideCatalogBgmCacheController(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val favoriteBgms: StateFlow<List<GuideBgmFavoriteItem>>,
    private val events: MutableSharedFlow<BaGuideCatalogEvent>,
) {
    private var bgmCacheSnapshotJob: Job? = null
    private var bgmCacheSnapshotInput: List<GuideBgmFavoriteItem>? = null
    private var favoriteBgmOfflineCacheJob: Job? = null
    private var favoriteBgmOfflineCacheInput: List<GuideBgmFavoriteItem>? = null

    private val mutableBgmCacheSnapshot = MutableStateFlow(BaGuideFavoriteBgmCacheSnapshot())
    private val mutableFavoriteBgmOfflineCacheState =
        MutableStateFlow(BaGuideFavoriteBgmOfflineCacheUiState())

    val bgmCacheSnapshot: StateFlow<BaGuideFavoriteBgmCacheSnapshot> =
        mutableBgmCacheSnapshot.asStateFlow()
    val favoriteBgmOfflineCacheState: StateFlow<BaGuideFavoriteBgmOfflineCacheUiState> =
        mutableFavoriteBgmOfflineCacheState.asStateFlow()

    fun requestBgmCacheSnapshot(
        favorites: List<GuideBgmFavoriteItem>,
        force: Boolean = false,
    ) {
        if (!force && bgmCacheSnapshotInput == favorites) return
        bgmCacheSnapshotInput = favorites
        bgmCacheSnapshotJob?.cancel()
        bgmCacheSnapshotJob =
            scope.launch {
                mutableBgmCacheSnapshot.value =
                    BaGuideFavoriteBgmCacheRepository.loadCacheSnapshot(
                        context = appContext,
                        favorites = favorites,
                    )
            }
    }

    fun cacheMissingBgms(favorites: List<GuideBgmFavoriteItem>) {
        scope.launch {
            val targetCount =
                BaGuideFavoriteBgmCacheRepository.cacheMissingFavorites(
                    context = appContext,
                    favorites = favorites,
                )
            refreshBgmCacheStates(
                allFavorites = favoriteBgms.value,
                displayedFavorites = favoriteBgmOfflineCacheInput,
            )
            events.emit(BaGuideCatalogEvent.BgmCacheBatchDone(targetCount))
        }
    }

    fun cleanInvalidBgmCache(favorites: List<GuideBgmFavoriteItem>) {
        scope.launch {
            val cleaned =
                BaGuideFavoriteBgmCacheRepository.cleanInvalidFavorites(
                    context = appContext,
                    favorites = favorites,
                )
            refreshBgmCacheStates(
                allFavorites = favoriteBgms.value,
                displayedFavorites = favoriteBgmOfflineCacheInput,
            )
            events.emit(BaGuideCatalogEvent.BgmCacheCleaned(cleaned))
        }
    }

    fun requestFavoriteBgmOfflineCache(
        favorites: List<GuideBgmFavoriteItem>,
        isPageActive: Boolean,
        force: Boolean = false,
    ) {
        if (!isPageActive) return
        if (!force && favoriteBgmOfflineCacheInput == favorites) return
        favoriteBgmOfflineCacheInput = favorites
        favoriteBgmOfflineCacheJob?.cancel()
        favoriteBgmOfflineCacheJob =
            scope.launch {
                val offlineAudioUrls =
                    BaGuideFavoriteBgmCacheRepository.loadCachedAudioUrls(
                        context = appContext,
                        favorites = favorites,
                    )
                mutableFavoriteBgmOfflineCacheState.update { state ->
                    state.copy(offlineAudioUrls = offlineAudioUrls)
                }
            }
    }

    fun toggleFavoriteBgmOfflineCache(
        favorite: GuideBgmFavoriteItem,
        displayedFavorites: List<GuideBgmFavoriteItem>,
    ) {
        val audioUrl = favorite.audioUrl
        if (audioUrl.isBlank()) return
        val current = mutableFavoriteBgmOfflineCacheState.value
        if (audioUrl in current.offlineAudioUrls) {
            scope.launch {
                BaGuideFavoriteBgmCacheRepository.clearFavorite(appContext, favorite)
                mutableFavoriteBgmOfflineCacheState.update { state ->
                    state.copy(offlineAudioUrls = state.offlineAudioUrls - audioUrl)
                }
                refreshBgmCacheStates(
                    allFavorites = favoriteBgms.value,
                    displayedFavorites = displayedFavorites,
                )
                events.emit(BaGuideCatalogEvent.FavoriteBgmCacheRemoved)
            }
            return
        }
        if (audioUrl in current.cachingAudioUrls) return
        mutableFavoriteBgmOfflineCacheState.update { state ->
            state.copy(cachingAudioUrls = state.cachingAudioUrls + audioUrl)
        }
        scope.launch {
            val success =
                try {
                    BaGuideFavoriteBgmCacheRepository.cacheFavorite(appContext, favorite)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                } finally {
                    mutableFavoriteBgmOfflineCacheState.update { state ->
                        state.copy(cachingAudioUrls = state.cachingAudioUrls - audioUrl)
                    }
                }
            if (success) {
                mutableFavoriteBgmOfflineCacheState.update { state ->
                    state.copy(offlineAudioUrls = state.offlineAudioUrls + audioUrl)
                }
            }
            refreshBgmCacheStates(
                allFavorites = favoriteBgms.value,
                displayedFavorites = displayedFavorites,
            )
            events.emit(
                if (success) {
                    BaGuideCatalogEvent.FavoriteBgmCacheSuccess
                } else {
                    BaGuideCatalogEvent.FavoriteBgmCacheFailed
                },
            )
        }
    }

    suspend fun refreshBgmCacheStates(
        allFavorites: List<GuideBgmFavoriteItem>,
        displayedFavorites: List<GuideBgmFavoriteItem>?,
    ) {
        bgmCacheSnapshotInput = allFavorites
        mutableBgmCacheSnapshot.value =
            BaGuideFavoriteBgmCacheRepository.loadCacheSnapshot(
                context = appContext,
                favorites = allFavorites,
            )
        (displayedFavorites ?: favoriteBgmOfflineCacheInput)?.let { favorites ->
            favoriteBgmOfflineCacheInput = favorites
            val offlineAudioUrls =
                BaGuideFavoriteBgmCacheRepository.loadCachedAudioUrls(
                    context = appContext,
                    favorites = favorites,
                )
            mutableFavoriteBgmOfflineCacheState.update { state ->
                state.copy(offlineAudioUrls = offlineAudioUrls)
            }
        }
    }

    fun cancel() {
        bgmCacheSnapshotJob?.cancel()
        favoriteBgmOfflineCacheJob?.cancel()
    }
}
